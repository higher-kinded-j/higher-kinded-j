// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader_t;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTransformerValidationStage;

/**
 * Stage for configuring validation contexts in ReaderT core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages,
 * supporting inheritance hierarchies.
 *
 * @param <F> The outer monad witness type
 * @param <R> The environment type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ReaderTValidationStage<F extends WitnessArity<TypeArity.Unary>, R, A, B>
    extends BaseTransformerValidationStage<ReaderTValidationStage<F, R, A, B>> {

  private final ReaderTTestConfigStage<F, R, A, B> configStage;

  ReaderTValidationStage(ReaderTTestConfigStage<F, R, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected ReaderTValidationStage<F, R, A, B> self() {
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

  private ReaderTTestExecutor<F, R, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
