// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind2 interface marker for {@link Writer Writer&lt;W, A&gt;} in Higher-Kinded-J.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link Writer} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs) with two type parameters.
 *
 * <p>This representation treats {@link Writer} as a type constructor with two type parameters, both
 * of which can vary. This enables bifunctor operations where both the log (written output) and
 * value types can be transformed independently.
 *
 * <p>This is distinct from {@link WriterKind}, which fixes the log type parameter for use with
 * {@link org.higherkindedj.hkt.Functor} and {@link org.higherkindedj.hkt.Monad} instances.
 *
 * @param <W> The type of the log (written output)
 * @param <A> The type of the computed value
 * @see Writer
 * @see WriterKind
 * @see org.higherkindedj.hkt.Bifunctor
 */
public interface WriterKind2<W, A> extends Kind2<WriterKind2.Witness, W, A> {

  /**
   * The phantom type marker (witness type) for the {@code Writer<?, ?>} type constructor. This
   * non-instantiable class acts as a tag to represent the {@code Writer} type constructor for
   * bifunctor operations.
   */
  final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() {} // Private constructor to prevent instantiation.
  }
}
