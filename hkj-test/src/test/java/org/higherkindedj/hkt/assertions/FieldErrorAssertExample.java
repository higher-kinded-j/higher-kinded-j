// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.FieldErrorAssert.assertThatFieldError;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.FieldErrorAssert}. */
@DisplayName("FieldErrorAssert showcase")
class FieldErrorAssertExample {

  @Test
  @DisplayName("A leaf error is unlabelled; field() attaches the location")
  void leafError() {
    FieldError leaf = FieldError.of("not an address");

    assertThatFieldError(leaf).isUnlabelled().hasMessage("not an address");
  }

  @Test
  @DisplayName("at() prepends, so outer labels compose around inner ones")
  void pathComposition() {
    FieldError located = FieldError.of("not a postcode").at("zip").at("address");

    assertThatFieldError(located)
        .hasPath("address.zip")
        .hasSegments("address", "zip")
        .hasMessageContaining("postcode");
  }

  @Test
  @DisplayName("Asserting on the errors of a fields() assembly")
  void assemblyErrors() {
    record User(String name, String email) {}

    Validated<NonEmptyList<FieldError>, User> user =
        Validated.fields()
            .field("name", Validated.<FieldError, String>validNel("Ada"))
            .field(
                "email", Validated.<FieldError, String>invalidNel(FieldError.of("not an address")))
            .apply(User::new);

    assertThatValidated(user).isInvalid();
    NonEmptyList<FieldError> errors =
        user.fold(nel -> nel, _ -> NonEmptyList.single(FieldError.of("unexpectedly valid")));
    assertThatFieldError(errors.head()).hasPath("email").hasMessage("not an address");
  }
}
