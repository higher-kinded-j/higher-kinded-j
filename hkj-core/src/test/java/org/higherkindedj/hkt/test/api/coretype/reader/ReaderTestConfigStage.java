// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import java.util.function.Function;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <R> The environment type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ReaderTestConfigStage<R, A, B>
    extends BaseTestConfigStage<ReaderTestConfigStage<R, A, B>> {

  private final Class<?> contextClass;
  private final Reader<R, A> readerInstance;
  private final R environment;
  private final Function<A, B> mapper;

  // Type-specific test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeRun = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;

  ReaderTestConfigStage(
      Class<?> contextClass, Reader<R, A> readerInstance, R environment, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.readerInstance = readerInstance;
    this.environment = environment;
    this.mapper = mapper;
  }

  // =============================================================================
  // BaseTestConfigStage Implementation
  // =============================================================================

  @Override
  protected ReaderTestConfigStage<R, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public ReaderValidationStage<R, A, B> configureValidation() {
    return new ReaderValidationStage<>(this);
  }

  @Override
  public ReaderTestConfigStage<R, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public ReaderTestConfigStage<R, A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeFactoryMethods = false;
    includeRun = false;
    includeMap = false;
    includeFlatMap = false;
  }

  // =============================================================================
  // Type-Specific Test Selection Methods
  // =============================================================================

  public ReaderTestConfigStage<R, A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public ReaderTestConfigStage<R, A, B> skipRun() {
    this.includeRun = false;
    return this;
  }

  public ReaderTestConfigStage<R, A, B> skipMap() {
    this.includeMap = false;
    return this;
  }

  public ReaderTestConfigStage<R, A, B> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public ReaderTestConfigStage<R, A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public ReaderTestConfigStage<R, A, B> onlyRun() {
    disableAll();
    this.includeRun = true;
    return this;
  }

  public ReaderTestConfigStage<R, A, B> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public ReaderTestConfigStage<R, A, B> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  private ReaderTestExecutor<R, A, B> buildExecutor() {
    return new ReaderTestExecutor<>(
        contextClass,
        readerInstance,
        environment,
        mapper,
        includeFactoryMethods,
        includeRun,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases);
  }

  ReaderTestExecutor<R, A, B> buildExecutorWithValidation(
      ReaderValidationStage<R, A, B> validationStage) {
    return new ReaderTestExecutor<>(
        contextClass,
        readerInstance,
        environment,
        mapper,
        includeFactoryMethods,
        includeRun,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
