// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;

/**
 * Represents a tagged union of two types, either a Left value of type {@code L} or a Right value of
 * type {@code R}.
 *
 * @param <L> the left type
 * @param <R> the right type
 */
public interface Choice<L, R> {

  /**
   * Returns {@code true} if this is a Left value.
   *
   * @return {@code true} if left, {@code false} if right
   */
  boolean isLeft();

  /**
   * Returns {@code true} if this is a Right value.
   *
   * @return {@code true} if right, {@code false} if left
   */
  boolean isRight();

  /**
   * Returns the left value.
   *
   * @return the left value
   */
  L getLeft();

  /**
   * Returns the right value.
   *
   * @return the right value
   */
  R getRight();

  /**
   * Eliminates this Choice by applying the appropriate function.
   *
   * @param <T> the result type
   * @param leftMapper function to apply if this is a Left value
   * @param rightMapper function to apply if this is a Right value
   * @return the result of applying the appropriate function
   */
  <T> T fold(
      Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper);

  /**
   * Maps the right value using the given function.
   *
   * @param <R2> the new right type
   * @param mapper the function to apply to the right value
   * @return a new Choice with the mapped right value
   */
  <R2> Choice<L, R2> map(Function<? super R, ? extends R2> mapper);

  /**
   * Maps the left value using the given function.
   *
   * @param <L2> the new left type
   * @param mapper the function to apply to the left value
   * @return a new Choice with the mapped left value
   */
  <L2> Choice<L2, R> mapLeft(Function<? super L, ? extends L2> mapper);

  /**
   * Swaps the left and right values.
   *
   * @return a new Choice with left and right swapped
   */
  Choice<R, L> swap();

  /**
   * FlatMaps the right value using the given function.
   *
   * @param <R2> the new right type
   * @param mapper the function to apply to the right value
   * @return the result of applying the function
   */
  <R2> Choice<L, R2> flatMap(Function<? super R, ? extends Choice<L, R2>> mapper);
}
