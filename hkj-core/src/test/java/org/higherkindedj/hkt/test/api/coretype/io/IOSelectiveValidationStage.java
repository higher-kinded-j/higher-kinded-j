// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.io;

import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveValidationStage;

/**
 * Validation stage for IO Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * Selective operations.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * CoreTypeTest.io(IO.class)
 *     .withIO(ioInstance)
 *     .withSelectiveOperations(choiceLeft, choiceRight, booleanTrue, booleanFalse)
 *     .withHandlers(selectFunc, leftHandler, rightHandler)
 *     .configureValidation()
 *         .useSelectiveInheritanceValidation()
 *             .withSelectFrom(IOSelective.class)
 *             .withBranchFrom(IOSelective.class)
 *             .withWhenSFrom(IOSelective.class)
 *             .withIfSFrom(IOSelective.class)
 *         .testAll();
 * }</pre>
 *
 * @param <A> The input type
 * @param <B> The result type
 */
public final class IOSelectiveValidationStage<A, B>
    extends BaseSelectiveValidationStage<IOSelectiveValidationStage<A, B>> {

  private final IOSelectiveConfigStage<A, B> configStage;

  IOSelectiveValidationStage(IOSelectiveConfigStage<A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected IOSelectiveValidationStage<A, B> self() {
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

  private IOSelectiveTestExecutor<A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
