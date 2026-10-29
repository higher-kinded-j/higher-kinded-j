// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.selfcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.selfcheck.SelfCheckBook.countLines;
import static org.higherkindedj.example.book.foundations.selfcheck.SelfCheckBook.fromCacheOr;
import static org.higherkindedj.example.book.foundations.selfcheck.SelfCheckBook.receiptLine;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds every result the answers on the Foundations self-check predict. */
@DisplayName("Foundations self-check: every answer, proved")
class SelfCheckBookTest {

  private static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(
              new LineItem("LAMP", 1, new BigDecimal("40.00")),
              new LineItem("BULB", 4, new BigDecimal("2.50"))),
          Instant.parse("2026-10-01T09:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  private static final Monad<EitherKind.Witness<String>> EITHER_MONAD = Instances.monad(either());

  private static BigDecimal total(Order order) {
    return order.lines().stream()
        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Test
  @DisplayName("1: map goes through Maybe's instance, and narrow gives the Maybe back")
  void checkpoint1KindHasNoMethods() {
    Kind<MaybeKind.Witness, Order> found = MAYBE.widen(Maybe.just(ORDER));

    Maybe<BigDecimal> throughFunctor =
        MAYBE.narrow(Instances.functor(maybe()).map(SelfCheckBookTest::total, found));
    Maybe<BigDecimal> throughMonad =
        MAYBE.narrow(Instances.monad(maybe()).map(SelfCheckBookTest::total, found));

    assertThat(throughFunctor).isEqualTo(Maybe.just(new BigDecimal("50.00")));
    assertThat(throughMonad).isEqualTo(throughFunctor);
  }

  @Test
  @DisplayName("2: EitherPath holds no instance, and its via agrees with Either's flatMap")
  void checkpoint2ViaHoldsNoInstance() {
    List<String> ran = new ArrayList<>();
    Function<Order, Either<String, Customer>> findCustomer =
        order -> {
          ran.add("step");
          return Either.right(order.customer());
        };

    for (Either<String, Order> found :
        List.of(Either.<String, Order>right(ORDER), Either.<String, Order>left("not found"))) {
      EitherPath<String, Order> path = Path.either(found);
      Either<String, Customer> viaPath =
          path.via(order -> Path.either(findCustomer.apply(order))).run();
      Either<String, Customer> viaMonad =
          EITHER.narrow(
              EITHER_MONAD.flatMap(
                  order -> EITHER.widen(findCustomer.apply(order)), EITHER.widen(found)));
      assertThat(viaPath).isEqualTo(viaMonad);
    }
    assertThat(ran).hasSize(2);
    assertThat(Arrays.stream(EitherPath.class.getDeclaredFields()).map(Field::getType))
        .noneMatch(Functor.class::isAssignableFrom);
  }

  @Test
  @DisplayName(
      "3: of(null) throws on Either and gives Nothing on Maybe, and Unit.INSTANCE fits both")
  void checkpoint3UnitNotVoid() {
    assertThatThrownBy(() -> EITHER_MONAD.of(null)).isInstanceOf(NullPointerException.class);
    assertThat(MAYBE.narrow(Instances.monad(maybe()).of(null))).isEqualTo(Maybe.nothing());
    assertThat(EITHER.narrow(EITHER_MONAD.of(Unit.INSTANCE)))
        .isEqualTo(Either.right(Unit.INSTANCE));
    assertThat(MAYBE.narrow(Instances.monad(maybe()).of(Unit.INSTANCE)))
        .isEqualTo(Maybe.just(Unit.INSTANCE));
  }

  @Test
  @DisplayName(
      "4: a future that failed in an earlier stage hands the handler the original exception")
  void checkpoint4FutureHandlerGetsTheCause() {
    MonadError<CompletableFutureKind.Witness, Throwable> futures =
        Instances.monadError(completableFuture());
    IllegalStateException declined = new IllegalStateException("card declined");
    List<Throwable> seen = new ArrayList<>();
    CompletableFuture<Order> charged =
        CompletableFuture.supplyAsync(
                () -> {
                  throw declined;
                })
            .thenApply(nothing -> ORDER);

    Order recovered =
        FUTURE
            .narrow(
                futures.handleErrorWith(
                    FUTURE.widen(charged),
                    error -> {
                      seen.add(error);
                      return futures.of(ORDER);
                    }))
            .join();

    assertThat(recovered).isEqualTo(ORDER);
    assertThat(seen).singleElement().isSameAs(declined);
  }

  @Test
  @DisplayName("5: monad, applicative and functor on the try_() token are one object")
  void checkpoint5OneObject() {
    Object monad = Instances.monad(try_());

    assertThat(Instances.applicative(try_())).isSameAs(monad);
    assertThat(Instances.functor(try_())).isSameAs(monad);
  }

  @Test
  @DisplayName("6: map with a step that returns an Either nests it, and flatMap flattens it")
  void checkpoint6MapNestsFlatMapFlattens() {
    Function<Order, Kind<EitherKind.Witness<String>, Customer>> findCustomer =
        order -> EITHER.widen(Either.right(order.customer()));
    Kind<EitherKind.Witness<String>, Order> found = EITHER.widen(Either.right(ORDER));

    Kind<EitherKind.Witness<String>, Kind<EitherKind.Witness<String>, Customer>> nested =
        EITHER_MONAD.map(findCustomer, found);
    Kind<EitherKind.Witness<String>, Customer> flat = EITHER_MONAD.flatMap(findCustomer, found);

    assertThat(EITHER.narrow(nested).getRight()).isEqualTo(findCustomer.apply(ORDER));
    assertThat(EITHER.narrow(flat)).isEqualTo(Either.right(ORDER.customer()));
  }

  @Test
  @DisplayName(
      "7: traverse with Validated and a list semigroup gives both errors, Either the first")
  void checkpoint7TraverseKeepsWhatTheInstanceKeeps() {
    List<LineItem> lines =
        List.of(
            new LineItem("LAMP", 1, new BigDecimal("40.00")),
            new LineItem(" ", 0, new BigDecimal("2.50")),
            new LineItem("", 4, new BigDecimal("2.50")));
    Applicative<ValidatedKind.Witness<List<String>>> validated =
        Instances.validated(Semigroups.list());

    Validated<List<String>, ?> everyError =
        VALIDATED.narrow(
            ListTraverse.INSTANCE.traverse(
                validated,
                line ->
                    VALIDATED.widen(
                        line.sku().isBlank()
                            ? Validated.<List<String>, String>invalid(
                                List.of("line " + lines.indexOf(line) + ": SKU is blank"))
                            : Validated.<List<String>, String>valid(line.sku())),
                LIST.widen(lines)));
    Either<List<String>, ?> firstError =
        EITHER.narrow(
            ListTraverse.INSTANCE.traverse(
                Instances.<EitherKind.Witness<List<String>>>applicative(either()),
                line ->
                    EITHER.widen(
                        line.sku().isBlank()
                            ? Either.<List<String>, String>left(
                                List.of("line " + lines.indexOf(line) + ": SKU is blank"))
                            : Either.<List<String>, String>right(line.sku())),
                LIST.widen(lines)));

    assertThat(everyError)
        .isEqualTo(Validated.invalid(List.of("line 1: SKU is blank", "line 2: SKU is blank")));
    assertThat(firstError).isEqualTo(Either.left(List.of("line 1: SKU is blank")));
  }

  @Test
  @DisplayName("8: a Maybe widens to itself every time, and an Optional to a new holder each time")
  void checkpoint8WidenTwice() {
    Maybe<Order> maybeOrder = Maybe.just(ORDER);
    Optional<Order> optionalOrder = Optional.of(ORDER);

    assertThat(MAYBE.widen(maybeOrder)).isSameAs(MAYBE.widen(maybeOrder));
    assertThat(OPTIONAL.widen(optionalOrder)).isNotSameAs(OPTIONAL.widen(optionalOrder));
  }

  @Test
  @DisplayName("9: on List, par and a chain of from steps both give every combination")
  void checkpoint9ParAndFromAgreeOnList() {
    Monad<ListKind.Witness> lists = Instances.monad(list());
    Kind<ListKind.Witness, String> skus = LIST.widen(List.of("LAMP", "DESK"));
    Kind<ListKind.Witness, Integer> quantities = LIST.widen(List.of(1, 2));

    List<String> withPar =
        LIST.narrow(For.par(lists, skus, quantities).yield((sku, n) -> sku + "x" + n));
    List<String> withFrom =
        LIST.narrow(For.from(lists, skus).from(sku -> quantities).yield((sku, n) -> sku + "x" + n));

    assertThat(withPar).containsExactly("LAMPx1", "LAMPx2", "DESKx1", "DESKx2");
    assertThat(withFrom).isEqualTo(withPar);
  }

  @Test
  @DisplayName("10: countLines needs only a Functor, and runs on a Maybe and a List")
  void checkpoint10CountLines() {
    assertThat(MAYBE.narrow(countLines(Instances.functor(maybe()), MAYBE.widen(Maybe.just(ORDER)))))
        .isEqualTo(Maybe.just(2));
    assertThat(
            LIST.narrow(countLines(Instances.functor(list()), LIST.widen(List.of(ORDER, ORDER)))))
        .containsExactly(2, 2);
  }

  @Test
  @DisplayName("11: receiptLine needs only an Applicative: Validated keeps both errors, Either one")
  void checkpoint11ReceiptLine() {
    Applicative<ValidatedKind.Witness<List<String>>> validated =
        Instances.validated(Semigroups.list());
    Applicative<EitherKind.Witness<List<String>>> eithers = Instances.applicative(either());

    Validated<List<String>, String> line =
        VALIDATED.narrow(
            receiptLine(
                validated,
                VALIDATED.widen(Validated.valid(ORDER)),
                VALIDATED.widen(Validated.valid(new EmailAddress("ada@example.com")))));
    Validated<List<String>, String> bothErrors =
        VALIDATED.narrow(
            receiptLine(
                validated,
                VALIDATED.widen(Validated.invalid(List.of("order not found"))),
                VALIDATED.widen(Validated.invalid(List.of("email not verified")))));
    Either<List<String>, String> firstError =
        EITHER.narrow(
            receiptLine(
                eithers,
                EITHER.widen(Either.left(List.of("order not found"))),
                EITHER.widen(Either.left(List.of("email not verified")))));

    assertThat(line)
        .isEqualTo(Validated.valid("00000000-0000-0000-0000-000000000001 to ada@example.com"));
    assertThat(bothErrors)
        .isEqualTo(Validated.invalid(List.of("order not found", "email not verified")));
    assertThat(firstError).isEqualTo(Either.left(List.of("order not found")));
  }

  @Test
  @DisplayName("12: fromCacheOr calls the service only when the cache missed")
  void checkpoint12FromCacheOr() {
    MonadError<EitherKind.Witness<String>, String> eithers = Instances.monadError(either());
    List<String> serviceCalls = new ArrayList<>();
    Kind<EitherKind.Witness<String>, Order> fromService = EITHER.widen(Either.right(ORDER));

    Either<String, Order> cached =
        EITHER.narrow(
            fromCacheOr(
                eithers,
                EITHER.widen(Either.right(ORDER)),
                () -> {
                  serviceCalls.add("call");
                  return fromService;
                }));
    Either<String, Order> missed =
        EITHER.narrow(
            fromCacheOr(
                eithers,
                EITHER.widen(Either.left("not cached")),
                () -> {
                  serviceCalls.add("call");
                  return fromService;
                }));

    assertThat(cached).isEqualTo(Either.right(ORDER));
    assertThat(missed).isEqualTo(Either.right(ORDER));
    assertThat(serviceCalls).containsExactly("call");
  }
}
