// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.util.List;
import java.util.Objects;
import org.higherkindedj.example.book.optics.cast.Order;

// ANCHOR: order_page
/** One page of an order feed: its number, and the orders on it. A page past the last is empty. */
public record OrderPage(int number, List<Order> orders) {

  public OrderPage {
    orders = List.copyOf(Objects.requireNonNull(orders, "orders"));
  }
}
// ANCHOR_END: order_page
