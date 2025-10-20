// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.trymonad;

/**
 * Stage for configuring validation contexts in Try core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Configure Map Validation:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.tryType(Try.class)
 *     .withSuccess(successInstance)
 *     .withFailure(failureInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(TryFunctor.class)
 *         .testValidations();
 * }</pre>
 *
 * <h3>Configure Full Monad Hierarchy:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.tryType(Try.class)
 *     .withSuccess(successInstance)
 *     .withFailure(failureInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(TryFunctor.class)
 *             .withFlatMapFrom(TryMonad.class)
 *         .testAll();
 * }</pre>
 *
 * @param <T> The value type
 * @param <S> The mapped type
 */
public final class TryValidationStage<T, S> {
  private final TryTestConfigStage<T, S> configStage;

  // Validation context classes
  private Class<?> mapContext;
  private Class<?> flatMapContext;

  TryValidationStage(TryTestConfigStage<T, S> configStage) {
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
   *         .withMapFrom(TryFunctor.class)
   *         .withFlatMapFrom(TryMonad.class)
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
  public TryValidationStage<T, S> useDefaultValidation() {
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
     * @param contextClass The class that implements map (e.g., TryFunctor.class)
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
     * @param contextClass The class that implements flatMap (e.g., TryMonad.class)
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
    public TryValidationStage<T, S> done() {
      return TryValidationStage.this;
    }

    /**
     * Executes all configured tests.
     *
     * <p>Includes all test categories with the configured validation contexts.
     */
    public void testAll() {
      TryValidationStage.this.testAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
      TryValidationStage.this.testValidations();
    }
  }

  /**
   * Executes all configured tests.
   *
   * <p>Includes all test categories with the configured validation contexts.
   */
  public void testAll() {
    TryTestExecutor<T, S> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only validation tests with configured contexts. */
  public void testValidations() {
    // Create executor with only validations enabled
    TryTestExecutor<T, S> executor = buildExecutor();
    executor.testValidations();
  }

  // Package-private getters
  Class<?> getMapContext() {
    return mapContext;
  }

  Class<?> getFlatMapContext() {
    return flatMapContext;
  }

  private TryTestExecutor<T, S> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
