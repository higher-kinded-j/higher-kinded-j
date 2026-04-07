// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.effect;

import java.util.function.Function;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.jspecify.annotations.NullMarked;

/**
 * Effect algebra for notification operations.
 *
 * <p>Uses continuation-passing style: each operation carries a {@code Function} from its natural
 * result type ({@link Unit}) to {@code A}, enabling proper type inference at call sites.
 *
 * @param <A> the result type (constrained by the continuation)
 */
@NullMarked
@EffectAlgebra
public sealed interface NotificationOp<A>
    permits NotificationOp.SendReceipt, NotificationOp.AlertFraudTeam {

  /** Maps a function over the result type. */
  <B> NotificationOp<B> mapK(Function<? super A, ? extends B> f);

  /**
   * Send a payment receipt to the customer.
   *
   * @param customer the recipient
   * @param chargeResult the charge details to include in the receipt
   * @param k continuation from {@link Unit} to {@code A}
   * @param <A> the result type
   */
  record SendReceipt<A>(Customer customer, ChargeResult chargeResult, Function<Unit, A> k)
      implements NotificationOp<A> {
    @Override
    public <B> NotificationOp<B> mapK(Function<? super A, ? extends B> f) {
      return new SendReceipt<>(customer, chargeResult, k.andThen(f));
    }
  }

  /**
   * Alert the fraud team about a high-risk transaction.
   *
   * @param customer the customer involved
   * @param riskScore the risk assessment
   * @param k continuation from {@link Unit} to {@code A}
   * @param <A> the result type
   */
  record AlertFraudTeam<A>(Customer customer, RiskScore riskScore, Function<Unit, A> k)
      implements NotificationOp<A> {
    @Override
    public <B> NotificationOp<B> mapK(Function<? super A, ? extends B> f) {
      return new AlertFraudTeam<>(customer, riskScore, k.andThen(f));
    }
  }
}
