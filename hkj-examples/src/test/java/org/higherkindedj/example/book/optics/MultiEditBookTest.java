// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.edit.Edit.modify;
import static org.higherkindedj.optics.edit.Edit.modifyIfPresent;
import static org.higherkindedj.optics.edit.Edit.parseIfPresent;

import java.math.BigDecimal;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.optics.edit.Edits;
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

  @Test
  @DisplayName("checkpoint: two edits on one path run left to right, each seeing the last")
  void checkpointOverlappingEdits() {
    LineItem three = new LineItem("LAMP", 3, new BigDecimal("40.00"));

    LineItem after =
        Edits.combine(
                modify(MultiEditBook.QUANTITY, q -> q + 1),
                modify(MultiEditBook.QUANTITY, q -> q * 2))
            .apply(three);

    assertThat(after.quantity()).isEqualTo(8);
  }

  @Test
  @DisplayName("checkpoint: one bad field and nothing is written, not even the good edit")
  void checkpointValidateThenWrite() {
    LineItem lamp = new LineItem("LAMP", 1, new BigDecimal("40.00"));

    var patched =
        Edits.accumulate(
                parseIfPresent(MultiEditBook.SKU, "  ", Sku::parse),
                modifyIfPresent(MultiEditBook.QUANTITY, 2, (delta, qty) -> qty + delta),
                parseIfPresent(MultiEditBook.PRICE, "forty", Price::parse))
            .apply(lamp);

    assertThat(patched).hasToString("Invalid(NonEmptyList[sku: not a SKU, price: not a price])");
  }
}
