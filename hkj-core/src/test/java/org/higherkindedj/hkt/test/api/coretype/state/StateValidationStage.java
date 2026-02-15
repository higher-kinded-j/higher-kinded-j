// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Stage for configuring validation contexts in State core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * @param <S> The state type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class StateValidationStage<S, A, B>
    extends BaseValidationStage<StateValidationStage<S, A, B>> {

  private final StateTestConfigStage<S, A, B> configStage;

  StateValidationStage(StateTestConfigStage<S, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected StateValidationStage<S, A, B> self() {
    return this;
  }

  /** Executes all configured tests. */
  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  /** Executes only validation tests with configured contexts. */
  @Override
  public void testValidations() {
    buildExecutor().testValidations();
  }

  private StateTestExecutor<S, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
