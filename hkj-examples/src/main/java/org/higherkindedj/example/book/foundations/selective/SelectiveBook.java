// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.selective;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeSelective;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/selective.html">Selective</a> page: a
 * price parsed only when it still needs parsing, an order flagged for review only when it is large,
 * and a courier booked only for the branch the order's size picks. The page {@code {{#include}}}s
 * the anchored regions, {@code SelectiveBookTest} holds the claims the page makes, and the book's
 * output gate runs {@link #main} against the comments in the regions.
 */
public final class SelectiveBook {

  private SelectiveBook() {}

  // ANCHOR: large
  // An order above this total is large: it is reviewed, and it goes by freight
  static final BigDecimal LARGE = new BigDecimal("100.00");

  static Kind<IOKind.Witness, Boolean> isLarge(BigDecimal total) {
    return IO_OP.widen(IO.delay(() -> total.compareTo(LARGE) > 0));
  }

  // ANCHOR_END: large

  // ANCHOR: review
  // Flags an order for review, but only when it is large
  static Kind<IOKind.Witness, Unit> reviewIfLarge(BigDecimal total, List<String> flagged) {
    Kind<IOKind.Witness, Unit> flag =
        IO_OP.widen(IO.fromRunnable(() -> flagged.add("review " + total)));
    return IOSelective.INSTANCE.whenS(isLarge(total), flag);
  }

  // Runs the review for each total, and gives what it flagged
  static List<String> reviewAll(List<BigDecimal> totals) {
    List<String> flagged = new ArrayList<>();
    totals.forEach(total -> IO_OP.narrow(reviewIfLarge(total, flagged)).unsafeRunSync());
    return flagged;
  }

  // ANCHOR_END: review

  // ANCHOR: courier
  // Books freight for a large order and post for any other: only the chosen booking runs
  static Kind<IOKind.Witness, String> bookCourier(BigDecimal total, List<String> booked) {
    Kind<IOKind.Witness, String> freight = book("freight", booked);
    Kind<IOKind.Witness, String> post = book("post", booked);
    return IOSelective.INSTANCE.ifS(isLarge(total), freight, post);
  }

  // Books a courier for the total, and gives every booking made
  static List<String> bookingsFor(BigDecimal total) {
    List<String> booked = new ArrayList<>();
    IO_OP.narrow(bookCourier(total, booked)).unsafeRunSync();
    return booked;
  }

  static Kind<IOKind.Witness, String> book(String courier, List<String> booked) {
    return IO_OP.widen(
        IO.delay(
            () -> {
              booked.add(courier);
              return courier;
            }));
  }

  // ANCHOR_END: courier

  public static void main(String[] args) {
    // ANCHOR: select
    Selective<MaybeKind.Witness> selective = MaybeSelective.INSTANCE;
    Kind<MaybeKind.Witness, Function<String, BigDecimal>> parse = MAYBE.just(BigDecimal::new);

    // A Left still needs parsing, so select applies the function to it
    Maybe<BigDecimal> parsed =
        MAYBE.narrow(selective.select(MAYBE.just(Selective.left("40.00")), parse));
    // Just(40.00)

    // A Right is ready, so select never needs the function, even a missing one
    Maybe<BigDecimal> ready =
        MAYBE.narrow(
            selective.select(MAYBE.just(Selective.right(new BigDecimal("2.50"))), MAYBE.nothing()));
    // Just(2.50)
    // ANCHOR_END: select
    System.out.println(parsed);
    System.out.println(ready);

    // ANCHOR: review_run
    List<String> flagged = reviewAll(List.of(new BigDecimal("50.00"), new BigDecimal("120.00")));
    // [review 120.00]
    // ANCHOR_END: review_run
    System.out.println(flagged);

    // ANCHOR: courier_run
    List<String> booked = bookingsFor(new BigDecimal("50.00"));
    // [post]
    // ANCHOR_END: courier_run
    System.out.println(booked);
  }
}
