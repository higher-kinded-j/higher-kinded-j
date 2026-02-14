// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.List;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;

/**
 * Examples demonstrating error-accumulating validation with ValidationPath.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Accumulating all validation errors using {@link ValidationPath}
 *   <li>Using {@link Semigroup} to combine errors
 *   <li>Difference between short-circuit (via) and accumulating (zipWithAccum) operations
 *   <li>Complex validation scenarios collecting multiple errors
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.AccumulatingValidationExample}
 */
public class AccumulatingValidationExample {

  // Domain types
  record User(String name, String email, int age) {}

  record Address(String street, String city, String zipCode) {}

  record Registration(User user, Address address) {}

  // Semigroup for combining error lists
  private static final Semigroup<List<String>> LIST_SEMIGROUP = Semigroups.list();

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
    ValidationPath<List<String>, String> validName = validateName("Alice");
    ValidationPath<List<String>, String> validEmail = validateEmail("alice@example.com");
    ValidationPath<List<String>, Integer> validAge = validateAge(25);

    // zipWithAccum collects all errors (vs zipWith which short-circuits)
    ValidationPath<List<String>, User> validUser =
        validName.zipWith3Accum(validEmail, validAge, User::new);

    System.out.println("Valid user: " + validUser.run());
    // Valid[User[name=Alice, email=alice@example.com, age=25]]

    // Invalid case - ALL errors are collected
    ValidationPath<List<String>, String> invalidName = validateName("A"); // Too short
    ValidationPath<List<String>, String> invalidEmail = validateEmail("not-an-email"); // No @
    ValidationPath<List<String>, Integer> invalidAge = validateAge(-5); // Negative

    ValidationPath<List<String>, User> invalidUser =
        invalidName.zipWith3Accum(invalidEmail, invalidAge, User::new);

    System.out.println("Invalid user (all errors): " + invalidUser.run());
    // Invalid[[Name must be at least 2 characters, Email must contain @, Age cannot be negative]]

    System.out.println();
  }

  // ===== Short-Circuit vs Accumulating =====

  private static void shortCircuitVsAccumulating() {
    System.out.println("--- Short-Circuit vs Accumulating ---");

    ValidationPath<List<String>, String> invalidName = validateName("A");
    ValidationPath<List<String>, String> invalidEmail = validateEmail("bad");
    ValidationPath<List<String>, Integer> invalidAge = validateAge(-1);

    // Using via (short-circuits on first error)
    ValidationPath<List<String>, String> shortCircuit =
        invalidName.via(
            n ->
                invalidEmail.via(
                    e -> invalidAge.map(a -> String.format("User: %s, %s, %d", n, e, a))));

    System.out.println("via (short-circuit): " + shortCircuit.run());
    // Invalid[[Name must be at least 2 characters]] - only first error

    // Using zipWithAccum (accumulates all errors)
    ValidationPath<List<String>, String> accumulated =
        invalidName.zipWith3Accum(
            invalidEmail, invalidAge, (n, e, a) -> String.format("User: %s, %s, %d", n, e, a));

    System.out.println("zipWithAccum (accumulate): " + accumulated.run());
    // Invalid[[Name must be at least 2 characters, Email must contain @, Age cannot be negative]]

    System.out.println();
  }

  // ===== Complex Accumulating Validation =====

  private static void complexAccumulatingValidation() {
    System.out.println("--- Complex Accumulating Validation ---");

    // Validate user with some invalid fields
    ValidationPath<List<String>, User> userValidation =
        validateName("X") // Invalid
            .zipWith3Accum(
                validateEmail("bob@company.com"), validateAge(200), User::new); // Age also invalid

    // Validate address with some invalid fields
    ValidationPath<List<String>, Address> addressValidation =
        validateStreet("") // Invalid
            .zipWith3Accum(
                validateCity("New York"),
                validateZipCode("ABCDE"),
                Address::new); // Zip also invalid

    // Combine all validations - ALL errors collected
    ValidationPath<List<String>, Registration> registrationValidation =
        userValidation.zipWithAccum(addressValidation, Registration::new);

    var result = registrationValidation.run();
    if (result.isValid()) {
      System.out.println("Registration successful: " + result.get());
    } else {
      System.out.println("Registration failed with " + result.getError().size() + " errors:");
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

    ValidationPath<List<String>, String> name = validateName("Alice");
    ValidationPath<List<String>, String> email = validateEmail("not-email");

    // Keep name but also validate email
    ValidationPath<List<String>, String> result = name.andAlso(email);

    System.out.println("andAlso result: " + result.run());
    // Invalid[[Email must contain @]] - email error collected, but name would be kept if valid

    // When both are valid
    ValidationPath<List<String>, String> validName = validateName("Bob");
    ValidationPath<List<String>, String> validEmail = validateEmail("bob@example.com");

    ValidationPath<List<String>, String> bothValid = validName.andAlso(validEmail);

    System.out.println("Both valid: " + bothValid.run());
    // Valid[Bob] - only the first value is kept

    // When both are invalid
    ValidationPath<List<String>, String> invalidName = validateName("X");
    ValidationPath<List<String>, String> invalidEmail = validateEmail("bad");

    ValidationPath<List<String>, String> bothInvalid = invalidName.andAlso(invalidEmail);

    System.out.println("Both invalid: " + bothInvalid.run());
    // Invalid[[Name must be at least 2 characters, Email must contain @]]

    System.out.println();
  }

  // ===== Validation Functions =====

  private static ValidationPath<List<String>, String> validateName(String name) {
    if (name == null || name.isBlank()) {
      return Path.invalid(List.of("Name cannot be empty"), LIST_SEMIGROUP);
    }
    if (name.length() < 2) {
      return Path.invalid(List.of("Name must be at least 2 characters"), LIST_SEMIGROUP);
    }
    return Path.valid(name, LIST_SEMIGROUP);
  }

  private static ValidationPath<List<String>, String> validateEmail(String email) {
    if (email == null || email.isBlank()) {
      return Path.invalid(List.of("Email cannot be empty"), LIST_SEMIGROUP);
    }
    if (!email.contains("@")) {
      return Path.invalid(List.of("Email must contain @"), LIST_SEMIGROUP);
    }
    if (!email.contains(".")) {
      return Path.invalid(List.of("Email must contain a domain"), LIST_SEMIGROUP);
    }
    return Path.valid(email, LIST_SEMIGROUP);
  }

  private static ValidationPath<List<String>, Integer> validateAge(int age) {
    if (age < 0) {
      return Path.invalid(List.of("Age cannot be negative"), LIST_SEMIGROUP);
    }
    if (age > 150) {
      return Path.invalid(List.of("Age must be at most 150"), LIST_SEMIGROUP);
    }
    return Path.valid(age, LIST_SEMIGROUP);
  }

  private static ValidationPath<List<String>, String> validateStreet(String street) {
    if (street == null || street.isBlank()) {
      return Path.invalid(List.of("Street cannot be empty"), LIST_SEMIGROUP);
    }
    return Path.valid(street, LIST_SEMIGROUP);
  }

  private static ValidationPath<List<String>, String> validateCity(String city) {
    if (city == null || city.isBlank()) {
      return Path.invalid(List.of("City cannot be empty"), LIST_SEMIGROUP);
    }
    return Path.valid(city, LIST_SEMIGROUP);
  }

  private static ValidationPath<List<String>, String> validateZipCode(String zipCode) {
    if (zipCode == null || zipCode.isBlank()) {
      return Path.invalid(List.of("Zip code cannot be empty"), LIST_SEMIGROUP);
    }
    if (!zipCode.matches("\\d{5}")) {
      return Path.invalid(List.of("Zip code must be 5 digits"), LIST_SEMIGROUP);
    }
    return Path.valid(zipCode, LIST_SEMIGROUP);
  }
}
