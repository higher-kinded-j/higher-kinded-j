// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.traversal.optional;

import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Optional;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * A runnable example demonstrating the use of a generated Traversal for a field of type {@link
 * java.util.Optional}.
 */
public class OptionalTraversalExample {

  /**
   * An immutable Respondent record. A respondent must have an ID, but their email address is
   * optional. We use {@code @GenerateTraversals} to ask the annotation processor to create optics
   * for us.
   */
  @GenerateTraversals
  public record Respondent(int id, Optional<String> email) {}

  /**
   * An "effectful" function that validates an email address.
   *
   * <p>Instead of throwing an exception or returning null on failure, it returns a {@link
   * Validated} type. This makes the success and failure cases explicit and type-safe. The result is
   * "widened" to a {@code Kind} to be compatible with the Traverse interface.
   *
   * @param email The email string to validate.
   * @return A {@code Kind<Validated.Witness<String>, String>} which is either Valid(email) or
   *     Invalid("Error...").
   */
  public static Kind<ValidatedKind.Witness<String>, String> validateEmail(String email) {
    if (email != null && email.contains("@") && !email.endsWith("@")) {
      // Success case
      return VALIDATED.widen(Validated.valid(email));
    } else {
      // Failure case
      return VALIDATED.widen(Validated.invalid("Invalid email format"));
    }
  }

  public static void main(String[] args) {
    // 1. Setup: We need an Applicative instance for our effect type (Validated).
    // This tells the traversal how to combine results.
    final Semigroup<String> stringSemigroup = (s1, s2) -> s1 + "; " + s2;
    Applicative<ValidatedKind.Witness<String>> validatedApplicative =
        Instances.validated(stringSemigroup);

    // 2. Get the generated traversal for the 'email' field.
    // The annotation processor creates the `RespondentTraversals` class and the `email()` method.
    var emailTraversal = RespondentTraversals.email();

    System.out.println("--- Running Traversal Scenarios ---");

    // --- Scenario 1: A respondent with a valid email ---
    Respondent respondentWithValidEmail = new Respondent(1, Optional.of("test@example.com"));
    System.out.println("\nInput: " + respondentWithValidEmail);

    // Use the traversal to apply the validation function to the email field.
    Kind<ValidatedKind.Witness<String>, Respondent> result1 =
        emailTraversal.modifyF(
            OptionalTraversalExample::validateEmail,
            respondentWithValidEmail,
            validatedApplicative);

    // Narrow the result back to a concrete Validated type to inspect it.
    Validated<String, Respondent> validatedResult1 = VALIDATED.narrow(result1);
    System.out.println("Result: " + validatedResult1);
    // Expected: Valid(Respondent[id=1, email=Optional[test@example.com]])

    // --- Scenario 2: A respondent with an invalid email ---
    Respondent respondentWithInvalidEmail = new Respondent(2, Optional.of("invalid-email"));
    System.out.println("\nInput: " + respondentWithInvalidEmail);

    Kind<ValidatedKind.Witness<String>, Respondent> result2 =
        emailTraversal.modifyF(
            OptionalTraversalExample::validateEmail,
            respondentWithInvalidEmail,
            validatedApplicative);

    Validated<String, Respondent> validatedResult2 = VALIDATED.narrow(result2);
    System.out.println("Result: " + validatedResult2);
    // Expected: Invalid(Invalid email format)

    // --- Scenario 3: A respondent with no email ---
    Respondent respondentWithNoEmail = new Respondent(3, Optional.empty());
    System.out.println("\nInput: " + respondentWithNoEmail);

    // The traversal function is NOT even called because the Optional is empty.
    Kind<ValidatedKind.Witness<String>, Respondent> result3 =
        emailTraversal.modifyF(
            OptionalTraversalExample::validateEmail, respondentWithNoEmail, validatedApplicative);

    Validated<String, Respondent> validatedResult3 = VALIDATED.narrow(result3);
    System.out.println("Result: " + validatedResult3);
    // Expected: Valid(Respondent[id=3, email=Optional.empty])
  }
}
