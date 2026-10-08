// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.limiting;

// ANCHOR: imports
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.ListTraversals;
import org.higherkindedj.optics.util.Traversals;

// ANCHOR_END: imports

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/limiting_traversals.html">Limiting
 * Traversals</a> page, one region per limiting method. The page {@code {{#include}}}s the anchored
 * regions, and {@code LimitingBookTest} holds the claims the page makes about this code.
 */
public final class LimitingBook {

  private LimitingBook() {}

  /** One step's list, the list {@code modify} returns, and what {@code getAll} focuses. */
  record Slice(List<Product> source, List<Product> modified, List<Product> focused) {}

  /** What the element example returns: the list, its update, the element, and an index past it. */
  record ElementCase(
      List<Product> source,
      List<Product> updated,
      List<Product> element,
      List<Product> outOfBounds) {}

  /** The edge cases' four results, in the order the page shows them. */
  record EdgeCases(
      List<Integer> result1, List<Integer> result2, List<Integer> result3, List<Integer> result4) {}

  static Slice taking() {
    // ANCHOR: taking
    // Create a traversal for first 3 products
    Traversal<List<Product>, Product> first3 = ListTraversals.taking(3);

    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", new BigDecimal("10.00"), 100),
            new Product("SKU002", "Gadget", new BigDecimal("25.00"), 50),
            new Product("SKU003", "Gizmo", new BigDecimal("15.00"), 75),
            new Product("SKU004", "Doohickey", new BigDecimal("30.00"), 25),
            new Product("SKU005", "Thingamajig", new BigDecimal("20.00"), 60));

    // Apply 10% discount to ONLY first 3 products
    List<Product> result = Traversals.modify(first3, p -> p.applyDiscount(10), products);
    // First 3 discounted; last 2 preserved unchanged

    // Extract ONLY first 3 products
    List<Product> firstThree = Traversals.getAll(first3, products);
    // Widget, Gadget and Gizmo
    // ANCHOR_END: taking
    return new Slice(products, result, firstThree);
  }

  static Slice dropping(List<Product> products) {
    // ANCHOR: dropping
    // Skip first 2, focus on the rest
    Traversal<List<Product>, Product> afterFirst2 = ListTraversals.dropping(2);

    List<Product> result = Traversals.modify(afterFirst2, p -> p.applyDiscount(15), products);
    // First 2 unchanged; last 3 get 15% discount

    List<Product> skipped = Traversals.getAll(afterFirst2, products);
    // Gizmo, Doohickey and Thingamajig
    // ANCHOR_END: dropping
    return new Slice(products, result, skipped);
  }

  static Slice takingLast(List<Product> products) {
    // ANCHOR: taking_last
    // Focus on last 2 products
    Traversal<List<Product>, Product> last2 = ListTraversals.takingLast(2);

    List<Product> result = Traversals.modify(last2, p -> p.applyDiscount(20), products);
    // First 3 unchanged; last 2 get 20% discount

    List<Product> lastTwo = Traversals.getAll(last2, products);
    // Doohickey and Thingamajig
    // ANCHOR_END: taking_last
    return new Slice(products, result, lastTwo);
  }

  static Slice droppingLast(List<Product> products) {
    // ANCHOR: dropping_last
    // Focus on all except last 2
    Traversal<List<Product>, Product> exceptLast2 = ListTraversals.droppingLast(2);

    List<Product> result = Traversals.modify(exceptLast2, p -> p.applyDiscount(5), products);
    // First 3 get 5% discount; last 2 unchanged

    List<Product> allButLastTwo = Traversals.getAll(exceptLast2, products);
    // Widget, Gadget and Gizmo
    // ANCHOR_END: dropping_last
    return new Slice(products, result, allButLastTwo);
  }

  static Slice slicing(List<Product> products) {
    // ANCHOR: slicing
    // Focus on indices 1, 2, 3 (0-indexed, exclusive end)
    Traversal<List<Product>, Product> slice = ListTraversals.slicing(1, 4);

    List<Product> result = Traversals.modify(slice, p -> p.applyDiscount(12), products);
    // Index 0 unchanged; indices 1-3 discounted; index 4 unchanged

    List<Product> sliced = Traversals.getAll(slice, products);
    // Gadget, Gizmo and Doohickey
    // ANCHOR_END: slicing
    return new Slice(products, result, sliced);
  }

  static Slice takingWhile() {
    // ANCHOR: taking_while
    // Focus on products whilst price < 20
    Traversal<List<Product>, Product> affordablePrefix =
        ListTraversals.takingWhile(p -> p.price().compareTo(new BigDecimal("20")) < 0);

    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", new BigDecimal("10.00"), 100),
            new Product("SKU002", "Gadget", new BigDecimal("15.00"), 50),
            new Product("SKU003", "Gizmo", new BigDecimal("25.00"), 75), // Stops here
            new Product("SKU004", "Thing", new BigDecimal("12.00"), 25)); // Not included

    // Apply discount only to initial affordable items
    List<Product> result = Traversals.modify(affordablePrefix, p -> p.applyDiscount(10), products);
    // Widget and Gadget discounted; Gizmo and Thing unchanged

    // Extract the affordable prefix
    List<Product> affordable = Traversals.getAll(affordablePrefix, products);
    // Widget and Gadget: it stops at Gizmo, the first expensive item, so Thing is left out
    // ANCHOR_END: taking_while
    return new Slice(products, result, affordable);
  }

  static Slice droppingWhile() {
    // ANCHOR: dropping_while
    // Skip low-stock products, focus on well-stocked ones
    Traversal<List<Product>, Product> wellStocked =
        ListTraversals.droppingWhile(p -> p.stock() < 50);

    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", new BigDecimal("10.00"), 20),
            new Product("SKU002", "Gadget", new BigDecimal("25.00"), 30),
            new Product("SKU003", "Gizmo", new BigDecimal("15.00"), 75), // First to pass
            new Product("SKU004", "Thing", new BigDecimal("12.00"), 25)); // Included despite < 50

    // Restock only well-stocked items (and everything after)
    List<Product> restocked =
        Traversals.modify(
            wellStocked, p -> new Product(p.sku(), p.name(), p.price(), p.stock() + 50), products);
    // Widget and Gadget unchanged; Gizmo and Thing restocked

    List<Product> focused = Traversals.getAll(wellStocked, products);
    // Gizmo and Thing
    // ANCHOR_END: dropping_while
    return new Slice(products, restocked, focused);
  }

  static List<String> runtimeLogs() {
    // ANCHOR: dropping_while_logs
    // Skip the leading configuration block in a log
    Traversal<List<String>, String> runtimeLogs =
        ListTraversals.droppingWhile(line -> line.startsWith("[CONFIG]"));

    // Apply to log data
    List<String> logs =
        List.of(
            "[CONFIG] Database URL",
            "[CONFIG] Port",
            "INFO: System started",
            "ERROR: Connection failed");
    List<String> result = Traversals.modify(runtimeLogs, String::toUpperCase, logs);
    // [[CONFIG] Database URL, [CONFIG] Port, INFO: SYSTEM STARTED, ERROR: CONNECTION FAILED]
    // Note: a [CONFIG] line appearing AFTER runtime lines would be modified too;
    // droppingWhile only skips the leading prefix
    // ANCHOR_END: dropping_while_logs
    return result;
  }

  static ElementCase element() {
    // ANCHOR: element
    // Focus on element at index 2
    Traversal<List<Product>, Product> thirdProduct = ListTraversals.element(2);

    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", new BigDecimal("10.00"), 100),
            new Product("SKU002", "Gadget", new BigDecimal("25.00"), 50),
            new Product("SKU003", "Gizmo", new BigDecimal("15.00"), 75));

    // Modify only the third product
    List<Product> updated = Traversals.modify(thirdProduct, p -> p.applyDiscount(20), products);
    // Only Gizmo discounted

    // Extract the element (if present)
    List<Product> element = Traversals.getAll(thirdProduct, products);
    // Gizmo alone

    // Out of bounds: gracefully returns empty
    List<Product> outOfBounds = Traversals.getAll(ListTraversals.element(10), products);
    // an empty list, and no exception
    // ANCHOR_END: element
    return new ElementCase(products, updated, element, outOfBounds);
  }

  static EdgeCases edgeCases() {
    // ANCHOR: edge_cases
    // Examples of edge case handling
    List<Integer> numbers = List.of(1, 2, 3);

    // n > size: focuses on all elements
    List<Integer> result1 = Traversals.getAll(ListTraversals.taking(100), numbers);
    // [1, 2, 3]

    // Negative n with taking: treated as 0, so no focus
    List<Integer> result2 = Traversals.getAll(ListTraversals.taking(-5), numbers);
    // []
    // (dropping(-5) is also treated as dropping(0), which focuses on EVERY element)

    // Inverted range: no focus
    List<Integer> result3 = Traversals.getAll(ListTraversals.slicing(3, 1), numbers);
    // []

    // Empty list: safe operation
    List<Integer> result4 = Traversals.modify(ListTraversals.taking(3), x -> x * 2, List.of());
    // []
    // ANCHOR_END: edge_cases
    return new EdgeCases(result1, result2, result3, result4);
  }
}

record Product(String sku, String name, BigDecimal price, int stock) {
  Product applyDiscount(int percent) {
    BigDecimal factor = BigDecimal.valueOf(100 - percent, 2); // 10 percent off is 0.90
    return new Product(
        sku, name, price.multiply(factor).setScale(2, RoundingMode.HALF_EVEN), stock);
  }
}
