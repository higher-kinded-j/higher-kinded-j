// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.article2.optics.Lens;

/**
 * An order with items and status.
 *
 * <p>In production with higher-kinded-j, you would annotate this with {@code @GenerateLenses}.
 */
public record Order(String orderId, List<LineItem> items, OrderStatus status) {

  /** Lens accessors for Order fields. */
  public static final class Lenses {
    private Lenses() {}

    public static Lens<Order, String> orderId() {
      return Lens.of(
          Order::orderId,
          (newOrderId, order) -> new Order(newOrderId, order.items(), order.status()));
    }

    public static Lens<Order, List<LineItem>> items() {
      return Lens.of(
          Order::items, (newItems, order) -> new Order(order.orderId(), newItems, order.status()));
    }

    public static Lens<Order, OrderStatus> status() {
      return Lens.of(
          Order::status,
          (newStatus, order) -> new Order(order.orderId(), order.items(), newStatus));
    }
  }

  /** Calculate the total value of this order. */
  public BigDecimal total() {
    return items.stream().map(LineItem::total).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
