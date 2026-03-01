// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * A builder for composing multiple resilience patterns around a {@link VTask}.
 *
 * <p>Patterns are applied in a fixed order regardless of the order in which they are
 * specified on the builder:
 *
 * <ol>
 *   <li><b>Timeout</b> (outermost) bounds total elapsed time</li>
 *   <li><b>Bulkhead</b> limits concurrent access to the protected resource</li>
 *   <li><b>Retry</b> retries the inner operation on failure</li>
 *   <li><b>Circuit Breaker</b> (innermost) each attempt checks circuit state</li>
 * </ol>
 *
 * <p>Optionally, a fallback function is applied after all other patterns.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * VTask<String> resilient = ResilienceBuilder.of(
 *         VTask.of(() -> httpClient.get(url)))
 *     .withTimeout(Duration.ofSeconds(30))
 *     .withBulkhead(Bulkhead.withMaxConcurrent(10))
 *     .withRetry(RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(200))
 *         .retryOn(IOException.class))
 *     .withCircuitBreaker(serviceCircuitBreaker)
 *     .withFallback(ex -> "default response")
 *     .build();
 * }</pre>
 *
 * @param <A> the result type of the protected task
 * @see Resilience
 */
public final class ResilienceBuilder<A> {

  private final VTask<A> task;
  private @Nullable CircuitBreaker circuitBreaker;
  private @Nullable RetryPolicy retryPolicy;
  private @Nullable Bulkhead bulkhead;
  private @Nullable Duration timeout;
  private @Nullable Function<Throwable, A> fallback;

  private ResilienceBuilder(VTask<A> task) {
    this.task = task;
  }

  /**
   * Creates a new ResilienceBuilder for the given task.
   *
   * @param task the task to protect; must not be null
   * @param <A> the result type
   * @return a new ResilienceBuilder
   */
  static <A> ResilienceBuilder<A> of(VTask<A> task) {
    Objects.requireNonNull(task, "task must not be null");
    return new ResilienceBuilder<>(task);
  }

  /**
   * Adds circuit breaker protection.
   *
   * @param cb the circuit breaker; must not be null
   * @return this builder
   */
  public ResilienceBuilder<A> withCircuitBreaker(CircuitBreaker cb) {
    this.circuitBreaker = Objects.requireNonNull(cb, "circuitBreaker must not be null");
    return this;
  }

  /**
   * Adds circuit breaker protection with the given configuration.
   *
   * @param config the circuit breaker configuration; must not be null
   * @return this builder
   */
  public ResilienceBuilder<A> withCircuitBreaker(CircuitBreakerConfig config) {
    this.circuitBreaker = CircuitBreaker.create(config);
    return this;
  }

  /**
   * Adds retry with the given policy.
   *
   * @param policy the retry policy; must not be null
   * @return this builder
   */
  public ResilienceBuilder<A> withRetry(RetryPolicy policy) {
    this.retryPolicy = Objects.requireNonNull(policy, "policy must not be null");
    return this;
  }

  /**
   * Adds bulkhead concurrency limiting.
   *
   * @param bh the bulkhead; must not be null
   * @return this builder
   */
  public ResilienceBuilder<A> withBulkhead(Bulkhead bh) {
    this.bulkhead = Objects.requireNonNull(bh, "bulkhead must not be null");
    return this;
  }

  /**
   * Adds bulkhead concurrency limiting with the given configuration.
   *
   * @param config the bulkhead configuration; must not be null
   * @return this builder
   */
  public ResilienceBuilder<A> withBulkhead(BulkheadConfig config) {
    this.bulkhead = Bulkhead.create(config);
    return this;
  }

  /**
   * Adds a timeout for the entire protected operation.
   *
   * @param duration the timeout duration; must not be null
   * @return this builder
   */
  public ResilienceBuilder<A> withTimeout(Duration duration) {
    this.timeout = Objects.requireNonNull(duration, "timeout must not be null");
    return this;
  }

  /**
   * Adds a fallback function that produces a value when the protected operation fails.
   *
   * @param fallbackFn the fallback function; must not be null
   * @return this builder
   */
  public ResilienceBuilder<A> withFallback(Function<Throwable, A> fallbackFn) {
    this.fallback = Objects.requireNonNull(fallbackFn, "fallback must not be null");
    return this;
  }

  /**
   * Builds the protected VTask with all configured resilience patterns applied in the
   * correct order.
   *
   * @return the protected VTask
   */
  public VTask<A> build() {
    // Apply patterns from innermost to outermost:
    // Circuit Breaker (innermost) -> Retry -> Bulkhead -> Timeout (outermost)
    VTask<A> result = task;

    if (circuitBreaker != null) {
      result = circuitBreaker.protect(result);
    }

    if (retryPolicy != null) {
      result = Retry.retryTask(result, retryPolicy);
    }

    if (bulkhead != null) {
      result = bulkhead.protect(result);
    }

    if (timeout != null) {
      result = result.timeout(timeout);
    }

    if (fallback != null) {
      result = result.recover(fallback);
    }

    return result;
  }
}
