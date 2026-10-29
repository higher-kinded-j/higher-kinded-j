// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.why;

import static org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook.order;
import static org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook.totals;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Instances.functor;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/hkts/hkt_introduction.html">Why Higher-Kinded
 * Types?</a> page: one line of logic written once for each of three containers in plain Java, then
 * the chapter's {@code totals}, written once for any container, run on the same three. The page
 * {@code {{#include}}}s the anchored regions, {@code WhyHktBookTest} holds the claims the page
 * makes, and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class WhyHktBook {

  private WhyHktBook() {}

  // ANCHOR: without
  // One line of logic, written again for each container
  public static List<BigDecimal> totalsOfList(List<Order> orders) {
    return orders.stream().map(FoundationsIntroBook::total).toList();
  }

  public static Optional<BigDecimal> totalOfOptional(Optional<Order> order) {
    return order.map(FoundationsIntroBook::total);
  }

  public static CompletableFuture<BigDecimal> totalOfFuture(CompletableFuture<Order> order) {
    return order.thenApply(FoundationsIntroBook::total);
  }

  // ANCHOR_END: without

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

    // ANCHOR: with
    List<BigDecimal> listTotals =
        LIST.narrow(totals(functor(list()), LIST.widen(List.of(order, refill))));
    // [50.00, 7.50]
    Optional<BigDecimal> optionalTotal =
        OPTIONAL.narrow(totals(functor(optional()), OPTIONAL.widen(Optional.of(order))));
    // Optional[50.00]
    BigDecimal futureTotal =
        FUTURE
            .narrow(
                totals(
                    functor(completableFuture()),
                    FUTURE.widen(CompletableFuture.completedFuture(order))))
            .join();
    // 50.00
    // ANCHOR_END: with
    System.out.println(listTotals);
    System.out.println(optionalTotal);
    System.out.println(futureTotal);
  }
}
