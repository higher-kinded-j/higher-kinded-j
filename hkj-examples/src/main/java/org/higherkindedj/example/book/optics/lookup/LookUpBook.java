// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.lookup;

import java.util.List;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/ch7_intro.html">Look It Up</a> page. The
 * page {@code {{#include}}}s the anchored region, and {@code LookUpBookTest} holds the claims the
 * page makes about this code.
 */
public final class LookUpBook {

  /** What the lens reads directly, and what its fold reads one conversion later. */
  record Reads(String name, List<String> all) {}

  private LookUpBook() {}

  static Reads read(Order order) {
    // ANCHOR: payoff
    Lens<Order, String> customerName = OrderLenses.customer().andThen(CustomerLenses.name());

    String name = customerName.get(order);
    // "Ada": get is declared on Lens

    // customerName.getAll(order);
    // will not compile: getAll is on Fold, not Lens

    Fold<Order, String> asFold = customerName.asFold();
    List<String> all = asFold.getAll(order);
    // ["Ada"]: the same access, one conversion later
    // ANCHOR_END: payoff
    return new Reads(name, all);
  }
}
