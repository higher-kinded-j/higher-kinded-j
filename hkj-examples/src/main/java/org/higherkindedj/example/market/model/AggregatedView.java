// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.model;

import java.util.Objects;
import org.higherkindedj.example.market.model.value.Price;
import org.higherkindedj.example.market.model.value.Symbol;
import org.higherkindedj.example.market.model.value.Volume;

/**
 * An aggregated market view computed over a window of ticks.
 *
 * @param symbol the instrument symbol
 * @param vwap the volume-weighted average price
 * @param bestBid the highest bid in the window
 * @param bestAsk the lowest ask in the window
 * @param totalVolume total volume across all ticks in the window
 * @param tickCount the number of ticks in the window
 * @param maxRiskScore the highest risk score in the window
 */
public record AggregatedView(
    Symbol symbol,
    Price vwap,
    Price bestBid,
    Price bestAsk,
    Volume totalVolume,
    int tickCount,
    double maxRiskScore) {
  public AggregatedView {
    Objects.requireNonNull(symbol, "symbol must not be null");
    Objects.requireNonNull(vwap, "vwap must not be null");
    Objects.requireNonNull(bestBid, "bestBid must not be null");
    Objects.requireNonNull(bestAsk, "bestAsk must not be null");
    Objects.requireNonNull(totalVolume, "totalVolume must not be null");
  }

  /** The effective spread between best ask and best bid. */
  public Price spread() {
    return bestAsk.subtract(bestBid);
  }
}
