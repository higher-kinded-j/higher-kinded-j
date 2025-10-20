// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

/**
 * Stage for configuring validation contexts in Validated core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Configure Map Validation:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.validated(Validated.class)
 *     .withInvalid(invalidInstance)
 *     .withValid(validInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(ValidatedMonad.class)
 *         .testValidations();
 * }</pre>
 *
 * <h3>Configure Full Monad Hierarchy:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.validated(Validated.class)
 *     .withInvalid(invalidInstance)
 *     .withValid(validInstance)
 *     .withMappers(mapper)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withMapFrom(ValidatedMonad.class)
 *             .withFlatMapFrom(ValidatedMonad.class)
 *         .testAll();
 * }</pre>
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ValidatedValidationStage<E, A, B> {
  private final ValidatedTestConfigStage<E, A, B> configStage;

  // Validation context classes
  private Class<?> mapContext;
  private Class<?> flatMapContext;
  private Class<?> ifValidContext;
  private Class<?> ifInvalidContext;

  ValidatedValidationStage(ValidatedTestConfigStage<E, A, B> configStage) {
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
   *         .withMapFrom(ValidatedMonad.class)
   *         .withFlatMapFrom(ValidatedMonad.class)
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
  public ValidatedValidationStage<E, A, B> useDefaultValidation() {
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
     * @param contextClass The class that implements map (e.g., ValidatedMonad.class)
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
     * @param contextClass The class that implements flatMap (e.g., ValidatedMonad.class)
     * @return This builder for chaining
     */
    public InheritanceValidationBuilder withFlatMapFrom(Class<?> contextClass) {
      flatMapContext = contextClass;
      return this;
    }

    public InheritanceValidationBuilder withIfValidFrom(Class<?> contextClass) {
      ifValidContext = contextClass;
      return this;
    }

    public InheritanceValidationBuilder withIfInvalidFrom(Class<?> contextClass) {
      ifInvalidContext = contextClass;
      return this;
    }

    /**
     * Completes inheritance validation configuration.
     *
     * @return The parent validation stage for execution
     */
    public ValidatedValidationStage<E, A, B> done() {
      return ValidatedValidationStage.this;
    }

    /**
     * Executes all configured tests.
     *
     * <p>Includes all test categories with the configured validation contexts.
     */
    public void testAll() {
      ValidatedValidationStage.this.testAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
      ValidatedValidationStage.this.testValidations();
    }
  }

  /**
   * Executes all configured tests.
   *
   * <p>Includes all test categories with the configured validation contexts.
   */
  public void testAll() {
    ValidatedTestExecutor<E, A, B> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only validation tests with configured contexts. */
  public void testValidations() {
    // Create executor with only validations enabled
    ValidatedTestExecutor<E, A, B> executor = buildExecutor();
    executor.testValidations();
  }

  // Package-private getters
  Class<?> getMapContext() {
    return mapContext;
  }

  Class<?> getFlatMapContext() {
    return flatMapContext;
  }

  Class<?> getIfValidContext() {
    return ifValidContext;
  }

  Class<?> getIfInvalidContext() {
    return ifInvalidContext;
  }

  private ValidatedTestExecutor<E, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
