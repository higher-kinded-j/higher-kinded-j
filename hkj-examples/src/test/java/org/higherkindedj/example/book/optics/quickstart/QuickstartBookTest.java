// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.quickstart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.BULB;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.LAMP;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.consignment;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.line;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

/** Holds the claims the Optics Quickstart makes about its examples. */
@DisplayName("the Optics Quickstart")
class QuickstartBookTest {

  private static final Instant DISPATCHED_AT = Instant.parse("2026-10-07T09:00:00Z");

  @Test
  @DisplayName("one generated path changes the email three records down, and nothing else")
  void changeEmailThroughThePath() {
    Order updated = QuickstartBook.changeEmail(ORDER);

    assertThat(updated)
        .isEqualTo(
            new Order(
                ORDER.id(),
                new Customer("Ada", new EmailAddress("ada@example.org")),
                List.of(LAMP, BULB),
                ORDER.placedAt(),
                ORDER.currency(),
                ORDER.status()));
  }

  @Test
  @DisplayName("rounding reaches every line item's price, and only the prices")
  void roundEveryPrice() {
    Order order = order(List.of(line("LAMP", "36.000"), line("BULB", "2.250")));

    Order rounded = QuickstartBook.roundPrices(order);

    assertThat(rounded.lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("36.00"), new BigDecimal("2.25"));
    assertThat(rounded.customer()).isSameAs(order.customer());
  }

  @Test
  @DisplayName(
      "a pending consignment matches, keeps its variant under modify, and moves by a build")
  void pendingConsignment() {
    Consignment pending = consignment(new ConsignmentState.Pending());

    QuickstartBook.PrismResults results = QuickstartBook.matchAndMove(pending, DISPATCHED_AT);

    assertThat(results.isPending()).isTrue();
    assertThat(results.tidied()).isSameAs(pending.state());
    assertThat(results.dispatched()).isEqualTo(new ConsignmentState.Dispatched(DISPATCHED_AT));
  }

  @Test
  @DisplayName("a returned consignment is tidied in place, and is not moved")
  void returnedConsignment() {
    Consignment returned = consignment(new ConsignmentState.Returned("  damaged in transit "));

    QuickstartBook.PrismResults results = QuickstartBook.matchAndMove(returned, DISPATCHED_AT);

    assertThat(results.isPending()).isFalse();
    assertThat(results.tidied()).isEqualTo(new ConsignmentState.Returned("damaged in transit"));
    assertThat(results.dispatched()).isEqualTo(returned.state());
  }

  @Test
  @DisplayName("the generated JsonNode prisms read the first item's name")
  void readJson() {
    String json = "{\"items\": [{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]}";

    assertThat(QuickstartBook.firstName(json)).map(StringNode::stringValue).contains("Alice");
  }

  @Test
  @DisplayName("Jackson's own JSON Pointer reads the same name in one call")
  void jsonPointerReadsTheSameName() {
    String json = "{\"items\": [{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]}";

    assertThat(new ObjectMapper().readTree(json).at("/items/0/name").stringValue())
        .isEqualTo("Alice");
  }
}
