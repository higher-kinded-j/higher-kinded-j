// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Validation stage for Validated Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * Selective operations.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * CoreTypeTest.validated(Validated.class)
 *     .withInvalid(invalidInstance)
 *     .withValid(validInstance)
 *     .withSelectiveOperations(choiceLeft, choiceRight, booleanTrue, booleanFalse)
 *     .withHandlers(selectFunc, leftHandler, rightHandler)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withSelectFrom(ValidatedSelective.class)
 *             .withBranchFrom(ValidatedSelective.class)
 *             .withWhenSFrom(ValidatedSelective.class)
 *             .withIfSFrom(ValidatedSelective.class)
 *         .testAll();
 * }</pre>
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The result type
 */
public final class ValidatedSelectiveValidationStage<E, A, B>
    extends BaseValidationStage<ValidatedSelectiveValidationStage<E, A, B>> {

  private final ValidatedSelectiveConfigStage<E, A, B> configStage;

  // Selective-specific validation contexts
  private Class<?> selectContext;
  private Class<?> branchContext;
  private Class<?> whenSContext;
  private Class<?> ifSContext;

  ValidatedSelectiveValidationStage(ValidatedSelectiveConfigStage<E, A, B> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected ValidatedSelectiveValidationStage<E, A, B> self() {
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
   * Uses inheritance-based validation for Selective operations.
   *
   * @return Fluent builder for Selective validation contexts
   */
  public SelectiveInheritanceBuilder useSelectiveInheritanceValidation() {
    return new SelectiveInheritanceBuilder();
  }

  /** Fluent builder for Selective-specific validation contexts. */
  public final class SelectiveInheritanceBuilder extends InheritanceValidationBuilder {

    /**
     * Specifies the class for select operation validation.
     *
     * @param contextClass The class implementing select
     * @return This builder for chaining
     */
    public SelectiveInheritanceBuilder withSelectFrom(Class<?> contextClass) {
      selectContext = contextClass;
      return this;
    }

    /**
     * Specifies the class for branch operation validation.
     *
     * @param contextClass The class implementing branch
     * @return This builder for chaining
     */
    public SelectiveInheritanceBuilder withBranchFrom(Class<?> contextClass) {
      branchContext = contextClass;
      return this;
    }

    /**
     * Specifies the class for whenS operation validation.
     *
     * @param contextClass The class implementing whenS
     * @return This builder for chaining
     */
    public SelectiveInheritanceBuilder withWhenSFrom(Class<?> contextClass) {
      whenSContext = contextClass;
      return this;
    }

    /**
     * Specifies the class for ifS operation validation.
     *
     * @param contextClass The class implementing ifS
     * @return This builder for chaining
     */
    public SelectiveInheritanceBuilder withIfSFrom(Class<?> contextClass) {
      ifSContext = contextClass;
      return this;
    }

    @Override
    public ValidatedSelectiveValidationStage<E, A, B> done() {
      return ValidatedSelectiveValidationStage.this;
    }

    @Override
    public void testAll() {
      ValidatedSelectiveValidationStage.this.testAll();
    }

    @Override
    public void testValidations() {
      ValidatedSelectiveValidationStage.this.testValidations();
    }
  }

  private ValidatedSelectiveTestExecutor<E, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }

  // Package-private getters
  Class<?> getSelectContext() {
    return selectContext;
  }

  Class<?> getBranchContext() {
    return branchContext;
  }

  Class<?> getWhenSContext() {
    return whenSContext;
  }

  Class<?> getIfSContext() {
    return ifSContext;
  }
}
