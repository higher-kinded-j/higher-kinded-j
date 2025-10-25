// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Stage for configuring validation contexts in Maybe core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Configure Map Validation:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.maybe(Maybe.class)
 *     .withJust(justInstance)
 *     .withNothing(nothingInstance)
 *     .withMapper(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(MaybeFunctor.class)
 *         .testValidations();
 * }</pre>
 *
 * <h3>Configure Full Monad Hierarchy:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.maybe(Maybe.class)
 *     .withJust(justInstance)
 *     .withNothing(nothingInstance)
 *     .withMapper(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(MaybeFunctor.class)
 *             .withFlatMapFrom(MaybeMonad.class)
 *         .testAll();
 * }</pre>
 *
 * @param <T> The value type
 * @param <S> The mapped type
 */
public final class MaybeValidationStage<T, S>
    extends BaseValidationStage<MaybeValidationStage<T, S>> {

  private final MaybeTestConfigStage<T, S> configStage;

  MaybeValidationStage(MaybeTestConfigStage<T, S> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected MaybeValidationStage<T, S> self() {
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

  private MaybeTestExecutor<T, S> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
