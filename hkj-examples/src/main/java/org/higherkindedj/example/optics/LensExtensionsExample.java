// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.optics.extensions.LensExtensions.*;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A comprehensive example demonstrating {@link org.higherkindedj.optics.extensions.LensExtensions}
 * for safe lens operations with Higher-Kinded-J core types.
 *
 * <p>This example showcases how to combine lenses with {@link Maybe}, {@link Either}, {@link
 * Validated}, and {@link Try} to perform null-safe access and validated modifications without
 * cluttering business logic with defensive programming.
 *
 * <p><b>Scenario:</b> User profile management with comprehensive validation. We use lens extensions
 * to safely access and update user data whilst handling null values, validation errors, and
 * database exceptions in a functional manner.
 *
 * <p><b>Key Methods Demonstrated:</b>
 *
 * <ul>
 *   <li>{@code getMaybe} - Null-safe field access
 *   <li>{@code getEither} - Access with default error
 *   <li>{@code getValidated} - Access with validation error
 *   <li>{@code modifyMaybe} - Optional modifications
 *   <li>{@code modifyEither} - Modifications with fail-fast validation
 *   <li>{@code modifyValidated} - Modifications with validation
 *   <li>{@code modifyTry} - Exception-safe modifications
 *   <li>{@code setIfValid} - Conditional updates
 * </ul>
 *
 * @see org.higherkindedj.optics.extensions.LensExtensions
 */
public class LensExtensionsExample {

  // Domain model for user profile management
  @GenerateLenses
  record LEUserProfile(String userId, String name, String email, Integer age, String bio) {}

  @GenerateLenses
  record LEAddress(String street, String city, String postcode, String country) {}

  @GenerateLenses
  record LEUser(LEUserProfile profile, LEAddress address, String accountStatus) {}

  // Validation errors
  sealed interface ValidationError permits FieldError, BusinessRuleError {}

  record FieldError(String field, String message) implements ValidationError {
    @Override
    public String toString() {
      return field + ": " + message;
    }
  }

  record BusinessRuleError(String code, String message) implements ValidationError {
    @Override
    public String toString() {
      return "[" + code + "] " + message;
    }
  }

  public static void main(String[] args) {
    System.out.println("=== Lens Extensions Examples ===\n");

    demonstrateGetMaybe();
    demonstrateGetEither();
    demonstrateGetValidated();
    demonstrateModifyMaybe();
    demonstrateModifyEither();
    demonstrateModifyValidated();
    demonstrateModifyTry();
    demonstrateSetIfValid();
    demonstrateRealWorldScenario();
  }

  private static void demonstrateGetMaybe() {
    System.out.println("--- getMaybe: Null-Safe Field Access ---");

    Lens<LEUserProfile, String> bioLens = LEUserProfileLenses.bio();

    // Non-null field
    LEUserProfile withBio =
        new LEUserProfile("u1", "Alice", "alice@example.com", 30, "Software Engineer");
    Maybe<String> bio = getMaybe(bioLens, withBio);
    System.out.println("Bio (present): " + bio.orElse("No bio"));

    // Null field
    LEUserProfile withoutBio = new LEUserProfile("u2", "Bob", "bob@example.com", 25, null);
    Maybe<String> noBio = getMaybe(bioLens, withoutBio);
    System.out.println("Bio (absent): " + noBio.orElse("No bio"));

    System.out.println();
  }

  private static void demonstrateGetEither() {
    System.out.println("--- getEither: Access with Default Error ---");

    Lens<LEUserProfile, Integer> ageLens = LEUserProfileLenses.age();

    // Valid age
    LEUserProfile validProfile =
        new LEUserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");
    Either<String, Integer> age = getEither(ageLens, "Age not provided", validProfile);
    System.out.println("Age (valid): " + age.fold(error -> "Error: " + error, a -> "Age: " + a));

    // Null age
    LEUserProfile invalidProfile =
        new LEUserProfile("u2", "Bob", "bob@example.com", null, "Student");
    Either<String, Integer> noAge = getEither(ageLens, "Age not provided", invalidProfile);
    System.out.println(
        "Age (missing): " + noAge.fold(error -> "Error: " + error, a -> "Age: " + a));

    System.out.println();
  }

  private static void demonstrateGetValidated() {
    System.out.println("--- getValidated: Access with Validation Error ---");

    Lens<LEUserProfile, String> emailLens = LEUserProfileLenses.email();

    // Valid email
    LEUserProfile profile = new LEUserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");
    Validated<String, String> email = getValidated(emailLens, "Email is required", profile);
    System.out.println("Email: " + email.fold(error -> "Error: " + error, e -> "Email: " + e));

    // Null email
    LEUserProfile noEmail = new LEUserProfile("u2", "Bob", null, 25, "Student");
    Validated<String, String> missing = getValidated(emailLens, "Email is required", noEmail);
    System.out.println("Email: " + missing.fold(error -> "Error: " + error, e -> "Email: " + e));

    System.out.println();
  }

  private static void demonstrateModifyMaybe() {
    System.out.println("--- modifyMaybe: Optional Modifications ---");

    Lens<LEUserProfile, String> nameLens = LEUserProfileLenses.name();
    LEUserProfile profile = new LEUserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");

    // Successful modification
    Maybe<LEUserProfile> updated =
        modifyMaybe(
            nameLens,
            name -> name.length() >= 2 ? Maybe.just(name.toUpperCase()) : Maybe.nothing(),
            profile);
    System.out.println("Updated name: " + updated.map(p -> p.name()).orElse("Modification failed"));

    // Failed modification
    LEUserProfile shortName = new LEUserProfile("u2", "A", "a@example.com", 25, "Student");
    Maybe<LEUserProfile> failed =
        modifyMaybe(
            nameLens,
            name -> name.length() >= 2 ? Maybe.just(name.toUpperCase()) : Maybe.nothing(),
            shortName);
    System.out.println("Failed modification: " + failed.orElse(null));

    System.out.println();
  }

  private static void demonstrateModifyEither() {
    System.out.println("--- modifyEither: Fail-Fast Validation ---");

    Lens<LEUserProfile, Integer> ageLens = LEUserProfileLenses.age();
    LEUserProfile profile = new LEUserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");

    // Valid modification
    Either<String, LEUserProfile> updated =
        modifyEither(
            ageLens,
            age -> {
              if (age < 0) return Either.left("Age cannot be negative");
              if (age > 150) return Either.left("Age must be realistic");
              return Either.right(age + 1); // Birthday!
            },
            profile);
    System.out.println(
        "Updated age: " + updated.fold(error -> "Error: " + error, p -> "New age: " + p.age()));

    // Invalid modification
    LEUserProfile invalidAge =
        new LEUserProfile("u2", "Bob", "bob@example.com", 200, "Time traveller");
    Either<String, LEUserProfile> failed =
        modifyEither(
            ageLens,
            age -> {
              if (age < 0) return Either.left("Age cannot be negative");
              if (age > 150) return Either.left("Age must be realistic");
              return Either.right(age + 1);
            },
            invalidAge);
    System.out.println(
        "Failed modification: "
            + failed.fold(error -> "Error: " + error, p -> "New age: " + p.age()));

    System.out.println();
  }

  private static void demonstrateModifyValidated() {
    System.out.println("--- modifyValidated: Validated Modifications ---");

    Lens<LEUserProfile, String> emailLens = LEUserProfileLenses.email();
    LEUserProfile profile = new LEUserProfile("u1", "Alice", "old@example.com", 30, "Engineer");

    // Valid email format
    Validated<String, LEUserProfile> updated =
        modifyValidated(
            emailLens,
            email -> {
              if (!email.contains("@")) {
                return Validated.invalid("Email must contain @");
              }
              if (!email.endsWith(".com") && !email.endsWith(".co.uk")) {
                return Validated.invalid("Email must end with .com or .co.uk");
              }
              return Validated.valid(email.toLowerCase());
            },
            profile);
    System.out.println(
        "Updated email: "
            + updated.fold(error -> "Error: " + error, p -> "New email: " + p.email()));

    // Invalid email format
    LEUserProfile badEmail = new LEUserProfile("u2", "Bob", "invalid-email", 25, "Student");
    Validated<String, LEUserProfile> failed =
        modifyValidated(
            emailLens,
            email -> {
              if (!email.contains("@")) {
                return Validated.invalid("Email must contain @");
              }
              if (!email.endsWith(".com") && !email.endsWith(".co.uk")) {
                return Validated.invalid("Email must end with .com or .co.uk");
              }
              return Validated.valid(email.toLowerCase());
            },
            badEmail);
    System.out.println(
        "Failed modification: "
            + failed.fold(error -> "Error: " + error, p -> "New email: " + p.email()));

    System.out.println();
  }

  private static void demonstrateModifyTry() {
    System.out.println("--- modifyTry: Exception-Safe Modifications ---");

    Lens<LEUserProfile, String> emailLens = LEUserProfileLenses.email();
    LEUserProfile profile = new LEUserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");

    // Successful database update
    Try<LEUserProfile> updated =
        modifyTry(
            emailLens,
            email -> {
              // Simulate database call that might throw
              return Try.of(() -> updateEmailInDatabase(email));
            },
            profile);
    System.out.println(
        "Database update: "
            + updated.fold(p -> "Success: " + p.email(), ex -> "Failed: " + ex.getMessage()));

    // Failed database update
    LEUserProfile badEmail = new LEUserProfile("u2", "Bob", "fail@example.com", 25, "Student");
    Try<LEUserProfile> failed =
        modifyTry(
            emailLens,
            email -> {
              // Simulate database call that might throw
              return Try.of(() -> updateEmailInDatabase(email));
            },
            badEmail);
    System.out.println(
        "Database update: "
            + failed.fold(p -> "Success: " + p.email(), ex -> "Failed: " + ex.getMessage()));

    System.out.println();
  }

  private static void demonstrateSetIfValid() {
    System.out.println("--- setIfValid: Conditional Updates ---");

    Lens<LEUserProfile, String> nameLens = LEUserProfileLenses.name();
    LEUserProfile profile = new LEUserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");

    // Valid name format
    Either<String, LEUserProfile> updated =
        setIfValid(
            nameLens,
            name -> {
              if (name.length() < 2) {
                return Either.left("Name must be at least 2 characters");
              }
              if (!name.matches("[A-Z][a-z]+")) {
                return Either.left("Name must start with capital letter");
              }
              return Either.right(name);
            },
            "Robert",
            profile);
    System.out.println(
        "Updated name: " + updated.fold(error -> "Error: " + error, p -> "New name: " + p.name()));

    // Invalid name format
    Either<String, LEUserProfile> failed =
        setIfValid(
            nameLens,
            name -> {
              if (name.length() < 2) {
                return Either.left("Name must be at least 2 characters");
              }
              if (!name.matches("[A-Z][a-z]+")) {
                return Either.left("Name must start with capital letter");
              }
              return Either.right(name);
            },
            "bob123",
            profile);
    System.out.println(
        "Failed update: " + failed.fold(error -> "Error: " + error, p -> "New name: " + p.name()));

    System.out.println();
  }

  private static void demonstrateRealWorldScenario() {
    System.out.println("--- Real-World Scenario: User Profile Update Form ---\n");

    LEUserProfile original = new LEUserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");
    System.out.println("Original profile: " + original);

    // Scenario 1: Update email with validation
    System.out.println("\n📧 Updating email...");
    Lens<LEUserProfile, String> emailLens = LEUserProfileLenses.email();
    Either<String, LEUserProfile> emailUpdate =
        modifyEither(emailLens, email -> validateEmail(email).map(e -> e.toLowerCase()), original);

    emailUpdate.fold(
        error -> {
          System.out.println("  ❌ " + error);
          return null;
        },
        updated -> {
          System.out.println("  ✅ Email updated: " + updated.email());
          return null;
        });

    // Scenario 2: Update age with validation
    System.out.println("\n🎂 Celebrating birthday...");
    Lens<LEUserProfile, Integer> ageLens = LEUserProfileLenses.age();
    Either<String, LEUserProfile> ageUpdate =
        modifyEither(ageLens, age -> validateAge(age).map(a -> a + 1), original);

    ageUpdate.fold(
        error -> {
          System.out.println("  ❌ " + error);
          return null;
        },
        updated -> {
          System.out.println("  ✅ Happy birthday! New age: " + updated.age());
          return null;
        });

    // Scenario 3: Update bio (optional field)
    System.out.println("\n📝 Updating bio...");
    Lens<LEUserProfile, String> bioLens = LEUserProfileLenses.bio();
    Maybe<LEUserProfile> bioUpdate =
        modifyMaybe(
            bioLens,
            bio ->
                bio != null && bio.length() > 10 ? Maybe.just(bio.toUpperCase()) : Maybe.nothing(),
            original);

    System.out.println(
        bioUpdate.isJust()
            ? "  ✅ Bio updated: " + bioUpdate.get().bio()
            : "  ℹ️ Bio unchanged (too short or null)");

    System.out.println();
  }

  // Validation helpers
  private static Either<String, String> validateEmail(String email) {
    if (email == null || email.isEmpty()) {
      return Either.left("Email is required");
    }
    if (!email.contains("@")) {
      return Either.left("Email must contain @");
    }
    if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
      return Either.left("Email format is invalid");
    }
    return Either.right(email);
  }

  private static Either<String, Integer> validateAge(Integer age) {
    if (age == null) {
      return Either.left("Age is required");
    }
    if (age < 0) {
      return Either.left("Age cannot be negative");
    }
    if (age > 150) {
      return Either.left("Age must be realistic");
    }
    return Either.right(age);
  }

  // Simulate database operation
  private static String updateEmailInDatabase(String email) {
    if (email.contains("fail")) {
      throw new RuntimeException("Database connection failed");
    }
    return email; // Simulated successful update
  }
}
