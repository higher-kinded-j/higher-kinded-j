// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.bifunctor;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Fine-grained test selection stage.
 *
 * <p>Allows including/excluding specific test categories.
 *
 * @param <F> The Bifunctor witness type
 * @param <A> The first input type parameter
 * @param <B> The second input type parameter
 * @param <C> The first output type parameter
 * @param <D> The second output type parameter
 */
public final class BifunctorTestSelectionStage<
    F extends WitnessArity<TypeArity.Binary>, A, B, C, D> {

  private final BifunctorTestConfigStage<F, A, B, C, D> config;

  BifunctorTestSelectionStage(BifunctorTestConfigStage<F, A, B, C, D> config) {
    this.config = config;
  }

  // Negative selection (skip specific tests)

  public BifunctorTestSelectionStage<F, A, B, C, D> skipOperations() {
    config.includeOperations = false;
    return this;
  }

  public BifunctorTestSelectionStage<F, A, B, C, D> skipValidations() {
    config.includeValidations = false;
    return this;
  }

  public BifunctorTestSelectionStage<F, A, B, C, D> skipExceptions() {
    config.includeExceptions = false;
    return this;
  }

  public BifunctorTestSelectionStage<F, A, B, C, D> skipLaws() {
    config.includeLaws = false;
    return this;
  }

  // Positive selection (run only specific tests)

  public BifunctorTestSelectionStage<F, A, B, C, D> onlyOperations() {
    config.includeOperations = true;
    config.includeValidations = false;
    config.includeExceptions = false;
    config.includeLaws = false;
    return this;
  }

  public BifunctorTestSelectionStage<F, A, B, C, D> onlyValidations() {
    config.includeOperations = false;
    config.includeValidations = true;
    config.includeExceptions = false;
    config.includeLaws = false;
    return this;
  }

  public BifunctorTestSelectionStage<F, A, B, C, D> onlyExceptions() {
    config.includeOperations = false;
    config.includeValidations = false;
    config.includeExceptions = true;
    config.includeLaws = false;
    return this;
  }

  public BifunctorTestSelectionStage<F, A, B, C, D> onlyLaws() {
    if (config.equalityChecker == null) {
      throw new IllegalStateException(
          "Law tests require an equality checker. Use withEqualityChecker() first.");
    }
    config.includeOperations = false;
    config.includeValidations = false;
    config.includeExceptions = false;
    config.includeLaws = true;
    return this;
  }

  /** Executes the selected tests. */
  public void test() {
    config.build().executeSelected();
  }
}
