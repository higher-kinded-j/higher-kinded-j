// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.common;

import java.util.function.Function;

/**
 * Base test executor providing common functionality for core type tests.
 *
 * <p>This abstract class eliminates duplication across core type test executors by providing:
 *
 * <ul>
 *   <li>Common configuration management (context class, mapper functions)
 *   <li>Test execution coordination with conditional execution based on flags
 *   <li>Validation stage integration
 *   <li>Standard accessor methods
 * </ul>
 *
 * <h2>Usage Pattern:</h2>
 *
 * <pre>{@code
 * final class MyTypeTestExecutor<T, S>
 *     extends BaseCoreTypeTestExecutor<T, S, MyTypeValidationStage<T, S>> {
 *
 *     private final MyType<T> instance;
 *     private final boolean includeMyOperation;
 *
 *     MyTypeTestExecutor(
 *         Class<?> contextClass,
 *         MyType<T> instance,
 *         Function<T, S> mapper,
 *         boolean includeMyOperation,
 *         boolean includeValidations,
 *         boolean includeEdgeCases,
 *         MyTypeValidationStage<T, S> validationStage) {
 *
 *         super(contextClass, mapper, includeValidations, includeEdgeCases, validationStage);
 *         this.instance = instance;
 *         this.includeMyOperation = includeMyOperation;
 *     }
 *
 *     @Override
 *     protected void executeOperationTests() {
 *         if (includeMyOperation) testMyOperation();
 *     }
 *
 *     @Override
 *     protected void executeValidationTests() {
 *         // Implementation using getMapContext(), getFlatMapContext()
 *     }
 *
 *     @Override
 *     protected void executeEdgeCaseTests() {
 *         // Implementation for edge cases
 *     }
 *
 *     private void testMyOperation() {
 *         // Test implementation
 *     }
 * }
 * }</pre>
 *
 * @param <A> The input type
 * @param <B> The mapped type
 * @param <V> The validation stage type
 */
public abstract class BaseCoreTypeTestExecutor<A, B, V extends BaseValidationStage<?>> {

  protected final Class<?> contextClass;
  protected final Function<A, B> mapper;
  protected final boolean includeValidations;
  protected final boolean includeEdgeCases;
  protected final V validationStage;

  /**
   * Constructs a base executor with common configuration.
   *
   * @param contextClass The class context for error messages
   * @param mapper The mapping function (can be null if not needed)
   * @param includeValidations Whether to include validation tests
   * @param includeEdgeCases Whether to include edge case tests
   * @param validationStage Optional validation stage for custom contexts (can be null)
   */
  protected BaseCoreTypeTestExecutor(
      Class<?> contextClass,
      Function<A, B> mapper,
      boolean includeValidations,
      boolean includeEdgeCases,
      V validationStage) {

    this.contextClass = contextClass;
    this.mapper = mapper;
    this.includeValidations = includeValidations;
    this.includeEdgeCases = includeEdgeCases;
    this.validationStage = validationStage;
  }

  /**
   * Executes all configured tests.
   *
   * <p>Subclasses should not override this method. Instead, implement the specific test execution
   * methods.
   */
  public final void executeAll() {
    executeOperationTests();
    if (includeValidations) {
      executeValidationTests();
    }
    if (includeEdgeCases) {
      executeEdgeCaseTests();
    }
  }

  /**
   * Executes operation-specific tests.
   *
   * <p>Subclasses implement this to execute their specific operation tests based on their internal
   * flags.
   */
  protected abstract void executeOperationTests();

  /**
   * Executes validation tests.
   *
   * <p>Subclasses implement this to test null parameter validation, using {@link #getMapContext()}
   * and {@link #getFlatMapContext()} to determine the appropriate validation contexts.
   */
  protected abstract void executeValidationTests();

  /**
   * Executes edge case tests.
   *
   * <p>Subclasses implement this to test edge cases like null values, toString, equals, hashCode,
   * etc.
   */
  protected abstract void executeEdgeCaseTests();

  /**
   * Executes only validation tests with configured contexts.
   *
   * <p>This is a convenience method that delegates to {@link #executeValidationTests()}.
   */
  public final void testValidations() {
    executeValidationTests();
  }

  /**
   * Gets the configured map context class.
   *
   * <p>Returns the validation context for map operations if configured, otherwise returns the
   * default context class.
   *
   * @return The map context class for validation error messages
   */
  protected final Class<?> getMapContext() {
    return (validationStage != null && validationStage.getMapContext() != null)
        ? validationStage.getMapContext()
        : contextClass;
  }

  /**
   * Gets the configured flatMap context class.
   *
   * <p>Returns the validation context for flatMap operations if configured, otherwise returns the
   * default context class.
   *
   * @return The flatMap context class for validation error messages
   */
  protected final Class<?> getFlatMapContext() {
    return (validationStage != null && validationStage.getFlatMapContext() != null)
        ? validationStage.getFlatMapContext()
        : contextClass;
  }

  /**
   * Checks if a mapper function is available.
   *
   * @return true if mapper is not null, false otherwise
   */
  protected final boolean hasMapper() {
    return mapper != null;
  }
}
