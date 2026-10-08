// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the values the four group introductions' comments claim.
 *
 * <p>{@code BigDecimal} equality is scale-sensitive, so every price here is written with the two
 * decimal places the cast's prices carry, and the expected values keep that scale.
 */
@DisplayName("the Optics chapter's group introductions")
class GroupIntrosBookTest {

  private static final League LEAGUE =
      new League(
          "Pro League",
          List.of(
              new Team("Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90))),
              new Team("Bravo", List.of(new Player("Charlie", 110), new Player("Diana", 120)))));

  @Test
  @DisplayName("The Optic Types: one composed lens sets and modifies the customer's email")
  void opticTypes() {
    GroupIntrosBook.EmailUpdates updates = GroupIntrosBook.opticTypes(ORDER);

    assertThat(updates.email().get(updates.changed())).isEqualTo("ada@example.org");
    assertThat(updates.email().get(updates.shouted())).isEqualTo("ADA@EXAMPLE.COM");
    assertThat(updates.changed().lines()).isSameAs(ORDER.lines());
    assertThat(updates.email().get(ORDER)).isEqualTo("ada@example.com");
  }

  @Test
  @DisplayName("Collections: one composed traversal reads and raises every player's score")
  void collections() {
    GroupIntrosBook.Bonus bonus = GroupIntrosBook.collections(LEAGUE);

    assertThat(Traversals.getAll(bonus.everyScore(), LEAGUE)).containsExactly(100, 90, 110, 120);
    assertThat(Traversals.getAll(bonus.everyScore(), bonus.bonus()))
        .containsExactly(105, 95, 115, 125);
    assertThat(LEAGUE.teams().getFirst().players().getFirst().score()).isEqualTo(100);
  }

  @Test
  @DisplayName("Precision and Filtering: the filter narrows the update to prices over £10")
  void precision() {
    GroupIntrosBook.Discount discount = GroupIntrosBook.precision(ORDER);

    assertThat(Traversals.getAll(discount.pricey(), ORDER))
        .containsExactly(new BigDecimal("40.00"));
    assertThat(Traversals.getAll(discount.pricey(), discount.discounted()))
        .containsExactly(new BigDecimal("35.00"));
    assertThat(discount.discounted().lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("35.00"), new BigDecimal("2.50"));
    assertThat(ORDER.lines().getFirst().price()).isEqualTo(new BigDecimal("40.00"));
  }

  @Test
  @DisplayName("The Focus DSL in Depth: a chained read and a bulk update over the order")
  void focusInDepth() {
    GroupIntrosBook.Reminder reminder = GroupIntrosBook.focusInDepth(ORDER);

    assertThat(reminder.email()).isEqualTo("ada@example.com");
    // 40.00 x 1.10 and 2.50 x 1.10, at the scale multiply gives: 2 + 2 decimal places
    assertThat(reminder.afterRise().lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("44.0000"), new BigDecimal("2.7500"));
    assertThat(ORDER.lines().getFirst().price()).isEqualTo(new BigDecimal("40.00"));
  }
}
