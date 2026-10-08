// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.intro;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.optics.annotations.GenerateFocus;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/ch_intro.html">Optics</a> chapter
 * introduction. The page {@code {{#include}}}s the anchored regions, and {@code IntroBookTest}
 * holds the claims the page makes about this code.
 *
 * <p>The records here are the "before" half of a before-and-after pair, so they carry the withers
 * Lombok's {@code @With} would generate. Their components are the chapter cast's.
 */
public final class IntroBook {

  private IntroBook() {}

  static Order discountByWithers(Order order) {
    // ANCHOR: cascade
    // The withers Lombok's @With generates: each rebuilds only its own record, so the list is
    // rebuilt by hand
    Order discounted =
        order.withLines(
            order.lines().stream()
                .map(line -> line.withPrice(line.price().multiply(new BigDecimal("0.9"))))
                .toList());
    // ANCHOR_END: cascade
    return discounted;
  }

  static Order discountByFocus(Order order) {
    // ANCHOR: focus
    // The Focus DSL: one generated path reaches every price, and rebuilds what it passes through
    Order discounted =
        OrderFocus.lines()
            .via(LineItemFocus.price())
            .modifyAll(price -> price.multiply(new BigDecimal("0.9")), order);
    // ANCHOR_END: focus
    return discounted;
  }
}

// Each record carries the withers this example calls, written by hand in the shape Lombok's @With
// generates, since this module does not run Lombok. hkj-processor's LombokInteropTest compiles the
// real @With beside @GenerateFocus on the same records.

@GenerateFocus
record LineItem(String sku, Integer quantity, BigDecimal price) {
  LineItem withPrice(BigDecimal price) {
    return new LineItem(sku, quantity, price);
  }
}

@GenerateFocus
record Order(
    UUID id,
    Customer customer,
    List<LineItem> lines,
    Instant placedAt,
    Currency currency,
    OrderStatus status) {
  Order withLines(List<LineItem> lines) {
    return new Order(id, customer, lines, placedAt, currency, status);
  }
}
