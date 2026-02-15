// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveValidationStage;

/**
 * Validation stage for Either Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * Selective operations.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * CoreTypeTest.either(Either.class)
 *     .withLeft(leftInstance)
 *     .withRight(rightInstance)
 *     .withSelectiveOperations(choiceLeft, choiceRight, booleanTrue, booleanFalse)
 *     .withHandlers(selectFunc, leftHandler, rightHandler)
 *     .configureValidation()
 *         .useSelectiveInheritanceValidation()
 *             .withSelectFrom(EitherSelective.class)
 *             .withBranchFrom(EitherSelective.class)
 *             .withWhenSFrom(EitherSelective.class)
 *             .withIfSFrom(EitherSelective.class)
 *         .testAll();
 * }</pre>
 *
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The result type
 */
public final class EitherSelectiveValidationStage<L, R, S>
    extends BaseSelectiveValidationStage<EitherSelectiveValidationStage<L, R, S>> {

  private final EitherSelectiveConfigStage<L, R, S> configStage;

  EitherSelectiveValidationStage(EitherSelectiveConfigStage<L, R, S> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected EitherSelectiveValidationStage<L, R, S> self() {
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

  private EitherSelectiveTestExecutor<L, R, S> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
