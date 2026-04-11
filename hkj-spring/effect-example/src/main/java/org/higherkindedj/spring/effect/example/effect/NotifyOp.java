// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.effect;

import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.jspecify.annotations.NullMarked;

/**
 * Effect algebra for notification operations.
 *
 * @param <A> the result type
 */
@NullMarked
@EffectAlgebra
public sealed interface NotifyOp<A> permits NotifyOp.SendConfirmation {

  /** Maps a function over the result type. */
  <B> NotifyOp<B> mapK(Function<? super A, ? extends B> f);

  /**
   * Send an order confirmation notification.
   *
   * @param customerId the customer to notify
   * @param orderId the order that was confirmed
   * @param k continuation from Unit to A
   * @param <A> the result type
   */
  record SendConfirmation<A>(String customerId, String orderId, Function<Unit, A> k)
      implements NotifyOp<A> {
    @Override
    public <B> NotifyOp<B> mapK(Function<? super A, ? extends B> f) {
      return new SendConfirmation<>(customerId, orderId, k.andThen(f));
    }
  }
}
