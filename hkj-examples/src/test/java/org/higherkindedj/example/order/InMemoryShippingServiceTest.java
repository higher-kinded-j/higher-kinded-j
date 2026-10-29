// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.example.order.model.WarehouseInfo;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.service.impl.InMemoryShippingService;
import org.higherkindedj.hkt.assertions.SteppableClock;
import org.higherkindedj.hkt.time.TimeSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link InMemoryShippingService} works out delivery dates from an injected {@link
 * TimeSource}.
 */
@DisplayName("InMemoryShippingService")
class InMemoryShippingServiceTest {

  private static final Instant START = Instant.parse("2026-07-07T00:00:00Z");

  private static ValidatedShippingAddress address(InMemoryShippingService service, String country) {
    return service
        .validateAddress(
            new ShippingAddress("Test User", "123 Test Street", "London", "SW1A 1AA", country))
        .getRight();
  }

  private static Instant estimate(InMemoryShippingService service, String country) {
    ShipmentInfo shipment =
        service.createShipment(OrderId.generate(), address(service, country), List.of()).getRight();
    return shipment.estimatedDelivery();
  }

  @Test
  @DisplayName("a domestic shipment is estimated 2 days from now")
  void domesticShipmentIsEstimatedTwoDaysOut() {
    var service = new InMemoryShippingService(TimeSource.of(SteppableClock.startingAt(START)));

    assertThat(estimate(service, "GB")).isEqualTo(START.plus(Duration.ofDays(2)));
  }

  @Test
  @DisplayName("a European shipment is estimated 5 days from now")
  void europeanShipmentIsEstimatedFiveDaysOut() {
    var service = new InMemoryShippingService(TimeSource.of(SteppableClock.startingAt(START)));

    assertThat(estimate(service, "DE")).isEqualTo(START.plus(Duration.ofDays(5)));
  }

  @Test
  @DisplayName("an international shipment is estimated 10 days from now")
  void internationalShipmentIsEstimatedTenDaysOut() {
    var service = new InMemoryShippingService(TimeSource.of(SteppableClock.startingAt(START)));

    assertThat(estimate(service, "US")).isEqualTo(START.plus(Duration.ofDays(10)));
  }

  @Test
  @DisplayName("a warehouse shipment adds the warehouse's processing days to the zone's days")
  void warehouseShipmentAddsProcessingDays() {
    var service = new InMemoryShippingService(TimeSource.of(SteppableClock.startingAt(START)));
    var warehouse = WarehouseInfo.uk("WH-1", "Test", new BigDecimal("4.00"));

    ShipmentInfo shipment =
        service
            .createShipmentFromWarehouse(
                OrderId.generate(), warehouse, List.of(), address(service, "GB"))
            .getRight();

    assertThat(shipment.estimatedDelivery()).isEqualTo(START.plus(Duration.ofDays(3)));
  }

  @Test
  @DisplayName("moving the clock on a day moves the next estimate on a day")
  void advancingTheClockMovesTheEstimate() {
    var clock = SteppableClock.startingAt(START);
    var service = new InMemoryShippingService(TimeSource.of(clock));

    Instant first = estimate(service, "GB");
    clock.advance(Duration.ofDays(1));
    Instant second = estimate(service, "GB");

    assertThat(second).isEqualTo(first.plus(Duration.ofDays(1)));
  }
}
