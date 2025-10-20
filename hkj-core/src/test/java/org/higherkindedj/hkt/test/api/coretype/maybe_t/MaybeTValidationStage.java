// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe_t;

import org.higherkindedj.hkt.test.api.coretype.common.BaseTransformerValidationStage;

/**
 * Stage for configuring validation contexts in MaybeT core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages,
 * supporting inheritance hierarchies.
 *
 * @param <F> The outer monad witness type
 * @param <A> The type of the value potentially held by the inner Maybe
 * @param <B> The mapped type
 */
public final class MaybeTValidationStage<F, A, B>
    extends BaseTransformerValidationStage<MaybeTValidationStage<F, A, B>> {

  private final MaybeTTestConfigStage<F, A, B> configStage;

  MaybeTValidationStage(MaybeTTestConfigStage<F, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected MaybeTValidationStage<F, A, B> self() {
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

  private MaybeTTestExecutor<F, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
