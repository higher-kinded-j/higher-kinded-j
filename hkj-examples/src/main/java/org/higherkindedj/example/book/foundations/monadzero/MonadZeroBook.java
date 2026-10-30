// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.monadzero;

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/monad_zero.html">MonadZero</a> page:
 * the lines of an order filtered by one generic method, and the bulk lines of two baskets picked
 * out mid-chain by a {@code For} comprehension's {@code when}. The page {@code {{#include}}}s the
 * anchored regions, {@code MonadZeroBookTest} holds the claims the page makes, and the book's
 * output gate runs {@link #main} against the comments in the regions.
 */
public final class MonadZeroBook {

  private MonadZeroBook() {}

  // ANCHOR: baskets
  // Two baskets: a lamp and four bulbs, then three more bulbs
  static final LineItem LAMP = new LineItem("LAMP", 1, new BigDecimal("40.00"));

  static final List<List<LineItem>> BASKETS =
      List.of(
          List.of(LAMP, new LineItem("BULB", 4, new BigDecimal("2.50"))),
          List.of(new LineItem("BULB", 3, new BigDecimal("2.50"))));

  // ANCHOR_END: baskets

  // ANCHOR: generic
  // One method for any container with a zero: keeps a line only when it asks for more than one
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, LineItem> bulk(
      MonadZero<F> monadZero, Kind<F, LineItem> lines) {
    return monadZero.filter(line -> line.quantity() > 1, lines);
  }

  // ANCHOR_END: generic

  public static void main(String[] args) {
    // ANCHOR: filter
    MonadZero<ListKind.Witness> listZero = Instances.monadZero(list());
    MonadZero<MaybeKind.Witness> maybeZero = Instances.monadZero(maybe());

    List<LineItem> bulkLines = LIST.narrow(bulk(listZero, LIST.widen(BASKETS.getFirst())));
    // [LineItem[sku=BULB, quantity=4, price=2.50]]

    Maybe<LineItem> bulkLamp = MAYBE.narrow(bulk(maybeZero, MAYBE.just(LAMP)));
    // Nothing
    // ANCHOR_END: filter
    System.out.println(bulkLines);
    System.out.println(bulkLamp);

    // ANCHOR: when
    List<String> bulkSkus =
        LIST.narrow(
            For.from(listZero, LIST.widen(BASKETS))
                .from(basket -> LIST.widen(basket))
                .when(t -> t._2().quantity() > 1)
                .yield((basket, line) -> line.sku() + " x" + line.quantity()));
    // [BULB x4, BULB x3]
    // ANCHOR_END: when
    System.out.println(bulkSkus);
  }
}
