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
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
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
 * <p>The records are the chapter's cast, imported from its package. The path-tracking example nests
 * the cast's orders one level deeper, in a local record inside the method that shows it.
 */
public final class IndexedAdvancedBook {

  private IndexedAdvancedBook() {}

  /** The audit example's updated customer, and the changes it logged. */
  record Audited(Customer updated, List<AuditLog.FieldChange<?>> audit) {}

  /** The three pairs the utilities example builds from {@code Pair.of(1, "Hello")}. */
  record PairUtilities(
      int first,
      String second,
      Pair<Integer, String> modified,
      Pair<String, String> transformed,
      Pair<String, Integer> swapped) {}

  static void pairedIndices(List<Order> orders) {
    // ANCHOR: paired_indices
    // Nested structure: a list of orders, each with its list of lines. The orders here are two:
    // a laptop and a mouse, then a keyboard, a monitor and two cables.

    // First level: indexed traversal for orders
    IndexedTraversal<Integer, List<Order>, Order> ordersIndexed = IndexedTraversals.forList();

    // Second level: lens to the lines field
    Lens<Order, List<LineItem>> linesLens = OrderLenses.lines();

    // Third level: indexed traversal for the lines
    IndexedTraversal<Integer, List<LineItem>, LineItem> linesIndexed = IndexedTraversals.forList();

    // Compose: orders → lines field → each line with PAIRED indices
    IndexedTraversal<Pair<Integer, Integer>, List<Order>, LineItem> composed =
        ordersIndexed.andThen(linesLens.asTraversal()).iandThen(linesIndexed);

    // Access with paired indices: (order index, line index)
    List<Pair<Pair<Integer, Integer>, LineItem>> all =
        IndexedTraversals.toIndexedList(composed, orders);

    for (Pair<Pair<Integer, Integer>, LineItem> entry : all) {
      Pair<Integer, Integer> indices = entry.first();
      LineItem item = entry.second();
      System.out.printf("Order %d, Item %d: %s%n", indices.first(), indices.second(), item.sku());
    }
    // Output:
    // Order 0, Item 0: LAPTOP
    // Order 0, Item 1: MOUSE
    // Order 1, Item 0: KEYBOARD
    // Order 1, Item 1: MONITOR
    // Order 1, Item 2: CABLE
    // ANCHOR_END: paired_indices
  }

  static List<String> oneBased(List<LineItem> items) {
    // ANCHOR: index_transformation
    IndexedTraversal<Integer, List<String>, String> zeroIndexed = IndexedTraversals.forList();
    List<String> skus = items.stream().map(LineItem::sku).toList();

    List<String> numbered =
        IndexedTraversals.imodify(
            zeroIndexed,
            (zeroBasedIndex, sku) -> {
              int oneBasedIndex = zeroBasedIndex + 1;
              return "Item " + oneBasedIndex + ": " + sku;
            },
            skus);
    // The labels are "Item 1: LAPTOP", "Item 2: MOUSE" and "Item 3: KEYBOARD"
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
            new LineItem("LAPTOP", 1, new BigDecimal("999.99")), // Index 0, expensive ✓
            new LineItem("PEN", 1, new BigDecimal("2.99")), // Index 1, cheap ✗
            new LineItem("KEYBOARD", 1, new BigDecimal("79.99")), // Index 2, expensive ✓
            new LineItem("MOUSE", 1, new BigDecimal("24.99")), // Index 3, cheap ✗
            new LineItem("MONITOR", 1, new BigDecimal("299.99"))); // Index 4, expensive ✓

    List<Pair<Integer, LineItem>> results = IndexedTraversals.toIndexedList(targeted, items);
    // LAPTOP at index 0, KEYBOARD at 2 and MONITOR at 4:
    // all at even positions AND expensive
    // ANCHOR_END: layered_filters
    return results;
  }

  static Audited auditTrail() {
    // ANCHOR: audit_usage
    // Usage with indexed lens
    IndexedLens<String, Customer, String> nameLens =
        IndexedLens.of("name", Customer::name, (c, name) -> new Customer(name, c.email()));

    List<AuditLog.FieldChange<?>> audit = new ArrayList<>();

    Customer customer = new Customer("Ada", new EmailAddress("ada@example.com"));

    Customer updated =
        nameLens.imodify(AuditLog.loggedModification(name -> "Ada Lovelace", audit), customer);

    // Check audit log
    for (AuditLog.FieldChange<?> change : audit) {
      System.out.printf(
          "Field '%s' changed from %s to %s at %s%n",
          change.fieldName(), change.oldValue(), change.newValue(), change.timestamp());
    }
    // Output, ending with the instant the change was made, which differs on every run:
    // Field 'name' changed from Ada to Ada Lovelace at ...
    // ANCHOR_END: audit_usage
    return new Audited(updated, audit);
  }

  static void pathTracking(List<Order> adasOrders) {
    // ANCHOR: path_tracking
    // Nested structure with multiple levels: each history's orders, each order's lines
    record OrderHistory(List<Order> orders) {}

    // Build an indexed path through the structure
    IndexedTraversal<Integer, List<OrderHistory>, OrderHistory> historiesIdx =
        IndexedTraversals.forList();

    Lens<OrderHistory, List<Order>> ordersLens =
        Lens.of(OrderHistory::orders, (history, orders) -> new OrderHistory(orders));

    IndexedTraversal<Integer, List<Order>, Order> ordersIdx = IndexedTraversals.forList();

    Lens<Order, List<LineItem>> linesLens = OrderLenses.lines();

    IndexedTraversal<Integer, List<LineItem>, LineItem> linesIdx = IndexedTraversals.forList();

    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();

    // Compose the full indexed path
    IndexedTraversal<Pair<Pair<Integer, Integer>, Integer>, List<OrderHistory>, BigDecimal>
        fullPath =
            historiesIdx
                .andThen(ordersLens.asTraversal())
                .iandThen(ordersIdx)
                .andThen(linesLens.asTraversal())
                .iandThen(linesIdx)
                .andThen(priceLens.asTraversal());

    // One customer, Ada, with two orders: a laptop and a mouse, then a keyboard
    List<OrderHistory> histories = List.of(new OrderHistory(adasOrders));

    // Modify with full path visibility
    List<OrderHistory> updated =
        IndexedTraversals.imodify(
            fullPath,
            (indices, price) -> {
              int customerIdx = indices.first().first();
              int orderIdx = indices.first().second();
              int itemIdx = indices.second();
              // 10% increase, rounded back to pence
              BigDecimal raised =
                  price.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_EVEN);

              System.out.printf(
                  "Updating price at [history=%d, order=%d, item=%d]: %.2f -> %.2f%n",
                  customerIdx, orderIdx, itemIdx, price, raised);

              return raised;
            },
            histories);
    // Output shows the complete path to every modified price:
    // Updating price at [history=0, order=0, item=0]: 999.99 -> 1099.99
    // Updating price at [history=0, order=0, item=1]: 24.99 -> 27.49
    // Updating price at [history=0, order=1, item=0]: 79.99 -> 87.99
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
