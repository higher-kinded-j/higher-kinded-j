// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Standard codecs section's running example: the stock-codec mapping obeys the mapping laws,
 * and a bad wire reports every bad field at once with the codecs' located messages.
 */
@DisplayName("the book's stock-codec mapping obeys the laws and locates every failure")
class StandardCodecsBookTest {

  @Test
  void orderMappingObeysTheLaws() {
    MappingLaws.assertMappingLaws(
        OrderMappingImpl.INSTANCE.asValidatedPrism(),
        new OrderDto(
            "123e4567-e89b-12d3-a456-426614174000", "2026-07-28", "PAID", "99.95"), // parses
        new OrderDto("nope", "2026-07-28", "PAID", "99.95")); // must not parse
  }

  @Test
  void everyBadFieldReportsAtOnceWithTheCodecMessage() {
    // ANCHOR: codecs_errors
    Validated<NonEmptyList<FieldError>, Order> parsed =
        OrderMappingImpl.INSTANCE.parse(new OrderDto("NOPE", "28/07/2026", "SHIPPED", "1E+3"));

    assertThatValidated(parsed).isInvalid();
    assertThat(rendered(parsed))
        .containsExactly(
            "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
            "placedOn: not an ISO-8601 date (expected e.g. 2026-07-28)",
            "status: not a OrderStatus (expected one of NEW, PAID, CANCELLED)",
            "total: not a number (expected e.g. 123.45)");
    // ANCHOR_END: codecs_errors
  }

  @Test
  void aGoodWireBuildsBack() {
    Order order =
        new Order(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            LocalDate.of(2026, 7, 28),
            OrderStatus.PAID,
            new BigDecimal("99.95"));

    assertThat(OrderMappingImpl.INSTANCE.build(order))
        .isEqualTo(
            new OrderDto("123e4567-e89b-12d3-a456-426614174000", "2026-07-28", "PAID", "99.95"));
  }

  private static List<String> rendered(Validated<NonEmptyList<FieldError>, ?> result) {
    return result.fold(nel -> nel.map(FieldError::toString).toJavaList(), _ -> List.of());
  }
}
