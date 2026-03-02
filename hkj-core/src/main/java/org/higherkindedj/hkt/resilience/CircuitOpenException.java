// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;

/**
 * Exception thrown when a call is rejected because the {@link CircuitBreaker} is open.
 *
 * <p>This exception indicates that the circuit breaker has determined the protected service is
 * unhealthy and is not allowing calls through. The {@link #retryAfter()} duration indicates
 * approximately how long before the circuit breaker will transition to half-open and allow a probe
 * request.
 *
 * @see CircuitBreaker
 */
public class CircuitOpenException extends RuntimeException {

  private final CircuitBreaker.Status status;
  private final Duration retryAfter;

  /**
   * Creates a new CircuitOpenException.
   *
   * @param status the current status of the circuit breaker
   * @param retryAfter approximate duration until the circuit may allow a probe
   */
  public CircuitOpenException(CircuitBreaker.Status status, Duration retryAfter) {
    super("Circuit breaker is " + status + ", retry after " + retryAfter);
    this.status = status;
    this.retryAfter = retryAfter;
  }

  /**
   * Returns the status of the circuit breaker when the call was rejected.
   *
   * @return the circuit breaker status
   */
  public CircuitBreaker.Status status() {
    return status;
  }

  /**
   * Returns the approximate duration until the circuit breaker may allow a probe request.
   *
   * @return the retry-after duration
   */
  public Duration retryAfter() {
    return retryAfter;
  }
}
