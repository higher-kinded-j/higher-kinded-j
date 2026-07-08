// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Enhanced order workflow demonstrating recent HKJ innovations.
 *
 * <p>This workflow extends the patterns from {@link OrderWorkflow} with:
 *
 * <ul>
 *   <li><b>Context propagation</b> - Uses {@link OrderContext} for trace IDs, tenant isolation,
 *       security, and deadline enforcement across all operations
 *   <li><b>Typed async railway</b> - Uses {@link VResultPath} for the stacked shape {@code
 *       VTask<Either<OrderError, A>>}: {@code via} chains dependent steps on the success channel, a
 *       {@code Left} from any step short-circuits the rest, and {@code mapError} reshapes the error
 *       channel - with no {@code Kind} widening, transformer, or bridge helpers surfacing
 *   <li><b>Outcome-aware concurrency</b> - Uses {@link VResultPath#firstSuccess(List)} for parallel
 *       inventory checks: typed failures stay in the value channel and are collected rather than
 *       thrown, and the first successful warehouse wins
 *   <li><b>Outcome-aware resources</b> - Uses {@link VResultPath#bracketOutcome} so the release
 *       action sees the {@link Either} outcome and decides confirm-versus-release from the result,
 *       not from a mutable flag
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
 * <p>Inventory checks race in parallel across multiple warehouses; a warehouse failing with a typed
 * {@code Left} does not abort the race:
 *
 * <pre>{@code
 * VResultPath.firstSuccess(List.of(warehouse1, warehouse2, warehouse3))
 *     .mapError(NonEmptyList::head) // all failed: surface the first typed error
 *     .withTimeout(remaining, () -> SystemError.timeout("parallel inventory reservation"));
 * }</pre>
 *
 * <h2>Resource Safety</h2>
 *
 * <p>Reservations are bracketed with an outcome-aware release, so compensation is decided from the
 * {@code Either} result rather than a side flag:
 *
 * <pre>{@code
 * VResultPath.bracketOutcome(
 *     reserveInventory(order),                        // acquire
 *     reservation -> payAndShip(order, reservation),  // use
 *     (reservation, outcome) -> outcome.fold(         // release sees the outcome
 *         error -> release(reservation),
 *         success -> confirm(reservation)),
 *     defect -> SystemError.fromException("Order processing failed", defect));
 * }</pre>
 *
 * @see OrderWorkflow for the basic workflow patterns
 * @see OrderContext for available scoped values
 * @see VResultPath for the async-with-typed-error path
 */
public class EnhancedOrderWorkflow {

  private final CustomerService customerService;
  private final InventoryService inventoryService;
  private final DiscountService discountService;
  private final PaymentService paymentService;
  private final ShippingService shippingService;
  private final NotificationService notificationService;
  private final WorkflowConfig config;

  // Library resilience (hkt.resilience) applied to the idempotent customer lookup. These are
  // VTask-native, so they compose directly onto the VTaskPath via withCircuitBreaker/withRetry —
  // no hand-rolled retry loop. The breaker is shared across calls so it can trip open on a
  // persistently failing customer service.
  private final RetryPolicy customerLookupRetry =
      RetryPolicy.exponentialBackoff(3, Duration.ofMillis(50));
  private final CircuitBreaker customerLookupBreaker = CircuitBreaker.withDefaults();

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
  // Main Entry Point - VResultPath with Context
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
   * @return a lazy VResultPath describing the workflow; execute the carrier from {@link
   *     VResultPath#run()} at the boundary
   */
  public VResultPath<OrderError, OrderResult> process(OrderRequest request) {
    var orderId = OrderId.generate();
    var customerId = new CustomerId(request.customerId());

    // Check deadline before starting; a Left here short-circuits the whole railway.
    return checkDeadline("order.start")
        .via(
            _ ->
                logWithContext("Processing order", Map.of("orderId", orderId.value()))
                    .then(() -> processWithResources(orderId, customerId, request)));
  }

  // =========================================================================
  // The Gather Railway - validate, look up, build, then fulfil
  // =========================================================================

  /**
   * Gathers and validates everything the order needs, then hands over to the reservation-bracketed
   * fulfilment phase.
   *
   * <p>Async (VTask) and typed error (Either) are one railway here: each step is a {@code
   * VResultPath<OrderError, X>}, {@code via} chains them, and the first {@code Left} short-circuits
   * the rest. Steps nest only where a later step still needs an earlier binding (address and
   * customer feed the order build) - no transformer, {@code Kind} widening, or bridge helpers are
   * involved.
   */
  private VResultPath<OrderError, OrderResult> processWithResources(
      OrderId orderId, CustomerId customerId, OrderRequest request) {

    return validateShippingAddress(request.shippingAddress())
        .via(
            address ->
                lookupAndValidateCustomer(customerId)
                    .via(
                        customer ->
                            buildValidatedOrder(orderId, request, customer, address)
                                .via(order -> processWithReservation(order, customer))));
  }

  /**
   * Processes the order with an outcome-aware bracket around the inventory reservation.
   *
   * <p>{@link VResultPath#bracketOutcome} lets the release action see the {@link Either} outcome of
   * the fulfilment phase: a {@code Right} confirms the reservation, a {@code Left} releases it.
   * There is no mutable "confirmed" flag - compensation is decided from the result itself. A defect
   * thrown mid-fulfilment is first typed as a {@link SystemError}, so the release always observes a
   * real outcome (and releases the reservation).
   */
  private VResultPath<OrderError, OrderResult> processWithReservation(
      ValidatedOrder order, Customer customer) {

    return VResultPath.bracketOutcome(
        // Acquire: reserve inventory; a Left skips use and release entirely.
        Path.vresultDefer(() -> inventoryService.reserve(order.orderId(), order.lines())),
        // Use: discount -> payment -> shipment -> notification.
        reservation -> processAfterReservation(order, customer, reservation),
        // Release: decide confirm-versus-release from the outcome.
        (reservation, outcome) ->
            outcome.fold(
                _ ->
                    VTask.exec(
                        () -> {
                          logSync("Releasing reservation " + reservation.reservationId());
                          inventoryService.releaseReservation(reservation.reservationId());
                        }),
                _ ->
                    VTask.exec(
                        () -> inventoryService.confirmReservation(reservation.reservationId()))),
        defect -> SystemError.fromException("Order processing failed", defect));
  }

  /** Process steps after inventory is reserved: discount, payment, shipping, notification. */
  private VResultPath<OrderError, OrderResult> processAfterReservation(
      ValidatedOrder order, Customer customer, InventoryReservation reservation) {

    // The same railway as the gather phase: discount -> payment -> shipment -> notification,
    // short-circuiting on the first Left. Notifications are non-critical and are recovered to a
    // value inside #sendNotifications, so they never abort the order here - the step runs purely
    // for its side effect and its bound value is ignored with `_`.
    return applyDiscounts(order, customer)
        .via(
            discount ->
                processPayment(order, discount)
                    .via(
                        payment ->
                            createShipment(order)
                                .via(
                                    shipment ->
                                        sendNotifications(order, customer, discount)
                                            .map(
                                                _ ->
                                                    buildOrderResultValue(
                                                        order,
                                                        discount,
                                                        payment,
                                                        shipment,
                                                        reservation)))));
  }

  // =========================================================================
  // Parallel Inventory Check with firstSuccess
  // =========================================================================

  /**
   * Reserves inventory using parallel checks across multiple warehouses.
   *
   * <p>This demonstrates outcome-aware structured concurrency with {@link
   * VResultPath#firstSuccess(List)}: the first successful reservation wins and the other tasks are
   * cancelled, while a warehouse failing with a typed {@code Left} does not abort the race - typed
   * errors stay in the value channel and are collected. Only when every warehouse fails does the
   * race fail, and the first typed error is surfaced. Context values propagate to all forked tasks,
   * and the deadline bounds the whole race as a typed timeout error.
   *
   * @param orderId the order ID
   * @param lines the order lines to reserve
   * @return a VResultPath producing the winning reservation
   */
  public VResultPath<OrderError, InventoryReservation> reserveInventoryParallel(
      OrderId orderId, List<ValidatedOrderLine> lines) {

    // Each candidate automatically inherits the scoped values (traceId, tenantId, etc.).
    var candidates =
        List.of(
            warehouseReservation(1, Duration.ofMillis(50), orderId, lines),
            warehouseReservation(2, Duration.ofMillis(75), orderId, lines),
            warehouseReservation(3, Duration.ofMillis(100), orderId, lines));

    // Race all warehouses - first Right wins; all-Left surfaces the first warehouse's error.
    return VResultPath.firstSuccess(candidates)
        .peekLeft(errors -> logSync("All warehouses failed: " + errors.toJavaList()))
        .mapError(NonEmptyList::head)
        .withTimeout(
            getRemainingTimeout(), () -> SystemError.timeout("parallel inventory reservation"));
  }

  /** One warehouse's reservation attempt, with simulated network latency. */
  private VResultPath<OrderError, InventoryReservation> warehouseReservation(
      int warehouse, Duration latency, OrderId orderId, List<ValidatedOrderLine> lines) {
    return Path.vresult(
        VTask.of(
            () -> {
              logSync(
                  "Checking warehouse "
                      + warehouse
                      + " [trace="
                      + OrderContext.shortTraceId()
                      + "]");
              Thread.sleep(latency.toMillis()); // Simulate network latency
              return inventoryService.reserve(orderId, lines);
            }));
  }

  // =========================================================================
  // Individual Workflow Steps
  // =========================================================================

  private VResultPath<OrderError, ValidatedShippingAddress> validateShippingAddress(
      ShippingAddress address) {
    return Path.vresultDefer(() -> shippingService.validateAddress(address));
  }

  private VResultPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId customerId) {
    // Idempotent read: protect it with the library circuit breaker + retry so a flaky customer
    // service is retried with backoff and the breaker trips open after repeated failures. Retry
    // engages only when the lookup *throws* (a transient infra failure); a business `Left` (e.g.
    // customer-not-found) is returned as-is and never retried. Safe precisely because the lookup
    // has no side effects — unlike payment, which §1.3 never retries. The resilience combinators
    // live on the VTask layer, so the protected carrier is lifted into the typed-error path.
    return Path.vresult(
        Path.vtask(
                () -> {
                  logSync("Looking up customer [trace=" + OrderContext.shortTraceId() + "]");
                  return customerService
                      .findById(customerId)
                      .flatMap(customerService::validateEligibility);
                })
            .withCircuitBreaker(customerLookupBreaker)
            .withRetry(customerLookupRetry)
            .run());
  }

  private VResultPath<OrderError, ValidatedOrder> buildValidatedOrder(
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

  private VResultPath<OrderError, Optional<PromoCode>> validatePromoCodeIfPresent(
      Optional<String> promoCode) {
    return promoCode
        .<VResultPath<OrderError, Optional<PromoCode>>>map(
            code ->
                Path.vresultDefer(() -> discountService.validatePromoCode(code)).map(Optional::of))
        .orElseGet(() -> Path.vresultRight(Optional.empty()));
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

  private VResultPath<OrderError, DiscountResult> applyDiscounts(
      ValidatedOrder order, Customer customer) {
    return order
        .promoCode()
        .<VResultPath<OrderError, DiscountResult>>map(
            code -> Path.vresultDefer(() -> discountService.applyPromoCode(code, order.subtotal())))
        .orElseGet(
            () -> {
              if (config.featureFlags().enableLoyaltyDiscounts()) {
                return Path.vresultDefer(
                    () -> discountService.calculateLoyaltyDiscount(customer, order.subtotal()));
              }
              return Path.vresultRight(DiscountResult.noDiscount(order.subtotal()));
            });
  }

  private VResultPath<OrderError, PaymentConfirmation> processPayment(
      ValidatedOrder order, DiscountResult discount) {

    return checkDeadline("payment")
        .via(
            _ ->
                logWithContext("Processing payment", Map.of("amount", discount.finalTotal()))
                    .then(
                        () ->
                            Path.vresultDefer(
                                () ->
                                    paymentService.processPayment(
                                        order.orderId(),
                                        discount.finalTotal(),
                                        order.paymentMethod()))));
  }

  private VResultPath<OrderError, ShipmentInfo> createShipment(ValidatedOrder order) {
    return Path.vresultDefer(
        () ->
            shippingService.createShipment(
                order.orderId(), order.shippingAddress(), order.lines()));
  }

  private VResultPath<OrderError, NotificationResult> sendNotifications(
      ValidatedOrder order, Customer customer, DiscountResult discount) {
    // Notifications are non-critical: a failure (a Left result *or* a thrown error) is downgraded
    // to NotificationResult.none() so it never short-circuits the order railway. The step
    // therefore always yields a Right.
    return Path.vresult(
        VTask.of(
                () ->
                    notificationService.sendOrderConfirmation(
                        order.orderId(), customer, discount.finalTotal()))
            .<Either<OrderError, NotificationResult>>map(
                result ->
                    Either.right(result.fold(_ -> NotificationResult.none(), success -> success)))
            .recover(
                error -> {
                  logSync("Notification failed (non-critical): " + error.getMessage());
                  return Either.right(NotificationResult.none());
                }));
  }

  // =========================================================================
  // Result Building
  // =========================================================================

  private OrderResult buildOrderResultValue(
      ValidatedOrder order,
      DiscountResult discount,
      PaymentConfirmation payment,
      ShipmentInfo shipment,
      InventoryReservation reservation) {

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

    return new OrderResult(
        order.orderId(),
        order.customerId(),
        discount.finalTotal(),
        payment.transactionId(),
        shipment.trackingNumber(),
        shipment.estimatedDelivery(),
        auditLog);
  }

  // =========================================================================
  // Context Utilities
  // =========================================================================

  /** Checks if the deadline has been exceeded and fails fast if so. */
  private VResultPath<OrderError, Unit> checkDeadline(String operation) {
    return Path.vresultDefer(
        () -> {
          if (OrderContext.isDeadlineExceeded()) {
            return Either.left(SystemError.timeout(operation));
          }
          // Unit.INSTANCE rather than a null Right: success carries no value, but the success
          // channel stays non-null and total.
          return Either.right(Unit.INSTANCE);
        });
  }

  /** Logs a message with context information. */
  private VResultPath<OrderError, Unit> logWithContext(String message, Map<String, ?> data) {
    return Path.vresultDefer(
        () -> {
          String traceId = OrderContext.shortTraceId();
          String tenantId = OrderContext.TENANT_ID.isBound() ? OrderContext.TENANT_ID.get() : "?";
          System.out.printf("[%s] [tenant=%s] %s %s%n", traceId, tenantId, message, data);
          return Either.right(Unit.INSTANCE);
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
}
