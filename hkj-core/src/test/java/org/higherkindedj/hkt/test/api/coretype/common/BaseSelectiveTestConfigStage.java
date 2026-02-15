// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.common;

/**
 * Base test configuration stage for Selective operations on core types.
 *
 * <p>This abstract class eliminates duplication across Selective test configuration stages by
 * providing:
 *
 * <ul>
 *   <li>Common Selective operation test flags (select, branch, whenS, ifS)
 *   <li>Common validation and edge case flags
 *   <li>Fluent skip/only patterns for test categories
 *   <li>Standard execution methods
 * </ul>
 *
 * <h2>Usage Pattern:</h2>
 *
 * <pre>{@code
 * public final class EitherSelectiveConfigStage<L, R, S>
 *     extends BaseSelectiveTestConfigStage<EitherSelectiveConfigStage<L, R, S>> {
 *
 *     private final Class<?> contextClass;
 *     private final Either<L, R> leftInstance;
 *     private final Either<L, R> rightInstance;
 *     // ... other fields
 *
 *     @Override
 *     protected EitherSelectiveConfigStage<L, R, S> self() {
 *         return this;
 *     }
 *
 *     @Override
 *     public void testAll() {
 *         buildExecutor().executeAll();
 *     }
 *
 *     @Override
 *     public EitherSelectiveValidationStage<L, R, S> configureValidation() {
 *         return new EitherSelectiveValidationStage<>(this);
 *     }
 *
 *     private EitherSelectiveTestExecutor<L, R, S> buildExecutor() {
 *         return new EitherSelectiveTestExecutor<>(...);
 *     }
 * }
 * }</pre>
 *
 * @param <SELF> The concrete configuration stage type for fluent method chaining
 */
public abstract class BaseSelectiveTestConfigStage<
    SELF extends BaseSelectiveTestConfigStage<SELF>> {

  // Selective operation test selection flags - protected for subclass access
  protected boolean includeSelect = true;
  protected boolean includeBranch = true;
  protected boolean includeWhenS = true;
  protected boolean includeIfS = true;

  // Common test selection flags
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
  // Selective Operation Test Selection
  // =============================================================================

  /** Skips select operation tests. */
  public final SELF skipSelect() {
    this.includeSelect = false;
    return self();
  }

  /** Skips branch operation tests. */
  public final SELF skipBranch() {
    this.includeBranch = false;
    return self();
  }

  /** Skips whenS operation tests. */
  public final SELF skipWhenS() {
    this.includeWhenS = false;
    return self();
  }

  /** Skips ifS operation tests. */
  public final SELF skipIfS() {
    this.includeIfS = false;
    return self();
  }

  /** Runs only select operation tests (disables all others). */
  public final SELF onlySelect() {
    disableAll();
    this.includeSelect = true;
    return self();
  }

  /** Runs only branch operation tests (disables all others). */
  public final SELF onlyBranch() {
    disableAll();
    this.includeBranch = true;
    return self();
  }

  /** Runs only whenS operation tests (disables all others). */
  public final SELF onlyWhenS() {
    disableAll();
    this.includeWhenS = true;
    return self();
  }

  /** Runs only ifS operation tests (disables all others). */
  public final SELF onlyIfS() {
    disableAll();
    this.includeIfS = true;
    return self();
  }

  // =============================================================================
  // Common Test Selection
  // =============================================================================

  /** Skips validation tests. */
  public final SELF skipValidations() {
    this.includeValidations = false;
    return self();
  }

  /** Skips edge case tests. */
  public final SELF skipEdgeCases() {
    this.includeEdgeCases = false;
    return self();
  }

  /** Runs only validation tests (disables all others). */
  public final SELF onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return self();
  }

  /** Runs only edge case tests (disables all others). */
  public final SELF onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return self();
  }

  /**
   * Disables all test categories.
   *
   * <p>Subclasses should override this if they have additional flags, calling {@code
   * super.disableAll()} to disable base flags.
   */
  protected void disableAll() {
    includeSelect = false;
    includeBranch = false;
    includeWhenS = false;
    includeIfS = false;
    includeValidations = false;
    includeEdgeCases = false;
  }

  // =============================================================================
  // Common Execution Methods
  // =============================================================================

  /** Executes only operation tests (no validations or edge cases). */
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
