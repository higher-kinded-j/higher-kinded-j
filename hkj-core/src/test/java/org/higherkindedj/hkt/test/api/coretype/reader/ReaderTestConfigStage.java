// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import java.util.function.Function;
import org.higherkindedj.hkt.reader.Reader;

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
public final class ReaderTestConfigStage<R, A, B> {
  private final Class<?> contextClass;
  private final Reader<R, A> readerInstance;
  private final R environment;
  private final Function<A, B> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeRun = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  ReaderTestConfigStage(
      Class<?> contextClass, Reader<R, A> readerInstance, R environment, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.readerInstance = readerInstance;
    this.environment = environment;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
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

  public ReaderTestConfigStage<R, A, B> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public ReaderTestConfigStage<R, A, B> skipEdgeCases() {
    this.includeEdgeCases = false;
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

  public ReaderTestConfigStage<R, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public ReaderTestConfigStage<R, A, B> onlyEdgeCases() {
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
  public ReaderValidationStage<R, A, B> configureValidation() {
    return new ReaderValidationStage<>(this);
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
    ReaderTestExecutor<R, A, B> executor = buildExecutor();
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
