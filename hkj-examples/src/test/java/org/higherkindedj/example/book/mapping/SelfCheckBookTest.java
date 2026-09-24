// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The answers on the book's Check Your Understanding page. Each answer that predicts a result
 * includes one of the anchored regions below, so what the page says comes back is what this green
 * test asserts.
 */
@DisplayName("the book's self-check answers hold")
class SelfCheckBookTest {

  @Test
  @DisplayName("a null and a bad email are both reported, in declaration order")
  void nullAndBadLeafAccumulate() {
    // ANCHOR: null_and_leaf
    assertThatValidated(CustomerMappingImpl.INSTANCE.parse(new CustomerDto(null, "not-an-email")))
        .isInvalid()
        .hasFieldErrors("name: must not be null", "email: not an email address");
    // ANCHOR_END: null_and_leaf
  }

  @Test
  @DisplayName("a record's constructor runs only once every component has parsed")
  void constructorRunsLast() {
    // ANCHOR: constructor_last
    ReservationDto request =
        new ReservationDto(
            null, // no guest
            List.of(
                new StayDto("2026-03-01", "2026-03-04"),
                new StayDto("2026-03-09", "07/03/2026"))); // meant to leave before it arrives

    assertThatValidated(ReservationMappingImpl.INSTANCE.parse(request))
        .isInvalid()
        .hasFieldErrors(
            "guest: must not be null",
            "stays.1.checkOut: not an ISO-8601 date (expected e.g. 2026-07-28)");
    // ANCHOR_END: constructor_last
  }

  @Test
  @DisplayName("an instance bound on the spec reads null once the Impl is used first")
  void instanceBoundOnTheSpecReadsNull() {
    // ANCHOR: trap_proof
    VisitorMappingImpl mapper = VisitorMappingImpl.INSTANCE; // the program uses the Impl first

    assertThat(mapper).isNotNull();
    assertThat(VisitorMapping.MAPPER).isNull(); // and the spec's constant stays null for good
    // ANCHOR_END: trap_proof
  }

  @Test
  @DisplayName("the shipment spec locates a bad parcel by index and reads a null note as absent")
  void shipmentSpecLocatesAndBridges() {
    // ANCHOR: shipment_proof
    ShipmentMappingImpl shipments = ShipmentMappingImpl.INSTANCE;

    assertThatValidated(
            shipments.parse(
                new ShipmentDto(
                    "not-a-uuid",
                    List.of(new ParcelDto("A-1", 250), new ParcelDto(null, 90)),
                    null)))
        .isInvalid()
        .hasFieldErrors(
            "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
            "parcels.1.sku: must not be null");

    UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertThatValidated(
            shipments.parse(
                new ShipmentDto(id.toString(), List.of(new ParcelDto("A-1", 250)), null)))
        .hasValue(new Shipment(id, List.of(new Parcel("A-1", 250)), Optional.empty()));
    // ANCHOR_END: shipment_proof
  }
}
