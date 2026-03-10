// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface marker for the {@link WriterT WriterT&lt;F, W, A&gt;} monad transformer.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link WriterT} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs). A {@code WriterT<F, W, A>}
 * wraps a monadic value {@code Kind<F, Pair<A, W>>}.
 *
 * <p>For HKT purposes, {@code WriterT<F, W, ?>} (a {@code WriterT} with a fixed outer monad witness
 * {@code F} and a fixed output type {@code W}) is treated as a type constructor that takes one type
 * argument {@code A}.
 *
 * @param <F> The witness type of the outer monad.
 * @param <W> The type of the accumulated output.
 * @param <A> The type of the computed value.
 * @see WriterT
 * @see WriterTKindHelper
 * @see Kind
 */
public interface WriterTKind<F extends WitnessArity<TypeArity.Unary>, W, A>
    extends Kind<WriterTKind.Witness<F, W>, A> {

  /**
   * The phantom type marker (witness type) for the {@code WriterT<F, W, ?>} type constructor.
   *
   * @param <OUTER_F> The witness type of the outer monad.
   * @param <TYPE_W> The type of the accumulated output.
   */
  final class Witness<OUTER_F, TYPE_W> implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
