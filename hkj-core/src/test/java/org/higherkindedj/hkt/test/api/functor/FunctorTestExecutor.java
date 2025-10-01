// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.functor;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.internal.TestMethodRegistry;

/**
 * Internal executor for Functor tests.
 *
 * <p>This class is package-private and not exposed to users. It coordinates test execution by
 * delegating to {@link TestMethodRegistry}.
 *
 * @param <F> The Functor witness type
 * @param <A> The input type
 * @param <B> The output type
 */
final class FunctorTestExecutor<F, A, B> {
    private final Class<?> contextClass;
    private final Functor<F> functor;
    private final Kind<F, A> validKind;
    private final Function<A, B> mapper;
    private final Function<B, String> secondMapper;
    private final BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

    private final boolean includeOperations;
    private final boolean includeValidations;
    private final boolean includeExceptions;
    private final boolean includeLaws;

    private final FunctorValidationStage<F, A, B> validationStage;

    FunctorTestExecutor(
            Class<?> contextClass,
            Functor<F> functor,
            Kind<F, A> validKind,
            Function<A, B> mapper,
            Function<B, String> secondMapper,
            BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker,
            boolean includeOperations,
            boolean includeValidations,
            boolean includeExceptions,
            boolean includeLaws,
            FunctorValidationStage<F, A, B> validationStage) {

        this.contextClass = contextClass;
        this.functor = functor;
        this.validKind = validKind;
        this.mapper = mapper;
        this.secondMapper = secondMapper;
        this.equalityChecker = equalityChecker;
        this.includeOperations = includeOperations;
        this.includeValidations = includeValidations;
        this.includeExceptions = includeExceptions;
        this.includeLaws = includeLaws;
        this.validationStage = validationStage;
    }

  void executeAll() {
    if (includeOperations) executeOperations();
    if (includeValidations) executeValidations();
    if (includeExceptions) executeExceptions();
    if (includeLaws) executeLaws();
  }

  void executeOperations() {
    TestMethodRegistry.testFunctorOperations(functor, validKind, mapper);
  }

  void executeValidations() {
    TestMethodRegistry.testFunctorValidations(functor, contextClass, validKind, mapper);
  }

  void executeExceptions() {
    TestMethodRegistry.testFunctorExceptionPropagation(functor, validKind);
  }

  void executeLaws() {
    Function<B, String> effectiveSecondMapper =
        secondMapper != null ? secondMapper : Object::toString;
    BiPredicate<Kind<F, ?>, Kind<F, ?>> effectiveChecker =
        equalityChecker != null ? equalityChecker : (k1, k2) -> k1 == k2;

    TestMethodRegistry.testFunctorLaws(
        functor, validKind, mapper, effectiveSecondMapper, effectiveChecker);
  }

  void executeSelected() {
    executeAll();
  }

    void executeOperationsAndLaws() {
        if (includeOperations) executeOperations();
        if (includeLaws) executeLaws();
    }

    void executeOperationsAndValidations() {
        if (includeOperations) executeOperations();
        if (includeValidations) executeValidations();
    }
}
