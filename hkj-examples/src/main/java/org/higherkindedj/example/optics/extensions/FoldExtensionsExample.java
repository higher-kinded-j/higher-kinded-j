// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.extensions;

import static org.higherkindedj.optics.extensions.FoldExtensions.*;

import java.util.List;
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
 *   <li>Real-world scenarios: e-commerce analytics, user management, data validation
 * </ul>
 *
 * <p>FoldExtensions provides Maybe-based alternatives to Fold's Optional-returning methods,
 * ensuring consistency with higher-kinded-j's functional programming patterns.
 */
public class FoldExtensionsExample {

  // Domain Models
  record Product(String id, String name, double price, String category, int stock) {}

  record Order(String orderId, List<Product> items, String customerId) {}

  record Customer(String id, String name, String email, int loyaltyPoints) {}

  record Store(List<Product> inventory, List<Order> orders, List<Customer> customers) {}

  public static void main(String[] args) {
    // Create sample data
    var inventory =
        List.of(
            new Product("P001", "Laptop", 999.99, "Electronics", 5),
            new Product("P002", "Mouse", 25.00, "Electronics", 50),
            new Product("P003", "Desk", 350.00, "Furniture", 3),
            new Product("P004", "Chair", 200.00, "Furniture", 0), // Out of stock
            new Product("P005", "Monitor", 450.00, "Electronics", 10));

    var orders =
        List.of(
            new Order(
                "ORD-001",
                List.of(
                    new Product("P001", "Laptop", 999.99, "Electronics", 5),
                    new Product("P002", "Mouse", 25.00, "Electronics", 50)),
                "C001"),
            new Order("ORD-002", List.of(), "C002"), // Empty order
            new Order(
                "ORD-003",
                List.of(
                    new Product("P003", "Desk", 350.00, "Furniture", 3),
                    new Product("P004", "Chair", 200.00, "Furniture", 0)),
                "C003"));

    var customers =
        List.of(
            new Customer("C001", "Alice", "alice@example.com", 1500),
            new Customer("C002", "Bob", "bob@example.com", 200),
            new Customer("C003", "Charlie", "charlie@example.com", 3000));

    var store = new Store(inventory, orders, customers);

    System.out.println("=== FOLD EXTENSIONS EXAMPLE ===\n");

    demonstratePreviewMaybe(store);
    demonstrateFindMaybe(store);
    demonstrateGetAllMaybe(store);
    demonstrateChaining(store);
    demonstrateRealWorldScenarios(store);
  }

  // ============================================================================
  // SCENARIO 1: previewMaybe - Safe First Element Access
  // ============================================================================

  private static void demonstratePreviewMaybe(Store store) {
    System.out.println("--- Scenario 1: previewMaybe() ---");

    Fold<Store, Product> inventoryFold = Fold.of(Store::inventory);
    Fold<Order, Product> itemsFold = Fold.of(Order::items);

    // Get first product from inventory
    Maybe<Product> firstProduct = previewMaybe(inventoryFold, store);

    if (firstProduct.isJust()) {
      Product p = firstProduct.get();
      System.out.println("First product: " + p.name() + " ($" + p.price() + ")");
    }

    // Chain with map
    Maybe<String> firstProductName = previewMaybe(inventoryFold, store).map(Product::name);

    System.out.println("First product name: " + firstProductName.orElse("No products available"));

    // Handle empty case
    Order emptyOrder = store.orders().get(1); // ORD-002 is empty
    Maybe<Product> firstItem = previewMaybe(itemsFold, emptyOrder);

    if (firstItem.isNothing()) {
      System.out.println("Order ORD-002 is empty");
    } else {
      Product item = firstItem.get();
      System.out.println("First item: " + item.name());
    }

    // Provide default
    Product defaultProduct = new Product("P999", "Default", 0.0, "Unknown", 0);
    Product result = previewMaybe(itemsFold, emptyOrder).orElse(defaultProduct);

    System.out.println("Result with default: " + result.name());
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 2: findMaybe - Predicate-Based Search
  // ============================================================================

  private static void demonstrateFindMaybe(Store store) {
    System.out.println("--- Scenario 2: findMaybe() ---");

    Fold<Store, Product> inventoryFold = Fold.of(Store::inventory);
    Fold<Store, Customer> customersFold = Fold.of(Store::customers);

    // Find expensive products
    Maybe<Product> expensiveProduct = findMaybe(inventoryFold, p -> p.price() > 500, store);

    if (expensiveProduct.isJust()) {
      Product p = expensiveProduct.get();
      System.out.println("Found expensive product: " + p.name() + " ($" + p.price() + ")");
    }

    // Find out of stock items
    Maybe<Product> outOfStock = findMaybe(inventoryFold, p -> p.stock() == 0, store);

    if (outOfStock.isNothing()) {
      System.out.println("All items in stock!");
    } else {
      Product p = outOfStock.get();
      System.out.println("Out of stock: " + p.name());
    }

    // Find VIP customers (high loyalty points)
    Maybe<Customer> vipCustomer = findMaybe(customersFold, c -> c.loyaltyPoints() > 2000, store);

    if (vipCustomer.isJust()) {
      Customer c = vipCustomer.get();
      System.out.println("VIP customer found: " + c.name() + " (" + c.loyaltyPoints() + " points)");
    }

    // Chain with map
    Maybe<String> vipEmail =
        findMaybe(customersFold, c -> c.loyaltyPoints() > 2000, store).map(Customer::email);

    System.out.println("VIP email: " + vipEmail.orElse("No VIP customers"));

    // No match case
    Maybe<Product> veryExpensive = findMaybe(inventoryFold, p -> p.price() > 5000, store);

    System.out.println("Very expensive product exists: " + veryExpensive.isJust());
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 3: getAllMaybe - Distinguishing Empty from Nothing
  // ============================================================================

  private static void demonstrateGetAllMaybe(Store store) {
    System.out.println("--- Scenario 3: getAllMaybe() ---");

    Fold<Order, Product> itemsFold = Fold.of(Order::items);

    // Non-empty order
    Order order1 = store.orders().getFirst();
    Maybe<List<Product>> items1 = getAllMaybe(itemsFold, order1);

    if (items1.isJust()) {
      List<Product> items = items1.get();
      System.out.println("Order " + order1.orderId() + " has " + items.size() + " items");
    }

    // Empty order
    Order emptyOrder = store.orders().get(1);
    Maybe<List<Product>> items2 = getAllMaybe(itemsFold, emptyOrder);

    if (items2.isNothing()) {
      System.out.println("Order " + emptyOrder.orderId() + " is empty");
    } else {
      List<Product> items = items2.get();
      System.out.println("Order " + emptyOrder.orderId() + " has " + items.size() + " items");
    }

    // Calculate total price if items exist
    Maybe<Double> orderTotal =
        getAllMaybe(itemsFold, order1)
            .map(items -> items.stream().mapToDouble(Product::price).sum());

    if (orderTotal.isJust()) {
      Double total = orderTotal.get();
      System.out.println("Order total: $" + String.format("%.2f", total));
    }

    // Count items by category
    Maybe<Long> furnitureCount =
        getAllMaybe(itemsFold, store.orders().get(2))
            .map(items -> items.stream().filter(p -> "Furniture".equals(p.category())).count());

    if (furnitureCount.isJust()) {
      Long count = furnitureCount.get();
      System.out.println("Furniture items in order 3: " + count);
    }
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 4: Chaining Operations
  // ============================================================================

  private static void demonstrateChaining(Store store) {
    System.out.println("--- Scenario 4: Chaining Operations ---");

    Fold<Store, Product> inventoryFold = Fold.of(Store::inventory);

    // Find expensive electronics and get the name in uppercase
    Maybe<String> expensiveElectronicsName =
        findMaybe(inventoryFold, p -> "Electronics".equals(p.category()) && p.price() > 400, store)
            .map(Product::name)
            .map(String::toUpperCase);

    System.out.println("Expensive electronics: " + expensiveElectronicsName.orElse("Not found"));

    // Get first product, check stock, provide alert
    Maybe<String> stockAlert =
        previewMaybe(inventoryFold, store)
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
    Fold<Store, Order> ordersFold = Fold.of(Store::orders);
    Fold<Order, Product> itemsFold = Fold.of(Order::items);

    Maybe<String> mostExpensiveFurnitureMessage =
        getAllMaybe(inventoryFold, store)
            .flatMap(
                products ->
                    products.stream()
                        .filter(p -> "Furniture".equals(p.category()))
                        .max((p1, p2) -> Double.compare(p1.price(), p2.price()))
                        .map(Maybe::just)
                        .orElse(Maybe.nothing()))
            .map(p -> "Most expensive furniture: " + p.name() + " ($" + p.price() + ")");

    System.out.println(mostExpensiveFurnitureMessage.orElse("No furniture found"));
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 5: Real-World Scenarios
  // ============================================================================

  private static void demonstrateRealWorldScenarios(Store store) {
    System.out.println("--- Scenario 5: Real-World Scenarios ---");

    // Scenario A: Restock Alert System
    System.out.println("Restock Alerts:");

    Fold<Store, Product> inventoryFold = Fold.of(Store::inventory);

    store
        .inventory()
        .forEach(
            product -> {
              Maybe<Product> restockItem =
                  findMaybe(
                      inventoryFold, p -> p.id().equals(product.id()) && p.stock() < 5, store);
              if (restockItem.isJust()) {
                Product p = restockItem.get();
                System.out.println("  - Restock " + p.name() + " (current: " + p.stock() + ")");
              }
            });

    System.out.println();

    // Scenario B: VIP Customer Rewards
    System.out.println("VIP Customer Rewards:");

    Fold<Store, Customer> customersFold = Fold.of(Store::customers);

    Maybe<String> vipRewardMessage =
        findMaybe(customersFold, c -> c.loyaltyPoints() > 2500, store)
            .map(
                c ->
                    "Congratulations "
                        + c.name()
                        + "! You've earned a $50 reward ("
                        + c.loyaltyPoints()
                        + " points)");

    System.out.println(vipRewardMessage.orElse("No customers eligible for rewards"));
    System.out.println();

    // Scenario C: Order Fulfillment Check
    System.out.println("Order Fulfillment Check:");

    Fold<Order, Product> itemsFold = Fold.of(Order::items);

    store
        .orders()
        .forEach(
            order -> {
              Maybe<Product> unavailableItem = findMaybe(itemsFold, p -> p.stock() == 0, order);

              if (unavailableItem.isNothing()) {
                System.out.println("  Order " + order.orderId() + ": Can be fulfilled");
              } else {
                Product p = unavailableItem.get();
                System.out.println(
                    "  Order " + order.orderId() + ": BLOCKED - " + p.name() + " out of stock");
              }
            });

    System.out.println();

    // Scenario D: Category Analysis
    System.out.println("Category Analysis:");

    String[] categories = {"Electronics", "Furniture", "Clothing"};

    for (String category : categories) {
      Maybe<Long> count =
          getAllMaybe(inventoryFold, store)
              .map(
                  products -> products.stream().filter(p -> category.equals(p.category())).count());

      if (count.isJust()) {
        Long c = count.get();
        System.out.println("  " + category + ": " + c + " products");
      }
    }

    System.out.println();

    // Scenario E: Price Range Search
    System.out.println("Price Range Search ($100-$500):");

    Maybe<List<Product>> affordableProducts =
        getAllMaybe(inventoryFold, store)
            .map(
                products ->
                    products.stream().filter(p -> p.price() >= 100 && p.price() <= 500).toList());

    if (affordableProducts.isJust()) {
      List<Product> products = affordableProducts.get();
      if (products.isEmpty()) {
        System.out.println("  No products in this price range");
      } else {
        products.forEach(p -> System.out.println("  - " + p.name() + ": $" + p.price()));
      }
    }
  }
}
