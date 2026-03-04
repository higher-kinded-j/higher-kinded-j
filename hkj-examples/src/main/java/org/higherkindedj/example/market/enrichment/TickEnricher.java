// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.enrichment;

import java.math.BigDecimal;
import java.util.Objects;
import org.higherkindedj.example.market.model.EnrichedTick;
import org.higherkindedj.example.market.model.Instrument;
import org.higherkindedj.example.market.model.PriceTick;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Enriches raw price ticks with reference data and FX rates.
 *
 * <p><b>HKJ features demonstrated:</b>
 *
 * <ul>
 *   <li>{@link Par#map2} - Fetches instrument data and FX rate <em>concurrently</em> per tick
 *   <li>{@link VStreamPar#parEvalMap} - Bounded concurrent enrichment across the stream
 * </ul>
 *
 * <p>The enrichment pipeline for each tick:
 *
 * <pre>{@code
 * tick ──→ Par.map2(lookupInstrument, lookupFxRate)
 *      ──→ EnrichedTick(tick, instrument, fxRate)
 * }</pre>
 */
public class TickEnricher {

  private final ReferenceDataService refData;
  private final FxRateService fxService;
  private final int concurrency;

  /**
   * Creates a tick enricher.
   *
   * @param refData the reference data service
   * @param fxService the FX rate service
   * @param concurrency max concurrent enrichments (typically 4-16 for I/O-bound lookups)
   */
  public TickEnricher(ReferenceDataService refData, FxRateService fxService, int concurrency) {
    this.refData = Objects.requireNonNull(refData);
    this.fxService = Objects.requireNonNull(fxService);
    if (concurrency <= 0) {
      throw new IllegalArgumentException("concurrency must be positive");
    }
    this.concurrency = concurrency;
  }

  /**
   * Enriches a single tick by fetching instrument and FX data concurrently.
   *
   * <p>Uses {@link Par#map2} to execute both lookups in parallel on virtual threads.
   *
   * @param tick the raw price tick
   * @return a VTask producing the enriched tick
   */
  public VTask<EnrichedTick> enrichOne(PriceTick tick) {
    VTask<Instrument> instrumentTask = refData.lookup(tick.symbol());
    VTask<BigDecimal> fxTask = fxService.rateToUsd(tick.exchange().currency());

    return Par.map2(
        instrumentTask, fxTask, (instrument, fxRate) -> new EnrichedTick(tick, instrument, fxRate));
  }

  /**
   * Enriches a stream of ticks with bounded concurrency.
   *
   * <p>Uses {@link VStreamPar#parEvalMap} to process up to {@code concurrency} ticks at once.
   *
   * @param ticks the raw tick stream
   * @return a stream of enriched ticks
   */
  public VStream<EnrichedTick> enrich(VStream<PriceTick> ticks) {
    return VStreamPar.parEvalMap(ticks, concurrency, this::enrichOne);
  }
}
