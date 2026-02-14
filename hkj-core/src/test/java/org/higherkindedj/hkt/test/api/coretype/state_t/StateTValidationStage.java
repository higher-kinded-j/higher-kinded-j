// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state_t;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTransformerValidationStage;

/**
 * Stage for configuring validation contexts in StateT core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages,
 * supporting inheritance hierarchies.
 *
 * @param <S> The state type
 * @param <F> The outer monad witness type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class StateTValidationStage<S, F extends WitnessArity<TypeArity.Unary>, A, B>
    extends BaseTransformerValidationStage<StateTValidationStage<S, F, A, B>> {

  private final StateTTestConfigStage<S, F, A, B> configStage;

  StateTValidationStage(StateTTestConfigStage<S, F, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected StateTValidationStage<S, F, A, B> self() {
    return this;
  }

  /**
   * Executes all configured tests.
   *
   * <p>Includes all test categories with the configured validation contexts.
   */
  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  /** Executes only validation tests with configured contexts. */
  @Override
  public void testValidations() {
    buildExecutor().testValidations();
  }

  private StateTTestExecutor<S, F, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
