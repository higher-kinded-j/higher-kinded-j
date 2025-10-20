// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.lazy;

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
public final class LazyValidationStage<A, B> {
  private final LazyTestConfigStage<A, B> configStage;

  // Validation context classes
  private Class<?> mapContext;
  private Class<?> flatMapContext;

  LazyValidationStage(LazyTestConfigStage<A, B> configStage) {
    this.configStage = configStage;
  }

  /**
   * Uses inheritance-based validation with fluent configuration.
   *
   * <p>This allows you to specify which class in the inheritance hierarchy should be used in
   * validation error messages for each operation.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .configureValidation()
   *     .useInheritanceValidation()
   *         .withMapFrom(LazyMonad.class)
   *         .withFlatMapFrom(LazyMonad.class)
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
  public LazyValidationStage<A, B> useDefaultValidation() {
    this.mapContext = null;
    this.flatMapContext = null;
    return this;
  }

  /** Fluent builder for inheritance-based validation configuration. */
  public final class InheritanceValidationBuilder {

    /**
     * Specifies the class used for map operation validation.
     *
     * <p>Error messages for map null validations will reference this class.
     *
     * @param contextClass The class that implements map (e.g., LazyMonad.class)
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
     * @param contextClass The class that implements flatMap (e.g., LazyMonad.class)
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
    public LazyValidationStage<A, B> done() {
      return LazyValidationStage.this;
    }

    /**
     * Executes all configured tests.
     *
     * <p>Includes all test categories with the configured validation contexts.
     */
    public void testAll() {
      LazyValidationStage.this.testAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
      LazyValidationStage.this.testValidations();
    }
  }

  /**
   * Executes all configured tests.
   *
   * <p>Includes all test categories with the configured validation contexts.
   */
  public void testAll() {
    LazyTestExecutor<A, B> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only validation tests with configured contexts. */
  public void testValidations() {
    LazyTestExecutor<A, B> executor = buildExecutor();
    executor.testValidations();
  }

  // Package-private getters
  Class<?> getMapContext() {
    return mapContext;
  }

  Class<?> getFlatMapContext() {
    return flatMapContext;
  }

  private LazyTestExecutor<A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
