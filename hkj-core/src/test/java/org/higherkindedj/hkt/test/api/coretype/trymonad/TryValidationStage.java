// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.trymonad;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Stage for configuring validation contexts in Try core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * <h2>Purpose</h2>
 *
 * <p>This stage enables precise control over validation error messages when testing Try
 * implementations that use inheritance. By specifying the exact class context for each operation,
 * you ensure that validation failures reference the correct implementing class rather than a base
 * class or interface.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Configure Map Validation Context:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.tryType(Try.class)
 *     .withSuccess(successInstance)
 *     .withFailure(failureInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation(TryMonad.class)
 *         .testValidations();
 * }</pre>
 *
 * <h3>Configure Multiple Operation Contexts:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.tryType(Try.class)
 *     .withSuccess(successInstance)
 *     .withFailure(failureInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .withMapFrom(TryFunctor.class)
 *         .withFlatMapFrom(TryMonad.class)
 *         .testAll();
 * }</pre>
 *
 * <h3>Test Only Validations with Custom Contexts:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.tryType(Try.class)
 *     .withSuccess(successInstance)
 *     .withFailure(failureInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .withMapFrom(CustomTryFunctor.class)
 *         .withFlatMapFrom(CustomTryMonad.class)
 *         .testValidations();
 * }</pre>
 *
 * <h2>When to Use Validation Configuration</h2>
 *
 * <p>Use validation configuration when:
 *
 * <ul>
 *   <li>Your Try implementation uses inheritance hierarchies (e.g., separate Functor and Monad
 *       classes)
 *   <li>You want validation error messages to reference specific implementing classes
 *   <li>Different operations are implemented in different classes
 *   <li>You're testing that null validation occurs at the correct layer of abstraction
 * </ul>
 *
 * @param <T> The value type
 * @param <S> The mapped type
 * @see org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage
 * @see TryTestConfigStage
 * @see TryTestExecutor
 */
public final class TryValidationStage<T, S> extends BaseValidationStage<TryValidationStage<T, S>> {

  private final TryTestConfigStage<T, S> configStage;

  TryValidationStage(TryTestConfigStage<T, S> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected TryValidationStage<T, S> self() {
    return this;
  }

  /**
   * Executes all configured tests with validation contexts applied.
   *
   * <p>This includes operation tests, validation tests with custom contexts, and edge case tests.
   */
  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  /**
   * Executes only validation tests with configured contexts.
   *
   * <p>Use this when you want to focus on testing null parameter validation without running other
   * test categories. The validation tests will use the class contexts you configured via {@link
   * #useInheritanceValidation(Class)} or the fluent methods from {@link BaseValidationStage}.
   *
   * <h3>Example:</h3>
   *
   * <pre>{@code
   * CoreTypeTest.tryType(Try.class)
   *     .withSuccess(successInstance)
   *     .withFailure(failureInstance)
   *     .withMappers(mapper)
   *     .configureValidation()
   *         .withMapFrom(TryFunctor.class)
   *         .withFlatMapFrom(TryMonad.class)
   *         .testValidations();  // Only runs validation tests
   * }</pre>
   */
  @Override
  public void testValidations() {
    buildExecutor().testValidations();
  }

  private TryTestExecutor<T, S> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
