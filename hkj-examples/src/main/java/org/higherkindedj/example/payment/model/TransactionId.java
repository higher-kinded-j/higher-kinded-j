// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

/**
 * Type-safe identifier for payment transactions.
 *
 * @param value the underlying string value
 */
@NullMarked
public record TransactionId(String value) {

  public TransactionId {
    Objects.requireNonNull(value, "TransactionId value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("TransactionId cannot be blank");
    }
  }

  /**
   * Generates a new random transaction identifier.
   *
   * @return a new TransactionId
   */
  public static TransactionId generate() {
    return new TransactionId(UUID.randomUUID().toString());
  }

  @Override
  public String toString() {
    return value;
  }
}
