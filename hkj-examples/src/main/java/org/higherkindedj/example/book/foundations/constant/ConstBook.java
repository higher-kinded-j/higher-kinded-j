// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.constant;

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.constant.ConstBifunctor;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.constant.ConstKind2;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/const_type.html">Const</a> page: a value
 * that every map leaves alone, and one traversal of an order's lines, written for any {@code
 * Applicative}, that rebuilds the lines in {@code Id} and sums them in {@code Const}. The page
 * {@code {{#include}}}s the anchored regions, {@code ConstBookTest} holds the claims the page
 * makes, and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class ConstBook {

  static final BigDecimal VAT = new BigDecimal("1.20");

  static final List<LineItem> BASKET =
      List.of(
          new LineItem("LAMP", 1, new BigDecimal("40.00")),
          new LineItem("BULB", 4, new BigDecimal("2.50")));

  private ConstBook() {}

  // ANCHOR: each_line
  // Written once, for any Applicative: visits every line, and rebuilds the list in F
  static <F extends WitnessArity<TypeArity.Unary>>
      Kind<F, Kind<ListKind.Witness, LineItem>> eachLine(
          Applicative<F> applicative,
          Function<LineItem, Kind<F, LineItem>> visit,
          List<LineItem> lines) {
    return ListTraverse.INSTANCE.traverse(applicative, visit, LIST.widen(lines));
  }

  // ANCHOR_END: each_line

  static LineItem withVat(LineItem line) {
    return new LineItem(
        line.sku(),
        line.quantity(),
        line.price().multiply(VAT).setScale(2, RoundingMode.HALF_EVEN));
  }

  static BigDecimal lineTotal(LineItem line) {
    return line.price().multiply(BigDecimal.valueOf(line.quantity()));
  }

  public static void main(String[] args) {
    // ANCHOR: held
    // A Const<BigDecimal, LineItem> holds a BigDecimal, and no LineItem at all
    Const<BigDecimal, LineItem> total = new Const<>(new BigDecimal("50.00"));

    // Mapping the LineItem changes only the type: there is no LineItem to map
    Const<BigDecimal, String> relabelled = total.mapSecond(LineItem::sku);

    BigDecimal unchanged = relabelled.value();
    // 50.00
    // ANCHOR_END: held
    System.out.println(unchanged);

    // ANCHOR: rebuild
    // Run in Id, the traversal rebuilds the list: every line gets its price with VAT
    Kind<ListKind.Witness, LineItem> repriced =
        ID.narrow(
                eachLine(
                    Instances.applicative(id()), line -> ID.widen(Id.of(withVat(line))), BASKET))
            .value();

    List<BigDecimal> prices = LIST.narrow(repriced).stream().map(LineItem::price).toList();
    // [48.00, 3.00]
    // ANCHOR_END: rebuild
    System.out.println(prices);

    // ANCHOR: fold
    // Run in Const, the same traversal builds nothing: the monoid adds the totals before VAT
    ConstApplicative<BigDecimal> summing = new ConstApplicative<>(Monoids.bigDecimalAddition());

    Kind<ConstKind.Witness<BigDecimal>, Kind<ListKind.Witness, LineItem>> visited =
        eachLine(summing, line -> CONST.widen(new Const<>(lineTotal(line))), BASKET);

    BigDecimal basketTotal = CONST.narrow(visited).value();
    // 50.00
    // ANCHOR_END: fold
    System.out.println(basketTotal);

    // ANCHOR: kind
    ConstBifunctor bifunctor = ConstBifunctor.INSTANCE;

    // first changes the value; second changes only the type
    Kind2<ConstKind2.Witness, BigDecimal, LineItem> held =
        CONST.widen2(new Const<>(new BigDecimal("50.00")));
    Kind2<ConstKind2.Witness, BigDecimal, String> rounded =
        bifunctor.second(
            LineItem::sku, bifunctor.first(sum -> sum.setScale(0, RoundingMode.HALF_EVEN), held));

    BigDecimal whole = CONST.narrow2(rounded).value();
    // 50
    // ANCHOR_END: kind
    System.out.println(whole);
  }
}
