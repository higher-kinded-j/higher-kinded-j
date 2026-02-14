// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveValidationStage;

/**
 * Validation stage for Id Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * Selective operations.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * CoreTypeTest.id(Id.class)
 *     .withInstance(instance)
 *     .withSelectiveOperations(choiceLeft, choiceRight, booleanTrue, booleanFalse)
 *     .withHandlers(selectFunc, leftHandler, rightHandler)
 *     .configureValidation()
 *         .useSelectiveInheritanceValidation()
 *             .withSelectFrom(IdSelective.class)
 *             .withBranchFrom(IdSelective.class)
 *             .withWhenSFrom(IdSelective.class)
 *             .withIfSFrom(IdSelective.class)
 *         .testAll();
 * }</pre>
 *
 * @param <A> The value type
 * @param <B> The result type
 */
public final class IdSelectiveValidationStage<A, B>
    extends BaseSelectiveValidationStage<IdSelectiveValidationStage<A, B>> {

  private final IdSelectiveConfigStage<A, B> configStage;

  IdSelectiveValidationStage(IdSelectiveConfigStage<A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected IdSelectiveValidationStage<A, B> self() {
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

  private IdSelectiveTestExecutor<A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
