// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.FraudCheckOp;
import org.higherkindedj.example.payment.effect.FraudCheckOpInterpreter;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.jspecify.annotations.NullMarked;

/**
 * Production interpreter for fraud check operations.
 *
 * <p>Targets the {@code IO} monad. In a real system, this would call an ML fraud model. This
 * example returns a deterministic score based on the transaction amount.
 */
@NullMarked
public final class ProductionFraudInterpreter extends FraudCheckOpInterpreter<IOKind.Witness> {

  @Override
  protected <A> Kind<IOKind.Witness, A> handleCheckTransaction(
      FraudCheckOp.CheckTransaction<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return IOKindHelper.IO_OP.widen(
        IO.delay(
            () -> {
              int score = op.amount().amount().intValue() > 1000 ? 45 : 15;
              return op.k().apply(RiskScore.of(score));
            }));
  }
}
