// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.effect;

import java.util.function.Function;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.spring.effect.example.domain.OrderResult;
import org.higherkindedj.spring.effect.example.domain.OrderStatus;
import org.jspecify.annotations.NullMarked;

/**
 * Effect algebra for order operations.
 *
 * <p>Uses continuation-passing style (CPS): each operation carries a function from its natural
 * result type to A.
 *
 * @param <A> the result type
 */
@NullMarked
@EffectAlgebra
public sealed interface OrderOp<A> permits OrderOp.PlaceOrder, OrderOp.GetStatus {

  /** Maps a function over the result type. */
  <B> OrderOp<B> mapK(Function<? super A, ? extends B> f);

  /**
   * Place an order for a customer.
   *
   * @param customerId the customer
   * @param itemId the item to order
   * @param quantity number of units
   * @param k continuation from OrderResult to A
   * @param <A> the result type
   */
  record PlaceOrder<A>(String customerId, String itemId, int quantity, Function<OrderResult, A> k)
      implements OrderOp<A> {
    @Override
    public <B> OrderOp<B> mapK(Function<? super A, ? extends B> f) {
      return new PlaceOrder<>(customerId, itemId, quantity, k.andThen(f));
    }
  }

  /**
   * Get the status of an existing order.
   *
   * @param orderId the order ID
   * @param k continuation from OrderStatus to A
   * @param <A> the result type
   */
  record GetStatus<A>(String orderId, Function<OrderStatus, A> k) implements OrderOp<A> {
    @Override
    public <B> OrderOp<B> mapK(Function<? super A, ? extends B> f) {
      return new GetStatus<>(orderId, k.andThen(f));
    }
  }
}
