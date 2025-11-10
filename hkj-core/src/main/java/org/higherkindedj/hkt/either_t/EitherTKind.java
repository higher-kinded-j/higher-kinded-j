// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;

/**
 * Kind interface marker for the {@link EitherT EitherT&lt;F, L, R&gt;} monad transformer.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link EitherT} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs). An {@code EitherT<F, L, R>}
 * wraps a monadic value {@code Kind<F, Either<L, R>>}.
 *
 * <p>For HKT purposes, {@code EitherT<F, L, ?>} (an {@code EitherT} with a fixed outer monad
 * witness {@code F} and a fixed "Left" type {@code L}) is treated as a type constructor {@code G}
 * that takes one type argument {@code R} (the "Right" type of the inner {@link Either}).
 *
 * <p>Specifically, when using {@code EitherTKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" ({@code G} in {@code Kind<G, A>}) becomes {@code
 *       EitherTKind.Witness<F, L>}. This represents the {@code EitherT} type constructor, partially
 *       applied with the outer monad witness {@code F} and the "Left" type {@code L}.
 *   <li>The "value type" ({@code A} in {@code Kind<G, A>}) corresponds to {@code R}, the "Right"
 *       type of the inner {@link Either}.
 * </ul>
 *
 * <p>Instances of {@code Kind<EitherTKind.Witness<F, L>, R>} can be converted to/from concrete
 * {@code EitherT<F, L, R>} instances using {@link EitherTKindHelper}.
 *
 * @param <F> The witness type of the outer monad.
 * @param <L> The type of the "Left" value in the inner {@link Either}. This parameter is captured
 *     by the {@link Witness} type for HKT representation.
 * @param <R> The type of the "Right" value in the inner {@link Either}. This is the type parameter
 *     that varies for the higher-kinded type {@code EitherTKind.Witness<F, L>}.
 * @see EitherT
 * @see EitherTKind.Witness
 * @see EitherTKindHelper
 * @see Kind
 * @see Either
 */
public interface EitherTKind<F, L, R> extends Kind<EitherTKind.Witness<F, L>, R> {

  /**
   * The phantom type marker (witness type) for the {@code EitherT<F, L, ?>} type constructor. This
   * class is parameterised by {@code OUTER_F} (the witness of the outer monad) and {@code TYPE_L}
   * (the "Left" type of the inner {@link Either}). It is used as the first type argument to {@link
   * Kind} (i.e., {@code G} in {@code Kind<G, A>}) for {@code EitherT} instances with a fixed outer
   * monad and "Left" type.
   *
   * @param <OUTER_F> The witness type of the outer monad.
   * @param <TYPE_L> The type of the "Left" value {@code L} associated with this witness.
   */
  final class Witness<OUTER_F, TYPE_L> {
    private Witness() { // Private constructor to prevent instantiation.
    }
  }
}
