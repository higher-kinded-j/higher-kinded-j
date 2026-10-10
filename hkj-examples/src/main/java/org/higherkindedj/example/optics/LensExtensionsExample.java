// Copyright (c) 2025 - 2026 Magnus Smith
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
 * <p><b>Scenario:</b> Managing the order service's affiliates, the partners paid a commission on
 * the orders they refer. We use lens extensions to safely access and update an affiliate whilst
 * handling null values, validation errors, and database exceptions in a functional manner.
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

  // The Optics Extensions page's Affiliate, in its shape: a partner paid a commission, in percent,
  // on the orders they refer. A newcomer may not yet have given an email, agreed a commission or
  // written a bio, so those fields may be null.
  @GenerateLenses
  record Affiliate(String id, String name, String email, Integer commission, String bio) {}

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

    Lens<Affiliate, String> bioLens = AffiliateLenses.bio();

    // Non-null field
    Affiliate withBio = new Affiliate("a1", "Alice", "alice@example.com", 10, "Lighting blogger");
    Maybe<String> bio = getMaybe(bioLens, withBio);
    System.out.println("Bio (present): " + bio.orElse("No bio"));

    // Null field
    Affiliate withoutBio = new Affiliate("a2", "Bob", "bob@example.com", 5, null);
    Maybe<String> noBio = getMaybe(bioLens, withoutBio);
    System.out.println("Bio (absent): " + noBio.orElse("No bio"));

    System.out.println();
  }

  private static void demonstrateGetEither() {
    System.out.println("--- getEither: Access with Default Error ---");

    Lens<Affiliate, Integer> commissionLens = AffiliateLenses.commission();

    // Agreed commission
    Affiliate agreed = new Affiliate("a1", "Alice", "alice@example.com", 10, "Blogger");
    Either<String, Integer> commission = getEither(commissionLens, "Commission not agreed", agreed);
    System.out.println(
        "Commission (agreed): "
            + commission.fold(error -> "Error: " + error, c -> "Commission: " + c + "%"));

    // Null commission
    Affiliate newcomer = new Affiliate("a2", "Bob", "bob@example.com", null, "Reviewer");
    Either<String, Integer> noCommission =
        getEither(commissionLens, "Commission not agreed", newcomer);
    System.out.println(
        "Commission (missing): "
            + noCommission.fold(error -> "Error: " + error, c -> "Commission: " + c + "%"));

    System.out.println();
  }

  private static void demonstrateGetValidated() {
    System.out.println("--- getValidated: Access with Validation Error ---");

    Lens<Affiliate, String> emailLens = AffiliateLenses.email();

    // Valid email
    Affiliate affiliate = new Affiliate("a1", "Alice", "alice@example.com", 10, "Blogger");
    Validated<String, String> email = getValidated(emailLens, "Email is required", affiliate);
    System.out.println("Email: " + email.fold(error -> "Error: " + error, e -> "Email: " + e));

    // Null email
    Affiliate noEmail = new Affiliate("a2", "Bob", null, 5, "Reviewer");
    Validated<String, String> missing = getValidated(emailLens, "Email is required", noEmail);
    System.out.println("Email: " + missing.fold(error -> "Error: " + error, e -> "Email: " + e));

    System.out.println();
  }

  private static void demonstrateModifyMaybe() {
    System.out.println("--- modifyMaybe: Optional Modifications ---");

    Lens<Affiliate, String> nameLens = AffiliateLenses.name();
    Affiliate affiliate = new Affiliate("a1", "Alice", "alice@example.com", 10, "Blogger");

    // Successful modification
    Maybe<Affiliate> updated =
        modifyMaybe(
            nameLens,
            name -> name.length() >= 2 ? Maybe.just(name.toUpperCase()) : Maybe.nothing(),
            affiliate);
    System.out.println("Updated name: " + updated.map(a -> a.name()).orElse("Modification failed"));

    // Failed modification
    Affiliate shortName = new Affiliate("a2", "A", "a@example.com", 5, "Reviewer");
    Maybe<Affiliate> failed =
        modifyMaybe(
            nameLens,
            name -> name.length() >= 2 ? Maybe.just(name.toUpperCase()) : Maybe.nothing(),
            shortName);
    System.out.println("Failed modification: " + failed.orElse(null));

    System.out.println();
  }

  private static void demonstrateModifyEither() {
    System.out.println("--- modifyEither: Fail-Fast Validation ---");

    Lens<Affiliate, Integer> commissionLens = AffiliateLenses.commission();
    Affiliate affiliate = new Affiliate("a1", "Alice", "alice@example.com", 10, "Blogger");

    // Valid modification
    Either<String, Affiliate> updated =
        modifyEither(
            commissionLens,
            commission -> {
              if (commission < 0) return Either.left("Commission cannot be negative");
              if (commission > 50) return Either.left("Commission must be realistic");
              return Either.right(commission + 1); // A raise!
            },
            affiliate);
    System.out.println(
        "Updated commission: "
            + updated.fold(
                error -> "Error: " + error, a -> "New commission: " + a.commission() + "%"));

    // Invalid modification
    Affiliate overpaid = new Affiliate("a2", "Bob", "bob@example.com", 80, "Deal hunter");
    Either<String, Affiliate> failed =
        modifyEither(
            commissionLens,
            commission -> {
              if (commission < 0) return Either.left("Commission cannot be negative");
              if (commission > 50) return Either.left("Commission must be realistic");
              return Either.right(commission + 1);
            },
            overpaid);
    System.out.println(
        "Failed modification: "
            + failed.fold(
                error -> "Error: " + error, a -> "New commission: " + a.commission() + "%"));

    System.out.println();
  }

  private static void demonstrateModifyValidated() {
    System.out.println("--- modifyValidated: Validated Modifications ---");

    Lens<Affiliate, String> emailLens = AffiliateLenses.email();
    Affiliate affiliate = new Affiliate("a1", "Alice", "old@example.com", 10, "Blogger");

    // Valid email format
    Validated<String, Affiliate> updated =
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
            affiliate);
    System.out.println(
        "Updated email: "
            + updated.fold(error -> "Error: " + error, a -> "New email: " + a.email()));

    // Invalid email format
    Affiliate badEmail = new Affiliate("a2", "Bob", "invalid-email", 5, "Reviewer");
    Validated<String, Affiliate> failed =
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
            + failed.fold(error -> "Error: " + error, a -> "New email: " + a.email()));

    System.out.println();
  }

  private static void demonstrateModifyTry() {
    System.out.println("--- modifyTry: Exception-Safe Modifications ---");

    Lens<Affiliate, String> emailLens = AffiliateLenses.email();
    Affiliate affiliate = new Affiliate("a1", "Alice", "alice@example.com", 10, "Blogger");

    // Successful database update
    Try<Affiliate> updated =
        modifyTry(
            emailLens,
            email -> {
              // Simulate database call that might throw
              return Try.of(() -> updateEmailInDatabase(email));
            },
            affiliate);
    System.out.println(
        "Database update: "
            + updated.foldFailureFirst(
                ex -> "Failed: " + ex.getMessage(), a -> "Success: " + a.email()));

    // Failed database update
    Affiliate badEmail = new Affiliate("a2", "Bob", "fail@example.com", 5, "Reviewer");
    Try<Affiliate> failed =
        modifyTry(
            emailLens,
            email -> {
              // Simulate database call that might throw
              return Try.of(() -> updateEmailInDatabase(email));
            },
            badEmail);
    System.out.println(
        "Database update: "
            + failed.foldFailureFirst(
                ex -> "Failed: " + ex.getMessage(), a -> "Success: " + a.email()));

    System.out.println();
  }

  private static void demonstrateSetIfValid() {
    System.out.println("--- setIfValid: Conditional Updates ---");

    Lens<Affiliate, String> nameLens = AffiliateLenses.name();
    Affiliate affiliate = new Affiliate("a1", "Alice", "alice@example.com", 10, "Blogger");

    // Valid name format
    Either<String, Affiliate> updated =
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
            affiliate);
    System.out.println(
        "Updated name: " + updated.fold(error -> "Error: " + error, a -> "New name: " + a.name()));

    // Invalid name format
    Either<String, Affiliate> failed =
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
            affiliate);
    System.out.println(
        "Failed update: " + failed.fold(error -> "Error: " + error, a -> "New name: " + a.name()));

    System.out.println();
  }

  private static void demonstrateRealWorldScenario() {
    System.out.println("--- Real-World Scenario: Affiliate Update Form ---\n");

    Affiliate original = new Affiliate("a1", "Alice", "alice@example.com", 10, "Blogger");
    System.out.println("Original affiliate: " + original);

    // Scenario 1: Update email with validation
    System.out.println("\n📧 Updating email...");
    Lens<Affiliate, String> emailLens = AffiliateLenses.email();
    Either<String, Affiliate> emailUpdate =
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

    // Scenario 2: Update commission with validation
    System.out.println("\n💷 Giving a raise...");
    Lens<Affiliate, Integer> commissionLens = AffiliateLenses.commission();
    Either<String, Affiliate> commissionUpdate =
        modifyEither(commissionLens, c -> validateCommission(c).map(valid -> valid + 1), original);

    commissionUpdate.fold(
        error -> {
          System.out.println("  ❌ " + error);
          return null;
        },
        updated -> {
          System.out.println("  ✅ Commission raised: " + updated.commission() + "%");
          return null;
        });

    // Scenario 3: Update bio (optional field)
    System.out.println("\n📝 Updating bio...");
    Lens<Affiliate, String> bioLens = AffiliateLenses.bio();
    Maybe<Affiliate> bioUpdate =
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

  private static Either<String, Integer> validateCommission(Integer commission) {
    if (commission == null) {
      return Either.left("Commission is required");
    }
    if (commission < 0) {
      return Either.left("Commission cannot be negative");
    }
    if (commission > 50) {
      return Either.left("Commission must be realistic");
    }
    return Either.right(commission);
  }

  // Simulate database operation
  private static String updateEmailInDatabase(String email) {
    if (email.contains("fail")) {
      throw new RuntimeException("Database connection failed");
    }
    return email; // Simulated successful update
  }
}
