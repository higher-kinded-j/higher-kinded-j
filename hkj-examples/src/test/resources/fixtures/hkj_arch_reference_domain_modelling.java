// Fixture for .claude/skills/hkj-arch/reference/domain-modelling.md
//
// The page's snippets are a catalogue of modelling shapes: each one shows the type it is teaching and
// elides the rest of the domain. Supplying the rest here compiles the shapes against the real library
// and the real annotation processor, so `OrderStatusPrisms.confirmed()` is the genuinely generated
// prism rather than a stand-in. It already caught `Prism.preview(...)` (the method is `getOptional`),
// a `.price()` navigator call on a plain FocusPath, and a WriterPath block written against a
// `Semigroup.listSemigroup()` that does not exist.
//
// The types the page re-declares (Order, OrderStatus, OrderId, Money, OrderEvent) are supplied here in
// the same shape, so the snippets that do NOT declare them still compile. Nothing in `Fixture`
// constructs one: a snippet that declares its own version shadows the fixture's, and the fixture body
// is spliced in alongside it, so it must not depend on a particular constructor.
//
// `Draft`/`Confirmed`/`Shipped` are imported from OrderStatus because the transition snippet names them
// unqualified. Importing a nested type of the same compilation unit is legal, and it keeps the page's
// text intact.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import bookverify.OrderStatus.Confirmed;
import bookverify.OrderStatus.Draft;
import bookverify.OrderStatus.Shipped;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.WriterPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.focus.TraversalPath;

/** The reader's own value objects. */
record OrderId(String value) {}

record CustomerId(String value) {}

record Customer(CustomerId id, String name) {}

record PaymentRef(String value) {}

record TrackingNumber(String value) {}

record ShippingAddress(String line1, String postcode) {}

record OrderRequest(String customerId, List<String> skus) {}

@GenerateLenses
record Money(BigDecimal amount, Currency currency) {

  static final Money ZERO = Money.of(0);

  static Money of(double amount) {
    return new Money(BigDecimal.valueOf(amount), Currency.getInstance("USD"));
  }

  Money add(Money other) {
    return new Money(amount.add(other.amount), currency);
  }

  Money multiply(double factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
  }
}

@GenerateLenses
@GenerateFocus
record LineItem(String sku, Money price, int quantity) {
  Money total() {
    return price.multiply(quantity);
  }
}

/** The state machine the page shows, annotated as the page annotates it. */
@GeneratePrisms
sealed interface OrderStatus {
  record Draft(Instant created) implements OrderStatus {}

  record Confirmed(Instant confirmedAt, PaymentRef payment) implements OrderStatus {}

  record Shipped(TrackingNumber tracking, Instant shippedAt) implements OrderStatus {}

  record Delivered(Instant deliveredAt, String signedBy) implements OrderStatus {}

  record Cancelled(String reason, Instant cancelledAt) implements OrderStatus {}

  record Returned(String reason, Instant returnedAt) implements OrderStatus {}
}

@GenerateLenses
@GenerateFocus
record Order(
    OrderId id,
    Customer customer,
    List<LineItem> items,
    OrderStatus status,
    Optional<ShippingAddress> shipping,
    Optional<Money> discount) {

  Money subtotal() {
    return items.stream().map(LineItem::total).reduce(Money.ZERO, Money::add);
  }
}

/**
 * The application error hierarchy the page builds up. It carries the two transition failures the
 * OrderTransitions snippet reports as well as the two module errors the hierarchy snippet shows.
 */
sealed interface AppError permits OrderError, UserError, PaymentError {}

sealed interface OrderError extends AppError {
  record InvalidItems(List<String> reasons) implements OrderError {}

  record InsufficientStock(String itemId) implements OrderError {}

  record AlreadyConfirmed() implements OrderError {}

  record InvalidTransition(OrderStatus from, String attempted) implements OrderError {}
}

sealed interface UserError extends AppError {
  record NotFound(String userId) implements UserError {}

  record Suspended(String userId, String reason) implements UserError {}
}

sealed interface PaymentError extends AppError {
  record Declined(String reason) implements PaymentError {}

  record GatewayTimeout(String gateway) implements PaymentError {}
}

/** The events the domain-events snippet re-declares. */
sealed interface OrderEvent {
  record Created(OrderId id, Customer customer, Instant at) implements OrderEvent {}

  record Confirmed(OrderId id, PaymentRef payment, Instant at) implements OrderEvent {}

  record Shipped(OrderId id, TrackingNumber tracking, Instant at) implements OrderEvent {}

  record Cancelled(OrderId id, String reason, Instant at) implements OrderEvent {}
}

interface OrderService {
  Either<OrderError, Order> processOrder(OrderRequest request);
}

class Fixture {

  static OrderService orderService;
  static OrderRequest request;

  // Re-declared by several snippets, so never constructed here.
  static Order order;

  /** The warnings the "rich result" snippet folds into its ProcessingResult. */
  static List<String> warnings = List.of();

  EitherPath<AppError, Order> validateOrder(OrderRequest req) {
    return Path.right(order);
  }

  EitherPath<AppError, Order> executeOrder(Order toExecute) {
    return Path.right(toExecute);
  }

  WriterPath<List<OrderEvent>, Order> confirmOrder(Order toConfirm) {
    return Path.writerPure(toConfirm, Monoids.list());
  }
}
