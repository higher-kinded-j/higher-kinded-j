// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTransformerTestConfigStage;

/**
 * Stage 3: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <F> The outer monad witness type
 * @param <R> The environment type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ReaderTTestConfigStage<F extends WitnessArity<TypeArity.Unary>, R, A, B>
    extends BaseTransformerTestConfigStage<ReaderTTestConfigStage<F, R, A, B>> {

  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final ReaderT<F, R, A> readerTInstance;
  private final Function<A, B> mapper;

  // Additional flag for ReaderT runner methods
  private boolean includeRunnerMethods = true;

  ReaderTTestConfigStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      ReaderT<F, R, A> readerTInstance,
      Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.readerTInstance = readerTInstance;
    this.mapper = mapper;
  }

  @Override
  protected ReaderTTestConfigStage<F, R, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public ReaderTValidationStage<F, R, A, B> configureValidation() {
    return new ReaderTValidationStage<>(this);
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeRunnerMethods = false;
  }

  // =============================================================================
  // Type-Specific Test Selection Methods
  // =============================================================================

  public ReaderTTestConfigStage<F, R, A, B> skipRunnerMethods() {
    this.includeRunnerMethods = false;
    return this;
  }

  public ReaderTTestConfigStage<F, R, A, B> onlyRunnerMethods() {
    disableAll();
    this.includeRunnerMethods = true;
    return this;
  }

  private ReaderTTestExecutor<F, R, A, B> buildExecutor() {
    return new ReaderTTestExecutor<>(
        contextClass,
        outerMonad,
        readerTInstance,
        mapper,
        includeFactoryMethods,
        includeRunnerMethods,
        includeValidations,
        includeEdgeCases);
  }

  ReaderTTestExecutor<F, R, A, B> buildExecutorWithValidation(
      ReaderTValidationStage<F, R, A, B> validationStage) {
    return new ReaderTTestExecutor<>(
        contextClass,
        outerMonad,
        readerTInstance,
        mapper,
        includeFactoryMethods,
        includeRunnerMethods,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
