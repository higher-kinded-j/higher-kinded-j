// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
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
 *   <li><b>OPEN</b> (failing fast): all calls are immediately rejected with {@link
 *       CircuitOpenException}. After the configured open duration, the circuit transitions to
 *       half-open.
 *   <li><b>HALF_OPEN</b> (probing): a limited number of calls are allowed through as probes. If
 *       enough probes succeed, the circuit closes. If a probe fails, the circuit re-opens.
 * </ul>
 *
 * <p>A single {@code CircuitBreaker} instance should be shared across all callers of the same
 * service. The {@link #protect(VTask)} method is generic, so one circuit breaker can protect calls
 * that return different types.
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
  public enum Status {
    /** Normal operation: calls are allowed through. */
    CLOSED,
    /** Failing fast: all calls are rejected immediately. */
    OPEN,
    /** Probing: a limited number of calls are allowed to test recovery. */
    HALF_OPEN
  }

  private record InternalState(
      Status status, int failureCount, int successCount, Instant lastStateChange) {}

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
    this.stateRef = new AtomicReference<>(new InternalState(Status.CLOSED, 0, 0, Instant.now()));
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
   * <p>If the circuit is closed or half-open, the task executes normally. If it succeeds, success
   * is recorded. If it fails with an exception that matches the {@link
   * CircuitBreakerConfig#recordFailure()} predicate, the failure is recorded. If the circuit is
   * open, the task is immediately rejected with {@link CircuitOpenException}.
   *
   * <p>The call timeout from the configuration is applied using {@code VTask.timeout()}.
   *
   * <p>A call cancelled by an interrupt counts as neither a success nor a failure, whatever {@link
   * CircuitBreakerConfig#recordFailure()} says, since it tells nothing about the protected service.
   * Its {@code InterruptedException} passes through as thrown.
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
      if (current.status() == Status.OPEN) {
        Duration elapsed = Duration.between(current.lastStateChange(), Instant.now());
        if (elapsed.compareTo(config.openDuration()) >= 0) {
          // Attempt transition to HALF_OPEN
          InternalState halfOpen = new InternalState(Status.HALF_OPEN, 0, 0, Instant.now());
          if (stateRef.compareAndSet(current, halfOpen)) {
            stateTransitions.incrementAndGet();
            current = halfOpen;
          } else {
            current = stateRef.get();
          }
        }
      }

      // Reject if still OPEN
      if (current.status() == Status.OPEN) {
        rejectedCalls.incrementAndGet();
        // In saturating nanoseconds, with a wall clock that stepped back counted as no time
        long elapsedNanos =
            Math.max(
                0,
                TimeUnit.NANOSECONDS.convert(
                    Duration.between(current.lastStateChange(), Instant.now())));
        long remainingNanos =
            Math.max(0, TimeUnit.NANOSECONDS.convert(config.openDuration()) - elapsedNanos);
        throw new CircuitOpenException(Status.OPEN, Duration.ofNanos(remainingNanos));
      }

      // Execute the task with timeout (using execute() to preserve original exception types)
      try {
        A result = task.timeout(config.callTimeout()).execute();
        onSuccess();
        return result;
      } catch (InterruptedException cancelled) {
        // A cancelled call says nothing about the service, so it is not recorded
        throw cancelled;
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
   * Returns a new {@link VTask} protected by this circuit breaker, with a fallback value when the
   * circuit is open.
   *
   * <p>Any other failure passes through as thrown, as it does from {@link #protect}.
   *
   * @param task the task to protect; must not be null
   * @param fallback function to produce a fallback value when the circuit is open; must not be
   *     null, and must not return null: a null result fails the task with a {@link
   *     NullPointerException}
   * @param <A> the result type
   * @return a new VTask with circuit breaker protection and fallback
   * @throws NullPointerException if task or fallback is null
   */
  public <A> VTask<A> protectWithFallback(VTask<A> task, Function<Throwable, A> fallback) {
    Objects.requireNonNull(task, "task must not be null");
    Objects.requireNonNull(fallback, "fallback must not be null");
    VTask<A> protectedTask = protect(task);
    return () -> {
      try {
        return protectedTask.execute();
      } catch (CircuitOpenException open) {
        return Objects.requireNonNull(fallback.apply(open), "fallback must not return null");
      }
    };
  }

  // ===== State Inspection =====

  /**
   * Returns the current state of the circuit breaker.
   *
   * @return the current state
   */
  public Status currentStatus() {
    InternalState current = stateRef.get();
    // Check for pending OPEN -> HALF_OPEN transition
    if (current.status() == Status.OPEN) {
      Duration elapsed = Duration.between(current.lastStateChange(), Instant.now());
      if (elapsed.compareTo(config.openDuration()) >= 0) {
        return Status.HALF_OPEN;
      }
    }
    return current.status();
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

  /** Resets the circuit breaker to the closed state with zeroed counters. */
  public void reset() {
    stateRef.set(new InternalState(Status.CLOSED, 0, 0, Instant.now()));
    stateTransitions.incrementAndGet();
  }

  /** Manually trips the circuit breaker to the open state. */
  public void tripOpen() {
    stateRef.set(new InternalState(Status.OPEN, 0, 0, Instant.now()));
    stateTransitions.incrementAndGet();
  }

  // ===== Internal State Management =====

  private void onSuccess() {
    successfulCalls.incrementAndGet();
    InternalState prev =
        stateRef.getAndUpdate(
            current ->
                switch (current.status()) {
                  case CLOSED ->
                      current.failureCount() > 0
                          ? new InternalState(Status.CLOSED, 0, 0, current.lastStateChange())
                          : current;
                  case OPEN -> current; // OPEN state: must NOT transition to CLOSED
                  case HALF_OPEN -> {
                    int newSuccesses = current.successCount() + 1;
                    if (newSuccesses >= config.successThreshold()) {
                      yield new InternalState(Status.CLOSED, 0, 0, Instant.now());
                    }
                    yield new InternalState(
                        Status.HALF_OPEN, 0, newSuccesses, current.lastStateChange());
                  }
                });
    // Determine if this call caused a state transition based on the previous state.
    // This is done outside the CAS lambda to avoid double-counting on retries.
    if (prev.status() == Status.HALF_OPEN && prev.successCount() + 1 >= config.successThreshold()) {
      stateTransitions.incrementAndGet();
    }
  }

  private void onFailure() {
    failedCalls.incrementAndGet();
    InternalState prev =
        stateRef.getAndUpdate(
            current ->
                switch (current.status()) {
                  case CLOSED -> {
                    int newFailures = current.failureCount() + 1;
                    if (newFailures >= config.failureThreshold()) {
                      yield new InternalState(Status.OPEN, 0, 0, Instant.now());
                    }
                    yield new InternalState(
                        Status.CLOSED, newFailures, 0, current.lastStateChange());
                  }
                  case HALF_OPEN -> new InternalState(Status.OPEN, 0, 0, Instant.now());
                  case OPEN -> current; // Should not happen during execution
                });
    // Determine if this call caused a state transition based on the previous state.
    // This is done outside the CAS lambda to avoid double-counting on retries.
    boolean transitioned =
        (prev.status() == Status.CLOSED && prev.failureCount() + 1 >= config.failureThreshold())
            || prev.status() == Status.HALF_OPEN;
    if (transitioned) {
      stateTransitions.incrementAndGet();
    }
  }
}
