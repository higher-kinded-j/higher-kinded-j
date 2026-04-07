// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Type-safe identifier for customers.
 *
 * @param value the underlying string value
 */
@NullMarked
public record CustomerId(String value) {

  public CustomerId {
    Objects.requireNonNull(value, "CustomerId value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("CustomerId cannot be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
