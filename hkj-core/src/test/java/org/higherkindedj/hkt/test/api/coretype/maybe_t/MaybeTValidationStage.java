// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe_t;

/**
 * Stage for configuring validation contexts in MaybeT core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages,
 * supporting inheritance hierarchies.
 *
 * @param <F> The outer monad witness type
 * @param <A> The type of the value potentially held by the inner Maybe
 * @param <B> The mapped type
 */
public final class MaybeTValidationStage<F, A, B> {
  private final MaybeTTestConfigStage<F, A, B> configStage;

  // Validation context class
  private Class<?> validationContext;

  MaybeTValidationStage(MaybeTTestConfigStage<F, A, B> configStage) {
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
   *         .withContextFrom(MaybeT.class)
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
  public MaybeTValidationStage<F, A, B> useDefaultValidation() {
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
    public MaybeTValidationStage<F, A, B> done() {
      return MaybeTValidationStage.this;
    }

    /** Executes all configured tests. */
    public void testAll() {
      MaybeTValidationStage.this.testAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
      MaybeTValidationStage.this.testValidations();
    }
  }

  /**
   * Executes all configured tests.
   *
   * <p>Includes all test categories with the configured validation contexts.
   */
  public void testAll() {
    MaybeTTestExecutor<F, A, B> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only validation tests with configured contexts. */
  public void testValidations() {
    MaybeTTestExecutor<F, A, B> executor = buildExecutor();
    executor.testValidations();
  }

  // Package-private getter
  Class<?> getValidationContext() {
    return validationContext;
  }

  private MaybeTTestExecutor<F, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
