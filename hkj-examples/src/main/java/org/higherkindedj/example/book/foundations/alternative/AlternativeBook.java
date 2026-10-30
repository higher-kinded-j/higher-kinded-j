// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.alternative;

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/alternative.html">Alternative</a> page:
 * a price that falls back from today's offer to the catalogue, and the SKUs of two orders joined,
 * from one {@code orElse} that each instance reads its own way. The page {@code {{#include}}}s the
 * anchored regions, {@code AlternativeBookTest} holds the claims the page makes, and the book's
 * output gate runs {@link #main} against the comments in the regions.
 */
public final class AlternativeBook {

  private AlternativeBook() {}

  // ANCHOR: sources
  static final Alternative<MaybeKind.Witness> MAYBE_ALT = Instances.alternative(maybe());

  // Where a SKU's price can come from: today's offers first, then the catalogue
  static Kind<MaybeKind.Witness, BigDecimal> offerFor(String sku) {
    return sku.equals("BULB") ? MAYBE.just(new BigDecimal("2.00")) : MAYBE.nothing();
  }

  static Kind<MaybeKind.Witness, BigDecimal> catalogueFor(String sku) {
    return sku.equals("CHAIR") ? MAYBE.nothing() : MAYBE.just(new BigDecimal("40.00"));
  }

  // ANCHOR_END: sources

  // ANCHOR: all
  // The offer, then the catalogue, then a default: the first present price wins
  @SuppressWarnings(
      "unchecked") // varargs orElseAll: Java allows no @SafeVarargs on a default method
  static Kind<MaybeKind.Witness, BigDecimal> priceOrDefault(String sku) {
    return MAYBE_ALT.orElseAll(
        offerFor(sku), () -> catalogueFor(sku), () -> MAYBE.just(new BigDecimal("60.00")));
  }

  // ANCHOR_END: all

  public static void main(String[] args) {
    // ANCHOR: fallback
    Alternative<MaybeKind.Witness> maybeAlt = MAYBE_ALT;

    Maybe<BigDecimal> lampPrice =
        MAYBE.narrow(maybeAlt.orElse(offerFor("LAMP"), () -> catalogueFor("LAMP")));
    // Just(40.00)

    Maybe<BigDecimal> bulbPrice =
        MAYBE.narrow(maybeAlt.orElse(offerFor("BULB"), () -> catalogueFor("BULB")));
    // Just(2.00)

    Maybe<BigDecimal> chairPrice =
        MAYBE.narrow(maybeAlt.orElse(offerFor("CHAIR"), () -> catalogueFor("CHAIR")));
    // Nothing
    // ANCHOR_END: fallback
    System.out.println(lampPrice);
    System.out.println(bulbPrice);
    System.out.println(chairPrice);

    // ANCHOR: all_run
    Maybe<BigDecimal> chairOrDefault = MAYBE.narrow(priceOrDefault("CHAIR"));
    // Just(60.00)
    // ANCHOR_END: all_run
    System.out.println(chairOrDefault);

    // ANCHOR: join
    Alternative<ListKind.Witness> listAlt = Instances.alternative(list());

    List<String> skus =
        LIST.narrow(
            listAlt.orElse(LIST.widen(List.of("LAMP", "BULB")), () -> LIST.widen(List.of("DESK"))));
    // [LAMP, BULB, DESK]
    // ANCHOR_END: join
    System.out.println(skus);

    // ANCHOR: guard
    // guard turns a test into a value: of(Unit) when it holds, empty when it fails
    BigDecimal total = new BigDecimal("50.00");

    Maybe<Unit> overForty = MAYBE.narrow(maybeAlt.guard(total.compareTo(new BigDecimal("40")) > 0));
    // Just(())

    Maybe<Unit> overHundred =
        MAYBE.narrow(maybeAlt.guard(total.compareTo(new BigDecimal("100")) > 0));
    // Nothing
    // ANCHOR_END: guard
    System.out.println(overForty);
    System.out.println(overHundred);
  }
}
