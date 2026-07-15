// Fixture for .claude/skills/hkj-test/SKILL.md
//
// The skill's assertion examples elide the domain they assert on, as an assertion example should.
// Supplying it here means the assertion CHAINS and the law entry points are compiled against the real
// hkj-test API, so a method that is renamed or removed fails the build instead of quietly misleading
// a reader.
//
// The generated companions (OrderLenses, OrderErrorPrisms, OrderTraversals) are declared TOP-LEVEL,
// not nested in Fixture: a snippet that declares its own class does not extend Fixture, so anything
// it references must be a top-level type.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.EitherFAssert.assertThatEitherF;
import static org.higherkindedj.hkt.assertions.EitherOrBothAssert.assertThatEitherOrBoth;
import static org.higherkindedj.hkt.assertions.ErrorEnvelopeAssert.assertThatErrorEnvelope;
import static org.higherkindedj.hkt.assertions.FieldErrorAssert.assertThatFieldError;
import static org.higherkindedj.hkt.assertions.FreeAssert.assertThatFree;
import static org.higherkindedj.hkt.assertions.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.assertions.NonEmptyListAssert.assertThatNonEmptyList;
import static org.higherkindedj.hkt.assertions.VResultPathAssert.assertThatVResultPath;
import static org.higherkindedj.hkt.assertions.VStreamAssert.assertThatVStream;
import static org.higherkindedj.hkt.assertions.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.time.TimeSource;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.laws.LensLaws;
import org.higherkindedj.optics.laws.PrismLaws;
import org.higherkindedj.optics.laws.TraversalLaws;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

record Customer(String email, int age) {}

/** The reader's own record. */
record Order(String id, String email, List<Integer> lines) {}

/** The reader's own sealed error hierarchy. */
sealed interface OrderError {
  record OutOfStock(String sku) implements OrderError {}

  record NotFound(String id) implements OrderError {}
}

/** What @GenerateLenses would emit. */
final class OrderLenses {
  static Lens<Order, String> email() {
    return Lens.of(Order::email, (o, v) -> new Order(o.id(), v, o.lines()));
  }
}

/** What @GeneratePrisms would emit. */
final class OrderErrorPrisms {
  static Prism<OrderError, OrderError.OutOfStock> outOfStock() {
    return Prism.of(
        e -> e instanceof OrderError.OutOfStock s ? Optional.of(s) : Optional.empty(), s -> s);
  }
}

/** What @GenerateTraversals would emit. */
final class OrderTraversals {
  static Traversal<Order, Integer> quantities() {
    Lens<Order, List<Integer>> lines =
        Lens.of(Order::lines, (o, v) -> new Order(o.id(), o.email(), v));
    return lines.andThen(Traversals.forList());
  }
}

/**
 * The reader's typed failure channel, as a VResultPath's Left. The variants are singletons so the
 * page can write {@code OutOfStock.INSTANCE}, which is how a reader writes a payload-free error.
 */
sealed interface CheckoutError permits OutOfStock, ReservationExpired {
  String code();
}

enum OutOfStock implements CheckoutError {
  INSTANCE;

  @Override
  public String code() {
    return "OUT_OF_STOCK";
  }
}

enum ReservationExpired implements CheckoutError {
  INSTANCE;

  @Override
  public String code() {
    return "RESERVATION_EXPIRED";
  }
}

record OrderId(String value) {}

record TraceId(String value) {}

/** The typed context of an ErrorEnvelope: records as schema, not a Map<String, Object> bag. */
record StockContext(OrderId orderId, TraceId traceId) {}

/** A hierarchy carrying an ErrorEnvelope, exactly as @GenerateErrorEnvelope requires it to. */
sealed interface StockError {
  ErrorEnvelope<StockContext> envelope();

  record Unavailable(String sku, ErrorEnvelope<StockContext> envelope) implements StockError {}
}

/** The reader's own service: it reads the clock through a TimeSource, never Instant.now(). */
final class InMemoryInventoryService {

  private final TimeSource time;
  private Instant reservedAt = Instant.EPOCH;

  InMemoryInventoryService(TimeSource time) {
    this.time = time;
  }

  void reserve(String sku) {
    reservedAt = time.now().unsafeRunSync();
  }

  Either<CheckoutError, String> checkout(String sku) {
    return time.now().unsafeRunSync().isAfter(reservedAt.plus(Duration.ofMinutes(15)))
        ? Either.left(ReservationExpired.INSTANCE)
        : Either.right(sku);
  }
}

class Fixture {

  // Discriminated unions.
  static final Either<String, Integer> result = Either.right(42);
  static final Either<String, Integer> failure = Either.left("boom");
  static final String expectedError = "boom";

  // Validation accumulation.
  static final FieldError first = FieldError.of("not an email").at("email").at("order");
  static final FieldError second = FieldError.of("must be positive").at("total").at("order");
  static final FieldError other = FieldError.of("unknown field");
  static final NonEmptyList<FieldError> errors = NonEmptyList.of(first, second);

  static final Validated<NonEmptyList<FieldError>, Customer> invalidCustomer =
      Validated.invalidNel(FieldError.of("not an email").at("email").at("customer"));

  static final Object dto = new Object();
  static final Mapping mapping = new Mapping();

  // EitherOrBoth: Both is the state Either cannot express (a value plus non-fatal warnings).
  static final Order order = new Order("o1", "ada@example.com", List.of(1, 2, 3));
  static final List<String> warnings = List.of("address unverified");
  static final OrderId orderId = new OrderId("o1");
  static final Instant FROZEN_INSTANT = Instant.parse("2026-07-07T00:00:00Z");

  static final StockError error =
      new StockError.Unavailable(
          "sku-1",
          new ErrorEnvelope<>(
              "OUT_OF_STOCK",
              "sku-1 is out of stock",
              FROZEN_INSTANT,
              new StockContext(orderId, new TraceId("trace-1"))));

  static final EitherOrBoth<String, Order> clean = EitherOrBoth.right(order);
  static final EitherOrBoth<StockError, Order> fatal = EitherOrBoth.left(error);
  static final EitherOrBoth<List<String>, Order> warned = EitherOrBoth.both(warnings, order);

  // Effects: lazy, so the assertions have something to drive.
  static final IO<Integer> effect = IO.delay(() -> 2);

  static final IO<Integer> failing =
      IO.delay(
          () -> {
            throw new IllegalStateException("kaboom");
          });

  static final String expected = "done";
  static final VTask<String> task = VTask.succeed(expected);
  static final VTask<String> failingTask = VTask.fail(new IllegalStateException("kaboom"));
  static final VStream<Integer> stream = VStream.of(1, 2, 3);
  static final VStream<Integer> failingStream = VStream.fail(new IllegalStateException("kaboom"));

  // VResultPath: a typed Left, and a defect on the task channel, are different outcomes.
  static final VResultPath<CheckoutError, Order> path = VResultPath.pure(order);

  static final VResultPath<CheckoutError, Order> defective =
      VResultPath.fromVTask(VTask.fail(new IllegalStateException("kaboom")));

  // Free / EitherF.
  static final Kind<MaybeKind.Witness, String> maybeKind = MAYBE.widen(Maybe.just("hello"));

  // Deterministic time.
  static final String sku = "sku-1";

  static class Mapping {
    Validated<NonEmptyList<FieldError>, Customer> parse(Object wire) {
      return invalidCustomer;
    }
  }
}
