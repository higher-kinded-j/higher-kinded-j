// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state;

import java.util.function.Function;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <S> The state type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class StateTestConfigStage<S, A, B>
    extends BaseTestConfigStage<StateTestConfigStage<S, A, B>> {

  private final Class<?> contextClass;
  private final State<S, A> stateInstance;
  private final S initialState;
  private final Function<A, B> mapper;

  // Type-specific test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeRun = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;

  StateTestConfigStage(
      Class<?> contextClass, State<S, A> stateInstance, S initialState, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.stateInstance = stateInstance;
    this.initialState = initialState;
    this.mapper = mapper;
  }

  // =============================================================================
  // BaseTestConfigStage Implementation
  // =============================================================================

  @Override
  protected StateTestConfigStage<S, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public StateValidationStage<S, A, B> configureValidation() {
    return new StateValidationStage<>(this);
  }

  @Override
  public StateTestConfigStage<S, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public StateTestConfigStage<S, A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeFactoryMethods = false;
    includeRun = false;
    includeMap = false;
    includeFlatMap = false;
  }

  // =============================================================================
  // Type-Specific Test Selection Methods
  // =============================================================================

  public StateTestConfigStage<S, A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public StateTestConfigStage<S, A, B> skipRun() {
    this.includeRun = false;
    return this;
  }

  public StateTestConfigStage<S, A, B> skipMap() {
    this.includeMap = false;
    return this;
  }

  public StateTestConfigStage<S, A, B> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public StateTestConfigStage<S, A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public StateTestConfigStage<S, A, B> onlyRun() {
    disableAll();
    this.includeRun = true;
    return this;
  }

  public StateTestConfigStage<S, A, B> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public StateTestConfigStage<S, A, B> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  private StateTestExecutor<S, A, B> buildExecutor() {
    return new StateTestExecutor<>(
        contextClass,
        stateInstance,
        initialState,
        mapper,
        includeFactoryMethods,
        includeRun,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases);
  }

  StateTestExecutor<S, A, B> buildExecutorWithValidation(
      StateValidationStage<S, A, B> validationStage) {
    return new StateTestExecutor<>(
        contextClass,
        stateInstance,
        initialState,
        mapper,
        includeFactoryMethods,
        includeRun,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
