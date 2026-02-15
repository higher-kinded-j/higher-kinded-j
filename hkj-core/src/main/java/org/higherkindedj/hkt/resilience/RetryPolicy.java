// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * A configurable policy for retry behavior.
 *
 * <p>{@code RetryPolicy} defines how operations should be retried: how many times, with what delay
 * between attempts, and which exceptions should trigger retries.
 *
 * <h2>Built-in Policies</h2>
 *
 * <ul>
 *   <li>{@link #fixed(int, Duration)} - Fixed delay between attempts
 *   <li>{@link #exponentialBackoff(int, Duration)} - Exponentially increasing delays
 *   <li>{@link #exponentialBackoffWithJitter(int, Duration)} - Exponential backoff with
 *       randomization
 *   <li>{@link #noRetry()} - No retries (fail immediately)
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100))
 *     .withMaxDelay(Duration.ofSeconds(10))
 *     .retryOn(IOException.class);
 *
 * String result = Retry.execute(policy, () -> httpClient.get(url));
 * }</pre>
 *
 * @see Retry
 */
public final class RetryPolicy {

  private final int maxAttempts;
  private final Duration initialDelay;
  private final double backoffMultiplier;
  private final Duration maxDelay;
  private final boolean useJitter;
  private final Predicate<Throwable> retryPredicate;

  private RetryPolicy(
      int maxAttempts,
      Duration initialDelay,
      double backoffMultiplier,
      Duration maxDelay,
      boolean useJitter,
      Predicate<Throwable> retryPredicate) {
    this.maxAttempts = maxAttempts;
    this.initialDelay = initialDelay;
    this.backoffMultiplier = backoffMultiplier;
    this.maxDelay = maxDelay;
    this.useJitter = useJitter;
    this.retryPredicate = retryPredicate;
  }

  // ===== Factory Methods =====

  /**
   * Creates a policy with fixed delay between attempts.
   *
   * @param maxAttempts maximum number of attempts (must be at least 1)
   * @param delay the fixed delay between attempts; must not be null
   * @return a RetryPolicy with fixed delay
   * @throws IllegalArgumentException if maxAttempts is less than 1
   * @throws NullPointerException if delay is null
   */
  public static RetryPolicy fixed(int maxAttempts, Duration delay) {
    validateMaxAttempts(maxAttempts);
    Objects.requireNonNull(delay, "delay must not be null");
    return new RetryPolicy(maxAttempts, delay, 1.0, delay, false, _ -> true);
  }

  /**
   * Creates a policy with exponentially increasing delays.
   *
   * <p>Each subsequent delay is multiplied by 2: delay, delay*2, delay*4, etc.
   *
   * @param maxAttempts maximum number of attempts (must be at least 1)
   * @param initialDelay the initial delay; must not be null
   * @return a RetryPolicy with exponential backoff
   * @throws IllegalArgumentException if maxAttempts is less than 1
   * @throws NullPointerException if initialDelay is null
   */
  public static RetryPolicy exponentialBackoff(int maxAttempts, Duration initialDelay) {
    validateMaxAttempts(maxAttempts);
    Objects.requireNonNull(initialDelay, "initialDelay must not be null");
    return new RetryPolicy(maxAttempts, initialDelay, 2.0, Duration.ofMinutes(5), false, _ -> true);
  }

  /**
   * Creates a policy with exponential backoff plus jitter.
   *
   * <p>Jitter adds randomization to delays to prevent the "thundering herd" problem when many
   * clients retry simultaneously.
   *
   * @param maxAttempts maximum number of attempts (must be at least 1)
   * @param initialDelay the initial delay; must not be null
   * @return a RetryPolicy with exponential backoff and jitter
   * @throws IllegalArgumentException if maxAttempts is less than 1
   * @throws NullPointerException if initialDelay is null
   */
  public static RetryPolicy exponentialBackoffWithJitter(int maxAttempts, Duration initialDelay) {
    validateMaxAttempts(maxAttempts);
    Objects.requireNonNull(initialDelay, "initialDelay must not be null");
    return new RetryPolicy(maxAttempts, initialDelay, 2.0, Duration.ofMinutes(5), true, _ -> true);
  }

  /**
   * Creates a policy that doesn't retry (fails immediately on first error).
   *
   * @return a RetryPolicy with no retries
   */
  public static RetryPolicy noRetry() {
    return new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO, false, _ -> false);
  }

  /**
   * Returns a builder for creating custom retry policies.
   *
   * @return a new Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  // ===== Configuration Methods =====

  /**
   * Returns a copy of this policy with a different maximum number of attempts.
   *
   * @param maxAttempts the new maximum attempts (must be at least 1)
   * @return a new RetryPolicy with the updated setting
   * @throws IllegalArgumentException if maxAttempts is less than 1
   */
  public RetryPolicy withMaxAttempts(int maxAttempts) {
    validateMaxAttempts(maxAttempts);
    return new RetryPolicy(
        maxAttempts, initialDelay, backoffMultiplier, maxDelay, useJitter, retryPredicate);
  }

  /**
   * Returns a copy of this policy with a different initial delay.
   *
   * @param initialDelay the new initial delay; must not be null
   * @return a new RetryPolicy with the updated setting
   * @throws NullPointerException if initialDelay is null
   */
  public RetryPolicy withInitialDelay(Duration initialDelay) {
    Objects.requireNonNull(initialDelay, "initialDelay must not be null");
    return new RetryPolicy(
        maxAttempts, initialDelay, backoffMultiplier, maxDelay, useJitter, retryPredicate);
  }

  /**
   * Returns a copy of this policy with a different backoff multiplier.
   *
   * @param multiplier the new multiplier (must be at least 1.0)
   * @return a new RetryPolicy with the updated setting
   * @throws IllegalArgumentException if multiplier is less than 1.0
   */
  public RetryPolicy withBackoffMultiplier(double multiplier) {
    if (multiplier < 1.0) {
      throw new IllegalArgumentException("backoffMultiplier must be at least 1.0");
    }
    return new RetryPolicy(
        maxAttempts, initialDelay, multiplier, maxDelay, useJitter, retryPredicate);
  }

  /**
   * Returns a copy of this policy with a different maximum delay.
   *
   * @param maxDelay the new maximum delay; must not be null
   * @return a new RetryPolicy with the updated setting
   * @throws NullPointerException if maxDelay is null
   */
  public RetryPolicy withMaxDelay(Duration maxDelay) {
    Objects.requireNonNull(maxDelay, "maxDelay must not be null");
    return new RetryPolicy(
        maxAttempts, initialDelay, backoffMultiplier, maxDelay, useJitter, retryPredicate);
  }

  /**
   * Returns a copy of this policy that only retries for exceptions of the given type.
   *
   * @param exceptionClass the exception class to retry on
   * @return a new RetryPolicy that only retries on the specified exception type
   * @throws NullPointerException if exceptionClass is null
   */
  public RetryPolicy retryOn(Class<? extends Throwable> exceptionClass) {
    Objects.requireNonNull(exceptionClass, "exceptionClass must not be null");
    return new RetryPolicy(
        maxAttempts,
        initialDelay,
        backoffMultiplier,
        maxDelay,
        useJitter,
        ex -> exceptionClass.isInstance(ex));
  }

  /**
   * Returns a copy of this policy that retries based on a custom predicate.
   *
   * @param predicate the predicate to determine if a retry should occur
   * @return a new RetryPolicy with the custom retry predicate
   * @throws NullPointerException if predicate is null
   */
  public RetryPolicy retryIf(Predicate<Throwable> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new RetryPolicy(
        maxAttempts, initialDelay, backoffMultiplier, maxDelay, useJitter, predicate);
  }

  // ===== Accessors =====

  /**
   * Returns the maximum number of attempts.
   *
   * @return the maximum attempts
   */
  public int maxAttempts() {
    return maxAttempts;
  }

  /**
   * Returns the initial delay.
   *
   * @return the initial delay
   */
  public Duration initialDelay() {
    return initialDelay;
  }

  /**
   * Returns the backoff multiplier.
   *
   * @return the backoff multiplier
   */
  public double backoffMultiplier() {
    return backoffMultiplier;
  }

  /**
   * Returns the maximum delay.
   *
   * @return the maximum delay
   */
  public Duration maxDelay() {
    return maxDelay;
  }

  /**
   * Returns whether jitter is enabled.
   *
   * @return true if jitter is enabled
   */
  public boolean useJitter() {
    return useJitter;
  }

  /**
   * Tests whether the given exception should trigger a retry.
   *
   * @param throwable the exception to test
   * @return true if a retry should be attempted
   */
  public boolean shouldRetry(Throwable throwable) {
    return retryPredicate.test(throwable);
  }

  /**
   * Calculates the delay for a given attempt number.
   *
   * @param attemptNumber the attempt number (1-based)
   * @return the delay duration for this attempt
   */
  public Duration delayForAttempt(int attemptNumber) {
    if (attemptNumber <= 1) {
      return initialDelay;
    }

    // Calculate exponential delay
    double multiplier = Math.pow(backoffMultiplier, attemptNumber - 1);
    long delayMillis = (long) (initialDelay.toMillis() * multiplier);

    // Cap at maxDelay
    delayMillis = Math.min(delayMillis, maxDelay.toMillis());

    // Apply jitter if enabled (full jitter: 0 to delay)
    if (useJitter && delayMillis > 0) {
      delayMillis = ThreadLocalRandom.current().nextLong(delayMillis + 1);
    }

    return Duration.ofMillis(delayMillis);
  }

  // ===== Validation =====

  private static void validateMaxAttempts(int maxAttempts) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1");
    }
  }

  // ===== Builder =====

  /**
   * Builder for creating custom RetryPolicy instances.
   *
   * <p>Example:
   *
   * <pre>{@code
   * RetryPolicy policy = RetryPolicy.builder()
   *     .maxAttempts(5)
   *     .initialDelay(Duration.ofMillis(100))
   *     .backoffMultiplier(2.0)
   *     .maxDelay(Duration.ofSeconds(30))
   *     .useJitter(true)
   *     .retryIf(ex -> ex instanceof IOException)
   *     .build();
   * }</pre>
   */
  public static final class Builder {

    private int maxAttempts = 3;
    private Duration initialDelay = Duration.ofMillis(100);
    private double backoffMultiplier = 2.0;
    private Duration maxDelay = Duration.ofMinutes(5);
    private boolean useJitter = false;
    private Predicate<Throwable> retryPredicate = _ -> true;

    private Builder() {}

    /**
     * Sets the maximum number of attempts.
     *
     * @param maxAttempts the maximum attempts (must be at least 1)
     * @return this builder
     * @throws IllegalArgumentException if maxAttempts is less than 1
     */
    public Builder maxAttempts(int maxAttempts) {
      validateMaxAttempts(maxAttempts);
      this.maxAttempts = maxAttempts;
      return this;
    }

    /**
     * Sets the initial delay.
     *
     * @param initialDelay the initial delay; must not be null
     * @return this builder
     * @throws NullPointerException if initialDelay is null
     */
    public Builder initialDelay(Duration initialDelay) {
      this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay must not be null");
      return this;
    }

    /**
     * Sets the backoff multiplier.
     *
     * @param multiplier the multiplier (must be at least 1.0)
     * @return this builder
     * @throws IllegalArgumentException if multiplier is less than 1.0
     */
    public Builder backoffMultiplier(double multiplier) {
      if (multiplier < 1.0) {
        throw new IllegalArgumentException("backoffMultiplier must be at least 1.0");
      }
      this.backoffMultiplier = multiplier;
      return this;
    }

    /**
     * Sets the maximum delay.
     *
     * @param maxDelay the maximum delay; must not be null
     * @return this builder
     * @throws NullPointerException if maxDelay is null
     */
    public Builder maxDelay(Duration maxDelay) {
      this.maxDelay = Objects.requireNonNull(maxDelay, "maxDelay must not be null");
      return this;
    }

    /**
     * Sets whether to use jitter.
     *
     * @param useJitter true to enable jitter
     * @return this builder
     */
    public Builder useJitter(boolean useJitter) {
      this.useJitter = useJitter;
      return this;
    }

    /**
     * Sets the retry predicate.
     *
     * @param predicate the predicate to determine retries
     * @return this builder
     * @throws NullPointerException if predicate is null
     */
    public Builder retryIf(Predicate<Throwable> predicate) {
      this.retryPredicate = Objects.requireNonNull(predicate, "predicate must not be null");
      return this;
    }

    /**
     * Sets the retry predicate to retry only on the specified exception type.
     *
     * @param exceptionClass the exception class to retry on
     * @return this builder
     * @throws NullPointerException if exceptionClass is null
     */
    public Builder retryOn(Class<? extends Throwable> exceptionClass) {
      Objects.requireNonNull(exceptionClass, "exceptionClass must not be null");
      this.retryPredicate = ex -> exceptionClass.isInstance(ex);
      return this;
    }

    /**
     * Builds the RetryPolicy.
     *
     * @return the configured RetryPolicy
     */
    public RetryPolicy build() {
      return new RetryPolicy(
          maxAttempts, initialDelay, backoffMultiplier, maxDelay, useJitter, retryPredicate);
    }
  }

  @Override
  public String toString() {
    return "RetryPolicy{"
        + "maxAttempts="
        + maxAttempts
        + ", initialDelay="
        + initialDelay
        + ", backoffMultiplier="
        + backoffMultiplier
        + ", maxDelay="
        + maxDelay
        + ", useJitter="
        + useJitter
        + '}';
  }
}
