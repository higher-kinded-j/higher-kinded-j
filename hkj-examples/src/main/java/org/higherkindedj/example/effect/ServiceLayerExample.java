// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * Examples demonstrating service layer patterns with IOPath.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Building IO-based service layers
 *   <li>Composing database operations
 *   <li>Error handling in IO pipelines
 *   <li>Converting between IO and Try
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ServiceLayerExample}
 */
public class ServiceLayerExample {

  // Domain types
  record User(String id, String name, String email) {}

  record Order(String id, String userId, String product, int quantity) {}

  record OrderSummary(User user, Order order, String status) {}

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Service Layer Patterns ===\n");

    basicServiceOperations();
    composingServices();
    errorHandlingInServices();
    transactionPattern();
  }

  // Simulated database
  private static final Map<String, User> userDb = new HashMap<>();
  private static final Map<String, Order> orderDb = new HashMap<>();

  static {
    userDb.put("u1", new User("u1", "Alice", "alice@example.com"));
    userDb.put("u2", new User("u2", "Bob", "bob@example.com"));
    orderDb.put("o1", new Order("o1", "u1", "Widget", 3));
    orderDb.put("o2", new Order("o2", "u2", "Gadget", 1));
  }

  // ===== Repository Pattern with IOPath =====

  /** Simulates a database lookup - deferred execution */
  private static IOPath<User> findUserById(String id) {
    return Path.io(
        () -> {
          System.out.println("  [DB] Looking up user: " + id);
          User user = userDb.get(id);
          if (user == null) {
            throw new RuntimeException("User not found: " + id);
          }
          return user;
        });
  }

  private static IOPath<Order> findOrderById(String id) {
    return Path.io(
        () -> {
          System.out.println("  [DB] Looking up order: " + id);
          Order order = orderDb.get(id);
          if (order == null) {
            throw new RuntimeException("Order not found: " + id);
          }
          return order;
        });
  }

  private static void basicServiceOperations() {
    System.out.println("--- Basic Service Operations ---");

    // Build a computation - nothing happens yet
    IOPath<User> userLookup = findUserById("u1");
    System.out.println("Created user lookup (not executed)");

    // Execute with runSafe() to capture errors
    Try<User> result = userLookup.runSafe();
    System.out.println("Executed: " + result);

    // Chain operations
    IOPath<String> userEmail = findUserById("u1").map(User::email);

    System.out.println("User email: " + userEmail.unsafeRun()); // alice@example.com

    System.out.println();
  }

  private static void composingServices() {
    System.out.println("--- Composing Services ---");

    // Service: Get order summary (requires user lookup + order lookup)
    IOPath<OrderSummary> getOrderSummary =
        findOrderById("o1")
            .via(
                order ->
                    findUserById(order.userId())
                        .map(user -> new OrderSummary(user, order, "Processing")));

    System.out.println("Order summary computation created");
    System.out.println("Executing...");

    OrderSummary summary = getOrderSummary.unsafeRun();
    System.out.println("Summary: " + summary.user().name() + " ordered " + summary.order().product()
        + " (status: " + summary.status() + ")");

    // Combine independent lookups with zipWith
    System.out.println("\n--- Parallel-style Composition (zipWith) ---");

    IOPath<String> combinedInfo =
        findUserById("u1")
            .zipWith(
                findOrderById("o1"),
                (user, order) -> user.name() + " has order for " + order.product());

    System.out.println("Combined: " + combinedInfo.unsafeRun());

    System.out.println();
  }

  private static void errorHandlingInServices() {
    System.out.println("--- Error Handling in Services ---");

    // Service that may fail
    IOPath<User> mayFail = findUserById("unknown");

    // Option 1: Convert to TryPath for safe handling
    TryPath<User> safeLookup = mayFail.toTryPath();
    System.out.println("Safe lookup result: " + safeLookup.run());

    // Option 2: Handle error inline
    IOPath<User> withFallback =
        findUserById("unknown").handleError(ex -> new User("default", "Guest", "guest@example.com"));

    System.out.println("With fallback: " + withFallback.unsafeRun().name()); // Guest

    // Option 3: handleErrorWith for recovery computation
    IOPath<User> withRecovery =
        findUserById("unknown")
            .handleErrorWith(
                ex -> {
                  System.out.println("  Primary lookup failed, trying backup...");
                  return findUserById("u1"); // Fallback to known user
                });

    System.out.println("With recovery: " + withRecovery.unsafeRun().name()); // Alice

    System.out.println();
  }

  private static void transactionPattern() {
    System.out.println("--- Transaction-like Pattern ---");

    AtomicInteger operationCount = new AtomicInteger();

    // Simulate a transaction: multiple operations that should all succeed
    IOPath<String> transaction =
        Path.io(
                () -> {
                  System.out.println("  [TX] Starting transaction");
                  return "tx-123";
                })
            .via(
                txId ->
                    findUserById("u1")
                        .map(
                            user -> {
                              operationCount.incrementAndGet();
                              System.out.println("  [TX] Validated user: " + user.name());
                              return user;
                            }))
            .via(
                user ->
                    findOrderById("o1")
                        .map(
                            order -> {
                              operationCount.incrementAndGet();
                              System.out.println("  [TX] Validated order: " + order.id());
                              return order;
                            }))
            .map(
                order -> {
                  operationCount.incrementAndGet();
                  String result = "Processed order " + order.id();
                  System.out.println("  [TX] " + result);
                  return result;
                })
            .map(
                result -> {
                  System.out.println("  [TX] Committing transaction");
                  return "SUCCESS: " + result;
                });

    System.out.println("Transaction defined (0 operations executed)");
    System.out.println("Operations so far: " + operationCount.get());

    System.out.println("\nExecuting transaction...");
    String finalResult = transaction.unsafeRun();

    System.out.println("\nTransaction result: " + finalResult);
    System.out.println("Operations executed: " + operationCount.get());

    System.out.println();
  }
}
