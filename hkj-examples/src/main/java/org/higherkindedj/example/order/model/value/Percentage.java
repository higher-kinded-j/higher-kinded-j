// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model.value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A percentage value, stored as a number from 0-100.
 *
 * @param value the percentage value (e.g., 15 for 15%)
 */
public record Percentage(BigDecimal value) {

  /** Zero percent. */
  public static final Percentage ZERO = new Percentage(BigDecimal.ZERO);

  public Percentage {
    Objects.requireNonNull(value, "Percentage value cannot be null");
    if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new IllegalArgumentException("Percentage must be between 0 and 100, got: " + value);
    }
  }

  /**
   * Creates a percentage from an integer value.
   *
   * @param value the percentage (0-100)
   * @return a Percentage
   */
  public static Percentage of(int value) {
    return new Percentage(BigDecimal.valueOf(value));
  }

  /**
   * Returns this percentage as a decimal fraction (e.g., 15% becomes 0.15).
   *
   * @return the fractional representation
   */
  public BigDecimal asFraction() {
    return value.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
  }

  @Override
  public String toString() {
    return value.stripTrailingZeros().toPlainString() + "%";
  }
}
