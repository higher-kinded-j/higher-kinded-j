// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.resilience;

import java.time.Duration;
import java.util.Objects;
import org.higherkindedj.example.market.model.PriceTick;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Applies resilience patterns to exchange feed streams.
 *
 * <p><b>HKJ features demonstrated:</b>
 *
 * <ul>
 *   <li>{@link CircuitBreaker} - Trips open when a feed produces too many errors
 *   <li>{@link RetryPolicy} - Retries transient feed failures with exponential backoff
 *   <li>{@link VStream#recover} - Replaces failed pulls with recovery behaviour
 *   <li>{@link VStream#recoverWith} - Substitutes a fallback stream on failure
 * </ul>
 */
public class FeedResilience {

  private final CircuitBreaker circuitBreaker;
  private final RetryPolicy retryPolicy;

  /**
   * Creates a resilience wrapper with custom configuration.
   *
   * @param circuitBreaker the circuit breaker to use
   * @param retryPolicy the retry policy to use
   */
  public FeedResilience(CircuitBreaker circuitBreaker, RetryPolicy retryPolicy) {
    this.circuitBreaker = Objects.requireNonNull(circuitBreaker);
    this.retryPolicy = Objects.requireNonNull(retryPolicy);
  }

  /** Creates a resilience wrapper with sensible defaults. */
  public static FeedResilience withDefaults() {
    CircuitBreakerConfig cbConfig =
        CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .openDuration(Duration.ofSeconds(5))
            .callTimeout(Duration.ofSeconds(2))
            .build();

    return new FeedResilience(
        CircuitBreaker.create(cbConfig), RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100)));
  }

  /**
   * Protects a single tick-producing VTask with circuit breaker and retry.
   *
   * @param task the task to protect
   * @return a protected task
   */
  public VTask<PriceTick> protect(VTask<PriceTick> task) {
    return circuitBreaker.protect(Retry.retryTask(task, retryPolicy));
  }

  /**
   * Wraps a tick stream so that individual pull failures are recovered with a fallback.
   *
   * @param stream the source stream
   * @param fallback the stream to use if the source fails
   * @return a resilient stream
   */
  public VStream<PriceTick> withFallback(VStream<PriceTick> stream, VStream<PriceTick> fallback) {
    return stream.recoverWith(error -> fallback);
  }

  /** Returns the underlying circuit breaker for status inspection. */
  public CircuitBreaker circuitBreaker() {
    return circuitBreaker;
  }
}
