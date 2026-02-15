// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveValidationStage;

/**
 * Validation stage for Reader Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * Selective operations.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * CoreTypeTest.reader(Reader.class)
 *     .withReader(readerInstance)
 *     .withEnvironment(environment)
 *     .withSelectiveOperations(choiceLeft, choiceRight, booleanTrue, booleanFalse)
 *     .withHandlers(selectFunc, leftHandler, rightHandler)
 *     .configureValidation()
 *         .useSelectiveInheritanceValidation()
 *             .withSelectFrom(ReaderSelective.class)
 *             .withBranchFrom(ReaderSelective.class)
 *             .withWhenSFrom(ReaderSelective.class)
 *             .withIfSFrom(ReaderSelective.class)
 *         .testAll();
 * }</pre>
 *
 * @param <R> The environment type
 * @param <A> The input type
 * @param <B> The result type
 */
public final class ReaderSelectiveValidationStage<R, A, B>
    extends BaseSelectiveValidationStage<ReaderSelectiveValidationStage<R, A, B>> {

  private final ReaderSelectiveConfigStage<R, A, B> configStage;

  ReaderSelectiveValidationStage(ReaderSelectiveConfigStage<R, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected ReaderSelectiveValidationStage<R, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public void testValidations() {
    buildExecutor().testValidations();
  }

  private ReaderSelectiveTestExecutor<R, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
