// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Examples demonstrating resilience patterns for protecting services and coordinating distributed
 * operations.
 *
 * <p>This package contains runnable examples showing:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.resilience.CircuitBreakerExample} - Circuit breaker
 *       configuration, shared breakers across endpoints, fallback handling, and metrics monitoring
 *   <li>{@link org.higherkindedj.example.resilience.SagaOrderExample} - Saga pattern for
 *       distributed transaction compensation in an e-commerce order workflow
 *   <li>{@link org.higherkindedj.example.resilience.ResilientServiceExample} - Composing circuit
 *       breaker, retry, bulkhead, timeout, and fallback with {@code ResilienceBuilder}
 * </ul>
 *
 * <h2>Running Examples</h2>
 *
 * <p>Each example has a {@code main} method and can be run via Gradle:
 *
 * <pre>{@code
 * ./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.resilience.CircuitBreakerExample
 * ./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.resilience.SagaOrderExample
 * ./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.resilience.ResilientServiceExample
 * }</pre>
 *
 * @see org.higherkindedj.hkt.resilience.CircuitBreaker
 * @see org.higherkindedj.hkt.resilience.Saga
 * @see org.higherkindedj.hkt.resilience.Resilience
 * @see org.higherkindedj.hkt.resilience.ResilienceBuilder
 */
@NullMarked
package org.higherkindedj.example.resilience;

import org.jspecify.annotations.NullMarked;
