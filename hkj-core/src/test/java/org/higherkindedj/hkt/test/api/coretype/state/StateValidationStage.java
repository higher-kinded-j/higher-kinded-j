// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state;

/**
 * Stage for configuring validation contexts in State core type tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * @param <S> The state type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class StateValidationStage<S, A, B> {
  private final StateTestConfigStage<S, A, B> configStage;

  // Validation context classes
  private Class<?> mapContext;
  private Class<?> flatMapContext;

  StateValidationStage(StateTestConfigStage<S, A, B> configStage) {
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
  public StateValidationStage<S, A, B> useDefaultValidation() {
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
    public StateValidationStage<S, A, B> done() {
      return StateValidationStage.this;
    }

    /** Executes all configured tests. */
    public void testAll() {
      StateValidationStage.this.testAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
      StateValidationStage.this.testValidations();
    }
  }

  /** Executes all configured tests. */
  public void testAll() {
    StateTestExecutor<S, A, B> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only validation tests with configured contexts. */
  public void testValidations() {
    StateTestExecutor<S, A, B> executor = buildExecutor();
    executor.testValidations();
  }

  // Package-private getters
  Class<?> getMapContext() {
    return mapContext;
  }

  Class<?> getFlatMapContext() {
    return flatMapContext;
  }

  private StateTestExecutor<S, A, B> buildExecutor() {
    return configStage.buildExecutorWithValidation(this);
  }
}
