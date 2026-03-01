// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Utility class for combining multiple resilience patterns into a single protected operation.
 *
 * <p>Provides convenience methods for common combinations, and a {@link ResilienceBuilder}
 * for composing arbitrary patterns with explicit ordering.
 *
 * <h2>Pattern Application Order</h2>
 *
 * <p>When multiple patterns are combined, they are applied in this order (outermost to
 * innermost):
 *
 * <ol>
 *   <li><b>Timeout</b> (outermost) bounds total elapsed time</li>
 *   <li><b>Bulkhead</b> limits concurrent access to the protected resource</li>
 *   <li><b>Retry</b> retries the inner operation on failure</li>
 *   <li><b>Circuit Breaker</b> (innermost) each attempt checks circuit state</li>
 * </ol>
 *
 * <p>This ordering ensures that a retry attempt that fails due to {@link CircuitOpenException}
 * is not retried (circuit breaker failure is not retryable by default), and that the circuit
 * breaker sees each retry attempt individually.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * VTask<String> resilient = Resilience.<String>builder(
 *         VTask.of(() -> httpClient.get(url)))
 *     .withTimeout(Duration.ofSeconds(30))
 *     .withBulkhead(Bulkhead.withMaxConcurrent(10))
 *     .withRetry(RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(200)))
 *     .withCircuitBreaker(serviceCircuitBreaker)
 *     .withFallback(ex -> "default response")
 *     .build();
 * }</pre>
 *
 * @see ResilienceBuilder
 */
public final class Resilience {

  private Resilience() {
    // Utility class
  }

  /**
   * Wraps a task with circuit breaker protection and retry.
   *
   * @param task the task to protect
   * @param circuitBreaker the circuit breaker
   * @param retryPolicy the retry policy
   * @param <A> the result type
   * @return a protected VTask
   */
  public static <A> VTask<A> withCircuitBreakerAndRetry(
      VTask<A> task, CircuitBreaker circuitBreaker, RetryPolicy retryPolicy) {
    Objects.requireNonNull(task, "task must not be null");
    Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
    return Retry.retryTask(circuitBreaker.protect(task), retryPolicy);
  }

  /**
   * Wraps a task with all three core resilience patterns.
   *
   * @param task the task to protect
   * @param circuitBreaker the circuit breaker
   * @param retryPolicy the retry policy
   * @param bulkhead the bulkhead
   * @param <A> the result type
   * @return a protected VTask
   */
  public static <A> VTask<A> protect(
      VTask<A> task,
      CircuitBreaker circuitBreaker,
      RetryPolicy retryPolicy,
      Bulkhead bulkhead) {
    Objects.requireNonNull(task, "task must not be null");
    Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
    Objects.requireNonNull(bulkhead, "bulkhead must not be null");
    // Order: bulkhead -> retry -> circuit breaker -> task
    return bulkhead.protect(Retry.retryTask(circuitBreaker.protect(task), retryPolicy));
  }

  /**
   * Returns a builder for composing resilience patterns around the given task.
   *
   * @param task the task to protect
   * @param <A> the result type
   * @return a new ResilienceBuilder
   */
  public static <A> ResilienceBuilder<A> builder(VTask<A> task) {
    return ResilienceBuilder.of(task);
  }

  // ===== Stream Convenience Methods =====

  /**
   * Convenience for applying per-element retry to a stream via VTask composition.
   *
   * <p>Equivalent to: {@code stream.mapTask(a -> f.apply(a).retry(policy))}
   *
   * @param f the effectful function to apply to each element
   * @param policy the retry policy
   * @param <A> the input element type
   * @param <B> the output element type
   * @return a function that applies retry to each element's processing
   */
  public static <A, B> Function<A, VTask<B>> withRetryPerElement(
      Function<A, VTask<B>> f, RetryPolicy policy) {
    Objects.requireNonNull(f, "f must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    return a -> Retry.retryTask(f.apply(a), policy);
  }

  /**
   * Convenience for applying circuit breaker protection to a per-element function.
   *
   * <p>Equivalent to: {@code stream.mapTask(a -> cb.protect(f.apply(a)))}
   *
   * @param f the effectful function to apply to each element
   * @param circuitBreaker the circuit breaker
   * @param <A> the input element type
   * @param <B> the output element type
   * @return a function that protects each element's processing with the circuit breaker
   */
  public static <A, B> Function<A, VTask<B>> withCircuitBreakerPerElement(
      Function<A, VTask<B>> f, CircuitBreaker circuitBreaker) {
    Objects.requireNonNull(f, "f must not be null");
    Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    return a -> circuitBreaker.protect(f.apply(a));
  }
}
