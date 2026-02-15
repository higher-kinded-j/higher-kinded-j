// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.io;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveTestConfigStage;

/**
 * Configuration stage for IO Selective tests.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and execution
 * options.
 *
 * @param <A> The input type
 * @param <B> The result type
 */
public final class IOSelectiveConfigStage<A, B>
    extends BaseSelectiveTestConfigStage<IOSelectiveConfigStage<A, B>> {

  private final Class<?> contextClass;
  private final IO<A> ioInstance;
  private final IO<Choice<A, B>> choiceLeft;
  private final IO<Choice<A, B>> choiceRight;
  private final IO<Boolean> booleanTrue;
  private final IO<Boolean> booleanFalse;
  private final Function<A, B> selectFunction;
  private final Function<A, B> leftHandler;
  private final Function<B, B> rightHandler;

  IOSelectiveConfigStage(
      Class<?> contextClass,
      IO<A> ioInstance,
      IO<Choice<A, B>> choiceLeft,
      IO<Choice<A, B>> choiceRight,
      IO<Boolean> booleanTrue,
      IO<Boolean> booleanFalse,
      Function<A, B> selectFunction,
      Function<A, B> leftHandler,
      Function<B, B> rightHandler) {
    this.contextClass = contextClass;
    this.ioInstance = ioInstance;
    this.choiceLeft = choiceLeft;
    this.choiceRight = choiceRight;
    this.booleanTrue = booleanTrue;
    this.booleanFalse = booleanFalse;
    this.selectFunction = selectFunction;
    this.leftHandler = leftHandler;
    this.rightHandler = rightHandler;
  }

  @Override
  protected IOSelectiveConfigStage<A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public IOSelectiveValidationStage<A, B> configureValidation() {
    return new IOSelectiveValidationStage<>(this);
  }

  private IOSelectiveTestExecutor<A, B> buildExecutor() {
    return new IOSelectiveTestExecutor<>(
        contextClass,
        ioInstance,
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

  IOSelectiveTestExecutor<A, B> buildExecutorWithValidation(
      IOSelectiveValidationStage<A, B> validationStage) {
    return new IOSelectiveTestExecutor<>(
        contextClass,
        ioInstance,
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
