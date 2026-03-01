// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Instant;

/**
 * Immutable snapshot of a {@link CircuitBreaker}'s operational metrics.
 *
 * <p>Metrics are captured at a point in time and provide insight into how the circuit
 * breaker is performing: how many calls have been made, how many succeeded or failed,
 * how many were rejected because the circuit was open, and when the last state change
 * occurred.
 *
 * @param totalCalls total number of calls attempted (including rejected)
 * @param successfulCalls number of calls that completed successfully
 * @param failedCalls number of calls that failed (counted as failures by the predicate)
 * @param rejectedCalls number of calls rejected because the circuit was open
 * @param stateTransitions total number of state transitions
 * @param lastStateChange when the last state transition occurred
 * @see CircuitBreaker#metrics()
 */
public record CircuitBreakerMetrics(
    long totalCalls,
    long successfulCalls,
    long failedCalls,
    long rejectedCalls,
    long stateTransitions,
    Instant lastStateChange
) {}
