// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface marker for the {@link ReaderT ReaderT&lt;F, R, A&gt;} monad transformer.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link ReaderT} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs). A {@code ReaderT<F, R, A>}
 * wraps a function {@code R -> Kind<F, A>}.
 *
 * <p>For HKT purposes, {@code ReaderT<F, R, ?>} (a {@code ReaderT} with a fixed outer monad witness
 * {@code F} and a fixed environment type {@code R}) is treated as a type constructor {@code G} that
 * takes one type argument {@code A} (the result type of the computation within F).
 *
 * <p>Specifically, when using {@code ReaderTKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" ({@code G} in {@code Kind<G, A>}) becomes {@code
 *       ReaderTKind.Witness<F, R>}. This represents the {@code ReaderT} type constructor, partially
 *       applied with the outer monad witness {@code F} and the environment type {@code R}.
 *   <li>The "value type" ({@code A} in {@code Kind<G, A>}) corresponds to {@code A}, the result
 *       type of the computation within the outer monad {@code F}.
 * </ul>
 *
 * <p>Instances of {@code Kind<ReaderTKind.Witness<F, R>, A>} can be converted to/from concrete
 * {@code ReaderT<F, R, A>} instances using {@link ReaderTKindHelper}.
 *
 * @param <F> The witness type of the outer monad.
 * @param <R_ENV> The type of the read-only environment. This parameter is captured by the {@link
 *     Witness} type for HKT representation.
 * @param <A> The type of the value produced within the outer monad F. This is the type parameter
 *     that varies for the higher-kinded type {@code ReaderTKind.Witness<F, R_ENV>}.
 * @see ReaderT
 * @see ReaderTKind.Witness
 * @see ReaderTKindHelper
 * @see Kind
 */
public interface ReaderTKind<F extends WitnessArity<TypeArity.Unary>, R_ENV, A>
    extends Kind<ReaderTKind.Witness<F, R_ENV>, A> {

  /**
   * The phantom type marker (witness type) for the {@code ReaderT<F, R, ?>} type constructor. This
   * class is parameterised by {@code OUTER_F} (the witness of the outer monad) and {@code ENV_R}
   * (the environment type). It is used as the first type argument to {@link Kind} (i.e., {@code G}
   * in {@code Kind<G, A>}) for {@code ReaderT} instances with a fixed outer monad and environment
   * type.
   *
   * @param <OUTER_F> The witness type of the outer monad.
   * @param <ENV_R> The type of the environment {@code R} associated with this witness.
   */
  final class Witness<OUTER_F, ENV_R> implements WitnessArity<TypeArity.Unary> {
    private Witness() { // Private constructor to prevent instantiation.
    }
  }
}
