// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

/**
 * Exception thrown when a saga fails and one or more compensation actions also fail.
 *
 * <p>When all compensations succeed, the original exception is thrown directly.
 * This exception is only used when the compensation itself encounters errors,
 * providing access to the full {@link SagaError} for inspection.
 *
 * @see Saga
 * @see SagaError
 */
public class SagaExecutionException extends RuntimeException {

  private final SagaError sagaError;

  /**
   * Creates a new SagaExecutionException.
   *
   * @param sagaError the saga error with compensation details
   */
  public SagaExecutionException(SagaError sagaError) {
    super("Saga failed at step '" + sagaError.failedStep()
        + "' with " + sagaError.compensationFailures().size()
        + " compensation failure(s)", sagaError.originalError());
    this.sagaError = sagaError;
  }

  /**
   * Returns the detailed saga error including compensation results.
   *
   * @return the saga error
   */
  public SagaError sagaError() {
    return sagaError;
  }
}
