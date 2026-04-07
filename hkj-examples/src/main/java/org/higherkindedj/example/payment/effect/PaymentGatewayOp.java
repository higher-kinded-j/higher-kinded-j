// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.effect;

import java.util.function.Function;
import org.higherkindedj.example.payment.model.AuthorisationToken;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.model.TransactionId;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.jspecify.annotations.NullMarked;

/**
 * Effect algebra for payment gateway operations.
 *
 * <p>Uses continuation-passing style (CPS): each operation carries a {@code Function} that maps the
 * operation's natural result type to the generic {@code A}. This gives the type system enough
 * information to infer {@code A} at call sites, eliminating phantom-type casts.
 *
 * <p>The {@link #mapK(Function)} method enables the generated Functor to delegate to it instead of
 * using unsafe cast-through.
 *
 * @param <A> the result type (constrained by the continuation)
 */
@NullMarked
@EffectAlgebra
public sealed interface PaymentGatewayOp<A>
    permits PaymentGatewayOp.Authorise, PaymentGatewayOp.Charge, PaymentGatewayOp.Refund {

  /**
   * Maps a function over the result type.
   *
   * @param f the mapping function
   * @param <B> the new result type
   * @return a new operation with the mapped continuation
   */
  <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f);

  /**
   * Authorise a payment amount against a payment method.
   *
   * @param amount the amount to authorise
   * @param method the payment method to authorise against
   * @param k continuation from {@link AuthorisationToken} to {@code A}
   * @param <A> the result type
   */
  record Authorise<A>(Money amount, PaymentMethod method, Function<AuthorisationToken, A> k)
      implements PaymentGatewayOp<A> {
    @Override
    public <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Authorise<>(amount, method, k.andThen(f));
    }
  }

  /**
   * Charge a previously authorised amount.
   *
   * @param amount the amount to charge
   * @param method the payment method to charge
   * @param k continuation from {@link ChargeResult} to {@code A}
   * @param <A> the result type
   */
  record Charge<A>(Money amount, PaymentMethod method, Function<ChargeResult, A> k)
      implements PaymentGatewayOp<A> {
    @Override
    public <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Charge<>(amount, method, k.andThen(f));
    }
  }

  /**
   * Refund a previous transaction.
   *
   * @param transactionId the transaction to refund
   * @param amount the amount to refund
   * @param k continuation from {@link ChargeResult} to {@code A}
   * @param <A> the result type
   */
  record Refund<A>(TransactionId transactionId, Money amount, Function<ChargeResult, A> k)
      implements PaymentGatewayOp<A> {
    @Override
    public <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Refund<>(transactionId, amount, k.andThen(f));
    }
  }
}
