// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;

/**
 * A functional wrapper around Java 25's {@link StructuredTaskScope.Joiner} interface.
 *
 * <p>ScopeJoiner provides a hybrid approach that:
 *
 * <ul>
 *   <li>Wraps Java 25's native {@code Joiner} for direct interoperability
 *   <li>Provides functional result accessors via {@link Either} and {@link Validated}
 *   <li>Offers HKJ-specific joiners like error accumulation with {@link Validated}
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Use built-in joiner for all-succeed semantics
 * ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();
 *
 * // Use accumulating joiner for error collection
 * ScopeJoiner<String, Validated<List<Error>, List<String>>> accum =
 *     ScopeJoiner.accumulating(Error::fromException);
 *
 * // Access Java 25 Joiner directly when needed
 * StructuredTaskScope.Joiner<String, List<String>> java25Joiner = joiner.joiner();
 * }</pre>
 *
 * <h2>Preview API Notice</h2>
 *
 * <p><b>Note:</b> This class uses Java 25's structured concurrency APIs which are currently in
 * preview (JEP 505/525). The underlying API may change in future Java releases. The ScopeJoiner
 * abstraction provides a buffer against such changes.
 *
 * @param <T> the type of values produced by subtasks
 * @param <R> the type of the final result after joining
 * @see StructuredTaskScope
 * @see StructuredTaskScope.Joiner
 */
public sealed interface ScopeJoiner<T, R>
    permits AllSucceedJoiner, AnySucceedJoiner, FirstCompleteJoiner, AccumulatingJoiner {

  /**
   * Returns the underlying Java 25 {@link StructuredTaskScope.Joiner}.
   *
   * <p>Use this method when you need direct access to Java's native structured concurrency API, for
   * example when passing to {@code StructuredTaskScope.open(Joiner)}.
   *
   * @return the underlying Java 25 Joiner; never null
   */
  StructuredTaskScope.Joiner<T, R> joiner();

  /**
   * Returns the result wrapped in an {@link Either}, capturing any exceptions.
   *
   * <p>This provides a functional alternative to the throwing {@code result()} method of Java 25's
   * Joiner. Exceptions are captured in the Left side of the Either.
   *
   * @return {@code Either.right(result)} on success, {@code Either.left(exception)} on failure
   */
  default Either<Throwable, R> resultEither() {
    try {
      return Either.right(joiner().result());
    } catch (Throwable t) {
      return Either.left(t);
    }
  }

  // ==================== Factory Methods ====================

  /**
   * Creates a joiner that waits for all subtasks to succeed.
   *
   * <p>If any subtask fails, the entire operation fails with that exception. Results are collected
   * in the order tasks were forked.
   *
   * @param <T> the type of values produced by subtasks
   * @return a joiner that collects all successful results into a list
   */
  static <T> ScopeJoiner<T, List<T>> allSucceed() {
    return new AllSucceedJoiner<>();
  }

  /**
   * Creates a joiner that returns the first successful result.
   *
   * <p>As soon as any subtask succeeds, its result is returned and other tasks are cancelled. If
   * all tasks fail, the operation fails with the last exception.
   *
   * @param <T> the type of values produced by subtasks
   * @return a joiner that returns the first successful result
   */
  static <T> ScopeJoiner<T, T> anySucceed() {
    return new AnySucceedJoiner<>();
  }

  /**
   * Creates a joiner that returns the first completed result (success or failure).
   *
   * <p>This is useful for racing tasks where you want the fastest response, regardless of whether
   * it succeeded or failed.
   *
   * @param <T> the type of values produced by subtasks
   * @return a joiner that returns the first result to complete
   */
  static <T> ScopeJoiner<T, T> firstComplete() {
    return new FirstCompleteJoiner<>();
  }

  /**
   * Creates a joiner that accumulates errors using {@link Validated}.
   *
   * <p>Unlike fail-fast joiners, this joiner waits for all tasks to complete and collects both
   * successes and failures. The result is a {@code Validated} that is:
   *
   * <ul>
   *   <li>{@code Valid(List<T>)} if all tasks succeeded
   *   <li>{@code Invalid(List<E>)} if any task failed, containing all mapped errors
   * </ul>
   *
   * <p>This is particularly useful for validation scenarios where you want to report all errors at
   * once rather than stopping at the first failure.
   *
   * @param <E> the error type after mapping
   * @param <T> the type of values produced by subtasks
   * @param errorMapper function to convert exceptions to error type E; must not be null
   * @return a joiner that accumulates all errors
   * @throws NullPointerException if errorMapper is null
   */
  static <E, T> ScopeJoiner<T, Validated<List<E>, List<T>>> accumulating(
      Function<Throwable, E> errorMapper) {
    Objects.requireNonNull(errorMapper, "errorMapper must not be null");
    return new AccumulatingJoiner<>(errorMapper);
  }
}

// ==================== Implementation Classes ====================

/**
 * Joiner that waits for all subtasks to succeed.
 *
 * <p>Uses Java 25's built-in {@code Joiner.allSuccessfulOrThrow()} internally and converts the
 * Stream result to a List.
 *
 * @param <T> the type of values produced by subtasks
 */
@SuppressWarnings("preview")
final class AllSucceedJoiner<T> implements ScopeJoiner<T, List<T>> {

  private final StructuredTaskScope.Joiner<T, List<T>> delegate;

  AllSucceedJoiner() {
    // Use the built-in joiner that handles all the logic
    // Note: allSuccessfulOrThrow() returns Stream<Subtask<T>>, not Stream<T>
    StructuredTaskScope.Joiner<T, Stream<StructuredTaskScope.Subtask<T>>> builtIn =
        StructuredTaskScope.Joiner.allSuccessfulOrThrow();

    this.delegate =
        new StructuredTaskScope.Joiner<>() {
          @Override
          public boolean onFork(StructuredTaskScope.Subtask<? extends T> subtask) {
            return builtIn.onFork(subtask);
          }

          @Override
          public boolean onComplete(StructuredTaskScope.Subtask<? extends T> subtask) {
            return builtIn.onComplete(subtask);
          }

          @Override
          public List<T> result() throws Throwable {
            // Extract values from Subtasks and collect to List
            return builtIn.result().map(StructuredTaskScope.Subtask::get).toList();
          }
        };
  }

  @Override
  public StructuredTaskScope.Joiner<T, List<T>> joiner() {
    return delegate;
  }
}

/**
 * Joiner that returns the first successful result.
 *
 * @param <T> the type of values produced by subtasks
 */
@SuppressWarnings("preview")
final class AnySucceedJoiner<T> implements ScopeJoiner<T, T> {

  private final StructuredTaskScope.Joiner<T, T> delegate;

  AnySucceedJoiner() {
    this.delegate = StructuredTaskScope.Joiner.anySuccessfulResultOrThrow();
  }

  @Override
  public StructuredTaskScope.Joiner<T, T> joiner() {
    return delegate;
  }
}

/**
 * Joiner that returns the first completed result (success or failure).
 *
 * <p>Stores the first completed subtask and returns its result. Other subtasks are cancelled.
 *
 * @param <T> the type of values produced by subtasks
 */
@SuppressWarnings("preview")
final class FirstCompleteJoiner<T> implements ScopeJoiner<T, T> {

  private final StructuredTaskScope.Joiner<T, T> delegate;

  FirstCompleteJoiner() {
    AtomicReference<StructuredTaskScope.Subtask<? extends T>> firstCompleted =
        new AtomicReference<>();

    // Use awaitAll() for proper completion tracking
    StructuredTaskScope.Joiner<T, Void> completionTracker = StructuredTaskScope.Joiner.awaitAll();

    this.delegate =
        new StructuredTaskScope.Joiner<>() {
          @Override
          public boolean onFork(StructuredTaskScope.Subtask<? extends T> subtask) {
            if (firstCompleted.get() != null) {
              return false; // Don't fork if we already have a result
            }
            return completionTracker.onFork(subtask);
          }

          @Override
          public boolean onComplete(StructuredTaskScope.Subtask<? extends T> subtask) {
            if (firstCompleted.compareAndSet(null, subtask)) {
              return false; // Cancel remaining tasks - we have our result
            }
            return completionTracker.onComplete(subtask);
          }

          @Override
          public T result() throws Throwable {
            StructuredTaskScope.Subtask<? extends T> subtask = firstCompleted.get();
            if (subtask == null) {
              throw new IllegalStateException("No subtask completed");
            }
            if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
              throw subtask.exception();
            }
            return subtask.get();
          }
        };
  }

  @Override
  public StructuredTaskScope.Joiner<T, T> joiner() {
    return delegate;
  }
}

/**
 * Joiner that accumulates all errors using {@link Validated}.
 *
 * <p>Unlike fail-fast joiners, this waits for ALL subtasks to complete, collecting both successes
 * and failures. Subtasks are tracked when forked and processed in the result() method.
 *
 * @param <E> the error type after mapping
 * @param <T> the type of values produced by subtasks
 */
@SuppressWarnings("preview")
final class AccumulatingJoiner<E, T> implements ScopeJoiner<T, Validated<List<E>, List<T>>> {

  // Store as class field to ensure proper capture
  private final List<StructuredTaskScope.Subtask<? extends T>> allSubtasks =
      Collections.synchronizedList(new ArrayList<>());
  private final Function<Throwable, E> errorMapper;
  private final StructuredTaskScope.Joiner<T, Validated<List<E>, List<T>>> delegate;

  AccumulatingJoiner(Function<Throwable, E> errorMapper) {
    this.errorMapper = errorMapper;

    // Use awaitAll() for proper completion tracking - it knows when all tasks are done
    StructuredTaskScope.Joiner<T, Void> completionTracker = StructuredTaskScope.Joiner.awaitAll();

    this.delegate =
        new StructuredTaskScope.Joiner<>() {
          @Override
          public boolean onFork(StructuredTaskScope.Subtask<? extends T> subtask) {
            allSubtasks.add(subtask);
            return completionTracker.onFork(subtask); // Let awaitAll track completion
          }

          @Override
          public boolean onComplete(StructuredTaskScope.Subtask<? extends T> subtask) {
            return completionTracker.onComplete(subtask); // Let awaitAll track completion
          }

          @Override
          public Validated<List<E>, List<T>> result() throws Throwable {
            // awaitAll ensures all tasks are complete before this is called
            completionTracker.result();

            List<E> errors = new ArrayList<>();
            List<T> successes = new ArrayList<>();

            for (StructuredTaskScope.Subtask<? extends T> subtask : allSubtasks) {
              if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
                errors.add(errorMapper.apply(subtask.exception()));
              } else {
                successes.add(subtask.get());
              }
            }

            if (errors.isEmpty()) {
              return Validated.valid(successes);
            } else {
              return Validated.invalid(errors);
            }
          }
        };
  }

  @Override
  public StructuredTaskScope.Joiner<T, Validated<List<E>, List<T>>> joiner() {
    return delegate;
  }
}
