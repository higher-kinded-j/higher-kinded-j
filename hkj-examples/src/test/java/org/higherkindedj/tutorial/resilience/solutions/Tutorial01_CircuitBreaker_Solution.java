// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience.solutions;

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
 * Tutorial 01: Circuit Breaker Pattern - SOLUTIONS
 *
 * <p>This file contains the complete solutions for all exercises in Tutorial01_CircuitBreaker.java.
 */
@DisplayName("Tutorial 01: Circuit Breaker Pattern (Solutions)")
public class Tutorial01_CircuitBreaker_Solution {

  @Nested
  @DisplayName("Part 1: Creating and Configuring a Circuit Breaker")
  class CreatingCircuitBreakers {

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

    @Test
    @DisplayName("Exercise 4: Handle CircuitOpenException")
    void exercise4_handleCircuitOpenException() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      breaker.tripOpen();

      VTask<String> task = VTask.succeed("Should not execute");

      // SOLUTION: Protect and run against the open circuit
      Try<String> result = breaker.protect(task).runSafe();

      assertThat(result.isFailure()).isTrue();

      // SOLUTION: Extract the exception using fold
      Throwable error = result.fold(value -> null, ex -> ex);

      assertThat(error).isInstanceOf(CircuitOpenException.class);
      CircuitOpenException coe = (CircuitOpenException) error;
      assertThat(coe.status()).isEqualTo(CircuitBreaker.Status.OPEN);
    }
  }

  @Nested
  @DisplayName("Part 3: Fallback and Metrics")
  class FallbackAndMetrics {

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
