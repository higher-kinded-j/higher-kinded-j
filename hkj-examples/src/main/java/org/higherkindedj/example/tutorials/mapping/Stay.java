// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Tutorial 27 domain: a stay, which guards its own dates. A stay whose departure is not after its
 * arrival is refused by the constructor, whichever way it was built.
 */
public record Stay(LocalDate arrival, LocalDate departure) {
  public Stay {
    Objects.requireNonNull(arrival, "arrival");
    Objects.requireNonNull(departure, "departure");
    if (!departure.isAfter(arrival)) {
      throw new IllegalArgumentException("departure must be after arrival");
    }
  }
}
