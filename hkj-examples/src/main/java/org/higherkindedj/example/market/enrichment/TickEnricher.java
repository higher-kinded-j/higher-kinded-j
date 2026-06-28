// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.enrichment;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;
import org.higherkindedj.example.market.error.MarketError;
import org.higherkindedj.example.market.model.EnrichedTick;
import org.higherkindedj.example.market.model.Instrument;
import org.higherkindedj.example.market.model.PriceTick;
import org.higherkindedj.hkt.either.Either;
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
 *   <li>{@link VStream#peek} - Surfaces a typed {@link MarketError} for each tick that fails to
 *       enrich, before those failures are filtered out of the success stream
 * </ul>
 *
 * <p>A lookup miss (unknown instrument or currency) is turned into a typed {@link
 * MarketError.EnrichmentFailed} carrying the tick's symbol, rather than an uncaught exception
 * propagating through the pipeline. Failed ticks are reported to the configured error handler and
 * dropped from the enriched stream so a single bad symbol cannot abort the whole feed.
 */
public class TickEnricher {

  private final ReferenceDataService refData;
  private final FxRateService fxService;
  private final int concurrency;
  private final Consumer<MarketError> onEnrichmentError;

  /**
   * Creates a tick enricher that silently drops ticks that fail to enrich.
   *
   * @param refData the reference data service
   * @param fxService the FX rate service
   * @param concurrency max concurrent enrichments (typically 4-16 for I/O-bound lookups)
   */
  public TickEnricher(ReferenceDataService refData, FxRateService fxService, int concurrency) {
    this(refData, fxService, concurrency, _ -> {});
  }

  /**
   * Creates a tick enricher that reports enrichment failures to the given handler.
   *
   * @param refData the reference data service
   * @param fxService the FX rate service
   * @param concurrency max concurrent enrichments (typically 4-16 for I/O-bound lookups)
   * @param onEnrichmentError invoked with the typed {@link MarketError} for each tick that fails to
   *     enrich (e.g. logging or metrics)
   */
  public TickEnricher(
      ReferenceDataService refData,
      FxRateService fxService,
      int concurrency,
      Consumer<MarketError> onEnrichmentError) {
    this.refData = Objects.requireNonNull(refData);
    this.fxService = Objects.requireNonNull(fxService);
    if (concurrency <= 0) {
      throw new IllegalArgumentException("concurrency must be positive");
    }
    this.concurrency = concurrency;
    this.onEnrichmentError = Objects.requireNonNull(onEnrichmentError);
  }

  /**
   * Enriches a single tick by fetching instrument and FX data concurrently.
   *
   * <p>Uses {@link Par#map2} to execute both lookups in parallel on virtual threads. A failed
   * lookup is recovered into a typed {@link MarketError.EnrichmentFailed} for this tick's symbol
   * rather than propagating as an exception.
   *
   * @param tick the raw price tick
   * @return a VTask producing either the enriched tick or a typed enrichment error
   */
  public VTask<Either<MarketError, EnrichedTick>> enrichOne(PriceTick tick) {
    VTask<Instrument> instrumentTask = refData.lookup(tick.symbol());
    VTask<BigDecimal> fxTask = fxService.rateToUsd(tick.exchange().currency());

    return Par.map2(
            instrumentTask,
            fxTask,
            (instrument, fxRate) ->
                Either.<MarketError, EnrichedTick>right(new EnrichedTick(tick, instrument, fxRate)))
        .recover(
            error ->
                Either.left(new MarketError.EnrichmentFailed(tick.symbol(), error.getMessage())));
  }

  /**
   * Enriches a stream of ticks with bounded concurrency.
   *
   * <p>Uses {@link VStreamPar#parEvalMap} to process up to {@code concurrency} ticks at once. Ticks
   * that fail to enrich are reported to the error handler and dropped, so the returned stream
   * contains only successfully enriched ticks.
   *
   * @param ticks the raw tick stream
   * @return a stream of successfully enriched ticks
   */
  public VStream<EnrichedTick> enrich(VStream<PriceTick> ticks) {
    return VStreamPar.parEvalMap(ticks, concurrency, this::enrichOne)
        .peek(result -> result.ifLeft(onEnrichmentError))
        .flatMap(result -> result.fold(_ -> VStream.empty(), VStream::of));
  }
}
