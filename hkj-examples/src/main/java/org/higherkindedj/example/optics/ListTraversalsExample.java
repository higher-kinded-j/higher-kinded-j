// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import module java.base;
import module org.higherkindedj.core;

/**
 * A comprehensive runnable example demonstrating limiting traversals for positional list focusing.
 *
 * <p>This example showcases five factory methods from {@code ListTraversals}:
 *
 * <ul>
 *   <li><b>taking(n)</b>: Focus on first n elements
 *   <li><b>dropping(n)</b>: Skip first n, focus on rest
 *   <li><b>takingLast(n)</b>: Focus on last n elements
 *   <li><b>droppingLast(n)</b>: Focus on all except last n
 *   <li><b>slicing(from, to)</b>: Focus on index range [from, to)
 * </ul>
 *
 * <p>Key semantics demonstrated:
 *
 * <ul>
 *   <li>During <b>modification</b>: non-focused elements are <i>preserved unchanged</i> in the
 *       structure
 *   <li>During <b>queries</b> (like getAll): non-focused elements are <i>excluded</i> from results
 *   <li>Edge cases are handled gracefully (negative n, n &gt; size, empty lists)
 * </ul>
 */
public class ListTraversalsExample {

  // Domain models for an e-commerce platform
  public record Product(String sku, String name, double price, int stock) {
    Product applyDiscount(double percentage) {
      return new Product(sku, name, price * (1 - percentage), stock);
    }

    Product restock(int additional) {
      return new Product(sku, name, price, stock + additional);
    }
  }

  public record Category(String name, List<Product> products) {}

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("=== Limiting Traversals Examples ===\n");

    demonstrateTaking();
    demonstrateDropping();
    demonstrateTakingLast();
    demonstrateDroppingLast();
    demonstrateSlicing();
    demonstrateEdgeCases();
    demonstrateComposition();
    demonstrateChainedLimiting();
    demonstrateWithFilteredTraversals();
    demonstrateStructuralPreservation();
  }

  private static void demonstrateTaking() {
    System.out.println("--- 1. taking(n) - Focus on First N Elements ---");

    Traversal<List<Integer>, Integer> first3 = ListTraversals.taking(3);
    List<Integer> numbers = List.of(10, 20, 30, 40, 50);

    System.out.println("Original: " + numbers);

    // Modify: Double first 3 elements
    List<Integer> modified = Traversals.modify(first3, x -> x * 2, numbers);
    System.out.println("After doubling first 3: " + modified);
    // [20, 40, 60, 40, 50] - last 2 unchanged

    // Query: Get first 3 elements
    List<Integer> gotten = Traversals.getAll(first3, numbers);
    System.out.println("getAll first 3: " + gotten);
    // [10, 20, 30]

    System.out.println();
  }

  private static void demonstrateDropping() {
    System.out.println("--- 2. dropping(n) - Skip First N Elements ---");

    Traversal<List<Integer>, Integer> drop2 = ListTraversals.dropping(2);
    List<Integer> numbers = List.of(10, 20, 30, 40, 50);

    System.out.println("Original: " + numbers);

    // Modify: Triple elements after first 2
    List<Integer> modified = Traversals.modify(drop2, x -> x * 3, numbers);
    System.out.println("After tripling after first 2: " + modified);
    // [10, 20, 90, 120, 150] - first 2 unchanged

    // Query: Get elements after first 2
    List<Integer> gotten = Traversals.getAll(drop2, numbers);
    System.out.println("getAll after dropping 2: " + gotten);
    // [30, 40, 50]

    System.out.println();
  }

  private static void demonstrateTakingLast() {
    System.out.println("--- 3. takingLast(n) - Focus on Last N Elements ---");

    Traversal<List<Integer>, Integer> last3 = ListTraversals.takingLast(3);
    List<Integer> numbers = List.of(10, 20, 30, 40, 50);

    System.out.println("Original: " + numbers);

    // Modify: Add 100 to last 3 elements
    List<Integer> modified = Traversals.modify(last3, x -> x + 100, numbers);
    System.out.println("After adding 100 to last 3: " + modified);
    // [10, 20, 130, 140, 150] - first 2 unchanged

    // Query: Get last 3 elements
    List<Integer> gotten = Traversals.getAll(last3, numbers);
    System.out.println("getAll last 3: " + gotten);
    // [30, 40, 50]

    System.out.println();
  }

  private static void demonstrateDroppingLast() {
    System.out.println("--- 4. droppingLast(n) - Focus on All Except Last N ---");

    Traversal<List<Integer>, Integer> dropLast2 = ListTraversals.droppingLast(2);
    List<Integer> numbers = List.of(10, 20, 30, 40, 50);

    System.out.println("Original: " + numbers);

    // Modify: Negate all except last 2
    List<Integer> modified = Traversals.modify(dropLast2, x -> -x, numbers);
    System.out.println("After negating all except last 2: " + modified);
    // [-10, -20, -30, 40, 50] - last 2 unchanged

    // Query: Get all except last 2
    List<Integer> gotten = Traversals.getAll(dropLast2, numbers);
    System.out.println("getAll except last 2: " + gotten);
    // [10, 20, 30]

    System.out.println();
  }

  private static void demonstrateSlicing() {
    System.out.println("--- 5. slicing(from, to) - Focus on Index Range ---");

    Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(1, 4);
    List<Integer> numbers = List.of(10, 20, 30, 40, 50);

    System.out.println("Original: " + numbers);
    System.out.println("Slicing indices [1, 4) means indices 1, 2, 3");

    // Modify: Square elements at indices 1, 2, 3
    List<Integer> modified = Traversals.modify(slice, x -> x * x, numbers);
    System.out.println("After squaring slice [1,4): " + modified);
    // [10, 400, 900, 1600, 50] - indices 0 and 4 unchanged

    // Query: Get elements at indices 1, 2, 3
    List<Integer> gotten = Traversals.getAll(slice, numbers);
    System.out.println("getAll slice [1,4): " + gotten);
    // [20, 30, 40]

    System.out.println();
  }

  private static void demonstrateEdgeCases() {
    System.out.println("--- 6. Edge Case Handling ---");

    List<Integer> numbers = List.of(1, 2, 3);
    System.out.println("Test list: " + numbers);

    // n > size: focuses on all available
    List<Integer> takeMore = Traversals.getAll(ListTraversals.taking(100), numbers);
    System.out.println("taking(100) on 3-element list: " + takeMore);
    // [1, 2, 3]

    // Negative n: identity (no focus)
    List<Integer> takeNegative = Traversals.getAll(ListTraversals.taking(-5), numbers);
    System.out.println("taking(-5): " + takeNegative);
    // []

    // n = 0: no focus
    List<Integer> takeZero = Traversals.getAll(ListTraversals.taking(0), numbers);
    System.out.println("taking(0): " + takeZero);
    // []

    // drop more than size: no focus
    List<Integer> dropMore = Traversals.getAll(ListTraversals.dropping(10), numbers);
    System.out.println("dropping(10): " + dropMore);
    // []

    // Inverted range: no focus
    List<Integer> invertedSlice = Traversals.getAll(ListTraversals.slicing(3, 1), numbers);
    System.out.println("slicing(3, 1) inverted range: " + invertedSlice);
    // []

    // Negative from in slice: clamps to 0
    List<Integer> negativeFrom = Traversals.getAll(ListTraversals.slicing(-5, 2), numbers);
    System.out.println("slicing(-5, 2) negative from: " + negativeFrom);
    // [1, 2]

    // Empty list: safe operation
    List<Integer> emptyResult = Traversals.modify(ListTraversals.taking(3), x -> x * 2, List.of());
    System.out.println("taking(3) on empty list: " + emptyResult);
    // []

    System.out.println();
  }

  private static void demonstrateComposition() {
    System.out.println("--- 7. Composing with Lenses ---");

    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", 10.0, 100),
            new Product("SKU002", "Gadget", 25.0, 50),
            new Product("SKU003", "Gizmo", 15.0, 75),
            new Product("SKU004", "Doohickey", 30.0, 25),
            new Product("SKU005", "Thingamajig", 20.0, 60));

    System.out.println("Products: " + products.stream().map(Product::name).toList());

    // Lens for product price
    Lens<Product, Double> priceLens =
        Lens.of(
            Product::price, (p, newPrice) -> new Product(p.sku(), p.name(), newPrice, p.stock()));

    // Compose: first 3 products → their prices
    Traversal<List<Product>, Double> first3Prices =
        ListTraversals.<Product>taking(3).andThen(priceLens.asTraversal());

    // Apply 10% discount to first 3 products' prices
    List<Product> discounted = Traversals.modify(first3Prices, price -> price * 0.9, products);

    System.out.println("After 10% discount on first 3 products:");
    for (Product p : discounted) {
      System.out.printf("  %s: £%.2f%n", p.name(), p.price());
    }
    // Widget: £9.00, Gadget: £22.50, Gizmo: £13.50, Doohickey: £30.00, Thingamajig: £20.00

    // Get prices of last 2 products
    Traversal<List<Product>, Double> last2Prices =
        ListTraversals.<Product>takingLast(2).andThen(priceLens.asTraversal());
    List<Double> lastPrices = Traversals.getAll(last2Prices, products);
    System.out.println("Prices of last 2 products: " + lastPrices);
    // [30.0, 20.0]

    System.out.println();
  }

  private static void demonstrateChainedLimiting() {
    System.out.println("--- 8. Sequential Limiting Operations ---");

    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    System.out.println("Original: " + numbers);

    // Apply different operations to different slices
    // First 3: double
    List<Integer> step1 = Traversals.modify(ListTraversals.taking(3), x -> x * 2, numbers);
    System.out.println("After doubling first 3: " + step1);
    // [2, 4, 6, 4, 5, 6, 7, 8, 9, 10]

    // Last 3: add 100
    List<Integer> step2 = Traversals.modify(ListTraversals.takingLast(3), x -> x + 100, step1);
    System.out.println("After adding 100 to last 3: " + step2);
    // [2, 4, 6, 4, 5, 6, 7, 108, 109, 110]

    // Middle slice [3, 7): negate
    List<Integer> step3 = Traversals.modify(ListTraversals.slicing(3, 7), x -> -x, step2);
    System.out.println("After negating slice [3,7): " + step3);
    // [2, 4, 6, -4, -5, -6, -7, 108, 109, 110]

    System.out.println();
  }

  private static void demonstrateWithFilteredTraversals() {
    System.out.println("--- 9. Combining Limiting with Filtered Traversals ---");

    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", 10.0, 20), // low stock
            new Product("SKU002", "Gadget", 25.0, 150),
            new Product("SKU003", "Gizmo", 15.0, 30), // low stock
            new Product("SKU004", "Doohickey", 30.0, 200),
            new Product("SKU005", "Thingamajig", 20.0, 10), // low stock
            new Product("SKU006", "Whatsit", 35.0, 5), // low stock
            new Product("SKU007", "Contraption", 40.0, 180));

    System.out.println(
        "Products with stock: "
            + products.stream().map(p -> p.name() + "(" + p.stock() + ")").toList());

    // First 5 products that are also low stock (< 50 units)
    Traversal<List<Product>, Product> first5LowStock =
        ListTraversals.<Product>taking(5).filtered(p -> p.stock() < 50);

    List<Product> lowStockInFirst5 = Traversals.getAll(first5LowStock, products);
    System.out.println(
        "Low stock products in first 5: " + lowStockInFirst5.stream().map(Product::name).toList());
    // [Widget, Gizmo, Thingamajig]

    // Restock only the low-stock items in first 5
    List<Product> restocked = Traversals.modify(first5LowStock, p -> p.restock(100), products);
    System.out.println("After restocking low-stock items in first 5:");
    for (Product p : restocked) {
      System.out.printf("  %s: %d units%n", p.name(), p.stock());
    }
    // Widget: 120, Gadget: 150, Gizmo: 130, Doohickey: 200, Thingamajig: 110, Whatsit: 5,
    // Contraption: 180
    // Note: Whatsit (6th product) not restocked even though low stock

    System.out.println();
  }

  private static void demonstrateStructuralPreservation() {
    System.out.println("--- 10. Structural Preservation Semantics ---");

    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", 10.0, 100),
            new Product("SKU002", "Gadget", 25.0, 50),
            new Product("SKU003", "Gizmo", 15.0, 75));

    System.out.println("Original products: " + products.stream().map(Product::name).toList());
    System.out.println("Original size: " + products.size());

    Traversal<List<Product>, Product> first2 = ListTraversals.taking(2);

    // MODIFY: Structure is preserved, only focused elements change
    List<Product> modified = Traversals.modify(first2, p -> p.applyDiscount(0.2), products);
    System.out.println("\nAfter 20% discount on first 2:");
    System.out.println("Modified size: " + modified.size() + " (same as original!)");
    System.out.println("Third product (unfocused) unchanged: " + modified.get(2).name());

    // QUERY: Only focused elements returned
    List<Product> gotten = Traversals.getAll(first2, products);
    System.out.println("\ngetAll first 2:");
    System.out.println("Result size: " + gotten.size() + " (only focused elements)");

    // Original unchanged (immutability)
    System.out.println(
        "\nOriginal products unchanged: " + products.stream().map(Product::name).toList());
    System.out.println(
        "Original first product price: £"
            + products.get(0).price()
            + " (not £"
            + modified.get(0).price()
            + ")");

    System.out.println();
    System.out.println("=== All Limiting Traversal Examples Complete ===");
  }
}
