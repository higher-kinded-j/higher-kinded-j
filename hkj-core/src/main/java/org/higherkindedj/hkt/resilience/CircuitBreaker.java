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
 *   <li><b>HALF_OPEN</b> (probing): at most {@link CircuitBreakerConfig#successThreshold()} calls
 *       are let through, as probes, and any other call is rejected with a {@link
 *       CircuitOpenException} whose {@link CircuitOpenException#status() status} is {@code
 *       HALF_OPEN}. If that many probes succeed, the circuit closes; if one fails, it re-opens. A
 *       probe that is cancelled gives up its place to the next call.
 * </ul>
 *
 * <p>A call counts only towards the state it was admitted in. One still running when the circuit
 * changes state, by its own thresholds or by {@link #reset()} or {@link #tripOpen()}, changes
 * nothing when it finishes, though {@link #metrics()} still counts it: a success from before an
 * outage cannot close a half-open circuit.
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
    /**
     * Probing: at most {@link CircuitBreakerConfig#successThreshold()} calls are let through to
     * test recovery.
     */
    HALF_OPEN
  }

  /**
   * One state of the breaker. Each change of status starts a new {@code generation}, and a call is
   * admitted under one: when it finishes, it moves the breaker only if that generation is still
   * current. {@code probes} counts the half-open probes still running.
   */
  private record InternalState(
      Status status,
      long generation,
      int failureCount,
      int successCount,
      int probes,
      Instant lastStateChange) {

    /** The next generation, in {@code status}, with every count at zero. */
    InternalState moveTo(Status status, Instant now) {
      return new InternalState(status, generation + 1, 0, 0, 0, now);
    }

    InternalState withFailures(int failures) {
      return new InternalState(status, generation, failures, successCount, probes, lastStateChange);
    }

    InternalState withProbes(int probes) {
      return new InternalState(
          status, generation, failureCount, successCount, probes, lastStateChange);
    }

    /** A probe has succeeded: it leaves the running count for the success count. */
    InternalState probeSucceeded() {
      return new InternalState(
          status, generation, failureCount, successCount + 1, probes - 1, lastStateChange);
    }
  }

  /** What a call arriving at {@code state} finds: the state it leaves, and whether it may run. */
  private record Admission(InternalState next, boolean admitted) {}

  /** How an admitted call ended, as the breaker counts it. */
  private enum Outcome {
    SUCCESS,
    FAILURE,
    CANCELLED
  }

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
    this.stateRef =
        new AtomicReference<>(new InternalState(Status.CLOSED, 0, 0, 0, 0, Instant.now()));
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
   * <p>If the circuit is closed, the task executes normally. If it succeeds, success is recorded.
   * If it fails with an exception that matches the {@link CircuitBreakerConfig#recordFailure()}
   * predicate, the failure is recorded; one the predicate does not select, such as a business
   * exception, counts as a success. If the predicate itself throws, the call counts as a failure,
   * and the predicate's exception is added to the call's as suppressed. If the circuit is open, the
   * task is rejected at once with a {@link CircuitOpenException} whose {@link
   * CircuitOpenException#retryAfter() retryAfter} is the time left before the circuit half-opens.
   *
   * <p>If the circuit is half-open, the task runs as a probe while fewer than {@link
   * CircuitBreakerConfig#successThreshold()} probes are running or have succeeded. Otherwise it is
   * rejected with a {@link CircuitOpenException} whose status is {@code HALF_OPEN} and whose {@code
   * retryAfter} is the call timeout, by which a running probe that stops when interrupted has given
   * up its place.
   *
   * <p>An outcome is recorded against the state the call was admitted in. If the circuit has
   * changed state since, the call still passes its result or exception through, and {@link
   * #metrics()} counts it, but it moves the circuit no further.
   *
   * <p>The call timeout from the configuration is applied using {@code VTask.timeout()}: a call
   * slower than that is interrupted, and its {@code TimeoutException} is offered to {@link
   * CircuitBreakerConfig#recordFailure()} once the call has stopped. For a call that can hang where
   * an interrupt cannot reach it, protect {@code task.detachOnCancel()}, so that the failure is
   * recorded when the time runs out and the circuit can open. Otherwise such a call, running as a
   * probe, keeps its place until it stops, and the circuit turns every other call away meanwhile.
   *
   * <p>Protect a call with one breaker once. A call that a breaker protects twice, as {@code
   * io.withCircuitBreaker(b).map(f).withCircuitBreaker(b)} does, counts each outcome twice, and
   * while the circuit is half-open its inner call can find no probe place free.
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
      long generation = admit();
      A result;
      // Execute the task with timeout (using execute() to preserve original exception types)
      try {
        result = task.timeout(config.callTimeout()).execute();
      } catch (InterruptedException cancelled) {
        // A cancelled call says nothing about the service; a probe gives up its place
        settle(generation, Outcome.CANCELLED);
        throw cancelled;
      } catch (Throwable t) {
        settle(generation, outcomeOf(t));
        throw t;
      }
      settle(generation, Outcome.SUCCESS);
      return result;
    };
  }

  /**
   * Admits a call arriving now and returns the generation it is admitted under, or throws the
   * {@link CircuitOpenException} that turns it away.
   */
  private long admit() {
    // One clock reading, so the update and the decision read after it agree on the open duration
    Instant now = Instant.now();
    InternalState seen = stateRef.get();
    // A closed circuit admits a call, and a rejecting one turns it away, without a write
    InternalState arrived =
        admission(seen, now).next() == seen
            ? seen
            : stateRef.getAndUpdate(state -> admission(state, now).next());
    Admission admission = admission(arrived, now);
    if (!admission.admitted()) {
      rejectedCalls.incrementAndGet();
      throw refusal(arrived, now);
    }
    if (admission.next().generation() != arrived.generation()) {
      stateTransitions.incrementAndGet();
    }
    return admission.next().generation();
  }

  /**
   * Whether a call arriving at {@code state} at {@code now} may run, and the state it leaves: an
   * open circuit whose time is up half-opens with the call as its first probe, and a half-open one
   * takes a probe while fewer than the success threshold are running or have succeeded.
   */
  private Admission admission(InternalState state, Instant now) {
    return switch (state.status()) {
      case CLOSED -> new Admission(state, true);
      case OPEN ->
          openDurationElapsed(state, now)
              ? new Admission(state.moveTo(Status.HALF_OPEN, now).withProbes(1), true)
              : new Admission(state, false);
      case HALF_OPEN ->
          state.probes() + state.successCount() < config.successThreshold()
              ? new Admission(state.withProbes(state.probes() + 1), true)
              : new Admission(state, false);
    };
  }

  private boolean openDurationElapsed(InternalState state, Instant now) {
    return Duration.between(state.lastStateChange(), now).compareTo(config.openDuration()) >= 0;
  }

  /** The exception that turns away a call arriving at {@code state}, open or with no probe free. */
  private CircuitOpenException refusal(InternalState state, Instant now) {
    return switch (state.status()) {
      case HALF_OPEN -> new CircuitOpenException(Status.HALF_OPEN, config.callTimeout());
      // A closed circuit turns no call away, so this one found it open
      case OPEN, CLOSED -> new CircuitOpenException(Status.OPEN, openTimeLeft(state, now));
    };
  }

  /** In saturating nanoseconds, with a wall clock that stepped back counted as no time. */
  private Duration openTimeLeft(InternalState state, Instant now) {
    long elapsedNanos =
        Math.max(0, TimeUnit.NANOSECONDS.convert(Duration.between(state.lastStateChange(), now)));
    return Duration.ofNanos(
        Math.max(0, TimeUnit.NANOSECONDS.convert(config.openDuration()) - elapsedNanos));
  }

  /**
   * How a call that threw {@code failure} counts: a failure if the predicate selects it, else a
   * success. A predicate that throws cannot say, so the call counts as a failure, and the
   * predicate's exception goes with the call's as suppressed.
   */
  private Outcome outcomeOf(Throwable failure) {
    try {
      return config.recordFailure().test(failure) ? Outcome.FAILURE : Outcome.SUCCESS;
    } catch (Throwable predicateFailure) {
      if (predicateFailure != failure) {
        failure.addSuppressed(predicateFailure);
      }
      return Outcome.FAILURE;
    }
  }

  /**
   * Returns a new {@link VTask} protected by this circuit breaker, with a fallback value when the
   * breaker rejects the call: it is open, or half-open with every probe place taken.
   *
   * <p>Any other failure passes through as thrown, as it does from {@link #protect}.
   *
   * @param task the task to protect; must not be null
   * @param fallback function to produce a fallback value from the breaker's {@link
   *     CircuitOpenException}; must not be null, and must not return null: a null result fails the
   *     task with a {@link NullPointerException}
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
   * <p>An open circuit whose open duration has passed is reported as {@code HALF_OPEN}, before the
   * first probe arrives. {@code HALF_OPEN} does not promise that a call will run: one is rejected
   * while every probe place is taken.
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

  /**
   * Resets the circuit breaker to the closed state, with its failure and probe counts at zero;
   * {@link #metrics()} keeps its totals. A call still running does not move the circuit when it
   * finishes.
   */
  public void reset() {
    Instant now = Instant.now();
    stateRef.updateAndGet(state -> state.moveTo(Status.CLOSED, now));
    stateTransitions.incrementAndGet();
  }

  /**
   * Manually trips the circuit breaker to the open state. A call still running does not move the
   * circuit when it finishes.
   */
  public void tripOpen() {
    Instant now = Instant.now();
    stateRef.updateAndGet(state -> state.moveTo(Status.OPEN, now));
    stateTransitions.incrementAndGet();
  }

  // ===== Internal State Management =====

  /** Counts how a call admitted under {@code generation} ended, and moves the circuit if it may. */
  private void settle(long generation, Outcome outcome) {
    switch (outcome) {
      case SUCCESS -> successfulCalls.incrementAndGet();
      case FAILURE -> failedCalls.incrementAndGet();
      case CANCELLED -> {}
    }
    Instant now = Instant.now();
    InternalState seen = stateRef.get();
    // An outcome that leaves the circuit as it is, as most successes do, needs no write
    if (settled(seen, generation, outcome, now) == seen) {
      return;
    }
    InternalState before = stateRef.getAndUpdate(state -> settled(state, generation, outcome, now));
    // Decided from the state the update applied to, so a retried update is counted once
    if (settled(before, generation, outcome, now).generation() != before.generation()) {
      stateTransitions.incrementAndGet();
    }
  }

  /**
   * The state after a call admitted under {@code generation} ends with {@code outcome}. A call from
   * an earlier generation leaves it as it is.
   */
  private InternalState settled(
      InternalState state, long generation, Outcome outcome, Instant now) {
    if (state.generation() != generation) {
      return state;
    }
    return switch (state.status()) {
      case HALF_OPEN ->
          switch (outcome) {
            case SUCCESS ->
                state.successCount() + 1 >= config.successThreshold()
                    ? state.moveTo(Status.CLOSED, now)
                    : state.probeSucceeded();
            case FAILURE -> state.moveTo(Status.OPEN, now);
            case CANCELLED -> state.withProbes(state.probes() - 1);
          };
      // No call is admitted while the circuit is open, so one of this generation found it closed
      case CLOSED, OPEN ->
          switch (outcome) {
            case SUCCESS -> state.failureCount() > 0 ? state.withFailures(0) : state;
            case FAILURE ->
                state.failureCount() + 1 >= config.failureThreshold()
                    ? state.moveTo(Status.OPEN, now)
                    : state.withFailures(state.failureCount() + 1);
            case CANCELLED -> state;
          };
    };
  }
}
