// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Stage for configuring validation contexts in Id core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Configure Map Validation:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.id(Id.class)
 *     .withInstance(idInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(IdMonad.class)
 *         .testValidations();
 * }</pre>
 *
 * <h3>Configure Full Monad Hierarchy:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.id(Id.class)
 *     .withInstance(idInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(IdMonad.class)
 *             .withFlatMapFrom(IdMonad.class)
 *         .testAll();
 * }</pre>
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class IdValidationStage<A, B> extends BaseValidationStage<IdValidationStage<A, B>> {

  private final IdTestConfigStage<A, B> configStage;

  IdValidationStage(IdTestConfigStage<A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected IdValidationStage<A, B> self() {
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

  private IdTestExecutor<A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
