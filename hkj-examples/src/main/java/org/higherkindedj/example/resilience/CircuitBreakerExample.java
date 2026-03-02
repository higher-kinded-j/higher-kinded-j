// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.resilience;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.CircuitBreakerMetrics;
import org.higherkindedj.hkt.resilience.CircuitOpenException;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Demonstrates the Circuit Breaker resilience pattern using {@link CircuitBreaker}.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Creating a {@link CircuitBreaker} with custom {@link CircuitBreakerConfig}
 *   <li>Protecting a simulated HTTP call (a {@link VTask} that sometimes fails)
 *   <li>Sharing a single circuit breaker across multiple service endpoints returning different
 *       types
 *   <li>Fallback handling with {@link CircuitBreaker#protectWithFallback}
 *   <li>Monitoring metrics via {@link CircuitBreakerMetrics}
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.resilience.CircuitBreakerExample}
 */
public class CircuitBreakerExample {

  public static void main(String[] args) {
    System.out.println("=== Circuit Breaker Example ===\n");

    basicCircuitBreakerExample();
    sharedCircuitBreakerExample();
    fallbackExample();
    metricsMonitoringExample();
  }

  // ===== Basic Circuit Breaker =====

  private static void basicCircuitBreakerExample() {
    System.out.println("--- Basic Circuit Breaker ---");

    // Configure a circuit breaker:
    //   - Opens after 3 consecutive failures
    //   - Stays open for 1 second before allowing a probe
    //   - Each call has a 5-second timeout
    CircuitBreakerConfig config =
        CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .openDuration(Duration.ofSeconds(1))
            .callTimeout(Duration.ofSeconds(5))
            .build();

    CircuitBreaker breaker = CircuitBreaker.create(config);

    // Track how many times the simulated service is actually called
    AtomicInteger callCount = new AtomicInteger(0);

    // Simulate an HTTP GET that fails on the first 5 calls, then succeeds
    VTask<String> httpGet =
        VTask.of(
            () -> {
              int call = callCount.incrementAndGet();
              if (call <= 5) {
                throw new RuntimeException("HTTP 503: Service Unavailable (call " + call + ")");
              }
              return "HTTP 200: {\"status\": \"ok\"}";
            });

    // Protect the call with the circuit breaker
    VTask<String> protectedGet = breaker.protect(httpGet);

    // Make several calls to observe circuit breaker behavior
    for (int i = 1; i <= 8; i++) {
      try {
        String result = protectedGet.run();
        System.out.println(
            "  Call " + i + ": " + result + " (circuit: " + breaker.currentStatus() + ")");
      } catch (CircuitOpenException e) {
        // The circuit is open -- calls are rejected without hitting the service
        System.out.println(
            "  Call "
                + i
                + ": REJECTED - "
                + e.getMessage()
                + " (circuit: "
                + breaker.currentStatus()
                + ")");
      } catch (RuntimeException e) {
        System.out.println(
            "  Call "
                + i
                + ": FAILED - "
                + e.getMessage()
                + " (circuit: "
                + breaker.currentStatus()
                + ")");
      }
    }

    System.out.println("  Actual service calls made: " + callCount.get());
    System.out.println();
  }

  // ===== Shared Circuit Breaker Across Endpoints =====

  private static void sharedCircuitBreakerExample() {
    System.out.println("--- Shared Circuit Breaker Across Endpoints ---");

    // A single circuit breaker protects all calls to the same downstream service.
    // The protect() method is generic, so one breaker can guard calls returning
    // different types (String, Integer, Boolean, etc.).
    CircuitBreaker serviceBreaker =
        CircuitBreaker.create(
            CircuitBreakerConfig.builder()
                .failureThreshold(2)
                .openDuration(Duration.ofMillis(500))
                .callTimeout(Duration.ofSeconds(5))
                .build());

    // Endpoint 1: GET /users -> returns a String (JSON)
    VTask<String> getUsers =
        serviceBreaker.protect(
            VTask.of(
                () -> {
                  System.out.println("    [GET /users] called");
                  return "{\"users\": [\"Alice\", \"Bob\"]}";
                }));

    // Endpoint 2: GET /users/count -> returns an Integer
    VTask<Integer> getUserCount =
        serviceBreaker.protect(
            VTask.of(
                () -> {
                  System.out.println("    [GET /users/count] called");
                  return 42;
                }));

    // Endpoint 3: POST /health -> returns a Boolean
    VTask<Boolean> healthCheck =
        serviceBreaker.protect(
            VTask.of(
                () -> {
                  System.out.println("    [POST /health] called");
                  return true;
                }));

    // All three endpoints share the same circuit breaker
    try {
      String users = getUsers.run();
      System.out.println("  Users: " + users);

      Integer count = getUserCount.run();
      System.out.println("  User count: " + count);

      Boolean healthy = healthCheck.run();
      System.out.println("  Healthy: " + healthy);

      System.out.println("  Circuit status: " + serviceBreaker.currentStatus());
    } catch (RuntimeException e) {
      System.out.println("  Error: " + e.getMessage());
    }

    // Now trip the breaker manually to show all endpoints are affected
    serviceBreaker.tripOpen();
    System.out.println("  After tripping open:");

    for (String endpoint : new String[] {"/users", "/users/count", "/health"}) {
      try {
        serviceBreaker.protect(VTask.succeed("test")).run();
        System.out.println("    " + endpoint + ": allowed");
      } catch (CircuitOpenException e) {
        System.out.println("    " + endpoint + ": REJECTED (circuit open)");
      }
    }

    System.out.println();
  }

  // ===== Fallback Handling =====

  private static void fallbackExample() {
    System.out.println("--- Fallback Handling ---");

    CircuitBreaker breaker =
        CircuitBreaker.create(
            CircuitBreakerConfig.builder()
                .failureThreshold(2)
                .openDuration(Duration.ofSeconds(1))
                .callTimeout(Duration.ofSeconds(5))
                .build());

    AtomicInteger callCount = new AtomicInteger(0);

    // A service that always fails (simulating a persistent outage)
    VTask<String> unreliableService =
        VTask.of(
            () -> {
              callCount.incrementAndGet();
              throw new RuntimeException("Service is down");
            });

    // protectWithFallback: when the circuit is open, return a cached/default response
    // instead of throwing CircuitOpenException
    VTask<String> withFallback =
        breaker.protectWithFallback(
            unreliableService,
            ex -> {
              if (ex instanceof CircuitOpenException) {
                return "{\"source\": \"cache\", \"data\": \"stale but available\"}";
              }
              return "{\"error\": \"" + ex.getMessage() + "\"}";
            });

    // First 2 calls will fail and count toward the threshold
    for (int i = 1; i <= 5; i++) {
      try {
        String result = withFallback.run();
        System.out.println(
            "  Call " + i + ": " + result + " (circuit: " + breaker.currentStatus() + ")");
      } catch (RuntimeException e) {
        System.out.println(
            "  Call "
                + i
                + ": FAILED - "
                + e.getMessage()
                + " (circuit: "
                + breaker.currentStatus()
                + ")");
      }
    }

    System.out.println("  Actual service calls: " + callCount.get());
    System.out.println(
        "  Note: After the circuit opens, fallback is returned without calling the service.");
    System.out.println();
  }

  // ===== Monitoring Metrics =====

  private static void metricsMonitoringExample() {
    System.out.println("--- Monitoring Metrics ---");

    CircuitBreaker breaker =
        CircuitBreaker.create(
            CircuitBreakerConfig.builder()
                .failureThreshold(3)
                .openDuration(Duration.ofSeconds(2))
                .callTimeout(Duration.ofSeconds(5))
                .build());

    AtomicInteger callNumber = new AtomicInteger(0);

    // Simulate a mix of successes and failures
    VTask<String> service =
        VTask.of(
            () -> {
              int n = callNumber.incrementAndGet();
              // Fail on calls 3, 4, 5
              if (n >= 3 && n <= 5) {
                throw new RuntimeException("Failure on call " + n);
              }
              return "OK-" + n;
            });

    VTask<String> protectedService = breaker.protect(service);

    // Make 8 calls to generate some metrics
    for (int i = 1; i <= 8; i++) {
      try {
        String result = protectedService.run();
        System.out.println("  Call " + i + ": " + result);
      } catch (CircuitOpenException e) {
        System.out.println("  Call " + i + ": REJECTED (circuit open)");
      } catch (RuntimeException e) {
        System.out.println("  Call " + i + ": FAILED - " + e.getMessage());
      }
    }

    // Inspect the metrics snapshot
    CircuitBreakerMetrics metrics = breaker.metrics();
    System.out.println("\n  Metrics Snapshot:");
    System.out.println("    Total calls:        " + metrics.totalCalls());
    System.out.println("    Successful calls:   " + metrics.successfulCalls());
    System.out.println("    Failed calls:       " + metrics.failedCalls());
    System.out.println("    Rejected calls:     " + metrics.rejectedCalls());
    System.out.println("    State transitions:  " + metrics.stateTransitions());
    System.out.println("    Last state change:  " + metrics.lastStateChange());
    System.out.println("    Current status:     " + breaker.currentStatus());
    System.out.println();
  }
}
