// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.article2.optics.Lens;

/**
 * A customer with orders.
 *
 * <p>In production with higher-kinded-j, you would annotate this with {@code @GenerateLenses}.
 */
public record Customer(String id, String name, List<Order> orders) {

  /** Lens accessors for Customer fields. */
  public static final class Lenses {
    private Lenses() {}

    public static Lens<Customer, String> id() {
      return Lens.of(
          Customer::id, (newId, cust) -> new Customer(newId, cust.name(), cust.orders()));
    }

    public static Lens<Customer, String> name() {
      return Lens.of(
          Customer::name, (newName, cust) -> new Customer(cust.id(), newName, cust.orders()));
    }

    public static Lens<Customer, List<Order>> orders() {
      return Lens.of(
          Customer::orders, (newOrders, cust) -> new Customer(cust.id(), cust.name(), newOrders));
    }
  }

  /** Calculate total value of all orders. */
  public BigDecimal totalOrderValue() {
    return orders.stream().map(Order::total).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
