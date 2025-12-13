// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Provides the Effect Path API for fluent composition of effect types.
 *
 * <p>The Effect Path API wraps higher-kinded-j's effect types ({@code Maybe}, {@code Either},
 * {@code Try}, {@code IO}) in thin, fluent wrappers that provide a unified vocabulary for
 * composition. The vocabulary deliberately mirrors the Focus DSL from the optics module: where
 * FocusPath navigates through <em>data structures</em>, EffectPath navigates through <em>effect
 * types</em>.
 *
 * <h2>Core Components</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.effect.Path} - Static factory for creating path instances
 *   <li>{@link org.higherkindedj.hkt.effect.MaybePath} - Fluent wrapper for {@code Maybe}
 *   <li>{@link org.higherkindedj.hkt.effect.EitherPath} - Fluent wrapper for {@code Either}
 *   <li>{@link org.higherkindedj.hkt.effect.TryPath} - Fluent wrapper for {@code Try}
 *   <li>{@link org.higherkindedj.hkt.effect.IOPath} - Fluent wrapper for {@code IO}
 * </ul>
 *
 * <h2>Capability Interfaces</h2>
 *
 * <p>Path types implement capability interfaces that correspond to typeclass hierarchies:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Composable} - Functor operations (map, peek)
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Combinable} - Applicative operations
 *       (zipWith, map2)
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Chainable} - Monad operations (via, flatMap,
 *       then)
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Recoverable} - Error handling (recover,
 *       recoverWith, mapError)
 *   <li>{@link org.higherkindedj.hkt.effect.capability.Effectful} - Side effect execution
 *       (unsafeRun)
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Basic composition
 * String name = Path.maybe(userId)
 *     .via(id -> userRepo.findById(id))
 *     .map(User::getName)
 *     .getOrElse("Anonymous");
 *
 * // Error handling with type conversion
 * EitherPath<Error, User> result = Path.maybe(userId)
 *     .via(id -> userRepo.findById(id))
 *     .toEitherPath(Error.notFound("User not found"))
 *     .via(user -> validateEmail(user));
 * }</pre>
 *
 * @see org.higherkindedj.hkt.effect.Path
 * @see org.higherkindedj.hkt.effect.capability.Composable
 */
@NullMarked
package org.higherkindedj.hkt.effect;

import org.jspecify.annotations.NullMarked;
