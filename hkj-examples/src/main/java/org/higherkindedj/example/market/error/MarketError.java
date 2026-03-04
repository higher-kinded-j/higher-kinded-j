// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.error;

import org.higherkindedj.example.market.model.value.Symbol;

/**
 * Sealed error hierarchy for market data pipeline failures.
 *
 * <p>Each variant represents a distinct failure mode:
 *
 * <ul>
 *   <li>{@link FeedDisconnected} - Exchange feed connection lost
 *   <li>{@link EnrichmentFailed} - Reference data or FX rate lookup failed
 *   <li>{@link RiskLimitBreached} - Risk threshold exceeded
 *   <li>{@link StaleData} - Tick data is too old to be useful
 * </ul>
 */
public sealed interface MarketError {

  String message();

  record FeedDisconnected(String exchangeName, String message) implements MarketError {}

  record EnrichmentFailed(Symbol symbol, String message) implements MarketError {}

  record RiskLimitBreached(Symbol symbol, double riskScore, String message)
      implements MarketError {}

  record StaleData(Symbol symbol, String message) implements MarketError {}
}
