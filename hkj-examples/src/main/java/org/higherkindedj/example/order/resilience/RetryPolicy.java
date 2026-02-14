// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.resilience;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Policy for retry operations with exponential backoff.
 *
 * <p>Defines when and how to retry failed operations, including:
 *
 * <ul>
 *   <li>Maximum number of attempts
 *   <li>Initial delay and backoff multiplier
 *   <li>Maximum delay cap
 *   <li>Conditions for retry (which exceptions to retry on)
 * </ul>
 *
 * @param maxAttempts maximum number of attempts (including the first try)
 * @param initialDelay delay before the first retry
 * @param backoffMultiplier multiplier applied to delay after each retry
 * @param maxDelay maximum delay between retries
 * @param retryOn predicate determining which exceptions trigger retry
 */
public record RetryPolicy(
    int maxAttempts,
    Duration initialDelay,
    double backoffMultiplier,
    Duration maxDelay,
    Predicate<Throwable> retryOn) {
  /**
   * Creates a default retry policy. Retries on IOException and TimeoutException.
   *
   * @return default retry policy
   */
  public static RetryPolicy defaults() {
    return new RetryPolicy(
        3,
        Duration.ofMillis(100),
        2.0,
        Duration.ofSeconds(5),
        t -> t instanceof IOException || t instanceof TimeoutException);
  }

  /**
   * Creates a retry policy for testing with minimal delays.
   *
   * @return testing retry policy
   */
  public static RetryPolicy forTesting() {
    return new RetryPolicy(2, Duration.ofMillis(10), 1.5, Duration.ofMillis(50), t -> true);
  }

  /**
   * Creates a retry policy that never retries.
   *
   * @return no-retry policy
   */
  public static RetryPolicy noRetry() {
    return new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO, t -> false);
  }

  /**
   * Creates a retry policy from workflow configuration.
   *
   * @param maxRetries maximum retries
   * @param initialDelay initial delay
   * @param maxDelay maximum delay
   * @param backoffMultiplier backoff multiplier
   * @return configured retry policy
   */
  public static RetryPolicy of(
      int maxRetries, Duration initialDelay, Duration maxDelay, double backoffMultiplier) {
    return new RetryPolicy(
        maxRetries + 1, // +1 because maxAttempts includes first try
        initialDelay,
        backoffMultiplier,
        maxDelay,
        t -> t instanceof IOException || t instanceof TimeoutException);
  }

  /**
   * Calculates the delay for a given attempt number.
   *
   * @param attempt the attempt number (1-based)
   * @return the delay before this attempt
   */
  public Duration delayForAttempt(int attempt) {
    if (attempt <= 1) {
      return Duration.ZERO;
    }
    var retryNumber = attempt - 1;
    var delayMillis = initialDelay.toMillis() * Math.pow(backoffMultiplier, retryNumber - 1);
    return Duration.ofMillis(Math.min((long) delayMillis, maxDelay.toMillis()));
  }

  /**
   * Returns a new policy with a custom retry predicate.
   *
   * @param predicate the predicate for retry conditions
   * @return new policy with custom predicate
   */
  public RetryPolicy withRetryOn(Predicate<Throwable> predicate) {
    return new RetryPolicy(maxAttempts, initialDelay, backoffMultiplier, maxDelay, predicate);
  }

  /**
   * Returns a new policy that also retries on the given exception type.
   *
   * @param exceptionType exception class to retry on
   * @return new policy with extended retry conditions
   */
  public RetryPolicy alsoRetryOn(Class<? extends Throwable> exceptionType) {
    return withRetryOn(t -> retryOn.test(t) || exceptionType.isInstance(t));
  }
}
