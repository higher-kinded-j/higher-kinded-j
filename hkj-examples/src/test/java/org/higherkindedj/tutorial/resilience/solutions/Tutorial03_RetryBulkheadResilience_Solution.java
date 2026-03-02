// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience.solutions;

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
 * Tutorial 03: Retry, Bulkhead, and Resilience Composition - SOLUTIONS
 *
 * <p>This file contains the complete solutions for all exercises in
 * Tutorial03_RetryBulkheadResilience.java.
 */
@DisplayName("Tutorial 03: Retry, Bulkhead, and Resilience Composition (Solutions)")
public class Tutorial03_RetryBulkheadResilience_Solution {

  @Nested
  @DisplayName("Part 1: Retry with RetryPolicy")
  class RetryWithPolicy {

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
