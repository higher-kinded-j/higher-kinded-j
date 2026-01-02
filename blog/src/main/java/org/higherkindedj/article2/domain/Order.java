// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * An order with items and status.
 *
 * <p>The {@code @GenerateLenses} annotation generates {@code OrderLenses} with static lens methods
 * for each field.
 */
@GenerateLenses
public record Order(String orderId, List<LineItem> items, OrderStatus status) {

  /** Calculate the total value of this order. */
  public BigDecimal total() {
    return items.stream().map(LineItem::total).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
