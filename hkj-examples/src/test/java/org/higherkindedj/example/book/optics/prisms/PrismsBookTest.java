// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.prisms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Prisms page makes about getOptional and build. */
@DisplayName("the Prisms page: getOptional matches one case, build returns it as the sealed type")
class PrismsBookTest {

  @Test
  @DisplayName("getOptional holds a matching case, and is empty for any other")
  void getOptionalMatchesOneCase() {
    PrismsBook.CoreResults results = PrismsBook.coreOperations();

    assertThat(results.result1()).hasToString("Optional[JsonString[value=hello]]");
    assertThat(results.result2()).isEmpty().hasToString("Optional.empty");
  }

  @Test
  @DisplayName("build returns the part itself, typed as the sealed interface")
  void buildReturnsThePart() {
    assertThat(PrismsBook.coreOperations().result3())
        .isEqualTo(new JsonString("world"))
        .hasToString("JsonString[value=world]");
  }
}
