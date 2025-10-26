// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTransformerTestConfigStage;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <F> The outer monad witness type
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The mapped type
 */
public final class EitherTTestConfigStage<F, L, R, S>
    extends BaseTransformerTestConfigStage<EitherTTestConfigStage<F, L, R, S>> {

  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final EitherT<F, L, R> leftInstance;
  private final EitherT<F, L, R> rightInstance;
  private final Function<R, S> mapper;

  EitherTTestConfigStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      EitherT<F, L, R> leftInstance,
      EitherT<F, L, R> rightInstance,
      Function<R, S> mapper) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
    this.mapper = mapper;
  }

  @Override
  protected EitherTTestConfigStage<F, L, R, S> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public EitherTValidationStage<F, L, R, S> configureValidation() {
    return new EitherTValidationStage<>(this);
  }

  private EitherTTestExecutor<F, L, R, S> buildExecutor() {
    return new EitherTTestExecutor<>(
        contextClass,
        outerMonad,
        leftInstance,
        rightInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases);
  }

  EitherTTestExecutor<F, L, R, S> buildExecutorWithValidation(
      EitherTValidationStage<F, L, R, S> validationStage) {
    return new EitherTTestExecutor<>(
        contextClass,
        outerMonad,
        leftInstance,
        rightInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
