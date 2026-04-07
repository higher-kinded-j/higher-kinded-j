// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.effect;

import java.util.function.Function;
import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.jspecify.annotations.NullMarked;

/**
 * Effect algebra for fraud detection operations.
 *
 * <p>Uses continuation-passing style: each operation carries a {@code Function} from its natural
 * result type to {@code A}, enabling proper type inference at call sites.
 *
 * @param <A> the result type (constrained by the continuation)
 */
@NullMarked
@EffectAlgebra
public sealed interface FraudCheckOp<A> permits FraudCheckOp.CheckTransaction {

  /** Maps a function over the result type. */
  <B> FraudCheckOp<B> mapK(Function<? super A, ? extends B> f);

  /**
   * Assess the fraud risk of a transaction.
   *
   * @param amount the transaction amount
   * @param customer the customer initiating the transaction
   * @param k continuation from {@link RiskScore} to {@code A}
   * @param <A> the result type
   */
  record CheckTransaction<A>(Money amount, Customer customer, Function<RiskScore, A> k)
      implements FraudCheckOp<A> {
    @Override
    public <B> FraudCheckOp<B> mapK(Function<? super A, ? extends B> f) {
      return new CheckTransaction<>(amount, customer, k.andThen(f));
    }
  }
}
