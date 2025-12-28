// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monaderror;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 7 for configuring validation contexts in MonadError tests.
 *
 * @param <F> The MonadError witness type
 * @param <E> The error type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class MonadErrorValidationStage<F extends WitnessArity<TypeArity.Unary>, E, A, B> {
  private final MonadErrorHandlerStage<F, E, A, B> handlerStage;
  private final MonadErrorLawsStage<F, E, A, B> lawsStage;

  // Validation context classes
  private Class<?> mapContext;
  private Class<?> apContext;
  private Class<?> map2Context;
  private Class<?> flatMapContext;
  private Class<?> handleErrorWithContext;

  MonadErrorValidationStage(
      MonadErrorHandlerStage<F, E, A, B> handlerStage, MonadErrorLawsStage<F, E, A, B> lawsStage) {
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
   *         .withMapFrom(EitherFunctor.class)
   *         .withApFrom(EitherMonad.class)
   *         .withFlatMapFrom(EitherMonad.class)
   *         .withHandleErrorWithFrom(EitherMonad.class)
   *     .testAll()
   * }</pre>
   *
   * @return Fluent configuration builder
   */
  public InheritanceValidationBuilder useInheritanceValidation() {
    return new InheritanceValidationBuilder();
  }

  /** Uses default validation (no class context). */
  public MonadErrorValidationStage<F, E, A, B> useDefaultValidation() {
    this.mapContext = null;
    this.apContext = null;
    this.map2Context = null;
    this.flatMapContext = null;
    this.handleErrorWithContext = null;
    return this;
  }

  /** Fluent builder for inheritance-based validation configuration. */
  public final class InheritanceValidationBuilder {

    public InheritanceValidationBuilder withMapFrom(Class<?> contextClass) {
      mapContext = contextClass;
      return this;
    }

    public InheritanceValidationBuilder withApFrom(Class<?> contextClass) {
      apContext = contextClass;
      return this;
    }

    public InheritanceValidationBuilder withMap2From(Class<?> contextClass) {
      map2Context = contextClass;
      return this;
    }

    public InheritanceValidationBuilder withFlatMapFrom(Class<?> contextClass) {
      flatMapContext = contextClass;
      return this;
    }

    public InheritanceValidationBuilder withHandleErrorWithFrom(Class<?> contextClass) {
      handleErrorWithContext = contextClass;
      return this;
    }

    public MonadErrorValidationStage<F, E, A, B> done() {
      return MonadErrorValidationStage.this;
    }

    public MonadErrorTestSelectionStage<F, E, A, B> selectTests() {
      return MonadErrorValidationStage.this.selectTests();
    }

    public void testAll() {
      MonadErrorValidationStage.this.testAll();
    }

    public void testOperationsAndValidations() {
      MonadErrorValidationStage.this.testOperationsAndValidations();
    }

    public void testValidations() {
      MonadErrorValidationStage.this.testValidations();
    }
  }

  public MonadErrorTestSelectionStage<F, E, A, B> selectTests() {
    return new MonadErrorTestSelectionStage<>(handlerStage, lawsStage, this);
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

  Class<?> getApContext() {
    return apContext;
  }

  Class<?> getMap2Context() {
    return map2Context;
  }

  Class<?> getFlatMapContext() {
    return flatMapContext;
  }

  Class<?> getHandleErrorWithContext() {
    return handleErrorWithContext;
  }
}
