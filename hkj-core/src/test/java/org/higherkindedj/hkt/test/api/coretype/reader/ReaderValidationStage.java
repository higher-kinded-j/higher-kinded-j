// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Stage for configuring validation contexts in Reader core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * @param <R> The environment type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ReaderValidationStage<R, A, B>
    extends BaseValidationStage<ReaderValidationStage<R, A, B>> {

  private final ReaderTestConfigStage<R, A, B> configStage;

  ReaderValidationStage(ReaderTestConfigStage<R, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected ReaderValidationStage<R, A, B> self() {
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

  private ReaderTestExecutor<R, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
