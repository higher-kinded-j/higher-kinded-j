// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Final outcome of a payment processing operation.
 *
 * <p>Represents one of three states: approved, declined, or failed.
 */
@NullMarked
public sealed interface PaymentResult
    permits PaymentResult.Approved, PaymentResult.Declined, PaymentResult.Failed {

  /**
   * Payment was approved and charged.
   *
   * @param chargeResult the successful charge details
   * @param ledgerEntry the accounting entry created
   * @param riskScore the fraud risk assessment
   */
  record Approved(ChargeResult chargeResult, LedgerEntry ledgerEntry, RiskScore riskScore)
      implements PaymentResult {
    public Approved {
      Objects.requireNonNull(chargeResult, "chargeResult cannot be null");
      Objects.requireNonNull(ledgerEntry, "ledgerEntry cannot be null");
      Objects.requireNonNull(riskScore, "riskScore cannot be null");
    }
  }

  /**
   * Payment was declined before charging.
   *
   * @param reason the decline reason
   */
  record Declined(String reason) implements PaymentResult {
    public Declined {
      Objects.requireNonNull(reason, "reason cannot be null");
    }
  }

  /**
   * Payment attempt failed during charging.
   *
   * @param chargeResult the failed charge details
   */
  record Failed(ChargeResult chargeResult) implements PaymentResult {
    public Failed {
      Objects.requireNonNull(chargeResult, "chargeResult cannot be null");
    }
  }

  /**
   * Convenience factory for a declined result.
   *
   * @param reason the decline reason
   * @return a Declined result
   */
  static PaymentResult declined(String reason) {
    return new Declined(reason);
  }

  /**
   * Convenience factory for a failed result.
   *
   * @param chargeResult the failed charge
   * @return a Failed result
   */
  static PaymentResult failed(ChargeResult chargeResult) {
    return new Failed(chargeResult);
  }

  /**
   * Whether this result represents a successful payment.
   *
   * @return true if the payment was approved
   */
  default boolean isApproved() {
    return this instanceof Approved;
  }

  /**
   * Whether this result represents a declined payment.
   *
   * @return true if the payment was declined
   */
  default boolean isDeclined() {
    return this instanceof Declined;
  }
}
