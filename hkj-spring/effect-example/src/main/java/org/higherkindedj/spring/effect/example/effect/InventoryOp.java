// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.effect;

import java.util.function.Function;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.jspecify.annotations.NullMarked;

/**
 * Effect algebra for inventory operations.
 *
 * @param <A> the result type
 */
@NullMarked
@EffectAlgebra
public sealed interface InventoryOp<A> permits InventoryOp.CheckStock, InventoryOp.Reserve {

  /** Maps a function over the result type. */
  <B> InventoryOp<B> mapK(Function<? super A, ? extends B> f);

  /**
   * Check available stock for an item.
   *
   * @param itemId the item to check
   * @param k continuation from available quantity (Integer) to A
   * @param <A> the result type
   */
  record CheckStock<A>(String itemId, Function<Integer, A> k) implements InventoryOp<A> {
    @Override
    public <B> InventoryOp<B> mapK(Function<? super A, ? extends B> f) {
      return new CheckStock<>(itemId, k.andThen(f));
    }
  }

  /**
   * Reserve stock for an order.
   *
   * @param itemId the item to reserve
   * @param quantity how many to reserve
   * @param k continuation from success (Boolean) to A
   * @param <A> the result type
   */
  record Reserve<A>(String itemId, int quantity, Function<Boolean, A> k) implements InventoryOp<A> {
    @Override
    public <B> InventoryOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Reserve<>(itemId, quantity, k.andThen(f));
    }
  }
}
