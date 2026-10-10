// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.EitherTraversals;
import org.higherkindedj.optics.util.MaybeTraversals;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.higherkindedj.optics.util.TryTraversals;
import org.higherkindedj.optics.util.ValidatedTraversals;

/**
 * A comprehensive example demonstrating prisms and traversals for Higher-Kinded-J core types.
 *
 * <p>This example showcases how to work with {@link Maybe}, {@link Either}, {@link Validated}, and
 * {@link Try} using optics, providing a functional alternative to traditional pattern matching and
 * null checking.
 *
 * <p><b>Scenario:</b> Processing the order service's API responses, which may contain a customer,
 * errors, or validation failures. We use optics to safely extract and transform these responses
 * without verbose null checks or try-catch blocks.
 *
 * <p><b>Key Utilities Demonstrated:</b>
 *
 * <ul>
 *   <li>{@link Prisms#just()} - Extract values from {@code Maybe}
 *   <li>{@link Prisms#valid()} and {@link Prisms#invalid()} - Work with {@code Validated} cases
 *   <li>{@link Prisms#success()} and {@link Prisms#failure()} - Handle {@code Try} results
 *   <li>{@link MaybeTraversals}, {@link EitherTraversals}, {@link ValidatedTraversals}, {@link
 *       TryTraversals} - Modify values within these types
 * </ul>
 *
 * @see Prisms
 * @see MaybeTraversals
 * @see EitherTraversals
 * @see ValidatedTraversals
 * @see TryTraversals
 */
public class CoreTypePrismsExample {

  // The order service API's answer to a customer lookup, carrying the chapter's Customer
  @GenerateLenses
  record CustomerLookup(String requestId, Maybe<Customer> customer, List<String> warnings) {}

  sealed interface ValidationError permits FieldError, BusinessError {}

  record FieldError(String field, String message) implements ValidationError {}

  record BusinessError(String code, String message) implements ValidationError {}

  public static void main(String[] args) {
    System.out.println("=== Core Type Prisms & Traversals Examples ===\n");

    demonstrateMaybePrisms();
    demonstrateEitherPrisms();
    demonstrateValidatedPrisms();
    demonstrateTryPrisms();
    demonstrateMaybeTraversals();
    demonstrateEitherTraversals();
    demonstrateComposition();
    demonstrateBeforeAfterComparison();
  }

  private static void demonstrateMaybePrisms() {
    System.out.println("--- Maybe Prisms: Safe Optional Value Extraction ---");

    Prism<Maybe<String>, String> justPrism = Prisms.just();

    // Extract from Just
    Maybe<String> present = Maybe.just("Hello, World!");
    Optional<String> extracted = justPrism.getOptional(present);
    System.out.println("Extract from Just: " + extracted.orElse("N/A"));

    // Returns empty for Nothing
    Maybe<String> absent = Maybe.nothing();
    Optional<String> noMatch = justPrism.getOptional(absent);
    System.out.println("Extract from Nothing: " + noMatch.orElse("N/A"));

    // Build wraps value in Maybe.just()
    Maybe<String> built = justPrism.build("Functional Java");
    System.out.println("Built Maybe: " + built);

    // Practical use: Extracting the customer from an API response
    CustomerLookup response =
        new CustomerLookup(
            "req-123",
            Maybe.just(new Customer("Ada", new EmailAddress("ada@example.com"))),
            List.of());
    Prism<Maybe<Customer>, Customer> customerPrism = Prisms.just();
    Optional<Customer> customer = customerPrism.getOptional(response.customer());
    System.out.println(
        "Customer from response: "
            + customer.map(c -> c.name() + " (" + c.email().value() + ")").orElse("No customer"));

    System.out.println();
  }

  private static void demonstrateEitherPrisms() {
    System.out.println("--- Either Prisms: Working with Success/Failure Cases ---");

    Prism<Either<String, Integer>, String> leftPrism = Prisms.left();
    Prism<Either<String, Integer>, Integer> rightPrism = Prisms.right();

    // Simulating API responses that can fail
    Either<String, Integer> successResponse = Either.right(200);
    Either<String, Integer> errorResponse = Either.left("Not Found");

    // Extract from Right (success)
    System.out.println("Success status: " + rightPrism.getOptional(successResponse).orElse(-1));
    System.out.println("Error message: " + leftPrism.getOptional(successResponse).orElse("None"));

    // Extract from Left (error)
    System.out.println("Success status: " + rightPrism.getOptional(errorResponse).orElse(-1));
    System.out.println("Error message: " + leftPrism.getOptional(errorResponse).orElse("None"));

    // Practical use: Processing a batch of Either results
    List<Either<String, Customer>> results =
        List.of(
            Either.right(new Customer("Ada", new EmailAddress("ada@example.com"))),
            Either.left("Invalid email format"),
            Either.right(new Customer("Grace", new EmailAddress("grace@example.com"))));

    Prism<Either<String, Customer>, Customer> customerRightPrism = Prisms.right();
    long successCount =
        results.stream().filter(r -> customerRightPrism.getOptional(r).isPresent()).count();
    System.out.println(
        "Successful customer sign-ups: " + successCount + " out of " + results.size());

    System.out.println();
  }

  private static void demonstrateValidatedPrisms() {
    System.out.println("--- Validated Prisms: Extracting Valid/Invalid Cases ---");

    Prism<Validated<String, Integer>, Integer> validPrism = Prisms.valid();
    Prism<Validated<String, Integer>, String> invalidPrism = Prisms.invalid();

    // Valid case
    Validated<String, Integer> validAge = Validated.valid(30);
    System.out.println("Valid age: " + validPrism.getOptional(validAge).orElse(-1));
    System.out.println("Error: " + invalidPrism.getOptional(validAge).orElse("None"));

    // Invalid case
    Validated<String, Integer> invalidAge = Validated.invalid("Age must be positive");
    System.out.println("Valid age: " + validPrism.getOptional(invalidAge).orElse(-1));
    System.out.println("Error: " + invalidPrism.getOptional(invalidAge).orElse("None"));

    // Practical use: Form validation results
    List<Validated<String, Customer>> validationResults =
        List.of(
            Validated.valid(new Customer("Ada", new EmailAddress("ada@example.com"))),
            Validated.invalid("Email already exists"),
            Validated.invalid("Age must be at least 18"),
            Validated.valid(new Customer("Grace", new EmailAddress("grace@example.com"))));

    Prism<Validated<String, Customer>, Customer> customerValidPrism = Prisms.valid();
    Prism<Validated<String, Customer>, String> errorPrism = Prisms.invalid();

    List<Customer> validCustomers =
        validationResults.stream()
            .flatMap(v -> customerValidPrism.getOptional(v).stream())
            .toList();
    List<String> errors =
        validationResults.stream().flatMap(v -> errorPrism.getOptional(v).stream()).toList();

    System.out.println("Valid customers: " + validCustomers.size());
    System.out.println("Validation errors: " + errors);

    System.out.println();
  }

  private static void demonstrateTryPrisms() {
    System.out.println("--- Try Prisms: Safe Exception Handling ---");

    Prism<Try<Integer>, Integer> successPrism = Prisms.success();
    Prism<Try<Integer>, Throwable> failurePrism = Prisms.failure();

    // Success case
    Try<Integer> successResult = Try.success(42);
    System.out.println("Success value: " + successPrism.getOptional(successResult).orElse(-1));
    System.out.println(
        "Exception: "
            + failurePrism.getOptional(successResult).map(Throwable::getMessage).orElse("None"));

    // Failure case
    Try<Integer> failureResult = Try.failure(new IllegalArgumentException("Invalid input"));
    System.out.println("Success value: " + successPrism.getOptional(failureResult).orElse(-1));
    System.out.println(
        "Exception: "
            + failurePrism.getOptional(failureResult).map(Throwable::getMessage).orElse("None"));

    // Practical use: Database operations
    Prism<Try<Customer>, Customer> customerSuccessPrism = Prisms.success();
    Try<Customer> dbResult = Try.of(() -> fetchCustomerFromDatabase("grace@example.com"));
    System.out.println(
        "Customer loaded: "
            + customerSuccessPrism
                .getOptional(dbResult)
                .map(Customer::name)
                .orElse("Failed to load"));

    System.out.println();
  }

  private static void demonstrateMaybeTraversals() {
    System.out.println("--- Maybe Traversals: Modifying Values Inside Maybe ---");

    Traversal<Maybe<String>, String> justTraversal = MaybeTraversals.just();

    // Modify value in Just
    Maybe<String> present = Maybe.just("hello");
    Maybe<String> uppercase = Traversals.modify(justTraversal, String::toUpperCase, present);
    System.out.println("Modified Maybe: " + uppercase);

    // No effect on Nothing
    Maybe<String> absent = Maybe.nothing();
    Maybe<String> unchanged = Traversals.modify(justTraversal, String::toUpperCase, absent);
    System.out.println("Unchanged Nothing: " + unchanged);

    // Practical use: Updating the customer's email if present
    CustomerLookup response =
        new CustomerLookup(
            "req-456",
            Maybe.just(new Customer("Ada", new EmailAddress("ada@example.com"))),
            List.of());
    Traversal<Maybe<Customer>, Customer> customerTraversal = MaybeTraversals.just();

    // Note: This would require composing with a lens to actually modify the email field
    System.out.println("Original customer: " + response.customer());

    System.out.println();
  }

  private static void demonstrateEitherTraversals() {
    System.out.println("--- Either Traversals: Modifying Right/Left Cases ---");

    Traversal<Either<String, Integer>, Integer> rightTraversal = EitherTraversals.right();
    Traversal<Either<String, Integer>, String> leftTraversal = EitherTraversals.left();

    // Modify Right value
    Either<String, Integer> success = Either.right(100);
    Either<String, Integer> doubled = Traversals.modify(rightTraversal, n -> n * 2, success);
    System.out.println("Doubled Right: " + doubled);

    // Modify Left value (error enrichment)
    Either<String, Integer> error = Either.left("Connection failed");
    Either<String, Integer> enriched =
        Traversals.modify(leftTraversal, msg -> "[ERROR] " + msg, error);
    System.out.println("Enriched Left: " + enriched);

    System.out.println();
  }

  private static void demonstrateComposition() {
    System.out.println("--- Composition: Combining Prisms with Other Optics ---");

    // Scenario: Extract and modify the customer's email from an API response
    CustomerLookup response =
        new CustomerLookup(
            "req-789",
            Maybe.just(new Customer("Ada", new EmailAddress("ada@example.com"))),
            List.of("Deprecated API"));

    // Compose: CustomerLookup -> Maybe<Customer> -> Customer -> email
    Prism<Maybe<Customer>, Customer> customerPrism = Prisms.just();

    // Get the customer if present
    Optional<Customer> customer = customerPrism.getOptional(response.customer());
    System.out.println(
        "Customer email: " + customer.map(c -> c.email().value()).orElse("No customer found"));

    // In a real scenario, you'd compose with CustomerLenses.email() to create a full path:
    // var emailPath =
    //     CustomerLookupLenses.customer()
    //         .andThen(MaybeTraversals.just())
    //         .andThen(CustomerLenses.email());

    System.out.println();
  }

  private static void demonstrateBeforeAfterComparison() {
    System.out.println("--- Before/After: The Power of Prisms ---\n");

    Maybe<Customer> maybeCustomer =
        Maybe.just(new Customer("Ada", new EmailAddress("ada@example.com")));

    System.out.println("❌ Traditional Java approach:");
    // Verbose null checking and pattern matching
    if (maybeCustomer.isJust()) {
      Customer customer = maybeCustomer.get();
      String email = customer.email().value();
      System.out.println("  Email: " + email);
    } else {
      System.out.println("  Email: No customer found");
    }

    System.out.println("\n✅ With Prisms:");
    // Elegant, composable, functional
    Prism<Maybe<Customer>, Customer> customerPrism = Prisms.just();
    String email =
        customerPrism
            .getOptional(maybeCustomer)
            .map(c -> c.email().value())
            .orElse("No customer found");
    System.out.println("  Email: " + email);

    System.out.println("\n" + "=".repeat(60));
    System.out.println("\n❌ Traditional Try handling:");
    Try<Customer> tryCustomer = Try.of(() -> fetchCustomerFromDatabase("grace@example.com"));
    if (tryCustomer.isSuccess()) {
      System.out.println(
          "  Customer: "
              + tryCustomer.foldFailureFirst(ex -> "Failed: " + ex.getMessage(), c -> c.name()));
    } else {
      System.out.println(
          "  Error: " + tryCustomer.foldFailureFirst(ex -> ex.getMessage(), c -> ""));
    }

    System.out.println("\n✅ With Prisms:");
    Prism<Try<Customer>, Customer> successPrism = Prisms.success();
    Prism<Try<Customer>, Throwable> failurePrism = Prisms.failure();
    System.out.println(
        "  Customer: "
            + successPrism.getOptional(tryCustomer).map(Customer::name).orElse("Failed to load"));
    System.out.println(
        "  Error: "
            + failurePrism.getOptional(tryCustomer).map(Throwable::getMessage).orElse("None"));

    System.out.println();
  }

  // Helper method to simulate database access
  private static Customer fetchCustomerFromDatabase(String email) {
    // Simulated database fetch
    return new Customer("Grace", new EmailAddress(email));
  }
}
