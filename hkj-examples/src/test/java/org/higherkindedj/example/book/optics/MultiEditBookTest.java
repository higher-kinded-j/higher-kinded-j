// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the Many Edits at Once page's claims that its output comments cannot: {@code
 * BookExampleOutputTest} runs {@link MultiEditBook}'s {@code main} for the rest.
 */
@DisplayName("the Many Edits at Once page")
class MultiEditBookTest {

  @Test
  @DisplayName("a generated path is labelled with its component, and a composed one joins them")
  void generatedPathsLabelThemselves() {
    assertThat(LineItemFocus.sku().pathString()).isEqualTo("sku");
    assertThat(OrderFocus.customer().email().value().pathString())
        .isEqualTo("customer.email.value");
  }
}
