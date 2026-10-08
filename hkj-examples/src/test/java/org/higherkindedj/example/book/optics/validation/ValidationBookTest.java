// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Validation, Batching and Auditing introduction makes about its path. */
@DisplayName("the Validation, Batching and Auditing introduction: one path validates every name")
class ValidationBookTest {

  @Test
  @DisplayName("the sample form fails on its one permission that is not allowed")
  void sampleFormFailsOnTheDisallowedPermission() {
    Validated<String, Form> checked = ValidationBook.check(ValidationBook.FORM);

    assertThat(checked).hasToString("Invalid(Invalid permission: PERM_FLY)");
  }

  @Test
  @DisplayName("every failing name is collected, joined by the semigroup")
  void everyFailureIsCollected() {
    Form twoBad =
        new Form(
            7, new User("bob", List.of(new Permission("PERM_FLY"), new Permission("PERM_SWIM"))));

    assertThat(ValidationBook.check(twoBad))
        .hasToString("Invalid(Invalid permission: PERM_FLY; Invalid permission: PERM_SWIM)");
  }

  @Test
  @DisplayName("a Guest has no permissions in focus, and validates clean")
  void guestValidatesClean() {
    Form guest = new Form(43, new Guest());

    assertThat(ValidationBook.check(guest)).isEqualTo(Validated.valid(guest));
  }
}
