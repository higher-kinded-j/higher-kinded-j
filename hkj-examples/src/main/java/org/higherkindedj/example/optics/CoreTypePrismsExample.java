// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;

/**
 * A comprehensive example demonstrating prisms and traversals for Higher-Kinded-J core types.
 *
 * <p>This example showcases how to work with {@link Maybe}, {@link Either}, {@link Validated}, and
 * {@link Try} using optics, providing a functional alternative to traditional pattern matching and
 * null checking.
 *
 * <p><b>Scenario:</b> Processing API responses that may contain data, errors, or validation
 * failures. We use optics to safely extract and transform these responses without verbose null
 * checks or try-catch blocks.
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

  // Domain model for API responses
  @GenerateLenses
  record User(String id, String name, String email, int age) {}

  @GenerateLenses
  record ApiResponse(String requestId, Maybe<User> data, List<String> warnings) {}

  sealed interface ValidationError permits FieldError, BusinessError {}

  record FieldError(String field, String message) implements ValidationError {}

  record BusinessError(String code, String message) implements ValidationError {}

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
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

    // Practical use: Extracting user data from API response
    ApiResponse response =
        new ApiResponse(
            "req-123", Maybe.just(new User("u1", "Alice", "alice@example.com", 30)), List.of());
    Prism<Maybe<User>, User> userPrism = Prisms.just();
    Optional<User> user = userPrism.getOptional(response.data());
    System.out.println(
        "User from response: "
            + user.map(u -> u.name() + " (" + u.email() + ")").orElse("No user"));

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
    List<Either<String, User>> results =
        List.of(
            Either.right(new User("u1", "Alice", "alice@example.com", 30)),
            Either.left("Invalid email format"),
            Either.right(new User("u2", "Bob", "bob@example.com", 25)));

    Prism<Either<String, User>, User> userRightPrism = Prisms.right();
    long successCount =
        results.stream().filter(r -> userRightPrism.getOptional(r).isPresent()).count();
    System.out.println("Successful user creations: " + successCount + " out of " + results.size());

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
    List<Validated<String, User>> validationResults =
        List.of(
            Validated.valid(new User("u1", "Alice", "alice@example.com", 30)),
            Validated.invalid("Email already exists"),
            Validated.invalid("Age must be at least 18"),
            Validated.valid(new User("u2", "Bob", "bob@example.com", 25)));

    Prism<Validated<String, User>, User> userValidPrism = Prisms.valid();
    Prism<Validated<String, User>, String> errorPrism = Prisms.invalid();

    List<User> validUsers =
        validationResults.stream().flatMap(v -> userValidPrism.getOptional(v).stream()).toList();
    List<String> errors =
        validationResults.stream().flatMap(v -> errorPrism.getOptional(v).stream()).toList();

    System.out.println("Valid users: " + validUsers.size());
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
    Prism<Try<User>, User> userSuccessPrism = Prisms.success();
    Try<User> dbResult = Try.of(() -> fetchUserFromDatabase("u1"));
    System.out.println(
        "User loaded: "
            + userSuccessPrism.getOptional(dbResult).map(u -> u.name()).orElse("Failed to load"));

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

    // Practical use: Updating user email if present
    ApiResponse response =
        new ApiResponse(
            "req-456", Maybe.just(new User("u1", "Alice", "alice@example.com", 30)), List.of());
    Traversal<Maybe<User>, User> userTraversal = MaybeTraversals.just();

    // Note: This would require composing with a lens to actually modify the email field
    System.out.println("Original user: " + response.data());

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

    // Scenario: Extract and modify user email from an API response
    ApiResponse response =
        new ApiResponse(
            "req-789",
            Maybe.just(new User("u1", "Alice", "alice@example.com", 30)),
            List.of("Deprecated API"));

    // Compose: ApiResponse -> Maybe<User> -> User -> email
    Prism<Maybe<User>, User> userPrism = Prisms.just();

    // Get the user if present
    Optional<User> user = userPrism.getOptional(response.data());
    System.out.println("User email: " + user.map(User::email).orElse("No user found"));

    // In a real scenario, you'd compose with UserLenses.email() to create a full path:
    // var emailPath =
    // ApiResponseLenses.data().andThen(MaybeTraversals.just()).andThen(UserLenses.email());

    System.out.println();
  }

  private static void demonstrateBeforeAfterComparison() {
    System.out.println("--- Before/After: The Power of Prisms ---\n");

    Maybe<User> maybeUser = Maybe.just(new User("u1", "Alice", "alice@example.com", 30));

    System.out.println("❌ Traditional Java approach:");
    // Verbose null checking and pattern matching
    if (maybeUser.isJust()) {
      User user = maybeUser.get();
      String email = user.email();
      System.out.println("  Email: " + email);
    } else {
      System.out.println("  Email: No user found");
    }

    System.out.println("\n✅ With Prisms:");
    // Elegant, composable, functional
    Prism<Maybe<User>, User> userPrism = Prisms.just();
    String email = userPrism.getOptional(maybeUser).map(User::email).orElse("No user found");
    System.out.println("  Email: " + email);

    System.out.println("\n" + "=".repeat(60));
    System.out.println("\n❌ Traditional Try handling:");
    Try<User> tryUser = Try.of(() -> fetchUserFromDatabase("u1"));
    if (tryUser.isSuccess()) {
      System.out.println(
          "  User: " + tryUser.fold(u -> u.name(), ex -> "Failed: " + ex.getMessage()));
    } else {
      System.out.println("  Error: " + tryUser.fold(u -> "", ex -> ex.getMessage()));
    }

    System.out.println("\n✅ With Prisms:");
    Prism<Try<User>, User> successPrism = Prisms.success();
    Prism<Try<User>, Throwable> failurePrism = Prisms.failure();
    System.out.println(
        "  User: " + successPrism.getOptional(tryUser).map(User::name).orElse("Failed to load"));
    System.out.println(
        "  Error: " + failurePrism.getOptional(tryUser).map(Throwable::getMessage).orElse("None"));

    System.out.println();
  }

  // Helper method to simulate database access
  private static User fetchUserFromDatabase(String id) {
    // Simulated database fetch
    return new User(id, "Database User", "db@example.com", 25);
  }
}
