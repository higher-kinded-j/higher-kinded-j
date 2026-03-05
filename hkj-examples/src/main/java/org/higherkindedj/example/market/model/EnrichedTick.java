// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.model;

import java.math.BigDecimal;
import java.util.Objects;
import org.higherkindedj.example.market.model.value.Price;

/**
 * A price tick enriched with reference data and currency conversion.
 *
 * @param tick the original price tick
 * @param instrument the instrument reference data
 * @param fxRate the conversion rate to USD (1.0 if already USD)
 */
public record EnrichedTick(PriceTick tick, Instrument instrument, BigDecimal fxRate) {
  public EnrichedTick {
    Objects.requireNonNull(tick, "tick must not be null");
    Objects.requireNonNull(instrument, "instrument must not be null");
    Objects.requireNonNull(fxRate, "fxRate must not be null");
  }

  /** The mid-price converted to USD. */
  public Price midInUsd() {
    return tick.mid().multiply(fxRate);
  }
}
