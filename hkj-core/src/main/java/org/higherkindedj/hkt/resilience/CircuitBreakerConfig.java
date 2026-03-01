// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable configuration for a {@link CircuitBreaker}.
 *
 * <p>Configuration values control when the circuit breaker transitions between states
 * and which exceptions are counted as failures.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * CircuitBreakerConfig config = CircuitBreakerConfig.builder()
 *     .failureThreshold(5)
 *     .successThreshold(3)
 *     .openDuration(Duration.ofSeconds(30))
 *     .callTimeout(Duration.ofSeconds(5))
 *     .recordFailure(ex -> !(ex instanceof BusinessException))
 *     .build();
 * }</pre>
 *
 * @param failureThreshold failures in the closed state before the circuit opens
 * @param successThreshold successes in the half-open state before the circuit closes
 * @param openDuration how long the circuit stays open before transitioning to half-open
 * @param callTimeout timeout applied to each protected call via {@code VTask.timeout()}
 * @param recordFailure predicate that determines which exceptions count as failures
 * @see CircuitBreaker
 */
public record CircuitBreakerConfig(
    int failureThreshold,
    int successThreshold,
    Duration openDuration,
    Duration callTimeout,
    Predicate<Throwable> recordFailure
) {

  /** Default failure threshold. */
  public static final int DEFAULT_FAILURE_THRESHOLD = 5;

  /** Default success threshold in half-open state. */
  public static final int DEFAULT_SUCCESS_THRESHOLD = 1;

  /** Default duration the circuit stays open. */
  public static final Duration DEFAULT_OPEN_DURATION = Duration.ofSeconds(60);

  /** Default call timeout. */
  public static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(10);

  /**
   * Creates a CircuitBreakerConfig with validated parameters.
   */
  public CircuitBreakerConfig {
    if (failureThreshold < 1) {
      throw new IllegalArgumentException("failureThreshold must be at least 1");
    }
    if (successThreshold < 1) {
      throw new IllegalArgumentException("successThreshold must be at least 1");
    }
    Objects.requireNonNull(openDuration, "openDuration must not be null");
    Objects.requireNonNull(callTimeout, "callTimeout must not be null");
    Objects.requireNonNull(recordFailure, "recordFailure must not be null");
  }

  /**
   * Returns a builder for creating custom configurations.
   *
   * @return a new Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns a configuration with sensible defaults.
   *
   * @return the default configuration
   */
  public static CircuitBreakerConfig defaults() {
    return builder().build();
  }

  /**
   * Builder for creating custom {@link CircuitBreakerConfig} instances.
   */
  public static final class Builder {

    private int failureThreshold = DEFAULT_FAILURE_THRESHOLD;
    private int successThreshold = DEFAULT_SUCCESS_THRESHOLD;
    private Duration openDuration = DEFAULT_OPEN_DURATION;
    private Duration callTimeout = DEFAULT_CALL_TIMEOUT;
    private Predicate<Throwable> recordFailure = _ -> true;

    private Builder() {}

    /**
     * Sets the number of failures before the circuit opens.
     *
     * @param threshold the failure threshold (must be at least 1)
     * @return this builder
     */
    public Builder failureThreshold(int threshold) {
      this.failureThreshold = threshold;
      return this;
    }

    /**
     * Sets the number of successes in half-open state before the circuit closes.
     *
     * @param threshold the success threshold (must be at least 1)
     * @return this builder
     */
    public Builder successThreshold(int threshold) {
      this.successThreshold = threshold;
      return this;
    }

    /**
     * Sets how long the circuit stays open before transitioning to half-open.
     *
     * @param duration the open duration; must not be null
     * @return this builder
     */
    public Builder openDuration(Duration duration) {
      this.openDuration = Objects.requireNonNull(duration, "duration must not be null");
      return this;
    }

    /**
     * Sets the timeout applied to each protected call.
     *
     * @param timeout the call timeout; must not be null
     * @return this builder
     */
    public Builder callTimeout(Duration timeout) {
      this.callTimeout = Objects.requireNonNull(timeout, "timeout must not be null");
      return this;
    }

    /**
     * Sets the predicate that determines which exceptions count as failures.
     *
     * @param predicate the failure predicate; must not be null
     * @return this builder
     */
    public Builder recordFailure(Predicate<Throwable> predicate) {
      this.recordFailure = Objects.requireNonNull(predicate, "predicate must not be null");
      return this;
    }

    /**
     * Builds the CircuitBreakerConfig.
     *
     * @return the configured CircuitBreakerConfig
     */
    public CircuitBreakerConfig build() {
      return new CircuitBreakerConfig(
          failureThreshold, successThreshold, openDuration, callTimeout, recordFailure);
    }
  }
}
