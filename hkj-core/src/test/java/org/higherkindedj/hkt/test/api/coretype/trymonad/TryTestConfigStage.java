// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.trymonad;

import java.util.function.Function;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <T> The value type
 * @param <S> The mapped type
 */
public final class TryTestConfigStage<T, S> extends BaseTestConfigStage<TryTestConfigStage<T, S>> {

  private final Class<?> contextClass;
  private final Try<T> successInstance;
  private final Try<T> failureInstance;
  private final Function<T, S> mapper;

  // Type-specific test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeGetters = true;
  private boolean includeFold = true;
  private boolean includeOrElse = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;
  private boolean includeRecover = true;
  private boolean includeToEither = true;

  TryTestConfigStage(
      Class<?> contextClass,
      Try<T> successInstance,
      Try<T> failureInstance,
      Function<T, S> mapper) {
    this.contextClass = contextClass;
    this.successInstance = successInstance;
    this.failureInstance = failureInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // BaseTestConfigStage Implementation
  // =============================================================================

  @Override
  protected TryTestConfigStage<T, S> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public TryValidationStage<T, S> configureValidation() {
    return new TryValidationStage<>(this);
  }

  @Override
  public TryTestConfigStage<T, S> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public TryTestConfigStage<T, S> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeFactoryMethods = false;
    includeGetters = false;
    includeFold = false;
    includeOrElse = false;
    includeMap = false;
    includeFlatMap = false;
    includeRecover = false;
    includeToEither = false;
  }

  // =============================================================================
  // Type-Specific Test Selection Methods
  // =============================================================================

  public TryTestConfigStage<T, S> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipGetters() {
    this.includeGetters = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipFold() {
    this.includeFold = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipOrElse() {
    this.includeOrElse = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipMap() {
    this.includeMap = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipRecover() {
    this.includeRecover = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipToEither() {
    this.includeToEither = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public TryTestConfigStage<T, S> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyGetters() {
    disableAll();
    this.includeGetters = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyFold() {
    disableAll();
    this.includeFold = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyOrElse() {
    disableAll();
    this.includeOrElse = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyRecover() {
    disableAll();
    this.includeRecover = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyToEither() {
    disableAll();
    this.includeToEither = true;
    return this;
  }

  private TryTestExecutor<T, S> buildExecutor() {
    return new TryTestExecutor<>(
        contextClass,
        successInstance,
        failureInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeOrElse,
        includeMap,
        includeFlatMap,
        includeRecover,
        includeToEither,
        includeValidations,
        includeEdgeCases);
  }

  TryTestExecutor<T, S> buildExecutorWithValidation(TryValidationStage<T, S> validationStage) {
    return new TryTestExecutor<>(
        contextClass,
        successInstance,
        failureInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeOrElse,
        includeMap,
        includeFlatMap,
        includeRecover,
        includeToEither,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
