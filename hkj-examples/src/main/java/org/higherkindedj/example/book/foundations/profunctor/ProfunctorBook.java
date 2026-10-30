// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.profunctor;

import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;

import java.math.BigDecimal;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.monoid.MonoidBook;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.func.FunctionKind;
import org.higherkindedj.hkt.func.FunctionProfunctor;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/profunctor.html">Profunctor</a> page:
 * Semigroup and Monoid's {@code lineTotal}, adapted with {@code lmap}, {@code rmap} and {@code
 * dimap} to take a warehouse feed's text and to give a total in pence, then the same adaptation in
 * plain Java. The page {@code {{#include}}}s the anchored regions, {@code ProfunctorBookTest} holds
 * the claims the page makes, and the book's output gate runs {@link #main} against the comments in
 * the regions.
 */
public final class ProfunctorBook {

  private ProfunctorBook() {}

  // ANCHOR: adapters
  // A line as the warehouse's feed sends it, such as "BULB,4,2.50"
  public static LineItem parse(String feedLine) {
    String[] fields = feedLine.split(",");
    return new LineItem(fields[0], Integer.parseInt(fields[1]), new BigDecimal(fields[2]));
  }

  // A total in pence, as a card payment is taken
  public static int pence(BigDecimal total) {
    return total.movePointRight(2).intValueExact();
  }

  // ANCHOR_END: adapters

  public static void main(String[] args) {
    // ANCHOR: adapt
    FunctionProfunctor profunctor = FunctionProfunctor.INSTANCE;
    Kind2<FunctionKind.Witness, LineItem, BigDecimal> total = FUNCTION.widen(MonoidBook::lineTotal);

    // lmap: the function now takes the feed's text, and parses it before it totals
    Function<String, BigDecimal> fromFeed =
        FUNCTION.getFunction(profunctor.lmap(ProfunctorBook::parse, total));
    BigDecimal bulbs = fromFeed.apply("BULB,4,2.50");
    // 10.00

    // rmap: the function now gives its total in pence
    Function<LineItem, Integer> inPence =
        FUNCTION.getFunction(profunctor.rmap(ProfunctorBook::pence, total));
    int lamp = inPence.apply(new LineItem("LAMP", 1, new BigDecimal("40.00")));
    // 4000

    // dimap: both at once, from the feed's text to pence
    Function<String, Integer> feedToPence =
        FUNCTION.getFunction(profunctor.dimap(ProfunctorBook::parse, ProfunctorBook::pence, total));
    int bulbsInPence = feedToPence.apply("BULB,4,2.50");
    // 1000
    // ANCHOR_END: adapt
    System.out.println(bulbs);
    System.out.println(lamp);
    System.out.println(bulbsInPence);

    // ANCHOR: plain
    Function<LineItem, BigDecimal> lineTotal = MonoidBook::lineTotal;
    Function<String, Integer> plainJava =
        lineTotal.compose(ProfunctorBook::parse).andThen(ProfunctorBook::pence);
    int samePence = plainJava.apply("BULB,4,2.50");
    // 1000
    // ANCHOR_END: plain
    System.out.println(samePence);
  }
}
