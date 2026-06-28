// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.order.config.WorkflowConfig;
import org.higherkindedj.example.order.config.WorkflowConfig.Environment;
import org.higherkindedj.example.order.config.WorkflowConfig.FeatureFlags;
import org.higherkindedj.example.order.config.WorkflowConfig.RetryConfig;
import org.higherkindedj.example.order.config.WorkflowConfig.TimeoutConfig;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.OrderLineRequest;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.PaymentConfirmation;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.example.order.model.WarehouseInfo;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.service.PaymentService;
import org.higherkindedj.example.order.service.ShippingService;
import org.higherkindedj.example.order.service.impl.InMemoryCustomerService;
import org.higherkindedj.example.order.service.impl.InMemoryDiscountService;
import org.higherkindedj.example.order.service.impl.InMemoryInventoryService;
import org.higherkindedj.example.order.service.impl.InMemoryNotificationService;
import org.higherkindedj.example.order.service.impl.InMemoryPaymentService;
import org.higherkindedj.example.order.service.impl.InMemoryShippingService;
import org.higherkindedj.example.order.workflow.ConfigurableOrderWorkflow;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ConfigurableOrderWorkflow} resilience granularity — the fix that stops the
 * non-idempotent payment being retried. Retry now wraps only the idempotent pre-flight; the
 * committing workflow (which charges the customer) runs exactly once.
 */
@DisplayName("ConfigurableOrderWorkflow — resilience granularity")
class ConfigurableOrderWorkflowTest {

  @Test
  @DisplayName("a valid order succeeds and charges the customer exactly once")
  void validOrderChargesOnce() {
    var payments = new CountingPaymentService();
    var workflow = workflowWith(payments, new InMemoryShippingService(), configWithRetries(3));

    var result = workflow.process(request()).run();

    assertThatEither(result).isRight();
    assertThat(payments.calls()).isEqualTo(1);
  }

  @Test
  @DisplayName("a transient failure after payment does NOT re-run the payment")
  void transientFailureAfterPaymentDoesNotDoubleCharge() {
    var payments = new CountingPaymentService();
    // Shipping (which runs after payment) fails transiently. With maxRetries=3, the old code
    // retried the whole workflow and would have charged three times.
    var workflow = workflowWith(payments, new FailingShippingService(), configWithRetries(3));

    var result = workflow.process(request()).run();

    assertThatEither(result)
        .isLeft()
        .hasLeftSatisfying(error -> assertThat(error).isInstanceOf(OrderError.SystemError.class));
    // The committing workflow ran once: payment was charged exactly once despite three allowed
    // retries.
    assertThat(payments.calls()).isEqualTo(1);
  }

  private static ConfigurableOrderWorkflow workflowWith(
      PaymentService paymentService, ShippingService shippingService, WorkflowConfig config) {
    return new ConfigurableOrderWorkflow(
        new InMemoryCustomerService(),
        new InMemoryInventoryService(),
        new InMemoryDiscountService(),
        paymentService,
        shippingService,
        new InMemoryNotificationService(),
        config);
  }

  private static OrderRequest request() {
    return new OrderRequest(
        "CUST-001",
        List.of(new OrderLineRequest("PROD-001", 1)),
        Optional.empty(),
        new ShippingAddress("Test User", "123 Test Street", "London", "SW1A 1AA", "GB"),
        new PaymentMethod.CreditCard("4000000000000002", "12", "2025", "123"));
  }

  private static WorkflowConfig configWithRetries(int maxRetries) {
    return new WorkflowConfig(
        new RetryConfig(maxRetries, Duration.ofMillis(5), Duration.ofMillis(20), 2.0),
        new TimeoutConfig(
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            Duration.ofSeconds(2)),
        // Partial fulfilment off: a failure must not be diverted to the partial-fulfilment path.
        new FeatureFlags(false, false, false),
        Environment.TESTING);
  }

  /**
   * Counts how many times a payment is actually charged, delegating the work to the real service.
   */
  private static final class CountingPaymentService implements PaymentService {
    private final PaymentService delegate = new InMemoryPaymentService();
    private final AtomicInteger calls = new AtomicInteger();

    int calls() {
      return calls.get();
    }

    @Override
    public Either<OrderError, PaymentConfirmation> processPayment(
        OrderId orderId, Money amount, PaymentMethod paymentMethod) {
      calls.incrementAndGet();
      return delegate.processPayment(orderId, amount, paymentMethod);
    }

    @Override
    public Either<OrderError, PaymentConfirmation> refundPayment(
        String transactionId, Money amount) {
      return delegate.refundPayment(transactionId, amount);
    }

    @Override
    public Either<OrderError, PaymentMethod> validatePaymentMethod(PaymentMethod paymentMethod) {
      return delegate.validatePaymentMethod(paymentMethod);
    }
  }

  /** Shipping service whose {@code createShipment} fails transiently (runs after payment). */
  private static final class FailingShippingService implements ShippingService {
    private final ShippingService delegate = new InMemoryShippingService();

    @Override
    public Either<OrderError, ValidatedShippingAddress> validateAddress(ShippingAddress address) {
      return delegate.validateAddress(address);
    }

    @Override
    public Either<OrderError, ShipmentInfo> createShipment(
        OrderId orderId, ValidatedShippingAddress address, List<ValidatedOrderLine> lines) {
      throw new UncheckedIOException(new IOException("transient shipping outage"));
    }

    @Override
    public Either<OrderError, Void> cancelShipment(String shipmentId) {
      return delegate.cancelShipment(shipmentId);
    }

    @Override
    public Either<OrderError, ShipmentStatus> getShipmentStatus(String trackingNumber) {
      return delegate.getShipmentStatus(trackingNumber);
    }

    @Override
    public Either<OrderError, ShipmentInfo> createShipmentFromWarehouse(
        OrderId orderId,
        WarehouseInfo warehouse,
        List<InventoryReservation.ReservedItem> items,
        ValidatedShippingAddress address) {
      return delegate.createShipmentFromWarehouse(orderId, warehouse, items, address);
    }
  }
}
