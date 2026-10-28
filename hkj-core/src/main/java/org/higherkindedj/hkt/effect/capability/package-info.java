// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Provides capability interfaces for the Effect Path API.
 *
 * <p>These interfaces define composable capabilities that correspond to typeclass hierarchies in
 * functional programming. Path types implement these interfaces to expose their available
 * operations in a discoverable, IDE-friendly manner.
 *
 * <h2>Capability Hierarchy</h2>
 *
 * <pre>{@code
 *                Composable (Functor)
 *                     │
 *                Combinable (Applicative)
 *                     │
 *                Chainable (Monad)
 *                     │
 *       ┌─────────────┴─────────────┐
 *       │                           │
 * Recoverable                   Effectful
 * (MonadError)                     (IO)
 * }</pre>
 *
 * <h2>Interface Responsibilities</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Composable} - Transform values with {@code
 *       map} and observe with {@code peek}
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Combinable} - Combine independent
 *       computations with {@code zipWith} and {@code map2}
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Chainable} - Sequence dependent computations
 *       with {@code via}, {@code flatMap}, and {@code then}
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Recoverable} - Handle errors with {@code
 *       recover}, {@code recoverWith}, and {@code mapError}
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Effectful} - Execute side effects with
 *       {@code unsafeRun}
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Accumulating} - Combine independent
 *       computations, collecting every error, with {@code zipWithAccum}
 * </ul>
 *
 * <h2>The Family Parameter</h2>
 *
 * <p>Every interface from {@code Combinable} down, and {@code Accumulating}, takes the implementing
 * Path's family as its first type parameter: its own class with the value type left open, such as
 * {@code MaybePath<?>} or {@code EitherPath<? extends E, ?>}. A step passed to {@code via}, {@code
 * then}, {@code zipWith}, {@code recoverWith}, {@code orElse} or an accumulating method must belong
 * to that family, so the compiler checks what a step returns. See {@link
 * org.higherkindedj.hkt.effect.capability.Chainable} for how each Path's family may vary.
 *
 * @see org.higherkindedj.hkt.effect.Path
 */
@NullMarked
package org.higherkindedj.hkt.effect.capability;

import org.jspecify.annotations.NullMarked;
