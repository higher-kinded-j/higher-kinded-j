// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedMonad;

/**
 * Demonstrates form validation using Applicative patterns with {@link Either} and {@link
 * Validated}.
 *
 * <p>This example shows two fundamental patterns for validation:
 *
 * <ul>
 *   <li><b>Fail-Fast with {@link Either}:</b> Stops at the first validation error (useful for
 *       sequential checks)
 *   <li><b>Accumulating with {@link Validated}:</b> Collects all validation errors (best for form
 *       validation)
 * </ul>
 *
 * <h2>When to Use Each Approach</h2>
 *
 * <table>
 *   <caption>Validation Strategy Decision Matrix</caption>
 *   <tr><th>Use Case</th><th>Type</th><th>Reason</th></tr>
 *   <tr>
 *     <td>Form validation</td>
 *     <td>{@link Validated}</td>
 *     <td>Show user all errors at once</td>
 *   </tr>
 *   <tr>
 *     <td>Pipeline validation</td>
 *     <td>{@link Either}</td>
 *     <td>Stop early to avoid wasted processing</td>
 *   </tr>
 *   <tr>
 *     <td>Database constraints</td>
 *     <td>{@link Validated}</td>
 *     <td>Batch check all constraints together</td>
 *   </tr>
 *   <tr>
 *     <td>API authentication</td>
 *     <td>{@link Either}</td>
 *     <td>No point checking further if auth fails</td>
 *   </tr>
 * </table>
 *
 * <h2>Related Tutorials</h2>
 *
 * <ul>
 *   <li>Core Types Tutorial 03: Applicative Combining
 *   <li>Core Types Tutorial 06: Concrete Types
 *   <li>Core Types Tutorial 07: Real World
 * </ul>
 *
 * @see <a href="https://higher-kinded-j.github.io/functional/applicative.html">Applicative
 *     Guide</a>
 * @see <a href="https://higher-kinded-j.github.io/monads/validated_monad.html">Validated Monad</a>
 */
public final class ApplicativeValidation {

  private ApplicativeValidation() {
    // Utility class - no instantiation
  }

  // ============================================================================
  // Domain Model
  // ============================================================================

  /** Represents a user registration form with multiple validated fields. */
  record UserRegistration(String username, String email, int age, String password) {}

  /** Validation errors accumulated across multiple fields. */
  record ValidationErrors(List<String> errors) {
    @Override
    public String toString() {
      return String.join(", ", errors);
    }
  }

  // ============================================================================
  // Individual Field Validators
  // ============================================================================

  /**
   * Validates a username meets requirements.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Between 3 and 20 characters
   *   <li>Alphanumeric only (no special characters)
   * </ul>
   */
  private static final Function<String, Validated<String, String>> validateUsername =
      username -> {
        if (username == null || username.isBlank()) {
          return Validated.invalid("Username is required");
        }
        if (username.length() < 3 || username.length() > 20) {
          return Validated.invalid("Username must be 3-20 characters");
        }
        if (!username.matches("^[a-zA-Z0-9]+$")) {
          return Validated.invalid("Username must be alphanumeric");
        }
        return Validated.valid(username);
      };

  /**
   * Validates an email address.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Must contain '@'
   *   <li>Must contain a domain (text after '@')
   * </ul>
   *
   * <p><b>Note:</b> This is a simplified validation. Production systems should use more robust
   * email validation (e.g., Apache Commons Validator, regex with RFC 5322).
   */
  private static final Function<String, Validated<String, String>> validateEmail =
      email -> {
        if (email == null || email.isBlank()) {
          return Validated.invalid("Email is required");
        }
        if (!email.contains("@")) {
          return Validated.invalid("Email must contain @");
        }
        String[] parts = email.split("@");
        if (parts.length != 2 || parts[1].isEmpty()) {
          return Validated.invalid("Email must have a domain");
        }
        return Validated.valid(email);
      };

  /**
   * Validates an age is appropriate.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Must be 18 or older
   *   <li>Must be 120 or younger (reasonable upper bound)
   * </ul>
   */
  private static final Function<Integer, Validated<String, Integer>> validateAge =
      age -> {
        if (age < 18) {
          return Validated.invalid("Must be 18 or older");
        }
        if (age > 120) {
          return Validated.invalid("Age must be 120 or younger");
        }
        return Validated.valid(age);
      };

  /**
   * Validates a password meets security requirements.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>At least 8 characters
   *   <li>Contains at least one digit
   * </ul>
   *
   * <p><b>Production Note:</b> Real password validation should check for uppercase, lowercase,
   * special characters, and common password lists. Consider using a dedicated library like OWASP
   * Java HTML Sanitizer.
   */
  private static final Function<String, Validated<String, String>> validatePassword =
      password -> {
        if (password == null || password.length() < 8) {
          return Validated.invalid("Password must be at least 8 characters");
        }
        if (!password.matches(".*\\d.*")) {
          return Validated.invalid("Password must contain at least one digit");
        }
        return Validated.valid(password);
      };

  // ============================================================================
  // Fail-Fast Validation with Either
  // ============================================================================

  /**
   * Validates a user registration using {@link Either} (fail-fast strategy).
   *
   * <p>This approach stops at the <b>first</b> validation error. It's efficient when subsequent
   * validations depend on earlier ones, or when you want to avoid unnecessary work.
   *
   * <p><b>Use Case:</b> Pipeline validation where each step must succeed before proceeding.
   *
   * @param username The username to validate
   * @param email The email to validate
   * @param age The age to validate
   * @param password The password to validate
   * @return {@link Either#right} with {@link UserRegistration} if all valid, {@link Either#left}
   *     with first error otherwise
   */
  public static Either<String, UserRegistration> validateUserFailFast(
      String username, String email, int age, String password) {

    // Convert Validated to Either (discarding error accumulation)
    Either<String, String> usernameEither = validateUsername.apply(username).toEither();

    Either<String, String> emailEither = validateEmail.apply(email).toEither();

    Either<String, Integer> ageEither = validateAge.apply(age).toEither();

    Either<String, String> passwordEither = validatePassword.apply(password).toEither();

    // Combine using EitherMonad (Tutorial 03 pattern)
    EitherMonad<String> monad = EitherMonad.instance();
    return EITHER.narrow(
        monad.map4(
            EITHER.widen(usernameEither),
            EITHER.widen(emailEither),
            EITHER.widen(ageEither),
            EITHER.widen(passwordEither),
            UserRegistration::new));
  }

  // ============================================================================
  // Accumulating Validation with Validated
  // ============================================================================

  /**
   * Validates a user registration using {@link Validated} (accumulating strategy).
   *
   * <p>This approach collects <b>all</b> validation errors before returning. Users see every
   * problem at once, leading to better UX in form validation scenarios.
   *
   * <p><b>Use Case:</b> Form submission where users should fix all errors in one go.
   *
   * @param username The username to validate
   * @param email The email to validate
   * @param age The age to validate
   * @param password The password to validate
   * @return {@link Validated#valid} with {@link UserRegistration} if all valid, {@link
   *     Validated#invalid} with all errors otherwise
   */
  public static Validated<String, UserRegistration> validateUserAccumulating(
      String username, String email, int age, String password) {

    Validated<String, String> usernameResult = validateUsername.apply(username);
    Validated<String, String> emailResult = validateEmail.apply(email);
    Validated<String, Integer> ageResult = validateAge.apply(age);
    Validated<String, String> passwordResult = validatePassword.apply(password);

    // Combine using ValidatedMonad with error accumulation (Tutorial 03, 06 pattern)
    // The Semigroup defines how to combine multiple errors
    Semigroup<String> errorCombiner = Semigroups.string(", ");
    ValidatedMonad<String> monad = ValidatedMonad.instance(errorCombiner);

    return VALIDATED.narrow(
        monad.map4(
            VALIDATED.widen(usernameResult),
            VALIDATED.widen(emailResult),
            VALIDATED.widen(ageResult),
            VALIDATED.widen(passwordResult),
            UserRegistration::new));
  }

  // ============================================================================
  // Demonstration
  // ============================================================================

  /**
   * Demonstrates both validation strategies with various scenarios.
   *
   * @param args Command line arguments (unused)
   */
  public static void main(String[] args) {
    System.out.println("=== Applicative Validation Examples ===\n");

    // Scenario 1: All valid
    System.out.println("1. Valid Registration:");
    demonstrateValidation("alice123", "alice@example.com", 25, "SecurePass1");

    // Scenario 2: Single error
    System.out.println("\n2. Single Validation Error:");
    demonstrateValidation("al", "alice@example.com", 25, "SecurePass1");

    // Scenario 3: Multiple errors (showcases difference)
    System.out.println("\n3. Multiple Validation Errors:");
    demonstrateValidation("al", "invalid-email", 15, "weak");

    System.out.println("\n=== Key Takeaway ===");
    System.out.println("Either: Fails fast (first error only) → efficient for pipelines");
    System.out.println("Validated: Accumulates errors (all errors) → better UX for forms");
  }

  private static void demonstrateValidation(
      String username, String email, int age, String password) {
    System.out.println("Input: " + username + ", " + email + ", " + age + ", " + password);

    // Try fail-fast validation
    Either<String, UserRegistration> failFast =
        validateUserFailFast(username, email, age, password);
    System.out.println(
        "  Fail-Fast (Either): "
            + (failFast.isRight()
                ? "✓ Valid - " + failFast.getRight()
                : "✗ Error - " + failFast.getLeft()));

    // Try accumulating validation
    Validated<String, UserRegistration> accumulating =
        validateUserAccumulating(username, email, age, password);
    System.out.println(
        "  Accumulating (Validated): "
            + (accumulating.isValid()
                ? "✓ Valid - " + accumulating.get()
                : "✗ Errors - " + accumulating.getError()));
  }
}
