// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.monads.assembly;

import static org.higherkindedj.hkt.assertions.FieldErrorAssert.assertThatFieldError;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The located-error assertion shown on the book's Accumulating Assembly page, whose anchored region
 * the page {@code {{#include}}}s, so the snippet it displays is this test and it is green; beside
 * it, the errors behind the result the page's {@code construct} example prints.
 */
@DisplayName("the assembly page's located errors")
class ValidatedAssemblyBookTest {

  @Test
  @DisplayName("construct reports the window's refusal as the page shows it")
  void constructReportsTheWindowRefusal() {
    // The errors behind the result the page prints:
    assertThatValidated(
            Validated.fields()
                .field("opens", ValidatedAssemblyBook.parseDate("2026-03-09"))
                .field("closes", ValidatedAssemblyBook.parseDate("2026-03-07"))
                .construct(Window::new, "not a valid Window"))
        .isInvalid()
        .hasFieldErrors("closes must be after opens");
  }

  @Test
  void locatedErrorsCarryTheirPath() {
    // ANCHOR: field_error
    assertThatFieldError(FieldError.of("not a postcode").at("zip").at("address"))
        .hasPath("address.zip")
        .hasSegments("address", "zip")
        .hasMessageContaining("postcode");
    // ANCHOR_END: field_error
  }
}
