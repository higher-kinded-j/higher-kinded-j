// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import org.higherkindedj.hkt.test.api.coretype.common.BaseValidationStage;

/**
 * Validation stage for Maybe Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * Selective operations.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * CoreTypeTest.maybe(Maybe.class)
 *     .withJust(justInstance)
 *     .withNothing(nothingInstance)
 *     .withSelectiveOperations(choiceLeft, choiceRight, booleanTrue, booleanFalse)
 *     .withHandlers(selectFunc, leftHandler, rightHandler)
 *     .configureValidation()
 *         .useInheritanceValidation()
 *             .withSelectFrom(MaybeSelective.class)
 *             .withBranchFrom(MaybeSelective.class)
 *             .withWhenSFrom(MaybeSelective.class)
 *             .withIfSFrom(MaybeSelective.class)
 *         .testAll();
 * }</pre>
 *
 * @param <T> The value type
 * @param <S> The result type
 */
public final class MaybeSelectiveValidationStage<T, S>
    extends BaseValidationStage<MaybeSelectiveValidationStage<T, S>> {

  private final MaybeSelectiveConfigStage<T, S> configStage;

  // Selective-specific validation contexts
  private Class<?> selectContext;
  private Class<?> branchContext;
  private Class<?> whenSContext;
  private Class<?> ifSContext;

  MaybeSelectiveValidationStage(MaybeSelectiveConfigStage<T, S> configStage) {
    this.configStage = configStage;
  }

  @Override
  protected MaybeSelectiveValidationStage<T, S> self() {
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
    public MaybeSelectiveValidationStage<T, S> done() {
      return MaybeSelectiveValidationStage.this;
    }

    @Override
    public void testAll() {
      MaybeSelectiveValidationStage.this.testAll();
    }

    @Override
    public void testValidations() {
      MaybeSelectiveValidationStage.this.testValidations();
    }
  }

  private MaybeSelectiveTestExecutor<T, S> buildExecutor() {
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
