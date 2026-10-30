// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/either_monad.html">Either</a> page: two
 * steps that return their error as a value, a chain of them that stops at the first {@code Left}, a
 * {@code switch} that reads the result, and the same steps through {@code Either}'s {@code
 * MonadError} instance. The page {@code {{#include}}}s the anchored regions, {@code EitherBookTest}
 * holds the claims the page makes, and the book's output gate runs {@link #main} against the
 * comments in the regions.
 */
public final class EitherBook {

  /** An order that has been placed and not yet paid for. */
  static final Order PLACED = order("00000000-0000-0000-0000-000000000001", OrderStatus.NEW);

  /** An order that has shipped, so it can no longer change. */
  static final Order SHIPPED = order("00000000-0000-0000-0000-000000000002", OrderStatus.SHIPPED);

  /** An id no order has. */
  static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-000000000009");

  /** The orders on record, by id. */
  static final Map<UUID, Order> ORDERS = Map.of(PLACED.id(), PLACED, SHIPPED.id(), SHIPPED);

  private EitherBook() {}

  // ANCHOR: steps
  // Each step returns its error as a value, rather than throwing it
  static Either<OrderError, Order> find(Map<UUID, Order> orders, UUID id) {
    Order order = orders.get(id);
    return order == null ? Either.left(OrderError.notFound(id)) : Either.right(order);
  }

  static Either<OrderError, Order> requireOpen(Order order) {
    return order.status() == OrderStatus.SHIPPED
        ? Either.left(OrderError.alreadyShipped(order.id()))
        : Either.right(order);
  }

  // ANCHOR_END: steps

  // ANCHOR: chain
  static Either<OrderError, Order> markPaid(Map<UUID, Order> orders, UUID id) {
    return find(orders, id)
        .flatMap(EitherBook::requireOpen)
        .map(order -> OrderFocus.status().set(OrderStatus.PAID, order));
  }

  // ANCHOR_END: chain

  // ANCHOR: describe
  static String describe(Either<OrderError, Order> result) {
    return switch (result) {
      case Either.Right(Order order) -> "order is " + order.status();
      case Either.Left(OrderError.NotFound(UUID id)) -> "no order " + id;
      case Either.Left(OrderError.AlreadyShipped(UUID id)) -> "order " + id + " has shipped";
    };
  }

  // ANCHOR_END: describe

  static Order order(String id, OrderStatus status) {
    return new Order(
        UUID.fromString(id),
        new Customer("Ada", new EmailAddress("ada@example.com")),
        List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  public static void main(String[] args) {
    // ANCHOR: outcomes
    Either<OrderError, Order> paid = markPaid(ORDERS, PLACED.id());
    // Right(Order[id=00000000-0000-0000-0000-000000000001, ..., status=PAID])
    Either<OrderError, Order> missing = markPaid(ORDERS, UNKNOWN);
    // Left(NotFound[orderId=00000000-0000-0000-0000-000000000009])
    Either<OrderError, Order> refused = markPaid(ORDERS, SHIPPED.id());
    // Left(AlreadyShipped[orderId=00000000-0000-0000-0000-000000000002])
    // ANCHOR_END: outcomes
    System.out.println(paid);
    System.out.println(missing);
    System.out.println(refused);

    // ANCHOR: creating
    Either<OrderError, Order> found = Either.right(PLACED);
    Either<OrderError, Order> notFound = Either.left(OrderError.notFound(UNKNOWN));

    // A step that succeeds with nothing to return
    Either<OrderError, Unit> saved = Either.right(Unit.INSTANCE);
    // ANCHOR_END: creating

    // ANCHOR: kind
    MonadError<EitherKind.Witness<OrderError>, OrderError> orderErrors =
        Instances.monadError(either());

    Kind<EitherKind.Witness<OrderError>, Order> shipped = EITHER.widen(find(ORDERS, SHIPPED.id()));
    Kind<EitherKind.Witness<OrderError>, Order> checked =
        orderErrors.flatMap(order -> EITHER.widen(requireOpen(order)), shipped);
    Kind<EitherKind.Witness<OrderError>, String> answer =
        orderErrors.handleError(orderErrors.map(order -> "open", checked), error -> "refused");

    Either<OrderError, Order> stillShipped = EITHER.narrow(checked);
    // Left(AlreadyShipped[orderId=00000000-0000-0000-0000-000000000002])
    Either<OrderError, String> reply = EITHER.narrow(answer);
    // Right(refused)
    // ANCHOR_END: kind
    System.out.println(stillShipped);
    System.out.println(reply);
  }
}
