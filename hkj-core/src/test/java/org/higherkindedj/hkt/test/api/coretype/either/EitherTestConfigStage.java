// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The mapped type
 */
public final class EitherTestConfigStage<L, R, S>
    extends BaseTestConfigStage<EitherTestConfigStage<L, R, S>> {

  private final Class<?> contextClass;
  private final Either<L, R> leftInstance;
  private final Either<L, R> rightInstance;
  private final Function<R, S> mapper;

  // Type-specific test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeGetters = true;
  private boolean includeFold = true;
  private boolean includeSideEffects = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;

  EitherTestConfigStage(
      Class<?> contextClass,
      Either<L, R> leftInstance,
      Either<L, R> rightInstance,
      Function<R, S> mapper) {
    this.contextClass = contextClass;
    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // BaseTestConfigStage Implementation
  // =============================================================================

  @Override
  protected EitherTestConfigStage<L, R, S> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public EitherValidationStage<L, R, S> configureValidation() {
    return new EitherValidationStage<>(this);
  }

  @Override
  public EitherTestConfigStage<L, R, S> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public EitherTestConfigStage<L, R, S> onlyEdgeCases() {
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

  public EitherTestConfigStage<L, R, S> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public EitherTestConfigStage<L, R, S> skipGetters() {
    this.includeGetters = false;
    return this;
  }

  public EitherTestConfigStage<L, R, S> skipFold() {
    this.includeFold = false;
    return this;
  }

  public EitherTestConfigStage<L, R, S> skipSideEffects() {
    this.includeSideEffects = false;
    return this;
  }

  public EitherTestConfigStage<L, R, S> skipMap() {
    this.includeMap = false;
    return this;
  }

  public EitherTestConfigStage<L, R, S> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public EitherTestConfigStage<L, R, S> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public EitherTestConfigStage<L, R, S> onlyGetters() {
    disableAll();
    this.includeGetters = true;
    return this;
  }

  public EitherTestConfigStage<L, R, S> onlyFold() {
    disableAll();
    this.includeFold = true;
    return this;
  }

  public EitherTestConfigStage<L, R, S> onlySideEffects() {
    disableAll();
    this.includeSideEffects = true;
    return this;
  }

  public EitherTestConfigStage<L, R, S> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public EitherTestConfigStage<L, R, S> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  /**
   * Configures Selective-specific test operations for Either.
   *
   * <p>Progressive disclosure: Next step is {@code .withHandlers(...)}
   *
   * @param choiceLeft Either containing Choice with Left value
   * @param choiceRight Either containing Choice with Right value
   * @param booleanTrue Either containing true
   * @param booleanFalse Either containing false
   * @param <S> The result type for Selective operations
   * @return Stage for configuring Selective handlers
   */
  public <S> EitherSelectiveStage<L, R, S> withSelectiveOperations(
      Either<L, Choice<R, S>> choiceLeft,
      Either<L, Choice<R, S>> choiceRight,
      Either<L, Boolean> booleanTrue,
      Either<L, Boolean> booleanFalse) {

    return new EitherSelectiveStage<>(
        contextClass,
        leftInstance,
        rightInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse);
  }

  private EitherTestExecutor<L, R, S> buildExecutor() {
    return new EitherTestExecutor<>(
        contextClass,
        leftInstance,
        rightInstance,
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

  EitherTestExecutor<L, R, S> buildExecutorWithValidation(
      EitherValidationStage<L, R, S> validationStage) {
    return new EitherTestExecutor<>(
        contextClass,
        leftInstance,
        rightInstance,
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
