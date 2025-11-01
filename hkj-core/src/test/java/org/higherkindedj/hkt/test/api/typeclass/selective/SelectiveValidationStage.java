// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

/**
 * Stage 7 for configuring validation contexts in Selective tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * @param <F> The Selective witness type
 * @param <A> The input type
 * @param <B> The output type
 * @param <C> The result type
 */
public final class SelectiveValidationStage<F, A, B, C> {
  private final SelectiveHandlerStage<F, A, B, C> handlerStage;
  private final SelectiveLawsStage<F, A, B, C> lawsStage;

  // Validation context classes
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
   * <p>Example:
   *
   * <pre>{@code
   * .configureValidation()
   *     .useInheritanceValidation()
   *         .withSelectFrom(EitherSelective.class)
   *         .withBranchFrom(EitherSelective.class)
   *         .withWhenSFrom(Selective.class)
   *         .withIfSFrom(Selective.class)
   *     .testAll()
   * }</pre>
   *
   * @return Fluent configuration builder
   */
  public InheritanceValidationBuilder useInheritanceValidation() {
    return new InheritanceValidationBuilder();
  }

  /** Uses default validation (no class context). */
  public SelectiveValidationStage<F, A, B, C> useDefaultValidation() {
    this.selectContext = null;
    this.branchContext = null;
    this.whenSContext = null;
    this.ifSContext = null;
    return this;
  }

  /** Fluent builder for inheritance-based validation configuration. */
  public final class InheritanceValidationBuilder {

    public InheritanceValidationBuilder withSelectFrom(Class<?> contextClass) {
      selectContext = contextClass;
      return this;
    }

    public InheritanceValidationBuilder withBranchFrom(Class<?> contextClass) {
      branchContext = contextClass;
      return this;
    }

    public InheritanceValidationBuilder withWhenSFrom(Class<?> contextClass) {
      whenSContext = contextClass;
      return this;
    }

    public InheritanceValidationBuilder withIfSFrom(Class<?> contextClass) {
      ifSContext = contextClass;
      return this;
    }

    public SelectiveValidationStage<F, A, B, C> done() {
      return SelectiveValidationStage.this;
    }

    public SelectiveTestSelectionStage<F, A, B, C> selectTests() {
      return SelectiveValidationStage.this.selectTests();
    }

    public void testAll() {
      SelectiveValidationStage.this.testAll();
    }

    public void testOperationsAndValidations() {
      SelectiveValidationStage.this.testOperationsAndValidations();
    }

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
