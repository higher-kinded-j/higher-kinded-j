// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.optics.extensions.LensExtensions.*;
import static org.higherkindedj.optics.extensions.PrismExtensions.*;
import static org.higherkindedj.optics.extensions.TraversalExtensions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerFocus;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.edit.Edit;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.focus.FocusPath;
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
 *   <li>Combining first-error (Either) and error-accumulating (Validated) strategies
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

  // Domain model: the chapter's Order, Customer, LineItem and Consignment, and two supporting types
  // for what the chapter's cast does not carry

  // The phone number the shop holds for a customer, if they gave one
  @GenerateLenses
  record CustomerPhone(Customer customer, Maybe<String> number) {}

  // The discount code a shopper entered against an order line, if any
  @GenerateLenses
  record LineDiscount(LineItem line, Maybe<String> code) {}

  // API response wrapper
  @GenerateLenses
  record IPApiResponse<T>(int statusCode, Maybe<T> data, List<String> errors) {}

  // Validation results
  sealed interface OrderVerdict permits OrderApproved, OrderRejected {}

  record OrderApproved(Order order) implements OrderVerdict {}

  record OrderRejected(List<String> errors) implements OrderVerdict {}

  public static void main(String[] args) {
    System.out.println("=== Integrated Optics Patterns Example ===\n");
    System.out.println("Complete E-Commerce Order Processing Workflow\n");
    System.out.println("=".repeat(70) + "\n");

    demonstrateCompleteWorkflow();
    demonstrateErrorAccumulationVsFailFast();
    demonstrateCompositionPatterns();
  }

  private static void demonstrateCompleteWorkflow() {
    System.out.println("📦 SCENARIO 1: Complete Order Processing Workflow\n");

    // Step 1: Receive order from API
    IPApiResponse<Order> apiResponse = receiveOrderFromApi();
    System.out.println("Step 1: API Response Received");
    System.out.println("  Status Code: " + apiResponse.statusCode());
    System.out.println("  Has Data: " + apiResponse.data().isJust());
    System.out.println("  Errors: " + apiResponse.errors());

    // Step 2: Extract order using prism (safe optional extraction)
    Prism<Maybe<Order>, Order> justPrism = Prisms.just();
    Maybe<Order> maybeOrder = apiResponse.data();

    if (maybeOrder.isJust()) {
      Order order = maybeOrder.get();
      System.out.println("\n✅ Order extracted successfully: " + order.id());
      processOrder(order);
    } else {
      System.out.println("\n❌ No order data in API response");
    }

    System.out.println("\n" + "=".repeat(70) + "\n");
  }

  private static void processOrder(Order order) {
    // Step 3: Validate customer data
    System.out.println("\nStep 2: Customer Validation");
    Validated<NonEmptyList<FieldError>, Customer> customerValidation =
        validateCustomer(order.customer());

    customerValidation.fold(
        errors -> {
          System.out.println("  ❌ Customer validation failed:");
          errors.forEach(err -> System.out.println("     • " + err));
          return null;
        },
        validCustomer -> {
          System.out.println("  ✅ Customer valid: " + validCustomer.name());

          // Step 4: Validate all order items
          System.out.println("\nStep 3: Order Items Validation");
          OrderVerdict itemsValidation = validateOrderItems(order);

          switch (itemsValidation) {
            case OrderApproved approved -> {
              System.out.println("  ✅ All items valid");

              // Step 5: Apply discounts where applicable
              System.out.println("\nStep 4: Apply Discounts");
              List<LineItem> discountedItems = applyDiscounts(order.lines());
              System.out.println("  ✅ Discounts applied to eligible items");

              // Step 6: Calculate total
              BigDecimal total = calculateTotal(discountedItems);
              System.out.println("\nStep 5: Calculate Total");
              System.out.println("  Total Amount: £" + total);

              // Step 7: Save to database
              System.out.println("\nStep 6: Database Persistence");
              Try<Order> saveResult = saveOrderToDatabase(order);
              saveResult.foldFailureFirst(
                  error -> {
                    System.out.println("  ❌ Save failed: " + error.getMessage());
                    return null;
                  },
                  savedOrder -> {
                    System.out.println("  ✅ Order saved: " + savedOrder.id());
                    return null;
                  });
            }
            case OrderRejected rejected -> {
              System.out.println(
                  "  ❌ Item validation failed (" + rejected.errors().size() + " errors):");
              rejected.errors().forEach(err -> System.out.println("     • " + err));
            }
          }
          return null;
        });
  }

  private static Validated<NonEmptyList<FieldError>, Customer> validateCustomer(Customer customer) {
    FocusPath<Customer, String> name = CustomerFocus.name();
    FocusPath<Customer, String> email = CustomerFocus.email().value();

    // Every field validated independently; ALL failures reported at once, located by field.
    // A null incoming value would mean "absent" to parseIfPresent, so required fields are
    // defaulted to "" first - the parser then reports them as missing.
    return Edits.accumulate(
            Edit.parseIfPresent(
                    name,
                    Objects.requireNonNullElse(customer.name(), ""),
                    IntegrationPatternsExample::parseName)
                .at("name"),
            Edit.parseIfPresent(
                    email,
                    Objects.requireNonNullElse(customer.email(), new EmailAddress("")).value(),
                    IntegrationPatternsExample::parseEmail)
                .at("email"))
        .apply(customer);
  }

  private static Validated<NonEmptyList<FieldError>, String> parseName(String name) {
    if (name.trim().isEmpty()) {
      return Validated.invalidNel(FieldError.of("name is required"));
    }
    return name.length() < 2
        ? Validated.invalidNel(FieldError.of("name too short"))
        : Validated.validNel(name);
  }

  private static Validated<NonEmptyList<FieldError>, String> parseEmail(String email) {
    return email.contains("@")
        ? Validated.validNel(email)
        : Validated.invalidNel(FieldError.of("valid email is required"));
  }

  private static OrderVerdict validateOrderItems(Order order) {
    List<LineItem> items = order.lines();
    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    Lens<LineItem, Integer> quantityLens = LineItemLenses.quantity();
    Traversal<List<LineItem>, Integer> allQuantities =
        Traversals.<LineItem>forList().andThen(quantityLens.asTraversal());

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
      return new OrderApproved(order);
    } else {
      return new OrderRejected(allErrors);
    }
  }

  private static List<LineItem> applyDiscounts(List<LineItem> items) {
    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    // Apply 10% discount to items over £100 (selective modification)
    return modifyWherePossible(
        allPrices,
        price ->
            price.compareTo(new BigDecimal("100")) > 0
                ? Maybe.just(
                    price.multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_EVEN))
                : Maybe.nothing(),
        items);
  }

  private static BigDecimal calculateTotal(List<LineItem> items) {
    return items.stream()
        .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static Try<Order> saveOrderToDatabase(Order order) {
    return Try.of(
        () -> {
          // Simulate database operation
          if (order.customer().email().value().contains("fail")) {
            throw new RuntimeException("Database connection failed");
          }
          return order;
        });
  }

  private static void demonstrateErrorAccumulationVsFailFast() {
    System.out.println("📊 SCENARIO 2: Error Accumulation vs First Error\n");

    List<LineItem> problematicItems =
        List.of(
            new LineItem("SKU001", 1, new BigDecimal("-10.00")),
            new LineItem("SKU002", -5, new BigDecimal("50.00")),
            new LineItem("SKU003", 0, new BigDecimal("-20.00")));

    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    // Strategy 1: First Error (Either)
    System.out.println("Strategy 1: First Error with Either");
    Either<String, List<LineItem>> firstError =
        modifyAllEither(
            allPrices,
            price ->
                price.compareTo(BigDecimal.ZERO) > 0
                    ? Either.right(price)
                    : Either.left("Invalid price: " + price),
            problematicItems);

    firstError.fold(
        error -> {
          System.out.println("  ❌ Failed: " + error);
          return null;
        },
        success -> {
          System.out.println("  ✅ All valid");
          return null;
        });

    // Strategy 2: Error Accumulation (Validated)
    System.out.println("\nStrategy 2: Error Accumulation with Validated");
    Validated<List<String>, List<LineItem>> accumulate =
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
    System.out.println("  • First error: Reports one error, though every price is still checked");
    System.out.println("  • Accumulation: Collects all errors (better UX, show everything wrong)");

    System.out.println("\n" + "=".repeat(70) + "\n");
  }

  private static void demonstrateCompositionPatterns() {
    System.out.println("🔗 SCENARIO 3: Advanced Composition Patterns\n");

    Customer alice = new Customer("Alice", new EmailAddress("alice@example.com"));
    LineItem laptop = new LineItem("SKU001", 1, new BigDecimal("999.99"));
    LineItem mouse = new LineItem("SKU002", 2, new BigDecimal("29.99"));
    Order order = order("00000000-0000-0000-0000-000000000789", alice, laptop, mouse);

    CustomerPhone phoneOnFile = new CustomerPhone(alice, Maybe.just("07700900000"));
    List<LineDiscount> discounts =
        List.of(
            new LineDiscount(laptop, Maybe.just("SAVE10")),
            new LineDiscount(mouse, Maybe.nothing()));
    Consignment consignment =
        new Consignment(
            order.id(),
            new Address("1 Long Street", "London", "N1 1AA"),
            new ConsignmentState.Pending());

    // Pattern 1: Deep navigation with prisms
    System.out.println("Pattern 1: Extract customer phone (nested Maybe)");
    Lens<CustomerPhone, Maybe<String>> numberLens = CustomerPhoneLenses.number();
    Prism<Maybe<String>, String> justPrism = Prisms.just();

    // Get the phone number with the lens, then unwrap it with the prism
    Maybe<String> phone = numberLens.get(phoneOnFile);

    System.out.println("  Phone: " + justPrism.getOptional(phone).orElse("No phone provided"));

    // Pattern 2: Conditional bulk updates
    System.out.println("\nPattern 2: Apply discount codes");
    Lens<LineDiscount, Maybe<String>> codeLens = LineDiscountLenses.code();
    Traversal<List<LineDiscount>, Maybe<String>> allDiscountCodes =
        Traversals.<LineDiscount>forList().andThen(codeLens.asTraversal());

    int itemsWithDiscount =
        countValid(
            allDiscountCodes,
            maybeCode -> maybeCode.isJust() ? Either.right(maybeCode) : Either.left("No code"),
            discounts);

    System.out.println(
        "  Items with discount codes: " + itemsWithDiscount + "/" + discounts.size());

    // Pattern 3: State transitions with prisms
    System.out.println("\nPattern 3: Consignment state management");
    Prism<ConsignmentState, ConsignmentState.Pending> pendingPrism =
        ConsignmentStatePrisms.pending();

    if (pendingPrism.matches(consignment.state())) {
      System.out.println("  ✅ Consignment is Pending, ready to dispatch");
      Consignment dispatched =
          ConsignmentLenses.state()
              .set(
                  new ConsignmentState.Dispatched(Instant.parse("2026-10-01T12:00:00Z")),
                  consignment);
      System.out.println("  Transitioning to: " + dispatched.state());
    }

    System.out.println();
  }

  // Helper: Simulate API response
  private static IPApiResponse<Order> receiveOrderFromApi() {
    Order order =
        order(
            "00000000-0000-0000-0000-000000000456",
            new Customer("Bob Smith", new EmailAddress("bob@example.com")),
            new LineItem("SKU100", 2, new BigDecimal("49.99")),
            new LineItem("SKU200", 1, new BigDecimal("149.99")));

    return new IPApiResponse<>(200, Maybe.just(order), List.of());
  }

  // Helper: a new order of the customer's, placed on 1 October, in pounds
  private static Order order(String id, Customer customer, LineItem... lines) {
    return new Order(
        UUID.fromString(id),
        customer,
        List.of(lines),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        OrderStatus.NEW);
  }
}
