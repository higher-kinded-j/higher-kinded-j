// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveTestConfigStage;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Configuration stage for Validated Selective tests.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and execution
 * options.
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The result type
 */
public final class ValidatedSelectiveConfigStage<E, A, B>
    extends BaseSelectiveTestConfigStage<ValidatedSelectiveConfigStage<E, A, B>> {

  private final Class<?> contextClass;
  private final Validated<E, A> invalidInstance;
  private final Validated<E, A> validInstance;
  private final Validated<E, Choice<A, B>> choiceLeft;
  private final Validated<E, Choice<A, B>> choiceRight;
  private final Validated<E, Boolean> booleanTrue;
  private final Validated<E, Boolean> booleanFalse;
  private final Function<A, B> selectFunction;
  private final Function<A, B> leftHandler;
  private final Function<B, B> rightHandler;

  ValidatedSelectiveConfigStage(
      Class<?> contextClass,
      Validated<E, A> invalidInstance,
      Validated<E, A> validInstance,
      Validated<E, Choice<A, B>> choiceLeft,
      Validated<E, Choice<A, B>> choiceRight,
      Validated<E, Boolean> booleanTrue,
      Validated<E, Boolean> booleanFalse,
      Function<A, B> selectFunction,
      Function<A, B> leftHandler,
      Function<B, B> rightHandler) {
    this.contextClass = contextClass;
    this.invalidInstance = invalidInstance;
    this.validInstance = validInstance;
    this.choiceLeft = choiceLeft;
    this.choiceRight = choiceRight;
    this.booleanTrue = booleanTrue;
    this.booleanFalse = booleanFalse;
    this.selectFunction = selectFunction;
    this.leftHandler = leftHandler;
    this.rightHandler = rightHandler;
  }

  @Override
  protected ValidatedSelectiveConfigStage<E, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public ValidatedSelectiveValidationStage<E, A, B> configureValidation() {
    return new ValidatedSelectiveValidationStage<>(this);
  }

  private ValidatedSelectiveTestExecutor<E, A, B> buildExecutor() {
    return new ValidatedSelectiveTestExecutor<>(
        contextClass,
        invalidInstance,
        validInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler,
        includeSelect,
        includeBranch,
        includeWhenS,
        includeIfS,
        includeValidations,
        includeEdgeCases);
  }

  ValidatedSelectiveTestExecutor<E, A, B> buildExecutorWithValidation(
      ValidatedSelectiveValidationStage<E, A, B> validationStage) {
    return new ValidatedSelectiveTestExecutor<>(
        contextClass,
        invalidInstance,
        validInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler,
        includeSelect,
        includeBranch,
        includeWhenS,
        includeIfS,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
