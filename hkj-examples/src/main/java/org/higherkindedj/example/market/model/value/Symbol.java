// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.model.value;

import java.util.Objects;

/**
 * A ticker symbol identifying a financial instrument.
 *
 * @param value the ticker string (e.g. "AAPL", "GBPUSD")
 */
public record Symbol(String value) {
  public Symbol {
    Objects.requireNonNull(value, "symbol value must not be null");
  }

  @Override
  public String toString() {
    return value;
  }
}
