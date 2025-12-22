// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model.value;

import java.util.Objects;
import java.util.UUID;

/**
 * Type-safe identifier for orders.
 *
 * <p>Value objects like this prevent mixing up identifiers of different types at compile time,
 * catching errors early.
 *
 * @param value the underlying string value
 */
public record OrderId(String value) {

  public OrderId {
    Objects.requireNonNull(value, "OrderId value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("OrderId cannot be blank");
    }
  }

  /**
   * Generates a new unique order identifier.
   *
   * @return a new OrderId with a unique value
   */
  public static OrderId generate() {
    return new OrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
  }

  @Override
  public String toString() {
    return value;
  }
}
