// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Provides {@link org.higherkindedj.hkt.effect.boundary.EffectBoundary} and {@link
 * org.higherkindedj.hkt.effect.boundary.TestBoundary} for interpreting Free monad programs at a
 * clean boundary.
 *
 * <p>{@code EffectBoundary} targets the IO monad for production use. {@code TestBoundary} targets
 * the Id monad for pure, deterministic testing.
 *
 * @see org.higherkindedj.hkt.effect.boundary.EffectBoundary
 * @see org.higherkindedj.hkt.effect.boundary.TestBoundary
 * @see org.higherkindedj.hkt.effect.boundary.ProgramAnalysis
 */
@NullMarked
package org.higherkindedj.hkt.effect.boundary;

import org.jspecify.annotations.NullMarked;
