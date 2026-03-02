// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience;

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
 * Tutorial 01: Circuit Breaker Pattern
 *
 * <p>Learn to protect VTask operations using the Circuit Breaker pattern. A circuit breaker tracks
 * the health of a dependency and prevents repeated calls to a failing service by transitioning
 * through three states: CLOSED (normal), OPEN (rejecting), and HALF_OPEN (probing).
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>CircuitBreakerConfig configures failure thresholds and open durations
 *   <li>CircuitBreaker.protect() wraps a VTask with circuit breaker protection
 *   <li>When the circuit opens, calls are rejected with CircuitOpenException
 *   <li>protectWithFallback() provides a graceful degradation path
 *   <li>CircuitBreakerMetrics provides observability into circuit behaviour
 * </ul>
 *
 * <p>Requirements: Java 25+ (virtual threads and structured concurrency)
 *
 * <p>Replace each {@code fail("TODO: ...")} with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 01: Circuit Breaker Pattern")
public class Tutorial01_CircuitBreaker {

  // ===========================================================================
  // Part 1: Creating and Configuring a Circuit Breaker
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating and Configuring a Circuit Breaker")
  class CreatingCircuitBreakers {

    /**
     * Exercise 1: Create a CircuitBreaker with custom configuration
     *
     * <p>Use CircuitBreakerConfig.builder() to create a config with:
     *
     * <ul>
     *   <li>failureThreshold = 3 (open after 3 consecutive failures)
     *   <li>openDuration = 100ms (time before transitioning to half-open)
     * </ul>
     *
     * Then create a CircuitBreaker from that config.
     */
    @Test
    @DisplayName("Exercise 1: Create a CircuitBreaker with custom config")
    void exercise1_createCircuitBreaker() {
      // Create a CircuitBreakerConfig using the builder pattern
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(3)
              .openDuration(Duration.ofMillis(100))
              .build();

      // Create a CircuitBreaker from the config
      CircuitBreaker breaker = CircuitBreaker.create(config);

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    /**
     * Exercise 2: Protect a VTask with the circuit breaker
     *
     * <p>Use breaker.protect() to wrap a VTask with circuit breaker protection. When the task
     * succeeds, the result passes through unchanged.
     */
    @Test
    @DisplayName("Exercise 2: Protect a VTask with the circuit breaker")
    void exercise2_protectVTask() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      VTask<String> fetchData = VTask.succeed("Hello from service");

      // Wrap fetchData with circuit breaker protection
      VTask<String> protectedTask = breaker.protect(fetchData);

      String result = protectedTask.run();
      assertThat(result).isEqualTo("Hello from service");
    }
  }

  // ===========================================================================
  // Part 2: Circuit Breaker State Transitions
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Circuit Breaker State Transitions")
  class StateTransitions {

    /**
     * Exercise 3: Verify the circuit opens after threshold failures
     *
     * <p>Create a circuit breaker with failureThreshold=3. Send 3 failing tasks through it, then
     * verify that the circuit has transitioned to OPEN (or HALF_OPEN if enough time passes).
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

      // Execute the protected failing task 3 times using runSafe()
      breaker.protect(failingTask).runSafe();
      breaker.protect(failingTask).runSafe();
      breaker.protect(failingTask).runSafe();

      // After 3 failures, the circuit should be OPEN
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    /**
     * Exercise 4: Handle CircuitOpenException
     *
     * <p>When the circuit is open, protected calls are rejected immediately with a
     * CircuitOpenException. Use runSafe() to capture this exception and verify its properties.
     */
    @Test
    @DisplayName("Exercise 4: Handle CircuitOpenException")
    void exercise4_handleCircuitOpenException() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      // Manually trip the circuit open
      breaker.tripOpen();

      VTask<String> task = VTask.succeed("Should not execute");

      // Protect the task and execute it with runSafe()
      Try<String> result = breaker.protect(task).runSafe();

      assertThat(result.isFailure()).isTrue();

      // Extract the exception and verify it is a CircuitOpenException
      Throwable error = result.fold(value -> null, ex -> ex);

      assertThat(error).isInstanceOf(CircuitOpenException.class);
      CircuitOpenException coe = (CircuitOpenException) error;
      assertThat(coe.status()).isEqualTo(CircuitBreaker.Status.OPEN);
    }
  }

  // ===========================================================================
  // Part 3: Fallback and Metrics
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Fallback and Metrics")
  class FallbackAndMetrics {

    /**
     * Exercise 5: Use protectWithFallback
     *
     * <p>protectWithFallback() provides a fallback value when the circuit is open, instead of
     * throwing CircuitOpenException. The fallback function receives the exception.
     */
    @Test
    @DisplayName("Exercise 5: Use protectWithFallback for graceful degradation")
    void exercise5_protectWithFallback() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      breaker.tripOpen();

      VTask<String> task = VTask.succeed("Primary response");

      // Use breaker.protectWithFallback() with a fallback that returns "Fallback response"
      VTask<String> protectedTask = breaker.protectWithFallback(task, error -> "Fallback response");

      String result = protectedTask.run();
      assertThat(result).isEqualTo("Fallback response");
    }

    /**
     * Exercise 6: Check circuit breaker metrics
     *
     * <p>CircuitBreaker.metrics() returns a CircuitBreakerMetrics record with operational counters:
     * totalCalls, successfulCalls, failedCalls, rejectedCalls, and stateTransitions.
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

      // Get the metrics from the circuit breaker
      CircuitBreakerMetrics metrics = breaker.metrics();

      assertThat(metrics.totalCalls()).isEqualTo(3);
      assertThat(metrics.successfulCalls()).isEqualTo(2);
      assertThat(metrics.failedCalls()).isEqualTo(1);
    }
  }

  // ===========================================================================
  // Bonus: Complete Example
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * This test demonstrates a complete circuit breaker workflow:
     *
     * <ol>
     *   <li>Create a circuit breaker with a low failure threshold
     *   <li>Protect a service call
     *   <li>Observe it opening after failures
     *   <li>Use fallback when open
     * </ol>
     *
     * <p>This is provided as a reference - no exercise to complete.
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

  /**
   * Congratulations! You've completed Tutorial 01: Circuit Breaker Pattern
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to configure a CircuitBreaker with custom thresholds and durations
   *   <li>How to protect VTask operations with circuit breaker protection
   *   <li>How the circuit transitions from CLOSED to OPEN after reaching the failure threshold
   *   <li>How CircuitOpenException is thrown when the circuit is open
   *   <li>How to use protectWithFallback for graceful degradation
   *   <li>How to inspect CircuitBreakerMetrics for observability
   * </ul>
   *
   * <p>Next: Tutorial 02 - Saga Pattern for distributed compensation
   */
}
