// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.LineItem;

// ANCHOR: basket
/** The lines a customer has chosen so far. Adding a line gives a new basket. */
public record Basket(List<LineItem> lines) {

  public static final Basket EMPTY = new Basket(List.of());

  public Basket {
    lines = List.copyOf(lines);
  }

  public Basket add(LineItem line) {
    return new Basket(Stream.concat(lines.stream(), Stream.of(line)).toList());
  }

  /** Each line's price times its quantity, summed. */
  public BigDecimal total() {
    return lines.stream()
        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
// ANCHOR_END: basket
