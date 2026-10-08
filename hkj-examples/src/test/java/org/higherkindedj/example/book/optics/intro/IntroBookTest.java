// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.intro;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Optics chapter introduction makes about its before and after. */
@DisplayName("the Optics introduction: a wither cascade beside a Focus path")
class IntroBookTest {

  private static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(
              new LineItem("LAMP", 1, new BigDecimal("40.00")),
              new LineItem("BULB", 4, new BigDecimal("2.50"))),
          Instant.parse("2026-10-01T09:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  @Test
  @DisplayName("the wither cascade and the Focus path make the same change")
  void cascadeMatchesTheFocusPath() {
    Order byWithers = IntroBook.discountByWithers(ORDER);
    Order byFocus = IntroBook.discountByFocus(ORDER);

    assertThat(byFocus).isEqualTo(byWithers);
    assertThat(byFocus.lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("36.000"), new BigDecimal("2.250"));
    assertThat(byFocus.customer()).isSameAs(ORDER.customer());
  }
}
