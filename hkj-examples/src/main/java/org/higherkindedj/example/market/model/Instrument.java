// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.model;

import java.util.Objects;
import org.higherkindedj.example.market.model.value.Symbol;

/**
 * Reference data for a financial instrument.
 *
 * @param symbol the ticker symbol
 * @param name the full instrument name
 * @param type the instrument type (e.g. "Equity", "FX", "Bond")
 * @param lotSize the standard lot size for trading
 */
public record Instrument(Symbol symbol, String name, String type, int lotSize) {
  public Instrument {
    Objects.requireNonNull(symbol, "symbol must not be null");
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    if (lotSize <= 0) {
      throw new IllegalArgumentException("lotSize must be positive: " + lotSize);
    }
  }
}
