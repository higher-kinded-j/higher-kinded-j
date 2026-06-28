// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.FulfilmentStatus;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.Product;
import org.higherkindedj.example.order.model.ValidatedOrder;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.example.order.service.impl.InMemoryInventoryService;
import org.higherkindedj.example.order.service.impl.InMemoryNotificationService;
import org.higherkindedj.example.order.service.impl.InMemoryPaymentService;
import org.higherkindedj.example.order.service.impl.InMemoryShippingService;
import org.higherkindedj.example.order.workflow.PartialFulfilmentWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PartialFulfilmentWorkflow} — the fix for the partial-line money/fulfilment bug:
 * a partially-available line must ship (and be charged for) its available units and back-order only
 * its shortage, instead of being dropped from the shipment and back-ordered (and over-charged) in
 * full.
 */
@DisplayName("PartialFulfilmentWorkflow — ship N / back-order M")
class PartialFulfilmentWorkflowTest {

  private PartialFulfilmentWorkflow workflow;

  @BeforeEach
  void setUp() {
    workflow =
        new PartialFulfilmentWorkflow(
            new InMemoryInventoryService(),
            new InMemoryPaymentService(),
            new InMemoryShippingService(),
            new InMemoryNotificationService());
  }

  @Test
  @DisplayName("a partial line ships its available units and back-orders only the shortage")
  void partialLineShipsAvailableAndBackOrdersShortage() {
    // PROD-001 (stock 100) is fully available; PROD-005 (stock 10) is short by 5 of the 15
    // requested.
    var order = orderWith(line("PROD-001", 2), line("PROD-005", 15));

    var result = workflow.process(order).run();

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            fulfilment -> {
              assertThat(fulfilment.status()).isEqualTo(FulfilmentStatus.PARTIAL);
              assertThat(fulfilment.hasBackOrders()).isTrue();
              assertThat(fulfilment.backOrderCount()).isEqualTo(1);

              var backOrder = fulfilment.backOrders().getFirst();
              assertThat(backOrder.productId()).isEqualTo(new ProductId("PROD-005"));
              // Only the shortage is back-ordered, not the whole 15-unit line.
              assertThat(backOrder.quantity()).isEqualTo(5);

              // Charged now: 2 + 10 available units @ £10 = £120; back-order: 5 units @ £10 = £50.
              assertThat(fulfilment.fulfilledAmount()).isEqualTo(Money.gbp("120.00"));
              assertThat(fulfilment.backOrderAmount()).isEqualTo(Money.gbp("50.00"));
              // The two amounts together equal the full order value (no money lost or
              // double-counted).
              assertThat(fulfilment.totalOrderValue()).isEqualTo(Money.gbp("170.00"));
            });
  }

  @Test
  @DisplayName("an order with duplicate product lines is handled, not crashed")
  void handlesDuplicateProductLines() {
    // Two lines for the same product must not blow up the unitPrices / linesById toMap.
    var order = orderWith(line("PROD-001", 2), line("PROD-001", 3));

    var result = workflow.process(order).run();

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            fulfilment -> {
              assertThat(fulfilment.status()).isEqualTo(FulfilmentStatus.COMPLETE);
              // Both lines (2 + 3 units @ £10) ship and are charged; nothing is back-ordered.
              assertThat(fulfilment.fulfilledAmount()).isEqualTo(Money.gbp("50.00"));
            });
  }

  @Test
  @DisplayName("a fully available order completes with no back-orders")
  void fullyAvailableOrderCompletes() {
    var order = orderWith(line("PROD-001", 2), line("PROD-002", 1));

    var result = workflow.process(order).run();

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            fulfilment -> {
              assertThat(fulfilment.status()).isEqualTo(FulfilmentStatus.COMPLETE);
              assertThat(fulfilment.isFullyFulfilled()).isTrue();
              assertThat(fulfilment.fulfilledAmount()).isEqualTo(Money.gbp("30.00"));
              assertThat(fulfilment.backOrderAmount().isZeroOrNegative()).isTrue();
            });
  }

  private static ValidatedOrder orderWith(ValidatedOrderLine... lines) {
    var lineList = List.of(lines);
    var customer =
        new Customer(
            new CustomerId("CUST-001"),
            "Alice",
            "alice@example.com",
            "555-0000",
            Customer.CustomerStatus.ACTIVE,
            Customer.LoyaltyTier.GOLD);
    var address =
        new ValidatedShippingAddress(
            "Alice",
            "1 Test Street",
            "London",
            "SW1A 1AA",
            "GB",
            ValidatedShippingAddress.ShippingZone.DOMESTIC);
    return new ValidatedOrder(
        OrderId.generate(),
        customer.id(),
        customer,
        lineList,
        Optional.empty(),
        address,
        new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123"),
        ValidatedOrder.calculateSubtotal(lineList),
        Instant.now());
  }

  private static ValidatedOrderLine line(String productId, int quantity) {
    var id = new ProductId(productId);
    var product =
        new Product(id, "Product " + productId, "Description", Money.gbp("10.00"), "General", true);
    return ValidatedOrderLine.of(id, product, quantity);
  }
}
