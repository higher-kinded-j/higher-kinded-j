// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.forcomprehension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.forcomprehension.ForBook.BACKORDERED;
import static org.higherkindedj.example.book.foundations.forcomprehension.ForBook.ORDER;
import static org.higherkindedj.example.book.foundations.forcomprehension.ForBook.SHIPPED;
import static org.higherkindedj.example.book.foundations.forcomprehension.ForBook.receipt;
import static org.higherkindedj.example.book.foundations.forcomprehension.ForBook.receiptNested;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the For Comprehensions page makes about each step. */
@DisplayName("For: each step does what the instance's flatMap, map2 or traverse does")
class ForBookTest {

  private static final Monad<EitherKind.Witness<String>> EITHER_MONAD = Instances.monad(either());

  private static Kind<EitherKind.Witness<String>, EmailAddress> verified(Customer customer) {
    return EITHER.widen(Either.right(customer.email()));
  }

  @Test
  @DisplayName("The For chain gives what the nested flatMap calls give, on every outcome")
  void theForChainMatchesTheNestedCalls() {
    Function<BigDecimal, Kind<EitherKind.Witness<String>, String>> paid =
        amount -> EITHER.widen(Either.right("PAY-1"));
    Function<BigDecimal, Kind<EitherKind.Witness<String>, String>> declined =
        amount -> EITHER.widen(Either.left("card declined"));
    List<Kind<EitherKind.Witness<String>, Order>> founds =
        List.of(EITHER.widen(Either.right(ORDER)), EITHER.widen(Either.left("order not found")));

    List<Function<Customer, Kind<EitherKind.Witness<String>, EmailAddress>>> verifies =
        List.of(ForBookTest::verified, customer -> EITHER.widen(Either.left("email not verified")));

    for (Kind<EitherKind.Witness<String>, Order> found : founds) {
      for (Function<Customer, Kind<EitherKind.Witness<String>, EmailAddress>> verify : verifies) {
        for (Function<BigDecimal, Kind<EitherKind.Witness<String>, String>> charge :
            List.of(paid, declined)) {
          assertThat(EITHER.narrow(receipt(EITHER_MONAD, found, verify, charge)))
              .isEqualTo(EITHER.narrow(receiptNested(EITHER_MONAD, found, verify, charge)));
        }
      }
    }
    assertThat(
            EITHER.narrow(
                receipt(
                    EITHER_MONAD, EITHER.widen(Either.right(ORDER)), ForBookTest::verified, paid)))
        .isEqualTo(Either.right("receipt PAY-1 for 50.00 to ada@example.com"));
  }

  @Test
  @DisplayName("Either never charges once verification has given a Left")
  void eitherNeverChargesAfterALeft() {
    List<BigDecimal> charged = new ArrayList<>();
    Either<String, String> unverified =
        EITHER.narrow(
            receipt(
                EITHER_MONAD,
                EITHER.widen(Either.right(ORDER)),
                customer -> EITHER.widen(Either.left("email not verified")),
                amount -> {
                  charged.add(amount);
                  return EITHER.widen(Either.right("PAY-1"));
                }));

    assertThat(unverified).isEqualTo(Either.left("email not verified"));
    assertThat(charged).isEmpty();
  }

  @Test
  @DisplayName("List's instance gives a receipt for every order")
  void listGivesAReceiptForEveryOrder() {
    List<String> receipts =
        LIST.narrow(
            receipt(
                Instances.monad(list()),
                LIST.widen(List.of(ORDER, SHIPPED)),
                customer -> LIST.widen(List.of(customer.email())),
                amount -> LIST.widen(List.of("PAY-" + amount))));

    assertThat(receipts)
        .containsExactly(
            "receipt PAY-50.00 for 50.00 to ada@example.com",
            "receipt PAY-120.00 for 120.00 to ada@example.com");
  }

  @Test
  @DisplayName("when drops a List element, and turns a Maybe into Nothing, through MonadZero")
  void whenFiltersThroughMonadZero() {
    List<Order> shipped =
        LIST.narrow(
            For.from(Instances.monadZero(list()), LIST.widen(List.of(ORDER, SHIPPED)))
                .when(order -> order.status() == SHIPPED.status())
                .yield(order -> order));
    Maybe<Order> kept =
        MAYBE.narrow(
            For.from(Instances.monadZero(maybe()), MAYBE.just(SHIPPED))
                .when(order -> order.status() == SHIPPED.status())
                .yield(order -> order));

    Maybe<Order> dropped =
        MAYBE.narrow(
            For.from(Instances.monadZero(maybe()), MAYBE.just(ORDER))
                .when(order -> order.status() == SHIPPED.status())
                .yield(order -> order));

    assertThat(shipped).containsExactly(SHIPPED);
    assertThat(kept).isEqualTo(Maybe.just(SHIPPED));
    assertThat(dropped).isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName(
      "For.par combines with the instance's map2: Either keeps the first error, List pairs")
  void forParCombinesWithTheInstancesMap2() {
    Either<String, String> firstError =
        EITHER.narrow(
            For.par(
                    EITHER_MONAD,
                    EITHER.<String, String>widen(Either.left("SKU is blank")),
                    EITHER.<String, Integer>widen(Either.left("quantity must be positive")))
                .yield((sku, quantity) -> quantity + " x " + sku));
    List<String> combinations =
        LIST.narrow(
            For.par(
                    Instances.monad(list()),
                    LIST.widen(List.of("LAMP", "DESK")),
                    LIST.widen(List.of(1, 2)))
                .yield((sku, quantity) -> sku + "x" + quantity));

    assertThat(firstError).isEqualTo(Either.left("SKU is blank"));
    assertThat(combinations).containsExactly("LAMPx1", "LAMPx2", "DESKx1", "DESKx2");
  }

  @Test
  @DisplayName("For.par over VTask's Monad runs the two tasks one after the other")
  void forParOnVTaskRunsInOrder() {
    // the first task waits briefly for the second to start, which it cannot if they take turns
    CountDownLatch bothStarted = new CountDownLatch(2);
    Kind<VTaskKind.Witness, Boolean> firstMetSecond =
        For.par(
                Instances.monad(vtask()),
                VTASK.widen(VTask.delay(() -> meet(bothStarted, 100))),
                VTASK.widen(VTask.delay(() -> meet(bothStarted, 100))))
            .yield((first, second) -> first);

    assertThat(VTASK.narrow(firstMetSecond).run()).isFalse();
  }

  @Test
  @DisplayName("ForPath.par on VTaskPath runs the two tasks at the same time")
  void forPathParOnVTaskPathRunsAtOnce() {
    // each task waits for the other to start, so they finish only if they run together
    CountDownLatch bothStarted = new CountDownLatch(2);
    Boolean together =
        ForPath.par(
                Path.vtask(() -> meet(bothStarted, 5_000)),
                Path.vtask(() -> meet(bothStarted, 5_000)))
            .yield((first, second) -> first && second)
            .unsafeRun();

    assertThat(together).isTrue();
  }

  @Test
  @DisplayName("For.par starts a chain from up to five values, and a step's par fans out four")
  void parTakesUpToFiveValuesAndFansOutFour() {
    Either<String, Integer> five =
        EITHER.narrow(
            For.par(
                    EITHER_MONAD,
                    EITHER.widen(Either.right(1)),
                    EITHER.widen(Either.right(2)),
                    EITHER.widen(Either.right(3)),
                    EITHER.widen(Either.right(4)),
                    EITHER.widen(Either.right(5)))
                .yield((a, b, c, d, e) -> a + b + c + d + e));
    Either<String, Integer> fanOfFour =
        EITHER.narrow(
            For.from(EITHER_MONAD, EITHER.widen(Either.right(10)))
                .par(
                    n -> EITHER.widen(Either.right(n + 1)),
                    n -> EITHER.widen(Either.right(n + 2)),
                    n -> EITHER.widen(Either.right(n + 3)),
                    n -> EITHER.widen(Either.right(n + 4)))
                .yield((n, a, b, c, d) -> a + b + c + d));

    assertThat(five).isEqualTo(Either.right(15));
    assertThat(fanOfFour).isEqualTo(Either.right(50));
  }

  @Test
  @DisplayName("A step's par fans out from the value bound before it")
  void aStepsParFansOut() {
    Either<String, String> fannedOut =
        EITHER.narrow(
            For.from(EITHER_MONAD, EITHER.widen(Either.right(ORDER)))
                .par(
                    order -> verified(order.customer()),
                    order -> EITHER.widen(Either.right(ForBook.total(order))))
                .yield((order, email, total) -> total + " to " + email.value()));

    assertThat(fannedOut).isEqualTo(Either.right("50.00 to ada@example.com"));
  }

  @Test
  @DisplayName("traverse prices every line or gives Nothing; sequence and flatTraverse collect")
  void traverseSequenceAndFlatTraverse() {
    Monad<MaybeKind.Witness> maybeMonad = Instances.monad(maybe());
    Function<LineItem, Kind<MaybeKind.Witness, BigDecimal>> priceInStock =
        line -> line.quantity() > 0 ? MAYBE.just(ForBook.lineTotal(line)) : MAYBE.nothing();

    Maybe<List<BigDecimal>> backordered =
        MAYBE
            .narrow(
                For.from(maybeMonad, MAYBE.just(LIST.widen(BACKORDERED.lines())))
                    .traverse(ListTraverse.INSTANCE, lines -> lines, priceInStock)
                    .yield((lines, prices) -> prices))
            .map(LIST::narrow);
    List<Kind<MaybeKind.Witness, Integer>> quantities = List.of(MAYBE.just(1), MAYBE.just(4));
    Maybe<List<Integer>> collected =
        MAYBE
            .narrow(
                For.from(maybeMonad, MAYBE.just(LIST.widen(quantities)))
                    .sequence(ListTraverse.INSTANCE, items -> items)
                    .yield((items, values) -> values))
            .map(LIST::narrow);
    Maybe<List<Integer>> flattened =
        MAYBE
            .narrow(
                For.from(maybeMonad, MAYBE.just(LIST.widen(List.of(1, 2))))
                    .flatTraverse(
                        ListTraverse.INSTANCE,
                        Instances.monadZero(list()),
                        numbers -> numbers,
                        n -> MAYBE.just(LIST.widen(List.of(n, n * 10))))
                    .yield((numbers, flat) -> flat))
            .map(LIST::narrow);

    assertThat(backordered).isEqualTo(Maybe.nothing());
    assertThat(collected).isEqualTo(Maybe.just(List.of(1, 4)));
    assertThat(flattened).isEqualTo(Maybe.just(List.of(1, 10, 2, 20)));
  }

  @Test
  @DisplayName("A chain takes twelve bindings, and its yield reads them by position")
  void aChainTakesTwelveBindings() {
    Monad<IdKind.Witness> idMonad = Instances.monad(id());
    Kind<IdKind.Witness, Integer> twelfth =
        For.from(idMonad, Id.of(1))
            .let(a -> 2)
            .let(t -> 3)
            .let(t -> 4)
            .let(t -> 5)
            .let(t -> 6)
            .let(t -> 7)
            .let(t -> 8)
            .let(t -> 9)
            .let(t -> 10)
            .let(t -> 11)
            .let(t -> t._11() + 1)
            .yield(t -> t._12());

    assertThat(ID.narrow(twelfth).value()).isEqualTo(12);
  }

  /** Counts this task in, and reports whether the other task started within {@code millis}. */
  private static boolean meet(CountDownLatch bothStarted, long millis) {
    bothStarted.countDown();
    try {
      return bothStarted.await(millis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
