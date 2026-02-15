// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either_t;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTransformerValidationStage;

/**
 * Stage for configuring validation contexts in EitherT core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages,
 * supporting inheritance hierarchies.
 *
 * @param <F> The outer monad witness type
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The mapped type
 */
public final class EitherTValidationStage<F extends WitnessArity<TypeArity.Unary>, L, R, S>
    extends BaseTransformerValidationStage<EitherTValidationStage<F, L, R, S>> {

  private final EitherTTestConfigStage<F, L, R, S> configStage;

  EitherTValidationStage(EitherTTestConfigStage<F, L, R, S> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected EitherTValidationStage<F, L, R, S> self() {
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

  private EitherTTestExecutor<F, L, R, S> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
