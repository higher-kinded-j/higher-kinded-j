// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * Represents a higher-kinded type with two type parameters: {@code F<A, B>}. This is used for type
 * constructors that take two type arguments, such as {@code Function}, {@code Either}, or
 * profunctors in general.
 *
 * <h2>Witness Arity</h2>
 *
 * <p>The witness type {@code F} must implement {@link WitnessArity
 * WitnessArity&lt;TypeArity.Binary&gt;} to declare that it represents a binary type constructor:
 *
 * <pre>{@code
 * // Binary witness (for Bifunctor, Profunctor)
 * public interface EitherKind2<L, R> extends Kind2<EitherKind2.Witness, L, R> {
 *     final class Witness implements WitnessArity<TypeArity.Binary> {
 *         private Witness() {}
 *     }
 * }
 * }</pre>
 *
 * @param <F> The witness type for the type constructor. Must implement {@link WitnessArity
 *     WitnessArity&lt;TypeArity.Binary&gt;}.
 * @param <A> The first type parameter
 * @param <B> The second type parameter
 * @see WitnessArity
 * @see TypeArity.Binary
 * @see Kind
 * @see Bifunctor
 * @see Profunctor
 */
@NullMarked
public interface Kind2<F extends WitnessArity<TypeArity.Binary>, A, B> {}
