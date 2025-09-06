// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * Represents a higher-kinded type with two type parameters: {@code F<A, B>}. This is used for type
 * constructors that take two type arguments, such as {@code Function}, {@code Either}, or
 * profunctors in general.
 *
 * @param <F> The witness type for the type constructor
 * @param <A> The first type parameter
 * @param <B> The second type parameter
 */
@NullMarked
public interface Kind2<F, A, B> {}
