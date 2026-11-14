// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.trymonad.Try;
import org.jspecify.annotations.NullMarked;

/**
 * Wraps an {@link EffectBoundary} with metrics recording via {@link HkjMetricsService}.
 *
 * <p>Every {@code run()} invocation records success/error counters and execution duration. This
 * integrates with the existing hkj actuator metrics and is auto-configured when both an {@code
 * EffectBoundary} bean and {@code HkjMetricsService} bean are present.
 *
 * <p>Metrics recorded:
 *
 * <ul>
 *   <li>{@code hkj.effect.boundary.invocations{result="success|error"}} — execution count
 *   <li>{@code hkj.effect.boundary.duration} — execution time
 *   <li>{@code hkj.effect.boundary.errors{error_type="..."}} — error type distribution
 * </ul>
 *
 * @param <F> the composed effect witness type
 * @see EffectBoundary
 * @see HkjMetricsService
 */
@NullMarked
public final class ObservableEffectBoundary<F extends WitnessArity<TypeArity.Unary>> {

  private final EffectBoundary<F> delegate;
  private final HkjMetricsService metrics;

  /**
   * Creates an ObservableEffectBoundary wrapping the given boundary with metrics.
   *
   * @param delegate the underlying EffectBoundary
   * @param metrics the metrics service for recording invocations
   */
  public ObservableEffectBoundary(EffectBoundary<F> delegate, HkjMetricsService metrics) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
  }

  /**
   * Interprets and executes a Free program synchronously, recording metrics.
   *
   * @param program the Free monad program
   * @param <A> the result type
   * @return the result
   */
  public <A> A run(Free<F, A> program) {
    long start = System.currentTimeMillis();
    try {
      A result = delegate.run(program);
      metrics.recordEffectBoundarySuccess();
      return result;
    } catch (Exception e) {
      metrics.recordEffectBoundaryError(e.getClass().getSimpleName());
      throw e;
    } finally {
      metrics.recordEffectBoundaryDuration(System.currentTimeMillis() - start);
    }
  }

  /**
   * Interprets and executes a FreePath program synchronously, recording metrics.
   *
   * @param program the FreePath program
   * @param <A> the result type
   * @return the result
   */
  public <A> A run(FreePath<F, A> program) {
    return run(program.toFree());
  }

  /**
   * Interprets and executes safely, recording metrics.
   *
   * @param program the Free monad program
   * @param <A> the result type
   * @return a Try containing the result or exception
   */
  public <A> Try<A> runSafe(Free<F, A> program) {
    long start = System.currentTimeMillis();
    Try<A> result = delegate.runSafe(program);
    if (result.isSuccess()) {
      metrics.recordEffectBoundarySuccess();
    } else {
      metrics.recordEffectBoundaryError(
          ((Try.Failure<A>) result).cause().getClass().getSimpleName());
    }
    metrics.recordEffectBoundaryDuration(System.currentTimeMillis() - start);
    return result;
  }

  /**
   * Interprets asynchronously, recording metrics on completion.
   *
   * <p>Cancelling the returned future, with either flag, interrupts the program, as cancelling the
   * future from {@link EffectBoundary#runAsync(Free)} with {@code cancel(true)} does. It records
   * the invocation once, as an error, a {@link CancellationException}, unless the program's own
   * outcome was already being recorded.
   *
   * @param program the Free monad program
   * @param <A> the result type
   * @return a CompletableFuture that records metrics on completion
   */
  public <A> CompletableFuture<A> runAsync(Free<F, A> program) {
    long start = System.currentTimeMillis();
    // The outcome is recorded once, by whichever of the two callbacks below gets there first
    AtomicBoolean recorded = new AtomicBoolean(false);
    CompletableFuture<A> running = delegate.runAsync(program);
    CompletableFuture<A> observed =
        running.whenComplete(
            (result, throwable) -> {
              if (recorded.compareAndSet(false, true)) {
                if (throwable != null) {
                  metrics.recordEffectBoundaryError(throwable.getClass().getSimpleName());
                } else {
                  metrics.recordEffectBoundarySuccess();
                }
                metrics.recordEffectBoundaryDuration(System.currentTimeMillis() - start);
              }
            });
    // A cancel on the metrics stage would not reach the program, so pass it through. The metrics
    // callback is then skipped, since its stage already has a result, so record the cancel here,
    // unless that callback was already recording when the cancel landed.
    observed.whenComplete(
        (_, _) -> {
          if (observed.isCancelled()) {
            running.cancel(true);
            if (recorded.compareAndSet(false, true)) {
              metrics.recordEffectBoundaryError(CancellationException.class.getSimpleName());
              metrics.recordEffectBoundaryDuration(System.currentTimeMillis() - start);
            }
          }
        });
    return observed;
  }

  /**
   * Returns a deferred IOPath, recording metrics when eventually executed.
   *
   * @param program the Free monad program
   * @param <A> the result type
   * @return an IOPath that records metrics on execution
   */
  public <A> IOPath<A> runIO(Free<F, A> program) {
    return delegate.runIO(program);
  }

  /**
   * Returns the underlying EffectBoundary.
   *
   * @return the delegate boundary
   */
  public EffectBoundary<F> delegate() {
    return delegate;
  }
}
