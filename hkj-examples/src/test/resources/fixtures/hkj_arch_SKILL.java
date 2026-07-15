// Fixture for .claude/skills/hkj-arch/SKILL.md
//
// The skill teaches architecture, so its snippets show the *shape* of a core and a shell and elide
// the domain they operate on. Supplying that domain here means the HKJ API the snippets lean on
// (TimeSource/SteppableClock, the Focus DSL, Free's foldMap) is compiled against the real library:
// the skill is read by an assistant that generates code from it, so a wrong call here becomes code
// that does not build in someone's project. It already caught `IdMonad.INSTANCE` (private; the
// accessor is `instance()`) and a `.price()` navigator call on a plain FocusPath.
//
// The records are TOP-LEVEL so the processor generates the names the skill teaches (OrderFocus,
// LineItemFocus) rather than joining an enclosing class's name.
//
// Fields of a type a snippet re-declares for itself (Order, OrderStatus) are left uninitialised on
// purpose: the Fixture body is spliced into every snippet, including the ones that shadow those
// types, so it must not depend on a particular constructor shape.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.time.TimeSource;
import org.higherkindedj.hkt.assertions.SteppableClock;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** The reader's own value objects. */
record OrderId(String value) {
  static OrderId generate() {
    return new OrderId(UUID.randomUUID().toString());
  }
}

record Customer(String name, boolean goldMember) {}

record ShippingAddress(String line1, String postcode) {}

record TrackingNumber(String value) {}

record Money(BigDecimal amount) {
  static Money of(double amount) {
    return new Money(BigDecimal.valueOf(amount));
  }

  Money multiply(double factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)));
  }
}

@GenerateLenses
@GenerateFocus
record LineItem(String sku, Money price, int quantity) {}

/** The order status the "Domain State as Records" snippet re-declares for itself. */
sealed interface OrderStatus {
  record Draft() implements OrderStatus {}

  record Confirmed(Instant confirmedAt) implements OrderStatus {}
}

@GenerateLenses
@GenerateFocus
record Order(
    OrderId id,
    Customer customer,
    List<LineItem> items,
    OrderStatus status,
    Optional<ShippingAddress> shippingAddress) {}

record OrderRequest(List<LineItem> items) {}

/** The two intermediate shapes the boundary example's pipeline passes through. */
record ValidatedReq(OrderRequest request) {}

record InventoryCheck(ValidatedReq request) {}

/** The order errors the page re-declares as its sealed-hierarchy example. */
sealed interface OrderError {
  record NotFound(String orderId) implements OrderError {}

  record InvalidItems(List<String> reasons) implements OrderError {}

  record PaymentFailed(String gateway, String reason) implements OrderError {}

  record InsufficientStock(String itemId, int requested, int available) implements OrderError {}
}

/** The service the controller delegates to. */
interface OrderService {
  EitherPath<OrderError, Order> processOrder(OrderRequest req);
}

/** The reservation the TimeSource example builds. */
record Reservation(OrderId id, List<LineItem> lines, Instant expiresAt) {}

/** The seam the TimeSource example implements. */
interface InventoryService {
  IO<Reservation> reserve(OrderId id, List<LineItem> lines);
}

/**
 * The class the injection snippet declares. The test snippet ("Advance Time Instead of Sleeping")
 * constructs it, so it has to exist for that snippet even though the previous snippet shows it.
 */
class InMemoryInventoryService implements InventoryService {

  private static final Duration RESERVATION_HOLD = Duration.ofMinutes(15);

  private final TimeSource timeSource;

  InMemoryInventoryService(TimeSource timeSource) {
    this.timeSource = Objects.requireNonNull(timeSource, "timeSource must not be null");
  }

  @Override
  public IO<Reservation> reserve(OrderId id, List<LineItem> lines) {
    return timeSource.now().map(now -> new Reservation(id, lines, now.plus(RESERVATION_HOLD)));
  }
}

class Fixture {

  // Re-declared by the "Domain State as Records" snippet, so never constructed here.
  static Order order;

  static OrderService orderService;

  /** The three pure steps the boundary example's service composes. */
  EitherPath<OrderError, ValidatedReq> validateRequest(OrderRequest req) {
    return Path.right(new ValidatedReq(req));
  }

  EitherPath<OrderError, InventoryCheck> checkInventory(ValidatedReq req) {
    return Path.right(new InventoryCheck(req));
  }

  EitherPath<OrderError, Order> calculateTotal(InventoryCheck checked) {
    return Path.right(order);
  }

  /** The reader's Free program and its two interpreters, over their own effect algebra. */
  static Free<IdKind.Witness, String> program;

  static Natural<IdKind.Witness, IOKind.Witness> productionInterpreter;

  static Natural<IdKind.Witness, IdKind.Witness> testInterpreter;

  static String expected;

  LineItem line(String sku, int quantity) {
    return new LineItem(sku, Money.of(9.99), quantity);
  }
}
