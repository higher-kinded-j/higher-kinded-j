// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model.value;

import java.util.Objects;

/**
 * Type-safe identifier for products.
 *
 * @param value the underlying string value
 */
public record ProductId(String value) {

  public ProductId {
    Objects.requireNonNull(value, "ProductId value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("ProductId cannot be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
