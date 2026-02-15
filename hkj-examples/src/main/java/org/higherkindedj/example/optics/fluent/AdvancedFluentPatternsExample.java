// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.example.optics.fluent.generated.CatalogueTraversals;
import org.higherkindedj.example.optics.fluent.generated.CategoryTraversals;
import org.higherkindedj.example.optics.fluent.generated.ProductLenses;
import org.higherkindedj.example.optics.fluent.model.Catalogue;
import org.higherkindedj.example.optics.fluent.model.Category;
import org.higherkindedj.example.optics.fluent.model.Product;
import org.higherkindedj.example.optics.fluent.model.ProductStatus;
import org.higherkindedj.example.optics.fluent.model.Promotion;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;

/**
 * A runnable example demonstrating advanced fluent optic patterns. This example shows sophisticated
 * real-world scenarios that combine static methods, fluent builders, Free monad DSL, and multiple
 * interpreters.
 *
 * <p>The scenario is a product catalogue management system where we need to:
 *
 * <ul>
 *   <li>Perform bulk price updates across categories
 *   <li>Apply seasonal promotions with complex rules
 *   <li>Validate changes before applying them
 *   <li>Maintain audit trails for compliance
 *   <li>Handle errors gracefully
 *   <li>Chain multiple transformations together
 * </ul>
 */
public class AdvancedFluentPatternsExample {

  public static void main(String[] args) {
    System.out.println("=== ADVANCED FLUENT PATTERNS EXAMPLE ===\n");

    // Create sample catalogue
    Catalogue catalogue = createSampleCatalogue();
    System.out.println("Initial Catalogue:");
    printCatalogueSummary(catalogue);
    System.out.println("\n" + "=".repeat(60) + "\n");

    pattern1_ComplexQueries(catalogue);
    System.out.println("\n" + "=".repeat(60) + "\n");

    pattern2_ConditionalBulkUpdates(catalogue);
    System.out.println("\n" + "=".repeat(60) + "\n");

    pattern3_PipelinedTransformations(catalogue);
    System.out.println("\n" + "=".repeat(60) + "\n");

    pattern4_ValidatedWorkflow(catalogue);
    System.out.println("\n" + "=".repeat(60) + "\n");

    pattern5_CombiningStaticAndFluent(catalogue);
    System.out.println("\n" + "=".repeat(60) + "\n");

    pattern6_RealWorldSeasonalPromotion(catalogue);
  }

  // ============================================================================
  // PATTERN 1: Complex Queries with Multiple Criteria
  // ============================================================================

  private static void pattern1_ComplexQueries(Catalogue catalogue) {
    System.out.println("--- Pattern 1: Complex Queries ---\n");

    // Query 1: Find all active products
    System.out.println("Query 1: Count active products");
    Traversal<Catalogue, Product> allProducts =
        CatalogueTraversals.categories().andThen(CategoryTraversals.products());

    long activeCount =
        OpticOps.getAll(catalogue, allProducts).stream()
            .filter(p -> p.status() == ProductStatus.ACTIVE)
            .count();
    System.out.println("  Active products: " + activeCount);
    System.out.println();

    // Query 2: Find expensive products (> £100)
    System.out.println("Query 2: Find expensive products (> £100)");
    Traversal<Catalogue, BigDecimal> allPrices =
        allProducts.andThen(ProductLenses.price().asTraversal());

    boolean hasExpensiveProducts =
        OpticOps.querying(catalogue)
            .anyMatch(allPrices, price -> price.compareTo(new BigDecimal("100")) > 0);
    System.out.println("  Has expensive products: " + hasExpensiveProducts);
    System.out.println();

    // Query 3: Get all products with active promotions
    System.out.println("Query 3: Products with active promotions");
    List<Product> productsWithPromotions =
        OpticOps.getAll(catalogue, allProducts).stream()
            .filter(p -> p.activePromotion().isPresent())
            .collect(Collectors.toList());
    System.out.println("  Products on promotion: " + productsWithPromotions.size());
    productsWithPromotions.forEach(
        p ->
            System.out.println(
                "    - "
                    + p.name()
                    + " ("
                    + p.activePromotion().get().discountPercent()
                    + "% off)"));
    System.out.println();

    // Query 4: Check stock levels
    System.out.println("Query 4: Check if any products are low stock (< 10)");
    Traversal<Catalogue, Integer> allStockLevels =
        allProducts.andThen(ProductLenses.stockLevel().asTraversal());

    boolean hasLowStock =
        OpticOps.querying(catalogue).anyMatch(allStockLevels, stock -> stock < 10);
    System.out.println("  Has low stock items: " + hasLowStock);
    System.out.println();
  }

  // ============================================================================
  // PATTERN 2: Conditional Bulk Updates
  // ============================================================================

  private static void pattern2_ConditionalBulkUpdates(Catalogue catalogue) {
    System.out.println("--- Pattern 2: Conditional Bulk Updates ---\n");

    // Scenario: Increase prices by 10% for products > £50, by 5% for others
    System.out.println("Scenario: Tiered price increases\n");

    Traversal<Catalogue, Product> allProducts =
        CatalogueTraversals.categories().andThen(CategoryTraversals.products());

    // Get current price statistics
    List<BigDecimal> currentPrices =
        OpticOps.getAll(catalogue, allProducts.andThen(ProductLenses.price().asTraversal()));

    BigDecimal avgBefore =
        currentPrices.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(currentPrices.size()), 2, RoundingMode.HALF_UP);

    System.out.println("Before:");
    System.out.println("  Average price: £" + avgBefore);

    // Apply conditional price increases
    Catalogue updated =
        OpticOps.modifyAll(
            catalogue,
            allProducts,
            product -> {
              BigDecimal currentPrice = product.price();
              BigDecimal newPrice;
              if (currentPrice.compareTo(new BigDecimal("50")) > 0) {
                newPrice = currentPrice.multiply(new BigDecimal("1.10"));
              } else {
                newPrice = currentPrice.multiply(new BigDecimal("1.05"));
              }
              return OpticOps.set(
                  product, ProductLenses.price(), newPrice.setScale(2, RoundingMode.HALF_UP));
            });

    List<BigDecimal> updatedPrices =
        OpticOps.getAll(updated, allProducts.andThen(ProductLenses.price().asTraversal()));

    BigDecimal avgAfter =
        updatedPrices.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(updatedPrices.size()), 2, RoundingMode.HALF_UP);

    System.out.println("\nAfter:");
    System.out.println("  Average price: £" + avgAfter);
    System.out.println(
        "  Increase: "
            + avgBefore.subtract(avgAfter).abs()
            + " ("
            + avgAfter
                .subtract(avgBefore)
                .divide(avgBefore, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP)
            + "%)");
    System.out.println();
  }

  // ============================================================================
  // PATTERN 3: Pipelined Transformations
  // ============================================================================

  private static void pattern3_PipelinedTransformations(Catalogue catalogue) {
    System.out.println("--- Pattern 3: Pipelined Transformations ---\n");

    System.out.println("Scenario: Multi-stage product update pipeline\n");
    System.out.println("Stage 1: Mark out-of-stock products");
    System.out.println("Stage 2: Clear expired promotions");
    System.out.println("Stage 3: Normalise prices to 2 decimal places\n");

    Traversal<Catalogue, Product> allProducts =
        CatalogueTraversals.categories().andThen(CategoryTraversals.products());

    // Build pipeline using fluent chaining
    Catalogue result = catalogue;

    // Stage 1: Mark out-of-stock products
    result =
        OpticOps.modifying(result)
            .allThrough(
                allProducts,
                product ->
                    product.stockLevel() == 0
                        ? OpticOps.set(product, ProductLenses.status(), ProductStatus.OUT_OF_STOCK)
                        : product);

    int outOfStock =
        (int)
            OpticOps.getAll(result, allProducts).stream()
                .filter(p -> p.status() == ProductStatus.OUT_OF_STOCK)
                .count();
    System.out.println("  ✓ Stage 1 complete: " + outOfStock + " products marked out-of-stock");

    // Stage 2: Clear expired promotions
    LocalDate today = LocalDate.now();
    result =
        OpticOps.modifying(result)
            .allThrough(
                allProducts,
                product ->
                    product
                        .activePromotion()
                        .filter(promo -> promo.endDate().isAfter(today))
                        .map(promo -> product)
                        .orElse(
                            OpticOps.set(
                                product, ProductLenses.activePromotion(), Optional.empty())));

    int withPromotions =
        (int)
            OpticOps.getAll(result, allProducts).stream()
                .filter(p -> p.activePromotion().isPresent())
                .count();
    System.out.println("  ✓ Stage 2 complete: " + withPromotions + " active promotions remaining");

    // Stage 3: Normalise prices
    result =
        OpticOps.modifying(result)
            .allThrough(
                allProducts.andThen(ProductLenses.price().asTraversal()),
                price -> price.setScale(2, RoundingMode.HALF_UP));

    System.out.println("  ✓ Stage 3 complete: All prices normalised");
    System.out.println("\nPipeline execution complete!");
    System.out.println();
  }

  // ============================================================================
  // PATTERN 4: Validated Workflow with Free Monad
  // ============================================================================

  private static void pattern4_ValidatedWorkflow(Catalogue catalogue) {
    System.out.println("--- Pattern 4: Validated Workflow (Free Monad DSL) ---\n");

    System.out.println("Scenario: Bulk price increase with validation\n");

    // Build the price increase program
    Free<OpticOpKind.Witness, Catalogue> program =
        bulkPriceIncreaseProgram(catalogue, new BigDecimal("1.15"));

    // Phase 1: Validate the changes
    System.out.println("Phase 1: Validation (dry-run)");
    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult validationResult = validator.validate(program);

    System.out.println("  Validation results:");
    System.out.println("    Valid: " + validationResult.isValid());
    System.out.println("    Errors: " + validationResult.errors().size());
    System.out.println("    Warnings: " + validationResult.warnings().size());
    if (!validationResult.warnings().isEmpty()) {
      validationResult.warnings().stream()
          .limit(3)
          .forEach(warn -> System.out.println("      - " + warn));
    }
    System.out.println("  ✓ Validation passed");
    System.out.println();

    // Phase 2: Execute with audit logging
    System.out.println("Phase 2: Execution with audit trail");
    LoggingOpticInterpreter logger = OpticInterpreters.logging();
    Catalogue result = logger.run(program);

    System.out.println("  Audit log:");
    logger.getLog().stream().limit(5).forEach(entry -> System.out.println("    " + entry));
    if (logger.getLog().size() > 5) {
      System.out.println("    ... (" + (logger.getLog().size() - 5) + " more operations)");
    }
    System.out.println("  ✓ Execution complete");
    System.out.println();

    // Verify results
    Traversal<Catalogue, BigDecimal> allPrices =
        CatalogueTraversals.categories()
            .andThen(CategoryTraversals.products())
            .andThen(ProductLenses.price().asTraversal());

    BigDecimal avgPrice =
        OpticOps.getAll(result, allPrices).stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(
                new BigDecimal(
                    OpticOps.count(
                        result,
                        CatalogueTraversals.categories().andThen(CategoryTraversals.products()))),
                2,
                RoundingMode.HALF_UP);

    System.out.println("Results:");
    System.out.println("  Average price after increase: £" + avgPrice);
    System.out.println();
  }

  // ============================================================================
  // PATTERN 5: Combining Static and Fluent Styles
  // ============================================================================

  private static void pattern5_CombiningStaticAndFluent(Catalogue catalogue) {
    System.out.println("--- Pattern 5: Combining Static and Fluent Styles ---\n");

    System.out.println("Scenario: Smart inventory management\n");

    Traversal<Catalogue, Product> allProducts =
        CatalogueTraversals.categories().andThen(CategoryTraversals.products());

    // Use fluent for query
    System.out.println("Step 1: Identify low-stock active products");
    List<Product> lowStockProducts =
        OpticOps.getting(catalogue).allThrough(allProducts).stream()
            .filter(p -> p.stockLevel() < 20)
            .filter(p -> p.status() == ProductStatus.ACTIVE)
            .collect(Collectors.toList());

    System.out.println("  Found " + lowStockProducts.size() + " low-stock products:");
    lowStockProducts.forEach(
        p -> System.out.println("    - " + p.name() + " (stock: " + p.stockLevel() + ")"));
    System.out.println();

    // Use static for transformation
    System.out.println("Step 2: Apply clearance discounts to low-stock items");
    Catalogue updated =
        OpticOps.modifyAll(
            catalogue,
            allProducts,
            product -> {
              if (lowStockProducts.contains(product)) {
                // Use static method for simple operation
                BigDecimal clearancePrice =
                    OpticOps.get(product, ProductLenses.price())
                        .multiply(new BigDecimal("0.70"))
                        .setScale(2, RoundingMode.HALF_UP);

                return OpticOps.set(product, ProductLenses.price(), clearancePrice);
              }
              return product;
            });

    // Use fluent for verification
    System.out.println("\nStep 3: Verify discounts applied");
    BigDecimal avgDiscountedPrice =
        OpticOps.getting(updated).allThrough(allProducts).stream()
            .filter(lowStockProducts::contains)
            .map(p -> OpticOps.get(p, ProductLenses.price()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(lowStockProducts.size()), 2, RoundingMode.HALF_UP);

    System.out.println("  Average clearance price: £" + avgDiscountedPrice);
    System.out.println("  ✓ Clearance discounts applied successfully");
    System.out.println();
  }

  // ============================================================================
  // PATTERN 6: Real-World Seasonal Promotion Workflow
  // ============================================================================

  private static void pattern6_RealWorldSeasonalPromotion(Catalogue catalogue) {
    System.out.println("--- Pattern 6: Real-World Seasonal Promotion ---\n");

    System.out.println("Scenario: Black Friday promotion setup\n");
    System.out.println("Requirements:");
    System.out.println("  - 20% discount on electronics");
    System.out.println("  - 15% discount on clothing");
    System.out.println("  - 10% discount on everything else");
    System.out.println("  - Only active products eligible");
    System.out.println("  - Must have sufficient stock (> 5)\n");

    Promotion blackFriday =
        new Promotion(
            "BF2025", new BigDecimal("20.00"), LocalDate.now(), LocalDate.now().plusDays(3));

    // Build the promotion program
    Free<OpticOpKind.Witness, Catalogue> program =
        buildSeasonalPromotionProgram(catalogue, blackFriday);

    // Execute with full workflow
    System.out.println("Phase 1: Validation");
    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult validation = validator.validate(program);
    if (!validation.isValid()) {
      System.out.println("  ✗ Validation failed");
      validation.errors().forEach(err -> System.out.println("    " + err));
      return;
    }
    System.out.println("  ✓ Promotion rules validated");
    System.out.println();

    System.out.println("Phase 2: Execution");
    DirectOpticInterpreter executor = OpticInterpreters.direct();
    Catalogue promoted = executor.run(program);
    System.out.println("  ✓ Promotions applied");
    System.out.println();

    System.out.println("Phase 3: Results");
    Traversal<Catalogue, Product> allProducts =
        CatalogueTraversals.categories().andThen(CategoryTraversals.products());

    long promotedCount =
        OpticOps.getAll(promoted, allProducts).stream()
            .filter(p -> p.activePromotion().isPresent())
            .count();

    System.out.println("  Products on promotion: " + promotedCount);

    // Calculate savings
    List<Product> originalProducts = OpticOps.getAll(catalogue, allProducts);
    List<Product> promotedProducts = OpticOps.getAll(promoted, allProducts);

    BigDecimal totalSavings = BigDecimal.ZERO;
    for (int i = 0; i < originalProducts.size(); i++) {
      if (promotedProducts.get(i).activePromotion().isPresent()) {
        BigDecimal original = originalProducts.get(i).price();
        BigDecimal discount = promotedProducts.get(i).activePromotion().get().discountPercent();
        BigDecimal saving =
            original.multiply(discount.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        totalSavings = totalSavings.add(saving);
      }
    }

    System.out.println(
        "  Total customer savings: £" + totalSavings.setScale(2, RoundingMode.HALF_UP));
    System.out.println("\n✓ Black Friday promotion setup complete!");
    System.out.println();
  }

  // ============================================================================
  // Program Builders
  // ============================================================================

  private static Free<OpticOpKind.Witness, Catalogue> bulkPriceIncreaseProgram(
      Catalogue catalogue, BigDecimal multiplier) {
    Traversal<Catalogue, BigDecimal> allPrices =
        CatalogueTraversals.categories()
            .andThen(CategoryTraversals.products())
            .andThen(ProductLenses.price().asTraversal());

    return OpticPrograms.modifyAll(
        catalogue,
        allPrices,
        price -> price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
  }

  private static Free<OpticOpKind.Witness, Catalogue> buildSeasonalPromotionProgram(
      Catalogue catalogue, Promotion promotion) {
    Traversal<Catalogue, Product> allProducts =
        CatalogueTraversals.categories().andThen(CategoryTraversals.products());

    return OpticPrograms.modifyAll(
        catalogue,
        allProducts,
        product -> {
          // Only apply to active products with sufficient stock
          if (product.status() == ProductStatus.ACTIVE && product.stockLevel() > 5) {
            return OpticOps.set(product, ProductLenses.activePromotion(), Optional.of(promotion));
          }
          return product;
        });
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private static Catalogue createSampleCatalogue() {
    List<Category> categories = new ArrayList<>();

    // Electronics category
    List<Product> electronics = new ArrayList<>();
    electronics.add(
        new Product(
            "ELEC-001",
            "Laptop",
            new BigDecimal("899.99"),
            15,
            ProductStatus.ACTIVE,
            Optional.empty()));
    electronics.add(
        new Product(
            "ELEC-002",
            "Smartphone",
            new BigDecimal("599.99"),
            25,
            ProductStatus.ACTIVE,
            Optional.of(
                new Promotion(
                    "EARLY-BIRD",
                    new BigDecimal("10.00"),
                    LocalDate.now().minusDays(5),
                    LocalDate.now().plusDays(10)))));
    electronics.add(
        new Product(
            "ELEC-003",
            "Tablet",
            new BigDecimal("399.99"),
            0,
            ProductStatus.ACTIVE,
            Optional.empty()));

    categories.add(new Category("CAT-001", "Electronics", electronics));

    // Clothing category
    List<Product> clothing = new ArrayList<>();
    clothing.add(
        new Product(
            "CLOTH-001",
            "T-Shirt",
            new BigDecimal("19.99"),
            100,
            ProductStatus.ACTIVE,
            Optional.empty()));
    clothing.add(
        new Product(
            "CLOTH-002",
            "Jeans",
            new BigDecimal("49.99"),
            50,
            ProductStatus.ACTIVE,
            Optional.empty()));
    clothing.add(
        new Product(
            "CLOTH-003",
            "Jacket",
            new BigDecimal("89.99"),
            8,
            ProductStatus.ACTIVE,
            Optional.empty()));

    categories.add(new Category("CAT-002", "Clothing", clothing));

    // Home & Garden category
    List<Product> homeGarden = new ArrayList<>();
    homeGarden.add(
        new Product(
            "HOME-001",
            "Coffee Maker",
            new BigDecimal("79.99"),
            20,
            ProductStatus.ACTIVE,
            Optional.empty()));
    homeGarden.add(
        new Product(
            "HOME-002",
            "Blender",
            new BigDecimal("45.00"),
            12,
            ProductStatus.ACTIVE,
            Optional.empty()));

    categories.add(new Category("CAT-003", "Home & Garden", homeGarden));

    return new Catalogue("CAT-2025-MAIN", "Main Product Catalogue", categories);
  }

  private static void printCatalogueSummary(Catalogue catalogue) {
    System.out.println("  Catalogue: " + catalogue.name());
    System.out.println("  Categories: " + catalogue.categories().size());

    int totalProducts = 0;
    for (Category cat : catalogue.categories()) {
      totalProducts += cat.products().size();
    }
    System.out.println("  Total products: " + totalProducts);

    Traversal<Catalogue, BigDecimal> allPrices =
        CatalogueTraversals.categories()
            .andThen(CategoryTraversals.products())
            .andThen(ProductLenses.price().asTraversal());

    List<BigDecimal> prices = OpticOps.getAll(catalogue, allPrices);
    BigDecimal avgPrice =
        prices.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(prices.size()), 2, RoundingMode.HALF_UP);

    System.out.println("  Average price: £" + avgPrice);
  }
}
