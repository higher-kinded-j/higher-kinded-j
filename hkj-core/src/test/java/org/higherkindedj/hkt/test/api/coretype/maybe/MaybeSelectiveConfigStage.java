// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;

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
    extends BaseTestConfigStage<MaybeSelectiveConfigStage<T, S>> {

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

  // Selective-specific test selection flags
  private boolean includeSelect = true;
  private boolean includeBranch = true;
  private boolean includeWhenS = true;
  private boolean includeIfS = true;

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

  @Override
  public MaybeSelectiveConfigStage<T, S> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public MaybeSelectiveConfigStage<T, S> onlyEdgeCases() {
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
  public MaybeSelectiveConfigStage<T, S> skipSelect() {
    this.includeSelect = false;
    return this;
  }

  public MaybeSelectiveConfigStage<T, S> skipBranch() {
    this.includeBranch = false;
    return this;
  }

  public MaybeSelectiveConfigStage<T, S> skipWhenS() {
    this.includeWhenS = false;
    return this;
  }

  public MaybeSelectiveConfigStage<T, S> skipIfS() {
    this.includeIfS = false;
    return this;
  }

  public MaybeSelectiveConfigStage<T, S> onlySelect() {
    disableAll();
    this.includeSelect = true;
    return this;
  }

  public MaybeSelectiveConfigStage<T, S> onlyBranch() {
    disableAll();
    this.includeBranch = true;
    return this;
  }

  public MaybeSelectiveConfigStage<T, S> onlyWhenS() {
    disableAll();
    this.includeWhenS = true;
    return this;
  }

  public MaybeSelectiveConfigStage<T, S> onlyIfS() {
    disableAll();
    this.includeIfS = true;
    return this;
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
