// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;

/**
 * Exception thrown when a {@link CircuitBreaker} rejects a call: it is open, or it is half-open and
 * every probe place is taken.
 *
 * <p>This exception indicates that the circuit breaker has determined the protected service is
 * unhealthy, or is still testing whether it has recovered, and is not letting this call through.
 * {@link #status()} says which. For an open circuit, {@link #retryAfter()} is approximately how
 * long before it half-opens and lets a probe through. For a half-open one, it is the call timeout,
 * by which a running probe that stops when interrupted has given up its place. Neither promises
 * that a call will then be admitted: a probe that fails or times out re-opens the circuit for its
 * open duration.
 *
 * @see CircuitBreaker
 */
public class CircuitOpenException extends RuntimeException {

  private final CircuitBreaker.Status status;
  private final Duration retryAfter;

  /**
   * Creates a new CircuitOpenException.
   *
   * @param status the status of the circuit breaker when it rejected the call: {@code OPEN}, or
   *     {@code HALF_OPEN} when every probe place was taken
   * @param retryAfter for {@code OPEN}, approximately the time before the circuit half-opens; for
   *     {@code HALF_OPEN}, the call timeout
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
   * Returns how long to wait before trying again: for an open circuit, approximately the time
   * before it half-opens; for a half-open one, the call timeout, by which a running probe that
   * stops when interrupted has given up its place. It does not promise that the next call is
   * admitted.
   *
   * @return the retry-after duration
   */
  public Duration retryAfter() {
    return retryAfter;
  }
}
