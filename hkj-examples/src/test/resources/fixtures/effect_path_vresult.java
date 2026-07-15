// Fixture for hkj-book/src/effect/path_vresult.md
//
// The page shows two kinds of code: a concrete order pipeline, and a catalogue of shapes written
// against abstract type variables (`VResultPath<E, A> p1 = Path.vresultRight(value);`). The generic
// `Fixture<E, A, B>` below lends those variables to the snippet, so the catalogue is compiled rather
// than merely asserted.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources (see build.gradle.kts).

import java.time.Duration;
import java.util.List;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.VTask;

record Address(String line1) {}

record Customer(String id) {}

record OrderRequest(String customerId, String shippingAddress) {}

record Order(String id) {}

record OrderResult(String id) {}

record Reservation(String id) {}

sealed interface OrderError {
  record SystemError(String why) implements OrderError {}

  record Unavailable(String what) implements OrderError {}

  record Busy(String what) implements OrderError {}

  record Timeout() implements OrderError {}

  static OrderError timeout() {
    return new Timeout();
  }

  static OrderError unavailable(String what) {
    return new Unavailable(what);
  }

  static OrderError busy(String what) {
    return new Busy(what);
  }
}

final class SystemError {
  static OrderError fromDefect(Throwable t) {
    return new OrderError.SystemError(t.getMessage());
  }
}

class DomainException extends RuntimeException {
  DomainException(Object error) {
    super(String.valueOf(error));
  }
}

/** Generic so the page can show `VResultPath<E, A>` as a shape rather than a domain. */
class Fixture<E, A, B> {

  A value;
  E error;
  Either<E, A> either;
  VTask<Either<E, A>> vtaskOfEither;
  VResultPath<E, A> path;

  static final RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(10));
  static final CircuitBreaker breaker = CircuitBreaker.withDefaults();
  static final Bulkhead bulkhead = Bulkhead.withMaxConcurrent(4);

  static final VResultPath<OrderError, Reservation> warehouse1 = null;
  static final VResultPath<OrderError, Reservation> warehouse2 = null;
  static final VResultPath<OrderError, Reservation> warehouse3 = null;
  static final Order order = new Order("o-1");

  Either<E, A> decide() {
    return either;
  }

  B onError(E e) {
    return null;
  }

  B onValue(A a) {
    return null;
  }

  VResultPath<OrderError, Address> validateShippingAddress(String address) {
    return Path.vresultRight(new Address(address));
  }

  VResultPath<OrderError, Customer> lookupAndValidateCustomer(String id) {
    return Path.vresultRight(new Customer(id));
  }

  VResultPath<OrderError, Order> buildValidatedOrder(OrderRequest request, Customer customer) {
    return Path.vresultRight(new Order("o-1"));
  }

  VResultPath<OrderError, OrderResult> reserveThenFulfil(Order order) {
    return Path.vresultRight(new OrderResult(order.id()));
  }

  VResultPath<OrderError, OrderResult> retryOnce(OrderError error) {
    return Path.vresultLeft(error);
  }

  OrderError enrich(OrderError error) {
    return error;
  }

  static VResultPath<OrderError, Reservation> reserveInventory(Order order) {
    return Path.vresultRight(new Reservation("r-1"));
  }

  static VResultPath<OrderError, OrderResult> payThenShip(Order order, Reservation reservation) {
    return Path.vresultRight(new OrderResult(order.id()));
  }

  static VTask<?> confirm(Reservation reservation) {
    return VTask.succeed(reservation);
  }

  static VTask<?> releaseAndRefund(Reservation reservation) {
    return VTask.succeed(reservation);
  }
}
