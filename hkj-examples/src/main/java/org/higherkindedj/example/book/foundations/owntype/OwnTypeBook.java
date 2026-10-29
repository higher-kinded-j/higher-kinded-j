// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.owntype;

import static org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook.totals;
import static org.higherkindedj.example.book.foundations.owntype.ResultKindHelper.RESULT;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.expression.For;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/hkts/extending-simulation.html">Adding Your Own
 * Type</a> page: a {@link Result} of the reader's own, used by generic code and given a Path once
 * its {@link ResultMonad} exists. The page {@code {{#include}}}s the anchored regions, {@code
 * OwnTypeBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the regions.
 */
public final class OwnTypeBook {

  private OwnTypeBook() {}

  // ANCHOR: path_of
  // A Result as a Path, with Result's Monad behind it
  public static <A> GenericPath<ResultKind.Witness, A> path(Result<A> result) {
    return GenericPath.of(RESULT.widen(result), ResultMonad.INSTANCE);
  }

  // An email at example.com is verified; any other fails
  public static Result<String> verified(EmailAddress email) {
    return email.value().endsWith("@example.com")
        ? Result.ok(email.value())
        : Result.failed("email not verified");
  }

  // ANCHOR_END: path_of

  public static void main(String[] args) {
    Order order =
        FoundationsIntroBook.order(
            "00000000-0000-0000-0000-000000000001",
            List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))));

    // ANCHOR: generic
    Result<BigDecimal> total =
        RESULT.narrow(totals(ResultMonad.INSTANCE, RESULT.widen(Result.ok(order))));
    // Ok[value=40.00]
    Result<String> receipt =
        RESULT.narrow(
            For.from(ResultMonad.INSTANCE, RESULT.widen(Result.ok(order)))
                .from(placed -> RESULT.widen(verified(placed.customer().email())))
                .yield((placed, email) -> "order " + placed.id() + " to " + email));
    // Ok[value=order 00000000-0000-0000-0000-000000000001 to ada@example.com]
    // ANCHOR_END: generic

    // ANCHOR: path
    Result<String> email =
        RESULT.narrow(
            path(Result.ok(order))
                .map(Order::customer)
                .via(customer -> path(verified(customer.email())))
                .runKind());
    // Ok[value=ada@example.com]
    Result<String> missing =
        RESULT.narrow(
            path(Result.<Order>failed("order not found"))
                .map(Order::customer)
                .via(customer -> path(verified(customer.email())))
                .runKind());
    // Failed[reason=order not found]
    // ANCHOR_END: path
    System.out.println(total);
    System.out.println(receipt);
    System.out.println(email);
    System.out.println(missing);
  }
}
