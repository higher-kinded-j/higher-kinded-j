// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.functor;

import static org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook.totals;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Instances.functor;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/functor.html">Functor</a> page: the
 * chapter introduction's {@code totals}, run on containers that hold no order, to show that {@code
 * map} keeps the container's shape. The page {@code {{#include}}}s the anchored region, {@code
 * FunctorBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the region.
 */
public final class FunctorBook {

  private FunctorBook() {}

  public static void main(String[] args) {
    // ANCHOR: shapes
    Maybe<Order> noOrder = Maybe.nothing();
    Maybe<BigDecimal> noTotal = MAYBE.narrow(totals(functor(maybe()), MAYBE.widen(noOrder)));
    // Nothing
    Either<String, Order> notFound = Either.left("order not found");
    Either<String, BigDecimal> sameLeft =
        EITHER.narrow(totals(functor(either()), EITHER.widen(notFound)));
    // Left(order not found)
    List<Order> noOrders = List.of();
    List<BigDecimal> noTotals = LIST.narrow(totals(functor(list()), LIST.widen(noOrders)));
    // []
    // ANCHOR_END: shapes
    System.out.println(noTotal);
    System.out.println(sameLeft);
    System.out.println(noTotals);
  }
}
