// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Stage for configuring Selective-specific test operations for Validated.
 *
 * <p>Progressive disclosure: Shows Selective operation configuration and execution options.
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The result type for Selective operations
 */
public final class ValidatedSelectiveStage<E, A, B> {
  private final Class<?> contextClass;
  private final Validated<E, A> invalidInstance;
  private final Validated<E, A> validInstance;
  private final Validated<E, Choice<A, B>> choiceLeft;
  private final Validated<E, Choice<A, B>> choiceRight;
  private final Validated<E, Boolean> booleanTrue;
  private final Validated<E, Boolean> booleanFalse;

  ValidatedSelectiveStage(
      Class<?> contextClass,
      Validated<E, A> invalidInstance,
      Validated<E, A> validInstance,
      Validated<E, Choice<A, B>> choiceLeft,
      Validated<E, Choice<A, B>> choiceRight,
      Validated<E, Boolean> booleanTrue,
      Validated<E, Boolean> booleanFalse) {
    this.contextClass = contextClass;
    this.invalidInstance = invalidInstance;
    this.validInstance = validInstance;
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
   * @param selectFunction Function for select operation (A -> B)
   * @param leftHandler Function for branch left case (A -> B)
   * @param rightHandler Function for branch right case (B -> B)
   * @return Configuration stage with execution options
   */
  public ValidatedSelectiveConfigStage<E, A, B> withHandlers(
      Function<A, B> selectFunction, Function<A, B> leftHandler, Function<B, B> rightHandler) {
    return new ValidatedSelectiveConfigStage<>(
        contextClass,
        invalidInstance,
        validInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler);
  }
}
