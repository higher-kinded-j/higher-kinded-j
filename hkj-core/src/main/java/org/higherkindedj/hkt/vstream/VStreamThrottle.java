// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
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

    AtomicLong windowStart = new AtomicLong(System.nanoTime());
    AtomicLong emittedInWindow = new AtomicLong(0);
    long windowNanos = window.toNanos();

    return throttleWithState(stream, maxElements, windowNanos, windowStart, emittedInWindow);
  }

  private static <A> VStream<A> throttleWithState(
      VStream<A> stream,
      int maxElements,
      long windowNanos,
      AtomicLong windowStart,
      AtomicLong emittedInWindow) {
    return new VStream<>() {
      @Override
      public VTask<Step<A>> pull() {
        return () -> {
          Step<A> step = stream.pull().run();
          if (step instanceof Step.Done) {
            return step;
          }
          if (step instanceof Step.Skip<A> skip) {
            return new Step.Skip<>(
                throttleWithState(
                    skip.tail(), maxElements, windowNanos, windowStart, emittedInWindow));
          }

          // Rate limit emissions
          Step.Emit<A> emit = (Step.Emit<A>) step;
          long now = System.nanoTime();
          long start = windowStart.get();

          if (now - start >= windowNanos) {
            // New window
            windowStart.set(now);
            emittedInWindow.set(1);
          } else if (emittedInWindow.get() >= maxElements) {
            // Window limit reached — wait for next window;
            // sleepNanos is always positive here since (now - start) < windowNanos
            Thread.sleep(Duration.ofNanos(windowNanos - (now - start)));
            windowStart.set(System.nanoTime());
            emittedInWindow.set(1);
          } else {
            emittedInWindow.incrementAndGet();
          }

          return new Step.Emit<>(
              emit.value(),
              throttleWithState(
                  emit.tail(), maxElements, windowNanos, windowStart, emittedInWindow));
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
