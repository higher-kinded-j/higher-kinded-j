// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the {@link OptionalT OptionalT&lt;F, A&gt;} monad transformer.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link OptionalT} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs). An {@code OptionalT<F, A>}
 * wraps a monadic value {@code Kind<F, Optional<A>>}.
 *
 * <p>For HKT purposes, {@code OptionalT<F, ?>} (an {@code OptionalT} with a fixed outer monad
 * witness {@code F}) is treated as a type constructor {@code G} that takes one type argument {@code
 * A} (the type of the value in the inner {@link Optional}).
 *
 * <p>Specifically, when using {@code OptionalTKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" ({@code G} in {@code Kind<G, A>}) becomes {@code
 *       OptionalTKind.Witness<F>}. This represents the {@code OptionalT} type constructor,
 *       partially applied with the outer monad witness {@code F}.
 *   <li>The "value type" ({@code A} in {@code Kind<G, A>}) corresponds to {@code A}, the type of
 *       the value in the inner {@link Optional}.
 * </ul>
 *
 * <p>Instances of {@code Kind<OptionalTKind.Witness<F>, A>} can be converted to/from concrete
 * {@code OptionalT<F, A>} instances using {@link OptionalTKindHelper}.
 *
 * @param <F> The witness type of the outer monad. This parameter is captured by the {@link Witness}
 *     type for HKT representation.
 * @param <A> The type of the value potentially held by the inner {@link Optional}. This is the type
 *     parameter that varies for the higher-kinded type {@code OptionalTKind.Witness<F>}.
 * @see OptionalT
 * @see OptionalTKind.Witness
 * @see OptionalTKindHelper
 * @see Kind
 * @see Optional
 */
public interface OptionalTKind<F, A> extends Kind<OptionalTKind.Witness<F>, A> {

  /**
   * The phantom type marker (witness type) for the {@code OptionalT<F, ?>} type constructor. This
   * class is parameterized by {@code OUTER_F} (the witness of the outer monad). It is used as the
   * first type argument to {@link Kind} (i.e., {@code G} in {@code Kind<G, A>}) for {@code
   * OptionalT} instances with a fixed outer monad.
   *
   * @param <OUTER_F> The witness type of the outer monad.
   */
  final class Witness<OUTER_F> {
    private Witness() { // Private constructor to prevent instantiation.
    }
  }
}
