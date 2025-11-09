// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.common;

/**
 * Base validation stage for Selective operations on core types.
 *
 * <p>This abstract class eliminates duplication across Selective validation stages by providing:
 *
 * <ul>
 *   <li>Common validation context management (select, branch, whenS, ifS)
 *   <li>Fluent inheritance validation builder
 *   <li>Default validation configuration
 *   <li>Standard accessor methods
 * </ul>
 *
 * <h2>Usage Pattern:</h2>
 *
 * <pre>{@code
 * public final class EitherSelectiveValidationStage<L, R, S>
 *     extends BaseSelectiveValidationStage<EitherSelectiveValidationStage<L, R, S>> {
 *
 *     private final EitherSelectiveConfigStage<L, R, S> configStage;
 *
 *     @Override
 *     protected EitherSelectiveValidationStage<L, R, S> self() {
 *         return this;
 *     }
 *
 *     @Override
 *     public void testAll() {
 *         buildExecutor().executeAll();
 *     }
 *
 *     @Override
 *     public void testValidations() {
 *         buildExecutor().testValidations();
 *     }
 *
 *     private EitherSelectiveTestExecutor<L, R, S> buildExecutor() {
 *         return configStage.buildExecutorWithValidation(this);
 *     }
 * }
 * }</pre>
 *
 * @param <SELF> The concrete validation stage type for fluent method chaining
 */
public abstract class BaseSelectiveValidationStage<SELF extends BaseSelectiveValidationStage<SELF>>
    extends BaseValidationStage<SELF> {

  // Selective-specific validation contexts - protected for subclass access
  protected Class<?> selectContext;
  protected Class<?> branchContext;
  protected Class<?> whenSContext;
  protected Class<?> ifSContext;

  /**
   * Returns the concrete type instance for fluent method chaining.
   *
   * <p>Subclasses must implement this to return {@code this} cast to their concrete type.
   *
   * @return The concrete validation stage instance
   */
  protected abstract SELF self();

  /**
   * Executes all configured tests.
   *
   * <p>Subclasses implement this to delegate to their specific executor with all test categories
   * enabled.
   */
  public abstract void testAll();

  /**
   * Executes only validation tests with configured contexts.
   *
   * <p>Subclasses implement this to delegate to their specific executor with only validation tests
   * enabled.
   */
  public abstract void testValidations();

  // =============================================================================
  // Validation Configuration Methods
  // =============================================================================

  /**
   * Uses inheritance-based validation for Selective operations.
   *
   * <p>This allows specifying which class in the inheritance hierarchy should be used in validation
   * error messages for each Selective operation.
   *
   * <h2>Usage Example:</h2>
   *
   * <pre>{@code
   * .configureValidation()
   *     .useSelectiveInheritanceValidation()
   *         .withSelectFrom(EitherSelective.class)
   *         .withBranchFrom(EitherSelective.class)
   *         .withWhenSFrom(EitherSelective.class)
   *         .withIfSFrom(EitherSelective.class)
   *     .testAll()
   * }</pre>
   *
   * @return Fluent builder for Selective validation contexts
   */
  public SelectiveInheritanceBuilder useSelectiveInheritanceValidation() {
    return new SelectiveInheritanceBuilder();
  }

  /**
   * Uses default validation (no class context).
   *
   * <p>Error messages will not include specific class names.
   *
   * @return This stage for further configuration or execution
   */
  public final SELF useDefaultValidation() {
    // Clear parent class contexts (map, flatMap)
    super.useDefaultValidation();

    // Clear Selective-specific contexts
    this.selectContext = null;
    this.branchContext = null;
    this.whenSContext = null;
    this.ifSContext = null;
    return self();
  }

  // =============================================================================
  // Fluent Builder for Selective Inheritance-Based Validation
  // =============================================================================

  /**
   * Fluent builder for configuring Selective-specific inheritance-based validation contexts.
   *
   * <p>This inner class provides a clear, type-safe way to configure which implementation classes
   * should be referenced in validation error messages for Selective operations.
   */
  public final class SelectiveInheritanceBuilder {

    /**
     * Specifies the class used for select operation validation.
     *
     * <p>Error messages for select null validations will reference this class.
     *
     * @param contextClass The class that implements select (e.g., EitherSelective.class)
     * @return This builder for chaining
     */
    public SelectiveInheritanceBuilder withSelectFrom(Class<?> contextClass) {
      selectContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for branch operation validation.
     *
     * <p>Error messages for branch null validations will reference this class.
     *
     * @param contextClass The class that implements branch (e.g., EitherSelective.class)
     * @return This builder for chaining
     */
    public SelectiveInheritanceBuilder withBranchFrom(Class<?> contextClass) {
      branchContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for whenS operation validation.
     *
     * <p>Error messages for whenS null validations will reference this class.
     *
     * @param contextClass The class that implements whenS (e.g., EitherSelective.class)
     * @return This builder for chaining
     */
    public SelectiveInheritanceBuilder withWhenSFrom(Class<?> contextClass) {
      whenSContext = contextClass;
      return this;
    }

    /**
     * Specifies the class used for ifS operation validation.
     *
     * <p>Error messages for ifS null validations will reference this class.
     *
     * @param contextClass The class that implements ifS (e.g., EitherSelective.class)
     * @return This builder for chaining
     */
    public SelectiveInheritanceBuilder withIfSFrom(Class<?> contextClass) {
      ifSContext = contextClass;
      return this;
    }

    /**
     * Completes inheritance validation configuration.
     *
     * @return The parent validation stage for execution
     */
    public SELF done() {
      return self();
    }

    /**
     * Executes all configured tests.
     *
     * <p>Convenience method that completes configuration and immediately executes all tests.
     */
    public void testAll() {
      BaseSelectiveValidationStage.this.testAll();
    }

    /**
     * Executes only validation tests with configured contexts.
     *
     * <p>Convenience method that completes configuration and immediately executes validation tests.
     */
    public void testValidations() {
      BaseSelectiveValidationStage.this.testValidations();
    }
  }

  // =============================================================================
  // Package-Private Accessors
  // =============================================================================

  /** Gets the configured select context class. */
  public final Class<?> getSelectContext() {
    return selectContext;
  }

  /** Gets the configured branch context class. */
  public final Class<?> getBranchContext() {
    return branchContext;
  }

  /** Gets the configured whenS context class. */
  public final Class<?> getWhenSContext() {
    return whenSContext;
  }

  /** Gets the configured ifS context class. */
  public final Class<?> getIfSContext() {
    return ifSContext;
  }
}
