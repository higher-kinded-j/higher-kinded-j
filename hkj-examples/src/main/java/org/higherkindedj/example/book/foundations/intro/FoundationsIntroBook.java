// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.intro;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Instances.functor;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
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
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/hkts/foundations_intro.html">Foundations</a>
 * chapter introduction: one method, written once over any container, run on three of them. The page
 * {@code {{#include}}}s the anchored regions, {@code FoundationsIntroBookTest} holds the claims the
 * page makes, and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class FoundationsIntroBook {

  private FoundationsIntroBook() {}

  /** What an order costs: each line's price times its quantity, summed. */
  static BigDecimal total(Order order) {
    return order.lines().stream()
        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // ANCHOR: generic
  // One method for any container F: the Functor passed in knows how to map inside F
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, BigDecimal> totals(
      Functor<F> functor, Kind<F, Order> orders) {
    return functor.map(FoundationsIntroBook::total, orders);
  }

  // ANCHOR_END: generic

  static Order order(String id, List<LineItem> lines) {
    return new Order(
        UUID.fromString(id),
        new Customer("Ada", new EmailAddress("ada@example.com")),
        lines,
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        OrderStatus.NEW);
  }

  public static void main(String[] args) {
    Order order =
        order(
            "00000000-0000-0000-0000-000000000001",
            List.of(
                new LineItem("LAMP", 1, new BigDecimal("40.00")),
                new LineItem("BULB", 4, new BigDecimal("2.50"))));
    Order refill =
        order(
            "00000000-0000-0000-0000-000000000002",
            List.of(new LineItem("BULB", 3, new BigDecimal("2.50"))));

    // ANCHOR: three
    Maybe<BigDecimal> maybeTotal =
        MAYBE.narrow(totals(functor(maybe()), MAYBE.widen(Maybe.just(order))));
    // Just(50.00)
    Either<String, Order> found = Either.right(order);
    Either<String, BigDecimal> eitherTotal =
        EITHER.narrow(totals(functor(either()), EITHER.widen(found)));
    // Right(50.00)
    List<BigDecimal> listTotals =
        LIST.narrow(totals(functor(list()), LIST.widen(List.of(order, refill))));
    // [50.00, 7.50]
    // ANCHOR_END: three
    System.out.println(maybeTotal);
    System.out.println(eitherTotal);
    System.out.println(listTotals);
  }
}
