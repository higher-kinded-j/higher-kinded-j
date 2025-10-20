// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTransformerTestConfigStage;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <F> The outer monad witness type
 * @param <A> The type of the value potentially held by the inner Maybe
 * @param <B> The mapped type
 */
public final class MaybeTTestConfigStage<F, A, B>
    extends BaseTransformerTestConfigStage<MaybeTTestConfigStage<F, A, B>> {

  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final MaybeT<F, A> justInstance;
  private final MaybeT<F, A> nothingInstance;
  private final Function<A, B> mapper;

  MaybeTTestConfigStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      MaybeT<F, A> justInstance,
      MaybeT<F, A> nothingInstance,
      Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
    this.mapper = mapper;
  }

  @Override
  protected MaybeTTestConfigStage<F, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public MaybeTValidationStage<F, A, B> configureValidation() {
    return new MaybeTValidationStage<>(this);
  }

  private MaybeTTestExecutor<F, A, B> buildExecutor() {
    return new MaybeTTestExecutor<>(
        contextClass,
        outerMonad,
        justInstance,
        nothingInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases);
  }

  MaybeTTestExecutor<F, A, B> buildExecutorWithValidation(
      MaybeTValidationStage<F, A, B> validationStage) {
    return new MaybeTTestExecutor<>(
        contextClass,
        outerMonad,
        justInstance,
        nothingInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
