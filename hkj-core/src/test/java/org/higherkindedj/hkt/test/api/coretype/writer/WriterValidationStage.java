// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.writer;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Stage for configuring validation contexts in Writer core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * @param <W> The log type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class WriterValidationStage<W, A, B>
    extends BaseValidationStage<WriterValidationStage<W, A, B>> {

  private final WriterTestConfigStage<W, A, B> configStage;

  WriterValidationStage(WriterTestConfigStage<W, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected WriterValidationStage<W, A, B> self() {
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

  private WriterTestExecutor<W, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
