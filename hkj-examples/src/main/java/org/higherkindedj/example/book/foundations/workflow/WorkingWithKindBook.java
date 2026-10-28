// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.workflow;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/hkts/usage-guide.html">Working with Kind</a> page:
 * one method written once over {@code MonadError}, taken through the steps of widening, calling and
 * narrowing on a {@code Maybe}, then run unchanged on an {@code Either}. The page {@code
 * {{#include}}}s the anchored regions, {@code WorkingWithKindBookTest} holds the claims the page
 * makes, and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class WorkingWithKindBook {

  private WorkingWithKindBook() {}

  /** What an order costs: each line's price times its quantity, summed. */
  static BigDecimal total(Order order) {
    return order.lines().stream()
        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // ANCHOR: generic
  // One method for any container F that can hold a failure of type E
  static <F extends WitnessArity<TypeArity.Unary>, E> Kind<F, BigDecimal> totalUnlessShipped(
      MonadError<F, E> monadError, Kind<F, Order> order, E refusal) {
    return monadError.flatMap(
        o ->
            o.status() == OrderStatus.SHIPPED
                ? monadError.raiseError(refusal)
                : monadError.of(total(o)),
        order);
  }

  // ANCHOR_END: generic

  // ANCHOR: recover
  // Generic code cannot narrow, so it recovers through the instance
  static <F extends WitnessArity<TypeArity.Unary>, E> Kind<F, BigDecimal> totalOrZero(
      MonadError<F, E> monadError, Kind<F, Order> order, E refusal) {
    return monadError.handleErrorWith(
        totalUnlessShipped(monadError, order, refusal), _ -> monadError.of(BigDecimal.ZERO));
  }

  // ANCHOR_END: recover

  static Order order(String id, OrderStatus status) {
    return new Order(
        UUID.fromString(id),
        new Customer("Ada", new EmailAddress("ada@example.com")),
        List.of(
            new LineItem("LAMP", 1, new BigDecimal("40.00")),
            new LineItem("BULB", 4, new BigDecimal("2.50"))),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  public static void main(String[] args) {
    Order placed = order("00000000-0000-0000-0000-000000000001", OrderStatus.NEW);
    Order shipped = order("00000000-0000-0000-0000-000000000002", OrderStatus.SHIPPED);

    // ANCHOR: steps
    // steps 1 and 2: Maybe's witness, named by maybe(), and the instance for it
    MonadError<MaybeKind.Witness, Unit> maybeErrors = Instances.monadError(maybe());

    // step 3, widen: the Maybe, seen as a Kind
    Kind<MaybeKind.Witness, Order> found = MAYBE.widen(Maybe.just(placed));

    // step 4, call: the method sees only the Kind and the instance
    Kind<MaybeKind.Witness, BigDecimal> totalKind =
        totalUnlessShipped(maybeErrors, found, Unit.INSTANCE);

    // step 5, narrow: back to a Maybe
    Maybe<BigDecimal> maybeTotal = MAYBE.narrow(totalKind);
    // Just(50.00)
    // ANCHOR_END: steps

    // ANCHOR: either
    // the same method, unchanged, with Either's instance and a message as the error
    MonadError<EitherKind.Witness<String>, String> eitherErrors = Instances.monadError(either());

    Either<String, BigDecimal> refused =
        EITHER.narrow(
            totalUnlessShipped(
                eitherErrors, EITHER.widen(Either.right(shipped)), "order already shipped"));
    // Left(order already shipped)
    // ANCHOR_END: either

    // ANCHOR: recovered
    // a shipped order, recovered to zero before narrowing
    Maybe<BigDecimal> orZero =
        MAYBE.narrow(totalOrZero(maybeErrors, MAYBE.widen(Maybe.just(shipped)), Unit.INSTANCE));
    // Just(0)
    // ANCHOR_END: recovered

    System.out.println(maybeTotal);
    System.out.println(refused);
    System.out.println(orZero);
  }
}
