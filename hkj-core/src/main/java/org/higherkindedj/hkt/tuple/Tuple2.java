// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.util.validation.Validation;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * An immutable tuple containing two elements of potentially different types.
 *
 * <p>As a {@link java.lang.Record}, it automatically provides a canonical constructor, accessors
 * ({@code _1()}, {@code _2()}), and implementations for {@code equals()}, {@code hashCode()}, and
 * {@code toString()}.
 *
 * <p>This type is a natural Bifunctor, supporting independent transformation of both elements via
 * {@link #bimap(Function, Function)}, {@link #mapFirst(Function)}, and {@link
 * #mapSecond(Function)}.
 *
 * @param <A> The type of the first element.
 * @param <B> The type of the second element.
 * @param _1 The first element of the tuple.
 * @param _2 The second element of the tuple.
 */
@GenerateLenses
public record Tuple2<A, B>(A _1, B _2) implements Tuple {

  private static final Class<Tuple2> TUPLE2_CLASS = Tuple2.class;

  /**
   * Transforms both elements of this tuple using the provided mapping functions, producing a new
   * tuple with potentially different types for both elements.
   *
   * <p>This is the fundamental bifunctor operation for {@code Tuple2}, allowing simultaneous
   * transformation of both the first and second elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Tuple2<String, Integer> tuple = new Tuple2<>("Alice", 30);
   * Tuple2<Integer, String> result = tuple.bimap(
   *     String::length,           // Transform first element: "Alice" -> 5
   *     age -> age + " years"     // Transform second element: 30 -> "30 years"
   * );
   * // result = new Tuple2<>(5, "30 years")
   * }</pre>
   *
   * @param firstMapper The non-null function to apply to the first element.
   * @param secondMapper The non-null function to apply to the second element.
   * @param <C> The type of the first element in the resulting tuple.
   * @param <D> The type of the second element in the resulting tuple.
   * @return A new {@code Tuple2<C, D>} with both elements transformed. The returned tuple will be
   *     non-null.
   * @throws NullPointerException if either {@code firstMapper} or {@code secondMapper} is null.
   */
  public <C, D> Tuple2<C, D> bimap(
      Function<? super A, ? extends C> firstMapper,
      Function<? super B, ? extends D> secondMapper) {
    Validation.function().requireMapper(firstMapper, "firstMapper", TUPLE2_CLASS, BIMAP);
    Validation.function().requireMapper(secondMapper, "secondMapper", TUPLE2_CLASS, BIMAP);

    return new Tuple2<>(firstMapper.apply(_1), secondMapper.apply(_2));
  }

  /**
   * Transforms only the first element of this tuple, leaving the second element unchanged.
   *
   * <p>This operation allows you to transform the first element whilst preserving the second
   * element.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Tuple2<String, Integer> tuple = new Tuple2<>("Alice", 30);
   * Tuple2<Integer, Integer> result = tuple.mapFirst(String::length);
   * // result = new Tuple2<>(5, 30)
   * }</pre>
   *
   * <p><b>Note:</b> This is equivalent to calling {@code bimap(firstMapper, Function.identity())}.
   *
   * @param firstMapper The non-null function to apply to the first element.
   * @param <C> The type of the first element in the resulting tuple.
   * @return A new {@code Tuple2<C, B>} with the first element transformed. The returned tuple will
   *     be non-null.
   * @throws NullPointerException if {@code firstMapper} is null.
   */
  public <C> Tuple2<C, B> mapFirst(Function<? super A, ? extends C> firstMapper) {
    Validation.function().requireMapper(firstMapper, "firstMapper", TUPLE2_CLASS, MAP_FIRST);

    return new Tuple2<>(firstMapper.apply(_1), _2);
  }

  /**
   * Transforms only the second element of this tuple, leaving the first element unchanged.
   *
   * <p>This operation allows you to transform the second element whilst preserving the first
   * element.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Tuple2<String, Integer> tuple = new Tuple2<>("Alice", 30);
   * Tuple2<String, String> result = tuple.mapSecond(age -> age + " years old");
   * // result = new Tuple2<>("Alice", "30 years old")
   * }</pre>
   *
   * <p><b>Note:</b> This is equivalent to calling {@code bimap(Function.identity(),
   * secondMapper)}.
   *
   * @param secondMapper The non-null function to apply to the second element.
   * @param <D> The type of the second element in the resulting tuple.
   * @return A new {@code Tuple2<A, D>} with the second element transformed. The returned tuple
   *     will be non-null.
   * @throws NullPointerException if {@code secondMapper} is null.
   */
  public <D> Tuple2<A, D> mapSecond(Function<? super B, ? extends D> secondMapper) {
    Validation.function().requireMapper(secondMapper, "secondMapper", TUPLE2_CLASS, MAP_SECOND);

    return new Tuple2<>(_1, secondMapper.apply(_2));
  }
}
