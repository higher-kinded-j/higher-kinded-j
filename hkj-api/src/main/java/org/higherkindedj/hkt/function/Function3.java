// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.function;

import org.jspecify.annotations.Nullable;

/**
 * Represents a function that accepts three arguments and produces a result. This is a functional
 * interface whose functional method is {@link #apply(Object, Object, Object)}.
 *
 * <p>This interface is similar to {@link java.util.function.Function} and {@link
 * java.util.function.BiFunction} but is designed for three arguments. It is used within the
 * Higher-Kinded-J library, for example, in the {@code map3} methods of {@link
 * org.higherkindedj.hkt.Applicative} type classes to combine three monadic values.
 *
 * @param <T1> The type of the first argument to the function.
 * @param <T2> The type of the second argument to the function.
 * @param <T3> The type of the third argument to the function.
 * @param <R> The type of the result of the function.
 * @see org.higherkindedj.hkt.Applicative#map3(org.higherkindedj.hkt.Kind,
 *     org.higherkindedj.hkt.Kind, org.higherkindedj.hkt.Kind, Function3)
 */
@FunctionalInterface
public interface Function3<T1, T2, T3, R> {

  /**
   * Applies this function to the given arguments.
   *
   * @param t1 The first function argument.
   * @param t2 The second function argument.
   * @param t3 The third function argument.
   * @return The function result. The nullability of the result depends on the specific
   *     implementation of this function and the type {@code R}.
   */
  @Nullable R apply(T1 t1, T2 t2, T3 t3);
}
