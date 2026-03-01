// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * A circuit breaker that protects {@link VTask} operations from repeatedly calling a failing
 * service.
 *
 * <p>The circuit breaker tracks the health of a dependency through three states:
 *
 * <ul>
 *   <li><b>CLOSED</b> (normal operation): calls flow through. Failures are counted; when
 *       consecutive failures reach the threshold, the circuit opens.
 *   <li><b>OPEN</b> (failing fast): all calls are immediately rejected with
 *       {@link CircuitOpenException}. After the configured open duration, the circuit
 *       transitions to half-open.
 *   <li><b>HALF_OPEN</b> (probing): a limited number of calls are allowed through as probes.
 *       If enough probes succeed, the circuit closes. If a probe fails, the circuit re-opens.
 * </ul>
 *
 * <p>A single {@code CircuitBreaker} instance should be shared across all callers of the same
 * service. The {@link #protect(VTask)} method is generic, so one circuit breaker can protect
 * calls that return different types.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * CircuitBreaker breaker = CircuitBreaker.create(
 *     CircuitBreakerConfig.builder()
 *         .failureThreshold(5)
 *         .openDuration(Duration.ofSeconds(30))
 *         .build());
 *
 * VTask<String> protectedCall = breaker.protect(
 *     VTask.of(() -> httpClient.get(url)));
 *
 * VTask<Integer> protectedOtherCall = breaker.protect(
 *     VTask.of(() -> httpClient.getCount(url)));
 * }</pre>
 *
 * @see CircuitBreakerConfig
 * @see CircuitOpenException
 */
public final class CircuitBreaker {

  /** The possible states of the circuit breaker. */
  public enum State {
    /** Normal operation: calls are allowed through. */
    CLOSED,
    /** Failing fast: all calls are rejected immediately. */
    OPEN,
    /** Probing: a limited number of calls are allowed to test recovery. */
    HALF_OPEN
  }

  private record InternalState(
      State state,
      int failureCount,
      int successCount,
      Instant lastStateChange
  ) {}

  private final CircuitBreakerConfig config;
  private final AtomicReference<InternalState> stateRef;

  // Metrics counters
  private final AtomicLong totalCalls = new AtomicLong();
  private final AtomicLong successfulCalls = new AtomicLong();
  private final AtomicLong failedCalls = new AtomicLong();
  private final AtomicLong rejectedCalls = new AtomicLong();
  private final AtomicLong stateTransitions = new AtomicLong();

  private CircuitBreaker(CircuitBreakerConfig config) {
    this.config = config;
    this.stateRef = new AtomicReference<>(
        new InternalState(State.CLOSED, 0, 0, Instant.now()));
  }

  // ===== Factory Methods =====

  /**
   * Creates a circuit breaker with the given configuration.
   *
   * @param config the configuration; must not be null
   * @return a new CircuitBreaker
   * @throws NullPointerException if config is null
   */
  public static CircuitBreaker create(CircuitBreakerConfig config) {
    Objects.requireNonNull(config, "config must not be null");
    return new CircuitBreaker(config);
  }

  /**
   * Creates a circuit breaker with default configuration.
   *
   * @return a new CircuitBreaker with default settings
   */
  public static CircuitBreaker withDefaults() {
    return new CircuitBreaker(CircuitBreakerConfig.defaults());
  }

  // ===== Protection =====

  /**
   * Returns a new {@link VTask} that is protected by this circuit breaker.
   *
   * <p>If the circuit is closed or half-open, the task executes normally. If it succeeds,
   * success is recorded. If it fails with an exception that matches the
   * {@link CircuitBreakerConfig#recordFailure()} predicate, the failure is recorded. If the
   * circuit is open, the task is immediately rejected with {@link CircuitOpenException}.
   *
   * <p>The call timeout from the configuration is applied using {@code VTask.timeout()}.
   *
   * @param task the task to protect; must not be null
   * @param <A> the result type
   * @return a new VTask protected by this circuit breaker
   * @throws NullPointerException if task is null
   */
  public <A> VTask<A> protect(VTask<A> task) {
    Objects.requireNonNull(task, "task must not be null");
    return () -> {
      totalCalls.incrementAndGet();

      InternalState current = stateRef.get();

      // Check for OPEN -> HALF_OPEN transition
      if (current.state() == State.OPEN) {
        Duration elapsed = Duration.between(current.lastStateChange(), Instant.now());
        if (elapsed.compareTo(config.openDuration()) >= 0) {
          // Attempt transition to HALF_OPEN
          InternalState halfOpen = new InternalState(
              State.HALF_OPEN, 0, 0, Instant.now());
          if (stateRef.compareAndSet(current, halfOpen)) {
            stateTransitions.incrementAndGet();
            current = halfOpen;
          } else {
            current = stateRef.get();
          }
        }
      }

      // Reject if still OPEN
      if (current.state() == State.OPEN) {
        rejectedCalls.incrementAndGet();
        Duration remaining = config.openDuration().minus(
            Duration.between(current.lastStateChange(), Instant.now()));
        if (remaining.isNegative()) {
          remaining = Duration.ZERO;
        }
        throw new CircuitOpenException(State.OPEN, remaining);
      }

      // Execute the task with timeout
      try {
        A result = task.timeout(config.callTimeout()).run();
        onSuccess();
        return result;
      } catch (Throwable t) {
        if (config.recordFailure().test(t)) {
          onFailure();
        } else {
          // Exception not counted as failure (e.g., business exception)
          onSuccess();
        }
        throw t;
      }
    };
  }

  /**
   * Returns a new {@link VTask} protected by this circuit breaker, with a fallback value
   * when the circuit is open.
   *
   * @param task the task to protect; must not be null
   * @param fallback function to produce a fallback value when the circuit is open;
   *     must not be null
   * @param <A> the result type
   * @return a new VTask with circuit breaker protection and fallback
   * @throws NullPointerException if task or fallback is null
   */
  public <A> VTask<A> protectWithFallback(
      VTask<A> task, Function<Throwable, A> fallback) {
    Objects.requireNonNull(task, "task must not be null");
    Objects.requireNonNull(fallback, "fallback must not be null");
    return protect(task).recover(ex -> {
      if (ex instanceof CircuitOpenException) {
        return fallback.apply(ex);
      }
      throw (ex instanceof RuntimeException re) ? re : new RuntimeException(ex);
    });
  }

  // ===== State Inspection =====

  /**
   * Returns the current state of the circuit breaker.
   *
   * @return the current state
   */
  public State currentState() {
    InternalState current = stateRef.get();
    // Check for pending OPEN -> HALF_OPEN transition
    if (current.state() == State.OPEN) {
      Duration elapsed = Duration.between(current.lastStateChange(), Instant.now());
      if (elapsed.compareTo(config.openDuration()) >= 0) {
        return State.HALF_OPEN;
      }
    }
    return current.state();
  }

  /**
   * Returns a snapshot of the circuit breaker's metrics.
   *
   * @return the current metrics
   */
  public CircuitBreakerMetrics metrics() {
    InternalState current = stateRef.get();
    return new CircuitBreakerMetrics(
        totalCalls.get(),
        successfulCalls.get(),
        failedCalls.get(),
        rejectedCalls.get(),
        stateTransitions.get(),
        current.lastStateChange());
  }

  // ===== Manual Control =====

  /**
   * Resets the circuit breaker to the closed state with zeroed counters.
   */
  public void reset() {
    stateRef.set(new InternalState(State.CLOSED, 0, 0, Instant.now()));
    stateTransitions.incrementAndGet();
  }

  /**
   * Manually trips the circuit breaker to the open state.
   */
  public void tripOpen() {
    stateRef.set(new InternalState(State.OPEN, 0, 0, Instant.now()));
    stateTransitions.incrementAndGet();
  }

  // ===== Internal State Management =====

  private void onSuccess() {
    successfulCalls.incrementAndGet();
    stateRef.getAndUpdate(current -> switch (current.state()) {
      case CLOSED -> new InternalState(State.CLOSED, 0, 0, current.lastStateChange());
      case HALF_OPEN -> {
        int newSuccesses = current.successCount() + 1;
        if (newSuccesses >= config.successThreshold()) {
          stateTransitions.incrementAndGet();
          yield new InternalState(State.CLOSED, 0, 0, Instant.now());
        }
        yield new InternalState(State.HALF_OPEN, 0, newSuccesses, current.lastStateChange());
      }
      case OPEN -> current; // Should not happen during execution
    });
  }

  private void onFailure() {
    failedCalls.incrementAndGet();
    stateRef.getAndUpdate(current -> switch (current.state()) {
      case CLOSED -> {
        int newFailures = current.failureCount() + 1;
        if (newFailures >= config.failureThreshold()) {
          stateTransitions.incrementAndGet();
          yield new InternalState(State.OPEN, 0, 0, Instant.now());
        }
        yield new InternalState(State.CLOSED, newFailures, 0, current.lastStateChange());
      }
      case HALF_OPEN -> {
        stateTransitions.incrementAndGet();
        yield new InternalState(State.OPEN, 0, 0, Instant.now());
      }
      case OPEN -> current; // Should not happen during execution
    });
  }
}
