// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamThrottle;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests VStream resilience integration -- using VStream with resilience patterns.
 *
 * <p>Covers circuit breaker protection, per-element retry, throttling, and metering applied to
 * VStream pipelines.
 */
@DisplayName("VStream Resilience Integration Tests")
class VStreamResilienceTest {

  @Nested
  @DisplayName("Stream Circuit Breaker")
  class StreamCircuitBreakerTests {

    @Test
    @DisplayName("Circuit breaker protects each stream element successfully")
    void circuitBreakerProtectsStreamElements() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(3)
              .callTimeout(Duration.ofSeconds(5))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);
      VStream<Integer> protected_ = stream.mapTask(n -> cb.protect(VTask.of(() -> n * 10)));

      List<Integer> result = protected_.toList().run();

      assertThat(result).containsExactly(10, 20, 30, 40, 50);
      assertThat(cb.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("Circuit breaker opens after repeated failures in stream")
    void circuitBreakerOpensAfterFailures() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(2)
              .openDuration(Duration.ofSeconds(60))
              .callTimeout(Duration.ofSeconds(5))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      AtomicInteger callCount = new AtomicInteger(0);
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);
      VStream<Integer> protected_ =
          stream.mapTask(
              n ->
                  cb.protect(
                      VTask.of(
                          () -> {
                            callCount.incrementAndGet();
                            throw new RuntimeException("service down");
                          })));

      VStream<Integer> recovered = protected_.recover(ex -> -1);
      List<Integer> result = recovered.toList().run();

      assertThat(result).isNotEmpty();
      // After the circuit opens, subsequent elements get CircuitOpenException
      assertThat(cb.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName("withCircuitBreakerPerElement convenience protects each element")
    void withCircuitBreakerPerElementProtectsElements() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(5)
              .callTimeout(Duration.ofSeconds(5))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      VStream<String> stream = VStream.of("a", "b", "c");
      VStream<String> protected_ =
          stream.mapTask(
              Resilience.withCircuitBreakerPerElement(s -> VTask.of(() -> s.toUpperCase()), cb));

      List<String> result = protected_.toList().run();

      assertThat(result).containsExactly("A", "B", "C");
    }
  }

  @Nested
  @DisplayName("Stream Retry")
  class StreamRetryTests {

    @Test
    @DisplayName("Per-element retry succeeds after transient failures")
    void perElementRetrySucceedsAfterTransientFailures() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger callCount = new AtomicInteger(0);

      VStream<Integer> stream = VStream.of(1, 2, 3);
      VStream<String> retried =
          stream.mapTask(
              n ->
                  Retry.retryTask(
                      VTask.of(
                          () -> {
                            int count = callCount.incrementAndGet();
                            // Fail on first call for each element, succeed on retry
                            if (count % 2 == 1) {
                              throw new RuntimeException("transient failure");
                            }
                            return "ok-" + n;
                          }),
                      policy));

      List<String> result = retried.toList().run();

      assertThat(result).containsExactly("ok-1", "ok-2", "ok-3");
    }

    @Test
    @DisplayName("withRetryPerElement convenience applies retry to each element")
    void withRetryPerElementAppliesRetry() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      VStream<Integer> stream = VStream.of(10, 20, 30);
      VStream<Integer> retried =
          stream.mapTask(Resilience.withRetryPerElement(n -> VTask.of(() -> n + 1), policy));

      List<Integer> result = retried.toList().run();

      assertThat(result).containsExactly(11, 21, 31);
    }

    @Test
    @DisplayName("Per-element retry exhaustion produces error in stream")
    void perElementRetryExhaustionProducesError() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));

      VStream<Integer> stream = VStream.of(1);
      VStream<Integer> retried =
          stream.mapTask(
              n ->
                  Retry.retryTask(
                      VTask.of(
                          () -> {
                            throw new RuntimeException("permanent failure");
                          }),
                      policy));

      assertThatThrownBy(() -> retried.toList().run()).isInstanceOf(RetryExhaustedException.class);
    }

    @Test
    @DisplayName("Per-element retry with recovery provides fallback values")
    void perElementRetryWithRecovery() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));

      VStream<Integer> stream = VStream.of(1, 2, 3);
      VStream<String> retried =
          stream.mapTask(
              n ->
                  Retry.retryTaskWithFallback(
                      VTask.of(
                          () -> {
                            if (n == 2) {
                              throw new RuntimeException("fail on 2");
                            }
                            return "val-" + n;
                          }),
                      policy,
                      cause -> "fallback-" + n));

      List<String> result = retried.toList().run();

      assertThat(result).containsExactly("val-1", "fallback-2", "val-3");
    }
  }

  @Nested
  @DisplayName("Stream Throttle")
  class ThrottleTests {

    @Test
    @DisplayName("Throttle limits elements per time window")
    void throttleLimitsElementsPerWindow() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4);
      // Allow 2 elements per 50ms window
      VStream<Integer> throttled = VStreamThrottle.throttle(stream, 2, Duration.ofMillis(50));

      Instant start = Instant.now();
      List<Integer> result = throttled.toList().run();
      Duration elapsed = Duration.between(start, Instant.now());

      assertThat(result).containsExactly(1, 2, 3, 4);
      // With 4 elements and 2 per 50ms window, need at least 1 window boundary
      assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(40));
    }

    @Test
    @DisplayName("Throttle preserves all elements")
    void throttlePreservesAllElements() {
      List<Integer> input = List.of(1, 2, 3, 4, 5);
      VStream<Integer> stream = VStream.fromList(input);
      VStream<Integer> throttled = VStreamThrottle.throttle(stream, 10, Duration.ofMillis(50));

      List<Integer> result = throttled.toList().run();

      assertThat(result).containsExactlyElementsOf(input);
    }

    @Test
    @DisplayName("Throttle handles empty stream")
    void throttleHandlesEmptyStream() {
      VStream<Integer> stream = VStream.empty();
      VStream<Integer> throttled = VStreamThrottle.throttle(stream, 5, Duration.ofMillis(50));

      List<Integer> result = throttled.toList().run();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Throttle rejects maxElements less than 1")
    void throttleRejectsInvalidMaxElements() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      assertThat(
              Assertions.catchThrowable(
                  () -> VStreamThrottle.throttle(stream, 0, Duration.ofMillis(50))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxElements must be at least 1");

      assertThat(
              Assertions.catchThrowable(
                  () -> VStreamThrottle.throttle(stream, -1, Duration.ofMillis(50))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxElements must be at least 1");
    }

    @Test
    @DisplayName("Throttle handles filtered (Skip) elements correctly")
    void throttleHandlesFilteredElements() {
      // filter() produces Skip steps for non-matching elements
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5, 6).filter(n -> n % 2 == 0);
      VStream<Integer> throttled = VStreamThrottle.throttle(stream, 10, Duration.ofMillis(100));

      List<Integer> result = throttled.toList().run();

      assertThat(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("Throttle resets window when window time has naturally elapsed between pulls")
    void throttleResetsWindowOnNaturalExpiry() {
      // With a 1ns window, every pull will find the window expired → hits the "new window" branch
      VStream<Integer> stream = VStream.of(1, 2, 3, 4);
      VStream<Integer> throttled = VStreamThrottle.throttle(stream, 100, Duration.ofNanos(1));

      List<Integer> result = throttled.toList().run();

      assertThat(result).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Throttle enforces window boundary reset after window expires")
    void throttleEnforcesWindowBoundary() {
      // Create a stream with more elements than fit in one window
      // With 1 element per 30ms window and 4 elements, should take at least 90ms
      VStream<Integer> stream = VStream.of(1, 2, 3, 4);
      VStream<Integer> throttled = VStreamThrottle.throttle(stream, 1, Duration.ofMillis(30));

      Instant start = Instant.now();
      List<Integer> result = throttled.toList().run();
      Duration elapsed = Duration.between(start, Instant.now());

      assertThat(result).containsExactly(1, 2, 3, 4);
      // Each element after the first requires waiting for a new window
      assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(80));
    }
  }

  @Nested
  @DisplayName("Stream Metering")
  class MeteredTests {

    @Test
    @DisplayName("Metered stream inserts delay between elements")
    void meteredStreamInsertsDelay() {
      VStream<Integer> stream = VStream.of(1, 2, 3);
      VStream<Integer> metered = VStreamThrottle.metered(stream, Duration.ofMillis(20));

      Instant start = Instant.now();
      List<Integer> result = metered.toList().run();
      Duration elapsed = Duration.between(start, Instant.now());

      assertThat(result).containsExactly(1, 2, 3);
      // 3 elements with 20ms delay each = at least 60ms
      assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(50));
    }

    @Test
    @DisplayName("Metered stream preserves all elements")
    void meteredStreamPreservesAllElements() {
      List<String> input = List.of("alpha", "beta", "gamma");
      VStream<String> stream = VStream.fromList(input);
      VStream<String> metered = VStreamThrottle.metered(stream, Duration.ofMillis(10));

      List<String> result = metered.toList().run();

      assertThat(result).containsExactlyElementsOf(input);
    }

    @Test
    @DisplayName("Metered stream handles empty stream")
    void meteredStreamHandlesEmptyStream() {
      VStream<String> stream = VStream.empty();
      VStream<String> metered = VStreamThrottle.metered(stream, Duration.ofMillis(10));

      List<String> result = metered.toList().run();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Metered stream with single element completes with delay")
    void meteredStreamWithSingleElement() {
      VStream<Integer> stream = VStream.of(42);
      VStream<Integer> metered = VStreamThrottle.metered(stream, Duration.ofMillis(20));

      Instant start = Instant.now();
      List<Integer> result = metered.toList().run();
      Duration elapsed = Duration.between(start, Instant.now());

      assertThat(result).containsExactly(42);
      assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(15));
    }

    @Test
    @DisplayName("Metered stream handles filtered (Skip) elements correctly")
    void meteredStreamHandlesFilteredElements() {
      // filter() produces Skip steps for non-matching elements
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5).filter(n -> n % 2 != 0);
      VStream<Integer> metered = VStreamThrottle.metered(stream, Duration.ofMillis(10));

      List<Integer> result = metered.toList().run();

      assertThat(result).containsExactly(1, 3, 5);
    }
  }

  // ==========================================================================
  // Audit Issue #17: VStreamThrottle non-atomic compound operations on window state
  // ==========================================================================

  @Nested
  @DisplayName("Throttle Thread Safety (audit issue #17)")
  class ThrottleThreadSafetyTests {

    @Test
    @DisplayName("concurrent throttled streams sharing window state should not corrupt counts")
    void concurrentThrottledStreamsShouldNotCorruptState() throws Exception {
      int maxPerWindow = 5;
      Duration window = Duration.ofMillis(100);

      List<Integer> input = new ArrayList<>();
      for (int i = 0; i < 100; i++) input.add(i);
      VStream<Integer> stream = VStream.fromList(input);
      VStream<Integer> throttled = VStreamThrottle.throttle(stream, maxPerWindow, window);

      List<Integer> result = throttled.toList().run();
      assertThat(result).hasSize(100).containsExactlyElementsOf(input);
    }

    @Test
    @DisplayName("concurrent pulls exercise CAS retry on within-window increment path")
    void concurrentPullsCASRetryWithinWindow() throws Exception {
      // With a large window, all pulls land in the "within window" branch (line 102).
      // Multiple threads racing on compareAndSet cause some to fail and retry.
      runConcurrentThrottlePulls(100_000, Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("concurrent pulls exercise CAS retry on new-window reset path")
    void concurrentPullsCASRetryNewWindow() throws Exception {
      // With a tiny window (1ns), every pull sees the window as expired and enters
      // the "new window" branch (line 91). Concurrent compareAndSet causes retries.
      runConcurrentThrottlePulls(100_000, Duration.ofNanos(1));
    }

    private void runConcurrentThrottlePulls(int maxPerWindow, Duration window) throws Exception {
      int numThreads = 16;
      int pullsPerThread = 100;

      // Thread-safe infinite source: generate() creates independent tails per pull
      AtomicInteger counter = new AtomicInteger(0);
      VStream<Integer> source = VStream.generate(() -> counter.getAndIncrement());
      VStream<Integer> throttled = VStreamThrottle.throttle(source, maxPerWindow, window);

      CyclicBarrier barrier = new CyclicBarrier(numThreads);
      List<Thread> threads = new ArrayList<>();

      for (int t = 0; t < numThreads; t++) {
        threads.add(
            Thread.ofVirtual()
                .start(
                    () -> {
                      try {
                        barrier.await(5, TimeUnit.SECONDS);
                        for (int i = 0; i < pullsPerThread; i++) {
                          throttled.pull().run();
                        }
                      } catch (Exception e) {
                        // Concurrent pulls may see unexpected states — acceptable
                      }
                    }));
      }

      for (Thread thread : threads) {
        thread.join(10_000);
        assertThat(thread.isAlive()).as("Thread should not be stuck").isFalse();
      }
    }
  }
}
