// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

/**
 * An {@link OrderRepository} held in memory, for the chapter's examples and their tests. It is not
 * thread-safe: the examples run on one thread.
 */
public final class InMemoryOrderRepository implements OrderRepository {

  private final Map<UUID, Order> orders = new HashMap<>();

  private InMemoryOrderRepository() {}

  /** A repository that starts out holding the given orders. */
  public static InMemoryOrderRepository of(Order... initial) {
    InMemoryOrderRepository repository = new InMemoryOrderRepository();
    for (Order order : initial) {
      repository.orders.put(order.id(), order);
    }
    return repository;
  }

  @Override
  public MaybePath<Order> find(UUID id) {
    return Path.maybe(orders.get(id));
  }

  @Override
  public EitherPath<OrderError, Order> save(Order order) {
    if (orders.get(order.id()) instanceof Order onRecord
        && onRecord.status() == OrderStatus.SHIPPED) {
      return Path.left(OrderError.alreadyShipped(order.id()));
    }
    orders.put(order.id(), order);
    return Path.right(order);
  }
}
