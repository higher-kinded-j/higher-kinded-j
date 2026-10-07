// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.composition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Composition Rules page shows for its Prism-then-Lens example. */
@DisplayName("the Composition Rules page: Prism.andThen(Lens) is an Affine")
class CompositionRulesBookTest {

  @Test
  @DisplayName("the affine reads and doubles a circle's radius, and leaves a rectangle alone")
  void prismThenLens() {
    List<String> printed =
        CompositionRulesBook.prismThenLens().stream().map(String::valueOf).toList();

    assertThat(printed)
        .containsExactly(
            "Optional[5.0]",
            "Optional.empty",
            "Circle[radius=10.0, colour=red]",
            "Rectangle[width=10.0, height=20.0, colour=blue]");
  }
}
