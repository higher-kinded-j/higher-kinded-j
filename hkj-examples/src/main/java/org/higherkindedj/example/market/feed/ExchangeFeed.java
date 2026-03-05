// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.feed;

import org.higherkindedj.example.market.model.PriceTick;
import org.higherkindedj.hkt.vstream.VStream;

/** A source of price ticks from a single exchange. */
public interface ExchangeFeed {

  /** Returns a lazy VStream of price ticks from this exchange. */
  VStream<PriceTick> ticks();
}
