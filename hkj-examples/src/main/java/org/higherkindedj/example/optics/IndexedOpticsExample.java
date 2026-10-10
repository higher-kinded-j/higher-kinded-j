// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.ForIndexed;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
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

  // The records are the chapter's order service, imported from its cast package.

  private static final Customer ADA = new Customer("Ada", new EmailAddress("ada@example.com"));

  private static final BigDecimal FIFTY = new BigDecimal("50");

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  // An order of Ada's with the given lines, placed on the same day, in pounds
  private static Order order(String id, List<LineItem> lines) {
    return new Order(
        UUID.fromString(id),
        ADA,
        lines,
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        OrderStatus.NEW);
  }

  private static LineItem line(String sku, int quantity, String price) {
    return new LineItem(sku, quantity, new BigDecimal(price));
  }

  // A price with a discount applied, to the penny
  private static BigDecimal discounted(BigDecimal price, String factor) {
    return price.multiply(new BigDecimal(factor)).setScale(2, RoundingMode.HALF_EVEN);
  }

  // A line's total: its price times its quantity
  private static BigDecimal lineTotal(LineItem item) {
    return item.price().multiply(BigDecimal.valueOf(item.quantity()));
  }

  private static BigDecimal total(List<LineItem> items) {
    return items.stream()
        .map(IndexedOpticsExample::lineTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

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
            line("LAPTOP", 1, "999.99"),
            line("MOUSE", 2, "24.99"),
            line("KEYBOARD", 1, "79.99"),
            line("MONITOR", 1, "299.99"),
            line("WEBCAM", 1, "59.99"),
            line("HEADPHONES", 1, "149.99"));

    System.out.println("Original items:");
    for (int i = 0; i < items.size(); i++) {
      System.out.printf("  Position %d: %s - £%.2f%n", i, items.get(i).sku(), items.get(i).price());
    }

    // Apply 10% discount to items at even positions (0, 2, 4...)
    List<LineItem> discounted =
        IndexedTraversals.imodify(
            itemsIndexed,
            (index, item) -> {
              if (index % 2 == 0) {
                BigDecimal newPrice = discounted(item.price(), "0.90");
                return new LineItem(item.sku(), item.quantity(), newPrice);
              }
              return item;
            },
            items);

    System.out.println("\nAfter even-position discount (10% off):");
    for (int i = 0; i < discounted.size(); i++) {
      LineItem item = discounted.get(i);
      System.out.printf("  Position %d: %s - £%.2f", i, item.sku(), item.price());
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
              return new LineItem(prefix + item.sku(), item.quantity(), item.price());
            },
            items);

    System.out.println("\nWith first/last markers:");
    for (LineItem item : marked) {
      System.out.println("  " + item.sku());
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
            line("LAPTOP", 1, "999.99"), // Index 0, expensive
            line("PEN", 1, "2.99"), // Index 1, cheap
            line("KEYBOARD", 1, "79.99"), // Index 2, mid-range
            line("MOUSE", 1, "24.99"), // Index 3, cheap
            line("MONITOR", 1, "299.99")); // Index 4, expensive

    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    // Filter: even positions AND price > £50
    IndexedTraversal<Integer, List<LineItem>, LineItem> evenAndExpensive =
        itemsIndexed
            .filterIndex(i -> i % 2 == 0)
            .filtered(item -> item.price().compareTo(FIFTY) > 0);

    List<Pair<Integer, LineItem>> filtered =
        IndexedTraversals.toIndexedList(evenAndExpensive, items);

    System.out.println("\nEven positions AND price > £50:");
    for (Pair<Integer, LineItem> pair : filtered) {
      System.out.printf(
          "  Position %d: %s (£%.2f)%n", pair.first(), pair.second().sku(), pair.second().price());
    }

    System.out.println();
  }

  /** Demonstrates IndexedLens for field name tracking. */
  private static void demonstrateLensIndexing() {
    System.out.println("--- IndexedLens for Field Tracking ---");

    // Create indexed lens for customer email field
    IndexedLens<String, Customer, EmailAddress> emailLens =
        IndexedLens.of(
            "email", // The index: field name
            Customer::email, // Getter
            (customer, newEmail) -> new Customer(customer.name(), newEmail) // Setter
            );

    Customer customer = ADA;

    // Get both field name and value
    Pair<String, EmailAddress> fieldInfo = emailLens.iget(customer);
    System.out.println("Field name: " + fieldInfo.first());
    System.out.println("Field value: " + fieldInfo.second().value());

    // Modify with field name awareness
    Customer updated =
        emailLens.imodify(
            (fieldName, oldValue) -> {
              System.out.println(
                  "\nModifying field '" + fieldName + "' from '" + oldValue.value() + "'");
              return new EmailAddress("ada.lovelace@example.com");
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
    IndexedLens<String, Customer, EmailAddress> emailLens =
        IndexedLens.of("email", Customer::email, (c, email) -> new Customer(c.name(), email));

    Customer customer = new Customer("Grace", new EmailAddress("grace@old.example"));

    // Modify with audit logging
    Customer updated =
        emailLens.imodify(
            (fieldName, oldValue) -> {
              EmailAddress newValue = new EmailAddress("grace@new.example");

              if (!oldValue.equals(newValue)) {
                auditLog.add(
                    new FieldChange(fieldName, oldValue.value(), newValue.value(), Instant.now()));
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

    // First level: indexed traversal for orders
    IndexedTraversal<Integer, List<Order>, Order> ordersIndexed = IndexedTraversals.forList();

    // Second level: lens to the lines field
    Lens<Order, List<LineItem>> linesLens = OrderLenses.lines();

    // Third level: indexed traversal for the lines
    IndexedTraversal<Integer, List<LineItem>, LineItem> linesIndexed = IndexedTraversals.forList();

    // Compose: orders → lines field → each line with PAIRED indices
    IndexedTraversal<Pair<Integer, Integer>, List<Order>, LineItem> composed =
        ordersIndexed.andThen(linesLens.asTraversal()).iandThen(linesIndexed);

    List<Order> orders =
        List.of(
            order(
                "00000000-0000-0000-0000-000000000001",
                List.of(line("LAPTOP", 1, "999.99"), line("MOUSE", 1, "24.99"))),
            order(
                "00000000-0000-0000-0000-000000000002",
                List.of(line("KEYBOARD", 1, "79.99"), line("MONITOR", 1, "299.99"))));

    // Access with paired indices: (order index, item index)
    List<Pair<Pair<Integer, Integer>, LineItem>> all =
        IndexedTraversals.toIndexedList(composed, orders);

    System.out.println("All items with paired indices:");
    for (Pair<Pair<Integer, Integer>, LineItem> entry : all) {
      Pair<Integer, Integer> indices = entry.first();
      LineItem item = entry.second();
      System.out.printf("  Order %d, Item %d: %s%n", indices.first(), indices.second(), item.sku());
    }

    // Modify with full path visibility
    List<Order> updated =
        IndexedTraversals.imodify(
            composed,
            (indices, item) -> {
              int orderIdx = indices.first();
              int itemIdx = indices.second();
              System.out.printf(
                  "Processing [order=%d, item=%d]: %s%n", orderIdx, itemIdx, item.sku());
              return item;
            },
            orders);

    System.out.println();
  }

  /** Demonstrates a comprehensive real-world order fulfilment scenario. */
  private static void demonstrateRealWorldScenario() {
    System.out.println("--- Real-World: Order Fulfilment Dashboard ---");

    Order order =
        order(
            "00000000-0000-0000-0000-000000012345",
            List.of(
                line("LAPTOP", 1, "999.99"),
                line("MOUSE", 2, "24.99"),
                line("KEYBOARD", 1, "79.99"),
                line("MONITOR", 1, "299.99")));

    // The order's metadata, kept beside it, put in the order it is printed
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("priority", "express");
    metadata.put("gift-wrap", "true");
    metadata.put("delivery-note", "Leave at door");

    // Task 1: Generate packing slip
    System.out.println("Packing Slip for " + order.id() + ":");
    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    List<Pair<Integer, LineItem>> indexedItems =
        IndexedTraversals.toIndexedList(itemsIndexed, order.lines());

    for (Pair<Integer, LineItem> pair : indexedItems) {
      int position = pair.first() + 1; // 1-based for display
      LineItem item = pair.second();
      System.out.printf(
          "  Item %d: %s (Qty: %d) - £%.2f%n",
          position, item.sku(), item.quantity(), lineTotal(item));
    }

    // Task 2: Apply position-based discount (every 3rd item gets 15% off)
    System.out.println("\nApplying position-based discounts:");
    List<LineItem> discounted =
        IndexedTraversals.imodify(
            itemsIndexed,
            (index, item) -> {
              if ((index + 1) % 3 == 0) {
                BigDecimal newPrice = discounted(item.price(), "0.85");
                System.out.printf(
                    "  Position %d (%s): £%.2f → £%.2f (15%% off)%n",
                    index + 1, item.sku(), item.price(), newPrice);
                return new LineItem(item.sku(), item.quantity(), newPrice);
              }
              return item;
            },
            order.lines());

    System.out.printf("Original total: £%.2f%n", total(order.lines()));
    System.out.printf("Discounted total: £%.2f%n", total(discounted));

    // Task 3: Process metadata with key awareness
    System.out.println("\nProcessing metadata:");
    IndexedTraversal<String, Map<String, String>, String> metadataIndexed =
        IndexedTraversals.forMap();

    List<Pair<String, String>> metadataEntries =
        IndexedTraversals.toIndexedList(metadataIndexed, metadata);

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
        itemsIndexed.filteredWithIndex((index, item) -> item.price().compareTo(ONE_HUNDRED) > 0);

    List<Pair<Integer, LineItem>> expensive =
        IndexedTraversals.toIndexedList(highValue, order.lines());

    for (Pair<Integer, LineItem> pair : expensive) {
      System.out.printf(
          "  Position %d: %s (£%.2f)%n",
          pair.first() + 1, pair.second().sku(), pair.second().price());
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

    // Lens for accessing the price field, generated for the cast
    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();

    List<LineItem> items =
        List.of(
            line("LAPTOP", 1, "999.99"),
            line("MOUSE", 2, "24.99"),
            line("KEYBOARD", 1, "79.99"),
            line("MONITOR", 1, "299.99"),
            line("WEBCAM", 1, "59.99"));

    System.out.println("Original items:");
    for (int i = 0; i < items.size(); i++) {
      System.out.printf("  Position %d: %s - £%.2f%n", i, items.get(i).sku(), items.get(i).price());
    }

    // Example 1: Apply position-based discount using ForIndexed
    // Items at even positions (0, 2, 4) get 10% off
    Kind<IdKind.Witness, List<LineItem>> discountedResult =
        ForIndexed.overIndexed(itemsIndexed, items, Instances.monad(id()))
            .filterIndex(i -> i % 2 == 0)
            .modify(priceLens, (index, price) -> discounted(price, "0.90"))
            .run();

    List<LineItem> discounted = ID.unwrap(discountedResult);

    System.out.println("\nAfter ForIndexed even-position discount (10% off):");
    for (int i = 0; i < discounted.size(); i++) {
      LineItem item = discounted.get(i);
      System.out.printf("  Position %d: %s - £%.2f", i, item.sku(), item.price());
      if (i % 2 == 0) {
        System.out.print(" (discounted)");
      }
      System.out.println();
    }

    // Example 2: Combined index and value filtering
    // Only discount expensive items (>£50) at the first 3 positions
    Kind<IdKind.Witness, List<LineItem>> combinedResult =
        ForIndexed.overIndexed(itemsIndexed, items, Instances.monad(id()))
            .filter((index, item) -> index < 3 && item.price().compareTo(FIFTY) > 0)
            .modify(priceLens, (index, price) -> discounted(price, "0.85")) // 15% off
            .run();

    List<LineItem> combinedFiltered = ID.unwrap(combinedResult);

    System.out.println("\nAfter combined filter (first 3 positions AND price > £50, 15% off):");
    for (int i = 0; i < combinedFiltered.size(); i++) {
      LineItem item = combinedFiltered.get(i);
      boolean wasDiscounted = i < 3 && items.get(i).price().compareTo(FIFTY) > 0;
      System.out.printf("  Position %d: %s - £%.2f", i, item.sku(), item.price());
      if (wasDiscounted) {
        System.out.print(" (discounted)");
      }
      System.out.println();
    }

    // Example 3: Collect items with their indices
    Kind<IdKind.Witness, List<Pair<Integer, LineItem>>> indexedListResult =
        ForIndexed.overIndexed(itemsIndexed, items, Instances.monad(id()))
            .filterIndex(i -> i % 2 == 1) // Odd positions only
            .toIndexedList();

    List<Pair<Integer, LineItem>> indexedList = ID.unwrap(indexedListResult);

    System.out.println("\nOdd-positioned items collected with indices:");
    for (Pair<Integer, LineItem> pair : indexedList) {
      System.out.printf(
          "  Index %d: %s (£%.2f)%n", pair.first(), pair.second().sku(), pair.second().price());
    }

    System.out.println(
        "\n=== ForIndexed provides fluent, comprehension-style position-aware operations ===\n");
  }
}
