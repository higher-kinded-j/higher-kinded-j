// Fixture for hkj-book/src/examples/examples_order.md
//
// The page summarises the order-processing example, quoting its error hierarchy, its comprehension
// and its resilient pre-flight. Those types live in this module's main sources, which are on the
// gate's classpath; the fixture supplies the workflow's own private helpers, which the page calls
// bare, with the signatures the real workflow declares - so a change to one of them shows up here.
//
// The error package is imported on demand rather than by name: the first snippet declares its own
// `OrderError` to show the hierarchy's shape, and a single-type import of a name a snippet declares
// is a duplicate declaration. An on-demand import is shadowed by the declaration instead.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.order.error.*;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.DiscountResult;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.NotificationResult;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.OrderResult;
import org.higherkindedj.example.order.model.PaymentConfirmation;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.model.ValidatedOrder;
import org.higherkindedj.example.order.model.ValidatedOrderFocus;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.ValidatedOrderLineFocus;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.example.order.workflow.ProcessingState;
import org.higherkindedj.example.order.workflow.ProcessingStateLenses;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.jspecify.annotations.Nullable;

class Fixture {

  static final RetryPolicy retryPolicy = RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100));

  static final Duration preflightTimeout = Duration.ofSeconds(5);

  // The gate compiles snippets; it never runs them. Where building a value would mean assembling
  // half the domain, `sample()` stands in for it and keeps the fixture about the signatures.
  static final OrderRequest request = sample();

  static final ValidatedOrder order = sample();

  static final PaymentMethod newMethod =
      new PaymentMethod.CreditCard("**** 4242", "12", "2030", "***");

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static <A> Kind<EitherKind.Witness<OrderError>, A> lift(Either<OrderError, A> either) {
    return EITHER.widen(either);
  }

  static <A> Kind<EitherKind.Witness<OrderError>, A> lift(EitherPath<OrderError, A> path) {
    return EITHER.widen(path.run());
  }

  static <A> EitherPath<OrderError, A> toEitherPath(IOPath<A> operation, String operationName) {
    return operation
        .runSafe()
        .foldFailureFirst(cause -> Path.<OrderError, A>left(systemError()), Path::right);
  }

  // Not a factory call: the snippet that shows the hierarchy declares its own `OrderError`, and
  // the fixture is spliced into that unit too.
  static OrderError systemError() {
    return sample();
  }

  static Unit runPreflight(OrderRequest request) {
    return Unit.INSTANCE;
  }

  static EitherPath<OrderError, ValidatedShippingAddress> validateShippingAddress(
      ShippingAddress address) {
    return Path.left(systemError());
  }

  static EitherPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId customerId) {
    return Path.left(systemError());
  }

  static EitherPath<OrderError, ValidatedOrder> buildValidatedOrder(
      OrderId orderId,
      OrderRequest request,
      Customer customer,
      ValidatedShippingAddress validAddress) {
    return Path.left(systemError());
  }

  static Either<OrderError, InventoryReservation> reserveInventory(
      OrderId orderId, List<ValidatedOrderLine> lines) {
    return Either.left(systemError());
  }

  static Either<OrderError, DiscountResult> applyDiscounts(
      ValidatedOrder order, Customer customer) {
    return Either.left(systemError());
  }

  static Either<OrderError, PaymentConfirmation> processPayment(
      ValidatedOrder order, DiscountResult discount) {
    return Either.left(systemError());
  }

  static Either<OrderError, ShipmentInfo> createShipment(
      ValidatedOrder order, ValidatedShippingAddress address) {
    return Either.left(systemError());
  }

  static Either<OrderError, NotificationResult> sendNotifications(
      ValidatedOrder order, Customer customer, DiscountResult discount) {
    return Either.left(systemError());
  }

}

/** The comprehension yields through the workflow's own static, so it is named the same way here. */
final class OrderWorkflow {

  static OrderResult toOrderResult(ProcessingState state) {
    throw new UnsupportedOperationException();
  }
}
