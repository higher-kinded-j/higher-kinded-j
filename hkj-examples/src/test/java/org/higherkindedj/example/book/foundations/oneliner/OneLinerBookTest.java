// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.oneliner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Instances.monad;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.InMemoryOrderRepository;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.foundations.OrderRepository;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the One Line, Six Layers page makes about its one service method. */
@DisplayName("One Line, Six Layers: one service method as one Effect Path expression")
class OneLinerBookTest {

  private static final UUID PLACED = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SHIPPED = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-000000000009");
  private static final String NEW_EMAIL = "ada@example.org";

  private static OrderRepository orders() {
    return InMemoryOrderRepository.of(
        OneLinerBook.order(PLACED, OrderStatus.NEW),
        OneLinerBook.order(SHIPPED, OrderStatus.SHIPPED));
  }

  private static String email(Order order) {
    return OrderFocus.customer().email().value().get(order);
  }

  /** Counts the calls that reach the repository, so a test can see which steps ran. */
  private static final class Counting implements OrderRepository {
    private final OrderRepository delegate = orders();
    final AtomicInteger saves = new AtomicInteger();

    @Override
    public MaybePath<Order> find(UUID id) {
      return delegate.find(id);
    }

    @Override
    public EitherPath<OrderError, Order> save(Order order) {
      saves.incrementAndGet();
      return delegate.save(order);
    }
  }

  @Test
  @DisplayName("a placed order gets the new address, and the repository keeps it")
  void changesAndSaves() {
    OrderRepository orders = orders();

    Order before = OneLinerBook.order(PLACED, OrderStatus.NEW);
    Order expected =
        new Order(
            PLACED,
            new Customer("Ada", new EmailAddress(NEW_EMAIL)),
            before.lines(),
            before.placedAt(),
            before.currency(),
            before.status());

    Either<OrderError, Order> changed = OneLinerBook.changeEmail(orders, PLACED, NEW_EMAIL).run();

    assertThatEither(changed).isRight().hasRight(expected);
    assertThat(orders.find(PLACED).map(OneLinerBookTest::email).getOrElse("gone"))
        .isEqualTo(NEW_EMAIL);
  }

  @Test
  @DisplayName("an unknown id gives NotFound, and save never runs")
  void unknownIdSkipsTheRest() {
    Counting orders = new Counting();

    Either<OrderError, Order> missing = OneLinerBook.changeEmail(orders, UNKNOWN, NEW_EMAIL).run();

    assertThatEither(missing).isLeft().hasLeft(new OrderError.NotFound(UNKNOWN));
    assertThat(orders.saves).hasValue(0);
  }

  @Test
  @DisplayName("a shipped order is refused by save, with its own reason, and stays as it was")
  void shippedOrderIsRefused() {
    OrderRepository orders = orders();

    Either<OrderError, Order> refused = OneLinerBook.changeEmail(orders, SHIPPED, NEW_EMAIL).run();

    assertThatEither(refused).isLeft().hasLeft(new OrderError.AlreadyShipped(SHIPPED));
    assertThat(orders.find(SHIPPED).map(OneLinerBookTest::email).getOrElse("gone"))
        .isEqualTo("ada@example.com");
  }

  @Test
  @DisplayName("the variants change only the edit: mark paid, and discount every line")
  void variantsShareTheSkeleton() {
    OrderRepository orders = orders();

    Either<OrderError, Order> paid = OneLinerBook.markPaid(orders, PLACED).run();
    Either<OrderError, Order> discounted =
        OneLinerBook.discountEveryLine(
                orders, PLACED, price -> price.multiply(new BigDecimal("0.90")))
            .run();

    assertThatEither(paid)
        .isRight()
        .hasRightSatisfying(order -> assertThat(order.status()).isEqualTo(OrderStatus.PAID));
    assertThatEither(discounted)
        .isRight()
        .hasRightSatisfying(
            order -> assertThat(order.lines().getFirst().price()).isEqualByComparingTo("36.00"));
    assertThatEither(OneLinerBook.markPaid(orders, SHIPPED).run())
        .isLeft()
        .hasLeft(new OrderError.AlreadyShipped(SHIPPED));
    assertThatEither(OneLinerBook.markPaid(orders, UNKNOWN).run())
        .isLeft()
        .hasLeft(new OrderError.NotFound(UNKNOWN));
  }

  @Test
  @DisplayName("an EitherPath and a MaybePath are not a Kind")
  void aPathIsNotAKind() {
    assertThat(Kind.class.isAssignableFrom(EitherPath.class)).isFalse();
    assertThat(Kind.class.isAssignableFrom(MaybePath.class)).isFalse();
  }

  @Test
  @DisplayName("map and via on the Path agree with Functor's map and Monad's flatMap over Kind")
  void twoEncodingsOneAlgebra() {
    Monad<EitherKind.Witness<OrderError>> monad = monad(either());
    Function<Order, Order> edit =
        order -> OrderFocus.customer().email().value().set(NEW_EMAIL, order);

    for (UUID id : new UUID[] {PLACED, SHIPPED, UNKNOWN}) {
      OrderRepository onPath = orders();
      OrderRepository overKind = orders();
      Either<OrderError, Order> viaPath = OneLinerBook.changeEmail(onPath, id, NEW_EMAIL).run();

      Kind<EitherKind.Witness<OrderError>, Order> found =
          EITHER.widen(overKind.find(id).toEitherPath(OrderError.notFound(id)).run());
      Either<OrderError, Order> viaKind =
          EITHER.narrow(
              monad.flatMap(
                  order -> EITHER.widen(overKind.save(order).run()), monad.map(edit, found)));

      assertThat(viaKind).isEqualTo(viaPath);
    }
  }
}
