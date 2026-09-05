// Fixture for hkj-book/src/home.md
//
// The front page shows one "quick example" per chapter, so this fixture has to serve six of them:
// the payment railway, the focus/effect bridge, the optics record graph, the mapping codec, the
// effect algebra and the hkj-test assertions.
//
// The `User`/`Address`/`Street` records are declared here EXACTLY as the optics quick example
// declares them. That snippet shadows the fixture's copies with its own, so the two must agree or
// the fixture's helpers stop compiling underneath it.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.optics.validated.StandardCodecs.bigDecimal;
import static org.higherkindedj.optics.validated.StandardCodecs.enumByName;
import static org.higherkindedj.optics.validated.StandardCodecs.localDate;
import static org.higherkindedj.optics.validated.StandardCodecs.uuid;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

// Plain `@GenerateFocus`, deliberately: the focus/effect bridge example passes
// `UserFocus.address()` to `EitherPath.focus`, which takes a `FocusPath`. Navigators would make
// that accessor return `UserFocus.AddressNavigator` instead, and the bridge would not compile.
// The optics quick example declares its own copies of these records WITH navigators, which is
// what its fluent `.address().street().name()` chain needs.
@GenerateLenses
@GenerateFocus
record Street(String name, int number) {}

@GenerateLenses
@GenerateFocus
record Address(Street street, String postcode) {}

@GenerateLenses
@GenerateFocus
record User(String name, Address address) {}

/**
 * The error channel the payment railway runs on. The variants are top-level because the page
 * writes `new UserNotFound(userId)` unqualified, and shadowing `java.lang.Error` is deliberate:
 * `Error` is the name the front page uses.
 */
sealed interface Error permits UserNotFound, PaymentFailed, BadPostcode {}

record UserNotFound(String userId) implements Error {}

record PaymentFailed(Throwable cause) implements Error {}

record BadPostcode(String postcode) implements Error {}

record OrderResult(Charge charge) {

  static OrderResult success(Charge charge) {
    return new OrderResult(charge);
  }
}

record Charge(String id) {}

record PaymentRequest(String reference, BigDecimal amount) {}

final class PaymentException extends RuntimeException {

  PaymentException(String message) {
    super(message);
  }
}

/** The resilience example's typed errors. */
sealed interface OrderError {

  record OutOfStock(String sku) implements OrderError {}

  record SystemError(String what) implements OrderError {

    static SystemError timeout(String what, Duration after) {
      return new SystemError(what + " timed out after " + after);
    }
  }
}

record Reservation(String id) {}

class Fixture {

  static final String userId = "u-1";

  static final BigDecimal amount = new BigDecimal("42.00");

  static final PaymentRequest request = new PaymentRequest("r-1", amount);

  static final User user = new User("Ada", new Address(new Street("Old Street", 1), "EC1V 9NR"));

  static final Order order =
      new Order(UUID.randomUUID(), LocalDate.EPOCH, OrderStatus.NEW, new BigDecimal("1.00"));

  static final Validator validator = new Validator();

  static final PaymentService paymentService = new PaymentService();

  static final UserService userService = new UserService();

  static final Either<Error, Integer> result = Either.right(42);

  static final Maybe<String> value = Maybe.just("hello");

  static final Try<String> computation = Try.failure(new IOException("boom"));

  static Maybe<User> findUser(String userId) {
    return Maybe.just(user);
  }

  static EitherPath<Error, String> validatePostcode(String postcode) {
    return Path.either(Either.right(postcode));
  }

  static VResultPath<OrderError, Reservation> reserveInventory(Order order) {
    return Path.vresultRight(new Reservation("r-1"));
  }

  static final class Validator {

    Either<Error, PaymentRequest> validate(PaymentRequest request, User user) {
      return Either.right(request);
    }

    Validation validate(PaymentRequest request) {
      return new Validation();
    }
  }

  /** The pre-Path shape the front page's "traditional Java" half contrasts with. */
  static final class Validation {

    boolean isValid() {
      return true;
    }
  }

  static final class PaymentService {

    Charge charge(PaymentRequest request) {
      return new Charge("c-1");
    }
  }

  static final class UserService {

    EitherPath<Error, User> findById(String id) {
      return Path.either(Either.right(user));
    }
  }
}

// ---- the mapping quick example's domain and wire -------------------------------------------

enum OrderStatus {
  NEW,
  PAID,
  CANCELLED
}

record Order(UUID id, LocalDate placedOn, OrderStatus status, BigDecimal total) {}

record OrderDto(String id, String placedOn, String status, String total) {}

// ---- the effect algebra quick example's payloads --------------------------------------------

record Money(BigDecimal amount) {}

record PaymentMethod(String token) {}

record AuthorisationToken(String value) {}

record ChargeResult(String reference) {}

record RefundResult(String reference) {}

record TransactionId(String value) {}
