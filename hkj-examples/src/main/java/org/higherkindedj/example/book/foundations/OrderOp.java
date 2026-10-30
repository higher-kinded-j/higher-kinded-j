// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.jspecify.annotations.NullMarked;

// ANCHOR: order_op
/**
 * The order service's instructions, held as data so that a program built from them can be inspected
 * before an interpreter runs it. {@code @EffectAlgebra} generates the {@code Kind} encoding, the
 * {@code Functor}, the interpreter base class and the {@code Free} constructors.
 */
@NullMarked
@EffectAlgebra
public sealed interface OrderOp<A> permits OrderOp.PriceOf, OrderOp.Cancel {

  /** Maps a function over the result type. */
  <B> OrderOp<B> mapK(Function<? super A, ? extends B> f);

  /**
   * Looks up the price of a SKU.
   *
   * @param sku the SKU to price
   * @param k continuation from the price to {@code A}
   * @param <A> the result type
   */
  record PriceOf<A>(String sku, Function<BigDecimal, A> k) implements OrderOp<A> {
    @Override
    public <B> OrderOp<B> mapK(Function<? super A, ? extends B> f) {
      return new PriceOf<>(sku, k.andThen(f));
    }
  }

  /**
   * Cancels an order.
   *
   * @param orderId the order to cancel
   * @param k continuation from {@link Unit} to {@code A}
   * @param <A> the result type
   */
  record Cancel<A>(UUID orderId, Function<Unit, A> k) implements OrderOp<A> {
    @Override
    public <B> OrderOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Cancel<>(orderId, k.andThen(f));
    }
  }
}
// ANCHOR_END: order_op
