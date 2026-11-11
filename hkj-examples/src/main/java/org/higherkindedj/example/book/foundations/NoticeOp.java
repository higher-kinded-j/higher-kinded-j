// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.jspecify.annotations.NullMarked;

// ANCHOR: notice_op
/**
 * The order service's notices to customers, held as data beside {@link OrderOp}, so that one
 * program can use both instruction sets. {@code @EffectAlgebra} generates the {@code Kind}
 * encoding, the {@code Functor}, the interpreter base class and the {@code Free} constructors.
 */
@NullMarked
@EffectAlgebra
public sealed interface NoticeOp<A> permits NoticeOp.Send {

  /** Maps a function over the result type. */
  <B> NoticeOp<B> mapK(Function<? super A, ? extends B> f);

  /**
   * Sends the customer who placed an order a notice about it.
   *
   * @param orderId the order the notice is about
   * @param text what the notice says
   * @param k continuation from {@link Unit} to {@code A}
   * @param <A> the result type
   */
  record Send<A>(UUID orderId, String text, Function<Unit, A> k) implements NoticeOp<A> {
    @Override
    public <B> NoticeOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Send<>(orderId, text, k.andThen(f));
    }
  }
}
// ANCHOR_END: notice_op
