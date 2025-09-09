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
import org.jspecify.annotations.NonNull;

/**
 * A runnable example demonstrating composition of optics (Lens, Prism, and Traversal) to perform a
 * deep validation on a nested data structure.
 */
public class ValidatedTraversalExample {

    // --- Data Model ---
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

    // --- Validation Logic ---
    private static final Set<String> VALID_PERMISSIONS = Set.of("PERM_READ", "PERM_WRITE", "PERM_DELETE");

    public static Kind<ValidatedKind.Witness<String>, String> validatePermissionName(String name) {
        if (VALID_PERMISSIONS.contains(name)) {
            return VALIDATED.widen(Validated.valid(name));
        } else {
            return VALIDATED.widen(Validated.invalid("Invalid permission: " + name));
        }
    }

    // --- Reusable Optic Compositions ---
    public static final Traversal<Form, String> FORM_TO_PERMISSION_NAMES =
            FormLenses.principal().asTraversal()
                    .andThen(PrincipalPrisms.user().asTraversal())
                    .andThen(UserTraversals.permissions())
                    .andThen(PermissionLenses.name().asTraversal());

    // --- Helper Methods ---
    private static Applicative<ValidatedKind.Witness<String>> getValidatedApplicative() {
        return ValidatedMonad.instance(Semigroups.string("; "));
    }

    public static Validated<String, Form> validateFormPermissions(Form form) {
        Kind<ValidatedKind.Witness<String>, Form> result =
                FORM_TO_PERMISSION_NAMES.modifyF(
                        ValidatedTraversalExample::validatePermissionName,
                        form,
                        getValidatedApplicative()
                );
        return VALIDATED.narrow(result);
    }

    public static void main(String[] args) {
        System.out.println("=== OPTIC COMPOSITION VALIDATION EXAMPLE ===");
        System.out.println();

        // --- SCENARIO 1: Form with valid permissions ---
        System.out.println("--- Scenario 1: Valid Permissions ---");
        var validUser = new User("alice", List.of(
                new Permission("PERM_READ"),
                new Permission("PERM_WRITE")
        ));
        var validForm = new Form(1, validUser);

        System.out.println("Input: " + validForm);
        Validated<String, Form> validResult = validateFormPermissions(validForm);
        System.out.println("Result: " + validResult);
        System.out.println();

        // --- SCENARIO 2: Form with multiple invalid permissions ---
        System.out.println("--- Scenario 2: Multiple Invalid Permissions ---");
        var invalidUser = new User("charlie", List.of(
                new Permission("PERM_EXECUTE"),  // Invalid
                new Permission("PERM_WRITE"),    // Valid
                new Permission("PERM_SUDO"),     // Invalid
                new Permission("PERM_READ")      // Valid
        ));
        var multipleInvalidForm = new Form(3, invalidUser);

        System.out.println("Input: " + multipleInvalidForm);
        Validated<String, Form> invalidResult = validateFormPermissions(multipleInvalidForm);
        System.out.println("Result (errors accumulated): " + invalidResult);
        System.out.println();

        // --- SCENARIO 3: Form with Guest principal (no targets for traversal) ---
        System.out.println("--- Scenario 3: Guest Principal (No Validation Targets) ---");
        var guestForm = new Form(4, new Guest());

        System.out.println("Input: " + guestForm);
        Validated<String, Form> guestResult = validateFormPermissions(guestForm);
        System.out.println("Result (path does not match): " + guestResult);
        System.out.println();

        // --- SCENARIO 4: Form with empty permissions list ---
        System.out.println("--- Scenario 4: Empty Permissions List ---");
        var emptyPermissionsUser = new User("diana", List.of());
        var emptyPermissionsForm = new Form(5, emptyPermissionsUser);

        System.out.println("Input: " + emptyPermissionsForm);
        Validated<String, Form> emptyResult = validateFormPermissions(emptyPermissionsForm);
        System.out.println("Result (empty list): " + emptyResult);
        System.out.println();

        // --- SCENARIO 5: Demonstrating optic reusability ---
        System.out.println("--- Scenario 5: Optic Reusability ---");

        List<Form> formsToValidate = List.of(validForm, multipleInvalidForm, guestForm);

        System.out.println("Batch validation results:");
        formsToValidate.forEach(form -> {
            Validated<String, Form> result = validateFormPermissions(form);
            String status = result.isValid() ? "✓ VALID" : "✗ INVALID";
            System.out.println("  Form " + form.formId() + ": " + status);
            if (result.isInvalid()) {
                // Fix: Use getError() instead of getInvalid()
                System.out.println("    Errors: " + result.getError());
            }
        });
        System.out.println();

        // --- SCENARIO 6: Alternative validation with different error accumulation ---
        System.out.println("--- Scenario 6: Different Error Accumulation Strategy ---");

        // Use list-based error accumulation instead of string concatenation
        Applicative<ValidatedKind.Witness<List<String>>> listApplicative =
                ValidatedMonad.instance(Semigroups.list());

        // Fix: Create a proper function for list validation
        java.util.function.Function<String, Kind<ValidatedKind.Witness<List<String>>, String>> listValidation =
                name -> VALID_PERMISSIONS.contains(name)
                        ? VALIDATED.widen(Validated.valid(name))
                        : VALIDATED.widen(Validated.invalid(List.of("Invalid permission: " + name)));

        Kind<ValidatedKind.Witness<List<String>>, Form> listResult =
                FORM_TO_PERMISSION_NAMES.modifyF(listValidation, multipleInvalidForm, listApplicative);

        System.out.println("Input: " + multipleInvalidForm);
        System.out.println("Result with list accumulation: " + VALIDATED.narrow(listResult));
    }
}