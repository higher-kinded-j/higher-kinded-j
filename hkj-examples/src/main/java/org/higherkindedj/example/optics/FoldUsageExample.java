// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

// ANCHOR: complete_example

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Traversals;

/**
 * Comprehensive example demonstrating Fold optics for read-only querying and data extraction.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Basic query operations: getAll, preview, find, exists, all, isEmpty, length
 *   <li>Composing folds for deep queries across nested structures
 *   <li>Monoid-based aggregation for calculating sums, checking conditions, etc.
 *   <li>Real-world analytics on e-commerce purchase data
 * </ul>
 *
 * <p>Fold is a read-only optic designed specifically for querying without modification, making code
 * intent clear and preventing accidental mutations.
 */
public class FoldUsageExample {

  @GenerateLenses
  @GenerateFolds
  public record ProductItem(String name, BigDecimal price, String category, boolean inStock) {}

  @GenerateLenses
  @GenerateFolds
  public record Purchase(String purchaseId, List<ProductItem> items, String customerName) {}

  @GenerateLenses
  @GenerateFolds
  public record PurchaseHistory(List<Purchase> purchases) {}

  public static void main(String[] args) {
    // Create sample data
    var purchase1 =
        new Purchase(
            "ORD-001",
            List.of(
                new ProductItem("Laptop", new BigDecimal("999.99"), "Electronics", true),
                new ProductItem("Mouse", new BigDecimal("25.00"), "Electronics", true),
                new ProductItem("Desk", new BigDecimal("350.00"), "Furniture", false)),
            "Alice");

    var purchase2 =
        new Purchase(
            "ORD-002",
            List.of(
                new ProductItem("Keyboard", new BigDecimal("75.00"), "Electronics", true),
                new ProductItem("Monitor", new BigDecimal("450.00"), "Electronics", true),
                new ProductItem("Chair", new BigDecimal("200.00"), "Furniture", true)),
            "Bob");

    var history = new PurchaseHistory(List.of(purchase1, purchase2));

    System.out.println("=== FOLD USAGE EXAMPLE ===\n");

    // --- SCENARIO 1: Basic Query Operations ---
    System.out.println("--- Scenario 1: Basic Query Operations ---");
    Fold<Purchase, ProductItem> itemsFold = PurchaseFolds.items();

    List<ProductItem> allItems = itemsFold.getAll(purchase1);
    System.out.println("All items: " + allItems.size() + " products");

    Optional<ProductItem> firstItem = itemsFold.preview(purchase1);
    System.out.println("First item: " + firstItem.map(ProductItem::name).orElse("none"));

    int count = itemsFold.length(purchase1);
    System.out.println("Item count: " + count);

    boolean isEmpty = itemsFold.isEmpty(purchase1);
    System.out.println("Is empty: " + isEmpty + "\n");

    // --- SCENARIO 2: Conditional Queries ---
    System.out.println("--- Scenario 2: Conditional Queries ---");

    boolean hasOutOfStock = itemsFold.exists(p -> !p.inStock(), purchase1);
    System.out.println("Has out of stock items: " + hasOutOfStock);

    boolean allInStock = itemsFold.all(ProductItem::inStock, purchase1);
    System.out.println("All items in stock: " + allInStock);

    Optional<ProductItem> expensiveItem =
        itemsFold.find(p -> p.price().compareTo(new BigDecimal("500")) > 0, purchase1);
    System.out.println(
        "First expensive item: " + expensiveItem.map(ProductItem::name).orElse("none") + "\n");

    // --- SCENARIO 3: Composition ---
    System.out.println("--- Scenario 3: Composed Folds ---");

    Fold<PurchaseHistory, ProductItem> allProducts =
        PurchaseHistoryFolds.purchases().andThen(PurchaseFolds.items());

    List<ProductItem> allProductsFromHistory = allProducts.getAll(history);
    System.out.println("Total products across all purchases: " + allProductsFromHistory.size());

    Fold<PurchaseHistory, String> allCategories =
        allProducts.andThen(ProductItemLenses.category().asFold());

    Set<String> uniqueCategories = new TreeSet<>(allCategories.getAll(history));
    System.out.println("Unique categories: " + uniqueCategories + "\n");

    // --- SCENARIO 4: Monoid Aggregation ---
    System.out.println("--- Scenario 4: Monoid-Based Aggregation ---");

    // Monoids has no BigDecimal sum, so money sums with a monoid of its own
    Monoid<BigDecimal> sumMonoid =
        new Monoid<>() {
          @Override
          public BigDecimal empty() {
            return BigDecimal.ZERO;
          }

          @Override
          public BigDecimal combine(BigDecimal a, BigDecimal b) {
            return a.add(b);
          }
        };

    BigDecimal purchaseTotal = itemsFold.foldMap(sumMonoid, ProductItem::price, purchase1);
    System.out.println("Purchase 1 total: £" + purchaseTotal);

    BigDecimal historyTotal = allProducts.foldMap(sumMonoid, ProductItem::price, history);
    System.out.println("All purchases total: £" + historyTotal);

    // Standard monoids from the Monoids utility class: Boolean AND for checking conditions
    Monoid<Boolean> andMonoid = Monoids.booleanAnd();

    boolean allAffordable =
        itemsFold.foldMap(
            andMonoid, p -> p.price().compareTo(new BigDecimal("1000")) < 0, purchase1);
    System.out.println("All items under £1000: " + allAffordable);

    // Boolean OR monoid for checking any condition
    Monoid<Boolean> orMonoid = Monoids.booleanOr();

    boolean hasElectronics =
        allProducts.foldMap(orMonoid, p -> "Electronics".equals(p.category()), history);
    System.out.println("Has electronics: " + hasElectronics + "\n");

    // --- SCENARIO 5: Analytics ---
    System.out.println("--- Scenario 5: Real-World Analytics ---");

    // Most expensive product
    Optional<ProductItem> mostExpensive =
        allProducts.getAll(history).stream().max(Comparator.comparing(ProductItem::price));
    System.out.println(
        "Most expensive product: "
            + mostExpensive.map(p -> p.name() + " (£" + p.price() + ")").orElse("none"));

    // Average price
    List<ProductItem> allProds = allProducts.getAll(history);
    BigDecimal avgPrice =
        allProds.isEmpty()
            ? BigDecimal.ZERO
            : historyTotal.divide(BigDecimal.valueOf(allProds.size()), 2, RoundingMode.HALF_EVEN);
    System.out.println("Average product price: £" + avgPrice);

    // Count by category
    long electronicsCount =
        allProducts.getAll(history).stream()
            .filter(p -> "Electronics".equals(p.category()))
            .count();
    System.out.println("Electronics count: " + electronicsCount + "\n");

    // --- SCENARIO 6: Traversal-Derived Folds ---
    System.out.println("--- Scenario 6: Traversal-Derived Folds via asFold() ---");

    // Build a Traversal for all items across all purchases, then convert to Fold
    Lens<PurchaseHistory, List<Purchase>> purchasesLens =
        Lens.of(PurchaseHistory::purchases, (h, os) -> new PurchaseHistory(os));
    Lens<Purchase, List<ProductItem>> itemsLens =
        Lens.of(Purchase::items, (o, is) -> new Purchase(o.purchaseId(), is, o.customerName()));

    Traversal<PurchaseHistory, ProductItem> allItemsTraversal =
        purchasesLens
            .andThen(Traversals.<Purchase>forList())
            .andThen(itemsLens)
            .andThen(Traversals.forList());

    // Convert to Fold — now we have the same query power as generated folds
    Fold<PurchaseHistory, ProductItem> traversalDerivedFold = allItemsTraversal.asFold();

    // These produce the same results as using the generated folds
    List<ProductItem> allItems2 = traversalDerivedFold.getAll(history);
    System.out.println("Products via traversal-derived fold: " + allItems2.size());

    BigDecimal total = traversalDerivedFold.foldMap(sumMonoid, ProductItem::price, history);
    System.out.println("Total via traversal-derived fold: £" + total);

    // Filter the traversal, then convert to Fold for targeted queries
    Fold<PurchaseHistory, ProductItem> electronicsFold =
        allItemsTraversal.filtered(p -> "Electronics".equals(p.category())).asFold();

    int electronicsCount2 = electronicsFold.length(history);
    BigDecimal electronicsTotal = electronicsFold.foldMap(sumMonoid, ProductItem::price, history);
    System.out.println("Electronics count: " + electronicsCount2);
    System.out.println("Electronics total: £" + electronicsTotal);

    System.out.println("\n=== END OF EXAMPLE ===");
  }
}
// ANCHOR_END: complete_example
