// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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
   * window resets. The window is measured from the first emission in each window.
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

    AtomicReference<WindowState> state =
        new AtomicReference<>(new WindowState(System.nanoTime(), 0));
    long windowNanos = window.toNanos();

    return throttleWithState(stream, maxElements, windowNanos, state);
  }

  /** Immutable snapshot of the throttle window state, updated atomically via CAS. */
  private record WindowState(long windowStart, long emitted) {}

  private static <A> VStream<A> throttleWithState(
      VStream<A> stream, int maxElements, long windowNanos, AtomicReference<WindowState> state) {
    return new VStream<>() {
      @Override
      public VTask<Step<A>> pull() {
        return () -> {
          Step<A> step = stream.pull().run();
          if (step instanceof Step.Done) {
            return step;
          }
          if (step instanceof Step.Skip<A> skip) {
            return new Step.Skip<>(throttleWithState(skip.tail(), maxElements, windowNanos, state));
          }

          // Rate limit emissions — atomic read-check-update via CAS
          Step.Emit<A> emit = (Step.Emit<A>) step;
          while (true) {
            WindowState current = state.get();
            long now = System.nanoTime();

            if (now - current.windowStart() >= windowNanos) {
              // New window — reset and count this emission
              WindowState next = new WindowState(now, 1);
              if (state.compareAndSet(current, next)) {
                break;
              }
            } else if (current.emitted() >= maxElements) {
              // Window limit reached — sleep until window expires, then retry CAS
              long sleepNanos = windowNanos - (now - current.windowStart());
              Thread.sleep(Duration.ofNanos(sleepNanos));
              // After sleep, loop back to re-read state and start a new window
            } else {
              // Within window and under limit — increment emission count
              WindowState next = new WindowState(current.windowStart(), current.emitted() + 1);
              if (state.compareAndSet(current, next)) {
                break;
              }
            }
          }

          return new Step.Emit<>(
              emit.value(), throttleWithState(emit.tail(), maxElements, windowNanos, state));
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

    return new VStream<>() {
      @Override
      public VTask<Step<A>> pull() {
        return () -> {
          Step<A> step = stream.pull().run();
          if (step instanceof Step.Done) {
            return step;
          }
          if (step instanceof Step.Skip<A> skip) {
            return new Step.Skip<>(metered(skip.tail(), interval));
          }

          Step.Emit<A> emit = (Step.Emit<A>) step;
          Thread.sleep(interval);
          return new Step.Emit<>(emit.value(), metered(emit.tail(), interval));
        };
      }
    };
  }
}
