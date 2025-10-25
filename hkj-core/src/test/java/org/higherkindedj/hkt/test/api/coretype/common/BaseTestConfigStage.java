// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.common;

/**
 * Base test configuration stage providing common test selection patterns.
 *
 * <p>This abstract class eliminates duplication across core type test configuration stages by
 * providing:
 *
 * <ul>
 *   <li>Common test selection flags and methods
 *   <li>Fluent skip/only patterns for test categories
 *   <li>Standard execution methods
 *   <li>Validation configuration entry point
 * </ul>
 *
 * <h2>Usage Pattern:</h2>
 *
 * <pre>{@code
 * public final class MyTypeTestConfigStage<T, R>
 *     extends BaseTestConfigStage<MyTypeTestConfigStage<T, R>> {
 *
 *     // Test category flags specific to your type
 *     private boolean includeMyOperation = true;
 *
 *     @Override
 *     protected MyTypeTestConfigStage<T, R> self() {
 *         return this;
 *     }
 *
 *     @Override
 *     public void testAll() {
 *         buildExecutor().executeAll();
 *     }
 *
 *     @Override
 *     public MyTypeValidationStage<T, R> configureValidation() {
 *         return new MyTypeValidationStage<>(this);
 *     }
 *
 *     @Override
 *     protected void disableAll() {
 *         super.disableAll();
 *         includeMyOperation = false;
 *     }
 *
 *     private MyTypeTestExecutor<T, R> buildExecutor() {
 *         return new MyTypeTestExecutor<>(
 *             // Pass all configuration and flags
 *         );
 *     }
 * }
 * }</pre>
 *
 * @param <SELF> The concrete configuration stage type for fluent method chaining
 */
public abstract class BaseTestConfigStage<SELF extends BaseTestConfigStage<SELF>> {

  // Common test selection flags - protected for subclass access
  protected boolean includeValidations = true;
  protected boolean includeEdgeCases = true;

  /**
   * Returns the concrete type instance for fluent method chaining.
   *
   * <p>Subclasses must implement this to return {@code this} cast to their concrete type.
   *
   * @return The concrete configuration stage instance
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
   * Enters validation configuration mode.
   *
   * <p>Subclasses implement this to return their specific validation stage.
   *
   * @return Validation stage for configuring error message contexts
   */
  public abstract Object configureValidation();

  // =============================================================================
  // Common Test Selection Methods
  // =============================================================================

  /**
   * Skips validation tests.
   *
   * @return This stage for further configuration or execution
   */
  public final SELF skipValidations() {
    this.includeValidations = false;
    return self();
  }

  /**
   * Skips edge case tests.
   *
   * @return This stage for further configuration or execution
   */
  public final SELF skipEdgeCases() {
    this.includeEdgeCases = false;
    return self();
  }

  /**
   * Runs only validation tests (disables all others).
   *
   * <p>Subclasses should call {@code disableAll()} before enabling validations.
   *
   * @return This stage for further configuration or execution
   */
  public abstract SELF onlyValidations();

  /**
   * Runs only edge case tests (disables all others).
   *
   * <p>Subclasses should call {@code disableAll()} before enabling edge cases.
   *
   * @return This stage for further configuration or execution
   */
  public abstract SELF onlyEdgeCases();

  /**
   * Disables all test categories.
   *
   * <p>Subclasses should override this method to disable their specific flags and call {@code
   * super.disableAll()} to disable base flags.
   */
  protected void disableAll() {
    includeValidations = false;
    includeEdgeCases = false;
  }

  // =============================================================================
  // Common Execution Methods
  // =============================================================================

  /**
   * Executes only core operation tests (no validations or edge cases).
   *
   * <p>Subclasses can override this to provide custom behaviour.
   */
  public void testOperations() {
    includeValidations = false;
    includeEdgeCases = false;
    testAll();
  }

  /**
   * Executes only validation tests.
   *
   * <p>Uses the {@code onlyValidations()} method to configure flags, then executes.
   */
  public final void testValidations() {
    onlyValidations();
    testAll();
  }

  /**
   * Executes only edge case tests.
   *
   * <p>Uses the {@code onlyEdgeCases()} method to configure flags, then executes.
   */
  public final void testEdgeCases() {
    onlyEdgeCases();
    testAll();
  }
}
