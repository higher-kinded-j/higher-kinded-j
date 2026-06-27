// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Examples demonstrating error-accumulating validation with ValidationPath, using a {@link
 * NonEmptyList} error channel.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Accumulating all validation errors using {@link ValidationPath}
 *   <li>The {@link NonEmptyList} error channel — an <em>invalid</em> result always has at least one
 *       error, so {@code getError().head()} is total
 *   <li>Difference between short-circuit (via) and accumulating (zipWithAccum) operations
 *   <li>Complex validation scenarios collecting multiple errors
 * </ul>
 *
 * <p>The {@code validNel} / {@code invalidNel} factories bake in {@link NonEmptyList#semigroup()},
 * so there is no {@code Semigroup} argument and no manual {@code List.of(error)} wrapping. The
 * older {@code Path.valid(value, Semigroups.list())} / {@code Path.invalid(List.of(error),
 * Semigroups.list())} channel still works unchanged if you prefer a plain {@code List}.
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.AccumulatingValidationExample}
 */
public class AccumulatingValidationExample {

  // Domain types
  record User(String name, String email, int age) {}

  record Address(String street, String city, String zipCode) {}

  record Registration(User user, Address address) {}

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Accumulating Validation ===\n");

    basicAccumulation();
    shortCircuitVsAccumulating();
    complexAccumulatingValidation();
    andAlsoPattern();
  }

  // ===== Basic Error Accumulation =====

  private static void basicAccumulation() {
    System.out.println("--- Basic Error Accumulation ---");

    // Valid case
    ValidationPath<NonEmptyList<String>, String> validName = validateName("Alice");
    ValidationPath<NonEmptyList<String>, String> validEmail = validateEmail("alice@example.com");
    ValidationPath<NonEmptyList<String>, Integer> validAge = validateAge(25);

    // zipWithAccum collects all errors (vs zipWith which short-circuits)
    ValidationPath<NonEmptyList<String>, User> validUser =
        validName.zipWith3Accum(validEmail, validAge, User::new);

    System.out.println("Valid user: " + validUser.run());
    // Valid[User[name=Alice, email=alice@example.com, age=25]]

    // Invalid case - ALL errors are collected
    ValidationPath<NonEmptyList<String>, String> invalidName = validateName("A"); // Too short
    ValidationPath<NonEmptyList<String>, String> invalidEmail =
        validateEmail("not-an-email"); // No @
    ValidationPath<NonEmptyList<String>, Integer> invalidAge = validateAge(-5); // Negative

    ValidationPath<NonEmptyList<String>, User> invalidUser =
        invalidName.zipWith3Accum(invalidEmail, invalidAge, User::new);

    System.out.println("Invalid user (all errors): " + invalidUser.run());
    // Invalid — three errors collected: [Name must be at least 2 characters,
    //   Email must contain @, Age cannot be negative]

    System.out.println();
  }

  // ===== Short-Circuit vs Accumulating =====

  private static void shortCircuitVsAccumulating() {
    System.out.println("--- Short-Circuit vs Accumulating ---");

    ValidationPath<NonEmptyList<String>, String> invalidName = validateName("A");
    ValidationPath<NonEmptyList<String>, String> invalidEmail = validateEmail("bad");
    ValidationPath<NonEmptyList<String>, Integer> invalidAge = validateAge(-1);

    // Using via (short-circuits on first error)
    ValidationPath<NonEmptyList<String>, String> shortCircuit =
        invalidName.via(
            n ->
                invalidEmail.via(
                    e -> invalidAge.map(a -> String.format("User: %s, %s, %d", n, e, a))));

    System.out.println("via (short-circuit): " + shortCircuit.run());
    // Invalid — only the first error: [Name must be at least 2 characters]

    // Using zipWithAccum (accumulates all errors)
    ValidationPath<NonEmptyList<String>, String> accumulated =
        invalidName.zipWith3Accum(
            invalidEmail, invalidAge, (n, e, a) -> String.format("User: %s, %s, %d", n, e, a));

    System.out.println("zipWithAccum (accumulate): " + accumulated.run());
    // Invalid — all three errors: [Name must be at least 2 characters,
    //   Email must contain @, Age cannot be negative]

    System.out.println();
  }

  // ===== Complex Accumulating Validation =====

  private static void complexAccumulatingValidation() {
    System.out.println("--- Complex Accumulating Validation ---");

    // Validate user with some invalid fields
    ValidationPath<NonEmptyList<String>, User> userValidation =
        validateName("X") // Invalid
            .zipWith3Accum(
                validateEmail("bob@company.com"), validateAge(200), User::new); // Age also invalid

    // Validate address with some invalid fields
    ValidationPath<NonEmptyList<String>, Address> addressValidation =
        validateStreet("") // Invalid
            .zipWith3Accum(
                validateCity("New York"),
                validateZipCode("ABCDE"),
                Address::new); // Zip also invalid

    // Combine all validations - ALL errors collected
    ValidationPath<NonEmptyList<String>, Registration> registrationValidation =
        userValidation.zipWithAccum(addressValidation, Registration::new);

    var result = registrationValidation.run();
    if (result.isValid()) {
      System.out.println("Registration successful: " + result.get());
    } else {
      // getError() is a NonEmptyList — size() and iteration are total, head() never throws
      System.out.println("Registration failed with " + result.getError().size() + " errors:");
      System.out.println("  (first error: " + result.getError().head() + ")");
      for (String error : result.getError()) {
        System.out.println("  - " + error);
      }
    }
    // Registration failed with 4 errors:
    //   - Name must be at least 2 characters
    //   - Age must be at most 150
    //   - Street cannot be empty
    //   - Zip code must be 5 digits

    System.out.println();
  }

  // ===== andAlso Pattern =====

  private static void andAlsoPattern() {
    System.out.println("--- andAlso Pattern (Run Both, Keep First) ---");

    // andAlso runs both validations but keeps the first value
    // Useful for "side-effect" validations

    ValidationPath<NonEmptyList<String>, String> name = validateName("Alice");
    ValidationPath<NonEmptyList<String>, String> email = validateEmail("not-email");

    // Keep name but also validate email
    ValidationPath<NonEmptyList<String>, String> result = name.andAlso(email);

    System.out.println("andAlso result: " + result.run());
    // Invalid — email error collected; name would be kept if valid

    // When both are valid
    ValidationPath<NonEmptyList<String>, String> validName = validateName("Bob");
    ValidationPath<NonEmptyList<String>, String> validEmail = validateEmail("bob@example.com");

    ValidationPath<NonEmptyList<String>, String> bothValid = validName.andAlso(validEmail);

    System.out.println("Both valid: " + bothValid.run());
    // Valid[Bob] - only the first value is kept

    // When both are invalid
    ValidationPath<NonEmptyList<String>, String> invalidName = validateName("X");
    ValidationPath<NonEmptyList<String>, String> invalidEmail = validateEmail("bad");

    ValidationPath<NonEmptyList<String>, String> bothInvalid = invalidName.andAlso(invalidEmail);

    System.out.println("Both invalid: " + bothInvalid.run());
    // Invalid — both errors collected, in order: [Name must be at least 2 characters,
    //   Email must contain @]

    System.out.println();
  }

  // ===== Validation Functions =====
  // Each single-error leaf wraps its error in a singleton NonEmptyList via invalidNel — no
  // Semigroup argument, no manual List.of(...) wrapping.

  private static ValidationPath<NonEmptyList<String>, String> validateName(String name) {
    if (name == null || name.isBlank()) {
      return Path.invalidNel("Name cannot be empty");
    }
    if (name.length() < 2) {
      return Path.invalidNel("Name must be at least 2 characters");
    }
    return Path.validNel(name);
  }

  private static ValidationPath<NonEmptyList<String>, String> validateEmail(String email) {
    if (email == null || email.isBlank()) {
      return Path.invalidNel("Email cannot be empty");
    }
    if (!email.contains("@")) {
      return Path.invalidNel("Email must contain @");
    }
    if (!email.contains(".")) {
      return Path.invalidNel("Email must contain a domain");
    }
    return Path.validNel(email);
  }

  private static ValidationPath<NonEmptyList<String>, Integer> validateAge(int age) {
    if (age < 0) {
      return Path.invalidNel("Age cannot be negative");
    }
    if (age > 150) {
      return Path.invalidNel("Age must be at most 150");
    }
    return Path.validNel(age);
  }

  private static ValidationPath<NonEmptyList<String>, String> validateStreet(String street) {
    if (street == null || street.isBlank()) {
      return Path.invalidNel("Street cannot be empty");
    }
    return Path.validNel(street);
  }

  private static ValidationPath<NonEmptyList<String>, String> validateCity(String city) {
    if (city == null || city.isBlank()) {
      return Path.invalidNel("City cannot be empty");
    }
    return Path.validNel(city);
  }

  private static ValidationPath<NonEmptyList<String>, String> validateZipCode(String zipCode) {
    if (zipCode == null || zipCode.isBlank()) {
      return Path.invalidNel("Zip code cannot be empty");
    }
    if (!zipCode.matches("\\d{5}")) {
      return Path.invalidNel("Zip code must be 5 digits");
    }
    return Path.validNel(zipCode);
  }
}
