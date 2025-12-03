// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Coyoneda - the free functor for automatic functor instances and map fusion.
 *
 * <p>This package provides the {@link org.higherkindedj.hkt.coyoneda.Coyoneda} type, which gives
 * any type constructor F a Functor instance for free by deferring and accumulating map operations.
 *
 * <h2>Key Components</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.coyoneda.Coyoneda} - The free functor type
 *   <li>{@link org.higherkindedj.hkt.coyoneda.CoyonedaKind} - HKT representation
 *   <li>{@link org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper} - Widen/narrow operations
 *   <li>{@link org.higherkindedj.hkt.coyoneda.CoyonedaFunctor} - Functor instance (always valid)
 * </ul>
 *
 * <h2>Core Benefits</h2>
 *
 * <ul>
 *   <li><b>Automatic Functor instances:</b> Any type constructor gets a Functor without
 *       implementation
 *   <li><b>Map fusion:</b> Multiple map operations are fused into a single traversal
 *   <li><b>Deferred execution:</b> Actual mapping only happens when lowering
 *   <li><b>DSL simplification:</b> Combined with Free monad, instructions don't need to be Functors
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Lift any Kind<F, A> into Coyoneda
 * Coyoneda<F, Integer> coyo = Coyoneda.lift(someKind);
 *
 * // Map without needing a Functor for F
 * Coyoneda<F, String> mapped = coyo
 *     .map(x -> x * 2)
 *     .map(x -> x + 1)
 *     .map(Object::toString);
 * // All three maps are fused!
 *
 * // When ready, lower back using a Functor
 * Kind<F, String> result = mapped.lower(functor);
 * // Only ONE map operation is performed
 * }</pre>
 *
 * <h2>Mathematical Background</h2>
 *
 * <p>Coyoneda is the <em>covariant Yoneda lemma</em> applied to functors:
 *
 * <pre>
 * ∫^X (X → A) × F[X] ≅ F[A]
 * </pre>
 *
 * <p>This isomorphism shows that Coyoneda[F, A] is isomorphic to F[A] for any functor F.
 *
 * @see org.higherkindedj.hkt.coyoneda.Coyoneda
 * @see org.higherkindedj.hkt.coyoneda.CoyonedaFunctor
 * @see org.higherkindedj.hkt.Functor
 */
@NullMarked
package org.higherkindedj.hkt.coyoneda;

import org.jspecify.annotations.NullMarked;
