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
 * Tutorial 01: Circuit Breaker Pattern.
 *
 * <p>Pain → Promise. When a downstream service is failing, naively retrying every request makes
 * things worse: we hammer a struggling service, hold up our own threads, and turn a downstream
 * incident into a cascade. Hand-rolled circuit-breaker logic is one {@code AtomicInteger} per
 * service plus per-call {@code if (failures > threshold) throw} scaffolding.
 *
 * <p>{@link CircuitBreaker} captures the same state machine as a value:
 *
 * <pre>
 *   var breaker = CircuitBreaker.create(CircuitBreakerConfig.builder()
 *       .failureThreshold(3)
 *       .openDuration(Duration.ofSeconds(10))
 *       .build());
 *
 *   VTask&lt;Response&gt; protected = breaker.protect(() -&gt; service.fetch());
 * </pre>
 *
 * <p>Java idiom anchor: this is the same pattern as Resilience4j's CircuitBreaker, expressed as a
 * composable value rather than a per-call decorator. {@code protect}, {@code protectWithFallback},
 * and {@link CircuitBreakerMetrics} cover the common production needs.
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
 * <p>Replace each {@code answerRequired()} call with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 01: Circuit Breaker Pattern")
public class Tutorial01_CircuitBreaker {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ===========================================================================
  // Part 1: Creating and Configuring a Circuit Breaker
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating and Configuring a Circuit Breaker")
  class CreatingCircuitBreakers {

    /**
     * Exercise 1: Build a configured CircuitBreaker.
     *
     * <pre>
     *   // Nudge:    Builder pattern: failureThreshold(3), openDuration(Duration.ofMillis(100)).
     *   // Strategy: CircuitBreakerConfig.builder().failureThreshold(3)
     *   //              .openDuration(Duration.ofMillis(100)).build()
     *   //           CircuitBreaker.create(config)
     *   // Spoiler:  see hint above.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: Create a CircuitBreaker with custom config")
    void exercise1_createCircuitBreaker() {
      // TODO: Build a CircuitBreakerConfig with failureThreshold(3) and
      //       openDuration(Duration.ofMillis(100)) using CircuitBreakerConfig.builder()
      CircuitBreakerConfig config = answerRequired();

      // TODO: Create a CircuitBreaker from the config using CircuitBreaker.create(config)
      CircuitBreaker breaker = answerRequired();

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    /**
     * Exercise 2: protect() wraps a VTask in the breaker.
     *
     * <pre>
     *   // Nudge:    breaker.protect(task) returns a wrapped VTask.
     *   // Strategy: breaker.protect(fetchData)
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: Protect a VTask with the circuit breaker")
    void exercise2_protectVTask() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      VTask<String> fetchData = VTask.succeed("Hello from service");

      // TODO: Wrap fetchData with circuit breaker protection using breaker.protect(...)
      VTask<String> protectedTask = answerRequired();

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
     * Exercise 3: Open the circuit by hitting the threshold.
     *
     * <pre>
     *   // Nudge:    Run the protected failing task 3 times via runSafe so exceptions are caught.
     *   // Strategy: for (int i = 0; i &lt; 3; i++) { breaker.protect(failingTask).runSafe(); }
     *   // Spoiler:  exactly that.
     * </pre>
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

      // TODO: Execute the protected failing task 3 times.
      //       Use breaker.protect(failingTask).runSafe() to swallow exceptions
      //       so the loop can drive the circuit through 3 consecutive failures.
      answerRequired();

      // After 3 failures, the circuit should be OPEN
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    /**
     * Exercise 4: Capture {@link CircuitOpenException} from a tripped circuit.
     *
     * <pre>
     *   // Nudge:    Wrap the call in protect, then runSafe to get a Try.
     *   //           Extract the exception with fold.
     *   // Strategy: Try&lt;String&gt; result = breaker.protect(task).runSafe();
     *   //           Throwable error = result.fold(value -&gt; null, ex -&gt; ex);
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: Handle CircuitOpenException")
    void exercise4_handleCircuitOpenException() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      // Manually trip the circuit open
      breaker.tripOpen();

      VTask<String> task = VTask.succeed("Should not execute");

      // TODO: Protect the task and execute it with runSafe() to obtain Try<String>
      Try<String> result = answerRequired();

      assertThat(result.isFailure()).isTrue();

      // TODO: Extract the exception from the Try (hint: result.fold(value -> null, ex -> ex))
      Throwable error = answerRequired();

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
     * Exercise 5: Graceful degradation with {@code protectWithFallback}.
     *
     * <pre>
     *   // Nudge:    The fallback receives the exception and returns a value.
     *   // Strategy: breaker.protectWithFallback(task, error -&gt; "Fallback response")
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: Use protectWithFallback for graceful degradation")
    void exercise5_protectWithFallback() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      breaker.tripOpen();

      VTask<String> task = VTask.succeed("Primary response");

      // TODO: Use breaker.protectWithFallback(task, error -> "Fallback response")
      VTask<String> protectedTask = answerRequired();

      String result = protectedTask.run();
      assertThat(result).isEqualTo("Fallback response");
    }

    /**
     * Exercise 6: Inspect breaker metrics.
     *
     * <pre>
     *   // Nudge:    breaker.metrics() returns a snapshot record.
     *   // Strategy: breaker.metrics()
     *   // Spoiler:  exactly that.
     * </pre>
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

      // TODO: Get the CircuitBreakerMetrics from the breaker (hint: breaker.metrics())
      CircuitBreakerMetrics metrics = answerRequired();

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
