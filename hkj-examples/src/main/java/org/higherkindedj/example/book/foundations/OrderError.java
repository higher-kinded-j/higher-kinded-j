// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.util.UUID;

// ANCHOR: order_error
/** Why the order service refused a request. */
public sealed interface OrderError {

  /** No order has this id. */
  record NotFound(UUID orderId) implements OrderError {}

  /** The order has shipped, so it can no longer change. */
  record AlreadyShipped(UUID orderId) implements OrderError {}

  /** Typed as {@code OrderError}, so a chain that starts with it can end in any other variant. */
  static OrderError notFound(UUID orderId) {
    return new NotFound(orderId);
  }

  static OrderError alreadyShipped(UUID orderId) {
    return new AlreadyShipped(orderId);
  }
}
// ANCHOR_END: order_error
