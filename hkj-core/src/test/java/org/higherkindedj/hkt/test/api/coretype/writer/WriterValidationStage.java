// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.writer;

/**
 * Stage for configuring validation contexts in Writer core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * @param <W> The log type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class WriterValidationStage<W, A, B> {
  private final WriterTestConfigStage<W, A, B> configStage;

  // Validation context classes
  private Class<?> mapContext;
  private Class<?> flatMapContext;

  WriterValidationStage(WriterTestConfigStage<W, A, B> configStage) {
    this.configStage = configStage;
  }

  /**
   * Uses inheritance-based validation with fluent configuration.
   *
   * <p>This allows you to specify which class in the inheritance hierarchy should be used in
   * validation error messages for each operation.
   *
   * @return Fluent configuration builder
   */
  public InheritanceValidationBuilder useInheritanceValidation() {
    return new InheritanceValidationBuilder();
  }

  /**
   * Uses default validation (no class context).
   *
   * <p>Error messages will not include specific class names.
   *
   * @return This stage for further configuration or execution
   */
  public WriterValidationStage<W, A, B> useDefaultValidation() {
    this.mapContext = null;
    this.flatMapContext = null;
    return this;
  }

  /** Fluent builder for inheritance-based validation configuration. */
  public final class InheritanceValidationBuilder {

    /**
     * Specifies the class used for map operation validation.
     *
     * @param contextClass The class that implements map
     * @return This builder for chaining
     */
    public InheritanceValidationBuilder withMapFrom(Class<?> contextClass) {
      mapContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for flatMap operation validation.
     *
     * @param contextClass The class that implements flatMap
     * @return This builder for chaining
     */
    public InheritanceValidationBuilder withFlatMapFrom(Class<?> contextClass) {
      flatMapContext = contextClass;
      return this;
    }

    /**
     * Completes inheritance validation configuration.
     *
     * @return The parent validation stage for execution
     */
    public WriterValidationStage<W, A, B> done() {
      return WriterValidationStage.this;
    }

    /** Executes all configured tests. */
    public void testAll() {
      WriterValidationStage.this.testAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
      WriterValidationStage.this.testValidations();
    }
  }

  /** Executes all configured tests. */
  public void testAll() {
    WriterTestExecutor<W, A, B> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only validation tests with configured contexts. */
  public void testValidations() {
    WriterTestExecutor<W, A, B> executor = buildExecutor();
    executor.testValidations();
  }

  // Package-private getters
  Class<?> getMapContext() {
    return mapContext;
  }

  Class<?> getFlatMapContext() {
    return flatMapContext;
  }

  private WriterTestExecutor<W, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
