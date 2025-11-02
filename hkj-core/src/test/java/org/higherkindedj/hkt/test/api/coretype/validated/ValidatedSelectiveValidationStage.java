// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveValidationStage;

/**
 * Validation stage for Validated Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * Selective operations.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * CoreTypeTest.validated(Validated.class)
 *     .withInvalid(invalidInstance)
 *     .withValid(validInstance)
 *     .withSelectiveOperations(choiceLeft, choiceRight, booleanTrue, booleanFalse)
 *     .withHandlers(selectFunc, leftHandler, rightHandler)
 *     .configureValidation()
 *         .useSelectiveInheritanceValidation()
 *             .withSelectFrom(ValidatedSelective.class)
 *             .withBranchFrom(ValidatedSelective.class)
 *             .withWhenSFrom(ValidatedSelective.class)
 *             .withIfSFrom(ValidatedSelective.class)
 *         .testAll();
 * }</pre>
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The result type
 */
public final class ValidatedSelectiveValidationStage<E, A, B>
    extends BaseSelectiveValidationStage<ValidatedSelectiveValidationStage<E, A, B>> {

  private final ValidatedSelectiveConfigStage<E, A, B> configStage;

  ValidatedSelectiveValidationStage(ValidatedSelectiveConfigStage<E, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected ValidatedSelectiveValidationStage<E, A, B> self() {
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

  private ValidatedSelectiveTestExecutor<E, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
