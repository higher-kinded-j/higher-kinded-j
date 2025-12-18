// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.higherkindedj.hkt.effect.CompletableFuturePath;

/**
 * Examples demonstrating CompletableFuturePath for async computations.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Creating async computations with {@code supplyAsync}
 *   <li>Chaining async operations with {@code map} and {@code via}
 *   <li>Parallel composition with {@code zipWith}
 *   <li>Error handling with {@code recover} and {@code recoverWith}
 *   <li>Timeout handling
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.CompletableFuturePathExample}
 */
public class CompletableFuturePathExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: CompletableFuturePath ===\n");

    basicAsyncExamples();
    chainingAsyncOperations();
    parallelComposition();
    errorHandling();
    timeoutHandling();
  }

  private static void basicAsyncExamples() {
    System.out.println("--- Basic Async Operations ---");

    // Already completed future
    CompletableFuturePath<Integer> completed = CompletableFuturePath.completed(42);
    System.out.println("Completed immediately: " + completed.join());

    // Async computation
    CompletableFuturePath<String> async =
        CompletableFuturePath.supplyAsync(
            () -> {
              System.out.println("  [Running on: " + Thread.currentThread().getName() + "]");
              sleep(100);
              return "Async result";
            });

    System.out.println("Waiting for async...");
    System.out.println("Result: " + async.join());

    // From existing CompletableFuture
    CompletableFuture<Double> existingFuture = CompletableFuture.supplyAsync(() -> Math.PI);
    CompletableFuturePath<Double> fromExisting = CompletableFuturePath.fromFuture(existingFuture);
    System.out.println("From existing future: " + fromExisting.join());

    // Check status
    System.out.println("Is done? " + completed.isDone()); // true

    System.out.println();
  }

  private static void chainingAsyncOperations() {
    System.out.println("--- Chaining Async Operations ---");

    // Simulated async user service
    CompletableFuturePath<User> userFetch =
        CompletableFuturePath.supplyAsync(
            () -> {
              System.out.println("  [Fetching user...]");
              sleep(100);
              return new User("user-123", "Alice", "alice@example.com");
            });

    // Chain: fetch user -> fetch their orders -> get first order
    CompletableFuturePath<String> orderSummary =
        userFetch
            .map(user -> user.name()) // Extract name
            .via(
                name ->
                    CompletableFuturePath.supplyAsync(
                        () -> {
                          System.out.println("  [Fetching orders for " + name + "...]");
                          sleep(100);
                          return new Order("ord-456", name, "Widget Pro");
                        }))
            .map(order -> "Order " + order.id() + " for " + order.customerName());

    System.out.println("Chained result: " + orderSummary.join());

    // Peek for logging without affecting the chain
    CompletableFuturePath<Integer> withLogging =
        CompletableFuturePath.completed(100)
            .peek(v -> System.out.println("  [Log] Value is: " + v))
            .map(v -> v * 2)
            .peek(v -> System.out.println("  [Log] After doubling: " + v));

    System.out.println("Final value: " + withLogging.join());

    System.out.println();
  }

  private static void parallelComposition() {
    System.out.println("--- Parallel Composition ---");

    long start = System.currentTimeMillis();

    // Two independent async operations
    CompletableFuturePath<String> fetchUserData =
        CompletableFuturePath.supplyAsync(
            () -> {
              System.out.println("  [Fetching user data...]");
              sleep(200);
              return "UserData";
            });

    CompletableFuturePath<String> fetchProductData =
        CompletableFuturePath.supplyAsync(
            () -> {
              System.out.println("  [Fetching product data...]");
              sleep(200);
              return "ProductData";
            });

    // Combine them (runs in parallel!)
    CompletableFuturePath<String> combined =
        fetchUserData.zipWith(
            fetchProductData, (user, product) -> "Combined: " + user + " + " + product);

    String result = combined.join();
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("Result: " + result);
    System.out.println("Elapsed: " + elapsed + "ms (should be ~200ms, not ~400ms)");

    // zipWith3 for three parallel operations
    CompletableFuturePath<Integer> a =
        CompletableFuturePath.supplyAsync(
            () -> {
              sleep(50);
              return 10;
            });
    CompletableFuturePath<Integer> b =
        CompletableFuturePath.supplyAsync(
            () -> {
              sleep(50);
              return 20;
            });
    CompletableFuturePath<Integer> c =
        CompletableFuturePath.supplyAsync(
            () -> {
              sleep(50);
              return 30;
            });

    CompletableFuturePath<Integer> sum = a.zipWith3(b, c, (x, y, z) -> x + y + z);
    System.out.println("Sum of parallel computations: " + sum.join());

    System.out.println();
  }

  private static void errorHandling() {
    System.out.println("--- Error Handling ---");

    // Failed future
    CompletableFuturePath<String> failed =
        CompletableFuturePath.failed(new RuntimeException("Network error"));

    // Recover with a fallback value
    CompletableFuturePath<String> recovered = failed.recover(ex -> "Fallback: " + ex.getMessage());

    System.out.println("Recovered: " + recovered.join());

    // Recover with another async operation
    CompletableFuturePath<String> primaryService =
        CompletableFuturePath.supplyAsync(
            () -> {
              System.out.println("  [Primary service failing...]");
              throw new RuntimeException("Primary unavailable");
            });

    CompletableFuturePath<String> withFallbackService =
        primaryService.recoverWith(
            ex -> {
              System.out.println("  [Falling back to backup service...]");
              return CompletableFuturePath.supplyAsync(
                  () -> {
                    sleep(50);
                    return "Response from backup service";
                  });
            });

    System.out.println("With fallback: " + withFallbackService.join());

    // Chain operations where any step might fail
    CompletableFuturePath<String> pipeline =
        CompletableFuturePath.completed("input")
            .via(
                s ->
                    CompletableFuturePath.supplyAsync(
                        () -> {
                          if (s.length() < 10) {
                            throw new IllegalArgumentException("Input too short");
                          }
                          return s.toUpperCase();
                        }))
            .recover(ex -> "Default value after error: " + ex.getMessage());

    System.out.println("Pipeline result: " + pipeline.join());

    System.out.println();
  }

  private static void timeoutHandling() {
    System.out.println("--- Timeout Handling ---");

    // Fast operation - completes within timeout
    CompletableFuturePath<String> fast =
        CompletableFuturePath.supplyAsync(
            () -> {
              sleep(50);
              return "Fast result";
            });

    try {
      String result = fast.join(Duration.ofMillis(200));
      System.out.println("Fast result: " + result);
    } catch (TimeoutException e) {
      System.out.println("Fast timed out (unexpected)");
    }

    // Slow operation - times out
    CompletableFuturePath<String> slow =
        CompletableFuturePath.supplyAsync(
            () -> {
              sleep(500);
              return "Slow result";
            });

    try {
      String result = slow.join(Duration.ofMillis(100));
      System.out.println("Slow result: " + result);
    } catch (TimeoutException e) {
      System.out.println("Slow timed out (expected): " + e.getClass().getSimpleName());
    }

    // Using recover with timeout scenario
    CompletableFuturePath<String> withTimeoutRecovery =
        CompletableFuturePath.supplyAsync(
                () -> {
                  sleep(500);
                  return "Eventually completes";
                })
            .recover(ex -> "Recovered from: " + ex.getClass().getSimpleName());

    // Note: For proper timeout handling, you'd want to use CompletableFuture's
    // orTimeout or completeOnTimeout methods
    System.out.println("(For production, use CF.orTimeout or completeOnTimeout)");

    System.out.println();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // Domain records
  private record User(String id, String name, String email) {}

  private record Order(String id, String customerName, String product) {}
}
