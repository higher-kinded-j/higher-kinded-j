// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.order.audit.AuditLog;
import org.higherkindedj.example.order.config.WorkflowConfig;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.DiscountResult;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.NotificationResult;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.OrderResult;
import org.higherkindedj.example.order.model.PaymentConfirmation;
import org.higherkindedj.example.order.model.Product;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.model.ValidatedOrder;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.example.order.model.value.PromoCode;
import org.higherkindedj.example.order.service.CustomerService;
import org.higherkindedj.example.order.service.DiscountService;
import org.higherkindedj.example.order.service.InventoryService;
import org.higherkindedj.example.order.service.NotificationService;
import org.higherkindedj.example.order.service.PaymentService;
import org.higherkindedj.example.order.service.ShippingService;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.expression.ForPath;

/**
 * The main order processing workflow.
 *
 * <p>This class demonstrates the power of ForPath for composing complex multi-step workflows in a
 * readable, declarative style. Each step is chained using for-comprehension syntax, with automatic
 * error propagation.
 *
 * <h2>Workflow Steps</h2>
 *
 * <ol>
 *   <li>Validate order request and shipping address
 *   <li>Look up customer and verify eligibility
 *   <li>Resolve product details for all line items
 *   <li>Check and reserve inventory
 *   <li>Apply discounts (promo codes and loyalty)
 *   <li>Process payment
 *   <li>Create shipment
 *   <li>Send confirmation notifications
 * </ol>
 */
public class OrderWorkflow {

  private final CustomerService customerService;
  private final InventoryService inventoryService;
  private final DiscountService discountService;
  private final PaymentService paymentService;
  private final ShippingService shippingService;
  private final NotificationService notificationService;
  private final WorkflowConfig config;

  /** Creates a new order workflow with the given services and config. */
  public OrderWorkflow(
      CustomerService customerService,
      InventoryService inventoryService,
      DiscountService discountService,
      PaymentService paymentService,
      ShippingService shippingService,
      NotificationService notificationService,
      WorkflowConfig config) {
    this.customerService = customerService;
    this.inventoryService = inventoryService;
    this.discountService = discountService;
    this.paymentService = paymentService;
    this.shippingService = shippingService;
    this.notificationService = notificationService;
    this.config = config;
  }

  /** Creates a new order workflow with default config. */
  public OrderWorkflow(
      CustomerService customerService,
      InventoryService inventoryService,
      DiscountService discountService,
      PaymentService paymentService,
      ShippingService shippingService,
      NotificationService notificationService) {
    this(
        customerService,
        inventoryService,
        discountService,
        paymentService,
        shippingService,
        notificationService,
        WorkflowConfig.defaults());
  }

  /**
   * Processes an order request through the complete workflow.
   *
   * <p>Uses via() chains for composing workflow steps since EitherPath ForPath is limited to 3
   * steps. This approach provides the same error propagation with explicit chaining.
   *
   * @param request the order request to process
   * @return either an error or the successful order result
   */
  public EitherPath<OrderError, OrderResult> process(OrderRequest request) {
    var orderId = OrderId.generate();
    var customerId = new CustomerId(request.customerId());

    return validateShippingAddress(request.shippingAddress())
        .via(
            validAddress ->
                lookupAndValidateCustomer(customerId)
                    .via(
                        customer ->
                            buildValidatedOrder(orderId, request, customer, validAddress)
                                .via(order -> processOrderCore(order, customer))));
  }

  // -------------------------------------------------------------------------
  // Workflow Steps
  // -------------------------------------------------------------------------

  private EitherPath<OrderError, ValidatedShippingAddress> validateShippingAddress(
      ShippingAddress address) {
    return Path.either(shippingService.validateAddress(address));
  }

  private EitherPath<OrderError, Customer> lookupCustomer(CustomerId customerId) {
    return Path.either(customerService.findById(customerId));
  }

  private EitherPath<OrderError, Customer> validateCustomerEligibility(Customer customer) {
    return Path.either(customerService.validateEligibility(customer));
  }

  private EitherPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId customerId) {
    return ForPath.from(lookupCustomer(customerId))
        .from(this::validateCustomerEligibility)
        .yield((found, validated) -> validated);
  }

  private EitherPath<OrderError, ValidatedOrder> buildValidatedOrder(
      OrderId orderId,
      OrderRequest request,
      Customer customer,
      ValidatedShippingAddress validAddress) {
    // Build validated order lines from request
    var lines =
        request.lines().stream()
            .map(line -> createValidatedLine(line.productId(), line.quantity()))
            .toList();

    var subtotal = ValidatedOrder.calculateSubtotal(lines);

    // Validate promo code if present - fail if invalid
    return validatePromoCodeIfPresent(request.promoCode())
        .map(
            validatedPromoCode ->
                new ValidatedOrder(
                    orderId,
                    customer.id(),
                    customer,
                    lines,
                    validatedPromoCode,
                    validAddress,
                    request.paymentMethod(),
                    subtotal,
                    Instant.now()));
  }

  private EitherPath<OrderError, Optional<PromoCode>> validatePromoCodeIfPresent(
      Optional<String> promoCode) {
    return promoCode
        .<EitherPath<OrderError, Optional<PromoCode>>>map(
            code -> Path.either(discountService.validatePromoCode(code)).map(Optional::of))
        .orElse(Path.right(Optional.empty()));
  }

  private ValidatedOrderLine createValidatedLine(String productIdStr, int quantity) {
    var productId = new ProductId(productIdStr);
    // Placeholder product - in real implementation, would fetch from ProductService
    var product =
        new Product(
            productId,
            "Product " + productIdStr,
            "Description",
            Money.gbp("10.00"),
            "General",
            true);
    return ValidatedOrderLine.of(productId, product, quantity);
  }

  private EitherPath<OrderError, InventoryReservation> reserveInventory(
      OrderId orderId, List<ValidatedOrderLine> lines) {
    return Path.either(inventoryService.reserve(orderId, lines));
  }

  private EitherPath<OrderError, DiscountResult> applyDiscounts(
      ValidatedOrder order, Customer customer) {
    return order
        .promoCode()
        .<EitherPath<OrderError, DiscountResult>>map(
            code -> Path.either(discountService.applyPromoCode(code, order.subtotal())))
        .orElseGet(
            () -> {
              if (config.featureFlags().enableLoyaltyDiscounts()) {
                return Path.either(
                    discountService.calculateLoyaltyDiscount(customer, order.subtotal()));
              }
              return Path.right(DiscountResult.noDiscount(order.subtotal()));
            });
  }

  private EitherPath<OrderError, PaymentConfirmation> processPayment(
      ValidatedOrder order, DiscountResult discount) {
    return Path.either(
        paymentService.processPayment(
            order.orderId(), discount.finalTotal(), order.paymentMethod()));
  }

  private EitherPath<OrderError, ShipmentInfo> createShipment(
      ValidatedOrder order, ValidatedShippingAddress address) {
    return Path.either(shippingService.createShipment(order.orderId(), address, order.lines()));
  }

  private EitherPath<OrderError, NotificationResult> sendNotifications(
      ValidatedOrder order, Customer customer, DiscountResult discount) {
    return Path.either(
            notificationService.sendOrderConfirmation(
                order.orderId(), customer, discount.finalTotal()))
        .recoverWith(error -> Path.right(NotificationResult.none()));
  }

  private OrderResult buildOrderResult(
      ValidatedOrder order,
      DiscountResult discount,
      PaymentConfirmation payment,
      ShipmentInfo shipment,
      NotificationResult notification) {
    var auditLog =
        AuditLog.EMPTY
            .append(AuditLog.of("ORDER_CREATED", "Order " + order.orderId() + " created"))
            .append(AuditLog.of("PAYMENT_PROCESSED", "Transaction " + payment.transactionId()))
            .append(AuditLog.of("SHIPMENT_CREATED", "Tracking " + shipment.trackingNumber()));

    return new OrderResult(
        order.orderId(),
        order.customerId(),
        discount.finalTotal(),
        payment.transactionId(),
        shipment.trackingNumber(),
        shipment.estimatedDelivery(),
        auditLog);
  }

  // -------------------------------------------------------------------------
  // Helper for core processing
  // -------------------------------------------------------------------------

  private EitherPath<OrderError, OrderResult> processOrderCore(
      ValidatedOrder order, Customer customer) {
    return reserveInventory(order.orderId(), order.lines())
        .via(
            reservation ->
                applyDiscounts(order, customer)
                    .via(
                        discount ->
                            processPayment(order, discount)
                                .via(
                                    payment ->
                                        createShipment(order, order.shippingAddress())
                                            .via(
                                                shipment ->
                                                    sendNotifications(order, customer, discount)
                                                        .map(
                                                            notification ->
                                                                buildOrderResult(
                                                                    order,
                                                                    discount,
                                                                    payment,
                                                                    shipment,
                                                                    notification))))));
  }
}
