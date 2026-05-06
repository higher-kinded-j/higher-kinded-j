// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.Resilience;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryEvent;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 03: Retry, Bulkhead, and Resilience Composition.
 *
 * <p>Pain → Promise. Retry-with-backoff and concurrent-call limits (bulkheads) are two of the most
 * common production resilience patterns, and the most common to roll by hand badly. Hand-rolled
 * retry loses the policy in a {@code for} loop; hand-rolled bulkheads use a semaphore that is easy
 * to leak.
 *
 * <pre>
 *   var retry = Retry.create(RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100)));
 *   var bulkhead = Bulkhead.create(maxConcurrent: 10);
 *
 *   VTask&lt;Response&gt; protected = Resilience.combine(retry, bulkhead, breaker)
 *       .protect(() -&gt; service.fetch());
 * </pre>
 *
 * <p>Compose with Tutorial 01's CircuitBreaker via {@code Resilience.combine(...)} and the order
 * matters: typically circuit breaker first (so retries do not hit an open circuit), then retry,
 * then bulkhead.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>RetryPolicy configures retry behaviour (attempts, delay, backoff strategy)
 *   <li>Retry.retryTask() wraps a VTask with retry logic
 *   <li>RetryEvent provides observability into retry attempts
 *   <li>Bulkhead limits concurrent access to protect shared resources
 *   <li>ResilienceBuilder composes multiple patterns in the correct order
 * </ul>
 *
 * <p>Requirements: Java 25+ (virtual threads and structured concurrency)
 *
 * <p>Replace each {@code answerRequired()} call with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 03: Retry, Bulkhead, and Resilience Composition")
public class Tutorial03_RetryBulkheadResilience {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ===========================================================================
  // Part 1: Retry with RetryPolicy
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Retry with RetryPolicy")
  class RetryWithPolicy {

    /**
     * Exercise 1: Retry a flaky VTask.
     *
     * <pre>
     *   // Nudge:    RetryPolicy.fixed(maxAttempts, delay) and Retry.retryTask(task, policy).
     *   // Strategy: RetryPolicy.fixed(3, Duration.ofMillis(10))
     *   //           Retry.retryTask(unstableTask, policy)
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: Retry a failing task until success")
    void exercise1_retryTask() {
      AtomicInteger attempts = new AtomicInteger(0);

      VTask<String> unstableTask =
          VTask.of(
              () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                  throw new RuntimeException("Attempt " + attempt + " failed");
                }
                return "Success on attempt " + attempt;
              });

      // TODO: Create a fixed retry policy with 3 attempts and 10ms delay.
      //       Hint: RetryPolicy.fixed(3, Duration.ofMillis(10))
      RetryPolicy policy = answerRequired();

      // TODO: Wrap unstableTask with retry logic using Retry.retryTask(unstableTask, policy)
      VTask<String> retriedTask = answerRequired();

      String result = retriedTask.run();
      assertThat(result).isEqualTo("Success on attempt 3");
      assertThat(attempts.get()).isEqualTo(3);
    }

    /**
     * Exercise 2: Monitor retries with {@code onRetry} listener.
     *
     * <pre>
     *   // Nudge:    Build the policy with .onRetry(events::add).
     *   // Strategy: RetryPolicy.fixed(3, Duration.ofMillis(10)).onRetry(events::add)
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: Monitor retry attempts with RetryEvent")
    void exercise2_monitorRetries() {
      AtomicInteger callCount = new AtomicInteger(0);
      List<RetryEvent> events = Collections.synchronizedList(new ArrayList<>());

      VTask<String> unstableTask =
          VTask.of(
              () -> {
                int attempt = callCount.incrementAndGet();
                if (attempt < 3) {
                  throw new RuntimeException("Fail " + attempt);
                }
                return "done";
              });

      // TODO: Create a fixed retry policy and attach an onRetry listener that records each
      //       RetryEvent into the `events` list. Hint: .onRetry(events::add)
      RetryPolicy policy = answerRequired();

      VTask<String> retriedTask = Retry.retryTask(unstableTask, policy);
      retriedTask.run();

      // The listener is called before each retry (not the first attempt, not the final success)
      assertThat(events).hasSize(2);
      assertThat(events.get(0).attemptNumber()).isEqualTo(1);
      assertThat(events.get(1).attemptNumber()).isEqualTo(2);
    }
  }

  // ===========================================================================
  // Part 2: Bulkhead
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Bulkhead")
  class BulkheadProtection {

    /**
     * Exercise 3: Bulkhead-protect a VTask.
     *
     * <pre>
     *   // Nudge:    Bulkhead.withMaxConcurrent(N) creates the bulkhead; .protect wraps a task.
     *   // Strategy: Bulkhead.withMaxConcurrent(5)
     *   //           bulkhead.protect(task)
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: Protect a VTask with a Bulkhead")
    void exercise3_bulkheadProtect() {
      // TODO: Create a Bulkhead allowing at most 5 concurrent executions
      //       Hint: Bulkhead.withMaxConcurrent(5)
      Bulkhead bulkhead = answerRequired();

      VTask<String> task = VTask.succeed("protected result");

      // TODO: Protect the task with the bulkhead using bulkhead.protect(task)
      VTask<String> protectedTask = answerRequired();

      String result = protectedTask.run();
      assertThat(result).isEqualTo("protected result");

      // Verify the bulkhead is not holding any permits after completion
      assertThat(bulkhead.availablePermits()).isEqualTo(5);
    }
  }

  // ===========================================================================
  // Part 3: Composing Resilience Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Composing Resilience Patterns")
  class ComposingPatterns {

    /**
     * Exercise 4: Compose patterns with ResilienceBuilder.
     *
     * <pre>
     *   // Nudge:    Builder pattern; chain withCircuitBreaker / withRetry / withFallback / build.
     *   // Strategy: Resilience.&lt;String&gt;builder(serviceCall)
     *   //               .withCircuitBreaker(breaker)
     *   //               .withRetry(RetryPolicy.fixed(3, Duration.ofMillis(10)))
     *   //               .withFallback(error -&gt; "fallback value")
     *   //               .build()
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: Compose patterns with ResilienceBuilder")
    void exercise4_resilienceBuilder() {
      CircuitBreaker breaker =
          CircuitBreaker.create(CircuitBreakerConfig.builder().failureThreshold(5).build());

      VTask<String> serviceCall = VTask.succeed("service response");

      // TODO: Build a resilient VTask<String> using Resilience.<String>builder(serviceCall):
      //       .withCircuitBreaker(breaker)
      //       .withRetry(RetryPolicy.fixed(3, Duration.ofMillis(10)))
      //       .withFallback(error -> "fallback value")
      //       .build()
      VTask<String> resilientTask = answerRequired();

      String result = resilientTask.run();
      assertThat(result).isEqualTo("service response");
    }

    /**
     * Exercise 5: convenience {@code withCircuitBreakerAndRetry}.
     *
     * <pre>
     *   // Nudge:    Single call combining the two most common patterns.
     *   // Strategy: Resilience.withCircuitBreakerAndRetry(unstableTask, breaker, policy)
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: Use Resilience.withCircuitBreakerAndRetry")
    void exercise5_convenienceCombination() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(10));

      AtomicInteger attempts = new AtomicInteger(0);
      VTask<String> unstableTask =
          VTask.of(
              () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 2) {
                  throw new RuntimeException("Temporary failure");
                }
                return "recovered";
              });

      // TODO: Use Resilience.withCircuitBreakerAndRetry(unstableTask, breaker, policy)
      VTask<String> resilientTask = answerRequired();

      String result = resilientTask.run();
      assertThat(result).isEqualTo("recovered");
      assertThat(attempts.get()).isEqualTo(2);
    }
  }

  // ===========================================================================
  // Bonus: Complete Example
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * This test demonstrates combining all resilience patterns:
     *
     * <ol>
     *   <li>Circuit Breaker to stop calling a failing service
     *   <li>Retry to handle transient failures
     *   <li>Bulkhead to limit concurrency
     *   <li>Fallback for graceful degradation
     * </ol>
     *
     * <p>This is provided as a reference - no exercise to complete.
     */
    @Test
    @DisplayName("Complete resilience composition example")
    void completeResilienceComposition() {
      AtomicInteger callCount = new AtomicInteger(0);

      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(5)
                  .openDuration(Duration.ofSeconds(30))
                  .build());

      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(10);

      RetryPolicy retryPolicy =
          RetryPolicy.fixed(3, Duration.ofMillis(10))
              .onRetry(event -> {} /* logging would go here */);

      VTask<String> unreliableService =
          VTask.of(
              () -> {
                int attempt = callCount.incrementAndGet();
                if (attempt == 1) {
                  throw new RuntimeException("Transient error");
                }
                return "Success after retry";
              });

      // Compose all patterns using the builder
      VTask<String> resilientCall =
          Resilience.<String>builder(unreliableService)
              .withCircuitBreaker(breaker)
              .withRetry(retryPolicy)
              .withBulkhead(bulkhead)
              .withTimeout(Duration.ofSeconds(5))
              .withFallback(ex -> "cached response")
              .build();

      String result = resilientCall.run();
      assertThat(result).isEqualTo("Success after retry");
      assertThat(callCount.get()).isEqualTo(2);

      // Circuit breaker stayed closed (not enough failures)
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }
  }

  /**
   * Congratulations! You've completed Tutorial 03: Retry, Bulkhead, and Resilience Composition
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to configure RetryPolicy with fixed, exponential, and custom backoff strategies
   *   <li>How to use Retry.retryTask() to wrap VTasks with retry logic
   *   <li>How to monitor retries with RetryEvent and onRetry listeners
   *   <li>How to create Bulkheads to limit concurrent access to resources
   *   <li>How to compose multiple patterns with ResilienceBuilder
   *   <li>How to use Resilience convenience methods for common combinations
   * </ul>
   *
   * <p>Next: Tutorial 04 - Path-based resilience with VTaskPath, VStreamPath, and VTaskContext
   */
}
