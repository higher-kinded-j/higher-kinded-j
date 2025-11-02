// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;
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
    extends BaseTestConfigStage<ValidatedSelectiveConfigStage<E, A, B>> {

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

  // Selective-specific test selection flags
  private boolean includeSelect = true;
  private boolean includeBranch = true;
  private boolean includeWhenS = true;
  private boolean includeIfS = true;

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

  @Override
  public ValidatedSelectiveConfigStage<E, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public ValidatedSelectiveConfigStage<E, A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeSelect = false;
    includeBranch = false;
    includeWhenS = false;
    includeIfS = false;
  }

  // Selective-specific test selection
  public ValidatedSelectiveConfigStage<E, A, B> skipSelect() {
    this.includeSelect = false;
    return this;
  }

  public ValidatedSelectiveConfigStage<E, A, B> skipBranch() {
    this.includeBranch = false;
    return this;
  }

  public ValidatedSelectiveConfigStage<E, A, B> skipWhenS() {
    this.includeWhenS = false;
    return this;
  }

  public ValidatedSelectiveConfigStage<E, A, B> skipIfS() {
    this.includeIfS = false;
    return this;
  }

  public ValidatedSelectiveConfigStage<E, A, B> onlySelect() {
    disableAll();
    this.includeSelect = true;
    return this;
  }

  public ValidatedSelectiveConfigStage<E, A, B> onlyBranch() {
    disableAll();
    this.includeBranch = true;
    return this;
  }

  public ValidatedSelectiveConfigStage<E, A, B> onlyWhenS() {
    disableAll();
    this.includeWhenS = true;
    return this;
  }

  public ValidatedSelectiveConfigStage<E, A, B> onlyIfS() {
    disableAll();
    this.includeIfS = true;
    return this;
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
