// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.error;

import java.time.Instant;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.market.model.value.Symbol;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;

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
 *
 * <p>Each variant declares only its domain-specific components plus one {@link ErrorEnvelope}
 * carrying the shared fields (code, message, timestamp) and the typed {@link MarketErrorContext}
 * (issue #610). Construct variants through the generated {@link MarketErrors} companion, whose
 * factories derive the code and message from the variant name and offer {@code TimeSource}
 * overloads for deterministic timestamps.
 */
@GenerateErrorEnvelope
public sealed interface MarketError {

  /** The shared envelope: code, message, timestamp and typed context. */
  ErrorEnvelope<MarketErrorContext> envelope();

  /** Error code for categorisation. */
  default String code() {
    return envelope().code();
  }

  /** Human-readable error message. */
  default String message() {
    return envelope().message();
  }

  /** When the error occurred. */
  default Instant timestamp() {
    return envelope().timestamp();
  }

  /** Additional typed context for logs and metrics. */
  default MarketErrorContext context() {
    return envelope().context();
  }

  /**
   * Rebuilds this error with its context transformed by {@code edit}, seeded from the current
   * context; code, message and timestamp are preserved.
   */
  default MarketError editContext(UnaryOperator<MarketErrors.ContextBuilder> edit) {
    return MarketErrors.editContext(this, edit);
  }

  /**
   * Exchange feed connection lost.
   *
   * @param exchangeName the disconnected exchange
   * @param envelope the shared error envelope
   */
  record FeedDisconnected(String exchangeName, ErrorEnvelope<MarketErrorContext> envelope)
      implements MarketError {}

  /**
   * Reference data or FX rate lookup failed.
   *
   * @param symbol the symbol whose tick could not be enriched
   * @param envelope the shared error envelope
   */
  record EnrichmentFailed(Symbol symbol, ErrorEnvelope<MarketErrorContext> envelope)
      implements MarketError {

    /**
     * Creates an enrichment failure carrying the lookup failure detail as its message; the symbol
     * is also recorded in the typed context.
     *
     * @param symbol the symbol whose tick could not be enriched
     * @param detail the lookup failure detail
     * @return an EnrichmentFailed
     */
    public static EnrichmentFailed of(Symbol symbol, String detail) {
      return new EnrichmentFailed(
          symbol,
          ErrorEnvelope.of(
              "ENRICHMENT_FAILED", detail, MarketErrors.context().symbol(symbol).build()));
    }
  }

  /**
   * Risk threshold exceeded.
   *
   * @param symbol the symbol that breached the limit
   * @param riskScore the offending risk score
   * @param envelope the shared error envelope
   */
  record RiskLimitBreached(
      Symbol symbol, double riskScore, ErrorEnvelope<MarketErrorContext> envelope)
      implements MarketError {}

  /**
   * Tick data is too old to be useful.
   *
   * @param symbol the symbol whose data went stale
   * @param envelope the shared error envelope
   */
  record StaleData(Symbol symbol, ErrorEnvelope<MarketErrorContext> envelope)
      implements MarketError {}
}
