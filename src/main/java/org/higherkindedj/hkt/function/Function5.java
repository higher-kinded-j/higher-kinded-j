// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.function;

import org.jspecify.annotations.Nullable;

/**
 * Represents a function that accepts five arguments and produces a result. This is a functional
 * interface whose functional method is {@link #apply(Object, Object, Object, Object, Object)}.
 *
 * <p>This interface extends the concept of functions with multiple arguments to support five
 * parameters. It is used within the Higher-Kinded-J library, particularly in the {@code yield}
 * methods of the {@link org.higherkindedj.hkt.expression.For} comprehension builder to combine five
 * monadic values.
 *
 * @param <T1> The type of the first argument to the function.
 * @param <T2> The type of the second argument to the function.
 * @param <T3> The type of the third argument to the function.
 * @param <T4> The type of the fourth argument to the function.
 * @param <T5> The type of the fifth argument to the function.
 * @param <R> The type of the result of the function.
 * @see org.higherkindedj.hkt.expression.For.MonadicSteps5#yield(Function5)
 */
@FunctionalInterface
public interface Function5<T1, T2, T3, T4, T5, R> {

  /**
   * Applies this function to the given arguments.
   *
   * @param t1 The first function argument.
   * @param t2 The second function argument.
   * @param t3 The third function argument.
   * @param t4 The fourth function argument.
   * @param t5 The fifth function argument.
   * @return The function result. The nullability of the result depends on the specific
   *     implementation of this function and the type {@code R}.
   */
  @Nullable R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5);
}
