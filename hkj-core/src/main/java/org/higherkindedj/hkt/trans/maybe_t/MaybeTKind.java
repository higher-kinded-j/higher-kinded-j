// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trans.maybe_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Kind interface marker for the {@link MaybeT MaybeT&lt;F, A&gt;} monad transformer.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link MaybeT} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs). An {@code MaybeT<F, A>}
 * wraps a monadic value {@code Kind<F, Maybe<A>>}.
 *
 * <p>For HKT purposes, {@code MaybeT<F, ?>} (a {@code MaybeT} with a fixed outer monad witness
 * {@code F}) is treated as a type constructor {@code G} that takes one type argument {@code A} (the
 * type of the value in the inner {@link Maybe}).
 *
 * <p>Specifically, when using {@code MaybeTKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" ({@code G} in {@code Kind<G, A>}) becomes {@code
 *       MaybeTKind.Witness<F>}. This represents the {@code MaybeT} type constructor, partially
 *       applied with the outer monad witness {@code F}.
 *   <li>The "value type" ({@code A} in {@code Kind<G, A>}) corresponds to {@code A}, the type of
 *       the value in the inner {@link Maybe}.
 * </ul>
 *
 * <p>Instances of {@code Kind<MaybeTKind.Witness<F>, A>} can be converted to/from concrete {@code
 * MaybeT<F, A>} instances using {@link MaybeTKindHelper}.
 *
 * @param <F> The witness type of the outer monad. This parameter is captured by the {@link Witness}
 *     type for HKT representation.
 * @param <A> The type of the value potentially held by the inner {@link Maybe}. This is the type
 *     parameter that varies for the higher-kinded type {@code MaybeTKind.Witness<F>}.
 * @see MaybeT
 * @see MaybeTKind.Witness
 * @see MaybeTKindHelper
 * @see Kind
 * @see Maybe
 */
public interface MaybeTKind<F, A> extends Kind<MaybeTKind.Witness<F>, A> {

  /**
   * The phantom type marker (witness type) for the {@code MaybeT<F, ?>} type constructor. This
   * class is parameterized by {@code OUTER_F} (the witness of the outer monad). It is used as the
   * first type argument to {@link Kind} (i.e., {@code G} in {@code Kind<G, A>}) for {@code MaybeT}
   * instances with a fixed outer monad.
   *
   * @param <OUTER_F> The witness type of the outer monad.
   */
  final class Witness<OUTER_F> {
    private Witness() { // Private constructor to prevent instantiation.
    }
  }
}
