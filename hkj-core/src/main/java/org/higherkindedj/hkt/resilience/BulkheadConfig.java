// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for a {@link Bulkhead}.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * BulkheadConfig config = BulkheadConfig.builder()
 *     .maxConcurrent(10)
 *     .maxWait(20)
 *     .waitTimeout(Duration.ofSeconds(5))
 *     .fairness(true)
 *     .build();
 * }</pre>
 *
 * @param maxConcurrent maximum number of concurrent executions allowed
 * @param maxWait maximum number of callers that can wait for a permit
 * @param waitTimeout how long a caller will wait for a permit before giving up
 * @param fairness whether waiting callers are served in FIFO order
 * @see Bulkhead
 */
public record BulkheadConfig(
    int maxConcurrent,
    int maxWait,
    Duration waitTimeout,
    boolean fairness
) {

  /** Default maximum concurrent executions. */
  public static final int DEFAULT_MAX_CONCURRENT = 10;

  /** Default maximum waiting callers. */
  public static final int DEFAULT_MAX_WAIT = 0;

  /** Default wait timeout. */
  public static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(5);

  /**
   * Creates a BulkheadConfig with validated parameters.
   */
  public BulkheadConfig {
    if (maxConcurrent < 1) {
      throw new IllegalArgumentException("maxConcurrent must be at least 1");
    }
    if (maxWait < 0) {
      throw new IllegalArgumentException("maxWait must not be negative");
    }
    Objects.requireNonNull(waitTimeout, "waitTimeout must not be null");
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
   * Builder for creating custom {@link BulkheadConfig} instances.
   */
  public static final class Builder {

    private int maxConcurrent = DEFAULT_MAX_CONCURRENT;
    private int maxWait = DEFAULT_MAX_WAIT;
    private Duration waitTimeout = DEFAULT_WAIT_TIMEOUT;
    private boolean fairness = false;

    private Builder() {}

    /**
     * Sets the maximum number of concurrent executions.
     *
     * @param max the maximum concurrent (must be at least 1)
     * @return this builder
     */
    public Builder maxConcurrent(int max) {
      this.maxConcurrent = max;
      return this;
    }

    /**
     * Sets the maximum number of callers that can wait for a permit.
     *
     * @param max the maximum waiting callers (must not be negative)
     * @return this builder
     */
    public Builder maxWait(int max) {
      this.maxWait = max;
      return this;
    }

    /**
     * Sets how long a caller will wait for a permit.
     *
     * @param timeout the wait timeout; must not be null
     * @return this builder
     */
    public Builder waitTimeout(Duration timeout) {
      this.waitTimeout = Objects.requireNonNull(timeout, "timeout must not be null");
      return this;
    }

    /**
     * Sets whether waiting callers are served in FIFO order.
     *
     * @param fair true for FIFO ordering
     * @return this builder
     */
    public Builder fairness(boolean fair) {
      this.fairness = fair;
      return this;
    }

    /**
     * Builds the BulkheadConfig.
     *
     * @return the configured BulkheadConfig
     */
    public BulkheadConfig build() {
      return new BulkheadConfig(maxConcurrent, maxWait, waitTimeout, fairness);
    }
  }
}
