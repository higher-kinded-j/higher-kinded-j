// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Examples demonstrating validation pipelines with the Effect Path API.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Combining independent validations with {@code zipWith}
 *   <li>Fail-fast validation with MaybePath
 *   <li>Error-preserving validation with EitherPath
 *   <li>Complex validation scenarios
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ValidationPipelineExample}
 */
public class ValidationPipelineExample {

  // Domain types
  record User(String name, String email, int age) {}

  record Address(String street, String city, String zipCode) {}

  record Registration(User user, Address address) {}

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Validation Pipelines ===\n");

    maybeValidation();
    eitherValidation();
    complexValidation();
  }

  // ===== MaybePath Validation (fail-fast, no error messages) =====

  private static void maybeValidation() {
    System.out.println("--- MaybePath Validation (Fail-Fast) ---");

    // Independent validations combined with zipWith
    MaybePath<String> validName = validateNameM("Alice");
    MaybePath<String> validEmail = validateEmailM("alice@example.com");
    MaybePath<Integer> validAge = validateAgeM(25);

    // Combine all validations
    MaybePath<User> user = validName.zipWith3(validEmail, validAge, User::new);

    System.out.println("Valid user: " + user.run());
    // Just[User[name=Alice, email=alice@example.com, age=25]]

    // Invalid case - fails fast on first Nothing
    MaybePath<String> invalidName = validateNameM("A"); // Too short
    MaybePath<String> invalidEmail = validateEmailM("alice@example.com");
    MaybePath<Integer> invalidAge = validateAgeM(25);

    MaybePath<User> invalidUser = invalidName.zipWith3(invalidEmail, invalidAge, User::new);

    System.out.println("Invalid user (no error info): " + invalidUser.run()); // Nothing

    System.out.println();
  }

  private static MaybePath<String> validateNameM(String name) {
    return name != null && name.length() >= 2 ? Path.just(name) : Path.nothing();
  }

  private static MaybePath<String> validateEmailM(String email) {
    return email != null && email.contains("@") ? Path.just(email) : Path.nothing();
  }

  private static MaybePath<Integer> validateAgeM(int age) {
    return age >= 0 && age <= 150 ? Path.just(age) : Path.nothing();
  }

  // ===== EitherPath Validation (fail-fast with error messages) =====

  private static void eitherValidation() {
    System.out.println("--- EitherPath Validation (With Error Messages) ---");

    // Valid case
    EitherPath<String, String> name = validateNameE("Alice");
    EitherPath<String, String> email = validateEmailE("alice@example.com");
    EitherPath<String, Integer> age = validateAgeE(25);

    EitherPath<String, User> validUser = name.zipWith3(email, age, User::new);

    var validResult = validUser.run();
    if (validResult.isRight()) {
      System.out.println("Valid user: " + validResult.getRight());
    } else {
      System.out.println("Validation failed: " + validResult.getLeft());
    }

    // Invalid case - captures first error
    EitherPath<String, String> invalidEmail = validateEmailE("invalid-email");
    EitherPath<String, User> invalidUser = name.zipWith3(invalidEmail, age, User::new);

    var invalidResult = invalidUser.run();
    if (invalidResult.isRight()) {
      System.out.println("User: " + invalidResult.getRight());
    } else {
      System.out.println("Validation failed: " + invalidResult.getLeft());
    }

    System.out.println();
  }

  private static EitherPath<String, String> validateNameE(String name) {
    if (name == null || name.isBlank()) {
      return Path.left("Name cannot be empty");
    }
    if (name.length() < 2) {
      return Path.left("Name must be at least 2 characters");
    }
    return Path.right(name);
  }

  private static EitherPath<String, String> validateEmailE(String email) {
    if (email == null || email.isBlank()) {
      return Path.left("Email cannot be empty");
    }
    if (!email.contains("@")) {
      return Path.left("Email must contain @");
    }
    if (!email.contains(".")) {
      return Path.left("Email must contain a domain");
    }
    return Path.right(email);
  }

  private static EitherPath<String, Integer> validateAgeE(int age) {
    if (age < 0) {
      return Path.left("Age cannot be negative");
    }
    if (age > 150) {
      return Path.left("Age seems unrealistic");
    }
    return Path.right(age);
  }

  // ===== Complex Validation Scenario =====

  private static void complexValidation() {
    System.out.println("--- Complex Validation Scenario ---");

    // Validate user
    EitherPath<String, User> userValidation =
        validateNameE("Bob")
            .zipWith3(validateEmailE("bob@company.com"), validateAgeE(30), User::new);

    // Validate address
    EitherPath<String, Address> addressValidation =
        validateStreetE("123 Main St")
            .zipWith3(validateCityE("New York"), validateZipCodeE("10001"), Address::new);

    // Combine user and address validations
    EitherPath<String, Registration> registrationValidation =
        userValidation.zipWith(addressValidation, Registration::new);

    var regResult = registrationValidation.run();
    if (regResult.isRight()) {
      var reg = regResult.getRight();
      System.out.println("Registration successful!");
      System.out.println("  User: " + reg.user().name() + " (" + reg.user().email() + ")");
      System.out.println(
          "  Address: "
              + reg.address().street()
              + ", "
              + reg.address().city()
              + " "
              + reg.address().zipCode());
    } else {
      System.out.println("Registration failed: " + regResult.getLeft());
    }

    // Invalid registration
    System.out.println("\nInvalid registration attempt:");
    EitherPath<String, Registration> invalidReg =
        validateNameE("X") // Invalid - too short
            .zipWith3(validateEmailE("bob@company.com"), validateAgeE(30), User::new)
            .zipWith(
                validateStreetE("123 Main St")
                    .zipWith3(validateCityE("New York"), validateZipCodeE("10001"), Address::new),
                Registration::new);

    var invalidRegResult = invalidReg.run();
    if (invalidRegResult.isRight()) {
      System.out.println("Registration: " + invalidRegResult.getRight());
    } else {
      System.out.println("Registration failed: " + invalidRegResult.getLeft());
    }

    System.out.println();
  }

  private static EitherPath<String, String> validateStreetE(String street) {
    if (street == null || street.isBlank()) {
      return Path.left("Street cannot be empty");
    }
    return Path.right(street);
  }

  private static EitherPath<String, String> validateCityE(String city) {
    if (city == null || city.isBlank()) {
      return Path.left("City cannot be empty");
    }
    return Path.right(city);
  }

  private static EitherPath<String, String> validateZipCodeE(String zipCode) {
    if (zipCode == null || zipCode.isBlank()) {
      return Path.left("Zip code cannot be empty");
    }
    if (!zipCode.matches("\\d{5}")) {
      return Path.left("Zip code must be 5 digits");
    }
    return Path.right(zipCode);
  }
}
