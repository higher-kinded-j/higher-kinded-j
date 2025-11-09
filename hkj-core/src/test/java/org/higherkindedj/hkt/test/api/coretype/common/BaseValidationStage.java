// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.common;

/**
 * Base validation stage providing common validation configuration patterns.
 *
 * <p>This abstract class eliminates duplication across all core type validation stages by
 * providing:
 *
 * <ul>
 *   <li>Common validation context management (map, flatMap)
 *   <li>Fluent inheritance validation builder
 *   <li>Default validation configuration
 *   <li>Standard accessor methods
 * </ul>
 *
 * <h2>Usage Pattern:</h2>
 *
 * <pre>{@code
 * public final class MyTypeValidationStage<T, R>
 *     extends BaseValidationStage<MyTypeValidationStage<T, R>> {
 *
 *     private final MyTypeTestConfigStage<T, R> configStage;
 *
 *     @Override
 *     protected MyTypeValidationStage<T, R> self() {
 *         return this;
 *     }
 *
 *     @Override
 *     public void testAll() {
 *         buildExecutor().executeAll();
 *     }
 *
 *     @Override
 *     public void testValidations() {
 *         buildExecutor().testValidations();
 *     }
 *
 *     private MyTypeTestExecutor<T, R> buildExecutor() {
 *         return configStage.buildExecutorWithValidation(this);
 *     }
 * }
 * }</pre>
 *
 * @param <SELF> The concrete validation stage type for fluent method chaining
 */
public abstract class BaseValidationStage<SELF extends BaseValidationStage<SELF>> {

  // Validation context classes - protected for subclass access
  protected Class<?> mapContext;
  protected Class<?> flatMapContext;

  /**
   * Returns the concrete type instance for fluent method chaining.
   *
   * <p>Subclasses must implement this to return {@code this} cast to their concrete type.
   *
   * @return The concrete validation stage instance
   */
  protected abstract SELF self();

  /**
   * Executes all configured tests.
   *
   * <p>Subclasses implement this to delegate to their specific executor with all test categories
   * enabled.
   */
  public abstract void testAll();

  /**
   * Executes only validation tests with configured contexts.
   *
   * <p>Subclasses implement this to delegate to their specific executor with only validation tests
   * enabled.
   */
  public abstract void testValidations();

  // =============================================================================
  // Validation Configuration Methods
  // =============================================================================

  /**
   * Uses inheritance-based validation with fluent configuration.
   *
   * <p>This allows specifying which class in the inheritance hierarchy should be used in validation
   * error messages for each operation.
   *
   * <h2>Usage Example:</h2>
   *
   * <pre>{@code
   * .configureValidation()
   *     .useInheritanceValidation()
   *         .withMapFrom(MyTypeFunctor.class)
   *         .withFlatMapFrom(MyTypeMonad.class)
   *     .testAll()
   * }</pre>
   *
   * @return Fluent configuration builder
   */
  public InheritanceValidationBuilder useInheritanceValidation() {
    return new InheritanceValidationBuilder();
  }

  /**
   * Uses default validation (no class context).
   *
   * <p>Error messages will not include specific class names.
   *
   * @return This stage for further configuration or execution
   */
  public SELF useDefaultValidation() {
    this.mapContext = null;
    this.flatMapContext = null;
    return self();
  }

  // =============================================================================
  // Fluent Builder for Inheritance-Based Validation
  // =============================================================================

  /**
   * Fluent builder for configuring inheritance-based validation contexts.
   *
   * <p>This inner class provides a clear, type-safe way to configure which implementation classes
   * should be referenced in validation error messages.
   */
  public class InheritanceValidationBuilder {

    /**
     * Specifies the class used for map operation validation.
     *
     * <p>Error messages for map null validations will reference this class.
     *
     * @param contextClass The class that implements map (e.g., MyTypeFunctor.class)
     * @return This builder for chaining
     */
    public InheritanceValidationBuilder withMapFrom(Class<?> contextClass) {
      mapContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for flatMap operation validation.
     *
     * <p>Error messages for flatMap null validations will reference this class.
     *
     * @param contextClass The class that implements flatMap (e.g., MyTypeMonad.class)
     * @return This builder for chaining
     */
    public InheritanceValidationBuilder withFlatMapFrom(Class<?> contextClass) {
      flatMapContext = contextClass;
      return this;
    }

    /**
     * Completes inheritance validation configuration.
     *
     * @return The parent validation stage for execution
     */
    public SELF done() {
      return self();
    }

    /**
     * Executes all configured tests.
     *
     * <p>Convenience method that completes configuration and immediately executes all tests.
     */
    public void testAll() {
      BaseValidationStage.this.testAll();
    }

    /**
     * Executes only validation tests with configured contexts.
     *
     * <p>Convenience method that completes configuration and immediately executes validation tests.
     */
    public void testValidations() {
      BaseValidationStage.this.testValidations();
    }
  }

  // =============================================================================
  // Package-Private Accessors
  // =============================================================================

  /**
   * Gets the configured map context class.
   *
   * @return The map context class, or null if not configured
   */
  public final Class<?> getMapContext() {
    return mapContext;
  }

  /**
   * Gets the configured flatMap context class.
   *
   * @return The flatMap context class, or null if not configured
   */
  public final Class<?> getFlatMapContext() {
    return flatMapContext;
  }
}
