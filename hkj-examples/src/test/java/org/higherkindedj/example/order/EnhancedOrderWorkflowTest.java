// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.order.config.WorkflowConfig;
import org.higherkindedj.example.order.context.OrderContext;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.OrderLineRequest;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.OrderResult;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.service.impl.InMemoryCustomerService;
import org.higherkindedj.example.order.service.impl.InMemoryDiscountService;
import org.higherkindedj.example.order.service.impl.InMemoryInventoryService;
import org.higherkindedj.example.order.service.impl.InMemoryNotificationService;
import org.higherkindedj.example.order.service.impl.InMemoryPaymentService;
import org.higherkindedj.example.order.service.impl.InMemoryShippingService;
import org.higherkindedj.example.order.workflow.EnhancedOrderWorkflow;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EnhancedOrderWorkflow}, focused on its {@code VResultPath<OrderError, _>}
 * railway: the happy path threads through both phases (gather then
 * discount/payment/shipment/notification), and a {@code Left} from any step short-circuits the rest
 * of the pipeline.
 */
@DisplayName("EnhancedOrderWorkflow")
class EnhancedOrderWorkflowTest {

  private EnhancedOrderWorkflow workflow;

  @BeforeEach
  void setUp() {
    // The in-memory services seed CUST-001 (GOLD, ACTIVE), PROD-001/PROD-002 stock, and a declined
    // test card (4111…), so the scenarios below need no extra fixture wiring.
    workflow =
        new EnhancedOrderWorkflow(
            new InMemoryCustomerService(),
            new InMemoryInventoryService(),
            new InMemoryDiscountService(),
            new InMemoryPaymentService(),
            new InMemoryShippingService(),
            new InMemoryNotificationService(),
            WorkflowConfig.defaults());
  }

  @Nested
  @DisplayName("Happy Path")
  class HappyPathTests {

    @Test
    @DisplayName("processes a valid order end-to-end through the full VResultPath railway")
    void processesValidOrder() {
      // CUST-001 (GOLD, ACTIVE) and PROD-001/PROD-002 are seeded by the in-memory services.
      var request =
          new OrderRequest(
              "CUST-001",
              List.of(new OrderLineRequest("PROD-001", 2), new OrderLineRequest("PROD-002", 1)),
              Optional.empty(),
              validAddress(),
              acceptedCard());

      var result = process(request);

      assertThatEither(result)
          .isRight()
          .hasRightSatisfying(
              orderResult -> {
                assertThat(orderResult.orderId()).isNotNull();
                assertThat(orderResult.trackingNumber()).isNotEmpty();
                assertThat(orderResult.totalCharged()).isNotNull();
                // The result is only built once the notification (final, non-critical) step has
                // run, so a populated audit log proves the whole post-reservation railway
                // completed.
                assertThat(orderResult.auditLog().entries()).isNotEmpty();
              });
    }
  }

  @Nested
  @DisplayName("Short-Circuit Behaviour")
  class ShortCircuitTests {

    @Test
    @DisplayName("short-circuits the gather phase when the customer is unknown")
    void shortCircuitsOnUnknownCustomer() {
      var request =
          new OrderRequest(
              "UNKNOWN-CUSTOMER",
              List.of(new OrderLineRequest("PROD-001", 1)),
              Optional.empty(),
              validAddress(),
              acceptedCard());

      var result = process(request);

      assertThatEither(result)
          .isLeft()
          .hasLeftSatisfying(
              error -> {
                assertThat(error).isInstanceOf(OrderError.CustomerError.class);
                assertThat(error.code()).isEqualTo("CUSTOMER_NOT_FOUND");
              });
    }

    @Test
    @DisplayName("short-circuits the post-reservation phase when payment is declined")
    void shortCircuitsOnDeclinedPayment() {
      var request =
          new OrderRequest(
              "CUST-001",
              List.of(new OrderLineRequest("PROD-001", 1)),
              Optional.empty(),
              validAddress(),
              new PaymentMethod.CreditCard("4111111111111111", "12", "2025", "123"));

      var result = process(request);

      assertThatEither(result)
          .isLeft()
          .hasLeftSatisfying(
              error -> {
                assertThat(error).isInstanceOf(OrderError.PaymentError.class);
                assertThat(error.code()).isEqualTo("PAYMENT_DECLINED");
              });
    }
  }

  /** Runs the workflow inside a bound {@link OrderContext} scope and unwraps the result. */
  private Either<OrderError, OrderResult> process(OrderRequest request) {
    return ScopedValue.where(OrderContext.TRACE_ID, OrderContext.generateTraceId())
        .where(OrderContext.TENANT_ID, "test-tenant")
        .where(OrderContext.DEADLINE, Instant.now().plus(Duration.ofSeconds(30)))
        .call(() -> workflow.process(request).run().run());
  }

  private static ShippingAddress validAddress() {
    return new ShippingAddress("Test User", "123 Test Street", "London", "SW1A 1AA", "GB");
  }

  private static PaymentMethod acceptedCard() {
    return new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123");
  }
}
