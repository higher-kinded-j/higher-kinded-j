// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.ListTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * A real-world example demonstrating limiting traversals for REST API pagination.
 *
 * <p>This example simulates a product catalogue API where:
 *
 * <ul>
 *   <li>Products are paginated for efficient data transfer
 *   <li>Featured products (first page) receive special treatment
 *   <li>Clearance items (last few) have different pricing rules
 *   <li>Bulk updates are applied to specific page ranges
 * </ul>
 *
 * <p>Key patterns demonstrated:
 *
 * <ul>
 *   <li>Paginated data access using {@code slicing(from, to)}
 *   <li>Featured item highlighting using {@code taking(n)}
 *   <li>Clearance handling using {@code takingLast(n)} and {@code droppingLast(n)}
 *   <li>Composing limiting traversals with domain lenses
 * </ul>
 */
public class PaginationExample {

  // Domain models
  public record Product(
      String sku, String name, double price, int stock, boolean featured, String badge) {
    Product withBadge(String newBadge) {
      return new Product(sku, name, price, stock, featured, newBadge);
    }

    Product applyDiscount(double percentage) {
      return new Product(sku, name, price * (1 - percentage), stock, featured, badge);
    }

    Product markFeatured() {
      return new Product(sku, name, price, stock, true, badge);
    }
  }

  public record PageInfo(int pageNumber, int pageSize, int totalItems, int totalPages) {}

  public record PagedResponse(List<Product> items, PageInfo pageInfo) {}

  // Constants
  private static final int DEFAULT_PAGE_SIZE = 5;

  public static void main(String[] args) {
    List<Product> catalogue = createCatalogue();

    System.out.println("=== REST API Pagination with Limiting Traversals ===\n");
    System.out.println("Total products in catalogue: " + catalogue.size());
    System.out.println("Page size: " + DEFAULT_PAGE_SIZE);
    System.out.println();

    demonstratePagination(catalogue);
    demonstrateFeaturedProducts(catalogue);
    demonstrateClearanceSection(catalogue);
    demonstrateBulkPageUpdate(catalogue);
    demonstratePageRangeQuery(catalogue);
  }

  private static void demonstratePagination(List<Product> catalogue) {
    System.out.println("--- Scenario 1: Basic Pagination ---");

    int totalPages = (int) Math.ceil(catalogue.size() / (double) DEFAULT_PAGE_SIZE);
    System.out.println("Total pages: " + totalPages);
    System.out.println();

    for (int page = 0; page < totalPages; page++) {
      PagedResponse response = getPage(catalogue, page, DEFAULT_PAGE_SIZE);
      System.out.printf("Page %d of %d:%n", page + 1, response.pageInfo().totalPages());
      for (Product p : response.items()) {
        System.out.printf("  • %s - £%.2f (%d in stock)%n", p.name(), p.price(), p.stock());
      }
      System.out.println();
    }
  }

  private static void demonstrateFeaturedProducts(List<Product> catalogue) {
    System.out.println("--- Scenario 2: Featured Products (Hero Section) ---");

    // First 3 products are featured on the hero section
    Traversal<List<Product>, Product> heroProducts = ListTraversals.taking(3);

    // Mark them as featured and add "HOT" badge
    List<Product> withHeroSection =
        Traversals.modify(heroProducts, p -> p.markFeatured().withBadge("HOT"), catalogue);

    System.out.println("Hero section products:");
    Traversals.getAll(heroProducts, withHeroSection)
        .forEach(
            p ->
                System.out.printf(
                    "  ⭐ %s [%s] - Featured: %s%n", p.name(), p.badge(), p.featured()));

    // Apply special 15% discount to hero products
    Lens<Product, Double> priceLens =
        Lens.of(
            Product::price,
            (prod, newPrice) ->
                new Product(
                    prod.sku(),
                    prod.name(),
                    newPrice,
                    prod.stock(),
                    prod.featured(),
                    prod.badge()));

    Traversal<List<Product>, Double> heroPrices = heroProducts.andThen(priceLens.asTraversal());

    List<Product> discountedHero = Traversals.modify(heroPrices, price -> price * 0.85, catalogue);

    System.out.println("\nAfter 15% hero discount:");
    for (int i = 0; i < 5; i++) {
      Product original = catalogue.get(i);
      Product discounted = discountedHero.get(i);
      String marker = i < 3 ? "★" : " ";
      System.out.printf(
          "  %s %s: £%.2f → £%.2f%n",
          marker, original.name(), original.price(), discounted.price());
    }
    System.out.println();
  }

  private static void demonstrateClearanceSection(List<Product> catalogue) {
    System.out.println("--- Scenario 3: Clearance Section ---");

    // Last 4 products are clearance items
    Traversal<List<Product>, Product> clearanceItems = ListTraversals.takingLast(4);

    System.out.println("Clearance items (last 4):");
    List<Product> clearance = Traversals.getAll(clearanceItems, catalogue);
    clearance.forEach(p -> System.out.printf("  🏷️ %s - £%.2f%n", p.name(), p.price()));

    // Apply 30% clearance discount
    List<Product> withClearance =
        Traversals.modify(
            clearanceItems, p -> p.applyDiscount(0.3).withBadge("CLEARANCE"), catalogue);

    System.out.println("\nAfter 30% clearance discount:");
    Traversals.getAll(clearanceItems, withClearance)
        .forEach(p -> System.out.printf("  🏷️ %s [%s] - £%.2f%n", p.name(), p.badge(), p.price()));

    // Regular items (all except clearance)
    Traversal<List<Product>, Product> regularItems = ListTraversals.droppingLast(4);
    List<Product> regular = Traversals.getAll(regularItems, catalogue);
    System.out.println("\nRegular items (excluding clearance): " + regular.size() + " products");

    System.out.println();
  }

  private static void demonstrateBulkPageUpdate(List<Product> catalogue) {
    System.out.println("--- Scenario 4: Bulk Update Specific Pages ---");

    // Apply different badges to different pages
    System.out.println("Applying seasonal badges by page:");

    // Page 1 (indices 0-4): "SPRING"
    Traversal<List<Product>, Product> page1 = ListTraversals.slicing(0, 5);
    List<Product> step1 = Traversals.modify(page1, p -> p.withBadge("SPRING"), catalogue);
    System.out.println("  Page 1: SPRING badge");

    // Page 2 (indices 5-9): "SUMMER"
    Traversal<List<Product>, Product> page2 = ListTraversals.slicing(5, 10);
    List<Product> step2 = Traversals.modify(page2, p -> p.withBadge("SUMMER"), step1);
    System.out.println("  Page 2: SUMMER badge");

    // Page 3 (indices 10-14): "AUTUMN"
    Traversal<List<Product>, Product> page3 = ListTraversals.slicing(10, 15);
    List<Product> step3 = Traversals.modify(page3, p -> p.withBadge("AUTUMN"), step2);
    System.out.println("  Page 3: AUTUMN badge");

    // Remaining (indices 15+): "WINTER"
    Traversal<List<Product>, Product> remaining = ListTraversals.dropping(15);
    List<Product> finalCatalogue = Traversals.modify(remaining, p -> p.withBadge("WINTER"), step3);
    System.out.println("  Remaining: WINTER badge");

    System.out.println("\nSample products after seasonal tagging:");
    System.out.printf(
        "  Product 1 (Page 1): %s [%s]%n",
        finalCatalogue.get(0).name(), finalCatalogue.get(0).badge());
    System.out.printf(
        "  Product 6 (Page 2): %s [%s]%n",
        finalCatalogue.get(5).name(), finalCatalogue.get(5).badge());
    System.out.printf(
        "  Product 11 (Page 3): %s [%s]%n",
        finalCatalogue.get(10).name(), finalCatalogue.get(10).badge());
    System.out.printf(
        "  Product 16 (Page 4): %s [%s]%n",
        finalCatalogue.get(15).name(), finalCatalogue.get(15).badge());

    System.out.println();
  }

  private static void demonstratePageRangeQuery(List<Product> catalogue) {
    System.out.println("--- Scenario 5: Query Specific Page Ranges ---");

    // Get products from pages 2 and 3 (indices 5-14)
    Traversal<List<Product>, Product> pages2And3 = ListTraversals.slicing(5, 15);
    List<Product> middlePages = Traversals.getAll(pages2And3, catalogue);

    System.out.println("Products from pages 2-3 (for bulk shipment):");
    System.out.println("  Count: " + middlePages.size() + " products");

    double totalValue = middlePages.stream().mapToDouble(p -> p.price() * p.stock()).sum();
    System.out.printf("  Total inventory value: £%.2f%n", totalValue);

    int totalStock = middlePages.stream().mapToInt(Product::stock).sum();
    System.out.println("  Total units: " + totalStock);

    // Calculate average price for middle pages
    double avgPrice = middlePages.stream().mapToDouble(Product::price).average().orElse(0.0);
    System.out.printf("  Average price: £%.2f%n", avgPrice);

    System.out.println();
    System.out.println("=== Pagination Examples Complete ===");
  }

  // Helper methods
  private static PagedResponse getPage(List<Product> catalogue, int pageNumber, int pageSize) {
    Traversal<List<Product>, Product> pageTraversal =
        ListTraversals.slicing(pageNumber * pageSize, (pageNumber + 1) * pageSize);

    List<Product> items = Traversals.getAll(pageTraversal, catalogue);

    int totalPages = (int) Math.ceil(catalogue.size() / (double) pageSize);
    PageInfo pageInfo = new PageInfo(pageNumber, pageSize, catalogue.size(), totalPages);

    return new PagedResponse(items, pageInfo);
  }

  private static List<Product> createCatalogue() {
    List<Product> products = new ArrayList<>();

    String[] categories = {"Electronics", "Home", "Garden", "Sports", "Books"};
    String[] adjectives = {"Premium", "Standard", "Budget", "Deluxe", "Basic"};

    IntStream.range(1, 21)
        .forEach(
            i -> {
              String category = categories[(i - 1) % categories.length];
              String adjective = adjectives[(i - 1) % adjectives.length];
              String name = adjective + " " + category + " Item " + i;
              String sku = String.format("SKU%03d", i);
              double price = 10.0 + (i * 5.0) + ((i % 3) * 2.5);
              int stock = 50 + (i * 10) - ((i % 4) * 15);

              products.add(new Product(sku, name, price, Math.max(5, stock), false, ""));
            });

    return products;
  }
}
