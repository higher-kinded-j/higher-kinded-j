// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.writer;

import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;
import org.higherkindedj.hkt.writer.Writer;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <W> The log type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class WriterTestConfigStage<W, A, B>
    extends BaseTestConfigStage<WriterTestConfigStage<W, A, B>> {

  private final Class<?> contextClass;
  private final Writer<W, A> writerInstance;
  private final Monoid<W> monoid;
  private final Function<A, B> mapper;

  // Type-specific test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeRun = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;

  WriterTestConfigStage(
      Class<?> contextClass, Writer<W, A> writerInstance, Monoid<W> monoid, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.writerInstance = writerInstance;
    this.monoid = monoid;
    this.mapper = mapper;
  }

  // =============================================================================
  // BaseTestConfigStage Implementation
  // =============================================================================

  @Override
  protected WriterTestConfigStage<W, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public WriterValidationStage<W, A, B> configureValidation() {
    return new WriterValidationStage<>(this);
  }

  @Override
  public WriterTestConfigStage<W, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public WriterTestConfigStage<W, A, B> onlyEdgeCases() {
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

  public WriterTestConfigStage<W, A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public WriterTestConfigStage<W, A, B> skipRun() {
    this.includeRun = false;
    return this;
  }

  public WriterTestConfigStage<W, A, B> skipMap() {
    this.includeMap = false;
    return this;
  }

  public WriterTestConfigStage<W, A, B> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public WriterTestConfigStage<W, A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public WriterTestConfigStage<W, A, B> onlyRun() {
    disableAll();
    this.includeRun = true;
    return this;
  }

  public WriterTestConfigStage<W, A, B> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public WriterTestConfigStage<W, A, B> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  private WriterTestExecutor<W, A, B> buildExecutor() {
    return new WriterTestExecutor<>(
        contextClass,
        writerInstance,
        monoid,
        mapper,
        includeFactoryMethods,
        includeRun,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases);
  }

  WriterTestExecutor<W, A, B> buildExecutorWithValidation(
      WriterValidationStage<W, A, B> validationStage) {
    return new WriterTestExecutor<>(
        contextClass,
        writerInstance,
        monoid,
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
