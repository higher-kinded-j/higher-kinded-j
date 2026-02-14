// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.optics.extensions.LensExtensions.*;
import static org.higherkindedj.optics.extensions.PrismExtensions.*;
import static org.higherkindedj.optics.extensions.TraversalExtensions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

/**
 * A comprehensive real-world example demonstrating integrated optics patterns with Higher-Kinded-J
 * core types.
 *
 * <p>This example combines all three integration approaches in a complete e-commerce order
 * processing workflow:
 *
 * <ol>
 *   <li><b>Core Type Prisms</b> - Safely extract and pattern match on Maybe, Either, Try results
 *   <li><b>Lens Extensions</b> - Validate individual fields with null safety
 *   <li><b>Traversal Extensions</b> - Process collections with error accumulation
 * </ol>
 *
 * <p><b>Scenario:</b> Complete e-commerce order validation and processing pipeline. We receive API
 * responses, validate customer data, process order items, apply discounts, and handle database
 * persistence—all whilst maintaining functional purity and comprehensive error handling.
 *
 * <p><b>Key Patterns Demonstrated:</b>
 *
 * <ul>
 *   <li>Composing prisms with traversals for deep data extraction
 *   <li>Combining fail-fast (Either) and error accumulation (Validated) strategies
 *   <li>Safe null handling throughout the pipeline
 *   <li>Exception-safe database operations with Try
 *   <li>Conditional updates based on business rules
 * </ul>
 *
 * @see org.higherkindedj.optics.extensions.LensExtensions
 * @see org.higherkindedj.optics.extensions.PrismExtensions
 * @see org.higherkindedj.optics.extensions.TraversalExtensions
 */
public class IntegrationPatternsExample {

  // Domain model
  @GenerateLenses
  record IPCustomer(String customerId, String name, String email, Maybe<String> phoneNumber) {}

  @GenerateLenses
  record IPOrderItem(
      String sku, String name, BigDecimal unitPrice, int quantity, Maybe<String> discountCode) {}

  @GenerateLenses
  record IPOrder(
      String orderId, IPCustomer customer, List<IPOrderItem> items, IPOrderStatus status) {}

  @GeneratePrisms
  sealed interface IPOrderStatus
      permits IPDraft, IPSubmitted, IPProcessing, IPCompleted, IPFailed {}

  record IPDraft() implements IPOrderStatus {}

  record IPSubmitted(LocalDateTime timestamp) implements IPOrderStatus {}

  record IPProcessing(String stage) implements IPOrderStatus {}

  record IPCompleted(LocalDateTime timestamp, BigDecimal totalAmount) implements IPOrderStatus {}

  record IPFailed(String reason, LocalDateTime timestamp) implements IPOrderStatus {}

  // API response wrapper
  @GenerateLenses
  record IPApiResponse<T>(int statusCode, Maybe<T> data, List<String> errors) {}

  // Validation results
  sealed interface ValidationResult permits ValidationSuccess, ValidationFailure {}

  record ValidationSuccess(IPOrder order) implements ValidationResult {}

  record ValidationFailure(List<String> errors) implements ValidationResult {}

  public static void main(String[] args) {
    System.out.println("=== Integrated Optics Patterns Example ===\n");
    System.out.println("Complete E-Commerce IPOrder IPProcessing Workflow\n");
    System.out.println("=".repeat(70) + "\n");

    demonstrateCompleteWorkflow();
    demonstrateErrorAccumulationVsFailFast();
    demonstrateCompositionPatterns();
  }

  private static void demonstrateCompleteWorkflow() {
    System.out.println("📦 SCENARIO 1: Complete IPOrder IPProcessing Workflow\n");

    // Step 1: Receive order from API
    IPApiResponse<IPOrder> apiResponse = receiveOrderFromApi();
    System.out.println("Step 1: API Response Received");
    System.out.println("  Status Code: " + apiResponse.statusCode());
    System.out.println("  Has Data: " + apiResponse.data().isJust());
    System.out.println("  Errors: " + apiResponse.errors());

    // Step 2: Extract order using prism (safe optional extraction)
    Prism<Maybe<IPOrder>, IPOrder> justPrism = Prisms.just();
    Maybe<IPOrder> maybeOrder = apiResponse.data();

    if (maybeOrder.isJust()) {
      IPOrder order = maybeOrder.get();
      System.out.println("\n✅ IPOrder extracted successfully: " + order.orderId());
      processOrder(order);
    } else {
      System.out.println("\n❌ No order data in API response");
    }

    System.out.println("\n" + "=".repeat(70) + "\n");
  }

  private static void processOrder(IPOrder order) {
    // Step 3: Validate customer data
    System.out.println("\nStep 2: IPCustomer Validation");
    Validated<List<String>, IPCustomer> customerValidation = validateCustomer(order.customer());

    customerValidation.fold(
        errors -> {
          System.out.println("  ❌ IPCustomer validation failed:");
          errors.forEach(err -> System.out.println("     • " + err));
          return null;
        },
        validCustomer -> {
          System.out.println("  ✅ IPCustomer valid: " + validCustomer.name());

          // Step 4: Validate all order items
          System.out.println("\nStep 3: IPOrder Items Validation");
          ValidationResult itemsValidation = validateOrderItems(order.items());

          switch (itemsValidation) {
            case ValidationSuccess success -> {
              System.out.println("  ✅ All items valid");

              // Step 5: Apply discounts where applicable
              System.out.println("\nStep 4: Apply Discounts");
              List<IPOrderItem> discountedItems = applyDiscounts(order.items());
              System.out.println("  ✅ Discounts applied to eligible items");

              // Step 6: Calculate total
              BigDecimal total = calculateTotal(discountedItems);
              System.out.println("\nStep 5: Calculate Total");
              System.out.println("  Total Amount: £" + total);

              // Step 7: Save to database
              System.out.println("\nStep 6: Database Persistence");
              Try<IPOrder> saveResult = saveOrderToDatabase(order);
              saveResult.fold(
                  savedOrder -> {
                    System.out.println("  ✅ IPOrder saved: " + savedOrder.orderId());
                    return null;
                  },
                  error -> {
                    System.out.println("  ❌ Save failed: " + error.getMessage());
                    return null;
                  });
            }
            case ValidationFailure failure -> {
              System.out.println(
                  "  ❌ Item validation failed (" + failure.errors().size() + " errors):");
              failure.errors().forEach(err -> System.out.println("     • " + err));
            }
          }
          return null;
        });
  }

  private static Validated<List<String>, IPCustomer> validateCustomer(IPCustomer customer) {
    Lens<IPCustomer, String> nameLens = IPCustomerLenses.name();
    Lens<IPCustomer, String> emailLens = IPCustomerLenses.email();

    // Validate each field independently and collect errors
    List<String> errors = new ArrayList<>();

    // Validate name
    Validated<String, String> nameValidation =
        customer.name() == null || customer.name().trim().isEmpty()
            ? Validated.invalid("Name is required")
            : customer.name().length() < 2
                ? Validated.invalid("Name too short")
                : Validated.valid(customer.name());

    // Validate email
    Validated<String, String> emailValidation =
        customer.email() == null || !customer.email().contains("@")
            ? Validated.invalid("Valid email is required")
            : Validated.valid(customer.email());

    // Collect errors
    if (nameValidation.isInvalid()) {
      errors.add(nameValidation.getError());
    }
    if (emailValidation.isInvalid()) {
      errors.add(emailValidation.getError());
    }

    // If there are errors, return invalid with all errors
    if (!errors.isEmpty()) {
      return Validated.invalid(errors);
    }

    // Both validations passed, update the customer with validated values
    IPCustomer updated = nameLens.set(nameValidation.get(), customer);
    updated = emailLens.set(emailValidation.get(), updated);

    return Validated.valid(updated);
  }

  private static ValidationResult validateOrderItems(List<IPOrderItem> items) {
    Lens<IPOrderItem, BigDecimal> priceLens = IPOrderItemLenses.unitPrice();
    Traversal<List<IPOrderItem>, BigDecimal> allPrices =
        Traversals.<IPOrderItem>forList().andThen(priceLens.asTraversal());

    Lens<IPOrderItem, Integer> quantityLens = IPOrderItemLenses.quantity();
    Traversal<List<IPOrderItem>, Integer> allQuantities =
        Traversals.<IPOrderItem>forList().andThen(quantityLens.asTraversal());

    // Collect all validation errors
    List<String> priceErrors =
        collectErrors(
            allPrices,
            price -> {
              if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return Either.left("Price must be positive");
              }
              if (price.compareTo(new BigDecimal("10000")) > 0) {
                return Either.left("Price exceeds maximum");
              }
              return Either.right(price);
            },
            items);

    List<String> quantityErrors =
        collectErrors(
            allQuantities,
            qty -> {
              if (qty <= 0) {
                return Either.left("Quantity must be positive");
              }
              if (qty > 100) {
                return Either.left("Quantity exceeds maximum");
              }
              return Either.right(qty);
            },
            items);

    List<String> allErrors =
        List.of(priceErrors, quantityErrors).stream().flatMap(List::stream).toList();

    if (allErrors.isEmpty()) {
      return new ValidationSuccess(null); // Placeholder
    } else {
      return new ValidationFailure(allErrors);
    }
  }

  private static List<IPOrderItem> applyDiscounts(List<IPOrderItem> items) {
    Lens<IPOrderItem, BigDecimal> priceLens = IPOrderItemLenses.unitPrice();
    Traversal<List<IPOrderItem>, BigDecimal> allPrices =
        Traversals.<IPOrderItem>forList().andThen(priceLens.asTraversal());

    // Apply 10% discount to items over £100 (selective modification)
    return modifyWherePossible(
        allPrices,
        price ->
            price.compareTo(new BigDecimal("100")) > 0
                ? Maybe.just(price.multiply(new BigDecimal("0.9")))
                : Maybe.nothing(),
        items);
  }

  private static BigDecimal calculateTotal(List<IPOrderItem> items) {
    return items.stream()
        .map(item -> item.unitPrice().multiply(new BigDecimal(item.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static Try<IPOrder> saveOrderToDatabase(IPOrder order) {
    return Try.of(
        () -> {
          // Simulate database operation
          if (order.customer().email().contains("fail")) {
            throw new RuntimeException("Database connection failed");
          }
          return order;
        });
  }

  private static void demonstrateErrorAccumulationVsFailFast() {
    System.out.println("📊 SCENARIO 2: Error Accumulation vs Fail-Fast\n");

    List<IPOrderItem> problematicItems =
        List.of(
            new IPOrderItem("SKU001", "Item 1", new BigDecimal("-10"), 1, Maybe.nothing()),
            new IPOrderItem("SKU002", "Item 2", new BigDecimal("50"), -5, Maybe.nothing()),
            new IPOrderItem("SKU003", "Item 3", new BigDecimal("-20"), 0, Maybe.nothing()));

    Lens<IPOrderItem, BigDecimal> priceLens = IPOrderItemLenses.unitPrice();
    Traversal<List<IPOrderItem>, BigDecimal> allPrices =
        Traversals.<IPOrderItem>forList().andThen(priceLens.asTraversal());

    // Strategy 1: Fail-Fast (Either)
    System.out.println("Strategy 1: Fail-Fast with Either");
    Either<String, List<IPOrderItem>> failFast =
        modifyAllEither(
            allPrices,
            price ->
                price.compareTo(BigDecimal.ZERO) > 0
                    ? Either.right(price)
                    : Either.left("Invalid price: " + price),
            problematicItems);

    failFast.fold(
        error -> {
          System.out.println("  ❌ IPFailed immediately: " + error);
          return null;
        },
        success -> {
          System.out.println("  ✅ All valid");
          return null;
        });

    // Strategy 2: Error Accumulation (Validated)
    System.out.println("\nStrategy 2: Error Accumulation with Validated");
    Validated<List<String>, List<IPOrderItem>> accumulate =
        modifyAllValidated(
            allPrices,
            price ->
                price.compareTo(BigDecimal.ZERO) > 0
                    ? Validated.valid(price)
                    : Validated.invalid("Invalid price: " + price),
            problematicItems);

    accumulate.fold(
        errors -> {
          System.out.println("  ❌ Found " + errors.size() + " errors:");
          errors.forEach(err -> System.out.println("     • " + err));
          return null;
        },
        success -> {
          System.out.println("  ✅ All valid");
          return null;
        });

    System.out.println("\n💡 Key Difference:");
    System.out.println("  • Fail-Fast: Stops at first error (efficient for quick rejection)");
    System.out.println("  • Accumulation: Collects all errors (better UX, show everything wrong)");

    System.out.println("\n" + "=".repeat(70) + "\n");
  }

  private static void demonstrateCompositionPatterns() {
    System.out.println("🔗 SCENARIO 3: Advanced Composition Patterns\n");

    IPOrder order =
        new IPOrder(
            "ORD-789",
            new IPCustomer("CUST-123", "Alice", "alice@example.com", Maybe.just("07700900000")),
            List.of(
                new IPOrderItem(
                    "SKU001", "Premium Laptop", new BigDecimal("999.99"), 1, Maybe.just("SAVE10")),
                new IPOrderItem(
                    "SKU002", "Wireless Mouse", new BigDecimal("29.99"), 2, Maybe.nothing())),
            new IPDraft());

    // Pattern 1: Deep navigation with prisms
    System.out.println("Pattern 1: Extract customer phone (nested Maybe)");
    Lens<IPOrder, IPCustomer> customerLens = IPOrderLenses.customer();
    Lens<IPCustomer, Maybe<String>> phoneLens = IPCustomerLenses.phoneNumber();

    // Get phone number using lens composition and prism
    Maybe<String> phone = order.customer().phoneNumber();
    Prism<Maybe<String>, String> justPrism = Prisms.just();

    System.out.println("  Phone: " + justPrism.getOptional(phone).orElse("No phone provided"));

    // Pattern 2: Conditional bulk updates
    System.out.println("\nPattern 2: Apply discount codes");
    Lens<IPOrderItem, Maybe<String>> discountLens = IPOrderItemLenses.discountCode();
    Traversal<List<IPOrderItem>, Maybe<String>> allDiscountCodes =
        Traversals.<IPOrderItem>forList().andThen(discountLens.asTraversal());

    int itemsWithDiscount =
        countValid(
            allDiscountCodes,
            maybeCode -> maybeCode.isJust() ? Either.right(maybeCode) : Either.left("No code"),
            order.items());

    System.out.println(
        "  Items with discount codes: " + itemsWithDiscount + "/" + order.items().size());

    // Pattern 3: Status transitions with prisms
    System.out.println("\nPattern 3: IPOrder status management");
    Prism<IPOrderStatus, IPDraft> draftPrism = IPOrderStatusPrisms.iPDraft();

    if (draftPrism.matches(order.status())) {
      System.out.println("  ✅ IPOrder is in IPDraft status, ready to submit");
      IPOrderStatus newStatus = new IPSubmitted(LocalDateTime.now());
      System.out.println("  Transitioning to: IPSubmitted");
    }

    System.out.println();
  }

  // Helper: Simulate API response
  private static IPApiResponse<IPOrder> receiveOrderFromApi() {
    IPOrder order =
        new IPOrder(
            "ORD-456",
            new IPCustomer("CUST-789", "Bob Smith", "bob@example.com", Maybe.nothing()),
            List.of(
                new IPOrderItem("SKU100", "Product A", new BigDecimal("49.99"), 2, Maybe.nothing()),
                new IPOrderItem(
                    "SKU200", "Product B", new BigDecimal("149.99"), 1, Maybe.just("FIRST10"))),
            new IPDraft());

    return new IPApiResponse<>(200, Maybe.just(order), List.of());
  }
}
