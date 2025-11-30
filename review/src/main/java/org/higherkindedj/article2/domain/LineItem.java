// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.math.BigDecimal;
import org.higherkindedj.article2.optics.Lens;

/**
 * A line item in an order.
 *
 * <p>In production with higher-kinded-j, you would annotate this with {@code @GenerateLenses}.
 */
public record LineItem(String productId, int quantity, BigDecimal price) {

  /** Lens accessors for LineItem fields. */
  public static final class Lenses {
    private Lenses() {}

    public static Lens<LineItem, String> productId() {
      return Lens.of(
          LineItem::productId,
          (newProductId, item) -> new LineItem(newProductId, item.quantity(), item.price()));
    }

    public static Lens<LineItem, Integer> quantity() {
      return Lens.of(
          LineItem::quantity,
          (newQty, item) -> new LineItem(item.productId(), newQty, item.price()));
    }

    public static Lens<LineItem, BigDecimal> price() {
      return Lens.of(
          LineItem::price,
          (newPrice, item) -> new LineItem(item.productId(), item.quantity(), newPrice));
    }
  }

  /** Calculate the total for this line item. */
  public BigDecimal total() {
    return price.multiply(BigDecimal.valueOf(quantity));
  }
}
