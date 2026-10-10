// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.extensions;

import static org.higherkindedj.optics.extensions.FoldExtensions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;

/**
 * Comprehensive example demonstrating {@link org.higherkindedj.optics.extensions.FoldExtensions}
 * for Maybe-based fold operations.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Using {@code previewMaybe} for safe first-element access
 *   <li>Using {@code findMaybe} for predicate-based searching
 *   <li>Using {@code getAllMaybe} to distinguish empty from nothing
 *   <li>Chaining Maybe operations for complex data pipelines
 *   <li>Real-world scenarios: e-commerce analytics, customer loyalty, order fulfilment
 * </ul>
 *
 * <p>FoldExtensions provides Maybe-based alternatives to Fold's Optional-returning methods,
 * ensuring consistency with higher-kinded-j's functional programming patterns.
 */
public class FoldExtensionsExample {

  // Domain models: the chapter's Order, Customer and LineItem, and a catalogue entry keyed by the
  // sku a line item carries
  record Product(String sku, String name, BigDecimal price, int stock) {}

  // A customer's loyalty points, which the chapter's Customer does not carry
  record LoyaltyAccount(Customer customer, int points) {}

  // The retailer's inventory, the category it files each product under by sku, its orders, and
  // its loyalty accounts
  record Retailer(
      List<Product> inventory,
      Map<String, String> categories,
      List<Order> orders,
      List<LoyaltyAccount> accounts) {

    String categoryOf(String sku) {
      return categories.getOrDefault(sku, "Unknown");
    }

    int stockOf(String sku) {
      return inventory.stream().filter(p -> p.sku().equals(sku)).mapToInt(Product::stock).sum();
    }
  }

  // An order of the customer's, placed on the same day, in pounds
  private static Order order(int number, Customer customer, LineItem... lines) {
    return new Order(
        new UUID(0, number),
        customer,
        List.of(lines),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        OrderStatus.NEW);
  }

  public static void main(String[] args) {
    // Create sample data
    var inventory =
        List.of(
            new Product("LAPTOP", "Laptop", new BigDecimal("999.99"), 5),
            new Product("MOUSE", "Mouse", new BigDecimal("25.00"), 50),
            new Product("DESK", "Desk", new BigDecimal("350.00"), 3),
            new Product("CHAIR", "Chair", new BigDecimal("200.00"), 0), // Out of stock
            new Product("MONITOR", "Monitor", new BigDecimal("450.00"), 10));

    var categories =
        Map.of(
            "LAPTOP", "Electronics",
            "MOUSE", "Electronics",
            "DESK", "Furniture",
            "CHAIR", "Furniture",
            "MONITOR", "Electronics");

    var alice = new Customer("Alice", new EmailAddress("alice@example.com"));
    var bob = new Customer("Bob", new EmailAddress("bob@example.com"));
    var charlie = new Customer("Charlie", new EmailAddress("charlie@example.com"));

    var orders =
        List.of(
            order(
                1,
                alice,
                new LineItem("LAPTOP", 1, new BigDecimal("999.99")),
                new LineItem("MOUSE", 1, new BigDecimal("25.00"))),
            order(2, bob), // Empty order
            order(
                3,
                charlie,
                new LineItem("DESK", 1, new BigDecimal("350.00")),
                new LineItem("CHAIR", 1, new BigDecimal("200.00"))));

    var accounts =
        List.of(
            new LoyaltyAccount(alice, 1500),
            new LoyaltyAccount(bob, 200),
            new LoyaltyAccount(charlie, 3000));

    var retailer = new Retailer(inventory, categories, orders, accounts);

    System.out.println("=== FOLD EXTENSIONS EXAMPLE ===\n");

    demonstratePreviewMaybe(retailer);
    demonstrateFindMaybe(retailer);
    demonstrateGetAllMaybe(retailer);
    demonstrateChaining(retailer);
    demonstrateRealWorldScenarios(retailer);
  }

  // ============================================================================
  // SCENARIO 1: previewMaybe - Safe First Element Access
  // ============================================================================

  private static void demonstratePreviewMaybe(Retailer retailer) {
    System.out.println("--- Scenario 1: previewMaybe() ---");

    Fold<Retailer, Product> inventoryFold = Fold.of(Retailer::inventory);
    Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

    // Get first product from inventory
    Maybe<Product> firstProduct = previewMaybe(inventoryFold, retailer);

    if (firstProduct.isJust()) {
      Product p = firstProduct.get();
      System.out.println("First product: " + p.name() + " (£" + p.price() + ")");
    }

    // Chain with map
    Maybe<String> firstProductName = previewMaybe(inventoryFold, retailer).map(Product::name);

    System.out.println("First product name: " + firstProductName.orElse("No products available"));

    // Handle empty case
    Order emptyOrder = retailer.orders().get(1); // Bob's order is empty
    Maybe<LineItem> firstLine = previewMaybe(linesFold, emptyOrder);

    if (firstLine.isNothing()) {
      System.out.println(emptyOrder.customer().name() + "'s order is empty");
    } else {
      LineItem line = firstLine.get();
      System.out.println("First line: " + line.sku());
    }

    // Provide default
    LineItem defaultLine = new LineItem("NONE", 0, BigDecimal.ZERO);
    LineItem result = previewMaybe(linesFold, emptyOrder).orElse(defaultLine);

    System.out.println("Result with default: " + result.sku());
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 2: findMaybe - Predicate-Based Search
  // ============================================================================

  private static void demonstrateFindMaybe(Retailer retailer) {
    System.out.println("--- Scenario 2: findMaybe() ---");

    Fold<Retailer, Product> inventoryFold = Fold.of(Retailer::inventory);
    Fold<Retailer, LoyaltyAccount> accountsFold = Fold.of(Retailer::accounts);

    // Find expensive products
    Maybe<Product> expensiveProduct =
        findMaybe(inventoryFold, p -> p.price().compareTo(new BigDecimal("500")) > 0, retailer);

    if (expensiveProduct.isJust()) {
      Product p = expensiveProduct.get();
      System.out.println("Found expensive product: " + p.name() + " (£" + p.price() + ")");
    }

    // Find out of stock items
    Maybe<Product> outOfStock = findMaybe(inventoryFold, p -> p.stock() == 0, retailer);

    if (outOfStock.isNothing()) {
      System.out.println("All items in stock!");
    } else {
      Product p = outOfStock.get();
      System.out.println("Out of stock: " + p.name());
    }

    // Find VIP customers (high loyalty points)
    Maybe<LoyaltyAccount> vipAccount = findMaybe(accountsFold, a -> a.points() > 2000, retailer);

    if (vipAccount.isJust()) {
      LoyaltyAccount a = vipAccount.get();
      System.out.println(
          "VIP customer found: " + a.customer().name() + " (" + a.points() + " points)");
    }

    // Chain with map
    Maybe<String> vipEmail =
        findMaybe(accountsFold, a -> a.points() > 2000, retailer)
            .map(a -> a.customer().email().value());

    System.out.println("VIP email: " + vipEmail.orElse("No VIP customers"));

    // No match case
    Maybe<Product> veryExpensive =
        findMaybe(inventoryFold, p -> p.price().compareTo(new BigDecimal("5000")) > 0, retailer);

    System.out.println("Very expensive product exists: " + veryExpensive.isJust());
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 3: getAllMaybe - Distinguishing Empty from Nothing
  // ============================================================================

  private static void demonstrateGetAllMaybe(Retailer retailer) {
    System.out.println("--- Scenario 3: getAllMaybe() ---");

    Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

    // Non-empty order
    Order order1 = retailer.orders().getFirst();
    Maybe<List<LineItem>> lines1 = getAllMaybe(linesFold, order1);

    if (lines1.isJust()) {
      List<LineItem> lines = lines1.get();
      System.out.println(order1.customer().name() + "'s order has " + lines.size() + " lines");
    }

    // Empty order
    Order emptyOrder = retailer.orders().get(1);
    Maybe<List<LineItem>> lines2 = getAllMaybe(linesFold, emptyOrder);

    if (lines2.isNothing()) {
      System.out.println(emptyOrder.customer().name() + "'s order is empty");
    } else {
      List<LineItem> lines = lines2.get();
      System.out.println(emptyOrder.customer().name() + "'s order has " + lines.size() + " lines");
    }

    // Calculate the order total if it has lines: each line's price times its quantity
    Maybe<BigDecimal> orderTotal =
        getAllMaybe(linesFold, order1)
            .map(
                lines ->
                    lines.stream()
                        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

    if (orderTotal.isJust()) {
      BigDecimal total = orderTotal.get();
      System.out.println("Order total: £" + total);
    }

    // Count lines by category
    Maybe<Long> furnitureCount =
        getAllMaybe(linesFold, retailer.orders().get(2))
            .map(
                lines ->
                    lines.stream()
                        .filter(line -> "Furniture".equals(retailer.categoryOf(line.sku())))
                        .count());

    if (furnitureCount.isJust()) {
      Long count = furnitureCount.get();
      System.out.println("Furniture lines in order 3: " + count);
    }
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 4: Chaining Operations
  // ============================================================================

  private static void demonstrateChaining(Retailer retailer) {
    System.out.println("--- Scenario 4: Chaining Operations ---");

    Fold<Retailer, Product> inventoryFold = Fold.of(Retailer::inventory);

    // Find expensive electronics and get the name in uppercase
    Maybe<String> expensiveElectronicsName =
        findMaybe(
                inventoryFold,
                p ->
                    "Electronics".equals(retailer.categoryOf(p.sku()))
                        && p.price().compareTo(new BigDecimal("400")) > 0,
                retailer)
            .map(Product::name)
            .map(String::toUpperCase);

    System.out.println("Expensive electronics: " + expensiveElectronicsName.orElse("Not found"));

    // Get first product, check stock, provide alert
    Maybe<String> stockAlert =
        previewMaybe(inventoryFold, retailer)
            .map(
                p -> {
                  if (p.stock() == 0) {
                    return "ALERT: " + p.name() + " is out of stock!";
                  } else if (p.stock() < 5) {
                    return "WARNING: " + p.name() + " is low on stock (" + p.stock() + " left)";
                  } else {
                    return "OK: " + p.name() + " is well stocked";
                  }
                });

    if (stockAlert.isJust()) {
      System.out.println(stockAlert.get());
    }

    // Complex chain: Get all furniture, find most expensive, format message
    Maybe<String> mostExpensiveFurnitureMessage =
        getAllMaybe(inventoryFold, retailer)
            .flatMap(
                products ->
                    products.stream()
                        .filter(p -> "Furniture".equals(retailer.categoryOf(p.sku())))
                        .max((p1, p2) -> p1.price().compareTo(p2.price()))
                        .map(Maybe::just)
                        .orElse(Maybe.nothing()))
            .map(p -> "Most expensive furniture: " + p.name() + " (£" + p.price() + ")");

    System.out.println(mostExpensiveFurnitureMessage.orElse("No furniture found"));
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 5: Real-World Scenarios
  // ============================================================================

  private static void demonstrateRealWorldScenarios(Retailer retailer) {
    System.out.println("--- Scenario 5: Real-World Scenarios ---");

    // Scenario A: Restock Alert System
    System.out.println("Restock Alerts:");

    Fold<Retailer, Product> inventoryFold = Fold.of(Retailer::inventory);

    retailer
        .inventory()
        .forEach(
            product -> {
              Maybe<Product> restockItem =
                  findMaybe(
                      inventoryFold, p -> p.sku().equals(product.sku()) && p.stock() < 5, retailer);
              if (restockItem.isJust()) {
                Product p = restockItem.get();
                System.out.println("  - Restock " + p.name() + " (current: " + p.stock() + ")");
              }
            });

    System.out.println();

    // Scenario B: VIP Customer Rewards
    System.out.println("VIP Customer Rewards:");

    Fold<Retailer, LoyaltyAccount> accountsFold = Fold.of(Retailer::accounts);

    Maybe<String> vipRewardMessage =
        findMaybe(accountsFold, a -> a.points() > 2500, retailer)
            .map(
                a ->
                    "Congratulations "
                        + a.customer().name()
                        + "! You've earned a £50 reward ("
                        + a.points()
                        + " points)");

    System.out.println(vipRewardMessage.orElse("No customers eligible for rewards"));
    System.out.println();

    // Scenario C: Order Fulfilment Check
    System.out.println("Order Fulfilment Check:");

    Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

    retailer
        .orders()
        .forEach(
            order -> {
              Maybe<LineItem> unavailableLine =
                  findMaybe(linesFold, line -> retailer.stockOf(line.sku()) == 0, order);
              String whose = order.customer().name() + "'s order";

              if (unavailableLine.isNothing()) {
                System.out.println("  " + whose + ": Can be fulfilled");
              } else {
                LineItem line = unavailableLine.get();
                System.out.println("  " + whose + ": BLOCKED - " + line.sku() + " out of stock");
              }
            });

    System.out.println();

    // Scenario D: Category Analysis
    System.out.println("Category Analysis:");

    String[] categories = {"Electronics", "Furniture", "Clothing"};

    for (String category : categories) {
      Maybe<Long> count =
          getAllMaybe(inventoryFold, retailer)
              .map(
                  products ->
                      products.stream()
                          .filter(p -> category.equals(retailer.categoryOf(p.sku())))
                          .count());

      if (count.isJust()) {
        Long c = count.get();
        System.out.println("  " + category + ": " + c + " products");
      }
    }

    System.out.println();

    // Scenario E: Price Range Search
    System.out.println("Price Range Search (£100-£500):");

    BigDecimal low = new BigDecimal("100");
    BigDecimal high = new BigDecimal("500");
    Maybe<List<Product>> affordableProducts =
        getAllMaybe(inventoryFold, retailer)
            .map(
                products ->
                    products.stream()
                        .filter(
                            p -> p.price().compareTo(low) >= 0 && p.price().compareTo(high) <= 0)
                        .toList());

    if (affordableProducts.isJust()) {
      List<Product> products = affordableProducts.get();
      if (products.isEmpty()) {
        System.out.println("  No products in this price range");
      } else {
        products.forEach(p -> System.out.println("  - " + p.name() + ": £" + p.price()));
      }
    }
  }
}
