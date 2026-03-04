// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.model;

import java.time.Instant;
import java.util.Objects;
import org.higherkindedj.example.market.model.value.Price;
import org.higherkindedj.example.market.model.value.Symbol;
import org.higherkindedj.example.market.model.value.Volume;

/**
 * A raw price tick received from an exchange feed.
 *
 * @param symbol the instrument symbol
 * @param bid the bid price
 * @param ask the ask price
 * @param volume the traded volume
 * @param exchange the source exchange
 * @param timestamp the tick timestamp
 */
public record PriceTick(
    Symbol symbol, Price bid, Price ask, Volume volume, Exchange exchange, Instant timestamp) {
  public PriceTick {
    Objects.requireNonNull(symbol, "symbol must not be null");
    Objects.requireNonNull(bid, "bid must not be null");
    Objects.requireNonNull(ask, "ask must not be null");
    Objects.requireNonNull(volume, "volume must not be null");
    Objects.requireNonNull(exchange, "exchange must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
  }

  /** The mid-price between bid and ask. */
  public Price mid() {
    return Price.of((bid.toDouble() + ask.toDouble()) / 2.0);
  }

  /** The spread between ask and bid. */
  public Price spread() {
    return ask.subtract(bid);
  }
}
