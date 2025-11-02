// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Stage for configuring Selective-specific test operations for Maybe.
 *
 * <p>Progressive disclosure: Shows Selective operation configuration and execution options.
 *
 * @param <T> The value type
 * @param <S> The result type for Selective operations
 */
public final class MaybeSelectiveStage<T, S> {
  private final Class<?> contextClass;
  private final Maybe<T> justInstance;
  private final Maybe<T> nothingInstance;
  private final Maybe<Choice<T, S>> choiceLeft;
  private final Maybe<Choice<T, S>> choiceRight;
  private final Maybe<Boolean> booleanTrue;
  private final Maybe<Boolean> booleanFalse;

  MaybeSelectiveStage(
      Class<?> contextClass,
      Maybe<T> justInstance,
      Maybe<T> nothingInstance,
      Maybe<Choice<T, S>> choiceLeft,
      Maybe<Choice<T, S>> choiceRight,
      Maybe<Boolean> booleanTrue,
      Maybe<Boolean> booleanFalse) {
    this.contextClass = contextClass;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
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
   * @param selectFunction Function for select operation (T -> S)
   * @param leftHandler Function for branch left case (T -> S)
   * @param rightHandler Function for branch right case (S -> S)
   * @return Configuration stage with execution options
   */
  public MaybeSelectiveConfigStage<T, S> withHandlers(
      Function<T, S> selectFunction, Function<T, S> leftHandler, Function<S, S> rightHandler) {
    return new MaybeSelectiveConfigStage<>(
        contextClass,
        justInstance,
        nothingInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler);
  }
}
