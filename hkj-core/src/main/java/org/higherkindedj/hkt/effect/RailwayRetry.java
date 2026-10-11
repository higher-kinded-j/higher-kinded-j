// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryExhaustedException;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * Railway-aware retry lowering shared by {@link EitherPath} and {@link VResultPath}.
 *
 * <p>This lives in {@code hkt.effect} rather than {@code hkt.resilience} deliberately: the
 * architecture rules keep the resilience primitives agnostic of specific carriers such as {@link
 * Either}, so the typed-error awareness is layered here, on top of the single retry loop behind
 * {@link Retry#execute} and {@link Retry#retryTask}. A retry-selected {@code Left} is lowered into
 * the exception channel through the stackless {@link LeftRetrySignal} and restored on the way out -
 * one implementation, several entry points.
 */
final class RailwayRetry {

  private RailwayRetry() {}

  /**
   * Executes a typed-error operation with railway-aware retry: a thrown exception retries per the
   * policy's predicate, and a {@link Either#left Left} retries only when {@code retryOn} selects
   * it. A {@code Left} the predicate does not select is a business decision, not a fault - it is
   * returned immediately, never retried. Every {@code Right} returns immediately.
   *
   * <p>When the attempts are exhausted while retrying a selected {@code Left}, the <em>last</em>
   * {@code Left} is returned - typed errors stay on the typed channel rather than becoming a thrown
   * {@link RetryExhaustedException}. Exhaustion on a thrown exception still throws, exactly as
   * {@link Retry#execute} does. If the thread is interrupted between typed retries, the last {@code
   * Left} is likewise returned, with the thread's interrupt status set.
   *
   * <p>Retry listeners registered via {@link RetryPolicy#onRetry} fire for typed retries too; the
   * event's exception is an internal signal whose message names the selected error.
   */
  static <E, A> Either<E, A> executeEither(
      RetryPolicy policy, Predicate<? super E> retryOn, Supplier<Either<E, A>> supplier) {
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(retryOn, "retryOn must not be null");
    Objects.requireNonNull(supplier, "supplier must not be null");
    try {
      return Retry.execute(signalling(policy), () -> signalIfSelected(supplier.get(), retryOn));
    } catch (RetryExhaustedException exhausted) {
      return lastLeft(exhausted);
    } catch (LeftRetrySignal escaped) {
      // A retry listener may rethrow event.lastException() (a plausible abort idiom); the signal
      // must never reach user code, so restore the typed Left it was carrying.
      return escaped.outcome();
    }
  }

  /**
   * Returns a lazy {@link VTask} applying {@link #executeEither}'s rules to the given typed-error
   * task - the railway-aware sibling of {@link Retry#retryTask}. It retries through {@code
   * retryTask}, so the task's exception reaches the policy's predicate as thrown, and an interrupt
   * stops the retry as {@link Retry} describes.
   */
  static <E, A> VTask<Either<E, A>> retryTaskEither(
      VTask<Either<E, A>> task, Predicate<? super E> retryOn, RetryPolicy policy) {
    Objects.requireNonNull(task, "task must not be null");
    Objects.requireNonNull(retryOn, "retryOn must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    VTask<Either<E, A>> retried =
        Retry.retryTask(() -> signalIfSelected(task.execute(), retryOn), signalling(policy));
    return () -> {
      try {
        return retried.execute();
      } catch (RetryExhaustedException exhausted) {
        return lastLeft(exhausted);
      } catch (LeftRetrySignal escaped) {
        // As in executeEither: a listener that rethrows the signal gets its Left back
        return escaped.outcome();
      }
    };
  }

  // The policy, extended to retry the signal that carries a selected Left
  private static RetryPolicy signalling(RetryPolicy policy) {
    return policy.retryIf(t -> t instanceof LeftRetrySignal || policy.shouldRetry(t));
  }

  private static <E, A> Either<E, A> signalIfSelected(
      @Nullable Either<E, A> outcome, Predicate<? super E> retryOn) {
    Either<E, A> returned = Objects.requireNonNull(outcome, "supplier returned null");
    if (returned.isLeft() && retryOn.test(returned.getLeft())) {
      throw new LeftRetrySignal(returned);
    }
    return returned;
  }

  // The last selected Left when the retry ran out on one, else the exhaustion itself
  private static <E, A> Either<E, A> lastLeft(RetryExhaustedException exhausted) {
    if (exhausted.getCause() instanceof LeftRetrySignal signal) {
      return signal.outcome();
    }
    throw exhausted;
  }

  /**
   * Stackless internal signal lowering a retry-selected {@code Left} into the exception channel so
   * the single retry loop in {@link Retry} serves both channels. Never escapes {@link
   * #executeEither} or {@link #retryTaskEither} unwrapped.
   */
  static final class LeftRetrySignal extends RuntimeException {
    private final Either<?, ?> outcome;

    LeftRetrySignal(Either<?, ?> outcome) {
      super(null, null, false, false);
      this.outcome = outcome;
    }

    @SuppressWarnings("unchecked") // the signal carries the Either it was constructed with
    private <E, A> Either<E, A> outcome() {
      return (Either<E, A>) outcome;
    }

    @Override
    public String getMessage() {
      // Computed lazily so the error's toString runs only when a listener or log asks for it,
      // never on the retry hot path.
      return "typed error selected for retry: " + outcome.getLeft();
    }
  }
}
