// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.CircuitBreakerMetrics;
import org.higherkindedj.hkt.resilience.CircuitOpenException;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial01 CircuitBreaker — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial 01: Circuit Breaker Pattern (Solutions)")
public class Tutorial01_CircuitBreaker_Solution {

  @Nested
  @DisplayName("Part 1: Creating and Configuring a Circuit Breaker")
  class CreatingCircuitBreakers {

    /**
     * Why this is idiomatic: a {@code CircuitBreaker} starts {@code CLOSED} — calls flow through.
     * The threshold and open duration come from a config builder so each breaker can be tuned
     * independently.
     *
     * <p>Alternative: {@code CircuitBreaker.withDefaults()} for prototype code. Production breakers
     * want explicit thresholds matched to the service.
     *
     * <p>Common wrong attempt: assume the breaker starts {@code OPEN}. {@code CLOSED} = healthy;
     * {@code OPEN} = tripped; {@code HALF_OPEN} = probing.
     */
    @Test
    @DisplayName("Exercise 1: Create a CircuitBreaker with custom config")
    void exercise1_createCircuitBreaker() {
      // SOLUTION: Use the builder to create a custom config
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(3)
              .openDuration(Duration.ofMillis(100))
              .build();

      // SOLUTION: Create a CircuitBreaker from the config
      CircuitBreaker breaker = CircuitBreaker.create(config);

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    /**
     * Why this is idiomatic: {@code breaker.protect(task)} wraps a VTask so every execution checks
     * the circuit state. Closed circuits run the task; open circuits short-circuit with {@code
     * CircuitOpenException}.
     *
     * <p>Alternative: imperatively check {@code currentStatus} before each call. Race-prone; the
     * protect call atomically reads + acts.
     *
     * <p>Common wrong attempt: protect a different task each time. The circuit is per-breaker;
     * protect the same logical operation through one breaker.
     */
    @Test
    @DisplayName("Exercise 2: Protect a VTask with the circuit breaker")
    void exercise2_protectVTask() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      VTask<String> fetchData = VTask.succeed("Hello from service");

      // SOLUTION: Use breaker.protect() to wrap the task
      VTask<String> protectedTask = breaker.protect(fetchData);

      String result = protectedTask.run();
      assertThat(result).isEqualTo("Hello from service");
    }
  }

  @Nested
  @DisplayName("Part 2: Circuit Breaker State Transitions")
  class StateTransitions {

    /**
     * Why this is idiomatic: the threshold is the count of consecutive failures before the circuit
     * opens. After three failed calls the breaker trips; subsequent calls fail fast without
     * hammering the failing downstream service.
     *
     * <p>Alternative: a percentage-based threshold over a sliding window. More sophisticated; the
     * count-based threshold is the simplest start.
     *
     * <p>Common wrong attempt: pick a threshold of one. Single transient errors trip the breaker;
     * pick a threshold matching the service's tolerance for occasional faults.
     */
    @Test
    @DisplayName("Exercise 3: Circuit opens after threshold failures")
    void exercise3_circuitOpensAfterThreshold() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(3)
              .openDuration(Duration.ofSeconds(60))
              .build();
      CircuitBreaker breaker = CircuitBreaker.create(config);

      VTask<String> failingTask = VTask.fail(new RuntimeException("Service down"));

      // SOLUTION: Execute the protected failing task 3 times
      breaker.protect(failingTask).runSafe();
      breaker.protect(failingTask).runSafe();
      breaker.protect(failingTask).runSafe();

      // After 3 failures, the circuit should be OPEN
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    /**
     * Why this is idiomatic: an open circuit fails fast with {@code CircuitOpenException}.
     * Pattern-match on it to apply circuit-specific recovery (different from a downstream error).
     *
     * <p>Alternative: catch every exception and recover uniformly. Loses the distinction between
     * "downstream is failing" and "circuit is protecting us"; treat them differently.
     *
     * <p>Common wrong attempt: assume the circuit opens immediately on {@code tripOpen()}. The
     * state changes synchronously; the next protected call sees {@code OPEN}.
     */
    @Test
    @DisplayName("Exercise 4: Handle CircuitOpenException")
    void exercise4_handleCircuitOpenException() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      breaker.tripOpen();

      VTask<String> task = VTask.succeed("Should not execute");

      // SOLUTION: Protect and run against the open circuit
      Try<String> result = breaker.protect(task).runSafe();

      assertThat(result.isFailure()).isTrue();

      // SOLUTION: Extract the exception using foldFailureFirst
      Throwable error = result.foldFailureFirst(ex -> ex, value -> null);

      assertThat(error).isInstanceOf(CircuitOpenException.class);
      CircuitOpenException coe = (CircuitOpenException) error;
      assertThat(coe.status()).isEqualTo(CircuitBreaker.Status.OPEN);
    }
  }

  @Nested
  @DisplayName("Part 3: Fallback and Metrics")
  class FallbackAndMetrics {

    /**
     * Why this is idiomatic: {@code protectWithFallback(task, fallback)} combines the breaker with
     * a recovery function. Open circuits return the fallback; closed circuits run the task.
     *
     * <p>Alternative: chain {@code protect} with {@code recover}. Equivalent; the named method is
     * the explicit "graceful degradation" form.
     *
     * <p>Common wrong attempt: pick a fallback that itself calls the downstream. Defeats the
     * purpose; fallbacks should be cheap and local (cache, default).
     */
    @Test
    @DisplayName("Exercise 5: Use protectWithFallback for graceful degradation")
    void exercise5_protectWithFallback() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      breaker.tripOpen();

      VTask<String> task = VTask.succeed("Primary response");

      // SOLUTION: Use protectWithFallback to provide a fallback value
      VTask<String> protectedTask = breaker.protectWithFallback(task, error -> "Fallback response");

      String result = protectedTask.run();
      assertThat(result).isEqualTo("Fallback response");
    }

    /**
     * Why this is idiomatic: {@code breaker.metrics()} exposes total/successful/failed call counts.
     * Useful for dashboards and SLO tracking — observe before reacting.
     *
     * <p>Alternative: instrument each call manually. Same data; the breaker's metrics are atomic
     * and consistent with the state transitions.
     *
     * <p>Common wrong attempt: assume the metrics reset after a state transition. Counters
     * accumulate; reset explicitly when needed.
     */
    @Test
    @DisplayName("Exercise 6: Inspect circuit breaker metrics")
    void exercise6_checkMetrics() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(3)
              .openDuration(Duration.ofSeconds(60))
              .build();
      CircuitBreaker breaker = CircuitBreaker.create(config);

      // Run 2 successful calls
      breaker.protect(VTask.succeed("ok")).runSafe();
      breaker.protect(VTask.succeed("ok")).runSafe();

      // Run 1 failed call
      breaker.protect(VTask.fail(new RuntimeException("fail"))).runSafe();

      // SOLUTION: Get the metrics from the circuit breaker
      CircuitBreakerMetrics metrics = breaker.metrics();

      assertThat(metrics.totalCalls()).isEqualTo(3);
      assertThat(metrics.successfulCalls()).isEqualTo(2);
      assertThat(metrics.failedCalls()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * Why this is idiomatic: a complete workflow combines failure-driven tripping with fallback
     * responses. Two failures open the circuit; subsequent calls return the cached response without
     * hitting the downstream.
     *
     * <p>Alternative: a try/catch around every downstream call with a counter. Tedious and
     * error-prone; the breaker centralises both logics.
     *
     * <p>Common wrong attempt: forget to inspect metrics in production. The breaker is observable;
     * surface the counts so operators see when it trips.
     */
    @Test
    @DisplayName("Complete circuit breaker workflow example")
    void completeCircuitBreakerWorkflow() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(2)
              .openDuration(Duration.ofMillis(100))
              .build();
      CircuitBreaker breaker = CircuitBreaker.create(config);

      // Simulate 2 failures to open the circuit
      VTask<String> failing = VTask.fail(new RuntimeException("timeout"));
      breaker.protect(failing).runSafe();
      breaker.protect(failing).runSafe();

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      // Use fallback when circuit is open
      VTask<String> withFallback =
          breaker.protectWithFallback(VTask.succeed("primary"), error -> "cached response");

      String result = withFallback.run();
      assertThat(result).isEqualTo("cached response");

      // Check metrics
      CircuitBreakerMetrics metrics = breaker.metrics();
      assertThat(metrics.failedCalls()).isEqualTo(2);
      assertThat(metrics.stateTransitions()).isGreaterThanOrEqualTo(1);
    }
  }
}
