// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.*;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Comprehensive example demonstrating Fold optics for read-only querying and data extraction.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Basic query operations: getAll, preview, find, exists, all, isEmpty, length
 *   <li>Composing folds for deep queries across nested structures
 *   <li>Monoid-based aggregation for calculating sums, checking conditions, etc.
 *   <li>Real-world analytics on e-commerce order data
 * </ul>
 *
 * <p>Fold is a read-only optic designed specifically for querying without modification, making code
 * intent clear and preventing accidental mutations.
 */
public class FoldUsageExample {

  @GenerateLenses
  @GenerateFolds
  public record Product(String name, double price, String category, boolean inStock) {}

  @GenerateLenses
  @GenerateFolds
  public record Order(String orderId, List<Product> items, String customerName) {}

  @GenerateLenses
  @GenerateFolds
  public record OrderHistory(List<Order> orders) {}

  public static void main(String[] args) {
    // Create sample data
    var order1 =
        new Order(
            "ORD-001",
            List.of(
                new Product("Laptop", 999.99, "Electronics", true),
                new Product("Mouse", 25.00, "Electronics", true),
                new Product("Desk", 350.00, "Furniture", false)),
            "Alice");

    var order2 =
        new Order(
            "ORD-002",
            List.of(
                new Product("Keyboard", 75.00, "Electronics", true),
                new Product("Monitor", 450.00, "Electronics", true),
                new Product("Chair", 200.00, "Furniture", true)),
            "Bob");

    var history = new OrderHistory(List.of(order1, order2));

    System.out.println("=== FOLD USAGE EXAMPLE ===\n");

    // --- SCENARIO 1: Basic Query Operations ---
    System.out.println("--- Scenario 1: Basic Query Operations ---");
    Fold<Order, Product> itemsFold = OrderFolds.items();

    List<Product> allItems = itemsFold.getAll(order1);
    System.out.println("All items: " + allItems.size() + " products");

    Optional<Product> firstItem = itemsFold.preview(order1);
    System.out.println("First item: " + firstItem.map(Product::name).orElse("none"));

    int count = itemsFold.length(order1);
    System.out.println("Item count: " + count);

    boolean isEmpty = itemsFold.isEmpty(order1);
    System.out.println("Is empty: " + isEmpty + "\n");

    // --- SCENARIO 2: Conditional Queries ---
    System.out.println("--- Scenario 2: Conditional Queries ---");

    boolean hasOutOfStock = itemsFold.exists(p -> !p.inStock(), order1);
    System.out.println("Has out of stock items: " + hasOutOfStock);

    boolean allInStock = itemsFold.all(Product::inStock, order1);
    System.out.println("All items in stock: " + allInStock);

    Optional<Product> expensiveItem = itemsFold.find(p -> p.price() > 500, order1);
    System.out.println(
        "First expensive item: " + expensiveItem.map(Product::name).orElse("none") + "\n");

    // --- SCENARIO 3: Composition ---
    System.out.println("--- Scenario 3: Composed Folds ---");

    Fold<OrderHistory, Product> allProducts =
        OrderHistoryFolds.orders().andThen(OrderFolds.items());

    List<Product> allProductsFromHistory = allProducts.getAll(history);
    System.out.println("Total products across all orders: " + allProductsFromHistory.size());

    Fold<OrderHistory, String> allCategories =
        allProducts.andThen(ProductLenses.category().asFold());

    Set<String> uniqueCategories = new HashSet<>(allCategories.getAll(history));
    System.out.println("Unique categories: " + uniqueCategories + "\n");

    // --- SCENARIO 4: Monoid Aggregation ---
    System.out.println("--- Scenario 4: Monoid-Based Aggregation ---");

    // Sum monoid for calculating totals
    Monoid<Double> sumMonoid =
        new Monoid<>() {
          @Override
          public Double empty() {
            return 0.0;
          }

          @Override
          public Double combine(Double a, Double b) {
            return a + b;
          }
        };

    double orderTotal = itemsFold.foldMap(sumMonoid, Product::price, order1);
    System.out.println("Order 1 total: £" + String.format("%.2f", orderTotal));

    double historyTotal = allProducts.foldMap(sumMonoid, Product::price, history);
    System.out.println("All orders total: £" + String.format("%.2f", historyTotal));

    // Boolean AND monoid for checking conditions
    Monoid<Boolean> andMonoid =
        new Monoid<>() {
          @Override
          public Boolean empty() {
            return true;
          }

          @Override
          public Boolean combine(Boolean a, Boolean b) {
            return a && b;
          }
        };

    boolean allAffordable = itemsFold.foldMap(andMonoid, p -> p.price() < 1000, order1);
    System.out.println("All items under £1000: " + allAffordable);

    // Boolean OR monoid for checking any condition
    Monoid<Boolean> orMonoid =
        new Monoid<>() {
          @Override
          public Boolean empty() {
            return false;
          }

          @Override
          public Boolean combine(Boolean a, Boolean b) {
            return a || b;
          }
        };

    boolean hasElectronics =
        allProducts.foldMap(orMonoid, p -> "Electronics".equals(p.category()), history);
    System.out.println("Has electronics: " + hasElectronics + "\n");

    // --- SCENARIO 5: Analytics ---
    System.out.println("--- Scenario 5: Real-World Analytics ---");

    // Most expensive product
    Optional<Product> mostExpensive =
        allProducts.getAll(history).stream().max(Comparator.comparing(Product::price));
    System.out.println(
        "Most expensive product: "
            + mostExpensive.map(p -> p.name() + " (£" + p.price() + ")").orElse("none"));

    // Average price
    List<Product> allProds = allProducts.getAll(history);
    double avgPrice = allProds.isEmpty() ? 0.0 : historyTotal / allProds.size();
    System.out.println("Average product price: £" + String.format("%.2f", avgPrice));

    // Count by category
    long electronicsCount =
        allProducts.getAll(history).stream()
            .filter(p -> "Electronics".equals(p.category()))
            .count();
    System.out.println("Electronics count: " + electronicsCount);

    System.out.println("\n=== END OF EXAMPLE ===");
  }
}
