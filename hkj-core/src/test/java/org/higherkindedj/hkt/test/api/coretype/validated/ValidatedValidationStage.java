// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

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
 *         .useExtendedInheritanceValidation()
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
 *         .useExtendedInheritanceValidation()
 *             .withMapFrom(ValidatedMonad.class)
 *             .withFlatMapFrom(ValidatedMonad.class)
 *         .testAll();
 * }</pre>
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ValidatedValidationStage<E, A, B>
    extends BaseValidationStage<ValidatedValidationStage<E, A, B>> {

  private final ValidatedTestConfigStage<E, A, B> configStage;

  // Additional validation context classes beyond base class
  private Class<?> ifValidContext;
  private Class<?> ifInvalidContext;

  ValidatedValidationStage(ValidatedTestConfigStage<E, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected ValidatedValidationStage<E, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public void testValidations() {
    buildExecutor().testValidations();
  }

  /**
   * Uses inheritance-based validation with fluent configuration.
   *
   * <p>This method provides access to Validated-specific validation contexts including ifValid and
   * ifInvalid operations, in addition to the standard map and flatMap contexts.
   *
   * <p>For Validated types, always use this method instead of the base class's {@code
   * useInheritanceValidation()}, as it provides access to all validation contexts.
   *
   * @return Fluent configuration builder with all Validated validation options
   */
  public ValidatedInheritanceValidationBuilder withValidatedInheritanceValidation() {
    return new ValidatedInheritanceValidationBuilder();
  }

  /**
   * Fluent builder for Validated-specific inheritance-based validation configuration.
   *
   * <p>This builder provides all validation context methods needed for Validated types.
   */
  public final class ValidatedInheritanceValidationBuilder {

    /**
     * Specifies the class used for map operation validation.
     *
     * @param contextClass The class that implements map
     * @return This builder for chaining
     */
    public ValidatedInheritanceValidationBuilder withMapFrom(Class<?> contextClass) {
      mapContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for flatMap operation validation.
     *
     * @param contextClass The class that implements flatMap
     * @return This builder for chaining
     */
    public ValidatedInheritanceValidationBuilder withFlatMapFrom(Class<?> contextClass) {
      flatMapContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for ifValid operation validation.
     *
     * @param contextClass The class that implements ifValid
     * @return This builder for chaining
     */
    public ValidatedInheritanceValidationBuilder withIfValidFrom(Class<?> contextClass) {
      ifValidContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for ifInvalid operation validation.
     *
     * @param contextClass The class that implements ifInvalid
     * @return This builder for chaining
     */
    public ValidatedInheritanceValidationBuilder withIfInvalidFrom(Class<?> contextClass) {
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

    /** Executes all configured tests. */
    public void testAll() {
      ValidatedValidationStage.this.testAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
      ValidatedValidationStage.this.testValidations();
    }
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
