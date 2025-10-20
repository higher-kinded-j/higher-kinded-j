// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.common;

/**
 * Base validation stage for transformer types.
 *
 * <p>Transformer types typically validate at the transformer level rather than separating
 * map/flatMap validations, so this provides a simpler single-context validation pattern.
 *
 * @param <SELF> The concrete validation stage type for fluent method chaining
 */
public abstract class BaseTransformerValidationStage<
    SELF extends BaseTransformerValidationStage<SELF>> {

  // Single validation context for transformer-level operations
  protected Class<?> validationContext;

  /**
   * Returns the concrete type instance for fluent method chaining.
   *
   * @return The concrete validation stage instance
   */
  protected abstract SELF self();

  /** Executes all configured tests. */
  public abstract void testAll();

  /** Executes only validation tests with configured contexts. */
  public abstract void testValidations();

  // =============================================================================
  // Validation Configuration Methods
  // =============================================================================

  /**
   * Uses inheritance-based validation with fluent configuration.
   *
   * @return Fluent configuration builder
   */
  public final InheritanceValidationBuilder useInheritanceValidation() {
    return new InheritanceValidationBuilder();
  }

  /**
   * Uses default validation (no class context).
   *
   * @return This stage for further configuration or execution
   */
  public final SELF useDefaultValidation() {
    this.validationContext = null;
    return self();
  }

  // =============================================================================
  // Fluent Builder for Inheritance-Based Validation
  // =============================================================================

  /** Fluent builder for configuring inheritance-based validation contexts. */
  public final class InheritanceValidationBuilder {

    /**
     * Specifies the class used for validation error messages.
     *
     * @param contextClass The class that implements the operations
     * @return This builder for chaining
     */
    public InheritanceValidationBuilder withContextFrom(Class<?> contextClass) {
      validationContext = contextClass;
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

    /** Executes all configured tests. */
    public void testAll() {
      BaseTransformerValidationStage.this.testAll();
    }

    /** Executes only validation tests with configured contexts. */
    public void testValidations() {
      BaseTransformerValidationStage.this.testValidations();
    }
  }

  /**
   * Gets the configured validation context class.
   *
   * @return The validation context class, or null if not configured
   */
  public final Class<?> getValidationContext() {
    return validationContext;
  }
}
