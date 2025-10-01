// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.monaderror;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.test.api.internal.TestMethodRegistry;
import org.higherkindedj.hkt.test.patterns.FlexibleValidationConfig;

/**
 * Internal executor for MonadError tests.
 *
 * <p>This class is package-private and not exposed to users. It coordinates test execution by
 * delegating to {@link TestMethodRegistry}.
 *
 * @param <F> The MonadError witness type
 * @param <E> The error type
 * @param <A> The input type
 * @param <B> The mapped type
 */
final class MonadErrorTestExecutor<F, E, A, B> {
  private final Class<?> contextClass;
  private final MonadError<F, E> monadError;
  private final Kind<F, A> validKind;
  private final Function<A, B> mapper;
  private final Function<A, Kind<F, B>> flatMapper;
  private final Kind<F, Function<A, B>> functionKind;
  private final Function<E, Kind<F, A>> handler;
  private final Kind<F, A> fallback;

  // Optional law testing
  private final MonadErrorLawsStage<F, E, A, B> lawsStage;

  // Optional validation configuration
  private final MonadErrorValidationStage<F, E, A, B> validationStage;

  // Test selection flags
  private boolean includeOperations = true;
  private boolean includeValidations = true;
  private boolean includeExceptions = true;
  private boolean includeLaws = true;

  MonadErrorTestExecutor(
      Class<?> contextClass,
      MonadError<F, E> monadError,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind,
      Function<E, Kind<F, A>> handler,
      Kind<F, A> fallback,
      MonadErrorLawsStage<F, E, A, B> lawsStage,
      MonadErrorValidationStage<F, E, A, B> validationStage) {

    this.contextClass = contextClass;
    this.monadError = monadError;
    this.validKind = validKind;
    this.mapper = mapper;
    this.flatMapper = flatMapper;
    this.functionKind = functionKind;
    this.handler = handler;
    this.fallback = fallback;
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
    TestMethodRegistry.testMonadErrorOperations(
        monadError, validKind, mapper, flatMapper, functionKind, handler, fallback);
  }

  void executeValidations() {
    if (validationStage != null) {
      // Use custom validation configuration
      createFlexibleValidationConfig().test();
    } else {
      // Use standard validation
      TestMethodRegistry.testMonadErrorValidations(
          monadError, contextClass, validKind, mapper, flatMapper, functionKind, handler, fallback);
    }
  }

  void executeExceptions() {
    TestMethodRegistry.testMonadErrorExceptionPropagation(monadError, validKind);
  }

  void executeLaws() {
    if (lawsStage == null) {
      throw new IllegalStateException(
          "Cannot execute laws without law configuration. "
              + "Use .withLawsTesting() to configure laws.");
    }

    TestMethodRegistry.testMonadLaws(
        monadError,
        validKind,
        lawsStage.getTestValue(),
        lawsStage.getTestFunction(),
        lawsStage.getChainFunction(),
        lawsStage.getEqualityChecker());
  }

  void executeSelected() {
    executeAll();
  }

  private FlexibleValidationConfig.MonadErrorValidation<F, E, A, B>
      createFlexibleValidationConfig() {

    // Create base validation config
    FlexibleValidationConfig.MonadErrorValidation<F, E, A, B> config =
        new FlexibleValidationConfig.MonadErrorValidation<>(
            monadError,
            validKind,
            validKind, // validKind2 - using same for now
            mapper,
            functionKind,
            (a1, a2) -> mapper.apply(a1), // combining function
            flatMapper,
            handler,
            fallback);

    // Apply validation contexts if configured
    if (validationStage != null) {
      if (validationStage.getMapContext() != null) {
        config.mapWithClassContext(validationStage.getMapContext());
      }
      if (validationStage.getApContext() != null) {
        config.apWithClassContext(validationStage.getApContext());
      }
      if (validationStage.getFlatMapContext() != null) {
        config.flatMapWithClassContext(validationStage.getFlatMapContext());
      }
      if (validationStage.getHandleErrorWithContext() != null) {
        config.handleErrorWithClassContext(validationStage.getHandleErrorWithContext());
      }
    }

    return config;
  }
}
