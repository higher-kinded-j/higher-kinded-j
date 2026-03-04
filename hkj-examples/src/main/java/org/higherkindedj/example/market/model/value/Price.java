// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.model.value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A decimal price value with fixed scale.
 *
 * @param value the price as a BigDecimal
 */
public record Price(BigDecimal value) implements Comparable<Price> {
  public Price {
    Objects.requireNonNull(value, "price value must not be null");
  }

  public static Price of(String s) {
    return new Price(new BigDecimal(s));
  }

  public static Price of(double d) {
    return new Price(BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP));
  }

  public Price multiply(BigDecimal factor) {
    return new Price(value.multiply(factor).setScale(4, RoundingMode.HALF_UP));
  }

  public Price add(Price other) {
    return new Price(value.add(other.value));
  }

  public Price subtract(Price other) {
    return new Price(value.subtract(other.value));
  }

  public double toDouble() {
    return value.doubleValue();
  }

  @Override
  public int compareTo(Price other) {
    return value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
  }
}
