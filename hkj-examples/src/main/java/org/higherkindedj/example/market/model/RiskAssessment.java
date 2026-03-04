// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.model;

import java.util.List;
import java.util.Objects;

/**
 * The result of a risk calculation for an enriched tick.
 *
 * @param tick the enriched tick that was assessed
 * @param riskScore a normalised risk score (0.0 = low, 1.0 = high)
 * @param flags any risk flags raised during assessment
 */
public record RiskAssessment(EnrichedTick tick, double riskScore, List<String> flags) {
  public RiskAssessment {
    Objects.requireNonNull(tick, "tick must not be null");
    Objects.requireNonNull(flags, "flags must not be null");
    if (riskScore < 0.0 || riskScore > 1.0) {
      throw new IllegalArgumentException("riskScore must be in [0.0, 1.0]: " + riskScore);
    }
  }

  public boolean isHighRisk() {
    return riskScore > 0.7;
  }
}
