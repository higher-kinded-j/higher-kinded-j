// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.filtered;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/filtered_optics.html">Filtered Optics</a>
 * page, where it composes a filtered traversal over an order's lines and shows what {@code modify}
 * keeps and what {@code getAll} leaves out. The page {@code {{#include}}}s the anchored regions,
 * and {@code FilteredOpticsBookTest} holds the claims the page makes about this code.
 */
public final class FilteredOpticsBook {

  private FilteredOpticsBook() {}

  /** What the page's Step 2 returns: the bulk prices, and the lines with only those changed. */
  record Composed(List<BigDecimal> prices, List<LineItem> result) {}

  /** What the page's semantics example returns: the modified lines, and the matching lines. */
  record Semantics(List<LineItem> modified, List<LineItem> gotten) {}

  static Composed composeWithALens() {
    // ANCHOR: compose
    // Compose: lines → filtered bulk lines → line price
    Traversal<List<LineItem>, BigDecimal> bulkPrices =
        Traversals.<LineItem>forList()
            .filtered(line -> line.quantity() >= 4)
            .andThen(LineItemLenses.price());

    List<LineItem> lines =
        List.of(
            new LineItem("BULB", 4, new BigDecimal("2.50")),
            new LineItem("LAMP", 1, new BigDecimal("40.00")),
            new LineItem("FUSE", 10, new BigDecimal("1.20")));

    // Get only the bulk lines' prices
    List<BigDecimal> prices = Traversals.getAll(bulkPrices, lines);
    // [2.50, 1.20]

    // Take £0.20 off only the bulk lines' prices
    List<LineItem> result =
        Traversals.modify(bulkPrices, price -> price.subtract(new BigDecimal("0.20")), lines);
    // [LineItem[sku=BULB, quantity=4, price=2.30],
    //  LineItem[sku=LAMP, quantity=1, price=40.00],
    //  LineItem[sku=FUSE, quantity=10, price=1.00]]
    // the lamp is unchanged because it is a single item
    // ANCHOR_END: compose
    return new Composed(prices, result);
  }

  static Semantics preservedAndExcluded() {
    // ANCHOR: ten_percent_off
    // Ten per cent off a line's price, to the penny
    UnaryOperator<LineItem> tenPercentOff =
        line -> {
          BigDecimal discounted =
              line.price().multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_EVEN);
          return new LineItem(line.sku(), line.quantity(), discounted);
        };
    // ANCHOR_END: ten_percent_off

    // ANCHOR: semantics
    List<LineItem> lines =
        List.of(
            new LineItem("BULB", 4, new BigDecimal("2.50")),
            new LineItem("LAMP", 1, new BigDecimal("40.00")),
            new LineItem("FUSE", 10, new BigDecimal("1.20")));

    Traversal<List<LineItem>, LineItem> bulkLines =
        Traversals.<LineItem>forList().filtered(line -> line.quantity() >= 4);

    // MODIFY: the structure is preserved, and only the matching lines change
    List<LineItem> modified = Traversals.modify(bulkLines, tenPercentOff, lines);
    // [LineItem[sku=BULB, quantity=4, price=2.25],
    //  LineItem[sku=LAMP, quantity=1, price=40.00],
    //  LineItem[sku=FUSE, quantity=10, price=1.08]]
    // the bulbs and fuses are ten per cent off; the lamp, a single item, keeps its place unchanged

    // QUERY: only the matching lines are returned
    List<LineItem> gotten = Traversals.getAll(bulkLines, lines);
    // [LineItem[sku=BULB, quantity=4, price=2.50],
    //  LineItem[sku=FUSE, quantity=10, price=1.20]]
    // the lamp is left out entirely
    // ANCHOR_END: semantics
    return new Semantics(modified, gotten);
  }
}
