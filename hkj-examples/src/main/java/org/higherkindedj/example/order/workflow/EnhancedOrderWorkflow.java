// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.example.order.audit.AuditLog;
import org.higherkindedj.example.order.config.WorkflowConfig;
import org.higherkindedj.example.order.context.OrderContext;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.error.OrderError.SystemError;
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
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Enhanced order workflow demonstrating recent HKJ innovations.
 *
 * <p>This workflow extends the patterns from {@link OrderWorkflow} with:
 *
 * <ul>
 *   <li><b>Context propagation</b> - Uses {@link OrderContext} for trace IDs, tenant isolation,
 *       security, and deadline enforcement across all operations
 *   <li><b>Structured concurrency</b> - Uses {@link Scope} for parallel inventory checks with
 *       proper cancellation and timeout handling
 *   <li><b>Resource management</b> - Uses {@link Resource} for guaranteed cleanup of connections
 *       and reservations
 *   <li><b>Virtual thread execution</b> - Uses {@link VTaskPath} for scalable concurrent order
 *       processing
 * </ul>
 *
 * <h2>Context Propagation</h2>
 *
 * <p>All operations automatically inherit scoped values from the calling context:
 *
 * <pre>{@code
 * ScopedValue
 *     .where(OrderContext.TRACE_ID, traceId)
 *     .where(OrderContext.TENANT_ID, tenantId)
 *     .where(OrderContext.DEADLINE, deadline)
 *     .run(() -> workflow.process(request));
 * }</pre>
 *
 * <h2>Parallel Operations</h2>
 *
 * <p>Inventory checks run in parallel across multiple warehouses using structured concurrency:
 *
 * <pre>{@code
 * Scope.<InventoryReservation>anySucceed()
 *     .timeout(Duration.ofSeconds(10))
 *     .fork(warehouse1.reserve(orderId, lines))
 *     .fork(warehouse2.reserve(orderId, lines))
 *     .join();
 * }</pre>
 *
 * <h2>Resource Safety</h2>
 *
 * <p>Reservations are managed as resources with guaranteed cleanup:
 *
 * <pre>{@code
 * Resource<InventoryReservation> reservation = Resource.make(
 *     () -> inventoryService.reserve(orderId, lines),
 *     res -> inventoryService.releaseReservation(res.reservationId())
 * );
 * }</pre>
 *
 * @see OrderWorkflow for the basic workflow patterns
 * @see OrderContext for available scoped values
 * @see Scope for structured concurrency patterns
 * @see Resource for resource management patterns
 */
public class EnhancedOrderWorkflow {

  private final CustomerService customerService;
  private final InventoryService inventoryService;
  private final DiscountService discountService;
  private final PaymentService paymentService;
  private final ShippingService shippingService;
  private final NotificationService notificationService;
  private final WorkflowConfig config;

  /**
   * Creates an enhanced order workflow with the given services and config.
   *
   * @param customerService customer lookup and validation
   * @param inventoryService inventory reservation
   * @param discountService discount and promo code handling
   * @param paymentService payment processing
   * @param shippingService shipment creation
   * @param notificationService notification sending
   * @param config workflow configuration
   */
  public EnhancedOrderWorkflow(
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

  // =========================================================================
  // Main Entry Point - VTaskPath with Context
  // =========================================================================

  /**
   * Processes an order request using virtual threads and context propagation.
   *
   * <p>This method should be called within a scope where {@link OrderContext} values are bound. All
   * operations automatically inherit trace ID, tenant ID, and deadline from the calling context.
   *
   * <p><b>Context Requirements:</b>
   *
   * <ul>
   *   <li>{@link OrderContext#TRACE_ID} - Recommended for distributed tracing
   *   <li>{@link OrderContext#TENANT_ID} - Required for multi-tenant isolation
   *   <li>{@link OrderContext#DEADLINE} - Optional, enables timeout enforcement
   * </ul>
   *
   * @param request the order request to process
   * @return a VTaskPath that executes the workflow when run
   */
  public VTaskPath<Either<OrderError, OrderResult>> process(OrderRequest request) {
    var orderId = OrderId.generate();
    var customerId = new CustomerId(request.customerId());

    // Check deadline before starting
    return checkDeadline("order.start")
        .via(
            _ ->
                logWithContext("Processing order", Map.of("orderId", orderId.value()))
                    .then(() -> processWithResources(orderId, customerId, request)));
  }

  // =========================================================================
  // Resource-Managed Processing
  // =========================================================================

  /**
   * Processes the order with resource management for inventory reservation.
   *
   * <p>Uses the bracket pattern to ensure inventory is released if payment fails. Each step is
   * extracted to a helper method to avoid deep nesting.
   */
  private VTaskPath<Either<OrderError, OrderResult>> processWithResources(
      OrderId orderId, CustomerId customerId, OrderRequest request) {

    return validateShippingAddress(request.shippingAddress())
        .via(addressResult -> continueWithAddress(orderId, customerId, request, addressResult));
  }

  /** Continues processing after address validation. */
  private VTaskPath<Either<OrderError, OrderResult>> continueWithAddress(
      OrderId orderId,
      CustomerId customerId,
      OrderRequest request,
      Either<OrderError, ValidatedShippingAddress> addressResult) {

    return addressResult.fold(
        error -> Path.vtaskPure(Either.left(error)),
        validAddress ->
            lookupAndValidateCustomer(customerId)
                .via(
                    customerResult ->
                        continueWithCustomer(orderId, request, validAddress, customerResult)));
  }

  /** Continues processing after customer lookup. */
  private VTaskPath<Either<OrderError, OrderResult>> continueWithCustomer(
      OrderId orderId,
      OrderRequest request,
      ValidatedShippingAddress validAddress,
      Either<OrderError, Customer> customerResult) {

    return customerResult.fold(
        error -> Path.vtaskPure(Either.left(error)),
        customer ->
            buildValidatedOrder(orderId, request, customer, validAddress)
                .via(orderResult -> continueWithOrder(customer, orderResult)));
  }

  /** Continues processing after order validation. */
  private VTaskPath<Either<OrderError, OrderResult>> continueWithOrder(
      Customer customer, Either<OrderError, ValidatedOrder> orderResult) {

    return orderResult.fold(
        error -> Path.vtaskPure(Either.left(error)),
        order -> processWithReservation(order, customer));
  }

  /**
   * Processes order with managed inventory reservation.
   *
   * <p>The reservation is automatically released if subsequent steps fail. On success, the
   * reservation is confirmed instead of released.
   */
  private VTaskPath<Either<OrderError, OrderResult>> processWithReservation(
      ValidatedOrder order, Customer customer) {

    // Track whether the reservation was confirmed (success) or needs release (failure)
    var confirmed = new AtomicBoolean(false);

    // Create a resource for the inventory reservation
    // This ensures cleanup even if payment or shipping fails
    Resource<InventoryReservation> reservationResource =
        Resource.make(
            () -> {
              // Acquire: Reserve inventory
              Either<OrderError, InventoryReservation> result =
                  inventoryService.reserve(order.orderId(), order.lines());
              return result.fold(
                  error -> {
                    throw new ReservationException(error);
                  },
                  res -> res);
            },
            reservation -> {
              // Release: Only release if not confirmed (i.e., on failure)
              if (!confirmed.get()) {
                logSync("Releasing reservation " + reservation.reservationId());
                inventoryService.releaseReservation(reservation.reservationId());
              }
            });

    // Use the resource with proper cleanup
    return Path.vtaskPath(
        reservationResource.use(
            reservation ->
                processAfterReservation(order, customer, reservation)
                    .run()
                    .map(
                        result ->
                            result.fold(
                                // On failure, release is handled by Resource cleanup
                                Either::left,
                                success -> {
                                  // On success, confirm the reservation and prevent release
                                  inventoryService.confirmReservation(reservation.reservationId());
                                  confirmed.set(true);
                                  return Either.right(success);
                                }))));
  }

  /** Process steps after inventory is reserved: discount, payment, shipping, notification. */
  private VTaskPath<Either<OrderError, OrderResult>> processAfterReservation(
      ValidatedOrder order, Customer customer, InventoryReservation reservation) {

    return applyDiscounts(order, customer)
        .via(
            discountResult ->
                discountResult.fold(
                    error -> Path.vtaskPure(Either.<OrderError, OrderResult>left(error)),
                    discount ->
                        processPayment(order, discount)
                            .via(
                                paymentResult ->
                                    paymentResult.fold(
                                        error ->
                                            Path.vtaskPure(
                                                Either.<OrderError, OrderResult>left(error)),
                                        payment ->
                                            createShipment(order)
                                                .via(
                                                    shipmentResult ->
                                                        shipmentResult.fold(
                                                            error ->
                                                                Path.vtaskPure(
                                                                    Either
                                                                        .<OrderError, OrderResult>
                                                                            left(error)),
                                                            shipment ->
                                                                sendNotifications(
                                                                        order, customer, discount)
                                                                    .map(
                                                                        notificationResult ->
                                                                            buildOrderResult(
                                                                                order,
                                                                                discount,
                                                                                payment,
                                                                                shipment,
                                                                                reservation,
                                                                                notificationResult
                                                                                    .fold(
                                                                                        err ->
                                                                                            NotificationResult
                                                                                                .none(),
                                                                                        success ->
                                                                                            success)))))))));
  }

  // =========================================================================
  // Parallel Inventory Check with Scope
  // =========================================================================

  /**
   * Reserves inventory using parallel checks across multiple warehouses.
   *
   * <p>This demonstrates structured concurrency with {@link Scope}: - First successful reservation
   * wins - Other tasks are automatically cancelled - Timeout prevents indefinite waiting - Context
   * values propagate to all forked tasks
   *
   * @param orderId the order ID
   * @param lines the order lines to reserve
   * @return VTaskPath with reservation result
   */
  public VTaskPath<Either<OrderError, InventoryReservation>> reserveInventoryParallel(
      OrderId orderId, List<ValidatedOrderLine> lines) {

    // Create tasks for parallel warehouse checks
    // Each task automatically inherits the scoped values (traceId, tenantId, etc.)
    VTask<InventoryReservation> warehouse1 =
        VTask.of(
            () -> {
              logSync("Checking warehouse 1 [trace=" + OrderContext.shortTraceId() + "]");
              Thread.sleep(50); // Simulate network latency
              return inventoryService
                  .reserve(orderId, lines)
                  .fold(
                      error -> {
                        throw new ReservationException(error);
                      },
                      res -> res);
            });

    VTask<InventoryReservation> warehouse2 =
        VTask.of(
            () -> {
              logSync("Checking warehouse 2 [trace=" + OrderContext.shortTraceId() + "]");
              Thread.sleep(75); // Simulate slightly slower warehouse
              return inventoryService
                  .reserve(orderId, lines)
                  .fold(
                      error -> {
                        throw new ReservationException(error);
                      },
                      res -> res);
            });

    VTask<InventoryReservation> warehouse3 =
        VTask.of(
            () -> {
              logSync("Checking warehouse 3 [trace=" + OrderContext.shortTraceId() + "]");
              Thread.sleep(100); // Simulate even slower warehouse
              return inventoryService
                  .reserve(orderId, lines)
                  .fold(
                      error -> {
                        throw new ReservationException(error);
                      },
                      res -> res);
            });

    // Race all warehouses - first to succeed wins
    VTask<InventoryReservation> raceResult =
        Scope.<InventoryReservation>anySucceed()
            .timeout(getRemainingTimeout())
            .fork(warehouse1)
            .fork(warehouse2)
            .fork(warehouse3)
            .join();

    // Convert to VTaskPath with proper error handling
    return Path.vtask(
        () -> {
          Try<InventoryReservation> result = raceResult.runSafe();
          return result.fold(
              Either::right,
              error -> {
                if (error instanceof ReservationException re) {
                  return Either.left(re.orderError);
                }
                return Either.left(
                    SystemError.fromException("Parallel inventory check failed", error));
              });
        });
  }

  // =========================================================================
  // Individual Workflow Steps
  // =========================================================================

  private VTaskPath<Either<OrderError, ValidatedShippingAddress>> validateShippingAddress(
      ShippingAddress address) {
    return Path.vtask(() -> shippingService.validateAddress(address));
  }

  private VTaskPath<Either<OrderError, Customer>> lookupAndValidateCustomer(CustomerId customerId) {
    return Path.vtask(
        () -> {
          logSync("Looking up customer [trace=" + OrderContext.shortTraceId() + "]");
          return customerService
              .findById(customerId)
              .flatMap(customer -> customerService.validateEligibility(customer));
        });
  }

  private VTaskPath<Either<OrderError, ValidatedOrder>> buildValidatedOrder(
      OrderId orderId,
      OrderRequest request,
      Customer customer,
      ValidatedShippingAddress validAddress) {

    // Build validated order lines
    var lines =
        request.lines().stream()
            .map(line -> createValidatedLine(line.productId(), line.quantity()))
            .toList();

    var subtotal = ValidatedOrder.calculateSubtotal(lines);

    // Validate promo code if present
    return validatePromoCodeIfPresent(request.promoCode())
        .map(
            promoResult ->
                promoResult.map(
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
                            Instant.now())));
  }

  private VTaskPath<Either<OrderError, Optional<PromoCode>>> validatePromoCodeIfPresent(
      Optional<String> promoCode) {
    return promoCode
        .<VTaskPath<Either<OrderError, Optional<PromoCode>>>>map(
            code ->
                Path.vtask(() -> discountService.validatePromoCode(code))
                    .map(result -> result.map(Optional::of)))
        .orElse(Path.vtaskPure(Either.right(Optional.empty())));
  }

  private ValidatedOrderLine createValidatedLine(String productIdStr, int quantity) {
    var productId = new ProductId(productIdStr);
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

  private VTaskPath<Either<OrderError, DiscountResult>> applyDiscounts(
      ValidatedOrder order, Customer customer) {
    return order
        .promoCode()
        .<VTaskPath<Either<OrderError, DiscountResult>>>map(
            code -> Path.vtask(() -> discountService.applyPromoCode(code, order.subtotal())))
        .orElseGet(
            () -> {
              if (config.featureFlags().enableLoyaltyDiscounts()) {
                return Path.vtask(
                    () -> discountService.calculateLoyaltyDiscount(customer, order.subtotal()));
              }
              return Path.vtaskPure(Either.right(DiscountResult.noDiscount(order.subtotal())));
            });
  }

  private VTaskPath<Either<OrderError, PaymentConfirmation>> processPayment(
      ValidatedOrder order, DiscountResult discount) {

    return checkDeadline("payment")
        .via(
            _ ->
                logWithContext("Processing payment", Map.of("amount", discount.finalTotal()))
                    .then(
                        () ->
                            Path.vtask(
                                () ->
                                    paymentService.processPayment(
                                        order.orderId(),
                                        discount.finalTotal(),
                                        order.paymentMethod()))));
  }

  private VTaskPath<Either<OrderError, ShipmentInfo>> createShipment(ValidatedOrder order) {
    return Path.vtask(
        () ->
            shippingService.createShipment(
                order.orderId(), order.shippingAddress(), order.lines()));
  }

  private VTaskPath<Either<OrderError, NotificationResult>> sendNotifications(
      ValidatedOrder order, Customer customer, DiscountResult discount) {
    // Notifications are non-critical - we recover from failures
    return Path.vtask(
            () ->
                notificationService.sendOrderConfirmation(
                    order.orderId(), customer, discount.finalTotal()))
        .handleError(
            error -> {
              logSync("Notification failed (non-critical): " + error.getMessage());
              return Either.right(NotificationResult.none());
            });
  }

  // =========================================================================
  // Result Building
  // =========================================================================

  private Either<OrderError, OrderResult> buildOrderResult(
      ValidatedOrder order,
      DiscountResult discount,
      PaymentConfirmation payment,
      ShipmentInfo shipment,
      InventoryReservation reservation,
      NotificationResult notification) {

    var auditLog =
        AuditLog.EMPTY
            .append(
                AuditLog.of(
                    "ORDER_CREATED",
                    "Order "
                        + order.orderId()
                        + " created [trace="
                        + OrderContext.shortTraceId()
                        + "]"))
            .append(
                AuditLog.of(
                    "INVENTORY_RESERVED",
                    "Reservation " + reservation.reservationId() + " created"))
            .append(AuditLog.of("PAYMENT_PROCESSED", "Transaction " + payment.transactionId()))
            .append(AuditLog.of("SHIPMENT_CREATED", "Tracking " + shipment.trackingNumber()));

    return Either.right(
        new OrderResult(
            order.orderId(),
            order.customerId(),
            discount.finalTotal(),
            payment.transactionId(),
            shipment.trackingNumber(),
            shipment.estimatedDelivery(),
            auditLog));
  }

  // =========================================================================
  // Context Utilities
  // =========================================================================

  /** Checks if the deadline has been exceeded and fails fast if so. */
  private VTaskPath<Either<OrderError, Void>> checkDeadline(String operation) {
    return Path.vtask(
        () -> {
          if (OrderContext.isDeadlineExceeded()) {
            return Either.left(SystemError.timeout(operation));
          }
          return Either.right(null);
        });
  }

  /** Logs a message with context information. */
  private VTaskPath<Either<OrderError, Void>> logWithContext(String message, Map<String, ?> data) {
    return Path.vtask(
        () -> {
          String traceId = OrderContext.shortTraceId();
          String tenantId = OrderContext.TENANT_ID.isBound() ? OrderContext.TENANT_ID.get() : "?";
          System.out.printf("[%s] [tenant=%s] %s %s%n", traceId, tenantId, message, data);
          return Either.right(null);
        });
  }

  /** Synchronous logging helper. */
  private void logSync(String message) {
    String traceId = OrderContext.shortTraceId();
    System.out.printf("[%s] %s%n", traceId, message);
  }

  /** Gets remaining timeout from deadline, or default. */
  private Duration getRemainingTimeout() {
    Duration remaining = OrderContext.remainingTime();
    // Cap at 30 seconds for individual operations
    return remaining.compareTo(Duration.ofSeconds(30)) > 0 ? Duration.ofSeconds(30) : remaining;
  }

  // =========================================================================
  // Exception Wrapper
  // =========================================================================

  /** Exception wrapper for reservation errors in parallel operations. */
  private static class ReservationException extends RuntimeException {
    final OrderError orderError;

    ReservationException(OrderError orderError) {
      super(orderError.message());
      this.orderError = orderError;
    }
  }
}
