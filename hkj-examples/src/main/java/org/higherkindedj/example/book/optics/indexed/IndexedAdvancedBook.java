// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.indexed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.indexed.IndexedLens;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/indexed_optics_advanced.html">Indexed
 * Optics: Advanced Patterns</a> page, where composed indexed optics carry the whole path to each
 * value. The page {@code {{#include}}}s the anchored regions, and {@code IndexedAdvancedBookTest}
 * holds the claims the page makes about this code.
 *
 * <p>The page declares each nesting where it uses it, so the orders, items and buyers here are
 * local records inside the method that shows them.
 */
public final class IndexedAdvancedBook {

  private IndexedAdvancedBook() {}

  /** An order line, as the page's other examples use it. */
  record LineItem(String productName, int quantity, BigDecimal price) {}

  /** A customer whose email the audit trail tracks. */
  record Customer(String name, String email) {}

  /** The audit example's updated customer, and the changes it logged. */
  record Audited(Customer updated, List<AuditLog.FieldChange<?>> audit) {}

  /** The three pairs the utilities example builds from {@code Pair.of(1, "Hello")}. */
  record PairUtilities(
      int first,
      String second,
      Pair<Integer, String> modified,
      Pair<String, String> transformed,
      Pair<String, Integer> swapped) {}

  static void pairedIndices() {
    // ANCHOR: paired_indices
    // Nested structure: List of Orders, each with List of Items
    record Order(String id, List<LineItem> items) {}

    // First level: indexed traversal for orders
    IndexedTraversal<Integer, List<Order>, Order> ordersIndexed = IndexedTraversals.forList();

    // Second level: lens to items field
    Lens<Order, List<LineItem>> itemsLens =
        Lens.of(Order::items, (order, items) -> new Order(order.id(), items));

    // Third level: indexed traversal for items
    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    // Compose: orders → items field → each item with PAIRED indices
    IndexedTraversal<Pair<Integer, Integer>, List<Order>, LineItem> composed =
        ordersIndexed.andThen(itemsLens.asTraversal()).iandThen(itemsIndexed);

    List<Order> orders =
        List.of(
            new Order(
                "ORD-1",
                List.of(
                    new LineItem("Laptop", 1, new BigDecimal("999.99")),
                    new LineItem("Mouse", 1, new BigDecimal("24.99")))),
            new Order(
                "ORD-2",
                List.of(
                    new LineItem("Keyboard", 1, new BigDecimal("79.99")),
                    new LineItem("Monitor", 1, new BigDecimal("299.99")),
                    new LineItem("Cable", 2, new BigDecimal("9.99")))));

    // Access with paired indices: (order index, item index)
    List<Pair<Pair<Integer, Integer>, LineItem>> all =
        IndexedTraversals.toIndexedList(composed, orders);

    for (Pair<Pair<Integer, Integer>, LineItem> entry : all) {
      Pair<Integer, Integer> indices = entry.first();
      LineItem item = entry.second();
      System.out.printf(
          "Order %d, Item %d: %s%n", indices.first(), indices.second(), item.productName());
    }
    // Output:
    // Order 0, Item 0: Laptop
    // Order 0, Item 1: Mouse
    // Order 1, Item 0: Keyboard
    // Order 1, Item 1: Monitor
    // Order 1, Item 2: Cable
    // ANCHOR_END: paired_indices
  }

  static List<LineItem> oneBased(List<LineItem> items) {
    // ANCHOR: index_transformation
    IndexedTraversal<Integer, List<LineItem>, LineItem> zeroIndexed = IndexedTraversals.forList();

    List<LineItem> numbered =
        IndexedTraversals.imodify(
            zeroIndexed,
            (zeroBasedIndex, item) -> {
              int oneBasedIndex = zeroBasedIndex + 1;
              return new LineItem(
                  "Item " + oneBasedIndex + ": " + item.productName(),
                  item.quantity(),
                  item.price());
            },
            items);
    // The product names become "Item 1: Laptop", "Item 2: Mouse" and "Item 3: Keyboard"
    // ANCHOR_END: index_transformation
    return numbered;
  }

  static List<Pair<Integer, LineItem>> layeredFilters() {
    // ANCHOR: layered_filters
    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    // Filter: even positions AND expensive items
    IndexedTraversal<Integer, List<LineItem>, LineItem> targeted =
        itemsIndexed
            .filterIndex(i -> i % 2 == 0) // Even positions only
            .filtered(item -> item.price().compareTo(new BigDecimal("50")) > 0); // Expensive only

    List<LineItem> items =
        List.of(
            new LineItem("Laptop", 1, new BigDecimal("999.99")), // Index 0, expensive ✓
            new LineItem("Pen", 1, new BigDecimal("2.99")), // Index 1, cheap ✗
            new LineItem("Keyboard", 1, new BigDecimal("79.99")), // Index 2, expensive ✓
            new LineItem("Mouse", 1, new BigDecimal("24.99")), // Index 3, cheap ✗
            new LineItem("Monitor", 1, new BigDecimal("299.99"))); // Index 4, expensive ✓

    List<Pair<Integer, LineItem>> results = IndexedTraversals.toIndexedList(targeted, items);
    // Laptop at index 0, Keyboard at 2 and Monitor at 4:
    // all at even positions AND expensive
    // ANCHOR_END: layered_filters
    return results;
  }

  static Audited auditTrail() {
    // ANCHOR: audit_usage
    // Usage with indexed lens
    IndexedLens<String, Customer, String> emailLens =
        IndexedLens.of("email", Customer::email, (c, email) -> new Customer(c.name(), email));

    List<AuditLog.FieldChange<?>> audit = new ArrayList<>();

    Customer customer = new Customer("Alice", "alice@old.com");

    Customer updated =
        emailLens.imodify(AuditLog.loggedModification(email -> "alice@new.com", audit), customer);

    // Check audit log
    for (AuditLog.FieldChange<?> change : audit) {
      System.out.printf(
          "Field '%s' changed from %s to %s at %s%n",
          change.fieldName(), change.oldValue(), change.newValue(), change.timestamp());
    }
    // Output, ending with the instant the change was made, which differs on every run:
    // Field 'email' changed from alice@old.com to alice@new.com at ...
    // ANCHOR_END: audit_usage
    return new Audited(updated, audit);
  }

  static void pathTracking() {
    // ANCHOR: path_tracking
    // Nested structure with multiple levels
    record Item(String name, BigDecimal price) {}
    record Order(List<Item> items) {}
    record Buyer(String name, List<Order> orders) {}

    // Build an indexed path through the structure
    IndexedTraversal<Integer, List<Buyer>, Buyer> buyersIdx = IndexedTraversals.forList();

    Lens<Buyer, List<Order>> ordersLens = Lens.of(Buyer::orders, (b, o) -> new Buyer(b.name(), o));

    IndexedTraversal<Integer, List<Order>, Order> ordersIdx = IndexedTraversals.forList();

    Lens<Order, List<Item>> itemsLens = Lens.of(Order::items, (order, items) -> new Order(items));

    IndexedTraversal<Integer, List<Item>, Item> itemsIdx = IndexedTraversals.forList();

    Lens<Item, BigDecimal> priceLens =
        Lens.of(Item::price, (item, price) -> new Item(item.name(), price));

    // Compose the full indexed path
    IndexedTraversal<Pair<Pair<Integer, Integer>, Integer>, List<Buyer>, BigDecimal> fullPath =
        buyersIdx
            .andThen(ordersLens.asTraversal())
            .iandThen(ordersIdx)
            .andThen(itemsLens.asTraversal())
            .iandThen(itemsIdx)
            .andThen(priceLens.asTraversal());

    List<Buyer> buyers =
        List.of(
            new Buyer(
                "Ada",
                List.of(
                    new Order(
                        List.of(
                            new Item("Laptop", new BigDecimal("999.99")),
                            new Item("Mouse", new BigDecimal("24.99")))),
                    new Order(List.of(new Item("Keyboard", new BigDecimal("79.99")))))));

    // Modify with full path visibility
    List<Buyer> updated =
        IndexedTraversals.imodify(
            fullPath,
            (indices, price) -> {
              int buyerIdx = indices.first().first();
              int orderIdx = indices.first().second();
              int itemIdx = indices.second();
              // 10% increase, rounded back to pence
              BigDecimal raised =
                  price.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_EVEN);

              System.out.printf(
                  "Updating price at [buyer=%d, order=%d, item=%d]: %.2f -> %.2f%n",
                  buyerIdx, orderIdx, itemIdx, price, raised);

              return raised;
            },
            buyers);
    // Output shows the complete path to every modified price:
    // Updating price at [buyer=0, order=0, item=0]: 999.99 -> 1099.99
    // Updating price at [buyer=0, order=0, item=1]: 24.99 -> 27.49
    // Updating price at [buyer=0, order=1, item=0]: 79.99 -> 87.99
    // ANCHOR_END: path_tracking
  }

  static PairUtilities pairUtilities() {
    // ANCHOR: pair_utilities
    Pair<Integer, String> pair = new Pair<>(1, "Hello");

    // Access components
    int first = pair.first();
    String second = pair.second();
    // first is 1, and second is "Hello"

    // Transform components
    Pair<Integer, String> modified = pair.withSecond("World");
    // Pair[first=1, second=World]

    Pair<String, String> transformed = pair.withFirst("One");
    // Pair[first=One, second=Hello]

    // Swap
    Pair<String, Integer> swapped = pair.swap();
    // Pair[first=Hello, second=1]

    // Factory method
    Pair<String, Integer> created = Pair.of("Key", 42);
    // ANCHOR_END: pair_utilities
    return new PairUtilities(first, second, modified, transformed, swapped);
  }
}

// ANCHOR: audit_log
// Generic field audit logger
final class AuditLog {
  record FieldChange<A>(String fieldName, A oldValue, A newValue, Instant timestamp) {}

  static <A> BiFunction<String, A, A> loggedModification(
      Function<A, A> transformation, List<FieldChange<?>> auditLog) {
    return (fieldName, oldValue) -> {
      A newValue = transformation.apply(oldValue);

      if (!oldValue.equals(newValue)) {
        auditLog.add(new FieldChange<>(fieldName, oldValue, newValue, Instant.now()));
      }

      return newValue;
    };
  }

  private AuditLog() {}
}
// ANCHOR_END: audit_log
