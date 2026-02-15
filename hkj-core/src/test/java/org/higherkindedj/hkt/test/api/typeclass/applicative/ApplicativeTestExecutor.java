// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.applicative;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.test.api.typeclass.internal.TestMethodRegistry;
import org.higherkindedj.hkt.test.patterns.FlexibleValidationConfig;

/**
 * Internal executor for Applicative tests.
 *
 * @param <F> The Applicative witness type
 * @param <A> The input type
 * @param <B> The output type
 */
final class ApplicativeTestExecutor<F extends WitnessArity<TypeArity.Unary>, A, B> {
  private final Class<?> contextClass;
  private final Applicative<F> applicative;
  private final Kind<F, A> validKind;
  private final Kind<F, A> validKind2;
  private final Function<A, B> mapper;
  private final Kind<F, Function<A, B>> functionKind;
  private final BiFunction<A, A, B> combiningFunction;
  private final ApplicativeLawsStage<F, A, B> lawsStage;
  private final ApplicativeValidationStage<F, A, B> validationStage;

  private boolean includeOperations = true;
  private boolean includeValidations = true;
  private boolean includeExceptions = true;
  private boolean includeLaws = true;

  ApplicativeTestExecutor(
      Class<?> contextClass,
      Applicative<F> applicative,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> mapper,
      Kind<F, Function<A, B>> functionKind,
      BiFunction<A, A, B> combiningFunction,
      ApplicativeLawsStage<F, A, B> lawsStage,
      ApplicativeValidationStage<F, A, B> validationStage) {

    this.contextClass = contextClass;
    this.applicative = applicative;
    this.validKind = validKind;
    this.validKind2 = validKind2;
    this.mapper = mapper;
    this.functionKind = functionKind;
    this.combiningFunction = combiningFunction;
    this.lawsStage = lawsStage;
    this.validationStage = validationStage;
  }

  void setTestSelection(boolean operations, boolean validations, boolean exceptions, boolean laws) {
    this.includeOperations = operations;
    this.includeValidations = validations;
    this.includeExceptions = exceptions;
    this.includeLaws = laws;
  }

  void executeAll() {
    if (includeOperations) executeOperations();
    if (includeValidations) executeValidations();
    if (includeExceptions) executeExceptions();
    if (includeLaws && lawsStage != null) executeLaws();
  }

  void executeOperationsAndValidations() {
    if (includeOperations) executeOperations();
    if (includeValidations) executeValidations();
  }

  void executeOperationsAndLaws() {
    if (includeOperations) executeOperations();
    if (includeLaws) executeLaws();
  }

  void executeOperations() {
    TestMethodRegistry.testApplicativeOperations(
        applicative, validKind, validKind2, mapper, functionKind, combiningFunction);
  }

  void executeValidations() {
    if (validationStage != null) {
      createFlexibleValidationConfig().test();
    } else {
      TestMethodRegistry.testApplicativeValidations(
          applicative,
          contextClass,
          validKind,
          validKind2,
          mapper,
          functionKind,
          combiningFunction);
    }
  }

  void executeExceptions() {
    TestMethodRegistry.testApplicativeExceptionPropagation(applicative, validKind);
  }

  void executeLaws() {
    if (lawsStage == null) {
      throw new IllegalStateException(
          "Cannot execute laws without law configuration. "
              + "Use .withLawsTesting() to configure laws.");
    }

    TestMethodRegistry.testApplicativeLaws(
        applicative,
        validKind,
        lawsStage.getTestValue(),
        lawsStage.getTestFunction(),
        lawsStage.getEqualityChecker());
  }

  void executeSelected() {
    executeAll();
  }

  private FlexibleValidationConfig.ApplicativeValidation<F, A, B> createFlexibleValidationConfig() {
    FlexibleValidationConfig.ApplicativeValidation<F, A, B> config =
        new FlexibleValidationConfig.ApplicativeValidation<>(
            applicative, validKind, validKind2, mapper, functionKind, combiningFunction);

    if (validationStage.getMapContext() != null) {
      config.mapWithClassContext(validationStage.getMapContext());
    }
    if (validationStage.getApContext() != null) {
      config.apWithClassContext(validationStage.getApContext());
    }
    if (validationStage.getMap2Context() != null) {
      config.map2WithClassContext(validationStage.getMap2Context());
    }

    return config;
  }
}
