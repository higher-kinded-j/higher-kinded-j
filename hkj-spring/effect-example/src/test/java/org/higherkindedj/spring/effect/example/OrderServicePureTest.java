// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.boundary.TestBoundary;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.spring.effect.example.domain.OrderRequest;
import org.higherkindedj.spring.effect.example.domain.OrderResult;
import org.higherkindedj.spring.effect.example.domain.OrderStatus;
import org.higherkindedj.spring.effect.example.effect.OrderOp;
import org.higherkindedj.spring.effect.example.effect.OrderOpKind;
import org.higherkindedj.spring.effect.example.effect.OrderOpKindHelper;
import org.higherkindedj.spring.effect.example.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link OrderService} using {@link TestBoundary}.
 *
 * <p>These tests run without Spring context, IO, or network. The same Free programs that run
 * against real services in production run against in-memory stubs here, verifying business logic in
 * milliseconds.
 */
@DisplayName("OrderService Pure Tests (No Spring Context)")
class OrderServicePureTest {

  /** In-memory Id-targeting interpreter for pure testing. */
  static class StubOrderInterpreter implements Natural<OrderOpKind.Witness, IdKind.Witness> {
    private int ordersPlaced = 0;
    private int statusQueries = 0;

    @Override
    @SuppressWarnings("unchecked")
    public <A> Kind<IdKind.Witness, A> apply(Kind<OrderOpKind.Witness, A> fa) {
      OrderOp<A> op = OrderOpKindHelper.ORDER_OP.narrow(fa);
      return switch (op) {
        case OrderOp.PlaceOrder<A> place -> {
          ordersPlaced++;
          String orderId = "ORD-TEST-" + UUID.randomUUID().toString().substring(0, 4);
          yield Id.of(place.k().apply(OrderResult.confirmed(orderId)));
        }
        case OrderOp.GetStatus<A> get -> {
          statusQueries++;
          yield Id.of(get.k().apply(OrderStatus.CONFIRMED));
        }
      };
    }

    int ordersPlaced() {
      return ordersPlaced;
    }

    int statusQueries() {
      return statusQueries;
    }
  }

  private final StubOrderInterpreter interpreter = new StubOrderInterpreter();
  private final TestBoundary<OrderOpKind.Witness> boundary = TestBoundary.of(interpreter);
  private final OrderService service = new OrderService();

  @Nested
  @DisplayName("placeOrder()")
  class PlaceOrderTests {

    @Test
    @DisplayName("Should place order and return confirmed result")
    void shouldPlaceOrder() {
      OrderResult result = boundary.run(service.placeOrder(new OrderRequest("C001", "ITEM-42", 2)));

      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
      assertThat(result.orderId()).startsWith("ORD-TEST-");
      assertThat(interpreter.ordersPlaced()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should track multiple order placements")
    void shouldTrackMultipleOrders() {
      boundary.run(service.placeOrder(new OrderRequest("C001", "A", 1)));
      boundary.run(service.placeOrder(new OrderRequest("C002", "B", 2)));
      boundary.run(service.placeOrder(new OrderRequest("C003", "C", 3)));

      assertThat(interpreter.ordersPlaced()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("getOrderStatus()")
  class GetStatusTests {

    @Test
    @DisplayName("Should return order status")
    void shouldReturnStatus() {
      OrderStatus status = boundary.run(service.getOrderStatus("ORD-123"));

      assertThat(status).isEqualTo(OrderStatus.CONFIRMED);
      assertThat(interpreter.statusQueries()).isEqualTo(1);
    }
  }
}
