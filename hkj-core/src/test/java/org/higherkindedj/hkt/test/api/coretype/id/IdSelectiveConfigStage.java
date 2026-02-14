// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveTestConfigStage;

/**
 * Configuration stage for Id Selective tests.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <A> The value type
 * @param <B> The result type
 */
public final class IdSelectiveConfigStage<A, B>
    extends BaseSelectiveTestConfigStage<IdSelectiveConfigStage<A, B>> {

  private final Class<?> contextClass;
  private final Id<A> instance;
  private final Id<Choice<A, B>> choiceLeft;
  private final Id<Choice<A, B>> choiceRight;
  private final Id<Boolean> booleanTrue;
  private final Id<Boolean> booleanFalse;
  private final Function<A, B> selectFunction;
  private final Function<A, B> leftHandler;
  private final Function<B, B> rightHandler;

  IdSelectiveConfigStage(
      Class<?> contextClass,
      Id<A> instance,
      Id<Choice<A, B>> choiceLeft,
      Id<Choice<A, B>> choiceRight,
      Id<Boolean> booleanTrue,
      Id<Boolean> booleanFalse,
      Function<A, B> selectFunction,
      Function<A, B> leftHandler,
      Function<B, B> rightHandler) {
    this.contextClass = contextClass;
    this.instance = instance;
    this.choiceLeft = choiceLeft;
    this.choiceRight = choiceRight;
    this.booleanTrue = booleanTrue;
    this.booleanFalse = booleanFalse;
    this.selectFunction = selectFunction;
    this.leftHandler = leftHandler;
    this.rightHandler = rightHandler;
  }

  @Override
  protected IdSelectiveConfigStage<A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public IdSelectiveValidationStage<A, B> configureValidation() {
    return new IdSelectiveValidationStage<>(this);
  }

  IdSelectiveTestExecutor<A, B> buildExecutor() {
    return new IdSelectiveTestExecutor<>(
        contextClass,
        instance,
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

  IdSelectiveTestExecutor<A, B> buildExecutorWithValidation(
      IdSelectiveValidationStage<A, B> validationStage) {
    return new IdSelectiveTestExecutor<>(
        contextClass,
        instance,
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
