// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.io;

import java.util.function.Function;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.test.api.coretype.common.BaseTestConfigStage;

/**
 * Stage 3: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class IOTestConfigStage<A, B> extends BaseTestConfigStage<IOTestConfigStage<A, B>> {

  private final Class<?> contextClass;
  private final IO<A> ioInstance;
  private final Function<A, B> mapper;

  // Type-specific test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeExecution = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;

  IOTestConfigStage(Class<?> contextClass, IO<A> ioInstance, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.ioInstance = ioInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // BaseTestConfigStage Implementation
  // =============================================================================

  @Override
  protected IOTestConfigStage<A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public IOValidationStage<A, B> configureValidation() {
    return new IOValidationStage<>(this);
  }

  @Override
  public IOTestConfigStage<A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  @Override
  public IOTestConfigStage<A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  @Override
  protected void disableAll() {
    super.disableAll();
    includeFactoryMethods = false;
    includeExecution = false;
    includeMap = false;
    includeFlatMap = false;
  }

  // =============================================================================
  // Type-Specific Test Selection Methods
  // =============================================================================

  public IOTestConfigStage<A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public IOTestConfigStage<A, B> skipExecution() {
    this.includeExecution = false;
    return this;
  }

  public IOTestConfigStage<A, B> skipMap() {
    this.includeMap = false;
    return this;
  }

  public IOTestConfigStage<A, B> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public IOTestConfigStage<A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public IOTestConfigStage<A, B> onlyExecution() {
    disableAll();
    this.includeExecution = true;
    return this;
  }

  public IOTestConfigStage<A, B> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public IOTestConfigStage<A, B> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  private IOTestExecutor<A, B> buildExecutor() {
    return new IOTestExecutor<>(
        contextClass,
        ioInstance,
        mapper,
        includeFactoryMethods,
        includeExecution,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases);
  }

  IOTestExecutor<A, B> buildExecutorWithValidation(IOValidationStage<A, B> validationStage) {
    return new IOTestExecutor<>(
        contextClass,
        ioInstance,
        mapper,
        includeFactoryMethods,
        includeExecution,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
