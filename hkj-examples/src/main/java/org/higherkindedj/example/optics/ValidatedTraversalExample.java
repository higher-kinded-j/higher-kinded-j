// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.Set;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * A runnable example demonstrating composition of optics (Lens, Prism, and Traversal) to perform a
 * deep validation on a nested data structure.
 */
public class ValidatedTraversalExample {

  // --- 1. A Data Model with a Sum Type ---
  @GenerateLenses
  public record Permission(String name) {}

  @GeneratePrisms
  public sealed interface Principal {}

  @GenerateLenses
  @GenerateTraversals
  public record User(String username, List<Permission> permissions) implements Principal {}

  public record Guest() implements Principal {}

  @GenerateLenses
  public record Form(int formId, Principal principal) {}

  // --- 2. An "effectful" validation function ---
  private static final Set<String> VALID_PERMISSIONS =
      Set.of("PERM_READ", "PERM_WRITE", "PERM_DELETE");

  public static Kind<ValidatedKind.Witness<String>, String> validatePermissionName(String name) {
    System.out.println("  -> Validating permission: '" + name + "'...");
    if (VALID_PERMISSIONS.contains(name)) {
      System.out.println("     ...OK");
      return VALIDATED.widen(Validated.valid(name));
    } else {
      System.out.println("     ...FAILED!");
      return VALIDATED.widen(Validated.invalid("Invalid permission: " + name));
    }
  }

  public static void main(String[] args) {
    // Define a Semigroup for combining String errors.
    final Semigroup<String> stringSemigroup = Semigroups.string("; ");
    // Create the Applicative instance with the error-combining logic.
    Applicative<ValidatedKind.Witness<String>> validatedApplicative =
        ValidatedMonad.instance(stringSemigroup);

    // --- Create a sample form to work with ---
    var originalForm =
        new Form(
            1,
            new User("Alice", List.of(new Permission("PERM_READ"), new Permission("PERM_WRITE"))));
    System.out.println("Original Form: " + originalForm);
    System.out.println("------------------------------------------");

    // =======================================================================
    // SCENARIO 1: Using the new `with*` helper method for a shallow update
    // =======================================================================
    System.out.println("--- Scenario 1: Using `with*` Helper ---");

    // Use the generated helper to replace the principal of the form.
    var formWithGuest = FormLenses.withPrincipal(originalForm, new Guest());

    System.out.println("After `withPrincipal`: " + formWithGuest);
    System.out.println("------------------------------------------");

    // =======================================================================
    // SCENARIO 2: Using a composed Traversal for deep validation
    // =======================================================================
    System.out.println("--- Scenario 2: Using Composed Traversal for Deep Validation ---");

    // 3. Compose optics to create a "deep" traversal into the permission names.
    Traversal<Form, String> formToPermissionNameTraversal =
        FormLenses.principal()
            .asTraversal()
            .andThen(PrincipalPrisms.user().asTraversal())
            .andThen(UserTraversals.permissions())
            .andThen(PermissionLenses.name().asTraversal());

    // --- Run validation on a form with multiple invalid permissions ---
    var formWithInvalidPerms =
        new Form(
            3,
            new User(
                "Charlie",
                List.of(
                    new Permission("PERM_EXECUTE"),
                    new Permission("PERM_WRITE"),
                    new Permission("PERM_SUDO"))));
    System.out.println("\nInput: " + formWithInvalidPerms);

    Kind<ValidatedKind.Witness<String>, Form> validationResult =
        formToPermissionNameTraversal.modifyF(
            ValidatedTraversalExample::validatePermissionName,
            formWithInvalidPerms,
            validatedApplicative);

    System.out.println("Result (errors accumulated): " + VALIDATED.narrow(validationResult));

    // --- Run validation on a form where the path does not match (Guest) ---
    System.out.println("\nInput: " + formWithGuest);
    Kind<ValidatedKind.Witness<String>, Form> guestResult =
        formToPermissionNameTraversal.modifyF(
            ValidatedTraversalExample::validatePermissionName, formWithGuest, validatedApplicative);
    // The traversal does nothing, so the result is Valid.
    System.out.println("Result (path does not match): " + VALIDATED.narrow(guestResult));
  }
}
