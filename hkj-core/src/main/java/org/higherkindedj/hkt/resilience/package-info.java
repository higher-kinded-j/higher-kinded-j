// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Resilience patterns for fault-tolerant applications.
 *
 * <p>This package provides retry policies and utilities for building resilient applications that
 * can handle transient failures gracefully.
 *
 * <h2>Core Types</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.resilience.RetryPolicy} - Configurable retry strategies
 *   <li>{@link org.higherkindedj.hkt.resilience.Retry} - Utility for executing with retry
 *   <li>{@link org.higherkindedj.hkt.resilience.RetryExhaustedException} - Exception when retries
 *       are exhausted
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Create a policy
 * RetryPolicy policy = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100))
 *     .withMaxDelay(Duration.ofSeconds(10))
 *     .retryOn(IOException.class);
 *
 * // Execute with retry
 * String result = Retry.execute(policy, () -> httpClient.get(url));
 *
 * // Or use with IOPath
 * IOPath<String> resilientOp = Path.io(() -> httpClient.get(url))
 *     .withRetry(policy);
 * }</pre>
 *
 * @see org.higherkindedj.hkt.resilience.RetryPolicy
 * @see org.higherkindedj.hkt.resilience.Retry
 */
@NullMarked
package org.higherkindedj.hkt.resilience;

import org.jspecify.annotations.NullMarked;
