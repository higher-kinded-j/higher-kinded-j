// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Provides Effect Context types that wrap monad transformers with user-friendly APIs.
 *
 * <p>Effect Contexts form Layer 2 of the transformer-path integration, offering transformer power
 * with domain-focused naming. They hide the complexity of higher-kinded types while preserving the
 * full capability of the underlying transformers.
 *
 * <h2>Available Contexts</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.effect.context.ErrorContext} - Wraps {@code EitherT} for typed
 *       error handling across effectful operations
 *   <li>{@link org.higherkindedj.hkt.effect.context.OptionalContext} - Wraps {@code MaybeT} for
 *       computations that may produce no value
 * </ul>
 *
 * <h2>Architecture</h2>
 *
 * <pre>{@code
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  Layer 3: Raw Transformers (Existing)                               │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  EitherT, MaybeT, ReaderT, StateT                           │    │
 * │  │  Full HKT access, maximum flexibility                        │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ▲                                       │
 * │                              │ escape hatch (toEitherT, toMaybeT)    │
 * │  Layer 2: Effect Contexts (This Package)                            │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  ErrorContext, OptionalContext                              │    │
 * │  │  Domain-focused naming, full transformer power               │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ▲                                       │
 * │                              │ opt-in complexity                     │
 * │  Layer 1: Simple Lifting                                            │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  path.liftIO(), path.liftFuture(), io.catching()            │    │
 * │  │  One-step promotion, stays in Path ecosystem                 │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * └─────────────────────────────────────────────────────────────────────┘
 * }</pre>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Clean composition with typed errors
 * Either<ApiError, Profile> result = ErrorContext
 *     .io(() -> fetchUser(userId), ApiError::fromException)
 *     .via(user -> ErrorContext.io(
 *         () -> fetchProfile(user.profileId()),
 *         ApiError::fromException))
 *     .recover(err -> defaultProfile)
 *     .runIO()
 *     .unsafeRun();
 * }</pre>
 *
 * @see org.higherkindedj.hkt.effect.context.ErrorContext
 * @see org.higherkindedj.hkt.effect.context.OptionalContext
 * @see org.higherkindedj.hkt.either_t.EitherT
 * @see org.higherkindedj.hkt.maybe_t.MaybeT
 */
@NullMarked
package org.higherkindedj.hkt.effect.context;

import org.jspecify.annotations.NullMarked;
