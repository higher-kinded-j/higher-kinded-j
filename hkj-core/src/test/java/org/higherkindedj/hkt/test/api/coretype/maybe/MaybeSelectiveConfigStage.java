// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveTestConfigStage;

/**
 * Configuration stage for Maybe Selective tests.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and execution
 * options.
 *
 * @param <T> The value type
 * @param <S> The result type
 */
public final class MaybeSelectiveConfigStage<T, S>
    extends BaseSelectiveTestConfigStage<MaybeSelectiveConfigStage<T, S>> {

  private final Class<?> contextClass;
  private final Maybe<T> justInstance;
  private final Maybe<T> nothingInstance;
  private final Maybe<Choice<T, S>> choiceLeft;
  private final Maybe<Choice<T, S>> choiceRight;
  private final Maybe<Boolean> booleanTrue;
  private final Maybe<Boolean> booleanFalse;
  private final Function<T, S> selectFunction;
  private final Function<T, S> leftHandler;
  private final Function<S, S> rightHandler;

  MaybeSelectiveConfigStage(
      Class<?> contextClass,
      Maybe<T> justInstance,
      Maybe<T> nothingInstance,
      Maybe<Choice<T, S>> choiceLeft,
      Maybe<Choice<T, S>> choiceRight,
      Maybe<Boolean> booleanTrue,
      Maybe<Boolean> booleanFalse,
      Function<T, S> selectFunction,
      Function<T, S> leftHandler,
      Function<S, S> rightHandler) {
    this.contextClass = contextClass;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
    this.choiceLeft = choiceLeft;
    this.choiceRight = choiceRight;
    this.booleanTrue = booleanTrue;
    this.booleanFalse = booleanFalse;
    this.selectFunction = selectFunction;
    this.leftHandler = leftHandler;
    this.rightHandler = rightHandler;
  }

  @Override
  protected MaybeSelectiveConfigStage<T, S> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public MaybeSelectiveValidationStage<T, S> configureValidation() {
    return new MaybeSelectiveValidationStage<>(this);
  }

  private MaybeSelectiveTestExecutor<T, S> buildExecutor() {
    return new MaybeSelectiveTestExecutor<>(
        contextClass,
        justInstance,
        nothingInstance,
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

  MaybeSelectiveTestExecutor<T, S> buildExecutorWithValidation(
      MaybeSelectiveValidationStage<T, S> validationStage) {
    return new MaybeSelectiveTestExecutor<>(
        contextClass,
        justInstance,
        nothingInstance,
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
