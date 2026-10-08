// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.parts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ADA;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.BULB;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.LAMP;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.consignment;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.order;

import java.time.Instant;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the What a Path Is Made Of page makes about its examples. */
@DisplayName("the What a Path Is Made Of page")
class PathPartsBookTest {

  private static final Instant AT = Instant.parse("2026-10-02T08:00:00Z");
  private static final Consignment DISPATCHED = consignment(new ConsignmentState.Dispatched(AT));
  private static final Consignment PENDING = consignment(new ConsignmentState.Pending());
  private static final Order MOVED =
      new Order(
          ORDER.id(),
          new Customer("Ada", new EmailAddress("ada@example.org")),
          ORDER.lines(),
          ORDER.placedAt(),
          ORDER.currency(),
          ORDER.status());

  @Test
  @DisplayName("each path's optic reads and writes what the path does")
  void toOptic() {
    PathPartsBook.Optics optics = PathPartsBook.toOptic();

    assertThat(optics.emailValue().set("ada@example.org", ORDER))
        .isEqualTo(MOVED)
        .isEqualTo(OrderFocus.customer().email().value().set("ada@example.org", ORDER));
    assertThat(FocusPath.of(optics.emailValue()).get(ORDER)).isEqualTo("ada@example.com");
    assertThat(optics.dispatched().getOptional(DISPATCHED))
        .contains(new ConsignmentState.Dispatched(AT));
    assertThat(optics.dispatched().getOptional(PENDING)).isEmpty();
    assertThat(Traversals.getAll(optics.quantities(), ORDER)).containsExactly(1, 4);
  }

  @Test
  @DisplayName("a lens written by hand is the accessor and a copy with one component replaced")
  void byHand() {
    EmailAddress other = new EmailAddress("lovelace@example.com");

    assertThat(PathPartsBook.emailByHand().get(ADA)).isEqualTo(ADA.email());
    assertThat(PathPartsBook.emailByHand().set(other, ADA))
        .isEqualTo(new Customer("Ada", other))
        .isEqualTo(CustomerLenses.email().set(other, ADA));
  }

  @Test
  @DisplayName("andThen reaches what the matching path reaches, and via is andThen on the optic")
  void andThen() {
    PathPartsBook.Optics composed = PathPartsBook.andThen();

    assertThat(composed.emailValue().set("ada@example.org", ORDER)).isEqualTo(MOVED);
    assertThat(composed.emailValue().set("ada@example.org", ORDER).lines()).isSameAs(ORDER.lines());
    assertThat(composed.dispatched().getOptional(DISPATCHED))
        .contains(new ConsignmentState.Dispatched(AT))
        .isEqualTo(
            ConsignmentFocus.state()
                .via(ConsignmentStatePrisms.dispatched())
                .getOptional(DISPATCHED));
    assertThat(composed.dispatched().getOptional(PENDING)).isEmpty();
    assertThat(Traversals.getAll(composed.quantities(), ORDER))
        .isEqualTo(OrderFocus.lines().via(LineItemFocus.quantity()).getAll(ORDER));
  }

  @Test
  @DisplayName("a raw traversal modifies through Traversals, folds, and an affine reads the state")
  void rawUse() {
    PathPartsBook.Raw raw = PathPartsBook.useRawOptics(ORDER, DISPATCHED);

    assertThat(raw.doubled().lines())
        .containsExactly(
            new LineItem("LAMP", 2, LAMP.price()), new LineItem("BULB", 8, BULB.price()));
    assertThat(raw.totalQuantity()).isEqualTo(5);
    assertThat(raw.dispatch()).contains(new ConsignmentState.Dispatched(AT));
    assertThat(PathPartsBook.useRawOptics(order(List.of()), PENDING).dispatch()).isEmpty();
  }
}
