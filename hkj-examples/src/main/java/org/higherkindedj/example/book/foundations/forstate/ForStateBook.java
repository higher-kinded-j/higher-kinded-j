// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.forstate;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.foroptics.ForOpticsBook;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/forstate_comprehension.html">ForState</a>
 * page: an order paid for with {@code ForState.withState}, its lines checked and its fields updated
 * through lenses, and a receipt whose values {@code For} gathers before {@code toState} names them
 * in a {@link Checkout}. The page {@code {{#include}}}s the anchored regions, {@code
 * ForStateBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the regions.
 */
public final class ForStateBook {

  private ForStateBook() {}

  static final MonadZero<MaybeKind.Witness> MAYBE_MONAD = Instances.monadZero(maybe());

  /** A new order of two lines, its customer's email typed with capitals. */
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

  /** A new order with a line the warehouse holds none of. */
  static final Order BACKORDERED =
      order(
          "00000000-0000-0000-0000-000000000003",
          OrderStatus.NEW,
          List.of(
              new LineItem("LAMP", 1, new BigDecimal("40.00")),
              new LineItem("CHAIR", 1, new BigDecimal("60.00"))));

  static Order order(String id, OrderStatus status, List<LineItem> lines) {
    return new Order(
        UUID.fromString(id),
        new Customer("Ada", new EmailAddress("Ada@Example.com")),
        lines,
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  /** What an order costs: each line's price times its quantity, summed. */
  static BigDecimal total(Order order) {
    return order.lines().stream()
        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // ANCHOR: in_stock
  // What the warehouse holds of each SKU; it holds no chairs
  static final Map<String, Integer> STOCK = Map.of("LAMP", 3, "BULB", 20, "DESK", 1);

  static Kind<MaybeKind.Witness, LineItem> inStock(LineItem line) {
    return STOCK.getOrDefault(line.sku(), 0) >= line.quantity()
        ? MAYBE.just(line)
        : MAYBE.nothing();
  }

  // ANCHOR_END: in_stock

  // ANCHOR: pay
  // Pay for a new order: every line must be in stock, and the email is kept in lower case
  public static Maybe<Order> pay(Order order) {
    return MAYBE.narrow(
        ForState.withState(MAYBE_MONAD, MAYBE.just(order))
            .when(current -> current.status() == OrderStatus.NEW)
            .traverse(OrderLenses.lines(), Traversals.forList(), ForStateBook::inStock)
            .modifyVia(
                OrderLenses.customer().andThen(CustomerLenses.email()),
                ForOpticsBook.EMAIL_TEXT,
                String::toLowerCase)
            .update(OrderLenses.status(), OrderStatus.PAID)
            .yield());
  }

  // ANCHOR_END: pay

  // ANCHOR: receipt
  // For gathers the order and a verified email; toState names them, with the total, in a Checkout
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> receipt(
      Monad<F> monad,
      Kind<F, Order> found,
      Function<Customer, Kind<F, EmailAddress>> verify,
      BigDecimal discount) {
    return For.from(monad, found)
        .from(order -> verify.apply(order.customer()))
        .toState((order, email) -> new Checkout(order, email, total(order)))
        .modify(CheckoutLenses.total(), total -> total.subtract(discount))
        .zoom(CheckoutLenses.order())
        .update(OrderLenses.status(), OrderStatus.PAID)
        .endZoom()
        .yield(
            checkout ->
                checkout.order().status()
                    + ": "
                    + checkout.total()
                    + " to "
                    + checkout.sendTo().value());
  }

  // ANCHOR_END: receipt

  public static void main(String[] args) {
    // ANCHOR: run_pay
    Maybe<String> paid =
        pay(ORDER).map(order -> order.status() + " " + order.customer().email().value());
    // Just(PAID ada@example.com)

    Maybe<Order> alreadyShipped = pay(SHIPPED);
    // Nothing

    Maybe<Order> backordered = pay(BACKORDERED);
    // Nothing
    // ANCHOR_END: run_pay

    // ANCHOR: run_receipt
    Monad<EitherKind.Witness<String>> eitherMonad = Instances.monad(either());
    BigDecimal discount = new BigDecimal("5.00");

    Either<String, String> sent =
        EITHER.narrow(
            receipt(
                eitherMonad,
                EITHER.widen(Either.right(ORDER)),
                customer -> EITHER.widen(Either.right(new EmailAddress("ada@example.com"))),
                discount));
    // Right(PAID: 45.00 to ada@example.com)

    Either<String, String> unverified =
        EITHER.narrow(
            receipt(
                eitherMonad,
                EITHER.widen(Either.right(ORDER)),
                customer -> EITHER.widen(Either.left("email not verified")),
                discount));
    // Left(email not verified)
    // ANCHOR_END: run_receipt

    System.out.println(paid);
    System.out.println(alreadyShipped);
    System.out.println(backordered);
    System.out.println(sent);
    System.out.println(unverified);
  }
}
