// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * A constant functor that holds a value of type {@code C} and ignores the phantom type {@code A}.
 *
 * <p>The {@code Const} type is useful for operations where you need to accumulate or preserve a
 * value while abstracting over a type parameter. When mapping over the second type parameter (the
 * phantom {@code A}), the constant value remains unchanged. This makes {@code Const} particularly
 * useful for implementing efficient folds and traversals.
 *
 * <p>As a {@link java.lang.Record}, it automatically provides a canonical constructor, an accessor
 * ({@code value()}), and implementations for {@code equals()}, {@code hashCode()}, and {@code
 * toString()}.
 *
 * <p>This type is a natural Bifunctor, where:
 *
 * <ul>
 *   <li>Mapping the first parameter transforms the constant value
 *   <li>Mapping the second parameter has no effect (the phantom type is ignored)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Create a Const holding a string value
 * Const<String, Integer> const1 = new Const<>("hello");
 *
 * // Mapping the phantom type (Integer -> Double) has no effect on the value
 * Const<String, Double> const2 = const1.mapSecond(i -> i * 2.0);
 * // const2.value() is still "hello"
 *
 * // Mapping the first parameter transforms the constant value
 * Const<Integer, Double> const3 = const2.mapFirst(String::length);
 * // const3.value() is 5
 * }</pre>
 *
 * @param <C> The type of the constant value.
 * @param <A> The phantom type parameter (not stored).
 * @param value The constant value held by this instance.
 */
public record Const<C, A>(C value) {

  private static final Class<?> CONST_CLASS = Const.class;

  /**
   * Transforms both the constant value and the phantom type parameter.
   *
   * <p>This is the fundamental bifunctor operation for {@code Const}. The first function is applied
   * to the constant value, while the second function affects only the phantom type parameter (and
   * thus has no runtime effect on the value).
   *
   * <p><b>Note on exception propagation:</b> Although the second function doesn't affect the
   * constant value, it is still applied (to a null input) to ensure that any exceptions it might
   * throw are properly propagated. This maintains consistency with bifunctor exception semantics.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Const<String, Integer> const = new Const<>("hello");
   * Const<Integer, String> result = const.bimap(
   *     String::length,           // Transform constant: "hello" -> 5
   *     i -> "Value: " + i        // Transform phantom type only
   * );
   * // result.value() is 5
   * }</pre>
   *
   * @param firstMapper The non-null function to apply to the constant value.
   * @param secondMapper The non-null function that defines the phantom type transformation.
   * @param <D> The type of the constant value in the resulting {@code Const}.
   * @param <B> The phantom type parameter in the resulting {@code Const}.
   * @return A new {@code Const<D, B>} with the constant value transformed. The returned instance
   *     will be non-null.
   * @throws NullPointerException if either {@code firstMapper} or {@code secondMapper} is null.
   */
  public <D, B> Const<D, B> bimap(
      Function<? super C, ? extends D> firstMapper, Function<? super A, ? extends B> secondMapper) {
    Validation.function().requireMapper(firstMapper, "firstMapper", CONST_CLASS, BIMAP);
    Validation.function().requireMapper(secondMapper, "secondMapper", CONST_CLASS, BIMAP);

    // Apply secondMapper to ensure exception propagation, even though we ignore the result
    // since A is phantom. We use null as the input since we don't have a value of type A.
    secondMapper.apply(null);

    return new Const<>(firstMapper.apply(value));
  }

  /**
   * Transforms only the constant value, leaving the phantom type unchanged.
   *
   * <p>This operation transforms the stored constant value whilst preserving the phantom type
   * parameter.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Const<String, Integer> const = new Const<>("hello");
   * Const<Integer, Integer> result = const.mapFirst(String::length);
   * // result.value() is 5
   * }</pre>
   *
   * <p><b>Note:</b> This is equivalent to calling {@code bimap(firstMapper, Function.identity())}.
   *
   * @param firstMapper The non-null function to apply to the constant value.
   * @param <D> The type of the constant value in the resulting {@code Const}.
   * @return A new {@code Const<D, A>} with the constant value transformed. The returned instance
   *     will be non-null.
   * @throws NullPointerException if {@code firstMapper} is null.
   */
  public <D> Const<D, A> mapFirst(Function<? super C, ? extends D> firstMapper) {
    Validation.function().requireMapper(firstMapper, "firstMapper", CONST_CLASS, MAP_FIRST);

    return new Const<>(firstMapper.apply(value));
  }

  /**
   * Transforms only the phantom type parameter, leaving the constant value unchanged.
   *
   * <p>Since the second type parameter is phantom (not stored), this operation has no effect on the
   * constant value. It only changes the phantom type in the type signature.
   *
   * <p><b>Note on exception propagation:</b> Although this operation doesn't affect the constant
   * value, the mapper is still applied (to a null input) to ensure that any exceptions it might
   * throw are properly propagated. This maintains consistency with bifunctor exception semantics.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Const<String, Integer> const = new Const<>("hello");
   * Const<String, Double> result = const.mapSecond(i -> i * 2.0);
   * // result.value() is still "hello" (unchanged)
   * }</pre>
   *
   * <p><b>Note:</b> This is equivalent to calling {@code bimap(Function.identity(), secondMapper)}.
   *
   * @param secondMapper The non-null function that defines the phantom type transformation.
   * @param <B> The phantom type parameter in the resulting {@code Const}.
   * @return A new {@code Const<C, B>} with the same constant value but a different phantom type.
   *     The returned instance will be non-null.
   * @throws NullPointerException if {@code secondMapper} is null.
   */
  @SuppressWarnings("unchecked")
  public <B> Const<C, B> mapSecond(Function<? super A, ? extends B> secondMapper) {
    Validation.function().requireMapper(secondMapper, "secondMapper", CONST_CLASS, MAP_SECOND);

    // Apply secondMapper to ensure exception propagation, even though we ignore the result
    // since A is phantom. We use null as the input since we don't have a value of type A.
    secondMapper.apply(null);

    // Since A is phantom, we can safely cast - the constant value remains unchanged
    return (Const<C, B>) this;
  }
}
