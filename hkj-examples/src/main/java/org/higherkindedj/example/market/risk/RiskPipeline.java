// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.risk;

import java.util.Objects;
import org.higherkindedj.example.market.model.EnrichedTick;
import org.higherkindedj.example.market.model.RiskAssessment;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;

/**
 * Applies risk calculations across a stream of enriched ticks with bounded concurrency.
 *
 * <p><b>HKJ features demonstrated:</b>
 *
 * <ul>
 *   <li>{@link VStreamPar#parEvalMap} - Concurrent risk assessment with bounded concurrency that
 *       <em>preserves input order</em>. Risk results are independent, but the downstream {@code
 *       WindowAggregator} groups ticks into windows, so emission order must match arrival order for
 *       a window to contain temporally adjacent ticks. Using the unordered variant here would feed
 *       the windower a completion-order grab-bag and make per-window VWAP meaningless.
 * </ul>
 */
public class RiskPipeline {

  private final RiskCalculator calculator;
  private final int concurrency;

  /**
   * Creates a risk pipeline.
   *
   * @param calculator the risk calculator to apply
   * @param concurrency max concurrent assessments
   */
  public RiskPipeline(RiskCalculator calculator, int concurrency) {
    this.calculator = Objects.requireNonNull(calculator);
    if (concurrency <= 0) {
      throw new IllegalArgumentException("concurrency must be positive");
    }
    this.concurrency = concurrency;
  }

  /**
   * Applies risk assessment across a stream of enriched ticks.
   *
   * @param enrichedTicks the input stream
   * @return a stream of risk assessments in the same order as the input ticks
   */
  public VStream<RiskAssessment> assess(VStream<EnrichedTick> enrichedTicks) {
    return VStreamPar.parEvalMap(enrichedTicks, concurrency, calculator::assess);
  }
}
