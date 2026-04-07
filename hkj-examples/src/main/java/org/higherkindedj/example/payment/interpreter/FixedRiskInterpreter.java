// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.FraudCheckOp;
import org.higherkindedj.example.payment.effect.FraudCheckOpInterpreter;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.jspecify.annotations.NullMarked;

/**
 * Test interpreter that returns a fixed risk score for all fraud checks.
 *
 * <p>Targets the {@code Id} monad for pure, synchronous testing. The fixed score allows tests to
 * exercise specific risk thresholds deterministically.
 */
@NullMarked
public final class FixedRiskInterpreter extends FraudCheckOpInterpreter<IdKind.Witness> {

  private final RiskScore fixedScore;

  /**
   * Creates an interpreter that always returns the given risk score.
   *
   * @param fixedScore the risk score to return for all checks
   */
  public FixedRiskInterpreter(RiskScore fixedScore) {
    this.fixedScore = Objects.requireNonNull(fixedScore, "fixedScore cannot be null");
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleCheckTransaction(
      FraudCheckOp.CheckTransaction<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return new Id<>(op.k().apply(fixedScore));
  }
}
