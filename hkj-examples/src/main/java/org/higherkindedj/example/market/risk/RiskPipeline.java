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
 *   <li>{@link VStreamPar#parEvalMapUnordered} - Concurrent risk assessment where output order does
 *       not matter (risk results are independent of each other)
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
   * @return a stream of risk assessments (may arrive in any order)
   */
  public VStream<RiskAssessment> assess(VStream<EnrichedTick> enrichedTicks) {
    return VStreamPar.parEvalMapUnordered(enrichedTicks, concurrency, calculator::assess);
  }
}
