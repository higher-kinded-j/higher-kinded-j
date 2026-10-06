// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.quickstart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

/** Holds the claims the Optics Quickstart makes about its examples. */
@DisplayName("the Optics Quickstart")
class QuickstartBookTest {

  private static final Instant SHIPPED_AT = Instant.parse("2026-10-07T09:00:00Z");

  @Test
  @DisplayName("one generated path renames the street three records down, and nothing else")
  void renameThroughThePath() {
    User user = new User("Alice", new Address(new Street("Old Street", 12), "London"));

    User updated = QuickstartBook.rename(user);

    assertThat(updated)
        .isEqualTo(new User("Alice", new Address(new Street("New Street", 12), "London")));
  }

  @Test
  @DisplayName("the discount reaches every line item's price")
  void discountEveryItem() {
    Order order =
        new Order(
            "o-1",
            new Status.Pending(),
            List.of(
                new LineItem("a", new BigDecimal("10.00")),
                new LineItem("b", new BigDecimal("20.00"))));

    Order discounted = QuickstartBook.discount(order);

    assertThat(discounted.items())
        .extracting(LineItem::price)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("9"), new BigDecimal("18"));
    assertThat(discounted.id()).isEqualTo("o-1");
  }

  @Test
  @DisplayName("a pending order matches, keeps its variant under modify, and moves by a build")
  void pendingOrder() {
    Order order = new Order("o-1", new Status.Pending(), List.of());

    QuickstartBook.PrismResults results = QuickstartBook.matchAndMove(order, SHIPPED_AT);

    assertThat(results.isPending()).isTrue();
    assertThat(results.tidied()).isEqualTo(new Status.Pending());
    assertThat(results.fulfilled()).isEqualTo(new Status.Shipped(SHIPPED_AT));
  }

  @Test
  @DisplayName("a cancelled order is tidied in place, and is not moved")
  void cancelledOrder() {
    Order order = new Order("o-2", new Status.Cancelled("  out of stock "), List.of());

    QuickstartBook.PrismResults results = QuickstartBook.matchAndMove(order, SHIPPED_AT);

    assertThat(results.isPending()).isFalse();
    assertThat(results.tidied()).isEqualTo(new Status.Cancelled("out of stock"));
    assertThat(results.fulfilled()).isEqualTo(order.status());
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
