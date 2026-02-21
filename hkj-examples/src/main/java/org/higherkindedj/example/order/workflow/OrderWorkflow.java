// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

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
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.optics.Lens;

/**
 * The main order processing workflow.
 *
 * <p>This class demonstrates the {@code For} → {@code toState()} → {@code ForState} pattern for
 * composing complex multi-step workflows. The workflow proceeds in two phases:
 *
 * <ol>
 *   <li><b>Gather phase</b> (For comprehension, steps 1-3): Accumulate the initial values —
 *       validated address, customer, and order — using tuple positions. At arity 3, tuple access is
 *       still clear.
 *   <li><b>Enrich phase</b> (ForState, steps 4-8): The {@code toState()} bridge constructs a {@link
 *       ProcessingState} from the gathered values. Subsequent steps use {@code fromThen()} with
 *       lenses, accessing earlier results by name ({@code s.order()}, {@code s.customer()}) rather
 *       than by position ({@code t._3()}, {@code t._2()}).
 * </ol>
 *
 * <h2>Workflow Steps</h2>
 *
 * <ol>
 *   <li>Validate shipping address
 *   <li>Look up customer and verify eligibility
 *   <li>Build validated order (with promo code validation)
 *   <li>Reserve inventory
 *   <li>Apply discounts (promo codes and loyalty)
 *   <li>Process payment
 *   <li>Create shipment
 *   <li>Send confirmation notifications
 * </ol>
 *
 * <h2>For → toState → ForState Comprehension</h2>
 *
 * <pre>{@code
 * For.from(monad, lift(validateShippingAddress(...)))
 *     .from(addr -> lift(lookupAndValidateCustomer(...)))
 *     .from(t -> lift(buildValidatedOrder(..., t._2(), t._1())))
 *     .toState((address, customer, order) ->                   // bridge to ForState
 *         ProcessingState.initial(address, customer, order))
 *     .fromThen(s -> lift(reserveInventory(s.order())),        reservationLens)
 *     .fromThen(s -> lift(applyDiscounts(s.order(), s.customer())), discountLens)
 *     .fromThen(s -> lift(processPayment(s.order(), s.discount())), paymentLens)
 *     .fromThen(s -> lift(createShipment(s.order(), s.address())), shipmentLens)
 *     .fromThen(s -> lift(sendNotifications(s.order(), s.customer(), s.discount())),
 *         notificationLens)
 *     .yield(OrderWorkflow::toOrderResult);
 * }</pre>
 *
 * <p>After the bridge, every value is accessed by name — no more counting tuple positions.
 *
 * @see org.higherkindedj.hkt.expression.ForState
 * @see For
 */
public class OrderWorkflow {

  private final CustomerService customerService;
  private final InventoryService inventoryService;
  private final DiscountService discountService;
  private final PaymentService paymentService;
  private final ShippingService shippingService;
  private final NotificationService notificationService;
  private final WorkflowConfig config;

  // -------------------------------------------------------------------------
  // Processing State — named record that replaces tuple positions
  // -------------------------------------------------------------------------

  /**
   * Immutable state record that accumulates workflow results with named fields.
   *
   * <p>Unlike tuple-based accumulation where values are accessed by position ({@code t._3()},
   * {@code t._5()}), this record provides named accessors ({@code state.order()}, {@code
   * state.discount()}) that are self-documenting and refactoring-safe.
   *
   * <p>Fields populated during the gather phase (address, customer, order) are always non-null.
   * Fields populated during the enrich phase start as null and are set by {@code fromThen()} steps.
   * The monadic short-circuit guarantees each field is populated before subsequent steps access it.
   *
   * @param address the validated shipping address (gather phase)
   * @param customer the validated customer (gather phase)
   * @param order the validated order (gather phase)
   * @param reservation the inventory reservation (enrich phase)
   * @param discount the discount calculation result (enrich phase)
   * @param payment the payment confirmation (enrich phase)
   * @param shipment the shipment info (enrich phase)
   * @param notification the notification result (enrich phase)
   */
  public record ProcessingState(
      ValidatedShippingAddress address,
      Customer customer,
      ValidatedOrder order,
      InventoryReservation reservation,
      DiscountResult discount,
      PaymentConfirmation payment,
      ShipmentInfo shipment,
      NotificationResult notification) {

    /**
     * Creates the initial state from the three values gathered by the For comprehension. Remaining
     * fields are null until populated by ForState steps.
     */
    static ProcessingState initial(
        ValidatedShippingAddress address, Customer customer, ValidatedOrder order) {
      return new ProcessingState(address, customer, order, null, null, null, null, null);
    }
  }

  // -------------------------------------------------------------------------
  // Lenses — provide named, type-safe access for ForState operations
  // -------------------------------------------------------------------------
  // In production code, annotate ProcessingState with @GenerateLenses
  // and the annotation processor generates these automatically.

  static final Lens<ProcessingState, InventoryReservation> reservationLens =
      Lens.of(
          ProcessingState::reservation,
          (s, v) ->
              new ProcessingState(
                  s.address(),
                  s.customer(),
                  s.order(),
                  v,
                  s.discount(),
                  s.payment(),
                  s.shipment(),
                  s.notification()));

  static final Lens<ProcessingState, DiscountResult> discountLens =
      Lens.of(
          ProcessingState::discount,
          (s, v) ->
              new ProcessingState(
                  s.address(),
                  s.customer(),
                  s.order(),
                  s.reservation(),
                  v,
                  s.payment(),
                  s.shipment(),
                  s.notification()));

  static final Lens<ProcessingState, PaymentConfirmation> paymentLens =
      Lens.of(
          ProcessingState::payment,
          (s, v) ->
              new ProcessingState(
                  s.address(),
                  s.customer(),
                  s.order(),
                  s.reservation(),
                  s.discount(),
                  v,
                  s.shipment(),
                  s.notification()));

  static final Lens<ProcessingState, ShipmentInfo> shipmentLens =
      Lens.of(
          ProcessingState::shipment,
          (s, v) ->
              new ProcessingState(
                  s.address(),
                  s.customer(),
                  s.order(),
                  s.reservation(),
                  s.discount(),
                  s.payment(),
                  v,
                  s.notification()));

  static final Lens<ProcessingState, NotificationResult> notificationLens =
      Lens.of(
          ProcessingState::notification,
          (s, v) ->
              new ProcessingState(
                  s.address(),
                  s.customer(),
                  s.order(),
                  s.reservation(),
                  s.discount(),
                  s.payment(),
                  s.shipment(),
                  v));

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

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

  // -------------------------------------------------------------------------
  // Main Workflow
  // -------------------------------------------------------------------------

  /**
   * Processes an order request through the complete workflow.
   *
   * <p>The workflow uses {@code For} → {@code toState()} → {@code ForState} to compose all eight
   * steps. The first three steps (gather phase) use a {@code For} comprehension with tuple access.
   * The {@code toState()} bridge then constructs a {@link ProcessingState} with named fields, and
   * the remaining five steps (enrich phase) use {@code fromThen()} with lenses for named access.
   *
   * <p>Any step producing a {@code Left} (error) short-circuits the entire comprehension and
   * propagates the {@link OrderError}.
   *
   * @param request the order request to process
   * @return either an error or the successful order result
   */
  public EitherPath<OrderError, OrderResult> process(OrderRequest request) {
    var orderId = OrderId.generate();
    var customerId = new CustomerId(request.customerId());
    EitherMonad<OrderError> monad = EitherMonad.instance();

    // Phase 1 (Gather): use For to accumulate address, customer, and order
    // Phase 2 (Enrich): use toState() to switch to named ForState access
    Kind<EitherKind.Witness<OrderError>, OrderResult> result =
        For.from(monad, lift(validateShippingAddress(request.shippingAddress())))
            .from(addr -> lift(lookupAndValidateCustomer(customerId)))
            .from(t -> lift(buildValidatedOrder(orderId, request, t._2(), t._1())))

            // Bridge: construct named state from the three gathered values
            .toState(
                (address, customer, order) -> ProcessingState.initial(address, customer, order))

            // From here on, every value is accessed by name
            .fromThen(
                s -> lift(reserveInventory(s.order().orderId(), s.order().lines())),
                reservationLens)
            .fromThen(s -> lift(applyDiscounts(s.order(), s.customer())), discountLens)
            .fromThen(s -> lift(processPayment(s.order(), s.discount())), paymentLens)
            .fromThen(s -> lift(createShipment(s.order(), s.address())), shipmentLens)
            .fromThen(
                s -> lift(sendNotifications(s.order(), s.customer(), s.discount())),
                notificationLens)
            .yield(OrderWorkflow::toOrderResult);

    return Path.either(EITHER.narrow(result));
  }

  // -------------------------------------------------------------------------
  // Kind Lifting — bridge between Either/EitherPath and the HKT system
  // -------------------------------------------------------------------------

  private static <A> Kind<EitherKind.Witness<OrderError>, A> lift(Either<OrderError, A> either) {
    return EITHER.widen(either);
  }

  private static <A> Kind<EitherKind.Witness<OrderError>, A> lift(EitherPath<OrderError, A> path) {
    return EITHER.widen(path.run());
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
    EitherMonad<OrderError> monad = EitherMonad.instance();
    Kind<EitherKind.Witness<OrderError>, Customer> result =
        For.from(monad, lift(lookupCustomer(customerId)))
            .from(found -> lift(validateCustomerEligibility(found)))
            .yield((found, validated) -> validated);
    return Path.either(EITHER.narrow(result));
  }

  private EitherPath<OrderError, ValidatedOrder> buildValidatedOrder(
      OrderId orderId,
      OrderRequest request,
      Customer customer,
      ValidatedShippingAddress validAddress) {
    var lines =
        request.lines().stream()
            .map(line -> createValidatedLine(line.productId(), line.quantity()))
            .toList();

    var subtotal = ValidatedOrder.calculateSubtotal(lines);

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

  private Either<OrderError, InventoryReservation> reserveInventory(
      OrderId orderId, List<ValidatedOrderLine> lines) {
    return inventoryService.reserve(orderId, lines);
  }

  private Either<OrderError, DiscountResult> applyDiscounts(
      ValidatedOrder order, Customer customer) {
    return order
        .promoCode()
        .<Either<OrderError, DiscountResult>>map(
            code -> discountService.applyPromoCode(code, order.subtotal()))
        .orElseGet(
            () -> {
              if (config.featureFlags().enableLoyaltyDiscounts()) {
                return discountService.calculateLoyaltyDiscount(customer, order.subtotal());
              }
              return Either.right(DiscountResult.noDiscount(order.subtotal()));
            });
  }

  private Either<OrderError, PaymentConfirmation> processPayment(
      ValidatedOrder order, DiscountResult discount) {
    return paymentService.processPayment(
        order.orderId(), discount.finalTotal(), order.paymentMethod());
  }

  private Either<OrderError, ShipmentInfo> createShipment(
      ValidatedOrder order, ValidatedShippingAddress address) {
    return shippingService.createShipment(order.orderId(), address, order.lines());
  }

  private Either<OrderError, NotificationResult> sendNotifications(
      ValidatedOrder order, Customer customer, DiscountResult discount) {
    return notificationService
        .sendOrderConfirmation(order.orderId(), customer, discount.finalTotal())
        .fold(error -> Either.right(NotificationResult.none()), Either::right);
  }

  // -------------------------------------------------------------------------
  // Result Assembly
  // -------------------------------------------------------------------------

  private static OrderResult toOrderResult(ProcessingState s) {
    var auditLog =
        AuditLog.EMPTY
            .append(AuditLog.of("ORDER_CREATED", "Order " + s.order().orderId() + " created"))
            .append(AuditLog.of("PAYMENT_PROCESSED", "Transaction " + s.payment().transactionId()))
            .append(AuditLog.of("SHIPMENT_CREATED", "Tracking " + s.shipment().trackingNumber()));

    return new OrderResult(
        s.order().orderId(),
        s.order().customerId(),
        s.discount().finalTotal(),
        s.payment().transactionId(),
        s.shipment().trackingNumber(),
        s.shipment().estimatedDelivery(),
        auditLog);
  }
}
