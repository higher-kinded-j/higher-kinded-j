package org.higherkindedj.hkt.function;

import org.jspecify.annotations.Nullable;

/**
 * Represents a function that accepts four arguments and produces a result. This is a functional
 * interface whose functional method is {@link #apply(Object, Object, Object, Object)}.
 *
 * <p>This interface extends the concept of {@link java.util.function.Function}, {@link
 * java.util.function.BiFunction}, and {@link Function3} to four arguments. It is used within the
 * Higher-Kinded-J library, for example, in the {@code map4} methods of {@link
 * org.higherkindedj.hkt.Applicative} type classes to combine four monadic values.
 *
 * @param <T1> The type of the first argument to the function.
 * @param <T2> The type of the second argument to the function.
 * @param <T3> The type of the third argument to the function.
 * @param <T4> The type of the fourth argument to the function.
 * @param <R> The type of the result of the function.
 * @see org.higherkindedj.hkt.Applicative#map4(org.higherkindedj.hkt.Kind,
 *     org.higherkindedj.hkt.Kind, org.higherkindedj.hkt.Kind, org.higherkindedj.hkt.Kind,
 *     Function4)
 */
@FunctionalInterface
public interface Function4<T1, T2, T3, T4, R> {

  /**
   * Applies this function to the given arguments.
   *
   * @param t1 The first function argument.
   * @param t2 The second function argument.
   * @param t3 The third function argument.
   * @param t4 The fourth function argument.
   * @return The function result. The nullability of the result depends on the specific
   *     implementation of this function and the type {@code R}.
   */
  @Nullable R apply(T1 t1, T2 t2, T3 t3, T4 t4);
}
