// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.selfcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.consignment;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.order;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.LensLaws;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Proves the answers on the Optics chapter's Check Your Understanding page. */
@DisplayName("the Optics self-check")
class SelfCheckBookTest {

  @Test
  @DisplayName("a traversal then a lens is a traversal over every line's price")
  void andThenType() {
    assertThat(Traversals.getAll(SelfCheckBook.prices(), ORDER))
        .containsExactly(new BigDecimal("40.00"), new BigDecimal("2.50"));
  }

  @Test
  @DisplayName("one path sets every line's quantity to one")
  void oneOfEach() {
    assertThat(SelfCheckBook.oneOfEach(ORDER).lines())
        .extracting(LineItem::quantity)
        .containsExactly(1, 1);
  }

  @Test
  @DisplayName("every line with no items is reported, and a good order is accepted")
  void checkQuantities() {
    Order bad =
        order(
            List.of(
                new LineItem("LAMP", 0, BigDecimal.TEN),
                new LineItem("BULB", 4, BigDecimal.ONE),
                new LineItem("SOFA", -1, BigDecimal.TEN)));

    assertThat(SelfCheckBook.acceptQuantities(bad))
        .isEqualTo(Validated.invalid(List.of("No items: 0", "No items: -1")));
    assertThat(SelfCheckBook.acceptQuantities(ORDER)).isEqualTo(Validated.valid(ORDER));
  }

  @Test
  @DisplayName("a returned reason is upper-cased, and any other state is left alone")
  void returnedReason() {
    Consignment returned = consignment(new ConsignmentState.Returned("damaged"));
    Consignment pending = consignment(new ConsignmentState.Pending());

    assertThat(SelfCheckBook.shoutReturnReason(returned).state())
        .isEqualTo(new ConsignmentState.Returned("DAMAGED"));
    assertThat(SelfCheckBook.shoutReturnReason(pending)).isEqualTo(pending);
  }

  @Test
  @DisplayName("a normalising constructor breaks the set-get law, and LensLaws reports it")
  void normalisingConstructorBreaksSetGet() {
    NormalisedEmail ada = new NormalisedEmail("ada@example.com");

    assertThat(SelfCheckBook.normalisedValue().set("ADA@EXAMPLE.COM", ada).value())
        .isEqualTo("ada@example.com");
    assertThatThrownBy(
            () -> LensLaws.assertSetGet(SelfCheckBook.normalisedValue(), ada, "ADA@EXAMPLE.COM"))
        .isInstanceOf(AssertionError.class);
  }
}
