// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface marker for the {@link Try} type in Higher-Kinded-J. Represents {@code Try} as a
 * type constructor 'F' in {@code Kind<F, T>}.
 *
 * <p>This interface, {@code TryKind<T>}, serves as a "kinded" version of {@code Try<T>}. It allows
 * {@code Try} to be treated as a type constructor (represented by {@link TryKind.Witness}) which
 * takes one type argument {@code T} (the type of the value potentially held by the {@code Try} in
 * case of a {@link Try.Success}).
 *
 * <p>When using {@code TryKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" ({@code F} or {@code Mu}) becomes {@link TryKind.Witness}.
 *   <li>The "value type" ({@code A} or {@code T}) is {@code T}.
 * </ul>
 *
 * An instance of {@code Kind<TryKind.Witness, T>} can be converted back to a concrete {@code
 * Try<T>} using {@link TryKindHelper#narrow(Kind)}.
 *
 * @param <T> The type of the value potentially held by the Try (in case of Success).
 * @see Try
 * @see Try.Success
 * @see Try.Failure
 * @see TryKindHelper
 * @see TryKind.Witness
 */
public interface TryKind<T> extends Kind<TryKind.Witness, T> {

  /**
   * The phantom type marker (witness type) for the Try type constructor. This is used as the 'F' in
   * {@code Kind<F, T>} for Try.
   */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    // Private constructor to prevent instantiation of the witness type itself.
    // Its purpose is purely for type-level representation.
    private Witness() {}
  }
}
