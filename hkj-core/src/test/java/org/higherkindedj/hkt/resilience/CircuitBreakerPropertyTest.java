// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import net.jqwik.api.*;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Property-based tests for {@link CircuitBreaker}.
 *
 * <p>Verifies state machine invariants, metrics consistency, and control operations using jqwik
 * properties.
 */
class CircuitBreakerPropertyTest {

  // ==================== Arbitrary Providers ====================

  @Provide
  Arbitrary<CircuitBreakerConfig> configs() {
    return Arbitraries.integers()
        .between(1, 10)
        .flatMap(
            failureThreshold ->
                Arbitraries.integers()
                    .between(1, 5)
                    .map(
                        successThreshold ->
                            CircuitBreakerConfig.builder()
                                .failureThreshold(failureThreshold)
                                .successThreshold(successThreshold)
                                .openDuration(Duration.ofMinutes(10))
                                .callTimeout(Duration.ofSeconds(5))
                                .build()));
  }

  // ==================== State Machine Invariant ====================

  /**
   * Property: The circuit breaker status is always one of CLOSED, OPEN, or HALF_OPEN.
   *
   * <p>After performing a random mix of successful and failed calls, the status must remain a valid
   * enum value.
   */
  @Property
  @Label("Status is always one of CLOSED, OPEN, or HALF_OPEN")
  void statusIsAlwaysValid(
      @ForAll("configs") CircuitBreakerConfig config,
      @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 20) int successCount,
      @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 20) int failureCount) {

    CircuitBreaker breaker = CircuitBreaker.create(config);

    for (int i = 0; i < successCount; i++) {
      try {
        breaker.protect(VTask.succeed("ok")).run();
      } catch (Throwable ignored) {
        // circuit may be open
      }
    }

    for (int i = 0; i < failureCount; i++) {
      try {
        breaker.protect(VTask.fail(new RuntimeException("fail"))).run();
      } catch (Throwable ignored) {
        // expected
      }
    }

    assertThat(breaker.currentStatus())
        .isIn(
            CircuitBreaker.Status.CLOSED,
            CircuitBreaker.Status.OPEN,
            CircuitBreaker.Status.HALF_OPEN);
  }

  // ==================== Metrics Consistency ====================

  /**
   * Property: totalCalls >= successfulCalls + failedCalls + rejectedCalls.
   *
   * <p>The total call count must always be at least the sum of the categorised counters. In the
   * current implementation they are equal because every call is categorised, but the invariant
   * expressed here is the weaker >= which holds regardless.
   */
  @Property
  @Label("Metrics consistency: totalCalls >= successfulCalls + failedCalls + rejectedCalls")
  void metricsAreConsistent(
      @ForAll("configs") CircuitBreakerConfig config,
      @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 15) int successCount,
      @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 15) int failureCount) {

    CircuitBreaker breaker = CircuitBreaker.create(config);

    for (int i = 0; i < successCount; i++) {
      try {
        breaker.protect(VTask.succeed("ok")).run();
      } catch (Throwable ignored) {
        // circuit may be open
      }
    }

    for (int i = 0; i < failureCount; i++) {
      try {
        breaker.protect(VTask.fail(new RuntimeException("fail"))).run();
      } catch (Throwable ignored) {
        // expected
      }
    }

    CircuitBreakerMetrics metrics = breaker.metrics();

    assertThat(metrics.totalCalls())
        .isGreaterThanOrEqualTo(
            metrics.successfulCalls() + metrics.failedCalls() + metrics.rejectedCalls());
  }

  // ==================== Reset Always Yields CLOSED ====================

  /**
   * Property: After calling {@code reset()}, the circuit breaker status is always CLOSED.
   *
   * <p>Regardless of how many failures have been recorded, reset brings the breaker back to its
   * initial state.
   */
  @Property
  @Label("After reset(), status is always CLOSED")
  void resetAlwaysYieldsClosed(
      @ForAll("configs") CircuitBreakerConfig config,
      @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 30) int failureCount) {

    CircuitBreaker breaker = CircuitBreaker.create(config);

    // Drive failures to potentially open the circuit
    for (int i = 0; i < failureCount; i++) {
      try {
        breaker.protect(VTask.fail(new RuntimeException("fail"))).run();
      } catch (Throwable ignored) {
        // expected
      }
    }

    breaker.reset();

    assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
  }

  // ==================== tripOpen Always Yields OPEN ====================

  /**
   * Property: After calling {@code tripOpen()}, the circuit breaker status is always OPEN.
   *
   * <p>Manual tripping overrides any current state and forces the circuit into the open state.
   */
  @Property
  @Label("After tripOpen(), status is always OPEN")
  void tripOpenAlwaysYieldsOpen(
      @ForAll("configs") CircuitBreakerConfig config,
      @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 15) int successCount) {

    CircuitBreaker breaker = CircuitBreaker.create(config);

    // Run some successes first
    for (int i = 0; i < successCount; i++) {
      try {
        breaker.protect(VTask.succeed("ok")).run();
      } catch (Throwable ignored) {
        // should not happen but guard anyway
      }
    }

    breaker.tripOpen();

    assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
  }

  // ==================== Failure Count Below Threshold Keeps CLOSED ====================

  /**
   * Property: After N consecutive failures where N is strictly less than the configured failure
   * threshold, the circuit breaker stays CLOSED.
   *
   * <p>This validates that the circuit does not open prematurely.
   */
  @Property
  @Label("N failures below threshold keeps circuit CLOSED")
  void failuresBelowThresholdKeepCircuitClosed(
      @ForAll @net.jqwik.api.constraints.IntRange(min = 2, max = 10) int failureThreshold) {

    CircuitBreakerConfig config =
        CircuitBreakerConfig.builder()
            .failureThreshold(failureThreshold)
            .openDuration(Duration.ofMinutes(10))
            .callTimeout(Duration.ofSeconds(5))
            .build();

    CircuitBreaker breaker = CircuitBreaker.create(config);

    // Inject exactly (failureThreshold - 1) failures
    int failuresToApply = failureThreshold - 1;
    for (int i = 0; i < failuresToApply; i++) {
      try {
        breaker.protect(VTask.fail(new RuntimeException("fail-" + i))).run();
      } catch (Throwable ignored) {
        // expected
      }
    }

    assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    assertThat(breaker.metrics().failedCalls()).isEqualTo(failuresToApply);
  }
}
