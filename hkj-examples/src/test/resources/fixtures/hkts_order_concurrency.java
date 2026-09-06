// Fixture for hkj-book/src/hkts/order-concurrency.md
//
// The page runs the same order workflow asynchronously, on VResultPath, with scoped values and
// bracketed reservations. The domain is the one order-composition.md builds; the concurrency
// vocabulary it adds - the warehouses, the breakers, the context - is declared here too.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
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

record InventoryReservation(String reservationId) {}

record DiscountResult(Money finalTotal) {}

record PaymentConfirmation(String transactionId) {}

record ShipmentInfo(String carrier) {}

record NotificationResult(boolean sent) {

  static NotificationResult none() {
    return new NotificationResult(false);
  }
}

record Order(String id) {}

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
    implements OrderError {

  static SystemError timeout(String operation) {
    return new SystemError("TIMEOUT", operation, Instant.now(), Map.of());
  }

  static SystemError fromException(String message, Throwable cause) {
    return new SystemError("SYSTEM", message, Instant.now(), Map.of());
  }
}

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

  void releaseReservation(String reservationId);

  void confirmReservation(String reservationId);
}

interface CustomerService {
  Either<OrderError, Customer> findById(CustomerId customerId);

  Either<OrderError, Customer> validateEligibility(Customer customer);
}

interface ShippingService {
  Either<OrderError, ValidatedShippingAddress> validateAddress(ShippingAddress address);
}

record Data(String body) {}

record User(String id) {}

record Request(String userId) {}

record Result(String value) {}

class UserNotFoundException extends RuntimeException {}

/** The reader's own error hierarchy, from the "adapting these patterns" section. */
sealed interface MyDomainError {

  String code();

  String message();

  record ValidationError(String code, String message) implements MyDomainError {}

  record NotFoundError(String code, String message) implements MyDomainError {

    static NotFoundError user(String id) {
      return new NotFoundError("USER_NOT_FOUND", "No user " + id);
    }
  }

  record ConflictError(String code, String message) implements MyDomainError {}

  record SystemError(String code, String message) implements MyDomainError {}
}

interface LegacyService {
  User findUser(String id) throws UserNotFoundException;
}

interface OrderWorkflowApi {
  VResultPath<OrderError, OrderResult> process(OrderRequest request);
}

/** The scoped values the page threads through the workflow. */
final class OrderContext {

  static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

  static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

  static final ScopedValue<Instant> DEADLINE = ScopedValue.newInstance();

  static boolean isDeadlineExceeded() {
    return DEADLINE.isBound() && Instant.now().isAfter(DEADLINE.get());
  }

  static String shortTraceId() {
    return TRACE_ID.isBound() ? TRACE_ID.get() : "none";
  }
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

  static final CustomerService customerService = sample();

  static final ShippingService shippingService = sample();

  static final DataSource connectionPool = sample();

  static final String sql = "select 1";

  static final OrderWorkflowApi workflow = sample();

  static final String traceId = "t-1";

  static final String tenantId = "acme";

  static final Instant deadline = Instant.now().plusSeconds(30);

  static final CircuitBreaker customerLookupBreaker = CircuitBreaker.withDefaults();

  static final RetryPolicy customerLookupRetry = RetryPolicy.exponentialBackoff(3, Duration.ofMillis(50));

  static final VResultPath<OrderError, InventoryReservation> warehouse1 = sample();

  static final VResultPath<OrderError, InventoryReservation> warehouse2 = sample();

  static final VResultPath<OrderError, InventoryReservation> warehouse3 = sample();

  static final VTask<Either<OrderError, Data>> vtaskOfEither = sample();

  static final Either<OrderError, Data> either = Either.left(sample());

  static final PaymentService paymentService = sample();

  static final NotificationService notificationService = sample();

  static final OrderRequest request = sample();

  static final OrderId orderId = OrderId.generate();

  static final CustomerId customerId = new CustomerId("c-1");

  static final CustomerId id = customerId;

  static final ShippingAddress address = new ShippingAddress("221B", "London");

  static final MonadError<EitherKind.Witness<OrderError>, OrderError> monad =
      Instances.monadError(either());

  static final LegacyService legacyService = sample();

  static void logSync(String message) {}

  Either<MyDomainError, Request> validateRequest(Request request) {
    return Either.right(request);
  }

  Either<MyDomainError, User> findUser(String id) {
    return Either.left(MyDomainError.NotFoundError.user(id));
  }

  Either<MyDomainError, String> performAction(User user, Request request) {
    return Either.right("done");
  }

  Result buildResult(String action) {
    return new Result(action);
  }

  static Duration getRemainingTimeout() {
    return Duration.ofSeconds(10);
  }

  static List<Order> executeQuery(PreparedStatement statement) {
    return List.of();
  }

  // Package-private instance methods: a snippet's declaration may only override one of the same
  // kind and access.
  VResultPath<OrderError, InventoryReservation> warehouseReservation(
      int warehouse, Duration latency, OrderId orderId, List<ValidatedOrderLine> lines) {
    return sample();
  }

  VResultPath<OrderError, OrderResult> processAfterReservation(
      ValidatedOrder order, Customer customer, InventoryReservation reservation) {
    return sample();
  }

  VResultPath<OrderError, OrderResult> processWithReservation(
      ValidatedOrder order, Customer customer) {
    return sample();
  }

  VResultPath<OrderError, Unit> checkDeadline(String operation) {
    return sample();
  }

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
  VResultPath<OrderError, ValidatedShippingAddress> validateShippingAddress(
      ShippingAddress address) {
    return sample();
  }

  static EitherPath<OrderError, Customer> lookupCustomer(CustomerId customerId) {
    return Path.left(sample());
  }

  static EitherPath<OrderError, Customer> validateCustomerEligibility(Customer customer) {
    return Path.left(sample());
  }

  VResultPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId customerId) {
    return sample();
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
