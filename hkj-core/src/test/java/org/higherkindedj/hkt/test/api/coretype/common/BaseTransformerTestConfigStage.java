// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.common;

/**
 * Base test configuration stage for transformer types (monad transformers).
 *
 * <p>This abstract class provides common test configuration for transformer types like EitherT,
 * MaybeT, StateT, and ReaderT, which typically have:
 *
 * <ul>
 *   <li>Factory method tests
 *   <li>Value accessor tests
 *   <li>Validation tests
 *   <li>Edge case tests
 * </ul>
 *
 * <p>Transformer types generally have fewer operation categories than base types, so this provides
 * a simpler base compared to {@link BaseTestConfigStage}.
 *
 * @param <SELF> The concrete configuration stage type for fluent method chaining
 */
public abstract class BaseTransformerTestConfigStage<
    SELF extends BaseTransformerTestConfigStage<SELF>> {

  // Common test selection flags - protected for subclass access
  protected boolean includeFactoryMethods = true;
  protected boolean includeValueAccessor = true;
  protected boolean includeValidations = true;
  protected boolean includeEdgeCases = true;

  /**
   * Returns the concrete type instance for fluent method chaining.
   *
   * @return The concrete configuration stage instance
   */
  protected abstract SELF self();

  /** Executes all configured tests. */
  public abstract void testAll();

  /**
   * Enters validation configuration mode.
   *
   * @return Validation stage for configuring error message contexts
   */
  public abstract Object configureValidation();

  // =============================================================================
  // Common Test Selection Methods
  // =============================================================================

  public final SELF skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return self();
  }

  public final SELF skipValueAccessor() {
    this.includeValueAccessor = false;
    return self();
  }

  public final SELF skipValidations() {
    this.includeValidations = false;
    return self();
  }

  public final SELF skipEdgeCases() {
    this.includeEdgeCases = false;
    return self();
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public final SELF onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return self();
  }

  public final SELF onlyValueAccessor() {
    disableAll();
    this.includeValueAccessor = true;
    return self();
  }

  public final SELF onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return self();
  }

  public final SELF onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return self();
  }

  /**
   * Disables all test categories.
   *
   * <p>Subclasses should override this if they have additional flags.
   */
  protected void disableAll() {
    includeFactoryMethods = false;
    includeValueAccessor = false;
    includeValidations = false;
    includeEdgeCases = false;
  }

  // =============================================================================
  // Common Execution Methods
  // =============================================================================

  /** Executes only core operation tests (no validations or edge cases). */
  public final void testOperations() {
    includeValidations = false;
    includeEdgeCases = false;
    testAll();
  }

  /** Executes only validation tests. */
  public final void testValidations() {
    onlyValidations();
    testAll();
  }

  /** Executes only edge case tests. */
  public final void testEdgeCases() {
    onlyEdgeCases();
    testAll();
  }
}
