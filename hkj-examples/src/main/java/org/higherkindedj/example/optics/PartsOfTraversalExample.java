// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating the partsOf combinator for list-level manipulation of traversal
 * focuses.
 *
 * <p>This example showcases how to convert a Traversal into a Lens on a List, enabling powerful
 * operations like sorting, reversing, and deduplicating focused elements whilst maintaining the
 * overall structure integrity.
 */
public class PartsOfTraversalExample {

  @GenerateLenses
  public record Product(String name, double price, int stockLevel) {}

  @GenerateLenses
  @GenerateTraversals
  public record Category(String name, List<Product> products) {}

  @GenerateLenses
  @GenerateTraversals
  public record Catalogue(String name, List<Category> categories) {}

  @GenerateLenses
  public record Task(String description, int priority, boolean completed) {}

  @GenerateLenses
  @GenerateTraversals
  public record Project(String name, List<Task> tasks) {}

  public static void main(String[] args) {
    System.out.println("=== PARTSOF TRAVERSAL EXAMPLE ===");
    System.out.println("Demonstrating list-level operations on traversal focuses\n");

    basicPartsOfUsage();
    sortingWithPartsOf();
    reversingWithPartsOf();
    distinctWithPartsOf();
    customComparatorSorting();
    combiningWithFilteredTraversals();
    realWorldCatalogueNormalisation();

    System.out.println("=== END OF PARTSOF EXAMPLES ===");
  }

  // --- SCENARIO 1: Basic partsOf Usage ---
  private static void basicPartsOfUsage() {
    System.out.println("--- Scenario 1: Basic partsOf Usage ---");

    List<Product> products =
        List.of(
            new Product("Widget", 25.99, 100),
            new Product("Gadget", 49.99, 50),
            new Product("Gizmo", 15.50, 200));

    // Create a traversal for all product prices
    Traversal<List<Product>, Double> priceTraversal =
        Traversals.<Product>forList().andThen(ProductLenses.price().asTraversal());

    // Convert to a lens on the list of prices
    Lens<List<Product>, List<Double>> pricesLens = Traversals.partsOf(priceTraversal);

    // Get all prices as a list
    List<Double> allPrices = pricesLens.get(products);
    System.out.println("All prices: " + allPrices);

    // Manipulate the list - apply a 10% discount to all prices
    List<Double> discountedPrices = new ArrayList<>();
    for (Double price : allPrices) {
      discountedPrices.add(price * 0.90);
    }

    // Set the modified prices back
    List<Product> updatedProducts = pricesLens.set(discountedPrices, products);

    System.out.println("\nOriginal products:");
    products.forEach(p -> System.out.println("  " + p.name() + ": £" + p.price()));

    System.out.println("\nAfter 10% discount:");
    updatedProducts.forEach(p -> System.out.println("  " + p.name() + ": £" + p.price()));
    System.out.println();
  }

  // --- SCENARIO 2: Sorting Focused Elements ---
  private static void sortingWithPartsOf() {
    System.out.println("--- Scenario 2: Sorting Product Prices ---");

    List<Product> products =
        List.of(
            new Product("Expensive Widget", 199.99, 10),
            new Product("Budget Gadget", 9.99, 500),
            new Product("Mid-range Gizmo", 59.99, 100),
            new Product("Premium Device", 299.99, 25));

    // Create a traversal for product prices
    Traversal<List<Product>, Double> priceTraversal =
        Traversals.<Product>forList().andThen(ProductLenses.price().asTraversal());

    // Sort prices using the convenience method
    List<Product> sortedByPrice = Traversals.sorted(priceTraversal, products);

    System.out.println("Original order (name -> price):");
    products.forEach(p -> System.out.println("  " + p.name() + " -> £" + p.price()));

    System.out.println("\nAfter sorting prices (prices redistributed to positions):");
    sortedByPrice.forEach(p -> System.out.println("  " + p.name() + " -> £" + p.price()));

    System.out.println("\nNote: The product names stay in their original positions,");
    System.out.println("but the prices are now in ascending order across all products.");
    System.out.println();
  }

  // --- SCENARIO 3: Reversing Task Priorities ---
  private static void reversingWithPartsOf() {
    System.out.println("--- Scenario 3: Reversing Task Priorities ---");

    Project project =
        new Project(
            "Website Redesign",
            List.of(
                new Task("Design mockups", 1, false),
                new Task("Implement frontend", 2, false),
                new Task("Backend API", 3, false),
                new Task("Testing", 4, false),
                new Task("Deployment", 5, false)));

    // Create a traversal for task priorities
    Traversal<Project, Integer> priorityTraversal =
        ProjectTraversals.tasks().andThen(TaskLenses.priority().asTraversal());

    // Reverse the priorities
    Project reversedProject = Traversals.reversed(priorityTraversal, project);

    System.out.println("Original task priorities:");
    project
        .tasks()
        .forEach(t -> System.out.println("  " + t.description() + " -> P" + t.priority()));

    System.out.println("\nAfter reversing priorities:");
    reversedProject
        .tasks()
        .forEach(t -> System.out.println("  " + t.description() + " -> P" + t.priority()));

    System.out.println("\nUse case: Quickly invert priority scheme without reordering tasks.");
    System.out.println();
  }

  // --- SCENARIO 4: Removing Duplicate Tags ---
  private static void distinctWithPartsOf() {
    System.out.println("--- Scenario 4: Removing Duplicate Product Names (Deduplication) ---");

    // Scenario: Products with duplicate names that need normalisation
    List<Product> products =
        List.of(
            new Product("Widget", 25.99, 100),
            new Product("Gadget", 49.99, 50),
            new Product("Widget", 30.00, 75), // Duplicate name
            new Product("Gizmo", 15.50, 200),
            new Product("Gadget", 55.00, 30)); // Duplicate name

    // Create a traversal for product names
    Traversal<List<Product>, String> nameTraversal =
        Traversals.<Product>forList().andThen(ProductLenses.name().asTraversal());

    // Remove duplicate names
    List<Product> deduplicatedProducts = Traversals.distinct(nameTraversal, products);

    System.out.println("Original product names:");
    products.forEach(p -> System.out.println("  " + p.name() + " (£" + p.price() + ")"));

    System.out.println("\nAfter deduplication:");
    deduplicatedProducts.forEach(
        p -> System.out.println("  " + p.name() + " (£" + p.price() + ")"));

    System.out.println("\nNote: Unique names (Widget, Gadget, Gizmo) are distributed");
    System.out.println("to the first positions. Remaining positions keep their original values.");
    System.out.println("This is useful for normalising data where structure must be preserved.");
    System.out.println();
  }

  // --- SCENARIO 5: Custom Comparator Sorting ---
  private static void customComparatorSorting() {
    System.out.println("--- Scenario 5: Case-Insensitive Name Sorting ---");

    List<Product> products =
        List.of(
            new Product("zebra Widget", 25.99, 100),
            new Product("Alpha Gadget", 49.99, 50),
            new Product("BETA Device", 35.00, 75),
            new Product("gamma Tool", 15.50, 200));

    // Create a traversal for product names
    Traversal<List<Product>, String> nameTraversal =
        Traversals.<Product>forList().andThen(ProductLenses.name().asTraversal());

    // Sort names case-insensitively
    List<Product> sortedProducts =
        Traversals.sorted(nameTraversal, String.CASE_INSENSITIVE_ORDER, products);

    System.out.println("Original names:");
    products.forEach(p -> System.out.println("  " + p.name()));

    System.out.println("\nAfter case-insensitive sorting:");
    sortedProducts.forEach(p -> System.out.println("  " + p.name()));

    // Sort by name length
    List<Product> sortedByLength =
        Traversals.sorted(nameTraversal, Comparator.comparingInt(String::length), products);

    System.out.println("\nAfter sorting by name length:");
    sortedByLength.forEach(
        p -> System.out.println("  " + p.name() + " (length: " + p.name().length() + ")"));
    System.out.println();
  }

  // --- SCENARIO 6: Combining with Filtered Traversals ---
  private static void combiningWithFilteredTraversals() {
    System.out.println("--- Scenario 6: Sorting Only In-Stock Products ---");

    List<Product> products =
        List.of(
            new Product("Widget A", 99.99, 0), // Out of stock
            new Product("Widget B", 25.99, 100),
            new Product("Widget C", 149.99, 0), // Out of stock
            new Product("Widget D", 49.99, 50),
            new Product("Widget E", 75.00, 200));

    // Create a traversal for prices of in-stock products only
    Traversal<List<Product>, Double> inStockPrices =
        Traversals.<Product>forList()
            .filtered(p -> p.stockLevel() > 0)
            .andThen(ProductLenses.price().asTraversal());

    // Sort only the in-stock prices
    List<Product> result = Traversals.sorted(inStockPrices, products);

    System.out.println("Original products:");
    products.forEach(
        p ->
            System.out.println(
                "  " + p.name() + " -> £" + p.price() + " (stock: " + p.stockLevel() + ")"));

    System.out.println("\nAfter sorting only in-stock product prices:");
    result.forEach(
        p ->
            System.out.println(
                "  " + p.name() + " -> £" + p.price() + " (stock: " + p.stockLevel() + ")"));

    System.out.println("\nNote: Out-of-stock products (A and C) remain unchanged,");
    System.out.println("whilst in-stock product prices are sorted amongst themselves.");
    System.out.println();
  }

  // --- SCENARIO 7: Real-World Catalogue Normalisation ---
  private static void realWorldCatalogueNormalisation() {
    System.out.println("--- Scenario 7: E-commerce Catalogue Normalisation ---");

    Catalogue catalogue =
        new Catalogue(
            "Tech Store",
            List.of(
                new Category(
                    "Electronics",
                    List.of(
                        new Product("Laptop", 999.99, 20),
                        new Product("Tablet", 499.99, 50),
                        new Product("Phone", 799.99, 100))),
                new Category(
                    "Accessories",
                    List.of(
                        new Product("Charger", 29.99, 500),
                        new Product("Case", 49.99, 300),
                        new Product("Cable", 19.99, 1000)))));

    // Create a deep traversal to all product prices in the catalogue
    Traversal<Catalogue, Double> allPrices =
        CatalogueTraversals.categories()
            .andThen(CategoryTraversals.products())
            .andThen(ProductLenses.price().asTraversal());

    System.out.println("Original catalogue prices:");
    printCataloguePrices(catalogue);

    // Sort all prices across the entire catalogue
    Catalogue sortedCatalogue = Traversals.sorted(allPrices, catalogue);

    System.out.println("After sorting all prices across categories:");
    printCataloguePrices(sortedCatalogue);

    // Reverse the sorted prices
    Catalogue reversedCatalogue = Traversals.reversed(allPrices, sortedCatalogue);

    System.out.println("After reversing (highest to lowest):");
    printCataloguePrices(reversedCatalogue);

    // Demonstrate size mismatch handling
    System.out.println("--- Size Mismatch Demonstration ---");
    Lens<Catalogue, List<Double>> pricesLens = Traversals.partsOf(allPrices);

    // Set fewer values than positions exist
    List<Double> partialPrices = List.of(100.00, 200.00, 300.00); // Only 3 values for 6 positions
    Catalogue partialUpdate = pricesLens.set(partialPrices, catalogue);

    System.out.println("Setting only 3 prices for 6 products:");
    printCataloguePrices(partialUpdate);
    System.out.println("Note: First 3 positions updated, remaining 3 keep original values.\n");
  }

  private static void printCataloguePrices(Catalogue catalogue) {
    catalogue
        .categories()
        .forEach(
            category -> {
              System.out.println("  " + category.name() + ":");
              category
                  .products()
                  .forEach(
                      product ->
                          System.out.println("    " + product.name() + ": £" + product.price()));
            });
    System.out.println();
  }
}
