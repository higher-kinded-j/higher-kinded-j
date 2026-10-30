// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.bifunctor;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedBifunctor;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/bifunctor.html">Bifunctor</a> page: one
 * method that turns an order service's result into what its caller sees, written once for any type
 * with two sides and run on {@code Either} and {@code Validated}. The page {@code {{#include}}}s
 * the anchored regions, {@code BifunctorBookTest} holds the claims the page makes, and the book's
 * output gate runs {@link #main} against the comments in the regions.
 */
public final class BifunctorBook {

  private BifunctorBook() {}

  static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
          Instant.parse("2026-10-01T09:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  // ANCHOR: generic
  // One method for any type with two sides: the error becomes the message a caller sees, and the
  // order becomes its status
  public static <F extends WitnessArity<TypeArity.Binary>> Kind2<F, String, OrderStatus> forCaller(
      Bifunctor<F> bifunctor, Kind2<F, OrderError, Order> result) {
    return bifunctor.bimap(BifunctorBook::message, Order::status, result);
  }

  static String message(OrderError error) {
    return switch (error) {
      case OrderError.NotFound _ -> "no such order";
      case OrderError.AlreadyShipped _ -> "the order has shipped";
    };
  }

  // ANCHOR_END: generic

  public static void main(String[] args) {
    // ANCHOR: run
    Either<String, OrderStatus> found =
        EITHER.narrow2(forCaller(EitherBifunctor.INSTANCE, EITHER.widen2(Either.right(ORDER))));
    // Right(NEW)

    Validated<String, OrderStatus> missing =
        VALIDATED.narrow2(
            forCaller(
                ValidatedBifunctor.INSTANCE,
                VALIDATED.widen2(Validated.invalid(OrderError.notFound(ORDER.id())))));
    // Invalid(no such order)
    // ANCHOR_END: run
    System.out.println(found);
    System.out.println(missing);
  }
}
