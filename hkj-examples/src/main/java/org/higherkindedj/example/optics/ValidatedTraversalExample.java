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
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
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

    // --- 3. Compose optics to create a "deep" traversal ---
    Lens<Form, Principal> formPrincipalLens = FormLenses.principal();
    Prism<Principal, User> principalUserPrism = PrincipalPrisms.user();
    Traversal<User, Permission> userPermissionsTraversal = UserTraversals.permissions();
    Lens<Permission, String> permissionNameLens = PermissionLenses.name();

    // We explicitly convert each Lens and Prism to a Traversal to ensure
    // the final result is correctly typed as a Traversal.
    Traversal<Form, String> formToPermissionNameTraversal =
        formPrincipalLens
            .asTraversal()
            .andThen(principalUserPrism.asTraversal())
            .andThen(userPermissionsTraversal)
            .andThen(permissionNameLens.asTraversal());

    System.out.println("--- Running Traversal Scenarios for Deep Validation ---");

    // --- Scenario 1: A completely valid form with a User principal ---
    var validForm =
        new Form(
            1,
            new User("Alice", List.of(new Permission("PERM_READ"), new Permission("PERM_WRITE"))));
    System.out.println("\nInput: " + validForm);

    Kind<ValidatedKind.Witness<String>, Form> result1 =
        formToPermissionNameTraversal.modifyF(
            ValidatedTraversalExample::validatePermissionName, validForm, validatedApplicative);

    System.out.println("Result: " + VALIDATED.narrow(result1));

    // --- Scenario 2: A form with an invalid permission and a User principal ---
    var invalidPermissionForm =
        new Form(
            2, new User("Bob", List.of(new Permission("PERM_READ"), new Permission("PERM_ADMIN"))));
    System.out.println("\nInput: " + invalidPermissionForm);

    Kind<ValidatedKind.Witness<String>, Form> result2 =
        formToPermissionNameTraversal.modifyF(
            ValidatedTraversalExample::validatePermissionName,
            invalidPermissionForm,
            validatedApplicative);

    System.out.println("Result: " + VALIDATED.narrow(result2));

    // --- Scenario 3: A form with multiple invalid permissions ---
    var multipleInvalidForm =
        new Form(
            3,
            new User(
                "Charlie",
                List.of(
                    new Permission("PERM_EXECUTE"),
                    new Permission("PERM_WRITE"),
                    new Permission("PERM_SUDO"))));
    System.out.println("\nInput: " + multipleInvalidForm);

    Kind<ValidatedKind.Witness<String>, Form> result3 =
        formToPermissionNameTraversal.modifyF(
            ValidatedTraversalExample::validatePermissionName,
            multipleInvalidForm,
            validatedApplicative);

    System.out.println("Result (errors accumulated): " + VALIDATED.narrow(result3));

    // --- Scenario 4: A form with a Guest principal ---
    var guestForm = new Form(4, new Guest());
    System.out.println("\nInput: " + guestForm);

    Kind<ValidatedKind.Witness<String>, Form> result4 =
        formToPermissionNameTraversal.modifyF(
            ValidatedTraversalExample::validatePermissionName, guestForm, validatedApplicative);

    System.out.println("Result: " + VALIDATED.narrow(result4));
  }
}
