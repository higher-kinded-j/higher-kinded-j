// Fixture for hkj-book/src/hkts/order-composition.md
//
// The page builds its own order workflow one piece at a time - the error hierarchy, the state
// record, the lift helper, the steps - so each snippet elides the pieces around it. They are all
// declared here, with the shapes the page gives them; a snippet that shows one declares it for
// itself and shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

record OrderId(String value) {

  static OrderId generate() {
    return new OrderId("ORD-1");
  }
}

record CustomerId(String value) {}

record ProductId(String value) {}

record Money(java.math.BigDecimal amount) {}

record ShippingAddress(String street, String city) {}

record ValidatedShippingAddress(String street, String city) {}

record Customer(String id, String name) {}

record ValidatedOrderLine(ProductId productId, int quantity) {}

record ValidatedOrder(
    OrderId orderId, List<ValidatedOrderLine> lines, Money subtotal, String paymentMethod) {}

record OrderLineRequest(String productId, int quantity) {}

record OrderRequest(
    String customerId, List<OrderLineRequest> lines, ShippingAddress shippingAddress) {}

record InventoryReservation(String id) {}

record DiscountResult(Money finalTotal) {}

record PaymentConfirmation(String transactionId) {}

record ShipmentInfo(String carrier) {}

record NotificationResult(boolean sent) {

  static NotificationResult none() {
    return new NotificationResult(false);
  }
}

record OrderResult(OrderId orderId, String status) {

  static OrderResult error(String message) {
    return new OrderResult(new OrderId("none"), message);
  }
}

/** The hierarchy the page builds, before the envelope generator tidies it. */
@GeneratePrisms
sealed interface OrderError
    permits ValidationError,
        CustomerError,
        InventoryError,
        DiscountError,
        PaymentError,
        ShippingError,
        NotificationError,
        SystemError {

  String code();

  String message();

  Instant timestamp();

  Map<String, Object> context();
}

record ValidationError(String code, String message, Instant timestamp, Map<String, Object> context)
    implements OrderError {}

record CustomerError(
    String code, String message, Instant timestamp, Map<String, Object> context, String customerId)
    implements OrderError {}

record InventoryError(String code, String message, Instant timestamp, Map<String, Object> context)
    implements OrderError {}

record DiscountError(String code, String message, Instant timestamp, Map<String, Object> context)
    implements OrderError {}

record PaymentError(String code, String message, Instant timestamp, Map<String, Object> context)
    implements OrderError {}

record ShippingError(String code, String message, Instant timestamp, Map<String, Object> context)
    implements OrderError {}

record NotificationError(
    String code, String message, Instant timestamp, Map<String, Object> context)
    implements OrderError {}

record SystemError(String code, String message, Instant timestamp, Map<String, Object> context)
    implements OrderError {}

@GenerateLenses
record ProcessingState(
    ValidatedShippingAddress address,
    Customer customer,
    ValidatedOrder order,
    InventoryReservation reservation,
    DiscountResult discount,
    PaymentConfirmation payment,
    ShipmentInfo shipment,
    NotificationResult notification) {

  static ProcessingState initial(
      ValidatedShippingAddress address, Customer customer, ValidatedOrder order) {
    return new ProcessingState(address, customer, order, null, null, null, null, null);
  }
}

interface InventoryService {
  Either<OrderError, InventoryReservation> reserve(
      OrderId orderId, List<ValidatedOrderLine> lines);
}

interface PaymentService {
  Either<OrderError, PaymentConfirmation> processPayment(
      OrderId orderId, Money amount, String method);
}

interface NotificationService {
  Either<OrderError, NotificationResult> sendOrderConfirmation(
      OrderId orderId, Customer customer, Money total);
}

/** The comprehension yields through the workflow's own static, so it is named the same way here. */
final class OrderWorkflow {

  static OrderResult toOrderResult(ProcessingState state) {
    return new OrderResult(state.order().orderId(), "CONFIRMED");
  }
}

class Fixture {

  static final InventoryService inventoryService = sample();

  static final PaymentService paymentService = sample();

  static final NotificationService notificationService = sample();

  static final OrderRequest request = sample();

  static final OrderId orderId = OrderId.generate();

  static final CustomerId customerId = new CustomerId("c-1");

  static final CustomerId id = customerId;

  static final ShippingAddress address = new ShippingAddress("221B", "London");

  static final MonadError<EitherKind.Witness<OrderError>, OrderError> monad =
      Instances.monadError(either());

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static <A> Kind<EitherKind.Witness<OrderError>, A> lift(Either<OrderError, A> either) {
    return EITHER.widen(either);
  }

  static <A> Kind<EitherKind.Witness<OrderError>, A> lift(EitherPath<OrderError, A> path) {
    return EITHER.widen(path.run());
  }

  // Package-private and, where the page shows them as members of its workflow class, instance
  // methods: a snippet's declaration may only override one of the same kind and access.
  EitherPath<OrderError, ValidatedShippingAddress> validateShippingAddress(
      ShippingAddress address) {
    return Path.left(sample());
  }

  static EitherPath<OrderError, Customer> lookupCustomer(CustomerId customerId) {
    return Path.left(sample());
  }

  static EitherPath<OrderError, Customer> validateCustomerEligibility(Customer customer) {
    return Path.left(sample());
  }

  EitherPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId customerId) {
    return Path.left(sample());
  }

  static EitherPath<OrderError, ValidatedOrder> buildValidatedOrder(
      OrderId orderId,
      OrderRequest request,
      Customer customer,
      ValidatedShippingAddress validAddress) {
    return Path.left(sample());
  }

  Either<OrderError, InventoryReservation> reserveInventory(
      OrderId orderId, List<ValidatedOrderLine> lines) {
    return Either.left(sample());
  }

  Either<OrderError, DiscountResult> applyDiscounts(
      ValidatedOrder order, Customer customer) {
    return Either.left(sample());
  }

  Either<OrderError, PaymentConfirmation> processPayment(
      ValidatedOrder order, DiscountResult discount) {
    return Either.left(sample());
  }

  Either<OrderError, ShipmentInfo> createShipment(
      ValidatedOrder order, ValidatedShippingAddress address) {
    return Either.left(sample());
  }

  Either<OrderError, NotificationResult> sendNotifications(
      ValidatedOrder order, Customer customer, DiscountResult discount) {
    return Either.left(sample());
  }
}
