// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.lazy;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Stage for configuring validation contexts in Lazy core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Configure Map Validation:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.lazy(Lazy.class)
 *     .withDeferred(deferredInstance)
 *     .withNow(nowInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(LazyMonad.class)
 *         .testValidations();
 * }</pre>
 *
 * <h3>Configure Full Monad Hierarchy:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.lazy(Lazy.class)
 *     .withDeferred(deferredInstance)
 *     .withNow(nowInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(LazyMonad.class)
 *             .withFlatMapFrom(LazyMonad.class)
 *         .testAll();
 * }</pre>
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class LazyValidationStage<A, B>
    extends BaseValidationStage<LazyValidationStage<A, B>> {

  private final LazyTestConfigStage<A, B> configStage;

  LazyValidationStage(LazyTestConfigStage<A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected LazyValidationStage<A, B> self() {
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

  private LazyTestExecutor<A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
