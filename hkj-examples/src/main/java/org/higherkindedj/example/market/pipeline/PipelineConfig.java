// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.pipeline;

/**
 * Configuration for the market data pipeline.
 *
 * @param enrichmentConcurrency max concurrent enrichment lookups
 * @param riskConcurrency max concurrent risk assessments
 * @param windowSize number of ticks per aggregation window
 * @param maxTicks max ticks to process (safety valve for demos)
 */
public record PipelineConfig(
    int enrichmentConcurrency, int riskConcurrency, int windowSize, int maxTicks) {

  public PipelineConfig {
    if (enrichmentConcurrency <= 0) {
      throw new IllegalArgumentException("enrichmentConcurrency must be positive");
    }
    if (riskConcurrency <= 0) {
      throw new IllegalArgumentException("riskConcurrency must be positive");
    }
    if (windowSize <= 0) {
      throw new IllegalArgumentException("windowSize must be positive");
    }
    if (maxTicks <= 0) {
      throw new IllegalArgumentException("maxTicks must be positive");
    }
  }

  /** Default configuration suitable for demos. */
  public static PipelineConfig defaults() {
    return new PipelineConfig(8, 4, 5, 50);
  }
}
