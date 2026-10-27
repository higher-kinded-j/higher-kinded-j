// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;

// ANCHOR: repository
/** Where orders are kept: a search may find nothing, and a save may be refused. */
public interface OrderRepository {

  MaybePath<Order> find(UUID id);

  /** Stores the order, refusing it once the order on record has shipped. */
  EitherPath<OrderError, Order> save(Order order);
}
// ANCHOR_END: repository
