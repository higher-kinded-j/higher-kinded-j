// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * Marker interface declaring a witness type's arity in the HKT encoding.
 *
 * <p>Witnesses implementing this interface declare what "shape" of type constructor they represent.
 * This enables compile-time verification that witnesses are used correctly with appropriate type
 * classes.
 *
 * <h2>Purpose</h2>
 *
 * <p>In Higher-Kinded-J, witness types are phantom markers that represent type constructors. This
 * interface adds type-level information about the witness's arity:
 *
 * <ul>
 *   <li>{@link TypeArity.Unary} - For witnesses used with {@link Kind Kind&lt;F, A&gt;}
 *   <li>{@link TypeArity.Binary} - For witnesses used with {@link Kind2 Kind2&lt;F, A, B&gt;}
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple Unary Witness</h3>
 *
 * <pre>{@code
 * // For types like Maybe<A>, List<A>, IO<A>
 * public interface MaybeKind<A> extends Kind<MaybeKind.Witness, A> {
 *     final class Witness implements WitnessArity<TypeArity.Unary> {
 *         private Witness() {}
 *     }
 * }
 * }</pre>
 *
 * <h3>Parameterized Unary Witness (Partial Application)</h3>
 *
 * <pre>{@code
 * // For types like Either<L, R> used as Functor/Monad (right-biased)
 * public interface EitherKind<L, R> extends Kind<EitherKind.Witness<L>, R> {
 *     final class Witness<TYPE_L> implements WitnessArity<TypeArity.Unary> {
 *         private Witness() {}
 *     }
 * }
 * }</pre>
 *
 * <h3>Binary Witness</h3>
 *
 * <pre>{@code
 * // For types like Either<L, R> used as Bifunctor
 * public interface EitherKind2<L, R> extends Kind2<EitherKind2.Witness, L, R> {
 *     final class Witness implements WitnessArity<TypeArity.Binary> {
 *         private Witness() {}
 *     }
 * }
 * }</pre>
 *
 * <h2>Type Class Constraints</h2>
 *
 * <p>Type classes use this interface to constrain their witness type parameter:
 *
 * <ul>
 *   <li>{@link Functor Functor&lt;F extends WitnessArity&lt;TypeArity.Unary&gt;&gt;}
 *   <li>{@link Applicative Applicative&lt;F extends WitnessArity&lt;TypeArity.Unary&gt;&gt;}
 *   <li>{@link Monad Monad&lt;M extends WitnessArity&lt;TypeArity.Unary&gt;&gt;}
 *   <li>{@link Bifunctor Bifunctor&lt;F extends WitnessArity&lt;TypeArity.Binary&gt;&gt;}
 *   <li>{@link Profunctor Profunctor&lt;P extends WitnessArity&lt;TypeArity.Binary&gt;&gt;}
 * </ul>
 *
 * <h2>Benefits</h2>
 *
 * <ul>
 *   <li><b>Compile-time safety:</b> Prevents using wrong-arity witnesses with type classes
 *   <li><b>Self-documenting:</b> Witness declarations explicitly show their intended use
 *   <li><b>IDE support:</b> Autocompletion and type checking work correctly
 *   <li><b>Zero runtime cost:</b> Purely compile-time type information
 * </ul>
 *
 * @param <A> The type arity of this witness ({@link TypeArity.Unary} or {@link TypeArity.Binary})
 * @see TypeArity
 * @see Kind
 * @see Kind2
 * @see Functor
 * @see Bifunctor
 */
@NullMarked
public interface WitnessArity<A extends TypeArity> {}
