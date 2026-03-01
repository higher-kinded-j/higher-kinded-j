// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

/**
 * Exception thrown when a {@link Bulkhead} cannot accept any more concurrent or waiting callers.
 *
 * @see Bulkhead
 */
public class BulkheadFullException extends RuntimeException {

  private final int maxConcurrent;
  private final int currentWaiting;

  /**
   * Creates a new BulkheadFullException.
   *
   * @param maxConcurrent the maximum concurrent executions allowed by the bulkhead
   * @param currentWaiting the current number of callers waiting for a permit
   */
  public BulkheadFullException(int maxConcurrent, int currentWaiting) {
    super("Bulkhead full: maxConcurrent=" + maxConcurrent + ", waiting=" + currentWaiting);
    this.maxConcurrent = maxConcurrent;
    this.currentWaiting = currentWaiting;
  }

  /**
   * Returns the maximum concurrent executions allowed.
   *
   * @return the maximum concurrent executions
   */
  public int maxConcurrent() {
    return maxConcurrent;
  }

  /**
   * Returns the number of callers that were waiting when this exception was thrown.
   *
   * @return the number of waiting callers
   */
  public int currentWaiting() {
    return currentWaiting;
  }
}
