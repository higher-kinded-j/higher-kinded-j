// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.writer;

import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;
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
public final class WriterTestConfigStage<W, A, B> {
  private final Class<?> contextClass;
  private final Writer<W, A> writerInstance;
  private final Monoid<W> monoid;
  private final Function<A, B> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeRun = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  WriterTestConfigStage(
      Class<?> contextClass, Writer<W, A> writerInstance, Monoid<W> monoid, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.writerInstance = writerInstance;
    this.monoid = monoid;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
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

  public WriterTestConfigStage<W, A, B> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public WriterTestConfigStage<W, A, B> skipEdgeCases() {
    this.includeEdgeCases = false;
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

  public WriterTestConfigStage<W, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public WriterTestConfigStage<W, A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  private void disableAll() {
    includeFactoryMethods = false;
    includeRun = false;
    includeMap = false;
    includeFlatMap = false;
    includeValidations = false;
    includeEdgeCases = false;
  }

  // =============================================================================
  // Validation Configuration
  // =============================================================================

  /**
   * Enters validation configuration mode.
   *
   * <p>Progressive disclosure: Shows validation context configuration options.
   *
   * @return Validation stage for configuring error message contexts
   */
  public WriterValidationStage<W, A, B> configureValidation() {
    return new WriterValidationStage<>(this);
  }

  // =============================================================================
  // Execution Methods
  // =============================================================================

  /**
   * Executes all configured tests.
   *
   * <p>This is the most comprehensive test execution option.
   */
  public void testAll() {
    WriterTestExecutor<W, A, B> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only core operation tests (no validations or edge cases). */
  public void testOperations() {
    includeValidations = false;
    includeEdgeCases = false;
    testAll();
  }

  /** Executes only validation tests. */
  public void testValidations() {
    onlyValidations();
    testAll();
  }

  /** Executes only edge case tests. */
  public void testEdgeCases() {
    onlyEdgeCases();
    testAll();
  }

  // =============================================================================
  // Internal Builder
  // =============================================================================

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
