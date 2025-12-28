// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTransformerTestConfigStage;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <S> The state type
 * @param <F> The outer monad witness type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class StateTTestConfigStage<S, F extends WitnessArity<TypeArity.Unary>, A, B>
    extends BaseTransformerTestConfigStage<StateTTestConfigStage<S, F, A, B>> {

  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final StateT<S, F, A> firstInstance;
  private final StateT<S, F, A> secondInstance;
  private final Function<A, B> mapper;

  // Additional flag for StateT runner methods
  private boolean includeRunnerMethods = true;

  StateTTestConfigStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      StateT<S, F, A> firstInstance,
      StateT<S, F, A> secondInstance,
      Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.firstInstance = firstInstance;
    this.secondInstance = secondInstance;
    this.mapper = mapper;
  }

  @Override
  protected StateTTestConfigStage<S, F, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public StateTValidationStage<S, F, A, B> configureValidation() {
    return new StateTValidationStage<>(this);
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeRunnerMethods = false;
  }

  // =============================================================================
  // Type-Specific Test Selection Methods
  // =============================================================================

  public StateTTestConfigStage<S, F, A, B> skipRunnerMethods() {
    this.includeRunnerMethods = false;
    return this;
  }

  public StateTTestConfigStage<S, F, A, B> onlyRunnerMethods() {
    disableAll();
    this.includeRunnerMethods = true;
    return this;
  }

  private StateTTestExecutor<S, F, A, B> buildExecutor() {
    return new StateTTestExecutor<>(
        contextClass,
        outerMonad,
        firstInstance,
        secondInstance,
        mapper,
        includeFactoryMethods,
        includeRunnerMethods,
        includeValidations,
        includeEdgeCases);
  }

  StateTTestExecutor<S, F, A, B> buildExecutorWithValidation(
      StateTValidationStage<S, F, A, B> validationStage) {
    return new StateTTestExecutor<>(
        contextClass,
        outerMonad,
        firstInstance,
        secondInstance,
        mapper,
        includeFactoryMethods,
        includeRunnerMethods,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
