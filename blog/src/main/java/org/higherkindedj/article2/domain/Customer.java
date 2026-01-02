// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A customer with orders.
 *
 * <p>The {@code @GenerateLenses} annotation generates {@code CustomerLenses} with static lens
 * methods for each field.
 */
@GenerateLenses
public record Customer(String id, String name, List<Order> orders) {

  /** Calculate total value of all orders. */
  public BigDecimal totalOrderValue() {
    return orders.stream().map(Order::total).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
