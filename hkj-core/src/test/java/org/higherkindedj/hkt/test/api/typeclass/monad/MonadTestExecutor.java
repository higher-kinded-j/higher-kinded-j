// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monad;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.test.api.typeclass.internal.TestMethodRegistry;
import org.higherkindedj.hkt.test.patterns.FlexibleValidationConfig;

/**
 * Internal executor for Monad tests.
 *
 * @param <F> The Monad witness type
 * @param <A> The input type
 * @param <B> The output type
 */
final class MonadTestExecutor<F, A, B> {
  private final Class<?> contextClass;
  private final Monad<F> monad;
  private final Kind<F, A> validKind;
  private final Kind<F, A> validKind2;
  private final Function<A, B> mapper;
  private final Function<A, Kind<F, B>> flatMapper;
  private final Kind<F, Function<A, B>> functionKind;
  private final BiFunction<A, A, B> combiningFunction;
  private final MonadLawsStage<F, A, B> lawsStage;
  private final MonadValidationStage<F, A, B> validationStage;

  private boolean includeOperations = true;
  private boolean includeValidations = true;
  private boolean includeExceptions = true;
  private boolean includeLaws = true;

  MonadTestExecutor(
      Class<?> contextClass,
      Monad<F> monad,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind,
      BiFunction<A, A, B> combiningFunction,
      MonadLawsStage<F, A, B> lawsStage,
      MonadValidationStage<F, A, B> validationStage) {

    this.contextClass = contextClass;
    this.monad = monad;
    this.validKind = validKind;
    this.validKind2 = validKind2;
    this.mapper = mapper;
    this.flatMapper = flatMapper;
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
    TestMethodRegistry.testMonadOperations(monad, validKind, mapper, flatMapper, functionKind);
  }

  void executeValidations() {
    // Always use FlexibleValidationConfig for consistent validation testing
    if (validationStage != null) {
      // Use configured validation contexts
      createFlexibleValidationConfig().test();
    } else {
      // Use default validation (test only directly implemented operations)
      // For Monad, only test map and flatMap (not ap/map2 which are inherited)
      FlexibleValidationConfig.MonadValidation<F, A, B> config =
          new FlexibleValidationConfig.MonadValidation<>(
              monad, validKind, validKind2, mapper, functionKind, combiningFunction, flatMapper);

      // Configure to test map and flatMap with class context (if overridden)
      // but don't test ap/map2 (which are inherited default implementations)
      config.mapWithClassContext(contextClass);
      config.flatMapWithClassContext(contextClass);
      // Don't configure ap or map2 - they won't be tested

      config.test();
    }
  }

  private FlexibleValidationConfig.MonadValidation<F, A, B> createFlexibleValidationConfig() {
    FlexibleValidationConfig.MonadValidation<F, A, B> config =
        new FlexibleValidationConfig.MonadValidation<>(
            monad, validKind, validKind2, mapper, functionKind, combiningFunction, flatMapper);

    if (validationStage.getMapContext() != null) {
      config.mapWithClassContext(validationStage.getMapContext());
    }
    if (validationStage.getApContext() != null) {
      config.apWithClassContext(validationStage.getApContext());
    }
    if (validationStage.getFlatMapContext() != null) {
      config.flatMapWithClassContext(validationStage.getFlatMapContext());
    }

    return config;
  }

  void executeExceptions() {
    TestMethodRegistry.testMonadExceptionPropagation(monad, validKind);
  }

  void executeLaws() {
    if (lawsStage == null) {
      throw new IllegalStateException(
          "Cannot execute laws without law configuration. "
              + "Use .withLawsTesting() to configure laws.");
    }

    TestMethodRegistry.testMonadLaws(
        monad,
        validKind,
        lawsStage.getTestValue(),
        lawsStage.getTestFunction(),
        lawsStage.getChainFunction(),
        lawsStage.getEqualityChecker());
  }

  void executeSelected() {
    executeAll();
  }
}
