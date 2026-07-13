// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.function;

import org.jspecify.annotations.Nullable;

/**
 * Represents a function that accepts sixteen arguments and produces a result. This is a functional
 * interface whose functional method is {@link #apply(Object, Object, Object, Object, Object,
 * Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object)}.
 *
 * <p>This interface extends the concept of functions with multiple arguments to support sixteen
 * parameters. It is used within the Higher-Kinded-J library to apply the terminal step of the
 * accumulating-assembly ladder ({@code Validated.fields()} / {@code accumulate()}), combining
 * sixteen independently validated fields into a record. The For-comprehension builder stops at
 * twelve arguments (see {@code org.higherkindedj.optics.annotations.ArityCeilings}).
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
 * @param <T11> The type of the eleventh argument to the function.
 * @param <T12> The type of the twelfth argument to the function.
 * @param <T13> The type of the thirteenth argument to the function.
 * @param <T14> The type of the fourteenth argument to the function.
 * @param <T15> The type of the fifteenth argument to the function.
 * @param <T16> The type of the sixteenth argument to the function.
 * @param <R> The type of the result of the function.
 * @see Function15
 */
@FunctionalInterface
public interface Function16<
    T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, R> {

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
   * @param t11 The eleventh function argument.
   * @param t12 The twelfth function argument.
   * @param t13 The thirteenth function argument.
   * @param t14 The fourteenth function argument.
   * @param t15 The fifteenth function argument.
   * @param t16 The sixteenth function argument.
   * @return The function result. The nullability of the result depends on the specific
   *     implementation of this function and the type {@code R}.
   */
  @Nullable R apply(
      T1 t1,
      T2 t2,
      T3 t3,
      T4 t4,
      T5 t5,
      T6 t6,
      T7 t7,
      T8 t8,
      T9 t9,
      T10 t10,
      T11 t11,
      T12 t12,
      T13 t13,
      T14 t14,
      T15 t15,
      T16 t16);
}
