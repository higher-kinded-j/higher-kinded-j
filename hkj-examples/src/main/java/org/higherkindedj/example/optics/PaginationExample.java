// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

  // ANCHOR: model
  public record Product(String sku, String name, BigDecimal price, int stock) {
    Product applyDiscount(int percent) {
      BigDecimal factor = BigDecimal.valueOf(100 - percent, 2); // 30 percent off is 0.70
      return new Product(
          sku, name, price.multiply(factor).setScale(2, RoundingMode.HALF_EVEN), stock);
    }
  }

  // How a page of the catalogue shows a product: featured or not, under a badge
  public record ShopListing(Product product, boolean featured, String badge) {
    ShopListing withBadge(String newBadge) {
      return new ShopListing(product, featured, newBadge);
    }

    ShopListing applyDiscount(int percent) {
      return new ShopListing(product.applyDiscount(percent), featured, badge);
    }

    ShopListing markFeatured() {
      return new ShopListing(product, true, badge);
    }
  }

  public record PageInfo(int pageNumber, int pageSize, int totalItems, int totalPages) {}

  public record PagedResponse(List<ShopListing> items, PageInfo pageInfo) {}

  // ANCHOR_END: model

  // Constants
  private static final int DEFAULT_PAGE_SIZE = 5;

  public static void main(String[] args) {
    List<ShopListing> catalogue = createCatalogue();

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

  private static void demonstratePagination(List<ShopListing> catalogue) {
    System.out.println("--- Scenario 1: Basic Pagination ---");

    int totalPages = (int) Math.ceil(catalogue.size() / (double) DEFAULT_PAGE_SIZE);
    System.out.println("Total pages: " + totalPages);
    System.out.println();

    for (int page = 0; page < totalPages; page++) {
      PagedResponse response = getPage(catalogue, page, DEFAULT_PAGE_SIZE);
      System.out.printf("Page %d of %d:%n", page + 1, response.pageInfo().totalPages());
      for (ShopListing listing : response.items()) {
        Product p = listing.product();
        System.out.printf("  • %s - £%.2f (%d in stock)%n", p.name(), p.price(), p.stock());
      }
      System.out.println();
    }
  }

  // ANCHOR: hero
  private static void demonstrateFeaturedProducts(List<ShopListing> catalogue) {
    System.out.println("--- Scenario 2: Featured Products (Hero Section) ---");

    // First 3 products are featured on the hero section
    Traversal<List<ShopListing>, ShopListing> heroProducts = ListTraversals.taking(3);

    // Mark them as featured and add "HOT" badge
    List<ShopListing> withHeroSection =
        Traversals.modify(heroProducts, l -> l.markFeatured().withBadge("HOT"), catalogue);

    System.out.println("Hero section products:");
    Traversals.getAll(heroProducts, withHeroSection)
        .forEach(
            l ->
                System.out.printf(
                    "  ⭐ %s [%s] - Featured: %s%n", l.product().name(), l.badge(), l.featured()));

    // Apply special 15% discount to hero products, through each listing's product
    Lens<ShopListing, Product> productLens =
        Lens.of(ShopListing::product, (l, p) -> new ShopListing(p, l.featured(), l.badge()));
    Lens<Product, BigDecimal> priceLens =
        Lens.of(
            Product::price, (p, newPrice) -> new Product(p.sku(), p.name(), newPrice, p.stock()));

    Traversal<List<ShopListing>, BigDecimal> heroPrices =
        heroProducts.andThen(productLens).andThen(priceLens);

    List<ShopListing> discountedHero =
        Traversals.modify(
            heroPrices,
            price -> price.multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_EVEN),
            catalogue);

    System.out.println("\nAfter 15% hero discount:");
    for (int i = 0; i < 5; i++) {
      Product original = catalogue.get(i).product();
      Product discounted = discountedHero.get(i).product();
      String marker = i < 3 ? "★" : " ";
      System.out.printf(
          "  %s %s: £%.2f → £%.2f%n",
          marker, original.name(), original.price(), discounted.price());
    }
    System.out.println();
  }

  // ANCHOR_END: hero

  // ANCHOR: clearance
  private static void demonstrateClearanceSection(List<ShopListing> catalogue) {
    System.out.println("--- Scenario 3: Clearance Section ---");

    // Last 4 products are clearance items
    Traversal<List<ShopListing>, ShopListing> clearanceItems = ListTraversals.takingLast(4);

    System.out.println("Clearance items (last 4):");
    List<ShopListing> clearance = Traversals.getAll(clearanceItems, catalogue);
    clearance.forEach(
        l -> System.out.printf("  🏷️ %s - £%.2f%n", l.product().name(), l.product().price()));

    // Apply 30% clearance discount
    List<ShopListing> withClearance =
        Traversals.modify(
            clearanceItems, l -> l.applyDiscount(30).withBadge("CLEARANCE"), catalogue);

    System.out.println("\nAfter 30% clearance discount:");
    Traversals.getAll(clearanceItems, withClearance)
        .forEach(
            l ->
                System.out.printf(
                    "  🏷️ %s [%s] - £%.2f%n", l.product().name(), l.badge(), l.product().price()));

    // Regular items (all except clearance)
    Traversal<List<ShopListing>, ShopListing> regularItems = ListTraversals.droppingLast(4);
    List<ShopListing> regular = Traversals.getAll(regularItems, catalogue);
    System.out.println("\nRegular items (excluding clearance): " + regular.size() + " products");

    System.out.println();
  }

  // ANCHOR_END: clearance

  private static void demonstrateBulkPageUpdate(List<ShopListing> catalogue) {
    System.out.println("--- Scenario 4: Bulk Update Specific Pages ---");

    // Apply different badges to different pages
    System.out.println("Applying seasonal badges by page:");

    // Page 1 (indices 0-4): "SPRING"
    Traversal<List<ShopListing>, ShopListing> page1 = ListTraversals.slicing(0, 5);
    List<ShopListing> step1 = Traversals.modify(page1, l -> l.withBadge("SPRING"), catalogue);
    System.out.println("  Page 1: SPRING badge");

    // Page 2 (indices 5-9): "SUMMER"
    Traversal<List<ShopListing>, ShopListing> page2 = ListTraversals.slicing(5, 10);
    List<ShopListing> step2 = Traversals.modify(page2, l -> l.withBadge("SUMMER"), step1);
    System.out.println("  Page 2: SUMMER badge");

    // Page 3 (indices 10-14): "AUTUMN"
    Traversal<List<ShopListing>, ShopListing> page3 = ListTraversals.slicing(10, 15);
    List<ShopListing> step3 = Traversals.modify(page3, l -> l.withBadge("AUTUMN"), step2);
    System.out.println("  Page 3: AUTUMN badge");

    // Remaining (indices 15+): "WINTER"
    Traversal<List<ShopListing>, ShopListing> remaining = ListTraversals.dropping(15);
    List<ShopListing> finalCatalogue =
        Traversals.modify(remaining, l -> l.withBadge("WINTER"), step3);
    System.out.println("  Remaining: WINTER badge");

    System.out.println("\nSample products after seasonal tagging:");
    System.out.printf(
        "  Product 1 (Page 1): %s [%s]%n",
        finalCatalogue.get(0).product().name(), finalCatalogue.get(0).badge());
    System.out.printf(
        "  Product 6 (Page 2): %s [%s]%n",
        finalCatalogue.get(5).product().name(), finalCatalogue.get(5).badge());
    System.out.printf(
        "  Product 11 (Page 3): %s [%s]%n",
        finalCatalogue.get(10).product().name(), finalCatalogue.get(10).badge());
    System.out.printf(
        "  Product 16 (Page 4): %s [%s]%n",
        finalCatalogue.get(15).product().name(), finalCatalogue.get(15).badge());

    System.out.println();
  }

  private static void demonstratePageRangeQuery(List<ShopListing> catalogue) {
    System.out.println("--- Scenario 5: Query Specific Page Ranges ---");

    // Get products from pages 2 and 3 (indices 5-14)
    Traversal<List<ShopListing>, ShopListing> pages2And3 = ListTraversals.slicing(5, 15);
    List<Product> middlePages =
        Traversals.getAll(pages2And3, catalogue).stream().map(ShopListing::product).toList();

    System.out.println("Products from pages 2-3 (for bulk shipment):");
    System.out.println("  Count: " + middlePages.size() + " products");

    BigDecimal totalValue =
        middlePages.stream()
            .map(p -> p.price().multiply(BigDecimal.valueOf(p.stock())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    System.out.printf("  Total inventory value: £%.2f%n", totalValue);

    int totalStock = middlePages.stream().mapToInt(Product::stock).sum();
    System.out.println("  Total units: " + totalStock);

    // Calculate average price for middle pages
    BigDecimal avgPrice =
        middlePages.isEmpty()
            ? BigDecimal.ZERO
            : middlePages.stream()
                .map(Product::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(middlePages.size()), 2, RoundingMode.HALF_EVEN);
    System.out.printf("  Average price: £%.2f%n", avgPrice);

    System.out.println();
    System.out.println("=== Pagination Examples Complete ===");
  }

  // Helper methods
  // ANCHOR: get_page
  private static PagedResponse getPage(List<ShopListing> catalogue, int pageNumber, int pageSize) {
    Traversal<List<ShopListing>, ShopListing> pageTraversal =
        ListTraversals.slicing(pageNumber * pageSize, (pageNumber + 1) * pageSize);

    List<ShopListing> items = Traversals.getAll(pageTraversal, catalogue);

    int totalPages = (int) Math.ceil(catalogue.size() / (double) pageSize);
    PageInfo pageInfo = new PageInfo(pageNumber, pageSize, catalogue.size(), totalPages);

    return new PagedResponse(items, pageInfo);
  }

  // ANCHOR_END: get_page

  private static List<ShopListing> createCatalogue() {
    List<ShopListing> listings = new ArrayList<>();

    String[] categories = {"Electronics", "Home", "Garden", "Sports", "Books"};
    String[] adjectives = {"Premium", "Standard", "Budget", "Deluxe", "Basic"};

    IntStream.range(1, 21)
        .forEach(
            i -> {
              String category = categories[(i - 1) % categories.length];
              String adjective = adjectives[(i - 1) % adjectives.length];
              String name = adjective + " " + category + " Item " + i;
              String sku = String.format("SKU%03d", i);
              // In pence: £10, plus £5 per item, plus £2.50 for each step of i % 3
              BigDecimal price = BigDecimal.valueOf(1000 + (i * 500) + ((i % 3) * 250), 2);
              int stock = 50 + (i * 10) - ((i % 4) * 15);

              Product product = new Product(sku, name, price, Math.max(5, stock));
              listings.add(new ShopListing(product, false, ""));
            });

    return listings;
  }
}
