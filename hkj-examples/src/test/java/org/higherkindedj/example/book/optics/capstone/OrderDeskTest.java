// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.capstone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.consignment;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.line;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Capstone page makes about the order desk. */
@DisplayName("the Capstone: an order desk")
class OrderDeskTest {

  private static final Instant AT = Instant.parse("2026-10-02T08:00:00Z");

  @Test
  @DisplayName("a good order is accepted, and only its bulk line is discounted")
  void acceptsAndDiscountsBulkLines() {
    Validated<List<String>, Order> accepted = OrderDesk.accept(ORDER);

    assertThat(accepted.isValid()).isTrue();
    assertThat(accepted.get().lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("40.00"), new BigDecimal("2.250"));
  }

  @Test
  @DisplayName(
      "an order with bad prices is refused with every bad price, and nothing is discounted")
  void refusesEveryBadPrice() {
    Order bad = order(List.of(line("LAMP", "-1.00"), line("BULB", "2.50"), line("SOFA", "-3.00")));

    assertThat(OrderDesk.accept(bad))
        .isEqualTo(
            Validated.invalid(
                List.of("Price cannot be negative: -1.00", "Price cannot be negative: -3.00")));
  }

  @Test
  @DisplayName("a good amendment writes the email three records down and the status")
  void amendsAnOrder() {
    Validated<NonEmptyList<FieldError>, Order> amended =
        OrderDesk.amend(ORDER, new OrderDesk.OrderAmendment("  Ada@Example.ORG ", "paid"));

    assertThat(amended.isValid()).isTrue();
    assertThat(amended.get().customer().email().value()).isEqualTo("ada@example.org");
    assertThat(amended.get().status()).isEqualTo(OrderStatus.PAID);
    assertThat(amended.get().lines()).isSameAs(ORDER.lines());
    assertThat(
            OrderDesk.amendByHand(
                ORDER, new OrderDesk.OrderAmendment("  Ada@Example.ORG ", "paid")))
        .isEqualTo(amended.get());
  }

  @Test
  @DisplayName("a bad amendment reports both bad fields, each located by its path")
  void reportsBothBadFields() {
    OrderDesk.OrderAmendment bad = new OrderDesk.OrderAmendment("ada.example.org", "LOST");

    assertThat(OrderDesk.amend(ORDER, bad))
        .hasToString(
            "Invalid(NonEmptyList[customer.email.value: not an email, status: not a status])");
    assertThatThrownBy(() -> OrderDesk.amendByHand(ORDER, bad))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("not an email");
  }

  @Test
  @DisplayName("an amendment that sends nothing leaves the order as it was")
  void emptyAmendment() {
    assertThat(OrderDesk.amend(ORDER, new OrderDesk.OrderAmendment(null, null)))
        .isEqualTo(Validated.valid(ORDER));
  }

  @Test
  @DisplayName("a pending consignment is dispatched, and a returned one is left as it is")
  void dispatchesOnlyPending() {
    Consignment pending = consignment(new ConsignmentState.Pending());
    Consignment returned = consignment(new ConsignmentState.Returned("damaged"));

    assertThat(OrderDesk.dispatch(pending, AT).state())
        .isEqualTo(new ConsignmentState.Dispatched(AT));
    assertThat(OrderDesk.dispatch(returned, AT)).isSameAs(returned);
  }

  @Test
  @DisplayName("the stored paths read what their names say")
  void pathsReadTheirFields() {
    assertThat(OrderDesk.EMAIL.get(ORDER)).isEqualTo("ada@example.com");
    assertThat(OrderDesk.BULK_PRICES.getAll(ORDER)).containsExactly(new BigDecimal("2.50"));
    assertThat(OrderDesk.PRICES.getAll(ORDER))
        .containsExactly(new BigDecimal("40.00"), new BigDecimal("2.50"));
  }
}
