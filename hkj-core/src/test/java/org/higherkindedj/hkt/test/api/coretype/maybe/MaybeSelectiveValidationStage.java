// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveValidationStage;

/**
 * Validation stage for Maybe Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * Selective operations.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * CoreTypeTest.maybe(Maybe.class)
 *     .withJust(justInstance)
 *     .withNothing(nothingInstance)
 *     .withSelectiveOperations(choiceLeft, choiceRight, booleanTrue, booleanFalse)
 *     .withHandlers(selectFunc, leftHandler, rightHandler)
 *     .configureValidation()
 *         .useSelectiveInheritanceValidation()
 *             .withSelectFrom(MaybeSelective.class)
 *             .withBranchFrom(MaybeSelective.class)
 *             .withWhenSFrom(MaybeSelective.class)
 *             .withIfSFrom(MaybeSelective.class)
 *         .testAll();
 * }</pre>
 *
 * @param <T> The value type
 * @param <S> The result type
 */
public final class MaybeSelectiveValidationStage<T, S>
    extends BaseSelectiveValidationStage<MaybeSelectiveValidationStage<T, S>> {

  private final MaybeSelectiveConfigStage<T, S> configStage;

  MaybeSelectiveValidationStage(MaybeSelectiveConfigStage<T, S> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected MaybeSelectiveValidationStage<T, S> self() {
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

  private MaybeSelectiveTestExecutor<T, S> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
