// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.forcomprehension;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/for_comprehension.html">For
 * Comprehensions</a> page: {@code receipt}, one chain written as nested {@code flatMap} calls and
 * again with {@code For}, then a filter, independent steps and a traversal. The page {@code
 * {{#include}}}s the anchored regions, {@code ForBookTest} holds what the page says each step does,
 * and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class ForBook {

  /** An order of two lines, not yet paid for. */
  static final Order ORDER =
      order(
          "00000000-0000-0000-0000-000000000001",
          OrderStatus.NEW,
          List.of(
              new LineItem("LAMP", 1, new BigDecimal("40.00")),
              new LineItem("BULB", 4, new BigDecimal("2.50"))));

  /** An order that has shipped. */
  static final Order SHIPPED =
      order(
          "00000000-0000-0000-0000-000000000002",
          OrderStatus.SHIPPED,
          List.of(new LineItem("DESK", 1, new BigDecimal("120.00"))));

  /** An order with a line nobody can supply. */
  static final Order BACKORDERED =
      order(
          "00000000-0000-0000-0000-000000000003",
          OrderStatus.NEW,
          List.of(
              new LineItem("LAMP", 1, new BigDecimal("40.00")),
              new LineItem("CHAIR", 0, new BigDecimal("60.00"))));

  private ForBook() {}

  /** What an order costs: each line's price times its quantity, summed. */
  static BigDecimal total(Order order) {
    return order.lines().stream().map(ForBook::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static BigDecimal lineTotal(LineItem line) {
    return line.price().multiply(BigDecimal.valueOf(line.quantity()));
  }

  // ANCHOR: nested
  // One receipt for any container F, as nested flatMap calls
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> receiptNested(
      Monad<F> monad,
      Kind<F, Order> found,
      Function<Customer, Kind<F, EmailAddress>> verify,
      Function<BigDecimal, Kind<F, String>> charge) {
    return monad.flatMap(
        order -> {
          BigDecimal total = total(order);
          return monad.flatMap(
              email ->
                  monad.map(
                      confirmation ->
                          "receipt " + confirmation + " for " + total + " to " + email.value(),
                      charge.apply(total)),
              verify.apply(order.customer()));
        },
        found);
  }

  // ANCHOR_END: nested

  // ANCHOR: generic
  // The same receipt, as a For comprehension: each step reads what the steps before it bound
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> receipt(
      Monad<F> monad,
      Kind<F, Order> found,
      Function<Customer, Kind<F, EmailAddress>> verify,
      Function<BigDecimal, Kind<F, String>> charge) {
    return For.from(monad, found)
        .let(order -> total(order))
        .from(t -> verify.apply(t._1().customer()))
        .from(t -> charge.apply(t._2()))
        .yield(
            (order, total, email, confirmation) ->
                "receipt " + confirmation + " for " + total + " to " + email.value());
  }

  // ANCHOR_END: generic

  static Order order(String id, OrderStatus status, List<LineItem> lines) {
    return new Order(
        UUID.fromString(id),
        new Customer("Ada", new EmailAddress("ada@example.com")),
        lines,
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  public static void main(String[] args) {
    // ANCHOR: instances
    Monad<EitherKind.Witness<String>> eitherMonad = Instances.monad(either());
    Function<BigDecimal, Kind<EitherKind.Witness<String>, String>> charge =
        amount -> EITHER.widen(Either.right("PAY-1"));

    Either<String, String> sent =
        EITHER.narrow(
            receipt(
                eitherMonad,
                EITHER.widen(Either.right(ORDER)),
                customer -> EITHER.widen(Either.right(customer.email())),
                charge));
    // Right(receipt PAY-1 for 50.00 to ada@example.com)

    Either<String, String> unverified =
        EITHER.narrow(
            receipt(
                eitherMonad,
                EITHER.widen(Either.right(ORDER)),
                customer -> EITHER.widen(Either.left("email not verified")),
                charge));
    // Left(email not verified)
    // ANCHOR_END: instances

    // ANCHOR: when
    MonadZero<ListKind.Witness> listMonad = Instances.monadZero(list());
    List<UUID> shippedIds =
        LIST.narrow(
            For.from(listMonad, LIST.widen(List.of(ORDER, SHIPPED)))
                .when(order -> order.status() == OrderStatus.SHIPPED)
                .yield(Order::id));
    // [00000000-0000-0000-0000-000000000002]

    MonadZero<MaybeKind.Witness> maybeMonad = Instances.monadZero(maybe());
    Maybe<UUID> notShipped =
        MAYBE.narrow(
            For.from(maybeMonad, MAYBE.just(ORDER))
                .when(order -> order.status() == OrderStatus.SHIPPED)
                .yield(Order::id));
    // Nothing
    // ANCHOR_END: when

    // ANCHOR: par
    Monad<ValidatedKind.Witness<List<String>>> validatedMonad =
        Instances.validated(Semigroups.list());
    Kind<ValidatedKind.Witness<List<String>>, String> blankSku =
        VALIDATED.widen(Validated.invalid(List.of("SKU is blank")));
    Kind<ValidatedKind.Witness<List<String>>, Integer> noQuantity =
        VALIDATED.widen(Validated.invalid(List.of("quantity must be positive")));

    Validated<List<String>, String> independent =
        VALIDATED.narrow(
            For.par(validatedMonad, blankSku, noQuantity)
                .yield((sku, quantity) -> quantity + " x " + sku));
    // Invalid([SKU is blank, quantity must be positive])

    Validated<List<String>, String> inSequence =
        VALIDATED.narrow(
            For.from(validatedMonad, blankSku)
                .from(sku -> noQuantity)
                .yield((sku, quantity) -> quantity + " x " + sku));
    // Invalid([SKU is blank])

    Either<String, String> fannedOut =
        EITHER.narrow(
            For.from(eitherMonad, EITHER.widen(Either.right(ORDER)))
                .par(
                    order -> EITHER.widen(Either.right(order.customer().email())),
                    order -> EITHER.widen(Either.right(total(order))))
                .yield((order, email, total) -> total + " to " + email.value()));
    // Right(50.00 to ada@example.com)
    // ANCHOR_END: par

    // ANCHOR: traverse
    Function<LineItem, Kind<MaybeKind.Witness, BigDecimal>> priceInStock =
        line -> line.quantity() > 0 ? MAYBE.just(lineTotal(line)) : MAYBE.nothing();

    Maybe<List<BigDecimal>> priced =
        MAYBE
            .narrow(
                For.from(maybeMonad, MAYBE.just(LIST.widen(ORDER.lines())))
                    .traverse(ListTraverse.INSTANCE, lines -> lines, priceInStock)
                    .yield((lines, prices) -> prices))
            .map(LIST::narrow);
    // Just([40.00, 10.00])

    Maybe<List<BigDecimal>> backordered =
        MAYBE
            .narrow(
                For.from(maybeMonad, MAYBE.just(LIST.widen(BACKORDERED.lines())))
                    .traverse(ListTraverse.INSTANCE, lines -> lines, priceInStock)
                    .yield((lines, prices) -> prices))
            .map(LIST::narrow);
    // Nothing

    Maybe<List<Integer>> quantities =
        MAYBE
            .narrow(
                For.from(maybeMonad, MAYBE.just(LIST.widen(List.of(MAYBE.just(1), MAYBE.just(4)))))
                    .sequence(ListTraverse.INSTANCE, items -> items)
                    .yield((items, values) -> values))
            .map(LIST::narrow);
    // Just([1, 4])

    Maybe<List<Integer>> flattened =
        MAYBE
            .narrow(
                For.from(maybeMonad, MAYBE.just(LIST.widen(List.of(1, 2))))
                    .flatTraverse(
                        ListTraverse.INSTANCE,
                        Instances.monad(list()),
                        numbers -> numbers,
                        n -> MAYBE.just(LIST.widen(List.of(n, n * 10))))
                    .yield((numbers, flat) -> flat))
            .map(LIST::narrow);
    // Just([1, 10, 2, 20])
    // ANCHOR_END: traverse

    System.out.println(sent);
    System.out.println(unverified);
    System.out.println(shippedIds);
    System.out.println(notShipped);
    System.out.println(independent);
    System.out.println(inSequence);
    System.out.println(fannedOut);
    System.out.println(priced);
    System.out.println(backordered);
    System.out.println(quantities);
    System.out.println(flattened);
  }
}
