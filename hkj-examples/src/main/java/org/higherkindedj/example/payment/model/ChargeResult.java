// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Result of a payment charge attempt.
 *
 * <p>A charge is either successful (with a transaction id) or failed (with an error message).
 *
 * @param transactionId the transaction identifier (null if failed)
 * @param amount the charged amount
 * @param success whether the charge succeeded
 * @param errorMessage error message if the charge failed (null if successful)
 */
@NullMarked
public record ChargeResult(
    @Nullable TransactionId transactionId,
    Money amount,
    boolean success,
    @Nullable String errorMessage) {

  public ChargeResult {
    Objects.requireNonNull(amount, "amount cannot be null");
  }

  /**
   * Creates a successful charge result.
   *
   * @param transactionId the transaction identifier
   * @param amount the charged amount
   * @return a successful ChargeResult
   */
  public static ChargeResult success(TransactionId transactionId, Money amount) {
    return new ChargeResult(transactionId, amount, true, null);
  }

  /**
   * Creates a failed charge result.
   *
   * @param amount the attempted amount
   * @param errorMessage the failure reason
   * @return a failed ChargeResult
   */
  public static ChargeResult failed(Money amount, String errorMessage) {
    return new ChargeResult(null, amount, false, errorMessage);
  }

  /**
   * Whether this charge failed.
   *
   * @return true if the charge was unsuccessful
   */
  public boolean isFailed() {
    return !success;
  }

  /**
   * Returns the transaction id, throwing if the charge failed.
   *
   * @return the transaction identifier
   * @throws IllegalStateException if the charge failed
   */
  public TransactionId id() {
    if (transactionId == null) {
      throw new IllegalStateException("No transaction id for failed charge");
    }
    return transactionId;
  }
}
