// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.validation;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.Set;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/ch5_intro.html">Validation, Batching and
 * Auditing</a> page. The page {@code {{#include}}}s the anchored region, and {@code
 * ValidationBookTest} holds the claims the page makes about this code.
 */
public final class ValidationBook {

  private static final Set<String> ALLOWED = Set.of("PERM_READ", "PERM_WRITE", "PERM_DELETE");

  /** The page's sample form: a user with one allowed permission and one that is not. */
  static final Form FORM =
      new Form(
          42, new User("alice", List.of(new Permission("PERM_READ"), new Permission("PERM_FLY"))));

  private ValidationBook() {}

  static Kind<ValidatedKind.Witness<String>, String> validatePermission(String name) {
    return ALLOWED.contains(name)
        ? VALIDATED.widen(Validated.valid(name))
        : VALIDATED.widen(Validated.invalid("Invalid permission: " + name));
  }

  static Validated<String, Form> check(Form form) {
    // ANCHOR: payoff
    Traversal<Form, String> everyPermissionName =
        FormLenses.principal()
            .andThen(PrincipalPrisms.user())
            .andThen(UserTraversals.permissions())
            .andThen(PermissionLenses.name());

    Validated<String, Form> checked =
        VALIDATED.narrow(
            everyPermissionName.modifyF(
                ValidationBook::validatePermission,
                form,
                Instances.validated(Semigroups.string("; "))));
    // Invalid(Invalid permission: PERM_FLY), for the sample form
    // A Guest principal would simply have no permissions in focus, and validate clean.
    // ANCHOR_END: payoff
    return checked;
  }
}

@GenerateLenses
record Permission(String name) {}

@GeneratePrisms
sealed interface Principal permits User, Guest {}

@GenerateLenses
@GenerateTraversals
record User(String username, List<Permission> permissions) implements Principal {}

record Guest() implements Principal {}

@GenerateLenses
record Form(int formId, Principal principal) {}
