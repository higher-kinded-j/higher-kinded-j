// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ValidatedTestConfigStage<E, A, B>
    extends BaseTestConfigStage<ValidatedTestConfigStage<E, A, B>> {

  private final Class<?> contextClass;
  private final Validated<E, A> invalidInstance;
  private final Validated<E, A> validInstance;
  private final Function<A, B> mapper;

  // Type-specific test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeGetters = true;
  private boolean includeFold = true;
  private boolean includeSideEffects = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;

  ValidatedTestConfigStage(
      Class<?> contextClass,
      Validated<E, A> invalidInstance,
      Validated<E, A> validInstance,
      Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.invalidInstance = invalidInstance;
    this.validInstance = validInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // BaseTestConfigStage Implementation
  // =============================================================================

  @Override
  protected ValidatedTestConfigStage<E, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public ValidatedValidationStage<E, A, B> configureValidation() {
    return new ValidatedValidationStage<>(this);
  }

  @Override
  public ValidatedTestConfigStage<E, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public ValidatedTestConfigStage<E, A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeFactoryMethods = false;
    includeGetters = false;
    includeFold = false;
    includeSideEffects = false;
    includeMap = false;
    includeFlatMap = false;
  }

  // =============================================================================
  // Type-Specific Test Selection Methods
  // =============================================================================

  public ValidatedTestConfigStage<E, A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipGetters() {
    this.includeGetters = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipFold() {
    this.includeFold = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipSideEffects() {
    this.includeSideEffects = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipMap() {
    this.includeMap = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public ValidatedTestConfigStage<E, A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyGetters() {
    disableAll();
    this.includeGetters = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyFold() {
    disableAll();
    this.includeFold = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlySideEffects() {
    disableAll();
    this.includeSideEffects = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  /**
   * Configures Selective-specific test operations for Validated.
   *
   * <p>Progressive disclosure: Next step is {@code .withHandlers(...)}
   *
   * @param choiceLeft Validated containing Choice with Left value
   * @param choiceRight Validated containing Choice with Right value
   * @param booleanTrue Validated containing true
   * @param booleanFalse Validated containing false
   * @param <R> The result type for Selective operations
   * @return Stage for configuring Selective handlers
   */
  public <R> ValidatedSelectiveStage<E, A, R> withSelectiveOperations(
      Validated<E, Choice<A, R>> choiceLeft,
      Validated<E, Choice<A, R>> choiceRight,
      Validated<E, Boolean> booleanTrue,
      Validated<E, Boolean> booleanFalse) {

    return new ValidatedSelectiveStage<>(
        contextClass,
        invalidInstance,
        validInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse);
  }

  private ValidatedTestExecutor<E, A, B> buildExecutor() {
    return new ValidatedTestExecutor<>(
        contextClass,
        invalidInstance,
        validInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeSideEffects,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases);
  }

  ValidatedTestExecutor<E, A, B> buildExecutorWithValidation(
      ValidatedValidationStage<E, A, B> validationStage) {
    return new ValidatedTestExecutor<>(
        contextClass,
        invalidInstance,
        validInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeSideEffects,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
