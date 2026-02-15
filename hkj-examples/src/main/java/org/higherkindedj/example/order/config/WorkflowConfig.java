// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.config;

import java.time.Duration;

/**
 * Configuration for the order workflow.
 *
 * <p>This record is used with {@code ReaderPath} to thread configuration through the workflow
 * without explicit parameter passing.
 *
 * @param retryConfig retry configuration for transient failures
 * @param timeoutConfig timeout configuration for external calls
 * @param featureFlags feature toggles
 * @param environment deployment environment
 */
public record WorkflowConfig(
    RetryConfig retryConfig,
    TimeoutConfig timeoutConfig,
    FeatureFlags featureFlags,
    Environment environment) {
  /**
   * Creates a default configuration for production.
   *
   * @return default production configuration
   */
  public static WorkflowConfig defaults() {
    return new WorkflowConfig(
        RetryConfig.defaults(),
        TimeoutConfig.defaults(),
        FeatureFlags.defaults(),
        Environment.PRODUCTION);
  }

  /**
   * Creates a configuration for testing with faster timeouts.
   *
   * @return testing configuration
   */
  public static WorkflowConfig forTesting() {
    return new WorkflowConfig(
        RetryConfig.forTesting(),
        TimeoutConfig.forTesting(),
        new FeatureFlags(true, true, true),
        Environment.TESTING);
  }

  /**
   * Retry configuration.
   *
   * @param maxRetries maximum number of retry attempts
   * @param initialDelay initial delay before first retry
   * @param maxDelay maximum delay between retries
   * @param backoffMultiplier multiplier for exponential backoff
   */
  public record RetryConfig(
      int maxRetries, Duration initialDelay, Duration maxDelay, double backoffMultiplier) {
    public static RetryConfig defaults() {
      return new RetryConfig(3, Duration.ofMillis(100), Duration.ofSeconds(5), 2.0);
    }

    public static RetryConfig forTesting() {
      return new RetryConfig(1, Duration.ofMillis(10), Duration.ofMillis(50), 1.5);
    }
  }

  /**
   * Timeout configuration.
   *
   * @param paymentTimeout timeout for payment processing
   * @param inventoryTimeout timeout for inventory operations
   * @param shippingTimeout timeout for shipping operations
   * @param notificationTimeout timeout for notification sending
   */
  public record TimeoutConfig(
      Duration paymentTimeout,
      Duration inventoryTimeout,
      Duration shippingTimeout,
      Duration notificationTimeout) {
    public static TimeoutConfig defaults() {
      return new TimeoutConfig(
          Duration.ofSeconds(30),
          Duration.ofSeconds(10),
          Duration.ofSeconds(15),
          Duration.ofSeconds(5));
    }

    public static TimeoutConfig forTesting() {
      return new TimeoutConfig(
          Duration.ofMillis(100),
          Duration.ofMillis(50),
          Duration.ofMillis(50),
          Duration.ofMillis(25));
    }
  }

  /**
   * Feature flags for workflow behaviour.
   *
   * @param enablePartialFulfilment allow partial order fulfilment
   * @param enableSplitShipments allow splitting into multiple shipments
   * @param enableLoyaltyDiscounts apply loyalty tier discounts
   */
  public record FeatureFlags(
      boolean enablePartialFulfilment,
      boolean enableSplitShipments,
      boolean enableLoyaltyDiscounts) {
    public static FeatureFlags defaults() {
      return new FeatureFlags(true, true, true);
    }
  }

  /** Deployment environment. */
  public enum Environment {
    DEVELOPMENT,
    TESTING,
    STAGING,
    PRODUCTION
  }
}
