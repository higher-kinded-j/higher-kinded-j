// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.functor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook.totals;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Instances.functor;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Functor page makes about what {@code map} promises. */
@DisplayName("Functor: map keeps the container's shape")
class FunctorBookTest {

  private static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
          Instant.parse("2026-10-01T09:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  /** How many times {@code map} runs its function on the container given. */
  private static <F extends WitnessArity<TypeArity.Unary>> int runsOf(
      Functor<F> functor, Kind<F, Order> orders) {
    AtomicInteger calls = new AtomicInteger();
    functor.map(order -> calls.incrementAndGet(), orders);
    return calls.get();
  }

  @Test
  @DisplayName("on containers that hold no order, totals gives Nothing, the same Left, and []")
  void emptyContainersKeepTheirShape() {
    Maybe<Order> noOrder = Maybe.nothing();
    Either<String, Order> notFound = Either.left("order not found");
    List<Order> noOrders = List.of();

    assertThat(MAYBE.narrow(totals(functor(maybe()), MAYBE.widen(noOrder))))
        .isEqualTo(Maybe.nothing());
    assertThat(EITHER.narrow(totals(functor(either()), EITHER.widen(notFound)))).isSameAs(notFound);
    assertThat(LIST.narrow(totals(functor(list()), LIST.widen(noOrders)))).isEmpty();
  }

  @Test
  @DisplayName("an empty Optional stays empty, and a failed Try comes back as the same failure")
  void optionalAndTryKeepTheirShape() {
    Try<Order> declined = Try.failure(new IllegalStateException("payment declined"));

    assertThat(OPTIONAL.narrow(totals(functor(optional()), OPTIONAL.widen(Optional.empty()))))
        .isEmpty();
    assertThat(TRY.narrow(totals(functor(try_()), TRY.widen(declined)))).isSameAs(declined);
  }

  @Test
  @DisplayName(
      "map runs the function once per element, once on a Just or a Right, never when empty")
  void mapRunsTheFunctionAsTheTableSays() {
    Functor<EitherKind.Witness<String>> eitherFunctor = functor(either());

    assertThat(runsOf(functor(list()), LIST.widen(List.of(ORDER, ORDER, ORDER)))).isEqualTo(3);
    assertThat(runsOf(functor(maybe()), MAYBE.widen(Maybe.just(ORDER)))).isEqualTo(1);
    assertThat(runsOf(eitherFunctor, EITHER.widen(Either.right(ORDER)))).isEqualTo(1);
    assertThat(runsOf(functor(optional()), OPTIONAL.widen(Optional.of(ORDER)))).isEqualTo(1);

    assertThat(runsOf(functor(maybe()), MAYBE.widen(Maybe.nothing()))).isZero();
    assertThat(runsOf(functor(optional()), OPTIONAL.widen(Optional.empty()))).isZero();
    assertThat(runsOf(functor(list()), LIST.widen(List.of()))).isZero();
    assertThat(runsOf(eitherFunctor, EITHER.widen(Either.left("order not found")))).isZero();
    assertThat(
            runsOf(
                functor(try_()),
                TRY.widen(Try.failure(new IllegalStateException("payment declined")))))
        .isZero();
  }

  @Test
  @DisplayName("map with a step that can fail gives an Either inside an Either")
  void mapWithAFailingStepNests() {
    Order shipped =
        new Order(
            ORDER.id(),
            ORDER.customer(),
            ORDER.lines(),
            ORDER.placedAt(),
            ORDER.currency(),
            OrderStatus.SHIPPED);
    Functor<EitherKind.Witness<String>> eitherFunctor = functor(either());
    Function<Order, Either<String, Order>> refuseShipped =
        order ->
            order.status() == OrderStatus.SHIPPED
                ? Either.left("order already shipped")
                : Either.right(order);

    Either<String, Either<String, Order>> nested =
        EITHER.narrow(eitherFunctor.map(refuseShipped, EITHER.widen(Either.right(shipped))));

    assertThat(nested).hasToString("Right(Left(order already shipped))");
  }

  @Test
  @DisplayName("IO's map runs its function only when the IO runs, and again on each run")
  void ioMapRunsNothingYet() {
    AtomicInteger calls = new AtomicInteger();
    Kind<IOKind.Witness, Integer> mapped =
        functor(io()).map(order -> calls.incrementAndGet(), IO_OP.widen(IO.delay(() -> ORDER)));

    assertThat(calls).hasValue(0);
    IO_OP.narrow(mapped).unsafeRunSync();
    assertThat(calls).hasValue(1);
    IO_OP.narrow(mapped).unsafeRunSync();
    assertThat(calls).hasValue(2);
  }
}
