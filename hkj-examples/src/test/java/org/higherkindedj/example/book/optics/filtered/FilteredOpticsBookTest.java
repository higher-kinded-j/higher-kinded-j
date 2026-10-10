// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.filtered;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Filtered Optics page makes about what a filtered traversal changes. */
@DisplayName("the Filtered Optics page: modify keeps, getAll excludes")
class FilteredOpticsBookTest {

  private static LineItem line(String sku, int quantity, String price) {
    return new LineItem(sku, quantity, new BigDecimal(price));
  }

  @Test
  @DisplayName("a filtered traversal composed with a lens reads and writes only the bulk prices")
  void composedWithALens() {
    FilteredOpticsBook.Composed composed = FilteredOpticsBook.composeWithALens();

    assertThat(composed.prices()).containsExactly(new BigDecimal("2.50"), new BigDecimal("1.20"));
    assertThat(composed.prices()).hasToString("[2.50, 1.20]");
    assertThat(composed.result())
        .containsExactly(
            line("BULB", 4, "2.30"), line("LAMP", 1, "40.00"), line("FUSE", 10, "1.00"));
    assertThat(composed.result().getFirst())
        .hasToString("LineItem[sku=BULB, quantity=4, price=2.30]");
  }

  @Test
  @DisplayName("modify keeps the lamp in place unchanged, and getAll leaves it out")
  void preservedAndExcluded() {
    FilteredOpticsBook.Semantics semantics = FilteredOpticsBook.preservedAndExcluded();

    assertThat(semantics.modified())
        .containsExactly(
            line("BULB", 4, "2.25"), line("LAMP", 1, "40.00"), line("FUSE", 10, "1.08"));
    assertThat(semantics.gotten())
        .containsExactly(line("BULB", 4, "2.50"), line("FUSE", 10, "1.20"));
    assertThat(semantics.modified())
        .extracting(LineItem::toString)
        .containsExactly(
            "LineItem[sku=BULB, quantity=4, price=2.25]",
            "LineItem[sku=LAMP, quantity=1, price=40.00]",
            "LineItem[sku=FUSE, quantity=10, price=1.08]");
    assertThat(semantics.gotten())
        .extracting(LineItem::toString)
        .containsExactly(
            "LineItem[sku=BULB, quantity=4, price=2.50]",
            "LineItem[sku=FUSE, quantity=10, price=1.20]");
  }
}
