// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Product;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.example.order.service.impl.InMemoryInventoryService;
import org.higherkindedj.hkt.assertions.SteppableClock;
import org.higherkindedj.hkt.time.TimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InMemoryInventoryService} reservation atomicity — the fix for the check-then-act
 * race: stock must never oversell or go negative, multi-line reservations are all-or-nothing, and
 * expired holds are reclaimed.
 */
@DisplayName("InMemoryInventoryService — atomic reservation")
class InMemoryInventoryServiceTest {

  private InMemoryInventoryService service;

  @BeforeEach
  void setUp() {
    service = new InMemoryInventoryService();
  }

  @Test
  @DisplayName("concurrent single-unit reservations never oversell")
  void concurrentReservationsNeverOversell() throws Exception {
    service.setStock("PROD-RACE", 5);
    int contenders = 50;

    var start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Boolean>> futures =
          IntStream.range(0, contenders)
              .mapToObj(
                  _ ->
                      pool.submit(
                          (Callable<Boolean>)
                              () -> {
                                start.await();
                                return service
                                    .reserve(OrderId.generate(), List.of(line("PROD-RACE", 1)))
                                    .isRight();
                              }))
              .toList();

      start.countDown(); // release all contenders at once

      long succeeded = 0;
      for (var future : futures) {
        if (future.get()) {
          succeeded++;
        }
      }

      // Exactly the available units are handed out — no more, no fewer — and stock bottoms at zero.
      assertThat(succeeded).isEqualTo(5);
      assertThat(stockOf("PROD-RACE")).isEqualTo(0);
    }
  }

  @Test
  @DisplayName("a multi-line reservation is all-or-nothing: a short line rolls back the rest")
  void multiLineReservationIsAllOrNothing() {
    // PROD-001 has stock 100, PROD-004 is out of stock (seeded by the service).
    var result =
        service.reserve(OrderId.generate(), List.of(line("PROD-001", 2), line("PROD-004", 1)));

    assertThatEither(result)
        .isLeft()
        .hasLeftSatisfying(
            error -> assertThat(error).isInstanceOf(OrderError.InventoryError.class));
    // The available line was returned, not left short.
    assertThat(stockOf("PROD-001")).isEqualTo(100);
  }

  @Test
  @DisplayName("releasing a reservation returns its stock")
  void releaseReturnsStock() {
    var reservation = service.reserve(OrderId.generate(), List.of(line("PROD-001", 10))).getRight();
    assertThat(stockOf("PROD-001")).isEqualTo(90);

    service.releaseReservation(reservation.reservationId());
    assertThat(stockOf("PROD-001")).isEqualTo(100);
  }

  @Test
  @DisplayName("an expired hold is reclaimed on the next reservation")
  void expiredHoldIsReclaimed() {
    // Inject a steppable time source (#609): reserve, advance past the hold, and the next
    // reservation reclaims the expired one - no seams, no negative durations, no sleeping.
    var clock = SteppableClock.startingAt(Instant.parse("2026-07-07T00:00:00Z"));
    service = new InMemoryInventoryService(TimeSource.of(clock));

    service.reserve(OrderId.generate(), List.of(line("PROD-001", 10)));
    assertThat(stockOf("PROD-001")).isEqualTo(90);

    clock.advance(Duration.ofMinutes(16));

    // This second reservation reclaims the expired first one before taking its own stock.
    service.reserve(OrderId.generate(), List.of(line("PROD-002", 1)));
    assertThat(stockOf("PROD-001")).isEqualTo(100);
  }

  /** Reads true on-hand stock by requesting more than could exist and reading what is available. */
  private int stockOf(String productId) {
    var availability =
        service.getDetailedAvailability(List.of(line(productId, 1_000_000))).getRight();
    return availability.getFirst().availableQty();
  }

  private static ValidatedOrderLine line(String productId, int quantity) {
    var id = new ProductId(productId);
    var product =
        new Product(id, "Product " + productId, "Description", Money.gbp("10.00"), "General", true);
    return ValidatedOrderLine.of(id, product, quantity);
  }
}
