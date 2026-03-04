// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.model.value;

/**
 * A trade volume value.
 *
 * @param value the number of units traded
 */
public record Volume(long value) {
  public Volume {
    if (value < 0) {
      throw new IllegalArgumentException("volume must not be negative: " + value);
    }
  }

  public static Volume of(long v) {
    return new Volume(v);
  }

  public Volume add(Volume other) {
    return new Volume(value + other.value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
