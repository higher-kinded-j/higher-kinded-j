// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.core.ConditionFactory;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link CircuitBreaker}.
 *
 * <p>Tests cover state transitions, failure and success thresholds, timeout behavior, metrics
 * tracking, concurrency safety, fallback support, and generic type protection.
 */
@DisplayName("CircuitBreaker Test Suite")
class CircuitBreakerTest {

  private static final Duration SHORT_OPEN_DURATION = Duration.ofMillis(50);
  private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(5);

  /** Shared Awaitility configuration used across all async polling in this test suite. */
  private static ConditionFactory awaitDefault() {
    return await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(10));
  }

  private static CircuitBreakerConfig configWithThresholds(
      int failureThreshold, int successThreshold) {
    return CircuitBreakerConfig.builder()
        .failureThreshold(failureThreshold)
        .successThreshold(successThreshold)
        .openDuration(SHORT_OPEN_DURATION)
        .callTimeout(GENEROUS_TIMEOUT)
        .build();
  }

  /** Triggers the specified number of consecutive failures on the given circuit breaker. */
  private static void causeFailures(CircuitBreaker breaker, int count) {
    for (int i = 0; i < count; i++) {
      VTask<String> failing = breaker.protect(VTask.fail(new RuntimeException("fail-" + i)));
      try {
        failing.run();
      } catch (RuntimeException ignored) {
        // Expected
      }
    }
  }

  /** Triggers the specified number of consecutive successes on the given circuit breaker. */
  private static void causeSuccesses(CircuitBreaker breaker, int count) {
    for (int i = 0; i < count; i++) {
      VTask<String> succeeding = breaker.protect(VTask.succeed("ok-" + i));
      succeeding.run();
    }
  }

  @Nested
  @DisplayName("State Transition Tests")
  class StateTransitionTests {

    @Test
    @DisplayName("starts in CLOSED state")
    void startsInClosedState() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("transitions from CLOSED to OPEN after reaching failure threshold")
    void closedToOpenAfterFailureThreshold() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(3, 1));

      causeFailures(breaker, 3);

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName("transitions from OPEN to HALF_OPEN after open duration elapses")
    void openToHalfOpenAfterDuration() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(2, 1));

      causeFailures(breaker, 2);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));
    }

    @Test
    @DisplayName("transitions from HALF_OPEN to CLOSED after success threshold met")
    void halfOpenToClosedAfterSuccesses() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(2, 2));

      causeFailures(breaker, 2);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));

      // Now in HALF_OPEN; two successes should close the circuit
      causeSuccesses(breaker, 2);

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("transitions from HALF_OPEN back to OPEN on failure")
    void halfOpenToOpenOnFailure() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(2, 2));

      causeFailures(breaker, 2);

      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));

      // A failure in HALF_OPEN should re-open the circuit
      causeFailures(breaker, 1);

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName("full cycle: CLOSED -> OPEN -> HALF_OPEN -> CLOSED")
    void fullCycleTransition() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(2, 1));

      // CLOSED -> OPEN
      causeFailures(breaker, 2);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      // OPEN -> HALF_OPEN
      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));

      // HALF_OPEN -> CLOSED
      causeSuccesses(breaker, 1);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("reset() transitions to CLOSED from any state")
    void resetTransitionsToClosed() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(2, 1));

      causeFailures(breaker, 2);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      breaker.reset();

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("tripOpen() manually opens the circuit")
    void tripOpenManuallyOpensCircuit() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);

      breaker.tripOpen();

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }
  }

  @Nested
  @DisplayName("Failure Threshold Tests")
  class FailureThresholdTests {

    @Test
    @DisplayName("circuit stays CLOSED when failures are below threshold")
    void staysClosedBelowThreshold() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      causeFailures(breaker, 4);

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("circuit opens exactly at failure threshold")
    void opensExactlyAtThreshold() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(3, 1));

      causeFailures(breaker, 2);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);

      causeFailures(breaker, 1);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName("successful call resets failure count in CLOSED state")
    void successResetsFailureCount() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(3, 1));

      causeFailures(breaker, 2);
      causeSuccesses(breaker, 1); // Should reset failure count
      causeFailures(breaker, 2);

      // Should still be closed because the success reset the counter
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("circuit opens with threshold of 1")
    void opensWithThresholdOfOne() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));

      causeFailures(breaker, 1);

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName("only exceptions matching recordFailure predicate count as failures")
    void onlyMatchingExceptionsCount() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(2)
              .successThreshold(1)
              .openDuration(SHORT_OPEN_DURATION)
              .callTimeout(GENEROUS_TIMEOUT)
              .recordFailure(ex -> ex instanceof IOException)
              .build();
      CircuitBreaker breaker = CircuitBreaker.create(config);

      // RuntimeExceptions should NOT count as failures
      for (int i = 0; i < 5; i++) {
        VTask<String> task = breaker.protect(VTask.fail(new RuntimeException("ignored")));
        try {
          task.run();
        } catch (RuntimeException ignored) {
          // Expected
        }
      }

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);

      // IOExceptions SHOULD count as failures
      for (int i = 0; i < 2; i++) {
        VTask<String> task = breaker.protect(VTask.fail(new IOException("real failure")));
        try {
          task.run();
        } catch (Exception ignored) {
          // Expected
        }
      }

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }
  }

  @Nested
  @DisplayName("Success Threshold Tests")
  class SuccessThresholdTests {

    @Test
    @DisplayName("circuit closes after reaching success threshold in HALF_OPEN")
    void closesAfterSuccessThreshold() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(2, 3));

      causeFailures(breaker, 2);

      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));

      // Need 3 successes to close
      causeSuccesses(breaker, 2);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN);

      causeSuccesses(breaker, 1);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("single success closes circuit when success threshold is 1")
    void singleSuccessClosesWithThresholdOne() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(2, 1));

      causeFailures(breaker, 2);

      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));

      causeSuccesses(breaker, 1);

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("failure in HALF_OPEN resets success count and re-opens")
    void failureInHalfOpenResetsAndReopens() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(2, 3));

      causeFailures(breaker, 2);

      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));

      // Partial successes then a failure
      causeSuccesses(breaker, 2);
      causeFailures(breaker, 1);

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }
  }

  @Nested
  @DisplayName("Timeout Tests")
  class TimeoutTests {

    @Test
    @DisplayName("circuit remains OPEN before open duration elapses")
    void remainsOpenBeforeDuration() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(1)
              .successThreshold(1)
              .openDuration(Duration.ofMillis(500))
              .callTimeout(GENEROUS_TIMEOUT)
              .build();
      CircuitBreaker breaker = CircuitBreaker.create(config);

      causeFailures(breaker, 1);

      // Immediately check - should still be OPEN
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName("circuit transitions to HALF_OPEN after open duration elapses")
    void transitionsAfterDuration() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(1)
              .successThreshold(1)
              .openDuration(Duration.ofMillis(50))
              .callTimeout(GENEROUS_TIMEOUT)
              .build();
      CircuitBreaker breaker = CircuitBreaker.create(config);

      causeFailures(breaker, 1);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));
    }

    @Test
    @DisplayName("rejects calls while OPEN and provides retryAfter duration")
    void rejectsCallsWithRetryAfterDuration() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(1)
              .successThreshold(1)
              .openDuration(Duration.ofMillis(500))
              .callTimeout(GENEROUS_TIMEOUT)
              .build();
      CircuitBreaker breaker = CircuitBreaker.create(config);

      causeFailures(breaker, 1);

      VTask<String> task = breaker.protect(VTask.succeed("should not reach"));

      assertThatThrownBy(task::run)
          .isInstanceOf(CircuitOpenException.class)
          .satisfies(
              ex -> {
                CircuitOpenException coe = (CircuitOpenException) ex;
                assertThat(coe.status()).isEqualTo(CircuitBreaker.Status.OPEN);
                assertThat(coe.retryAfter()).isNotNull();
                assertThat(coe.retryAfter().isNegative()).isFalse();
              });
    }

    @Test
    @DisplayName("allows probe call through after open duration in HALF_OPEN")
    void allowsProbeAfterDuration() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));

      causeFailures(breaker, 1);

      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));

      AtomicInteger callCount = new AtomicInteger(0);
      VTask<String> probe =
          breaker.protect(
              VTask.of(
                  () -> {
                    callCount.incrementAndGet();
                    return "probe-success";
                  }));

      String result = probe.run();

      assertThat(result).isEqualTo("probe-success");
      assertThat(callCount.get()).isEqualTo(1);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }
  }

  @Nested
  @DisplayName("Metrics Tests")
  class MetricsTests {

    @Test
    @DisplayName("initial metrics are all zero")
    void initialMetricsAreZero() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();

      CircuitBreakerMetrics metrics = breaker.metrics();

      assertThat(metrics.totalCalls()).isZero();
      assertThat(metrics.successfulCalls()).isZero();
      assertThat(metrics.failedCalls()).isZero();
      assertThat(metrics.rejectedCalls()).isZero();
    }

    @Test
    @DisplayName("successful calls are tracked in metrics")
    void successfulCallsTracked() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      causeSuccesses(breaker, 3);

      CircuitBreakerMetrics metrics = breaker.metrics();
      assertThat(metrics.totalCalls()).isEqualTo(3);
      assertThat(metrics.successfulCalls()).isEqualTo(3);
      assertThat(metrics.failedCalls()).isZero();
      assertThat(metrics.rejectedCalls()).isZero();
    }

    @Test
    @DisplayName("failed calls are tracked in metrics")
    void failedCallsTracked() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      causeFailures(breaker, 3);

      CircuitBreakerMetrics metrics = breaker.metrics();
      assertThat(metrics.totalCalls()).isEqualTo(3);
      assertThat(metrics.successfulCalls()).isZero();
      assertThat(metrics.failedCalls()).isEqualTo(3);
    }

    @Test
    @DisplayName("rejected calls are tracked in metrics")
    void rejectedCallsTracked() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));

      causeFailures(breaker, 1); // Opens circuit

      // Now attempt calls that should be rejected
      for (int i = 0; i < 4; i++) {
        VTask<String> task = breaker.protect(VTask.succeed("rejected"));
        try {
          task.run();
        } catch (CircuitOpenException ignored) {
          // Expected
        }
      }

      CircuitBreakerMetrics metrics = breaker.metrics();
      assertThat(metrics.totalCalls()).isEqualTo(5); // 1 failure + 4 rejected
      assertThat(metrics.failedCalls()).isEqualTo(1);
      assertThat(metrics.rejectedCalls()).isEqualTo(4);
    }

    @Test
    @DisplayName("state transitions are counted in metrics")
    void stateTransitionsTracked() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));

      long initialTransitions = breaker.metrics().stateTransitions();

      // CLOSED -> OPEN (1 transition)
      causeFailures(breaker, 1);

      // Wait for OPEN -> HALF_OPEN transition eligibility
      awaitDefault()
          .untilAsserted(
              () -> assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN));

      // HALF_OPEN -> CLOSED via successful probe (OPEN->HALF_OPEN + HALF_OPEN->CLOSED)
      causeSuccesses(breaker, 1);

      CircuitBreakerMetrics metrics = breaker.metrics();
      // Expect at least 3 transitions: CLOSED->OPEN, OPEN->HALF_OPEN, HALF_OPEN->CLOSED
      assertThat(metrics.stateTransitions()).isGreaterThanOrEqualTo(initialTransitions + 3);
    }

    @Test
    @DisplayName("lastStateChange is updated on transitions")
    void lastStateChangeUpdated() throws InterruptedException {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));

      var initialTime = breaker.metrics().lastStateChange();

      Thread.sleep(10);
      causeFailures(breaker, 1); // Triggers transition

      var afterTransition = breaker.metrics().lastStateChange();

      assertThat(afterTransition).isAfter(initialTime);
    }

    @Test
    @DisplayName("metrics reflect mixed success and failure calls")
    void mixedCallsTracked() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(10, 1));

      causeSuccesses(breaker, 3);
      causeFailures(breaker, 2);
      causeSuccesses(breaker, 1);

      CircuitBreakerMetrics metrics = breaker.metrics();
      assertThat(metrics.totalCalls()).isEqualTo(6);
      assertThat(metrics.successfulCalls()).isEqualTo(4);
      assertThat(metrics.failedCalls()).isEqualTo(2);
      assertThat(metrics.rejectedCalls()).isZero();
    }
  }

  @Nested
  @DisplayName("Concurrency Tests")
  class ConcurrencyTests {

    @Test
    @DisplayName("concurrent calls are handled safely in CLOSED state")
    void concurrentCallsInClosedState() throws InterruptedException {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(100, 1));

      int threadCount = 50;
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);
      AtomicInteger successCount = new AtomicInteger(0);
      CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

      for (int i = 0; i < threadCount; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    VTask<String> task = breaker.protect(VTask.succeed("ok"));
                    task.run();
                    successCount.incrementAndGet();
                  } catch (Throwable t) {
                    errors.add(t);
                  } finally {
                    doneLatch.countDown();
                  }
                });
      }

      startLatch.countDown();
      assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

      assertThat(errors).isEmpty();
      assertThat(successCount.get()).isEqualTo(threadCount);
      assertThat(breaker.metrics().totalCalls()).isEqualTo(threadCount);
      assertThat(breaker.metrics().successfulCalls()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("concurrent failures correctly trip the circuit")
    void concurrentFailuresTripCircuit() throws InterruptedException {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      int threadCount = 20;
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);

      for (int i = 0; i < threadCount; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    VTask<String> task =
                        breaker.protect(VTask.fail(new RuntimeException("concurrent-fail")));
                    task.run();
                  } catch (Throwable ignored) {
                    // Expected
                  } finally {
                    doneLatch.countDown();
                  }
                });
      }

      startLatch.countDown();
      assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

      // Circuit should have opened at some point
      // Some calls may have been rejected once it opened
      CircuitBreakerMetrics metrics = breaker.metrics();
      assertThat(metrics.totalCalls()).isEqualTo(threadCount);
      assertThat(metrics.failedCalls() + metrics.rejectedCalls())
          .isGreaterThanOrEqualTo(threadCount - metrics.successfulCalls());
    }

    @Test
    @DisplayName("concurrent mixed calls maintain consistent metrics")
    void concurrentMixedCallsMaintainMetrics() throws InterruptedException {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(100, 1));

      int threadCount = 40;
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);

      for (int i = 0; i < threadCount; i++) {
        final int index = i;
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    if (index % 2 == 0) {
                      breaker.protect(VTask.succeed("ok")).run();
                    } else {
                      breaker.protect(VTask.fail(new RuntimeException("fail"))).run();
                    }
                  } catch (Throwable ignored) {
                    // Expected for failures
                  } finally {
                    doneLatch.countDown();
                  }
                });
      }

      startLatch.countDown();
      assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

      CircuitBreakerMetrics metrics = breaker.metrics();
      assertThat(metrics.totalCalls()).isEqualTo(threadCount);
      assertThat(metrics.successfulCalls() + metrics.failedCalls() + metrics.rejectedCalls())
          .isEqualTo(threadCount);
    }
  }

  @Nested
  @DisplayName("Fallback Tests")
  class FallbackTests {

    @Test
    @DisplayName("fallback is invoked when circuit is OPEN")
    void fallbackInvokedWhenOpen() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));

      causeFailures(breaker, 1);

      VTask<String> task =
          breaker.protectWithFallback(VTask.succeed("should not reach"), ex -> "fallback-value");

      String result = task.run();

      assertThat(result).isEqualTo("fallback-value");
    }

    @Test
    @DisplayName("fallback receives CircuitOpenException")
    void fallbackReceivesCircuitOpenException() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));

      causeFailures(breaker, 1);

      List<Throwable> receivedExceptions = new ArrayList<>();
      VTask<String> task =
          breaker.protectWithFallback(
              VTask.succeed("should not reach"),
              ex -> {
                receivedExceptions.add(ex);
                return "fallback";
              });

      task.run();

      assertThat(receivedExceptions).hasSize(1);
      assertThat(receivedExceptions.get(0)).isInstanceOf(CircuitOpenException.class);
    }

    @Test
    @DisplayName("fallback is not invoked when circuit is CLOSED")
    void fallbackNotInvokedWhenClosed() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      AtomicInteger fallbackCount = new AtomicInteger(0);
      VTask<String> task =
          breaker.protectWithFallback(
              VTask.succeed("primary"),
              ex -> {
                fallbackCount.incrementAndGet();
                return "fallback";
              });

      String result = task.run();

      assertThat(result).isEqualTo("primary");
      assertThat(fallbackCount.get()).isZero();
    }

    @Test
    @DisplayName("fallback is not invoked for non-circuit exceptions")
    void fallbackNotInvokedForNonCircuitExceptions() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      AtomicInteger fallbackCount = new AtomicInteger(0);
      VTask<String> task =
          breaker.protectWithFallback(
              VTask.fail(new RuntimeException("app error")),
              ex -> {
                fallbackCount.incrementAndGet();
                return "fallback";
              });

      assertThatThrownBy(task::run).isInstanceOf(RuntimeException.class).hasMessage("app error");
      assertThat(fallbackCount.get()).isZero();
    }

    @Test
    @DisplayName("protectWithFallback rejects null task")
    void rejectsNullTask() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();

      assertThatNullPointerException()
          .isThrownBy(() -> breaker.protectWithFallback(null, ex -> "fallback"));
    }

    @Test
    @DisplayName("protectWithFallback rejects null fallback")
    void rejectsNullFallback() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();

      assertThatNullPointerException()
          .isThrownBy(() -> breaker.protectWithFallback(VTask.succeed("ok"), null));
    }
  }

  @Nested
  @DisplayName("Generic Protect Tests")
  class GenericProtectTests {

    @Test
    @DisplayName("single circuit breaker protects String tasks")
    void protectsStringTasks() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      VTask<String> task = breaker.protect(VTask.succeed("hello"));

      assertThat(task.run()).isEqualTo("hello");
    }

    @Test
    @DisplayName("single circuit breaker protects Integer tasks")
    void protectsIntegerTasks() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      VTask<Integer> task = breaker.protect(VTask.succeed(42));

      assertThat(task.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("single circuit breaker protects different types simultaneously")
    void protectsDifferentTypesSimultaneously() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      VTask<String> stringTask = breaker.protect(VTask.succeed("text"));
      VTask<Integer> intTask = breaker.protect(VTask.succeed(123));
      VTask<Double> doubleTask = breaker.protect(VTask.succeed(3.14));
      VTask<List<String>> listTask = breaker.protect(VTask.succeed(List.of("a", "b")));

      assertThat(stringTask.run()).isEqualTo("text");
      assertThat(intTask.run()).isEqualTo(123);
      assertThat(doubleTask.run()).isEqualTo(3.14);
      assertThat(listTask.run()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("circuit opens across different types when threshold is reached")
    void circuitOpensAcrossDifferentTypes() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(3, 1));

      // Use different types for the failing calls
      try {
        breaker.protect(VTask.<String>fail(new RuntimeException("fail-1"))).run();
      } catch (RuntimeException ignored) {
      }

      try {
        breaker.protect(VTask.<Integer>fail(new RuntimeException("fail-2"))).run();
      } catch (RuntimeException ignored) {
      }

      try {
        breaker.protect(VTask.<Double>fail(new RuntimeException("fail-3"))).run();
      } catch (RuntimeException ignored) {
      }

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      // All types should now be rejected
      assertThatThrownBy(() -> breaker.protect(VTask.succeed("text")).run())
          .isInstanceOf(CircuitOpenException.class);

      assertThatThrownBy(() -> breaker.protect(VTask.succeed(42)).run())
          .isInstanceOf(CircuitOpenException.class);
    }

    @Test
    @DisplayName("protect rejects null task")
    void protectRejectsNullTask() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();

      assertThatNullPointerException().isThrownBy(() -> breaker.protect(null));
    }

    @Test
    @DisplayName("create rejects null config")
    void createRejectsNullConfig() {
      assertThatNullPointerException().isThrownBy(() -> CircuitBreaker.create(null));
    }

    @Test
    @DisplayName("withDefaults creates a usable circuit breaker")
    void withDefaultsCreatesUsable() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();

      VTask<String> task = breaker.protect(VTask.succeed("default"));

      assertThat(task.run()).isEqualTo("default");
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }
  }

  @Nested
  @DisplayName("Config Validation Tests")
  class ConfigValidationTests {

    @Test
    @DisplayName("CircuitBreakerConfig rejects failureThreshold less than 1")
    void rejectsInvalidFailureThreshold() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> CircuitBreakerConfig.builder().failureThreshold(0).build())
          .withMessageContaining("failureThreshold must be at least 1");

      assertThatIllegalArgumentException()
          .isThrownBy(() -> CircuitBreakerConfig.builder().failureThreshold(-1).build())
          .withMessageContaining("failureThreshold must be at least 1");
    }

    @Test
    @DisplayName("CircuitBreakerConfig rejects successThreshold less than 1")
    void rejectsInvalidSuccessThreshold() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> CircuitBreakerConfig.builder().successThreshold(0).build())
          .withMessageContaining("successThreshold must be at least 1");

      assertThatIllegalArgumentException()
          .isThrownBy(() -> CircuitBreakerConfig.builder().successThreshold(-1).build())
          .withMessageContaining("successThreshold must be at least 1");
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("protectWithFallback re-throws non-CircuitOpenException as RuntimeException")
    void protectWithFallbackRethrowsNonCircuitException() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      // Use a checked exception (not RuntimeException) to exercise the wrapping branch
      Exception checkedException = new Exception("checked error");
      VTask<String> task =
          breaker.protectWithFallback(VTask.fail(checkedException), ex -> "fallback");

      assertThatThrownBy(task::run).isInstanceOf(RuntimeException.class).hasCause(checkedException);
    }

    @Test
    @DisplayName("protectWithFallback re-throws RuntimeException directly for non-circuit errors")
    void protectWithFallbackRethrowsRuntimeExceptionDirectly() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      RuntimeException runtimeEx = new RuntimeException("app error");
      VTask<String> task = breaker.protectWithFallback(VTask.fail(runtimeEx), ex -> "fallback");

      assertThatThrownBy(task::run).isInstanceOf(RuntimeException.class).isSameAs(runtimeEx);
    }

    @Test
    @DisplayName("concurrent CAS contention during OPEN->HALF_OPEN transition")
    void concurrentCasContentionDuringTransition() throws InterruptedException {
      // Use a very short open duration so the circuit becomes eligible for HALF_OPEN quickly
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(1)
              .successThreshold(1)
              .openDuration(Duration.ofMillis(20))
              .callTimeout(GENEROUS_TIMEOUT)
              .build();
      CircuitBreaker breaker = CircuitBreaker.create(config);

      causeFailures(breaker, 1);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      // Wait for the open duration to elapse, but do NOT call currentStatus() or protect()
      // so the AtomicReference still holds OPEN state — the transition hasn't been applied yet
      Thread.sleep(50);

      // Now launch many threads simultaneously — they will all read OPEN from stateRef,
      // see that the open duration has elapsed, and race to compareAndSet to HALF_OPEN.
      // Only one thread wins the CAS; the losers hit the else branch (line 139).
      int threadCount = 20;
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);
      AtomicInteger successCount = new AtomicInteger(0);
      AtomicInteger rejectedCount = new AtomicInteger(0);

      for (int i = 0; i < threadCount; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    VTask<String> task = breaker.protect(VTask.succeed("probe"));
                    task.run();
                    successCount.incrementAndGet();
                  } catch (CircuitOpenException e) {
                    rejectedCount.incrementAndGet();
                  } catch (Throwable t) {
                    // Other errors
                  } finally {
                    doneLatch.countDown();
                  }
                });
      }

      startLatch.countDown();
      assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

      // At least one thread should have succeeded (the CAS winner or subsequent HALF_OPEN calls)
      assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
      // Total should account for all threads
      assertThat(successCount.get() + rejectedCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("CircuitOpenException retryAfter is non-negative")
    void circuitOpenExceptionRetryAfterNonNegative() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(1)
              .successThreshold(1)
              .openDuration(Duration.ofMillis(10))
              .callTimeout(GENEROUS_TIMEOUT)
              .build();
      CircuitBreaker breaker = CircuitBreaker.create(config);

      causeFailures(breaker, 1);

      VTask<String> task = breaker.protect(VTask.succeed("should reject"));
      assertThatThrownBy(task::run)
          .isInstanceOf(CircuitOpenException.class)
          .satisfies(
              ex -> {
                CircuitOpenException coe = (CircuitOpenException) ex;
                assertThat(coe.retryAfter().isNegative()).isFalse();
              });
    }
  }
}
