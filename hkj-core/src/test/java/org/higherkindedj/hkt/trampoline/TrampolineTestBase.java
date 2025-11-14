// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

/**
 * Base class for {@link Trampoline} tests providing common test constants and helper methods.
 *
 * <p>This class provides shared utilities for testing Trampoline implementations, including:
 *
 * <ul>
 *   <li>Standard test constants (default values)
 *   <li>Helper methods for creating recursive test computations
 *   <li>Example trampolines for factorial and mutual recursion
 * </ul>
 *
 * <p>Subclasses can use these utilities to write consistent, readable tests without duplicating
 * common test setup code.
 */
abstract class TrampolineTestBase {

  // ============================================================================
  // Test Constants
  // ============================================================================

  /** Default integer value for testing. */
  protected static final Integer DEFAULT_VALUE = 42;

  /** Alternative integer value for testing different scenarios. */
  protected static final Integer ALTERNATIVE_VALUE = 24;

  /** Default string value for testing. */
  protected static final String DEFAULT_STRING_VALUE = "hello";

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
   * Creates a stack-safe factorial trampoline for testing deep recursion.
   *
   * @param n The number to compute factorial for.
   * @param acc The accumulator.
   * @return A Trampoline computing the factorial.
   */
  protected Trampoline<Long> factorial(long n, long acc) {
    if (n <= 0) {
      return Trampoline.done(acc);
    }
    return Trampoline.defer(() -> factorial(n - 1, n * acc));
  }

  /**
   * Creates a stack-safe even checker for mutual recursion testing.
   *
   * @param n The number to check.
   * @return A Trampoline computing whether n is even.
   */
  protected Trampoline<Boolean> isEven(int n) {
    if (n == 0) {
      return Trampoline.done(true);
    }
    return Trampoline.defer(() -> isOdd(n - 1));
  }

  /**
   * Creates a stack-safe odd checker for mutual recursion testing.
   *
   * @param n The number to check.
   * @return A Trampoline computing whether n is odd.
   */
  protected Trampoline<Boolean> isOdd(int n) {
    if (n == 0) {
      return Trampoline.done(false);
    }
    return Trampoline.defer(() -> isEven(n - 1));
  }
}
