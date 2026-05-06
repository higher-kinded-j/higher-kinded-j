// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.resilience;

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
 * Solution for Tutorial03 RetryBulkheadResilience — teaching-solution format.
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
@DisplayName("Tutorial 03: Retry, Bulkhead, and Resilience Composition (Solutions)")
public class Tutorial03_RetryBulkheadResilience_Solution {

  @Nested
  @DisplayName("Part 1: Retry with RetryPolicy")
  class RetryWithPolicy {

    /**
     * Why this is idiomatic: {@code Retry.retryTask(task, policy)} wraps a VTask with retry logic.
     * The policy controls attempts and delay; the task runs until success or attempts are
     * exhausted.
     *
     * <p>Alternative: a try/catch loop with manual sleeps. Same outcome; the combinator centralises
     * the policy.
     *
     * <p>Common wrong attempt: retry indefinitely. Always cap attempts — unbounded retries hide
     * real failures and exhaust resources.
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

      // SOLUTION: Create a fixed retry policy
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(10));

      // SOLUTION: Wrap the task with retry logic
      VTask<String> retriedTask = Retry.retryTask(unstableTask, policy);

      String result = retriedTask.run();
      assertThat(result).isEqualTo("Success on attempt 3");
      assertThat(attempts.get()).isEqualTo(3);
    }

    /**
     * Why this is idiomatic: {@code policy.onRetry(listener)} fires a {@code RetryEvent} per
     * attempt. Wire it to logging or metrics to see exactly when and why retries happen.
     *
     * <p>Alternative: instrument the task itself. Bleeds the retry concern into the business logic;
     * the listener keeps it separate.
     *
     * <p>Common wrong attempt: assume the listener fires for the final success. It fires for
     * retries; success is the absence of further events.
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

      // SOLUTION: Create policy with onRetry listener
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(10)).onRetry(events::add);

      VTask<String> retriedTask = Retry.retryTask(unstableTask, policy);
      retriedTask.run();

      assertThat(events).hasSize(2);
      assertThat(events.get(0).attemptNumber()).isEqualTo(1);
      assertThat(events.get(1).attemptNumber()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Part 2: Bulkhead")
  class BulkheadProtection {

    /**
     * Why this is idiomatic: {@code Bulkhead.withMaxConcurrent(n)} caps simultaneous executions;
     * {@code bulkhead.protect(task)} acquires a permit, runs the task, and releases on completion.
     *
     * <p>Alternative: a manual {@code Semaphore}. Same idea; the bulkhead is the named pattern with
     * consistent acquire/release semantics.
     *
     * <p>Common wrong attempt: pick {@code maxConcurrent} too high. The bulkhead is the protection;
     * tune it to the downstream service's actual capacity.
     */
    @Test
    @DisplayName("Exercise 3: Protect a VTask with a Bulkhead")
    void exercise3_bulkheadProtect() {
      // SOLUTION: Create a Bulkhead with max 5 concurrent executions
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(5);

      VTask<String> task = VTask.succeed("protected result");

      // SOLUTION: Protect the task with the bulkhead
      VTask<String> protectedTask = bulkhead.protect(task);

      String result = protectedTask.run();
      assertThat(result).isEqualTo("protected result");

      assertThat(bulkhead.availablePermits()).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("Part 3: Composing Resilience Patterns")
  class ComposingPatterns {

    /**
     * Why this is idiomatic: {@code Resilience.builder(task)} composes circuit breaker, retry, and
     * fallback into one resilient task. Each pattern lives in its own layer; the builder wires them
     * in the right order.
     *
     * <p>Alternative: hand-stack the patterns by chaining wrappers. Tedious and easy to get the
     * order wrong (retry inside the breaker, fallback outside).
     *
     * <p>Common wrong attempt: skip one of the layers. Each pattern handles a different failure
     * mode; combine all three for production-grade resilience.
     */
    @Test
    @DisplayName("Exercise 4: Compose patterns with ResilienceBuilder")
    void exercise4_resilienceBuilder() {
      CircuitBreaker breaker =
          CircuitBreaker.create(CircuitBreakerConfig.builder().failureThreshold(5).build());

      VTask<String> serviceCall = VTask.succeed("service response");

      // SOLUTION: Build a resilient task using ResilienceBuilder
      VTask<String> resilientTask =
          Resilience.<String>builder(serviceCall)
              .withCircuitBreaker(breaker)
              .withRetry(RetryPolicy.fixed(3, Duration.ofMillis(10)))
              .withFallback(error -> "fallback value")
              .build();

      String result = resilientTask.run();
      assertThat(result).isEqualTo("service response");
    }

    /**
     * Why this is idiomatic: {@code Resilience.withCircuitBreakerAndRetry} is the named convenience
     * for the most common pair. One call, both patterns, sane order.
     *
     * <p>Alternative: the full builder. Same answer; the convenience is shorter.
     *
     * <p>Common wrong attempt: assume retry runs outside the circuit breaker. The convention is
     * breaker on the outside, retry on the inside — a tripped breaker should not be retried.
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

      // SOLUTION: Use the convenience method for circuit breaker + retry
      VTask<String> resilientTask =
          Resilience.withCircuitBreakerAndRetry(unstableTask, breaker, policy);

      String result = resilientTask.run();
      assertThat(result).isEqualTo("recovered");
      assertThat(attempts.get()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * Why this is idiomatic: a complete resilience stack — circuit breaker, bulkhead, retry — all
     * composed via the builder. Each pattern handles a different failure shape; together they
     * shield the downstream from surges and the application from outages.
     *
     * <p>Alternative: a single ad-hoc retry-with-timeout. Catches some failures; misses
     * concurrent-call surges and tripped-circuit signals.
     *
     * <p>Common wrong attempt: configure each layer with default settings. Defaults are starting
     * points; tune thresholds, capacity, and policy to the actual downstream characteristics.
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

      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }
  }
}
