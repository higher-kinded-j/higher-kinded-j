// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.resilience;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.CircuitBreakerMetrics;
import org.higherkindedj.hkt.resilience.Resilience;
import org.higherkindedj.hkt.resilience.ResilienceBuilder;
import org.higherkindedj.hkt.resilience.RetryEvent;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Demonstrates combining multiple resilience patterns into a single protected operation.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>{@link ResilienceBuilder} composing circuit breaker + retry + bulkhead + timeout + fallback
 *   <li>Per-element retry with {@link Resilience#withRetryPerElement}
 *   <li>Monitoring via {@link RetryEvent} listener and {@link CircuitBreakerMetrics}
 *   <li>Printing a metrics summary at the end
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.resilience.ResilientServiceExample}
 */
public class ResilientServiceExample {

  public static void main(String[] args) {
    System.out.println("=== Resilient Service Example ===\n");

    resilienceBuilderExample();
    perElementRetryExample();
    retryEventMonitoringExample();
    fullMetricsSummaryExample();
  }

  // ===== ResilienceBuilder: Composing Multiple Patterns =====

  private static void resilienceBuilderExample() {
    System.out.println("--- ResilienceBuilder: Combined Patterns ---");

    // Create the individual resilience components
    CircuitBreaker circuitBreaker =
        CircuitBreaker.create(
            CircuitBreakerConfig.builder()
                .failureThreshold(5)
                .openDuration(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(5))
                .build());

    RetryPolicy retryPolicy =
        RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(100))
            .withMaxDelay(Duration.ofSeconds(2));

    Bulkhead bulkhead = Bulkhead.withMaxConcurrent(10);

    AtomicInteger callCount = new AtomicInteger(0);

    // Simulate a flaky service that fails the first 2 calls, then succeeds
    VTask<String> flakyService =
        VTask.of(
            () -> {
              int call = callCount.incrementAndGet();
              if (call <= 2) {
                throw new RuntimeException("Transient error on call " + call);
              }
              return "Response from API (call " + call + ")";
            });

    // Compose all patterns using the builder.
    // The builder applies them in the correct order:
    //   Timeout (outermost) -> Bulkhead -> Retry -> Circuit Breaker (innermost)
    VTask<String> resilientCall =
        Resilience.<String>builder(flakyService)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retryPolicy)
            .withBulkhead(bulkhead)
            .withTimeout(Duration.ofSeconds(30))
            .withFallback(ex -> "Fallback: service unavailable (" + ex.getMessage() + ")")
            .build();

    // Execute the resilient call
    String result = resilientCall.run();
    System.out.println("  Result: " + result);
    System.out.println("  Total service calls: " + callCount.get());
    System.out.println("  Circuit breaker status: " + circuitBreaker.currentStatus());
    System.out.println("  Bulkhead available permits: " + bulkhead.availablePermits());

    System.out.println();
  }

  // ===== Per-Element Retry =====

  private static void perElementRetryExample() {
    System.out.println("--- Per-Element Retry ---");

    AtomicInteger totalAttempts = new AtomicInteger(0);

    // Simulate a per-element processing function that intermittently fails.
    // Each element gets its own retry budget.
    Function<String, VTask<String>> processElement =
        item ->
            VTask.of(
                () -> {
                  int attempt = totalAttempts.incrementAndGet();
                  // Fail roughly 50% of the time
                  if (attempt % 2 == 0) {
                    throw new RuntimeException(
                        "Processing failed for '" + item + "' (attempt " + attempt + ")");
                  }
                  return item.toUpperCase();
                });

    // Wrap the function with per-element retry using Resilience.withRetryPerElement.
    // Each element is retried independently according to the policy.
    RetryPolicy perElementPolicy = RetryPolicy.fixed(3, Duration.ofMillis(50));
    Function<String, VTask<String>> resilientProcess =
        Resilience.withRetryPerElement(processElement, perElementPolicy);

    // Process a list of items, each with its own retry budget
    List<String> items = List.of("alpha", "beta", "gamma", "delta");
    System.out.println("  Processing " + items.size() + " items with per-element retry:");

    for (String item : items) {
      try {
        String result = resilientProcess.apply(item).run();
        System.out.println("    " + item + " -> " + result);
      } catch (RuntimeException e) {
        System.out.println("    " + item + " -> FAILED after retries: " + e.getMessage());
      }
    }

    System.out.println("  Total processing attempts: " + totalAttempts.get());
    System.out.println();
  }

  // ===== Retry Event Monitoring =====

  private static void retryEventMonitoringExample() {
    System.out.println("--- Retry Event Monitoring ---");

    AtomicInteger retryCount = new AtomicInteger(0);
    AtomicInteger callCount = new AtomicInteger(0);

    // Create a retry policy with an onRetry listener that logs each retry event.
    // The listener receives a RetryEvent with attempt number, exception, and delay info.
    RetryPolicy monitoredPolicy =
        RetryPolicy.exponentialBackoff(5, Duration.ofMillis(50))
            .onRetry(
                event -> {
                  retryCount.incrementAndGet();
                  System.out.println(
                      "    [RetryEvent] Attempt "
                          + event.attemptNumber()
                          + " failed: "
                          + event.lastException().getMessage()
                          + " | Next delay: "
                          + event.nextDelay().toMillis()
                          + "ms"
                          + " | Time: "
                          + event.timestamp());
                });

    // Operation that fails 3 times then succeeds
    VTask<String> flakyOperation =
        VTask.of(
            () -> {
              int call = callCount.incrementAndGet();
              if (call <= 3) {
                throw new RuntimeException("Transient failure #" + call);
              }
              return "Success on attempt " + call;
            });

    // Combine with circuit breaker to demonstrate layered monitoring
    CircuitBreaker breaker =
        CircuitBreaker.create(
            CircuitBreakerConfig.builder()
                .failureThreshold(10)
                .openDuration(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(5))
                .build());

    VTask<String> resilientOp =
        Resilience.<String>builder(flakyOperation)
            .withCircuitBreaker(breaker)
            .withRetry(monitoredPolicy)
            .build();

    System.out.println("  Executing operation with retry monitoring:");
    String result = resilientOp.run();
    System.out.println("  Result: " + result);
    System.out.println("  Retries observed: " + retryCount.get());

    // Also check circuit breaker metrics
    CircuitBreakerMetrics cbMetrics = breaker.metrics();
    System.out.println("  CB total calls: " + cbMetrics.totalCalls());
    System.out.println("  CB successful:  " + cbMetrics.successfulCalls());
    System.out.println("  CB failed:      " + cbMetrics.failedCalls());

    System.out.println();
  }

  // ===== Full Metrics Summary =====

  private static void fullMetricsSummaryExample() {
    System.out.println("--- Full Metrics Summary ---");

    // Set up all resilience components
    CircuitBreaker breaker =
        CircuitBreaker.create(
            CircuitBreakerConfig.builder()
                .failureThreshold(5)
                .openDuration(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(5))
                .build());

    AtomicInteger retryEvents = new AtomicInteger(0);

    RetryPolicy retryPolicy =
        RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(50))
            .onRetry(event -> retryEvents.incrementAndGet());

    Bulkhead bulkhead = Bulkhead.withMaxConcurrent(5);

    AtomicInteger callIndex = new AtomicInteger(0);

    // Simulate 10 service calls with varying outcomes
    System.out.println("  Running 10 service calls through full resilience stack:");
    int successes = 0;
    int failures = 0;

    for (int i = 1; i <= 10; i++) {
      // Each iteration creates a fresh task to simulate independent calls
      VTask<String> serviceCall =
          VTask.of(
              () -> {
                int idx = callIndex.incrementAndGet();
                // Fail about 30% of calls
                if (idx % 3 == 0) {
                  throw new RuntimeException("Service error on call " + idx);
                }
                return "OK-" + idx;
              });

      VTask<String> resilientCall =
          Resilience.<String>builder(serviceCall)
              .withCircuitBreaker(breaker)
              .withRetry(retryPolicy)
              .withBulkhead(bulkhead)
              .withTimeout(Duration.ofSeconds(10))
              .withFallback(ex -> "FALLBACK")
              .build();

      String result = resilientCall.run();
      if ("FALLBACK".equals(result)) {
        failures++;
        System.out.println("    Call " + i + ": " + result + " (fallback)");
      } else {
        successes++;
        System.out.println("    Call " + i + ": " + result);
      }
    }

    // Print comprehensive metrics
    CircuitBreakerMetrics metrics = breaker.metrics();
    System.out.println("\n  === Metrics Summary ===");
    System.out.println("  Application-level:");
    System.out.println("    Requests succeeded:    " + successes);
    System.out.println("    Requests fell back:    " + failures);
    System.out.println("    Retry events fired:    " + retryEvents.get());
    System.out.println();
    System.out.println("  Circuit Breaker:");
    System.out.println("    Status:                " + breaker.currentStatus());
    System.out.println("    Total calls:           " + metrics.totalCalls());
    System.out.println("    Successful calls:      " + metrics.successfulCalls());
    System.out.println("    Failed calls:          " + metrics.failedCalls());
    System.out.println("    Rejected calls:        " + metrics.rejectedCalls());
    System.out.println("    State transitions:     " + metrics.stateTransitions());
    System.out.println();
    System.out.println("  Bulkhead:");
    System.out.println("    Available permits:     " + bulkhead.availablePermits());
    System.out.println("    Active count:          " + bulkhead.activeCount());
    System.out.println();
  }
}
