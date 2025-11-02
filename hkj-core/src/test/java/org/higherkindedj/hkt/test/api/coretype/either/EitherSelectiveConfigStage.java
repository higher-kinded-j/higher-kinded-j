// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveTestConfigStage;

/**
 * Configuration stage for Either Selective tests.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and execution
 * options.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The result type
 */
public final class EitherSelectiveConfigStage<L, R, S>
    extends BaseSelectiveTestConfigStage<EitherSelectiveConfigStage<L, R, S>> {

  private final Class<?> contextClass;
  private final Either<L, R> leftInstance;
  private final Either<L, R> rightInstance;
  private final Either<L, Choice<R, S>> choiceLeft;
  private final Either<L, Choice<R, S>> choiceRight;
  private final Either<L, Boolean> booleanTrue;
  private final Either<L, Boolean> booleanFalse;
  private final Function<R, S> selectFunction;
  private final Function<R, S> leftHandler;
  private final Function<S, S> rightHandler;

  EitherSelectiveConfigStage(
      Class<?> contextClass,
      Either<L, R> leftInstance,
      Either<L, R> rightInstance,
      Either<L, Choice<R, S>> choiceLeft,
      Either<L, Choice<R, S>> choiceRight,
      Either<L, Boolean> booleanTrue,
      Either<L, Boolean> booleanFalse,
      Function<R, S> selectFunction,
      Function<R, S> leftHandler,
      Function<S, S> rightHandler) {
    this.contextClass = contextClass;
    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
    this.choiceLeft = choiceLeft;
    this.choiceRight = choiceRight;
    this.booleanTrue = booleanTrue;
    this.booleanFalse = booleanFalse;
    this.selectFunction = selectFunction;
    this.leftHandler = leftHandler;
    this.rightHandler = rightHandler;
  }

  @Override
  protected EitherSelectiveConfigStage<L, R, S> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public EitherSelectiveValidationStage<L, R, S> configureValidation() {
    return new EitherSelectiveValidationStage<>(this);
  }

  private EitherSelectiveTestExecutor<L, R, S> buildExecutor() {
    return new EitherSelectiveTestExecutor<>(
        contextClass,
        leftInstance,
        rightInstance,
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

  EitherSelectiveTestExecutor<L, R, S> buildExecutorWithValidation(
      EitherSelectiveValidationStage<L, R, S> validationStage) {
    return new EitherSelectiveTestExecutor<>(
        contextClass,
        leftInstance,
        rightInstance,
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
