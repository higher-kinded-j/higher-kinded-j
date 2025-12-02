// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.time.Instant;
import java.util.*;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.ForIndexed;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.indexed.IndexedLens;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;

/**
 * A runnable example demonstrating indexed optics for position-aware operations.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li><b>IndexedTraversal</b> for list and map operations with position tracking
 *   <li><b>IndexedFold</b> for position-aware queries
 *   <li><b>IndexedLens</b> for field name tracking
 *   <li>Practical use cases: numbering, labelling, audit trails, debugging
 * </ul>
 *
 * <p>Key concepts demonstrated:
 *
 * <ul>
 *   <li>Creating indexed traversals with {@code IndexedTraversals.forList()} and {@code
 *       IndexedTraversals.forMap()}
 *   <li>Accessing index-value pairs with {@code toIndexedList()}
 *   <li>Position-aware modifications with {@code imodify()}
 *   <li>Filtering by index with {@code filterIndex()} and {@code filteredWithIndex()}
 *   <li>Composing indexed optics for nested structures
 *   <li>Field tracking for audit logs with {@code IndexedLens}
 * </ul>
 */
public class IndexedOpticsExample {

  // Domain models for e-commerce order processing
  public record LineItem(String productName, int quantity, double price) {}

  public record Order(String orderId, List<LineItem> items, Map<String, String> metadata) {}

  public record Customer(String name, String email) {}

  public static void main(String[] args) {
    System.out.println("=== Indexed Optics Examples ===\n");

    demonstrateListIndexing();
    demonstrateMapIndexing();
    demonstratePositionBasedLogic();
    demonstrateIndexedFiltering();
    demonstrateLensIndexing();
    demonstrateAuditTrail();
    demonstrateComposition();
    demonstrateRealWorldScenario();
    demonstrateForIndexedComprehension();
  }

  /** Demonstrates basic list indexing with integer indices. */
  private static void demonstrateListIndexing() {
    System.out.println("--- List Indexing with Integer Indices ---");

    IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();

    List<String> tasks = List.of("Review PR", "Update docs", "Run tests", "Deploy");

    // Extract all index-value pairs
    List<Pair<Integer, String>> indexedTasks = IndexedTraversals.toIndexedList(indexed, tasks);

    System.out.println("Tasks with indices:");
    for (Pair<Integer, String> pair : indexedTasks) {
      System.out.println("  Index " + pair.first() + ": " + pair.second());
    }

    // Add sequence numbers (1-based for display)
    List<String> numbered =
        IndexedTraversals.imodify(indexed, (index, task) -> (index + 1) + ". " + task, tasks);

    System.out.println("\nNumbered tasks:");
    for (String task : numbered) {
      System.out.println("  " + task);
    }

    System.out.println();
  }

  /** Demonstrates map indexing where the key is the index. */
  private static void demonstrateMapIndexing() {
    System.out.println("--- Map Indexing with Key-Based Indices ---");

    IndexedTraversal<String, Map<String, Integer>, Integer> indexed = IndexedTraversals.forMap();

    Map<String, Integer> scores = new LinkedHashMap<>();
    scores.put("alice", 100);
    scores.put("bob", 85);
    scores.put("charlie", 92);

    // Extract key-value pairs
    List<Pair<String, Integer>> entries = IndexedTraversals.toIndexedList(indexed, scores);

    System.out.println("Scores with keys:");
    for (Pair<String, Integer> pair : entries) {
      System.out.println("  " + pair.first() + " scored " + pair.second());
    }

    // Transform values with key awareness
    Map<String, Integer> withBonus =
        IndexedTraversals.imodify(
            indexed,
            (key, score) -> {
              // Alice gets a 10-point bonus
              if (key.equals("alice")) {
                return score + 10;
              }
              return score;
            },
            scores);

    System.out.println("\nScores after bonus:");
    for (var entry : withBonus.entrySet()) {
      System.out.println("  " + entry.getKey() + ": " + entry.getValue());
    }

    System.out.println();
  }

  /** Demonstrates position-based conditional logic. */
  private static void demonstratePositionBasedLogic() {
    System.out.println("--- Position-Based Logic ---");

    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    List<LineItem> items =
        List.of(
            new LineItem("Laptop", 1, 999.99),
            new LineItem("Mouse", 2, 24.99),
            new LineItem("Keyboard", 1, 79.99),
            new LineItem("Monitor", 1, 299.99),
            new LineItem("Webcam", 1, 59.99),
            new LineItem("Headphones", 1, 149.99));

    System.out.println("Original items:");
    for (int i = 0; i < items.size(); i++) {
      System.out.printf(
          "  Position %d: %s - £%.2f%n", i, items.get(i).productName(), items.get(i).price());
    }

    // Apply 10% discount to items at even positions (0, 2, 4...)
    List<LineItem> discounted =
        IndexedTraversals.imodify(
            itemsIndexed,
            (index, item) -> {
              if (index % 2 == 0) {
                double newPrice = item.price() * 0.9;
                return new LineItem(item.productName(), item.quantity(), newPrice);
              }
              return item;
            },
            items);

    System.out.println("\nAfter even-position discount (10% off):");
    for (int i = 0; i < discounted.size(); i++) {
      LineItem item = discounted.get(i);
      System.out.printf("  Position %d: %s - £%.2f", i, item.productName(), item.price());
      if (i % 2 == 0) {
        System.out.print(" (discounted)");
      }
      System.out.println();
    }

    // Mark first and last items specially
    int lastIndex = items.size() - 1;
    List<LineItem> marked =
        IndexedTraversals.imodify(
            itemsIndexed,
            (index, item) -> {
              String prefix = "";
              if (index == 0) prefix = "[FIRST] ";
              if (index == lastIndex) prefix = "[LAST] ";
              return new LineItem(prefix + item.productName(), item.quantity(), item.price());
            },
            items);

    System.out.println("\nWith first/last markers:");
    for (LineItem item : marked) {
      System.out.println("  " + item.productName());
    }

    System.out.println();
  }

  /** Demonstrates filtering by index and by value with index. */
  private static void demonstrateIndexedFiltering() {
    System.out.println("--- Indexed Filtering ---");

    IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();

    List<String> values = List.of("a", "b", "c", "d", "e", "f", "g", "h");

    // Filter to odd positions only (1, 3, 5, 7)
    IndexedTraversal<Integer, List<String>, String> oddPositions =
        indexed.filterIndex(i -> i % 2 == 1);

    List<Pair<Integer, String>> oddPairs = IndexedTraversals.toIndexedList(oddPositions, values);
    System.out.println("Odd positions (indices preserved):");
    for (Pair<Integer, String> pair : oddPairs) {
      System.out.println("  Index " + pair.first() + ": " + pair.second());
    }

    // Filter by both index and value
    List<LineItem> items =
        List.of(
            new LineItem("Laptop", 1, 999.99), // Index 0, expensive
            new LineItem("Pen", 1, 2.99), // Index 1, cheap
            new LineItem("Keyboard", 1, 79.99), // Index 2, mid-range
            new LineItem("Mouse", 1, 24.99), // Index 3, cheap
            new LineItem("Monitor", 1, 299.99)); // Index 4, expensive

    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    // Filter: even positions AND price > £50
    IndexedTraversal<Integer, List<LineItem>, LineItem> evenAndExpensive =
        itemsIndexed.filterIndex(i -> i % 2 == 0).filtered(item -> item.price() > 50);

    List<Pair<Integer, LineItem>> filtered =
        IndexedTraversals.toIndexedList(evenAndExpensive, items);

    System.out.println("\nEven positions AND price > £50:");
    for (Pair<Integer, LineItem> pair : filtered) {
      System.out.printf(
          "  Position %d: %s (£%.2f)%n",
          pair.first(), pair.second().productName(), pair.second().price());
    }

    System.out.println();
  }

  /** Demonstrates IndexedLens for field name tracking. */
  private static void demonstrateLensIndexing() {
    System.out.println("--- IndexedLens for Field Tracking ---");

    // Create indexed lens for customer email field
    IndexedLens<String, Customer, String> emailLens =
        IndexedLens.of(
            "email", // The index: field name
            Customer::email, // Getter
            (customer, newEmail) -> new Customer(customer.name(), newEmail) // Setter
            );

    Customer customer = new Customer("Alice", "alice@example.com");

    // Get both field name and value
    Pair<String, String> fieldInfo = emailLens.iget(customer);
    System.out.println("Field name: " + fieldInfo.first());
    System.out.println("Field value: " + fieldInfo.second());

    // Modify with field name awareness
    Customer updated =
        emailLens.imodify(
            (fieldName, oldValue) -> {
              System.out.println("\nModifying field '" + fieldName + "' from '" + oldValue + "'");
              return "alice.smith@example.com";
            },
            customer);

    System.out.println("Updated customer: " + updated);

    System.out.println();
  }

  /** Demonstrates audit trail pattern with field change tracking. */
  private static void demonstrateAuditTrail() {
    System.out.println("--- Audit Trail Pattern ---");

    // Field change record
    record FieldChange(String fieldName, Object oldValue, Object newValue, Instant timestamp) {}

    List<FieldChange> auditLog = new ArrayList<>();

    // Create indexed lens with audit logging
    IndexedLens<String, Customer, String> emailLens =
        IndexedLens.of("email", Customer::email, (c, email) -> new Customer(c.name(), email));

    Customer customer = new Customer("Bob", "bob@old.com");

    // Modify with audit logging
    Customer updated =
        emailLens.imodify(
            (fieldName, oldValue) -> {
              String newValue = "bob@new.com";

              if (!oldValue.equals(newValue)) {
                auditLog.add(new FieldChange(fieldName, oldValue, newValue, Instant.now()));
              }

              return newValue;
            },
            customer);

    System.out.println("Original: " + customer);
    System.out.println("Updated: " + updated);
    System.out.println("\nAudit log:");
    for (FieldChange change : auditLog) {
      System.out.printf(
          "  Field '%s' changed from '%s' to '%s' at %s%n",
          change.fieldName(), change.oldValue(), change.newValue(), change.timestamp());
    }

    System.out.println();
  }

  /** Demonstrates composing indexed optics for nested structures. */
  private static void demonstrateComposition() {
    System.out.println("--- Composing Indexed Optics ---");

    // Nested structure: List of Orders, each with List of LineItems
    record OrderWithItems(String id, List<LineItem> items) {}

    // First level: indexed traversal for orders
    IndexedTraversal<Integer, List<OrderWithItems>, OrderWithItems> ordersIndexed =
        IndexedTraversals.forList();

    // Second level: lens to items field
    Lens<OrderWithItems, List<LineItem>> itemsLens =
        Lens.of(OrderWithItems::items, (order, items) -> new OrderWithItems(order.id(), items));

    // Third level: indexed traversal for items
    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    // Compose: orders → items field → each item with PAIRED indices
    IndexedTraversal<Pair<Integer, Integer>, List<OrderWithItems>, LineItem> composed =
        ordersIndexed.andThen(itemsLens.asTraversal()).iandThen(itemsIndexed);

    List<OrderWithItems> orders =
        List.of(
            new OrderWithItems(
                "ORD-1",
                List.of(new LineItem("Laptop", 1, 999.99), new LineItem("Mouse", 1, 24.99))),
            new OrderWithItems(
                "ORD-2",
                List.of(new LineItem("Keyboard", 1, 79.99), new LineItem("Monitor", 1, 299.99))));

    // Access with paired indices: (order index, item index)
    List<Pair<Pair<Integer, Integer>, LineItem>> all =
        IndexedTraversals.toIndexedList(composed, orders);

    System.out.println("All items with paired indices:");
    for (Pair<Pair<Integer, Integer>, LineItem> entry : all) {
      Pair<Integer, Integer> indices = entry.first();
      LineItem item = entry.second();
      System.out.printf(
          "  Order %d, Item %d: %s%n", indices.first(), indices.second(), item.productName());
    }

    // Modify with full path visibility
    List<OrderWithItems> updated =
        IndexedTraversals.imodify(
            composed,
            (indices, item) -> {
              int orderIdx = indices.first();
              int itemIdx = indices.second();
              System.out.printf(
                  "Processing [order=%d, item=%d]: %s%n", orderIdx, itemIdx, item.productName());
              return item;
            },
            orders);

    System.out.println();
  }

  /** Demonstrates a comprehensive real-world order fulfilment scenario. */
  private static void demonstrateRealWorldScenario() {
    System.out.println("--- Real-World: Order Fulfilment Dashboard ---");

    Order order =
        new Order(
            "ORD-12345",
            List.of(
                new LineItem("Laptop", 1, 999.99),
                new LineItem("Mouse", 2, 24.99),
                new LineItem("Keyboard", 1, 79.99),
                new LineItem("Monitor", 1, 299.99)),
            new LinkedHashMap<>(
                Map.of(
                    "priority", "express",
                    "gift-wrap", "true",
                    "delivery-note", "Leave at door")));

    // Task 1: Generate packing slip
    System.out.println("Packing Slip for " + order.orderId() + ":");
    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    List<Pair<Integer, LineItem>> indexedItems =
        IndexedTraversals.toIndexedList(itemsIndexed, order.items());

    for (Pair<Integer, LineItem> pair : indexedItems) {
      int position = pair.first() + 1; // 1-based for display
      LineItem item = pair.second();
      System.out.printf(
          "  Item %d: %s (Qty: %d) - £%.2f%n",
          position, item.productName(), item.quantity(), item.price() * item.quantity());
    }

    // Task 2: Apply position-based discount (every 3rd item gets 15% off)
    System.out.println("\nApplying position-based discounts:");
    List<LineItem> discounted =
        IndexedTraversals.imodify(
            itemsIndexed,
            (index, item) -> {
              if ((index + 1) % 3 == 0) {
                double newPrice = item.price() * 0.85;
                System.out.printf(
                    "  Position %d (%s): £%.2f → £%.2f (15%% off)%n",
                    index + 1, item.productName(), item.price(), newPrice);
                return new LineItem(item.productName(), item.quantity(), newPrice);
              }
              return item;
            },
            order.items());

    double originalTotal =
        order.items().stream().mapToDouble(item -> item.price() * item.quantity()).sum();
    double discountedTotal =
        discounted.stream().mapToDouble(item -> item.price() * item.quantity()).sum();

    System.out.printf("Original total: £%.2f%n", originalTotal);
    System.out.printf("Discounted total: £%.2f%n", discountedTotal);

    // Task 3: Process metadata with key awareness
    System.out.println("\nProcessing metadata:");
    IndexedTraversal<String, Map<String, String>, String> metadataIndexed =
        IndexedTraversals.forMap();

    List<Pair<String, String>> metadataEntries =
        IndexedTraversals.toIndexedList(metadataIndexed, order.metadata());

    for (Pair<String, String> entry : metadataEntries) {
      String key = entry.first();
      String value = entry.second();

      switch (key) {
        case "priority" -> System.out.println("  Shipping priority: " + value.toUpperCase());
        case "gift-wrap" ->
            System.out.println(
                "  Gift wrapping: " + (value.equals("true") ? "Required" : "Not required"));
        case "delivery-note" -> System.out.println("  Special instructions: " + value);
        default -> System.out.println("  " + key + ": " + value);
      }
    }

    // Task 4: Identify high-value positions (items > £100)
    System.out.println("\nHigh-value items (require special handling):");
    IndexedTraversal<Integer, List<LineItem>, LineItem> highValue =
        itemsIndexed.filteredWithIndex((index, item) -> item.price() > 100);

    List<Pair<Integer, LineItem>> expensive =
        IndexedTraversals.toIndexedList(highValue, order.items());

    for (Pair<Integer, LineItem> pair : expensive) {
      System.out.printf(
          "  Position %d: %s (£%.2f)%n",
          pair.first() + 1, pair.second().productName(), pair.second().price());
    }

    System.out.println("\n=== Indexed optics enable position-aware business logic ===");
  }

  /**
   * Demonstrates ForIndexed comprehension as a fluent alternative to IndexedTraversals.imodify().
   *
   * <p>ForIndexed provides a comprehension-style API for position-aware traversal operations,
   * combining filtering, modification, and collection in a fluent chain.
   */
  private static void demonstrateForIndexedComprehension() {
    System.out.println("--- ForIndexed: Comprehension-Style Position-Aware Operations ---");

    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    // Lens for accessing the price field
    Lens<LineItem, Double> priceLens =
        Lens.of(
            LineItem::price,
            (item, price) -> new LineItem(item.productName(), item.quantity(), price));

    List<LineItem> items =
        List.of(
            new LineItem("Laptop", 1, 999.99),
            new LineItem("Mouse", 2, 24.99),
            new LineItem("Keyboard", 1, 79.99),
            new LineItem("Monitor", 1, 299.99),
            new LineItem("Webcam", 1, 59.99));

    System.out.println("Original items:");
    for (int i = 0; i < items.size(); i++) {
      System.out.printf(
          "  Position %d: %s - £%.2f%n", i, items.get(i).productName(), items.get(i).price());
    }

    // Example 1: Apply position-based discount using ForIndexed
    // Items at even positions (0, 2, 4) get 10% off
    Kind<IdKind.Witness, List<LineItem>> discountedResult =
        ForIndexed.overIndexed(itemsIndexed, items, IdMonad.instance())
            .filterIndex(i -> i % 2 == 0)
            .modify(priceLens, (index, price) -> price * 0.9)
            .run();

    List<LineItem> discounted = ID.unwrap(discountedResult);

    System.out.println("\nAfter ForIndexed even-position discount (10% off):");
    for (int i = 0; i < discounted.size(); i++) {
      LineItem item = discounted.get(i);
      System.out.printf("  Position %d: %s - £%.2f", i, item.productName(), item.price());
      if (i % 2 == 0) {
        System.out.print(" (discounted)");
      }
      System.out.println();
    }

    // Example 2: Combined index and value filtering
    // Only discount expensive items (>£50) at the first 3 positions
    Kind<IdKind.Witness, List<LineItem>> combinedResult =
        ForIndexed.overIndexed(itemsIndexed, items, IdMonad.instance())
            .filter((index, item) -> index < 3 && item.price() > 50.0)
            .modify(priceLens, (index, price) -> price * 0.85) // 15% off
            .run();

    List<LineItem> combinedFiltered = ID.unwrap(combinedResult);

    System.out.println("\nAfter combined filter (first 3 positions AND price > £50, 15% off):");
    for (int i = 0; i < combinedFiltered.size(); i++) {
      LineItem item = combinedFiltered.get(i);
      boolean wasDiscounted = i < 3 && items.get(i).price() > 50.0;
      System.out.printf("  Position %d: %s - £%.2f", i, item.productName(), item.price());
      if (wasDiscounted) {
        System.out.print(" (discounted)");
      }
      System.out.println();
    }

    // Example 3: Collect items with their indices
    Kind<IdKind.Witness, List<Pair<Integer, LineItem>>> indexedListResult =
        ForIndexed.overIndexed(itemsIndexed, items, IdMonad.instance())
            .filterIndex(i -> i % 2 == 1) // Odd positions only
            .toIndexedList();

    List<Pair<Integer, LineItem>> indexedList = ID.unwrap(indexedListResult);

    System.out.println("\nOdd-positioned items collected with indices:");
    for (Pair<Integer, LineItem> pair : indexedList) {
      System.out.printf(
          "  Index %d: %s (£%.2f)%n",
          pair.first(), pair.second().productName(), pair.second().price());
    }

    System.out.println(
        "\n=== ForIndexed provides fluent, comprehension-style position-aware operations ===\n");
  }
}
