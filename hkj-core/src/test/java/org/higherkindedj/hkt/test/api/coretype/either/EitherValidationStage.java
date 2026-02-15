// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Stage for configuring validation contexts in Either core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Configure Map Validation:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.either(Either.class)
 *     .withLeft(leftInstance)
 *     .withRight(rightInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(EitherFunctor.class)
 *         .testValidations();
 * }</pre>
 *
 * <h3>Configure Full Monad Hierarchy:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.either(Either.class)
 *     .withLeft(leftInstance)
 *     .withRight(rightInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(EitherFunctor.class)
 *             .withFlatMapFrom(EitherMonad.class)
 *         .testAll();
 * }</pre>
 *
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The mapped type
 */
public final class EitherValidationStage<L, R, S>
    extends BaseValidationStage<EitherValidationStage<L, R, S>> {

  private final EitherTestConfigStage<L, R, S> configStage;

  EitherValidationStage(EitherTestConfigStage<L, R, S> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected EitherValidationStage<L, R, S> self() {
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

  private EitherTestExecutor<L, R, S> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
