// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.util.validation.Validation;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * An immutable tuple containing five elements of potentially different types.
 *
 * <p>As a {@link java.lang.Record}, it automatically provides a canonical constructor, accessors
 * (e.g., {@code _1()}, {@code _2()}), and implementations for {@code equals()}, {@code hashCode()},
 * and {@code toString()}.
 *
 * <p>This type supports independent transformation of all five elements via {@link #map(Function,
 * Function, Function, Function, Function)}, {@link #mapFirst(Function)}, {@link
 * #mapSecond(Function)}, {@link #mapThird(Function)}, {@link #mapFourth(Function)}, and {@link
 * #mapFifth(Function)}.
 *
 * @param <A> The type of the first element.
 * @param <B> The type of the second element.
 * @param <C> The type of the third element.
 * @param <D> The type of the fourth element.
 * @param <E> The type of the fifth element.
 * @param _1 The first element of the tuple.
 * @param _2 The second element of the tuple.
 * @param _3 The third element of the tuple.
 * @param _4 The fourth element of the tuple.
 * @param _5 The fifth element of the tuple.
 */
@GenerateLenses
public record Tuple5<A, B, C, D, E>(A _1, B _2, C _3, D _4, E _5) implements Tuple {

  private static final Class<Tuple5> TUPLE5_CLASS = Tuple5.class;

  /**
   * Transforms all five elements of this tuple using the provided mapping functions.
   *
   * @param firstMapper The non-null function to apply to the first element.
   * @param secondMapper The non-null function to apply to the second element.
   * @param thirdMapper The non-null function to apply to the third element.
   * @param fourthMapper The non-null function to apply to the fourth element.
   * @param fifthMapper The non-null function to apply to the fifth element.
   * @param <A2> The type of the first element in the resulting tuple.
   * @param <B2> The type of the second element in the resulting tuple.
   * @param <C2> The type of the third element in the resulting tuple.
   * @param <D2> The type of the fourth element in the resulting tuple.
   * @param <E2> The type of the fifth element in the resulting tuple.
   * @return A new {@code Tuple5<A2, B2, C2, D2, E2>} with all elements transformed.
   * @throws NullPointerException if any mapper is null.
   */
  public <A2, B2, C2, D2, E2> Tuple5<A2, B2, C2, D2, E2> map(
      Function<? super A, ? extends A2> firstMapper,
      Function<? super B, ? extends B2> secondMapper,
      Function<? super C, ? extends C2> thirdMapper,
      Function<? super D, ? extends D2> fourthMapper,
      Function<? super E, ? extends E2> fifthMapper) {
    Validation.function().requireMapper(firstMapper, "firstMapper", TUPLE5_CLASS, MAP);
    Validation.function().requireMapper(secondMapper, "secondMapper", TUPLE5_CLASS, MAP);
    Validation.function().requireMapper(thirdMapper, "thirdMapper", TUPLE5_CLASS, MAP);
    Validation.function().requireMapper(fourthMapper, "fourthMapper", TUPLE5_CLASS, MAP);
    Validation.function().requireMapper(fifthMapper, "fifthMapper", TUPLE5_CLASS, MAP);

    return new Tuple5<>(
        firstMapper.apply(_1),
        secondMapper.apply(_2),
        thirdMapper.apply(_3),
        fourthMapper.apply(_4),
        fifthMapper.apply(_5));
  }

  /**
   * Transforms only the first element of this tuple.
   *
   * @param firstMapper The non-null function to apply to the first element.
   * @param <A2> The type of the first element in the resulting tuple.
   * @return A new {@code Tuple5<A2, B, C, D, E>} with the first element transformed.
   * @throws NullPointerException if {@code firstMapper} is null.
   */
  public <A2> Tuple5<A2, B, C, D, E> mapFirst(Function<? super A, ? extends A2> firstMapper) {
    Validation.function().requireMapper(firstMapper, "firstMapper", TUPLE5_CLASS, MAP_FIRST);
    return new Tuple5<>(firstMapper.apply(_1), _2, _3, _4, _5);
  }

  /**
   * Transforms only the second element of this tuple.
   *
   * @param secondMapper The non-null function to apply to the second element.
   * @param <B2> The type of the second element in the resulting tuple.
   * @return A new {@code Tuple5<A, B2, C, D, E>} with the second element transformed.
   * @throws NullPointerException if {@code secondMapper} is null.
   */
  public <B2> Tuple5<A, B2, C, D, E> mapSecond(Function<? super B, ? extends B2> secondMapper) {
    Validation.function().requireMapper(secondMapper, "secondMapper", TUPLE5_CLASS, MAP_SECOND);
    return new Tuple5<>(_1, secondMapper.apply(_2), _3, _4, _5);
  }

  /**
   * Transforms only the third element of this tuple.
   *
   * @param thirdMapper The non-null function to apply to the third element.
   * @param <C2> The type of the third element in the resulting tuple.
   * @return A new {@code Tuple5<A, B, C2, D, E>} with the third element transformed.
   * @throws NullPointerException if {@code thirdMapper} is null.
   */
  public <C2> Tuple5<A, B, C2, D, E> mapThird(Function<? super C, ? extends C2> thirdMapper) {
    Validation.function().requireMapper(thirdMapper, "thirdMapper", TUPLE5_CLASS, MAP);
    return new Tuple5<>(_1, _2, thirdMapper.apply(_3), _4, _5);
  }

  /**
   * Transforms only the fourth element of this tuple.
   *
   * @param fourthMapper The non-null function to apply to the fourth element.
   * @param <D2> The type of the fourth element in the resulting tuple.
   * @return A new {@code Tuple5<A, B, C, D2, E>} with the fourth element transformed.
   * @throws NullPointerException if {@code fourthMapper} is null.
   */
  public <D2> Tuple5<A, B, C, D2, E> mapFourth(Function<? super D, ? extends D2> fourthMapper) {
    Validation.function().requireMapper(fourthMapper, "fourthMapper", TUPLE5_CLASS, MAP);
    return new Tuple5<>(_1, _2, _3, fourthMapper.apply(_4), _5);
  }

  /**
   * Transforms only the fifth element of this tuple.
   *
   * @param fifthMapper The non-null function to apply to the fifth element.
   * @param <E2> The type of the fifth element in the resulting tuple.
   * @return A new {@code Tuple5<A, B, C, D, E2>} with the fifth element transformed.
   * @throws NullPointerException if {@code fifthMapper} is null.
   */
  public <E2> Tuple5<A, B, C, D, E2> mapFifth(Function<? super E, ? extends E2> fifthMapper) {
    Validation.function().requireMapper(fifthMapper, "fifthMapper", TUPLE5_CLASS, MAP);
    return new Tuple5<>(_1, _2, _3, _4, fifthMapper.apply(_5));
  }
}
