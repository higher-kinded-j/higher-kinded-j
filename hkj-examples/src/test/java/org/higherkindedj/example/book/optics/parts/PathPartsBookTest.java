// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.parts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the What a Path Is Made Of page makes about its examples. */
@DisplayName("the What a Path Is Made Of page")
class PathPartsBookTest {

  private static final User ALICE =
      new User("Alice", new Address(new Street("Long Street", 1), "London"));
  private static final User RENAMED =
      new User("Alice", new Address(new Street("New Street", 1), "London"));
  private static final Order BY_CARD =
      new Order(
          "A-1",
          new Payment.Card("4242"),
          List.of(new LineItem("LAP-1", 1), new LineItem("MOU-1", 2)));
  private static final Order BY_INVOICE =
      new Order("A-2", new Payment.Invoice("30 days"), List.of(new LineItem("LAP-1", 3)));

  @Test
  @DisplayName("each path's optic reads and writes what the path does")
  void toOptic() {
    PathPartsBook.Optics optics = PathPartsBook.toOptic();

    assertThat(optics.streetName().set("New Street", ALICE))
        .isEqualTo(RENAMED)
        .isEqualTo(UserFocus.address().street().name().set("New Street", ALICE));
    assertThat(FocusPath.of(optics.streetName()).get(ALICE)).isEqualTo("Long Street");
    assertThat(optics.card().getOptional(BY_CARD)).contains(new Payment.Card("4242"));
    assertThat(optics.card().getOptional(BY_INVOICE)).isEmpty();
    assertThat(Traversals.getAll(optics.quantities(), BY_CARD)).containsExactly(1, 2);
  }

  @Test
  @DisplayName("a lens written by hand is the accessor and a copy with one component replaced")
  void byHand() {
    Address elsewhere = new Address(new Street("Short Street", 2), "Leeds");

    assertThat(PathPartsBook.addressByHand().get(ALICE)).isEqualTo(ALICE.address());
    assertThat(PathPartsBook.addressByHand().set(elsewhere, ALICE))
        .isEqualTo(new User("Alice", elsewhere))
        .isEqualTo(UserLenses.address().set(elsewhere, ALICE));
  }

  @Test
  @DisplayName("andThen reaches what the matching path reaches, and via is andThen on the optic")
  void andThen() {
    PathPartsBook.Optics composed = PathPartsBook.andThen();

    assertThat(composed.streetName().set("New Street", ALICE)).isEqualTo(RENAMED);
    assertThat(composed.card().getOptional(BY_CARD))
        .contains(new Payment.Card("4242"))
        .isEqualTo(OrderFocus.payment().via(PaymentPrisms.card()).getOptional(BY_CARD));
    assertThat(composed.card().getOptional(BY_INVOICE)).isEmpty();
    assertThat(Traversals.getAll(composed.quantities(), BY_CARD))
        .isEqualTo(OrderFocus.lines().via(LineItemFocus.quantity()).getAll(BY_CARD));
  }

  @Test
  @DisplayName("a raw traversal modifies through Traversals, folds, and an affine reads the card")
  void rawUse() {
    PathPartsBook.Raw raw = PathPartsBook.useRawOptics(BY_CARD);

    assertThat(raw.doubled().lines())
        .containsExactly(new LineItem("LAP-1", 2), new LineItem("MOU-1", 4));
    assertThat(raw.totalQuantity()).isEqualTo(3);
    assertThat(raw.paidByCard()).contains(new Payment.Card("4242"));
    assertThat(PathPartsBook.useRawOptics(BY_INVOICE).paidByCard()).isEmpty();
  }
}
