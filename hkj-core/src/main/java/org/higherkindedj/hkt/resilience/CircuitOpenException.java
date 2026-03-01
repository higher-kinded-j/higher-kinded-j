// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;

/**
 * Exception thrown when a call is rejected because the {@link CircuitBreaker} is open.
 *
 * <p>This exception indicates that the circuit breaker has determined the protected service
 * is unhealthy and is not allowing calls through. The {@link #retryAfter()} duration
 * indicates approximately how long before the circuit breaker will transition to half-open
 * and allow a probe request.
 *
 * @see CircuitBreaker
 */
public class CircuitOpenException extends RuntimeException {

  private final CircuitBreaker.State state;
  private final Duration retryAfter;

  /**
   * Creates a new CircuitOpenException.
   *
   * @param state the current state of the circuit breaker
   * @param retryAfter approximate duration until the circuit may allow a probe
   */
  public CircuitOpenException(CircuitBreaker.State state, Duration retryAfter) {
    super("Circuit breaker is " + state + ", retry after " + retryAfter);
    this.state = state;
    this.retryAfter = retryAfter;
  }

  /**
   * Returns the state of the circuit breaker when the call was rejected.
   *
   * @return the circuit breaker state
   */
  public CircuitBreaker.State state() {
    return state;
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
