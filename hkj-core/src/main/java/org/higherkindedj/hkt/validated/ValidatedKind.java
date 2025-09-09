// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NullMarked;

/**
 * Kind interface marker for the {@link Validated Validated&lt;E, A&gt;} type in Higher-Kinded-J.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link Validated} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs). A {@code Validated<E, A>}
 * represents a value that is either {@code Invalid<E>} or {@code Valid<A>}.
 *
 * <p>For HKT purposes, {@code Validated<E, ?>} (a {@code Validated} with a fixed "Error" type
 * {@code E}) is treated as a type constructor {@code F} that takes one type argument {@code A} (the
 * "Valid" value type). This structure facilitates defining typeclass instances (like Functor,
 * Monad) that are right-biased. Right-biased means that operations like {@code map} and {@code
 * flatMap} (defined by Functor and Monad respectively) will operate on the 'Valid' value ({@code
 * A}) if present, and will propagate the 'Invalid' error ({@code E}) unchanged.
 *
 * <p>Specifically, when using {@code ValidatedKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" ({@code F} in {@code Kind<F, A>}) becomes {@code
 *       ValidatedKind.Witness<E>}. This represents the {@code Validated} type constructor,
 *       partially applied with the "Error" type {@code E}.
 *   <li>The "value type" ({@code A_val} in {@code Kind<F, A_val>}) corresponds to {@code A}, the
 *       "Valid" value type.
 * </ul>
 *
 * <p>Instances of {@code Kind<ValidatedKind.Witness<E>, A>} can be converted to/from concrete
 * {@code Validated<E, A>} instances using {@link ValidatedKindHelper}.
 *
 * @param <E> The type of the "Error" value. This parameter is captured by the {@link Witness} type
 *     for HKT representation.
 * @param <A> The type of the "Valid" value. This is the type parameter that varies for the
 *     higher-kinded type {@code ValidatedKind.Witness<E>}.
 * @see Validated
 * @see Valid
 * @see Invalid
 * @see ValidatedKind.Witness
 * @see ValidatedKindHelper
 * @see Kind
 */
public interface ValidatedKind<E, A> extends Kind<ValidatedKind.Witness<E>, A> {

  /**
   * The phantom type marker (witness type) for the {@code Validated<E, ?>} type constructor. This
   * non-instantiable class acts as a tag to represent the {@code Validated} type constructor when
   * partially applied with a specific error type {@code E}. It is used as the first type argument
   * to {@link Kind} (i.e., {@code F} in {@code Kind<F, A>}) for {@code Validated} instances with a
   * fixed "Error" type.
   *
   * @param <ERROR_TYPE> The type of the "Error" value {@code E} associated with this witness.
   */
  final class Witness<ERROR_TYPE> {
    private Witness() { // Private constructor to prevent instantiation.
    }
  }
}
