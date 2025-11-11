// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.util.validation.Validation;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * An immutable tuple containing three elements of potentially different types.
 *
 * <p>As a {@link java.lang.Record}, it automatically provides a canonical constructor, accessors
 * ({@code _1()}, {@code _2()}, {@code _3()}), and implementations for {@code equals()}, {@code
 * hashCode()}, and {@code toString()}.
 *
 * <p>This type supports independent transformation of all three elements via {@link
 * #map(Function, Function, Function)}, {@link #mapFirst(Function)}, {@link #mapSecond(Function)},
 * and {@link #mapThird(Function)}.
 *
 * @param <A> The type of the first element.
 * @param <B> The type of the second element.
 * @param <C> The type of the third element.
 * @param _1 The first element of the tuple.
 * @param _2 The second element of the tuple.
 * @param _3 The third element of the tuple.
 */
@GenerateLenses
public record Tuple3<A, B, C>(A _1, B _2, C _3) implements Tuple {

  private static final Class<Tuple3> TUPLE3_CLASS = Tuple3.class;

  /**
   * Transforms all three elements of this tuple using the provided mapping functions, producing a
   * new tuple with potentially different types for all elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);
   * Tuple3<Integer, String, String> result = tuple.map(
   *     String::length,           // "Alice" -> 5
   *     age -> age + " years",    // 30 -> "30 years"
   *     active -> active ? "yes" : "no"  // true -> "yes"
   * );
   * // result = new Tuple3<>(5, "30 years", "yes")
   * }</pre>
   *
   * @param firstMapper The non-null function to apply to the first element.
   * @param secondMapper The non-null function to apply to the second element.
   * @param thirdMapper The non-null function to apply to the third element.
   * @param <A2> The type of the first element in the resulting tuple.
   * @param <B2> The type of the second element in the resulting tuple.
   * @param <C2> The type of the third element in the resulting tuple.
   * @return A new {@code Tuple3<A2, B2, C2>} with all elements transformed.
   * @throws NullPointerException if any mapper is null.
   */
  public <A2, B2, C2> Tuple3<A2, B2, C2> map(
      Function<? super A, ? extends A2> firstMapper,
      Function<? super B, ? extends B2> secondMapper,
      Function<? super C, ? extends C2> thirdMapper) {
    Validation.function().requireMapper(firstMapper, "firstMapper", TUPLE3_CLASS, MAP);
    Validation.function().requireMapper(secondMapper, "secondMapper", TUPLE3_CLASS, MAP);
    Validation.function().requireMapper(thirdMapper, "thirdMapper", TUPLE3_CLASS, MAP);

    return new Tuple3<>(firstMapper.apply(_1), secondMapper.apply(_2), thirdMapper.apply(_3));
  }

  /**
   * Transforms only the first element of this tuple.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);
   * Tuple3<Integer, Integer, Boolean> result = tuple.mapFirst(String::length);
   * // result = new Tuple3<>(5, 30, true)
   * }</pre>
   *
   * @param firstMapper The non-null function to apply to the first element.
   * @param <A2> The type of the first element in the resulting tuple.
   * @return A new {@code Tuple3<A2, B, C>} with the first element transformed.
   * @throws NullPointerException if {@code firstMapper} is null.
   */
  public <A2> Tuple3<A2, B, C> mapFirst(Function<? super A, ? extends A2> firstMapper) {
    Validation.function().requireMapper(firstMapper, "firstMapper", TUPLE3_CLASS, MAP_FIRST);
    return new Tuple3<>(firstMapper.apply(_1), _2, _3);
  }

  /**
   * Transforms only the second element of this tuple.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);
   * Tuple3<String, String, Boolean> result = tuple.mapSecond(age -> age + " years");
   * // result = new Tuple3<>("Alice", "30 years", true)
   * }</pre>
   *
   * @param secondMapper The non-null function to apply to the second element.
   * @param <B2> The type of the second element in the resulting tuple.
   * @return A new {@code Tuple3<A, B2, C>} with the second element transformed.
   * @throws NullPointerException if {@code secondMapper} is null.
   */
  public <B2> Tuple3<A, B2, C> mapSecond(Function<? super B, ? extends B2> secondMapper) {
    Validation.function().requireMapper(secondMapper, "secondMapper", TUPLE3_CLASS, MAP_SECOND);
    return new Tuple3<>(_1, secondMapper.apply(_2), _3);
  }

  /**
   * Transforms only the third element of this tuple.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);
   * Tuple3<String, Integer, String> result = tuple.mapThird(active -> active ? "yes" : "no");
   * // result = new Tuple3<>("Alice", 30, "yes")
   * }</pre>
   *
   * @param thirdMapper The non-null function to apply to the third element.
   * @param <C2> The type of the third element in the resulting tuple.
   * @return A new {@code Tuple3<A, B, C2>} with the third element transformed.
   * @throws NullPointerException if {@code thirdMapper} is null.
   */
  public <C2> Tuple3<A, B, C2> mapThird(Function<? super C, ? extends C2> thirdMapper) {
    Validation.function().requireMapper(thirdMapper, "thirdMapper", TUPLE3_CLASS, MAP);
    return new Tuple3<>(_1, _2, thirdMapper.apply(_3));
  }
}
