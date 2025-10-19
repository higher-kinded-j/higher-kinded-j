// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either_t;

/**
 * Stage for configuring validation contexts in EitherT core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages,
 * supporting inheritance hierarchies.
 *
 * @param <F> The outer monad witness type
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The mapped type
 */
public final class EitherTValidationStage<F, L, R, S> {
  private final EitherTTestConfigStage<F, L, R, S> configStage;

  // Validation context class
  private Class<?> validationContext;

  EitherTValidationStage(EitherTTestConfigStage<F, L, R, S> configStage) {
    this.configStage = configStage;
  }

  /**
   * Uses inheritance-based validation with fluent configuration.
   *
   * <p>Specifies which implementation class is used for validation messages.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .configureValidation()
   *     .useInheritanceValidation()
   *         .withContextFrom(EitherT.class)
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
  public EitherTValidationStage<F, L, R, S> useDefaultValidation() {
    this.validationContext = null;
    return this;
  }

  /** Fluent builder for inheritance-based validation configuration. */
  public final class InheritanceValidationBuilder {

    /**
     * Specifies the class used for validation error messages.
     *
     * @param contextClass The class that implements the operations
     * @return This builder for chaining
     */
    public InheritanceValidationBuilder withContextFrom(Class<?> contextClass) {
      validationContext = contextClass;
      return this;
    }

    /**
     * Completes inheritance validation configuration.
     *
     * @return The parent validation stage for execution
     */
    public EitherTValidationStage<F, L, R, S> done() {
      return EitherTValidationStage.this;
    }

    /** Executes all configured tests. */
    public void testAll() {
      EitherTValidationStage.this.testAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
      EitherTValidationStage.this.testValidations();
    }
  }

  /**
   * Executes all configured tests.
   *
   * <p>Includes all test categories with the configured validation contexts.
   */
  public void testAll() {
    EitherTTestExecutor<F, L, R, S> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only validation tests with configured contexts. */
  public void testValidations() {
    EitherTTestExecutor<F, L, R, S> executor = buildExecutor();
    executor.testValidations();
  }

  // Package-private getter
  Class<?> getValidationContext() {
    return validationContext;
  }

  private EitherTTestExecutor<F, L, R, S> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
