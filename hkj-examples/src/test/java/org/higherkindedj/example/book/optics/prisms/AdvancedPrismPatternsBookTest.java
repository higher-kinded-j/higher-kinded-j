// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.prisms;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Advanced Prism Patterns page shows for {@code Prisms.nearly}. */
@DisplayName("Advanced Prism Patterns: nearly matches a predicate")
class AdvancedPrismPatternsBookTest {

  @Test
  @DisplayName("a non-empty string matches, the empty string does not, and build gives the default")
  void nearlyMatchesAPredicate() {
    AdvancedPrismPatternsBook.Nearly nearly = AdvancedPrismPatternsBook.nearly();

    assertThat(nearly.hit()).contains(Unit.INSTANCE);
    assertThat(nearly.miss()).isEmpty();
    assertThat(nearly.built()).isEqualTo("default");
  }
}
