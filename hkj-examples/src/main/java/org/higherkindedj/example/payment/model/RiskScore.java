// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Fraud risk assessment score.
 *
 * <p>Scores range from 0 (no risk) to 100 (certain fraud).
 *
 * @param score the risk score (0-100)
 * @param reason human-readable reason for the risk assessment
 */
@NullMarked
public record RiskScore(int score, String reason) {

  public RiskScore {
    if (score < 0 || score > 100) {
      throw new IllegalArgumentException("Risk score must be between 0 and 100, got: " + score);
    }
    Objects.requireNonNull(reason, "reason cannot be null");
  }

  /**
   * Creates a risk score with a default reason.
   *
   * @param score the risk score
   * @return a new RiskScore
   */
  public static RiskScore of(int score) {
    return new RiskScore(score, score > 70 ? "High risk detected" : "Within acceptable range");
  }

  /**
   * Whether this score exceeds the given threshold.
   *
   * @param threshold the threshold to compare against
   * @return true if the score exceeds the threshold
   */
  public boolean exceeds(int threshold) {
    return score > threshold;
  }
}
