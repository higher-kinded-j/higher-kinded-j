// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.time.Instant;

/**
 * Event record emitted on each retry attempt, enabling monitoring and logging.
 *
 * <p>{@code RetryEvent} provides detailed information about a retry attempt: which attempt
 * number it was, what exception triggered the retry, how long the next delay will be, and
 * when the event occurred.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100))
 *     .onRetry(event -> log.warn(
 *         "Retry attempt {} after {}: {}",
 *         event.attemptNumber(),
 *         event.nextDelay(),
 *         event.lastException().getMessage()));
 * }</pre>
 *
 * @param attemptNumber the 1-based attempt number that just failed
 * @param lastException the exception that triggered this retry
 * @param nextDelay the delay before the next attempt
 * @param timestamp when this event occurred
 * @see RetryPolicy#onRetry(java.util.function.Consumer)
 */
public record RetryEvent(
    int attemptNumber,
    Throwable lastException,
    Duration nextDelay,
    Instant timestamp
) {

  /**
   * Creates a RetryEvent with the current timestamp.
   *
   * @param attemptNumber the 1-based attempt number that just failed
   * @param lastException the exception that triggered this retry
   * @param nextDelay the delay before the next attempt
   * @return a new RetryEvent with the current time as the timestamp
   */
  public static RetryEvent of(int attemptNumber, Throwable lastException, Duration nextDelay) {
    return new RetryEvent(attemptNumber, lastException, nextDelay, Instant.now());
  }
}
