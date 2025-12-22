// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Resilience patterns for the order workflow.
 *
 * <p>This package provides retry, timeout, and circuit breaker patterns that integrate with the
 * Effect API for fault-tolerant workflow execution.
 *
 * <p>Key classes:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.order.resilience.RetryPolicy} - Configurable retry with
 *       exponential backoff
 *   <li>{@link org.higherkindedj.example.order.resilience.Resilience} - Utility methods for
 *       applying resilience patterns
 * </ul>
 */
@NullMarked
package org.higherkindedj.example.order.resilience;

import org.jspecify.annotations.NullMarked;
