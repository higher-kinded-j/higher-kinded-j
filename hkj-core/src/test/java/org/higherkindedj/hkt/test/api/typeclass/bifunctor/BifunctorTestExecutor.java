// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.bifunctor;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.test.api.typeclass.internal.TestMethodRegistry;

/**
 * Executes bifunctor tests by delegating to {@link TestMethodRegistry}.
 *
 * @param <F> The Bifunctor witness type
 * @param <A> The first input type parameter
 * @param <B> The second input type parameter
 * @param <C> The first output type parameter
 * @param <D> The second output type parameter
 */
final class BifunctorTestExecutor<F extends WitnessArity<TypeArity.Binary>, A, B, C, D> {

  private final BifunctorTestConfigStage<F, A, B, C, D> config;

  BifunctorTestExecutor(BifunctorTestConfigStage<F, A, B, C, D> config) {
    this.config = config;
  }

  void executeAll() {
    if (config.includeOperations) {
      executeOperations();
    }
    if (config.includeValidations) {
      executeValidations();
    }
    if (config.includeExceptions) {
      executeExceptions();
    }
    if (config.includeLaws) {
      executeLaws();
    }
  }

  void executeSelected() {
    executeAll();
  }

  void executeOperations() {
    TestMethodRegistry.testBifunctorOperations(
        config.contextClass,
        config.bifunctor,
        config.validKind,
        config.firstMapper,
        config.secondMapper);
  }

  void executeValidations() {
    TestMethodRegistry.testBifunctorValidations(
        config.contextClass,
        config.bifunctor,
        config.validKind,
        config.firstMapper,
        config.secondMapper);
  }

  void executeExceptions() {
    TestMethodRegistry.testBifunctorExceptionPropagation(
        config.contextClass,
        config.bifunctor,
        config.validKind,
        config.firstMapper,
        config.secondMapper,
        config.firstExceptionKind,
        config.secondExceptionKind);
  }

  void executeLaws() {
    if (config.equalityChecker == null) {
      throw new IllegalStateException("Law tests require an equality checker");
    }

    TestMethodRegistry.testBifunctorLaws(
        config.contextClass,
        config.bifunctor,
        config.validKind,
        config.firstMapper,
        config.secondMapper,
        config.compositionFirstMapper,
        config.compositionSecondMapper,
        config.equalityChecker);
  }
}
