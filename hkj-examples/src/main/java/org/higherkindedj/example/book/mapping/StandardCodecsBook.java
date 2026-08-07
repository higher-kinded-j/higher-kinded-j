// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.higherkindedj.optics.validated.StandardCodecs.bigDecimal;
import static org.higherkindedj.optics.validated.StandardCodecs.enumByName;
import static org.higherkindedj.optics.validated.StandardCodecs.localDate;
import static org.higherkindedj.optics.validated.StandardCodecs.uuid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The book's Standard codecs section: a typical DTO boundary (identifier, date, enum, money) mapped
 * entirely from the stock vocabulary, with no hand-written leaf.
 */
// ANCHOR: codecs_spec
enum OrderStatus {
  NEW,
  PAID,
  CANCELLED
}

record Order(UUID id, LocalDate placedOn, OrderStatus status, BigDecimal total) {}

record OrderDto(String id, String placedOn, String status, String total) {}

@GenerateMapping
interface OrderMapping extends MappingSpec<Order, OrderDto> {
  default ValidatedPrism<String, UUID> id() {
    return uuid();
  }

  default ValidatedPrism<String, LocalDate> placedOn() {
    return localDate();
  }

  default ValidatedPrism<String, OrderStatus> status() {
    return enumByName(OrderStatus.class);
  }

  default ValidatedPrism<String, BigDecimal> total() {
    return bigDecimal();
  }
}
// ANCHOR_END: codecs_spec
