// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.monoid;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Update;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/semigroup_and_monoid.html">Semigroup
 * and Monoid</a> page: an order's line items combined, totalled and searched with the library's
 * semigroups and monoids, and a monoid of its own built with {@code Monoid.of}. The page {@code
 * {{#include}}}s the anchored regions, {@code MonoidBookTest} holds the claims the page makes, and
 * the book's output gate runs {@link #main} against the comments in the regions. The Foldable and
 * Traverse page reuses the line items and {@link #lineTotal}.
 */
public final class MonoidBook {

  private MonoidBook() {}

  // ANCHOR: lines
  /** An order's lines: one lamp at 40.00, and four bulbs at 2.50 each. */
  public static final List<LineItem> LINES =
      List.of(
          new LineItem("LAMP", 1, new BigDecimal("40.00")),
          new LineItem("BULB", 4, new BigDecimal("2.50")));

  /** A later order's lines: three more bulbs. */
  public static final List<LineItem> REFILL =
      List.of(new LineItem("BULB", 3, new BigDecimal("2.50")));

  /** What a line costs: its price times its quantity. */
  public static BigDecimal lineTotal(LineItem line) {
    return line.price().multiply(BigDecimal.valueOf(line.quantity()));
  }

  // ANCHOR_END: lines

  // ANCHOR: units
  // Units per SKU, which no Monoids factory gives: an empty map, and a merge adding the counts
  public static final Monoid<Map<String, Integer>> UNITS =
      Monoid.of(
          Map.of(),
          (left, right) -> {
            Map<String, Integer> sum = new TreeMap<>(left);
            right.forEach((sku, units) -> sum.merge(sku, units, Integer::sum));
            return Collections.unmodifiableMap(sum);
          });

  static Map<String, Integer> units(List<LineItem> lines) {
    return UNITS.combineAll(
        lines.stream().map(line -> Map.of(line.sku(), line.quantity())).toList());
  }

  // ANCHOR_END: units

  public static void main(String[] args) {
    // ANCHOR: semigroup
    Semigroup<List<String>> errors = Semigroups.list();
    List<String> both =
        errors.combine(List.of("SKU is blank"), List.of("quantity must be positive"));
    // [SKU is blank, quantity must be positive]

    Semigroup<String> joined = Semigroups.string("; ");
    String message = joined.combine("SKU is blank", "quantity must be positive");
    // the message reads "SKU is blank; quantity must be positive"
    // ANCHOR_END: semigroup
    System.out.println(both);

    // ANCHOR: total
    Monoid<BigDecimal> money = Monoids.bigDecimalAddition();
    BigDecimal total = money.combineAll(LINES.stream().map(MonoidBook::lineTotal).toList());
    // 50.00

    BigDecimal noLines = money.combineAll(List.of());
    // 0
    // ANCHOR_END: total
    System.out.println(total);
    System.out.println(noLines);

    // ANCHOR: units_run
    Map<String, Integer> stock = UNITS.combine(units(LINES), units(REFILL));
    // a map of BULB to 7 and LAMP to 1
    // ANCHOR_END: units_run

    // ANCHOR: defaults
    BigDecimal sixLamps = money.combineN(new BigDecimal("40.00"), 6);
    // 240.00

    boolean nothingOwed = money.isEmpty(new BigDecimal("0.00"));
    // true
    // ANCHOR_END: defaults
    System.out.println(sixLamps);
    System.out.println(nothingOwed);

    // ANCHOR: priciest
    Monoid<Optional<LineItem>> priciest = Monoids.maximum(Comparator.comparing(LineItem::price));
    Optional<LineItem> dearest = priciest.combineAll(LINES.stream().map(Optional::of).toList());
    // Optional[LineItem[sku=LAMP, quantity=1, price=40.00]]

    Optional<LineItem> none = priciest.combineAll(List.of());
    // an empty Optional, the monoid's empty value
    // ANCHOR_END: priciest
    System.out.println(dearest);

    // ANCHOR: update
    Update<LineItem> restock = line -> new LineItem(line.sku(), line.quantity() + 10, line.price());
    Update<LineItem> markDown =
        line ->
            new LineItem(
                line.sku(), line.quantity(), line.price().subtract(new BigDecimal("5.00")));

    Monoid<Update<LineItem>> steps = Monoids.update();
    Update<LineItem> restockThenMarkDown = steps.combineAll(List.of(restock, markDown));
    LineItem lamp = restockThenMarkDown.apply(LINES.getFirst());
    // LineItem[sku=LAMP, quantity=11, price=35.00]
    // ANCHOR_END: update
    System.out.println(lamp);
  }
}
