// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * The core of the Higher-Kinded Type (HKT) simulation.
 *
 * <p>{@code Kind<F, A>} is a type that represents the application of a type constructor {@code F}
 * to a type argument {@code A}. Since Java's type system does not natively support type
 * constructors as parameters (like F&lt;_&gt;), we use a "witness type" for {@code F} to stand in
 * for the constructor.
 *
 * <p>For example, a {@code java.util.List<String>} would be represented as {@code
 * Kind<ListKind.Witness, String>}, where {@code ListKind.Witness} is the marker type that
 * represents the {@code List} type constructor.
 *
 * <h2>Witness Arity</h2>
 *
 * <p>The witness type {@code F} must implement {@link WitnessArity} to declare its arity. This
 * enables compile-time verification that witnesses are used correctly:
 *
 * <pre>{@code
 * // Unary witness (for Functor, Monad, etc.)
 * final class Witness implements WitnessArity<TypeArity.Unary> {
 *     private Witness() {}
 * }
 *
 * // Parameterized unary witness (partial application)
 * final class Witness<L> implements WitnessArity<TypeArity.Unary> {
 *     private Witness() {}
 * }
 * }</pre>
 *
 * @param <F> The witness type for the type constructor. Must implement {@link WitnessArity}.
 * @param <A> The type of the value contained within the context.
 * @see WitnessArity
 * @see TypeArity
 * @see Kind2
 */
@NullMarked
public interface Kind<F extends WitnessArity<?>, A> {}
