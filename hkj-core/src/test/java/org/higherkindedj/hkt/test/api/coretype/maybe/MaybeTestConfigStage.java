// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <T> The value type
 * @param <S> The mapped type
 */
public final class MaybeTestConfigStage<T, S>
    extends BaseTestConfigStage<MaybeTestConfigStage<T, S>> {

  private final Class<?> contextClass;
  private final Maybe<T> justInstance;
  private final Maybe<T> nothingInstance;
  private final Function<T, S> mapper;

  // Type-specific test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeGetters = true;
  private boolean includeOrElse = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;

  MaybeTestConfigStage(
      Class<?> contextClass,
      Maybe<T> justInstance,
      Maybe<T> nothingInstance,
      Function<T, S> mapper) {
    this.contextClass = contextClass;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // BaseTestConfigStage Implementation
  // =============================================================================

  @Override
  protected MaybeTestConfigStage<T, S> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public MaybeValidationStage<T, S> configureValidation() {
    return new MaybeValidationStage<>(this);
  }

  @Override
  public MaybeTestConfigStage<T, S> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public MaybeTestConfigStage<T, S> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeFactoryMethods = false;
    includeGetters = false;
    includeOrElse = false;
    includeMap = false;
    includeFlatMap = false;
  }

  // =============================================================================
  // Type-Specific Test Selection Methods
  // =============================================================================

  public MaybeTestConfigStage<T, S> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipGetters() {
    this.includeGetters = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipOrElse() {
    this.includeOrElse = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipMap() {
    this.includeMap = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public MaybeTestConfigStage<T, S> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyGetters() {
    disableAll();
    this.includeGetters = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyOrElse() {
    disableAll();
    this.includeOrElse = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  /**
   * Configures Selective-specific test operations for Maybe.
   *
   * <p>Progressive disclosure: Next step is {@code .withHandlers(...)}
   *
   * @param choiceLeft Maybe containing Choice with Left value
   * @param choiceRight Maybe containing Choice with Right value
   * @param booleanTrue Maybe containing true
   * @param booleanFalse Maybe containing false
   * @param <R> The result type for Selective operations
   * @return Stage for configuring Selective handlers
   */
  public <R> MaybeSelectiveStage<T, R> withSelectiveOperations(
      Maybe<Choice<T, R>> choiceLeft,
      Maybe<Choice<T, R>> choiceRight,
      Maybe<Boolean> booleanTrue,
      Maybe<Boolean> booleanFalse) {

    return new MaybeSelectiveStage<>(
        contextClass,
        justInstance,
        nothingInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse);
  }

  private MaybeTestExecutor<T, S> buildExecutor() {
    return new MaybeTestExecutor<>(
        contextClass,
        justInstance,
        nothingInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeOrElse,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        null);
  }

  MaybeTestExecutor<T, S> buildExecutorWithValidation(MaybeValidationStage<T, S> validationStage) {
    return new MaybeTestExecutor<>(
        contextClass,
        justInstance,
        nothingInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeOrElse,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
