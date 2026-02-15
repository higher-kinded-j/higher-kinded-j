// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.function;

import org.jspecify.annotations.Nullable;

/**
 * Represents a function that accepts ten arguments and produces a result. This is a functional
 * interface whose functional method is {@link #apply(Object, Object, Object, Object, Object,
 * Object, Object, Object, Object, Object)}.
 *
 * <p>This interface extends the concept of functions with multiple arguments to support ten
 * parameters. It is used within the Higher-Kinded-J library, particularly in the {@code yield}
 * methods of the {@code org.higherkindedj.hkt.expression.For} comprehension builder to combine ten
 * monadic values.
 *
 * @param <T1> The type of the first argument to the function.
 * @param <T2> The type of the second argument to the function.
 * @param <T3> The type of the third argument to the function.
 * @param <T4> The type of the fourth argument to the function.
 * @param <T5> The type of the fifth argument to the function.
 * @param <T6> The type of the sixth argument to the function.
 * @param <T7> The type of the seventh argument to the function.
 * @param <T8> The type of the eighth argument to the function.
 * @param <T9> The type of the ninth argument to the function.
 * @param <T10> The type of the tenth argument to the function.
 * @param <R> The type of the result of the function.
 * @see Function9
 * @see Function11
 */
@FunctionalInterface
public interface Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> {

  /**
   * Applies this function to the given arguments.
   *
   * @param t1 The first function argument.
   * @param t2 The second function argument.
   * @param t3 The third function argument.
   * @param t4 The fourth function argument.
   * @param t5 The fifth function argument.
   * @param t6 The sixth function argument.
   * @param t7 The seventh function argument.
   * @param t8 The eighth function argument.
   * @param t9 The ninth function argument.
   * @param t10 The tenth function argument.
   * @return The function result. The nullability of the result depends on the specific
   *     implementation of this function and the type {@code R}.
   */
  @Nullable R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8, T9 t9, T10 t10);
}
