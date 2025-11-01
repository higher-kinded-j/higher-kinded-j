// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.either.Either;

/**
 * Stage for configuring Selective-specific test operations for Either.
 *
 * <p>Progressive disclosure: Shows Selective operation configuration and execution options.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The result type for Selective operations
 */
public final class EitherSelectiveStage<L, R, S> {
  private final Class<?> contextClass;
  private final Either<L, R> leftInstance;
  private final Either<L, R> rightInstance;
  private final Either<L, Choice<R, S>> choiceLeft;
  private final Either<L, Choice<R, S>> choiceRight;
  private final Either<L, Boolean> booleanTrue;
  private final Either<L, Boolean> booleanFalse;

  EitherSelectiveStage(
      Class<?> contextClass,
      Either<L, R> leftInstance,
      Either<L, R> rightInstance,
      Either<L, Choice<R, S>> choiceLeft,
      Either<L, Choice<R, S>> choiceRight,
      Either<L, Boolean> booleanTrue,
      Either<L, Boolean> booleanFalse) {
    this.contextClass = contextClass;
    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
    this.choiceLeft = choiceLeft;
    this.choiceRight = choiceRight;
    this.booleanTrue = booleanTrue;
    this.booleanFalse = booleanFalse;
  }

  /**
   * Provides function handlers for Selective operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param selectFunction Function for select operation (R -> S)
   * @param leftHandler Function for branch left case (R -> S)
   * @param rightHandler Function for branch right case (S -> S)
   * @return Configuration stage with execution options
   */
  public EitherSelectiveConfigStage<L, R, S> withHandlers(
      Function<R, S> selectFunction, Function<R, S> leftHandler, Function<S, S> rightHandler) {
    return new EitherSelectiveConfigStage<>(
        contextClass,
        leftInstance,
        rightInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler);
  }
}
