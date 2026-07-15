// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.monads.assembly;

import static org.higherkindedj.hkt.assertions.FieldErrorAssert.assertThatFieldError;

import org.higherkindedj.hkt.validated.FieldError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The located-error assertion shown on the book's Accumulating Assembly page. The page {@code
 * {{#include}}}s the anchored region, so the snippet it displays is this test, and it is green.
 */
@DisplayName("a located FieldError carries its path")
class ValidatedAssemblyBookTest {

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
