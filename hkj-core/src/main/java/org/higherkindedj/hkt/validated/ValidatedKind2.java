// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind2 interface marker for the {@link Validated Validated&lt;E, A&gt;} type in Higher-Kinded-J.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link Validated} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs) with two type parameters. A
 * {@code Validated<E, A>} represents a value that is either {@code Invalid<E>} or {@code Valid<A>}.
 *
 * <p>For bifunctor purposes, {@code Validated<?, ?>} is treated as a type constructor {@code F}
 * that takes two type arguments: {@code E} (the "Error" type) and {@code A} (the "Valid" value
 * type). This structure facilitates defining bifunctor instances that can transform both the error
 * and value channels independently.
 *
 * <p>Specifically, when using {@code ValidatedKind2} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" ({@code F} in {@code Kind2<F, E, A>}) becomes {@code
 *       ValidatedKind2.Witness}. This represents the {@code Validated} type constructor.
 *   <li>The first type parameter ({@code E} in {@code Kind2<F, E, A>}) corresponds to the "Error"
 *       type.
 *   <li>The second type parameter ({@code A} in {@code Kind2<F, E, A>}) corresponds to the "Valid"
 *       value type.
 * </ul>
 *
 * <p>Instances of {@code Kind2<ValidatedKind2.Witness, E, A>} can be converted to/from concrete
 * {@code Validated<E, A>} instances using {@link ValidatedKindHelper}.
 *
 * <p>This is distinct from {@link ValidatedKind}, which fixes the error type parameter for use with
 * {@link org.higherkindedj.hkt.Functor} and {@link org.higherkindedj.hkt.Applicative} instances.
 *
 * @param <E> The type of the "Error" value.
 * @param <A> The type of the "Valid" value.
 * @see Validated
 * @see Valid
 * @see Invalid
 * @see ValidatedKind2.Witness
 * @see ValidatedKindHelper
 * @see Kind2
 */
public interface ValidatedKind2<E, A> extends Kind2<ValidatedKind2.Witness, E, A> {

  /**
   * The phantom type marker (witness type) for the {@code Validated<?, ?>} type constructor. This
   * non-instantiable class acts as a tag to represent the {@code Validated} type constructor for
   * bifunctor operations. It is used as the first type argument to {@link Kind2} (i.e., {@code F}
   * in {@code Kind2<F, E, A>}) for {@code Validated} instances.
   */
  final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() { // Private constructor to prevent instantiation.
    }
  }
}
