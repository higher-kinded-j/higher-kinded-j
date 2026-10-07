// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.time.Duration;
import java.util.Objects;
import org.higherkindedj.hkt.vstream.VStream.Step;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Rate-limiting operators for {@link VStream}.
 *
 * <p><b>Distinction from VStreamPar:</b> {@code VStreamPar} limits how many elements are
 * <em>in-flight</em> concurrently. {@code VStreamThrottle} limits how many elements are <em>emitted
 * per time window</em>. Both can be combined: a stream can have bounded concurrency (VStreamPar)
 * and be rate-limited (VStreamThrottle) simultaneously.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * // Emit at most 10 elements per second
 * VStream<Response> throttled = VStreamThrottle.throttle(requests, 10, Duration.ofSeconds(1));
 *
 * // Add 50ms between each element
 * VStream<Response> metered = VStreamThrottle.metered(requests, Duration.ofMillis(50));
 * }</pre>
 *
 * @see VStream
 * @see VStreamPar
 */
public final class VStreamThrottle {

  private VStreamThrottle() {
    // Utility class
  }

  /**
   * Returns a stream that emits at most {@code maxElements} per {@code window}.
   *
   * <p>When the limit for the current window is reached, pulling the next element blocks until the
   * window resets. The window is measured from the first emission in each window. Each consumption
   * of the returned stream keeps its own window.
   *
   * @param stream the source stream; must not be null
   * @param maxElements the maximum number of elements per window; must be at least 1
   * @param window the time window duration; must not be null
   * @param <A> the element type
   * @return a rate-limited stream
   * @throws NullPointerException if stream or window is null
   * @throws IllegalArgumentException if maxElements is less than 1
   */
  public static <A> VStream<A> throttle(VStream<A> stream, int maxElements, Duration window) {
    Objects.requireNonNull(stream, "stream must not be null");
    Objects.requireNonNull(window, "window must not be null");
    if (maxElements < 1) {
      throw new IllegalArgumentException("maxElements must be at least 1, got: " + maxElements);
    }

    // Every consumption starts from this state, in which no window is open yet
    return throttleWithState(stream, maxElements, window.toNanos(), new WindowState(0, 0));
  }

  /**
   * The throttle window as one consumption has reached it. Each emission passes the next window to
   * its tail, so the window belongs to the consumption that pulled it. While nothing has been
   * emitted, no window is open, and the next emission opens one.
   */
  private record WindowState(long windowStart, long emitted) {}

  private static <A> VStream<A> throttleWithState(
      VStream<A> stream, int maxElements, long windowNanos, WindowState state) {
    return new DerivedStream<A, A>(stream) {
      @Override
      public VTask<Step<A>> pull() {
        return () -> {
          Step<A> step = upstream.pull().run();
          if (step instanceof Step.Done) {
            return step;
          }
          if (step instanceof Step.Skip<A> skip) {
            return new Step.Skip<>(throttleWithState(skip.tail(), maxElements, windowNanos, state));
          }

          Step.Emit<A> emit = (Step.Emit<A>) step;
          long now = System.nanoTime();
          WindowState next;
          if (state.emitted() == 0 || now - state.windowStart() >= windowNanos) {
            // A new window, opened by this emission
            next = new WindowState(now, 1);
          } else if (state.emitted() >= maxElements) {
            // The window is full: wait for it to end, then start the next with this emission
            waitHolding(Duration.ofNanos(windowNanos - (now - state.windowStart())), emit.tail());
            next = new WindowState(System.nanoTime(), 1);
          } else {
            // Within the window and under the limit
            next = new WindowState(state.windowStart(), state.emitted() + 1);
          }

          return new Step.Emit<>(
              emit.value(), throttleWithState(emit.tail(), maxElements, windowNanos, next));
        };
      }
    };
  }

  /**
   * Returns a stream that inserts a fixed delay between element emissions.
   *
   * <p>After each element is pulled from the source, the returned stream sleeps for the given
   * interval before making the element available.
   *
   * @param stream the source stream; must not be null
   * @param interval the delay between elements; must not be null
   * @param <A> the element type
   * @return a metered stream
   * @throws NullPointerException if stream or interval is null
   */
  public static <A> VStream<A> metered(VStream<A> stream, Duration interval) {
    Objects.requireNonNull(stream, "stream must not be null");
    Objects.requireNonNull(interval, "interval must not be null");

    return new DerivedStream<A, A>(stream) {
      @Override
      public VTask<Step<A>> pull() {
        return () -> {
          Step<A> step = upstream.pull().run();
          if (step instanceof Step.Done) {
            return step;
          }
          if (step instanceof Step.Skip<A> skip) {
            return new Step.Skip<>(metered(skip.tail(), interval));
          }

          Step.Emit<A> emit = (Step.Emit<A>) step;
          waitHolding(interval, emit.tail());
          return new Step.Emit<>(emit.value(), metered(emit.tail(), interval));
        };
      }
    };
  }

  /**
   * Waits before passing on an element. If the wait is interrupted, the rest of the stream, which
   * only this step holds, is closed, so its finalisers run.
   */
  private static void waitHolding(Duration wait, VStream<?> rest) throws InterruptedException {
    try {
      Thread.sleep(wait);
    } catch (InterruptedException interrupted) {
      Closing.closeAfterFailure(rest, interrupted);
      throw interrupted;
    }
  }
}
