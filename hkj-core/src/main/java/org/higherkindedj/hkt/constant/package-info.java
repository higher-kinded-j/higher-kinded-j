// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Provides components for the {@code Const} type and its simulation as a Higher-Kinded Type. {@code
 * Const} holds a value of type {@code C} and ignores its phantom type {@code A}, which makes it the
 * functor behind folds and getters. Includes {@link org.higherkindedj.hkt.constant.Const}, {@link
 * org.higherkindedj.hkt.constant.ConstKind}, {@link org.higherkindedj.hkt.constant.ConstKind2}, and
 * its applicative and bifunctor instances.
 */
@NullMarked
package org.higherkindedj.hkt.constant;

import org.jspecify.annotations.NullMarked;
