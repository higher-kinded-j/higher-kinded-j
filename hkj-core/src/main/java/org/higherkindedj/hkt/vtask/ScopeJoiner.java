// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Function;
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
 * @param <T> the type of values produced by subtasks
 */
@SuppressWarnings("preview")
final class AllSucceedJoiner<T> implements ScopeJoiner<T, List<T>> {

  private final StructuredTaskScope.Joiner<T, List<T>> delegate;

  AllSucceedJoiner() {
    this.delegate =
        StructuredTaskScope.Joiner.allSuccessfulOrThrow(
            subtasks -> {
              List<T> results = new ArrayList<>();
              for (var subtask : subtasks) {
                results.add(subtask.get());
              }
              return results;
            });
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
 * @param <T> the type of values produced by subtasks
 */
@SuppressWarnings("preview")
final class FirstCompleteJoiner<T> implements ScopeJoiner<T, T> {

  @Override
  public StructuredTaskScope.Joiner<T, T> joiner() {
    // Custom joiner that returns first completion
    return new StructuredTaskScope.Joiner<>() {
      private volatile T result;
      private volatile Throwable error;
      private volatile boolean completed = false;

      @Override
      public boolean onFork(StructuredTaskScope.Subtask<? extends T> subtask) {
        return !completed;
      }

      @Override
      public boolean onComplete(StructuredTaskScope.Subtask<? extends T> subtask) {
        if (!completed) {
          completed = true;
          switch (subtask.state()) {
            case SUCCESS -> result = subtask.get();
            case FAILED -> error = subtask.exception();
            default -> {}
          }
          return false; // Cancel remaining tasks
        }
        return true;
      }

      @Override
      public T result() throws Throwable {
        if (error != null) {
          throw error;
        }
        return result;
      }
    };
  }
}

/**
 * Joiner that accumulates all errors using {@link Validated}.
 *
 * @param <E> the error type after mapping
 * @param <T> the type of values produced by subtasks
 */
@SuppressWarnings("preview")
final class AccumulatingJoiner<E, T> implements ScopeJoiner<T, Validated<List<E>, List<T>>> {

  private final Function<Throwable, E> errorMapper;

  AccumulatingJoiner(Function<Throwable, E> errorMapper) {
    this.errorMapper = errorMapper;
  }

  @Override
  public StructuredTaskScope.Joiner<T, Validated<List<E>, List<T>>> joiner() {
    return new StructuredTaskScope.Joiner<>() {
      private final List<E> errors = Collections.synchronizedList(new ArrayList<>());
      private final List<T> successes = Collections.synchronizedList(new ArrayList<>());

      @Override
      public boolean onFork(StructuredTaskScope.Subtask<? extends T> subtask) {
        return true; // Always fork
      }

      @Override
      public boolean onComplete(StructuredTaskScope.Subtask<? extends T> subtask) {
        switch (subtask.state()) {
          case SUCCESS -> successes.add(subtask.get());
          case FAILED -> errors.add(errorMapper.apply(subtask.exception()));
          default -> {}
        }
        return true; // Continue waiting for all
      }

      @Override
      public Validated<List<E>, List<T>> result() {
        if (errors.isEmpty()) {
          return Validated.valid(new ArrayList<>(successes));
        } else {
          return Validated.invalid(new ArrayList<>(errors));
        }
      }
    };
  }

  /**
   * Returns the result wrapped in Either, adapting the Validated result.
   *
   * @return Either containing the Validated result
   */
  @Override
  public Either<Throwable, Validated<List<E>, List<T>>> resultEither() {
    try {
      return Either.right(joiner().result());
    } catch (Throwable t) {
      return Either.left(t);
    }
  }
}
