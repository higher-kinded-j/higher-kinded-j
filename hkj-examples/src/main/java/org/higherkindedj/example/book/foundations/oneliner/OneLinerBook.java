// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.oneliner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.book.foundations.InMemoryOrderRepository;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.foundations.OrderRepository;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.either.Either;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/hkts/one_line_six_layers.html">One Line, Six
 * Layers</a> page: one service method written as a single Effect Path expression, and two more that
 * change only the edit in the middle. The page {@code {{#include}}}s the anchored regions, {@code
 * OneLinerBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the regions.
 */
public final class OneLinerBook {

  private OneLinerBook() {}

  // ANCHOR: line
  static EitherPath<OrderError, Order> changeEmail(
      OrderRepository orders, UUID id, String newEmail) {
    return orders
        .find(id)
        .toEitherPath(OrderError.notFound(id))
        .map(order -> OrderFocus.customer().email().value().set(newEmail, order))
        .via(orders::save);
  }

  // ANCHOR_END: line

  // ANCHOR: variants
  static EitherPath<OrderError, Order> markPaid(OrderRepository orders, UUID id) {
    return orders
        .find(id)
        .toEitherPath(OrderError.notFound(id))
        .map(order -> OrderFocus.status().set(OrderStatus.PAID, order))
        .via(orders::save);
  }

  static EitherPath<OrderError, Order> discountEveryLine(
      OrderRepository orders, UUID id, UnaryOperator<BigDecimal> discount) {
    return orders
        .find(id)
        .toEitherPath(OrderError.notFound(id))
        .map(order -> OrderFocus.lines().price().modifyAll(discount, order))
        .via(orders::save);
  }

  // ANCHOR_END: variants

  static Order order(UUID id, OrderStatus status) {
    return new Order(
        id,
        new Customer("Ada", new EmailAddress("ada@example.com")),
        List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  public static void main(String[] args) {
    UUID placed = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID shipped = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID unknown = UUID.fromString("00000000-0000-0000-0000-000000000009");
    OrderRepository orders =
        InMemoryOrderRepository.of(
            order(placed, OrderStatus.NEW), order(shipped, OrderStatus.SHIPPED));

    // ANCHOR: outcomes
    Either<OrderError, Order> changed = changeEmail(orders, placed, "ada@example.org").run();
    // Right(Order[id=..., customer=Customer[name=Ada,
    //   email=EmailAddress[value=ada@example.org]], ...])
    Either<OrderError, Order> missing = changeEmail(orders, unknown, "ada@example.org").run();
    // Left(NotFound[orderId=...])
    Either<OrderError, Order> refused = changeEmail(orders, shipped, "ada@example.org").run();
    // Left(AlreadyShipped[orderId=...])
    // ANCHOR_END: outcomes
    System.out.println(changed);
    System.out.println(missing);
    System.out.println(refused);
  }
}
