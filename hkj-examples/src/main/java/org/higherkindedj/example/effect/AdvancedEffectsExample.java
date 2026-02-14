// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.effect.WriterPath;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Examples demonstrating advanced effect patterns: Reader, State, and Writer.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>{@link ReaderPath} for dependency injection and configuration access
 *   <li>{@link WithStatePath} for threading state through computations
 *   <li>{@link WriterPath} for logging and audit trails
 *   <li>Combining these patterns for real-world scenarios
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.AdvancedEffectsExample}
 */
public class AdvancedEffectsExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Advanced Effects ===\n");

    readerPathExamples();
    statePathExamples();
    writerPathExamples();
    combinedPatterns();
  }

  // ===== ReaderPath Examples =====

  /** Configuration record for dependency injection example. */
  record AppConfig(String dbUrl, int maxConnections, boolean debugMode) {}

  /** User record for domain examples. */
  record User(String id, String name, String email) {}

  private static void readerPathExamples() {
    System.out.println("--- ReaderPath: Dependency Injection ---");

    // Create a configuration
    AppConfig config = new AppConfig("jdbc:postgresql://localhost:5432/mydb", 10, true);

    // ReaderPath.asks extracts values from the environment
    ReaderPath<AppConfig, String> getDbUrl = ReaderPath.asks(AppConfig::dbUrl);
    ReaderPath<AppConfig, Integer> getMaxConn = ReaderPath.asks(AppConfig::maxConnections);
    ReaderPath<AppConfig, Boolean> isDebug = ReaderPath.asks(AppConfig::debugMode);

    System.out.println("Database URL: " + getDbUrl.run(config));
    System.out.println("Max connections: " + getMaxConn.run(config));
    System.out.println("Debug mode: " + isDebug.run(config));

    // Composing readers with via
    ReaderPath<AppConfig, String> connectionInfo =
        getDbUrl.via(
            url ->
                getMaxConn.map(
                    maxConn -> String.format("Connecting to %s with pool size %d", url, maxConn)));

    System.out.println("Connection info: " + connectionInfo.run(config));

    // Pure values ignore the environment
    ReaderPath<AppConfig, String> pureValue = ReaderPath.pure("I don't need config");
    System.out.println("Pure value: " + pureValue.run(config));

    // Combining independent readers with zipWith
    ReaderPath<AppConfig, String> combined =
        getDbUrl.zipWith(getMaxConn, (url, max) -> String.format("URL=%s, Pool=%d", url, max));

    System.out.println("Combined: " + combined.run(config));

    System.out.println();
  }

  // ===== WithStatePath Examples =====

  private static void statePathExamples() {
    System.out.println("--- WithStatePath: Stateful Computations ---");

    // Counter example - generating sequential IDs
    System.out.println("Counter/ID Generation:");

    WithStatePath<Integer, Integer> nextId =
        WithStatePath.<Integer>modify(n -> n + 1).then(WithStatePath::get);

    // Generate 3 IDs sequentially
    WithStatePath<Integer, List<Integer>> threeIds =
        nextId.via(id1 -> nextId.via(id2 -> nextId.map(id3 -> List.of(id1, id2, id3))));

    StateTuple<Integer, List<Integer>> result = threeIds.run(0);
    System.out.println("Generated IDs: " + result.value()); // [1, 2, 3]
    System.out.println("Final counter: " + result.state()); // 3

    // Shopping cart example
    System.out.println("\nShopping Cart:");

    // Uses class-level CartItem and Cart records defined below
    Cart emptyCart = new Cart(List.of(), 0.0);

    WithStatePath<Cart, Double> getTotal = WithStatePath.inspect(Cart::total);
    WithStatePath<Cart, Integer> getItemCount = WithStatePath.inspect(cart -> cart.items().size());

    // Build a shopping session
    WithStatePath<Cart, String> shoppingSession =
        addToCart(new CartItem("Book", 29.99))
            .then(() -> addToCart(new CartItem("Coffee", 4.99)))
            .then(() -> addToCart(new CartItem("Pen", 1.99)))
            .then(
                () ->
                    getTotal.via(
                        total ->
                            getItemCount.map(
                                count ->
                                    String.format(
                                        "Cart has %d items, total: £%.2f", count, total))));

    StateTuple<Cart, String> cartResult = shoppingSession.run(emptyCart);
    System.out.println(cartResult.value());
    System.out.println("Items: " + cartResult.state().items());

    System.out.println();
  }

  // Helper method for cart example (Java doesn't allow local methods)
  private static WithStatePath<Cart, Unit> addToCart(CartItem item) {
    return WithStatePath.modify(cart -> cart.addItem(item));
  }

  // Nested records for cart state
  private record CartItem(String name, double price) {}

  private record Cart(List<CartItem> items, double total) {
    Cart addItem(CartItem item) {
      var newItems = new ArrayList<>(items);
      newItems.add(item);
      return new Cart(newItems, total + item.price());
    }
  }

  // ===== WriterPath Examples =====

  private static void writerPathExamples() {
    System.out.println("--- WriterPath: Logging and Audit Trails ---");

    // Using List<String> for log accumulation
    Monoid<List<String>> logMonoid = Monoids.list();

    // Simple logging example
    WriterPath<List<String>, Integer> computation =
        WriterPath.tell(List.of("Starting computation"), logMonoid)
            .then(() -> WriterPath.pure(42, logMonoid))
            .via(n -> WriterPath.writer(n * 2, List.of("Doubled value to " + (n * 2)), logMonoid));

    System.out.println("Result: " + computation.value()); // 84
    System.out.println("Log: " + computation.written());
    // [Starting computation, Doubled value to 84]

    // Audit trail example
    System.out.println("\nAudit Trail:");

    record AuditEntry(String action, String timestamp) {
      @Override
      public String toString() {
        return "[" + timestamp + "] " + action;
      }
    }

    Monoid<List<AuditEntry>> auditMonoid = Monoids.list();

    WriterPath<List<AuditEntry>, String> auditedOperation =
        WriterPath.tell(List.of(new AuditEntry("User login", "2025-01-15T10:00:00")), auditMonoid)
            .then(
                () ->
                    WriterPath.writer(
                        "data-123",
                        List.of(new AuditEntry("Data accessed", "2025-01-15T10:00:01")),
                        auditMonoid))
            .via(
                data ->
                    WriterPath.writer(
                        "processed-" + data,
                        List.of(new AuditEntry("Data processed", "2025-01-15T10:00:02")),
                        auditMonoid));

    System.out.println("Operation result: " + auditedOperation.value());
    System.out.println("Audit trail:");
    for (AuditEntry entry : auditedOperation.written()) {
      System.out.println("  " + entry);
    }

    System.out.println();
  }

  // ===== Combined Patterns =====

  private static void combinedPatterns() {
    System.out.println("--- Combined Patterns: Real-World Scenario ---");

    // Scenario: Processing orders with configuration, state tracking, and logging
    // We'll simulate each effect separately and show how they might work together

    OrderConfig config = new OrderConfig(0.20, 100.0, 0.10); // 20% tax, 10% discount over £100

    // Calculate order with configuration
    double subtotal = 150.0;

    ReaderPath<OrderConfig, String> orderCalculation =
        applyDiscount(subtotal)
            .via(
                discounted ->
                    calculateTax(discounted)
                        .map(
                            tax ->
                                String.format(
                                    "Subtotal: £%.2f, After discount: £%.2f, Tax: £%.2f, Total: £%.2f",
                                    subtotal, discounted, tax, discounted + tax)));

    System.out.println("Order calculation:");
    System.out.println(orderCalculation.run(config));

    // State for tracking order count
    WithStatePath<Integer, List<String>> processOrders =
        trackOrder("ORD-001")
            .via(
                msg1 ->
                    trackOrder("ORD-002")
                        .via(msg2 -> trackOrder("ORD-003").map(msg3 -> List.of(msg1, msg2, msg3))));

    StateTuple<Integer, List<String>> orderTracking = processOrders.run(0);
    System.out.println("\nOrder tracking:");
    orderTracking.value().forEach(msg -> System.out.println("  " + msg));
    System.out.println("Total orders today: " + orderTracking.state());

    System.out.println();
  }

  // Helper methods for combined patterns (defined as instance methods for type inference)
  private static ReaderPath<OrderConfig, Double> calculateTax(double subtotal) {
    return ReaderPath.asks(cfg -> subtotal * cfg.taxRate());
  }

  private static ReaderPath<OrderConfig, Double> applyDiscount(double subtotal) {
    return ReaderPath.asks(
        cfg ->
            subtotal >= cfg.discountThreshold() ? subtotal * (1 - cfg.discountRate()) : subtotal);
  }

  private static WithStatePath<Integer, String> trackOrder(String orderId) {
    return WithStatePath.<Integer>modify(count -> count + 1)
        .then(WithStatePath::get)
        .map(count -> String.format("Order %s is #%d today", orderId, count));
  }

  private record OrderConfig(double taxRate, double discountThreshold, double discountRate) {}
}
