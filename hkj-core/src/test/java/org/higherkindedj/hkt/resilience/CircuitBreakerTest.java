// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.core.ConditionFactory;
import org.higherkindedj.hkt.test.fixtures.HeldTask;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link CircuitBreaker}.
 *
 * <p>Tests cover state transitions, failure and success thresholds, timeout behaviour, metrics
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

  /** Waits, without calling protect(), until the open duration is up and the next call probes. */
  private static void awaitHalfOpen(CircuitBreaker breaker) {
    awaitDefault().until(() -> breaker.currentStatus() == CircuitBreaker.Status.HALF_OPEN);
  }

  /** A call held in flight by {@link #startHeld}. */
  private record Held(Thread thread, AtomicReference<Throwable> thrown) {

    /** Waits for the call to finish, and gives what it threw, if anything. */
    Optional<Throwable> join() throws InterruptedException {
      thread.join(5000);
      assertThat(thread.isAlive()).as("the held call finished").isFalse();
      return Optional.ofNullable(thrown.get());
    }
  }

  /**
   * Starts {@code breaker.protect(...)} of a call that, once admitted, waits for {@code release}
   * and then runs {@code then}. Returns once the call is running, so the caller knows it was
   * admitted.
   */
  private static Held startHeld(CircuitBreaker breaker, CountDownLatch release, VTask<String> then)
      throws InterruptedException {
    CountDownLatch running = new CountDownLatch(1);
    VTask<String> call =
        breaker.protect(
            () -> {
              running.countDown();
              if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test did not release this call");
              }
              return then.execute();
            });
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    Thread thread =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    call.execute();
                  } catch (Throwable t) {
                    thrown.set(t);
                  }
                });
    assertThat(running.await(5, TimeUnit.SECONDS)).as("the held call was admitted").isTrue();
    return new Held(thread, thrown);
  }

  private static Held startHeld(CircuitBreaker breaker, CountDownLatch release)
      throws InterruptedException {
    return startHeld(breaker, release, VTask.succeed("held"));
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
    @DisplayName(
        "an open circuit reports retryAfter under an openDuration too long for nanoseconds")
    void rejectsWithRetryAfterUnderAHugeOpenDuration() {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .openDuration(ChronoUnit.FOREVER.getDuration())
                  .callTimeout(GENEROUS_TIMEOUT)
                  .build());
      breaker.tripOpen();

      VTask<String> task = breaker.protect(VTask.succeed("should not reach"));

      assertThatThrownBy(task::run)
          .isInstanceOfSatisfying(
              CircuitOpenException.class,
              open -> assertThat(open.retryAfter()).isGreaterThan(Duration.ofDays(365)));
    }

    @Test
    @DisplayName("a call slower than callTimeout is interrupted, and counts once it has stopped")
    void interruptsACallSlowerThanTheCallTimeout() {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(1)
                  .callTimeout(Duration.ofMillis(20))
                  .build());
      AtomicBoolean interrupted = new AtomicBoolean(false);
      AtomicBoolean cleanedUp = new AtomicBoolean(false);
      VTask<String> slowCall =
          VTask.of(
              () -> {
                try {
                  Thread.sleep(5000);
                  return "finished";
                } catch (InterruptedException e) {
                  interrupted.set(true);
                  throw e;
                } finally {
                  cleanedUp.set(true);
                }
              });

      assertThatThrownBy(breaker.protect(slowCall)::execute).isInstanceOf(TimeoutException.class);
      assertThat(interrupted).isTrue();
      assertThat(cleanedUp).isTrue();
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName("a detached call that ignores the interrupt counts when callTimeout runs out")
    void detachedCallCountsWhenTheCallTimeoutRunsOut() throws InterruptedException {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(1)
                  .callTimeout(Duration.ofMillis(20))
                  .build());
      HeldTask held = new HeldTask();
      try {
        assertThatThrownBy(breaker.protect(held.task("finished").detachOnCancel())::execute)
            .isInstanceOf(TimeoutException.class);
        assertThat(held.finished()).as("the call is still running").isFalse();
        assertThat(held.sawInterrupt()).isTrue();
        assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
      } finally {
        held.release();
      }
    }

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
    @DisplayName("a fallback that returns null fails the task")
    void fallbackReturningNullFailsTheTask() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));

      causeFailures(breaker, 1);

      VTask<String> task =
          breaker.protectWithFallback(VTask.succeed("should not reach"), ex -> null);

      assertThatNullPointerException()
          .isThrownBy(task::run)
          .withMessage("fallback must not return null");
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
      RuntimeException appError = new RuntimeException("app error");
      VTask<String> task =
          breaker.protectWithFallback(
              VTask.fail(appError),
              ex -> {
                fallbackCount.incrementAndGet();
                return "fallback";
              });

      assertThatThrownBy(task::run).isSameAs(appError);
      assertThat(fallbackCount.get()).isZero();
    }

    @Test
    @DisplayName("fallback passes a checked exception through as thrown")
    void fallbackPassesCheckedExceptionThrough() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      IOException checkedException = new IOException("disk");
      VTask<String> task =
          breaker.protectWithFallback(VTask.fail(checkedException), ex -> "fallback");

      assertThatThrownBy(task::execute).isSameAs(checkedException);
    }

    @Test
    @DisplayName("fallback passes an Error through as thrown")
    void fallbackPassesErrorThrough() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(5, 1));

      Error fatal = new Error("fatal");
      VTask<String> task = breaker.protectWithFallback(VTask.fail(fatal), ex -> "fallback");

      assertThatThrownBy(task::execute).isSameAs(fatal);
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
  @DisplayName("Cancellation Tests")
  class CancellationTests {

    @Test
    @DisplayName("a call that throws InterruptedException counts as neither success nor failure")
    void cancelledCallIsNotRecorded() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));
      InterruptedException cancelled = new InterruptedException("cancelled");

      VTask<String> task = breaker.protect(VTask.fail(cancelled));

      assertThatThrownBy(task::execute).isSameAs(cancelled);
      CircuitBreakerMetrics metrics = breaker.metrics();
      assertThat(metrics.totalCalls()).isEqualTo(1);
      assertThat(metrics.successfulCalls()).isZero();
      assertThat(metrics.failedCalls()).isZero();
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("an interrupted caller cancels the call, which is recorded as neither outcome")
    void interruptedCallerCancelsTheCall() throws InterruptedException {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));
      CountDownLatch callInterrupted = new CountDownLatch(1);
      VTask<String> task =
          breaker.protect(
              VTask.of(
                  () -> {
                    try {
                      Thread.sleep(3000);
                      return "finished";
                    } catch (InterruptedException e) {
                      callInterrupted.countDown();
                      throw e;
                    }
                  }));

      Thread.currentThread().interrupt();
      try {
        assertThatThrownBy(task::execute).isInstanceOf(InterruptedException.class);
      } finally {
        Thread.interrupted();
      }
      assertThat(callInterrupted.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(breaker.metrics().successfulCalls()).isZero();
      assertThat(breaker.metrics().failedCalls()).isZero();
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
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
    @DisplayName(
        "callers racing into a circuit whose open time is up are each admitted or turned away")
    void racingCallersAfterTheOpenDuration() throws InterruptedException {
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

      // Now launch many threads simultaneously: they all arrive at OPEN with the open duration
      // over, and race to half-open the circuit. The one whose update lands is the first probe;
      // with a success threshold of 1, the others find no probe place free and are rejected,
      // unless the probe has already closed the circuit when they arrive.
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

      // At least the probe succeeds, and so does any call arriving after it closed the circuit
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

  // ==========================================================================
  // Audit Issue #4: stateTransitions counter must not be corrupted by CAS retries
  // ==========================================================================

  @Nested
  @DisplayName("State Transition Counter Accuracy (audit issue #4)")
  class StateTransitionCounterTests {

    @Test
    @DisplayName("single CLOSED->OPEN transition increments stateTransitions by exactly 1")
    void singleTransitionIncrementsCountByOne() {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(3)
                  .successThreshold(1)
                  .openDuration(Duration.ofSeconds(60))
                  .callTimeout(Duration.ofSeconds(5))
                  .build());

      long initialTransitions = breaker.metrics().stateTransitions();

      // Cause exactly enough failures to trip CLOSED -> OPEN
      for (int i = 0; i < 3; i++) {
        try {
          breaker.protect(VTask.fail(new RuntimeException("fail"))).run();
        } catch (RuntimeException ignored) {
        }
      }

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
      assertThat(breaker.metrics().stateTransitions()).isEqualTo(initialTransitions + 1);
    }

    @Test
    @DisplayName(
        "concurrent failures produce correct transition count (no double-counting from CAS retries)")
    void concurrentFailuresProduceCorrectTransitionCount() throws Exception {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(1)
                  .successThreshold(1)
                  .openDuration(Duration.ofSeconds(60))
                  .callTimeout(Duration.ofSeconds(5))
                  .build());

      int threadCount = 10;
      CyclicBarrier barrier = new CyclicBarrier(threadCount);
      CountDownLatch done = new CountDownLatch(threadCount);

      for (int i = 0; i < threadCount; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    barrier.await(2, TimeUnit.SECONDS);
                    breaker.protect(VTask.fail(new RuntimeException("fail"))).run();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                });
      }

      done.await(5, TimeUnit.SECONDS);

      long transitions = breaker.metrics().stateTransitions();
      assertThat(transitions)
          .as("stateTransitions should be exactly 1 (CLOSED->OPEN), not inflated by CAS retries")
          .isEqualTo(1);
    }
  }

  // ==========================================================================
  // Audit Issue #5: onSuccess in OPEN state must not transition to CLOSED
  // ==========================================================================

  @Nested
  @DisplayName("OPEN State Success Handling (audit issue #5)")
  class OpenStateSuccessTests {

    @Test
    @DisplayName("success recorded while OPEN should not close the circuit")
    void successInOpenStateShouldNotCloseCircuit() {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(2)
                  .successThreshold(1)
                  .openDuration(Duration.ofSeconds(60))
                  .callTimeout(Duration.ofSeconds(5))
                  .build());

      // Trip the circuit to OPEN
      for (int i = 0; i < 2; i++) {
        try {
          breaker.protect(VTask.fail(new RuntimeException("fail"))).run();
        } catch (RuntimeException ignored) {
        }
      }
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      breaker.tripOpen();
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      // A call while OPEN should be rejected, not succeed
      VTask<String> task = breaker.protect(VTask.succeed("should-be-rejected"));
      assertThatThrownBy(task::run).isInstanceOf(CircuitOpenException.class);

      // Circuit should still be OPEN
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName(
        "onSuccess called while OPEN due to concurrent state change should not close circuit")
    void onSuccessWhileOpenDueToConcurrentStateChange() throws Exception {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(3)
                  .successThreshold(1)
                  .openDuration(Duration.ofSeconds(60))
                  .callTimeout(Duration.ofSeconds(5))
                  .build());

      CountDownLatch taskStarted = new CountDownLatch(1);
      CountDownLatch proceedToComplete = new CountDownLatch(1);
      AtomicReference<String> taskResult = new AtomicReference<>();

      // Start a slow task that will succeed - admitted while CLOSED
      VTask<String> slowTask =
          breaker.protect(
              () -> {
                taskStarted.countDown();
                proceedToComplete.await(5, TimeUnit.SECONDS);
                return "success";
              });

      Thread taskThread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      taskResult.set(slowTask.run());
                    } catch (Exception ignored) {
                    }
                  });

      // Wait for task to start executing (admitted while CLOSED)
      assertThat(taskStarted.await(2, TimeUnit.SECONDS)).isTrue();

      // Trip breaker to OPEN while the slow task is still in-flight
      causeFailures(breaker, 3);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      // Let the slow task complete - onSuccess() is called while state is OPEN
      proceedToComplete.countDown();
      taskThread.join(5000);

      // Breaker should still be OPEN: the call was admitted under an earlier state
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
      assertThat(taskResult.get()).isEqualTo("success");
    }
  }

  // ==========================================================================
  // Contention: concurrent OPEN -> HALF_OPEN transitions in protect()
  // ==========================================================================

  @Nested
  @DisplayName("Contention in protect()")
  class ContentionTests {

    @Test
    @DisplayName(
        "concurrent protect() calls race to half-open the circuit, without unexpected errors")
    void concurrentCallsRaceToHalfOpen() throws Exception {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(1)
                  .successThreshold(1)
                  .openDuration(Duration.ofMillis(1))
                  .callTimeout(Duration.ofSeconds(5))
                  .build());

      int iterations = 50;
      for (int iter = 0; iter < iterations; iter++) {
        // Trip to OPEN
        breaker.tripOpen();
        assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

        // Wait for open duration to elapse so next protect() will attempt HALF_OPEN transition
        Thread.sleep(5);

        // Race multiple threads to half-open the circuit in protect()
        int threadCount = 4;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threadCount; t++) {
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      barrier.await(2, TimeUnit.SECONDS);
                      breaker.protect(VTask.succeed("ok")).run();
                    } catch (CircuitOpenException ignored) {
                      // Expected for a thread that finds no probe place free
                    } catch (Exception e) {
                      errors.add(e);
                    } finally {
                      done.countDown();
                    }
                  });
        }

        done.await(5, TimeUnit.SECONDS);
        assertThat(errors).as("No unexpected exceptions in iteration " + iter).isEmpty();

        // Reset for next iteration
        breaker.reset();
      }
    }
  }

  // ==========================================================================
  // HALF_OPEN lets through at most successThreshold probes
  // ==========================================================================

  @Nested
  @DisplayName("Half-Open Probe Limit Tests")
  class HalfOpenProbeLimitTests {

    @Test
    @DisplayName(
        "HALF_OPEN admits successThreshold probes and rejects the next call, with its status and"
            + " the call timeout")
    void rejectsTheCallAfterTheProbes() throws Exception {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 3));
      causeFailures(breaker, 1);
      awaitHalfOpen(breaker);

      CountDownLatch release = new CountDownLatch(1);
      List<Held> probes = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        probes.add(startHeld(breaker, release));
      }
      long rejectedBefore = breaker.metrics().rejectedCalls();

      assertThatThrownBy(() -> breaker.protect(VTask.succeed("one too many")).run())
          .isInstanceOfSatisfying(
              CircuitOpenException.class,
              open -> {
                assertThat(open.status()).isEqualTo(CircuitBreaker.Status.HALF_OPEN);
                assertThat(open.retryAfter()).isEqualTo(GENEROUS_TIMEOUT);
              });
      assertThat(breaker.metrics().rejectedCalls()).isEqualTo(rejectedBefore + 1);

      release.countDown();
      for (Held probe : probes) {
        assertThat(probe.join()).isEmpty();
      }
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
      assertThat(breaker.protect(VTask.succeed("after")).run()).isEqualTo("after");
    }

    @Test
    @DisplayName("with the default success threshold, one probe runs at a time")
    void oneProbeByDefault() throws Exception {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));
      causeFailures(breaker, 1);
      awaitHalfOpen(breaker);

      CountDownLatch release = new CountDownLatch(1);
      Held probe = startHeld(breaker, release);
      assertThatThrownBy(() -> breaker.protect(VTask.succeed("second")).run())
          .isInstanceOf(CircuitOpenException.class);

      release.countDown();
      assertThat(probe.join()).isEmpty();
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("a probe that succeeds keeps its place until the probes have decided")
    void aSuccessfulProbeKeepsItsPlace() throws Exception {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 2));
      causeFailures(breaker, 1);
      awaitHalfOpen(breaker);

      CountDownLatch release = new CountDownLatch(1);
      Held held = startHeld(breaker, release);
      assertThat(breaker.protect(VTask.succeed("quick")).run()).isEqualTo("quick");
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN);

      assertThatThrownBy(() -> breaker.protect(VTask.succeed("third")).run())
          .isInstanceOf(CircuitOpenException.class);

      release.countDown();
      assertThat(held.join()).isEmpty();
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("a cancelled probe gives up its place to the next call")
    void aCancelledProbeGivesUpItsPlace() {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));
      causeFailures(breaker, 1);
      awaitHalfOpen(breaker);

      VTask<String> cancelled = breaker.protect(VTask.fail(new InterruptedException("cancelled")));
      assertThatThrownBy(cancelled::execute).isInstanceOf(InterruptedException.class);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN);

      assertThat(breaker.protect(VTask.succeed("next")).run()).isEqualTo("next");
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("of many callers arriving at once, only successThreshold reach the service")
    void manyCallersAtOnce() throws Exception {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 3));
      causeFailures(breaker, 1);
      awaitHalfOpen(breaker);

      int callers = 40;
      AtomicInteger reached = new AtomicInteger();
      AtomicInteger rejected = new AtomicInteger();
      List<Throwable> errors = new CopyOnWriteArrayList<>();
      CountDownLatch release = new CountDownLatch(1);
      CyclicBarrier barrier = new CyclicBarrier(callers);
      List<Thread> threads = new ArrayList<>();
      for (int i = 0; i < callers; i++) {
        threads.add(
            Thread.ofVirtual()
                .start(
                    () -> {
                      try {
                        barrier.await(5, TimeUnit.SECONDS);
                        breaker
                            .protect(
                                () -> {
                                  reached.incrementAndGet();
                                  return release.await(5, TimeUnit.SECONDS);
                                })
                            .execute();
                      } catch (CircuitOpenException open) {
                        rejected.incrementAndGet();
                      } catch (Throwable unexpected) {
                        errors.add(unexpected);
                      }
                    }));
      }

      // A probe's body runs on the thread its timeout starts, so it can count after the rejections
      awaitDefault()
          .untilAsserted(
              () -> {
                assertThat(rejected).hasValue(callers - 3);
                assertThat(reached).hasValue(3);
              });

      release.countDown();
      for (Thread thread : threads) {
        thread.join(5000);
        assertThat(thread.isAlive()).isFalse();
      }
      assertThat(errors).isEmpty();
      assertThat(reached).as("no fourth call reached the service").hasValue(3);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }
  }

  // ==========================================================================
  // A call counts only towards the state it was admitted in
  // ==========================================================================

  @Nested
  @DisplayName("A Call Counts Towards The State That Admitted It")
  class AdmittingStateTests {

    @Test
    @DisplayName("a success from a call admitted while CLOSED cannot close a later HALF_OPEN")
    void aStaleSuccessCannotClose() throws Exception {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));
      CountDownLatch releaseStale = new CountDownLatch(1);
      Held stale = startHeld(breaker, releaseStale);
      causeFailures(breaker, 1);
      awaitHalfOpen(breaker);
      CountDownLatch releaseProbe = new CountDownLatch(1);
      Held probe = startHeld(breaker, releaseProbe);
      long successesBefore = breaker.metrics().successfulCalls();

      releaseStale.countDown();
      assertThat(stale.join()).isEmpty();
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN);
      assertThat(breaker.metrics().successfulCalls()).isEqualTo(successesBefore + 1);
      assertThatThrownBy(() -> breaker.protect(VTask.succeed("no place")).run())
          .isInstanceOf(CircuitOpenException.class);

      releaseProbe.countDown();
      assertThat(probe.join()).isEmpty();
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("a failure from a call admitted while CLOSED cannot re-open a later HALF_OPEN")
    void aStaleFailureCannotReopen() throws Exception {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));
      CountDownLatch releaseStale = new CountDownLatch(1);
      RuntimeException late = new RuntimeException("late");
      Held stale = startHeld(breaker, releaseStale, VTask.fail(late));
      causeFailures(breaker, 1);
      awaitHalfOpen(breaker);
      CountDownLatch releaseProbe = new CountDownLatch(1);
      Held probe = startHeld(breaker, releaseProbe);
      long failuresBefore = breaker.metrics().failedCalls();

      releaseStale.countDown();
      assertThat(stale.join()).containsSame(late);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.HALF_OPEN);
      assertThat(breaker.metrics().failedCalls()).isEqualTo(failuresBefore + 1);

      releaseProbe.countDown();
      assertThat(probe.join()).isEmpty();
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("a probe from an earlier HALF_OPEN counts nothing towards a later one")
    void aStaleProbeCountsNothing() throws Exception {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 2));
      causeFailures(breaker, 1);
      awaitHalfOpen(breaker);
      CountDownLatch releaseStale = new CountDownLatch(1);
      Held staleProbe = startHeld(breaker, releaseStale);
      assertThatThrownBy(
              () -> breaker.protect(VTask.fail(new RuntimeException("probe fails"))).run())
          .isInstanceOf(RuntimeException.class);
      assertThat(breaker.metrics().stateTransitions()).isEqualTo(3);

      awaitHalfOpen(breaker);
      CountDownLatch releaseProbe = new CountDownLatch(1);
      Held probe = startHeld(breaker, releaseProbe);
      releaseStale.countDown();
      assertThat(staleProbe.join()).isEmpty();

      releaseProbe.countDown();
      assertThat(probe.join()).isEmpty();
      assertThat(breaker.currentStatus())
          .as("one success of the two this window needs")
          .isEqualTo(CircuitBreaker.Status.HALF_OPEN);
    }

    @Test
    @DisplayName("a failure from a call running across reset() leaves the circuit CLOSED")
    void aFailureAcrossResetIsNotCounted() throws Exception {
      CircuitBreaker breaker = CircuitBreaker.create(configWithThresholds(1, 1));
      CountDownLatch release = new CountDownLatch(1);
      RuntimeException afterReset = new RuntimeException("after reset");
      Held call = startHeld(breaker, release, VTask.fail(afterReset));

      breaker.reset();
      release.countDown();

      assertThat(call.join()).containsSame(afterReset);
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
      assertThat(breaker.metrics().failedCalls()).isEqualTo(1);
    }
  }

  // ==========================================================================
  // A recordFailure predicate that throws
  // ==========================================================================

  @Nested
  @DisplayName("Throwing Predicate Tests")
  class ThrowingPredicateTests {

    @Test
    @DisplayName(
        "a probe whose predicate throws counts as a failure, and the call's own exception carries"
            + " the predicate's")
    void aThrowingPredicateCountsAFailure() {
      IllegalArgumentException bug = new IllegalArgumentException("predicate bug");
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(1)
                  .successThreshold(1)
                  .openDuration(SHORT_OPEN_DURATION)
                  .callTimeout(GENEROUS_TIMEOUT)
                  .recordFailure(
                      ex -> {
                        if (ex instanceof IllegalStateException) {
                          throw bug;
                        }
                        return true;
                      })
                  .build());
      causeFailures(breaker, 1);
      awaitHalfOpen(breaker);
      IllegalStateException failure = new IllegalStateException("probe fails");

      assertThatThrownBy(breaker.protect(VTask.<String>fail(failure))::execute)
          .isSameAs(failure)
          .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(bug));
      assertThat(breaker.currentStatus())
          .as("the probe's failure re-opened the circuit, rather than holding its place")
          .isEqualTo(CircuitBreaker.Status.OPEN);
      assertThat(breaker.metrics().failedCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("a predicate that rethrows the call's exception adds nothing to it")
    void aPredicateRethrowingTheFailure() {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(1)
                  .callTimeout(GENEROUS_TIMEOUT)
                  .recordFailure(
                      ex -> {
                        throw (RuntimeException) ex;
                      })
                  .build());
      IllegalStateException failure = new IllegalStateException("rethrown");

      assertThatThrownBy(breaker.protect(VTask.<String>fail(failure))::execute)
          .isSameAs(failure)
          .satisfies(thrown -> assertThat(thrown.getSuppressed()).isEmpty());
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }
  }
}
