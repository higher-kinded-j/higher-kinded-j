// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 7 for configuring validation contexts in Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 */
public final class SelectiveValidationStage<F extends WitnessArity<TypeArity.Unary>, A, B, C> {
  private final SelectiveHandlerStage<F, A, B, C> handlerStage;
  private final SelectiveLawsStage<F, A, B, C> lawsStage;

  // Validation context classes for inherited operations
  private Class<?> mapContext;
  private Class<?> flatMapContext;
  private Class<?> apContext;

  // Validation context classes for Selective-specific operations
  private Class<?> selectContext;
  private Class<?> branchContext;
  private Class<?> whenSContext;
  private Class<?> ifSContext;

  SelectiveValidationStage(
      SelectiveHandlerStage<F, A, B, C> handlerStage, SelectiveLawsStage<F, A, B, C> lawsStage) {
    this.handlerStage = handlerStage;
    this.lawsStage = lawsStage;
  }

  /**
   * Uses inheritance-based validation with fluent configuration.
   *
   * <p>This allows configuring both inherited operations (map, ap) and Selective-specific
   * operations (select, branch, whenS, ifS).
   *
   * <p>Example:
   *
   * <pre>{@code
   * .configureValidation()
   *     .useInheritanceValidation()
   *         .withMapFrom(EitherFunctor.class)
   *         .withApFrom(EitherMonad.class)
   *         .withSelectFrom(EitherSelective.class)
   *         .withBranchFrom(EitherSelective.class)
   *         .withWhenSFrom(EitherSelective.class)
   *         .withIfSFrom(EitherSelective.class)
   *     .testAll()
   * }</pre>
   *
   * @return Fluent configuration builder
   */
  public CombinedInheritanceBuilder useInheritanceValidation() {
    return new CombinedInheritanceBuilder();
  }

  /** Uses default validation (no class context). */
  public SelectiveValidationStage<F, A, B, C> useDefaultValidation() {
    this.mapContext = null;
    this.flatMapContext = null;
    this.apContext = null;
    this.selectContext = null;
    this.branchContext = null;
    this.whenSContext = null;
    this.ifSContext = null;
    return this;
  }

  /**
   * Combined builder that supports both base operations (map, ap, flatMap) and Selective-specific
   * operations (select, branch, whenS, ifS).
   */
  public final class CombinedInheritanceBuilder {

    /**
     * Specifies the class used for map operation validation.
     *
     * @param contextClass The class that implements map (e.g., EitherFunctor.class)
     * @return This builder for chaining
     */
    public CombinedInheritanceBuilder withMapFrom(Class<?> contextClass) {
      mapContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for flatMap operation validation.
     *
     * @param contextClass The class that implements flatMap (e.g., EitherMonad.class)
     * @return This builder for chaining
     */
    public CombinedInheritanceBuilder withFlatMapFrom(Class<?> contextClass) {
      flatMapContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for ap operation validation.
     *
     * @param contextClass The class that implements ap (e.g., EitherMonad.class)
     * @return This builder for chaining
     */
    public CombinedInheritanceBuilder withApFrom(Class<?> contextClass) {
      apContext = contextClass;
      return this;
    }

    /**
     * Specifies the class for select operation validation.
     *
     * @param contextClass The class implementing select
     * @return This builder for chaining
     */
    public CombinedInheritanceBuilder withSelectFrom(Class<?> contextClass) {
      selectContext = contextClass;
      return this;
    }

    /**
     * Specifies the class for branch operation validation.
     *
     * @param contextClass The class implementing branch
     * @return This builder for chaining
     */
    public CombinedInheritanceBuilder withBranchFrom(Class<?> contextClass) {
      branchContext = contextClass;
      return this;
    }

    /**
     * Specifies the class for whenS operation validation.
     *
     * @param contextClass The class implementing whenS
     * @return This builder for chaining
     */
    public CombinedInheritanceBuilder withWhenSFrom(Class<?> contextClass) {
      whenSContext = contextClass;
      return this;
    }

    /**
     * Specifies the class for ifS operation validation.
     *
     * @param contextClass The class implementing ifS
     * @return This builder for chaining
     */
    public CombinedInheritanceBuilder withIfSFrom(Class<?> contextClass) {
      ifSContext = contextClass;
      return this;
    }

    /**
     * Completes inheritance validation configuration.
     *
     * @return The parent validation stage for execution
     */
    public SelectiveValidationStage<F, A, B, C> done() {
      return SelectiveValidationStage.this;
    }

    /**
     * Executes all configured tests.
     *
     * <p>Convenience method that completes configuration and immediately executes all tests.
     */
    public void testAll() {
      SelectiveValidationStage.this.testAll();
    }

    /**
     * Executes only validation tests with configured contexts.
     *
     * <p>Convenience method that completes configuration and immediately executes validation tests.
     */
    public void testValidations() {
      SelectiveValidationStage.this.testValidations();
    }
  }

  public SelectiveTestSelectionStage<F, A, B, C> selectTests() {
    return new SelectiveTestSelectionStage<>(handlerStage, lawsStage, this);
  }

  public void testAll() {
    if (lawsStage != null) {
      handlerStage.build(lawsStage, this).executeAll();
    } else {
      handlerStage.build(null, this).executeOperationsAndValidations();
    }
  }

  public void testOperationsAndValidations() {
    handlerStage.build(lawsStage, this).executeOperationsAndValidations();
  }

  public void testValidations() {
    handlerStage.build(lawsStage, this).executeValidations();
  }

  // Package-private getters
  Class<?> getMapContext() {
    return mapContext;
  }

  Class<?> getFlatMapContext() {
    return flatMapContext;
  }

  Class<?> getApContext() {
    return apContext;
  }

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
