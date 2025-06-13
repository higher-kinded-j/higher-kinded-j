// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.traversal.set;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Set;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * A runnable example demonstrating the use of a generated Traversal for a field of type {@link
 * Set}.
 */
public class SetTraversalExample {

  /**
   * An immutable UserGroup record containing a set of user emails. {@code @GenerateTraversals}
   * generates optics for this record.
   */
  @GenerateTraversals
  public record UserGroup(String name, Set<String> memberEmails) {}

  /** An "effectful" function that validates an email address format. */
  public static Kind<ValidatedKind.Witness<String>, String> validateEmail(String email) {
    if (email != null && email.contains("@")) {
      return VALIDATED.widen(Validated.valid(email));
    } else {
      return VALIDATED.widen(Validated.invalid("Email '" + email + "' is invalid"));
    }
  }

  public static void main(String[] args) {
    // 1. Setup
    Applicative<ValidatedKind.Witness<String>> validatedApplicative = ValidatedMonad.instance();
    var membersTraversal = UserGroupTraversals.memberEmails();

    System.out.println("--- Running Traversal Scenarios for Set ---");

    // --- Scenario 1: A group with all valid emails ---
    var groupAllValid = new UserGroup("Admins", Set.of("a@test.com", "b@test.com"));
    System.out.println("\nInput: " + groupAllValid);

    var result1 =
        membersTraversal.modifyF(
            SetTraversalExample::validateEmail, groupAllValid, validatedApplicative);

    System.out.println("Result: " + VALIDATED.narrow(result1));
    // Expected: Valid(UserGroup[name=Admins, memberEmails=[a@test.com, b@test.com]])

    // --- Scenario 2: A group with an invalid email ---
    var groupInvalid = new UserGroup("Testers", Set.of("a@test.com", "bad-email", "c@test.com"));
    System.out.println("\nInput: " + groupInvalid);

    var result2 =
        membersTraversal.modifyF(
            SetTraversalExample::validateEmail, groupInvalid, validatedApplicative);

    System.out.println("Result: " + VALIDATED.narrow(result2));
    // Expected: Invalid(Email 'bad-email' is invalid)
  }
}
