// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
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
 * // Access a Java 25 Joiner directly when needed: a new one for each StructuredTaskScope
 * StructuredTaskScope.Joiner<String, List<String>> java25Joiner = joiner.joiner();
 * }</pre>
 *
 * <h2>One ScopeJoiner, Many Scopes</h2>
 *
 * <p>A {@code ScopeJoiner} holds only its configuration, such as the error mapper given to {@link
 * #accumulating(Function)}. {@link #joiner()} builds a new {@code Joiner}, with state of its own,
 * each time it is called, because a {@code StructuredTaskScope} needs a {@code Joiner} that no
 * other scope has used. So one {@code ScopeJoiner}, and a {@link Scope} built with it, can join any
 * number of scopes, one after another or at the same time.
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
    permits AllSucceedJoiner,
        AnySucceedJoiner,
        FirstCompleteJoiner,
        AccumulatingJoiner,
        FirstSuccessEitherJoiner {

  /**
   * Returns a new Java 25 {@link StructuredTaskScope.Joiner}, with state of its own, each time it
   * is called.
   *
   * <p>Use this method when you need direct access to Java's native structured concurrency API, for
   * example when passing to {@code StructuredTaskScope.open(Joiner)}. Open one scope with each
   * {@code Joiner}, and keep it in a variable if you read its {@code result()} after the scope
   * joins.
   *
   * @return a new Java 25 Joiner; never null
   */
  StructuredTaskScope.Joiner<T, R> joiner();

  /**
   * Returns the result of the {@code Joiner} that {@link #joiner()} returned most recently, wrapped
   * in an {@link Either}, capturing any exceptions.
   *
   * <p>Exceptions are captured in the Left side of the Either. Before {@code joiner()} is first
   * called, this reads a {@code Joiner} that no scope has used.
   *
   * @return {@code Either.right(result)} on success, {@code Either.left(exception)} on failure
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. {@link #joiner()} returns a new {@code
   *     Joiner} on each call, so this reads whichever one was handed out last, which another scope
   *     or another thread may have replaced. Use {@link Scope#joinEither()}, or wrap the result of
   *     the scope's {@code join()} in an {@code Either}.
   */
  @Deprecated(since = "0.5.0", forRemoval = true)
  Either<Throwable, R> resultEither();

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
   * all tasks fail, the operation fails with the exception from one of them.
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
   * it succeeded or failed. As soon as one subtask completes, the others are cancelled.
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

  /**
   * Creates a joiner over {@link Either}-valued subtasks where the first {@code Right} wins.
   *
   * <p>The first subtask to complete with a {@code Right} supplies the result and the remaining
   * subtasks are cancelled — a winning {@code Right} outranks everything, including defects raised
   * by other subtasks. A subtask completing with a {@code Left} does not abort the race — its error
   * is collected, and only if every subtask completes {@code Left} does the join yield {@code Left}
   * of all collected errors (in fork order). A subtask that <em>throws</em> is a defect: when no
   * winner emerges, the join rethrows it rather than typing it.
   *
   * @param <E> the typed error carried by each subtask's {@code Left}
   * @param <T> the success value carried by each subtask's {@code Right}
   * @return a joiner racing to the first {@code Right} while collecting typed failures
   */
  static <E, T> ScopeJoiner<Either<E, T>, Either<List<E>, T>> firstSuccessEither() {
    return new FirstSuccessEitherJoiner<>();
  }
}

// ==================== Implementation Classes ====================

/**
 * Builds a new {@link StructuredTaskScope.Joiner} each time one is asked for, and remembers the
 * latest for the deprecated {@link ScopeJoiner#resultEither()}.
 *
 * <p>It starts with one built in advance, so {@code resultEither()} reads a {@code Joiner} that no
 * scope has used until the first is handed out, as it always has.
 *
 * @param <T> the type of values produced by subtasks
 * @param <R> the type of the final result after joining
 */
@SuppressWarnings("preview")
final class JoinerSource<T, R> {

  private final Supplier<StructuredTaskScope.Joiner<T, R>> build;
  private final AtomicReference<StructuredTaskScope.Joiner<T, R>> latest;

  JoinerSource(Supplier<StructuredTaskScope.Joiner<T, R>> build) {
    this.build = build;
    this.latest = new AtomicReference<>(build.get());
  }

  StructuredTaskScope.Joiner<T, R> next() {
    StructuredTaskScope.Joiner<T, R> joiner = build.get();
    latest.set(joiner);
    return joiner;
  }

  Either<Throwable, R> latestResult() {
    try {
      return Either.right(latest.get().result());
    } catch (Throwable t) {
      return Either.left(t);
    }
  }
}

/**
 * Joiner where the first {@code Right} wins and cancels the rest (outranking even defects raised by
 * other subtasks); {@code Left}s are collected and only surface (in fork order) when no subtask
 * ever produces a {@code Right}. With no winner, a thrown exception is a defect and is rethrown
 * rather than typed.
 *
 * @param <E> the typed error type
 * @param <T> the success type
 */
@SuppressWarnings("preview")
final class FirstSuccessEitherJoiner<E, T>
    implements ScopeJoiner<Either<E, T>, Either<List<E>, T>> {

  private final JoinerSource<Either<E, T>, Either<List<E>, T>> joiners =
      new JoinerSource<>(FirstSuccessEitherJoiner::newJoiner);

  @Override
  public StructuredTaskScope.Joiner<Either<E, T>, Either<List<E>, T>> joiner() {
    return joiners.next();
  }

  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public Either<Throwable, Either<List<E>, T>> resultEither() {
    return joiners.latestResult();
  }

  private static <E, T> StructuredTaskScope.Joiner<Either<E, T>, Either<List<E>, T>> newJoiner() {
    List<StructuredTaskScope.Subtask<? extends Either<E, T>>> allSubtasks =
        Collections.synchronizedList(new ArrayList<>());
    AtomicReference<Either<E, T>> winner = new AtomicReference<>();

    return new StructuredTaskScope.Joiner<>() {
      @Override
      public boolean onFork(StructuredTaskScope.Subtask<? extends Either<E, T>> subtask) {
        allSubtasks.add(subtask);
        return false;
      }

      @Override
      public boolean onComplete(StructuredTaskScope.Subtask<? extends Either<E, T>> subtask) {
        // The first Right cancels the remaining subtasks
        return subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS
            && subtask.get().isRight()
            && winner.compareAndSet(null, subtask.get());
      }

      @Override
      public Either<List<E>, T> result() throws Throwable {
        Either<E, T> won = winner.get();
        if (won != null) {
          return Either.right(won.getRight());
        }
        // No winner: every subtask here ran to completion, so UNAVAILABLE (and a Right)
        // is impossible in this loop. The only cancellation path through this joiner is
        // a successful winner CAS (returned above); Scope applies timeouts at the VTask
        // layer, not as a scope deadline; and a raw StructuredTaskScope configured with
        // a timeout throws TimeoutException from join() without consulting result().
        List<E> errors = new ArrayList<>();
        // Iterating a synchronizedList requires holding its monitor; forking is already done
        // by the time result() runs, but this keeps the contract explicit.
        synchronized (allSubtasks) {
          for (StructuredTaskScope.Subtask<? extends Either<E, T>> subtask : allSubtasks) {
            if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
              throw subtask.exception(); // with no winner, a defect outranks the typed channel
            }
            errors.add(subtask.get().getLeft());
          }
        }
        return Either.left(List.copyOf(errors));
      }
    };
  }
}

/**
 * Joiner that waits for all subtasks to succeed.
 *
 * <p>Uses Java 25's built-in {@code Joiner.allSuccessfulOrThrow()} internally, a new one for each
 * scope, and converts the Stream result to a List.
 *
 * @param <T> the type of values produced by subtasks
 */
@SuppressWarnings("preview")
final class AllSucceedJoiner<T> implements ScopeJoiner<T, List<T>> {

  private final JoinerSource<T, List<T>> joiners = new JoinerSource<>(AllSucceedJoiner::newJoiner);

  @Override
  public StructuredTaskScope.Joiner<T, List<T>> joiner() {
    return joiners.next();
  }

  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public Either<Throwable, List<T>> resultEither() {
    return joiners.latestResult();
  }

  private static <T> StructuredTaskScope.Joiner<T, List<T>> newJoiner() {
    // Note: allSuccessfulOrThrow() returns Stream<Subtask<T>>, not Stream<T>
    StructuredTaskScope.Joiner<T, Stream<StructuredTaskScope.Subtask<T>>> builtIn =
        StructuredTaskScope.Joiner.allSuccessfulOrThrow();

    return new StructuredTaskScope.Joiner<>() {
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
}

/**
 * Joiner that returns the first successful result.
 *
 * @param <T> the type of values produced by subtasks
 */
@SuppressWarnings("preview")
final class AnySucceedJoiner<T> implements ScopeJoiner<T, T> {

  private final JoinerSource<T, T> joiners =
      new JoinerSource<>(StructuredTaskScope.Joiner::anySuccessfulResultOrThrow);

  @Override
  public StructuredTaskScope.Joiner<T, T> joiner() {
    return joiners.next();
  }

  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public Either<Throwable, T> resultEither() {
    return joiners.latestResult();
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

  private final JoinerSource<T, T> joiners = new JoinerSource<>(FirstCompleteJoiner::newJoiner);

  @Override
  public StructuredTaskScope.Joiner<T, T> joiner() {
    return joiners.next();
  }

  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public Either<Throwable, T> resultEither() {
    return joiners.latestResult();
  }

  private static <T> StructuredTaskScope.Joiner<T, T> newJoiner() {
    AtomicReference<StructuredTaskScope.Subtask<? extends T>> firstCompleted =
        new AtomicReference<>();

    return new StructuredTaskScope.Joiner<>() {
      @Override
      public boolean onComplete(StructuredTaskScope.Subtask<? extends T> subtask) {
        // The first to complete cancels the rest
        return firstCompleted.compareAndSet(null, subtask);
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

  private final JoinerSource<T, Validated<List<E>, List<T>>> joiners;

  AccumulatingJoiner(Function<Throwable, E> errorMapper) {
    this.joiners = new JoinerSource<>(() -> newJoiner(errorMapper));
  }

  @Override
  public StructuredTaskScope.Joiner<T, Validated<List<E>, List<T>>> joiner() {
    return joiners.next();
  }

  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public Either<Throwable, Validated<List<E>, List<T>>> resultEither() {
    return joiners.latestResult();
  }

  private static <E, T> StructuredTaskScope.Joiner<T, Validated<List<E>, List<T>>> newJoiner(
      Function<Throwable, E> errorMapper) {
    List<StructuredTaskScope.Subtask<? extends T>> allSubtasks =
        Collections.synchronizedList(new ArrayList<>());

    return new StructuredTaskScope.Joiner<>() {
      @Override
      public boolean onFork(StructuredTaskScope.Subtask<? extends T> subtask) {
        allSubtasks.add(subtask);
        return false;
      }

      @Override
      public Validated<List<E>, List<T>> result() {
        // This joiner never cancels, so join() calls result() once every subtask has completed
        List<E> errors = new ArrayList<>();
        List<T> successes = new ArrayList<>();

        // Iterating a synchronizedList requires holding its monitor; forking is already done
        // by the time result() runs, but this keeps the contract explicit.
        synchronized (allSubtasks) {
          for (StructuredTaskScope.Subtask<? extends T> subtask : allSubtasks) {
            if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
              errors.add(errorMapper.apply(subtask.exception()));
            } else {
              successes.add(subtask.get());
            }
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
}
