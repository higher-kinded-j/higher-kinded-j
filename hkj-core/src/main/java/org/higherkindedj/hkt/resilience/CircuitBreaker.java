// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

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
 *   <li><b>HALF_OPEN</b> (probing): the circuit has {@link CircuitBreakerConfig#successThreshold()}
 *       probe places. A call runs as a probe while a place is free; a probe holds its place while
 *       it runs and keeps it once it succeeds, and a probe that is cancelled gives its place up to
 *       the next call. A call that finds every place taken is rejected with a {@link
 *       CircuitOpenException} whose {@link CircuitOpenException#status() status} is {@code
 *       HALF_OPEN}. If that many probes succeed, the circuit closes; if one fails, it re-opens.
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
     * Probing: a call runs as a probe, to test recovery, while fewer than {@link
     * CircuitBreakerConfig#successThreshold()} probes are running or have succeeded.
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

  /**
   * One admission's ticket, shared by the call admitted on it and a guard nested in it that borrows
   * it. It is settled exactly once: by the first of them to finish with an outcome that counts, or,
   * when neither does, freed by the last of them to finish, as a cancelled call frees its probe
   * place. A settled ticket cannot be borrowed, so a guard that finds one is admitted on its own.
   */
  private static final class Ticket {
    /** The holder count of a settled ticket. */
    private static final int SETTLED = 0;

    /** The generation the admission was made under. */
    private final long generation;

    /** How many guards run on the ticket and have not finished, or {@link #SETTLED}. */
    private final AtomicInteger holders = new AtomicInteger(1);

    private Ticket(long generation) {
      this.generation = generation;
    }

    /** Joins the ticket if it is not settled, and whether it could. */
    private boolean join() {
      return holders.getAndUpdate(held -> held == SETTLED ? SETTLED : held + 1) != SETTLED;
    }

    /** Settles the ticket with an outcome, and whether this holder was the first to. */
    private boolean claim() {
      return holders.getAndUpdate(_ -> SETTLED) != SETTLED;
    }

    /** Leaves the ticket without an outcome, and whether that left it to be freed. */
    private boolean leave() {
      return holders.getAndUpdate(held -> held == SETTLED ? SETTLED : held - 1) == 1;
    }
  }

  /**
   * A call a breaker protects, as the guards nested in it see it.
   *
   * <p>It is mutable, unlike the breaker's {@link InternalState}, because the guards nested in a
   * call, on whatever threads the call starts, tell it what they did: one runs on the call's
   * admission, and one counted an outcome, so the call counts nothing of its own.
   */
  private static final class Enclosing {
    /** The breaker's identity: not the breaker, which a thread the call leaves would keep alive. */
    private final Object owner;

    /** The admission the call runs on, its own or one it borrowed. */
    private final Ticket admission;

    /** The nearest call this one is nested in that was running when it began, or null. */
    private final @Nullable Enclosing outer;

    /** The call whose admission this one borrowed, or null when it was admitted on its own. */
    private final @Nullable Enclosing lender;

    /** Whether the call is running: a guard reached after it has finished is a call of its own. */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /** Whether a guard has been reached inside the call, the first of which it counted already. */
    private final AtomicBoolean entered = new AtomicBoolean();

    /** Whether a nested guard runs on the call's admission: one at a time borrows it. */
    private final AtomicBoolean lent = new AtomicBoolean();

    /** Whether a nested guard counted an outcome, or was turned away, so the call adds nothing. */
    private final AtomicBoolean counted = new AtomicBoolean();

    private Enclosing(
        Object owner, Ticket admission, @Nullable Enclosing outer, @Nullable Enclosing lender) {
      this.owner = owner;
      this.admission = admission;
      this.outer = outer;
      this.lender = lender;
    }
  }

  /**
   * The protected calls the current thread is inside, innermost first: set on the thread that runs
   * a protected task, and inherited by every thread the task starts. One field serves every
   * breaker, so a thread holds one entry however many breakers it calls. It is inherited rather
   * than a {@code ScopedValue}, because {@code VTask.timeout} and {@code detachOnCancel} run a task
   * on a thread of their own, which a {@code ScopedValue} binding does not reach. A long-lived
   * thread started during a call, such as an executor's, keeps that call's small node, and the
   * nodes of the calls it is nested in, for as long as the thread lives.
   */
  private static final InheritableThreadLocal<@Nullable Enclosing> ENCLOSING =
      new InheritableThreadLocal<>();

  private final Object identity = new Object();
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
   * probe, holds its place until it stops; with a success threshold of one, the circuit then turns
   * every other call away meanwhile.
   *
   * <p>A call this breaker already protects counts each attempt once, at the guard nearest the
   * dependency. A repository and the service that calls it may each guard one dependency with this
   * breaker, as in {@code io.withCircuitBreaker(b).withRetry(p).withCircuitBreaker(b)}. A {@code
   * protect} reached while such a call runs, on its thread or on a thread it starts, checks the
   * circuit, applies the call timeout and counts its outcome, as a lone guard does; the outer guard
   * then counts nothing. The first nested guard runs on the outer call's admission while the
   * circuit is in the state the call was admitted in, so it takes no second probe place and is not
   * counted again; a later one, such as a retry's next attempt, is admitted afresh, and an open
   * circuit turns it away. A nested guard that is cancelled, as by the outer call's timeout, counts
   * nothing, and the outer call then counts its own outcome unless another nested guard has counted
   * already. Of a call and a nested guard that outlives it, as a guard inside {@code
   * detachOnCancel()} does, the first to finish with an outcome counts it, so the outer call's
   * timeout counts when its time runs out. A guard reached after the call has finished is a call of
   * its own. A thread an executor starts during the call belongs to the call while it runs, so work
   * it runs meanwhile for another caller counts as part of this call; an executor thread started
   * before the call does not.
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
      @Nullable Enclosing chain = ENCLOSING.get();
      @Nullable Enclosing enclosing = enclosingCall(chain);
      // The call a guard is nested in counted that guard already; a later one counts on its own
      if (enclosing == null || !enclosing.entered.compareAndSet(false, true)) {
        totalCalls.incrementAndGet();
      }
      @Nullable Enclosing lender = borrowAdmission(enclosing);
      Ticket admission = lender != null ? lender.admission : new Ticket(admitWithin(enclosing));
      Enclosing call = new Enclosing(identity, admission, running(chain), lender);
      // Threads the task runs on, and the threads they start, find the call through this chain
      VTask<A> marked =
          () -> {
            ENCLOSING.set(call);
            try {
              return task.execute();
            } finally {
              ENCLOSING.set(chain);
            }
          };
      A result;
      // Execute the task with timeout (using execute() to preserve original exception types)
      try {
        result = marked.timeout(config.callTimeout()).execute();
      } catch (InterruptedException cancelled) {
        // A cancelled call says nothing about the service; a probe gives up its place
        end(call, enclosing, () -> Outcome.CANCELLED);
        throw cancelled;
      } catch (Throwable t) {
        end(call, enclosing, () -> outcomeOf(t));
        throw t;
      }
      end(call, enclosing, () -> Outcome.SUCCESS);
      return result;
    };
  }

  /**
   * The nearest call that {@code chain} holds and that is still running, of any breaker, or null: a
   * new call links to it, so a chain never holds the calls a thread outlived.
   */
  private static @Nullable Enclosing running(@Nullable Enclosing chain) {
    Enclosing call = chain;
    while (call != null && !call.running.get()) {
      call = call.outer;
    }
    return call;
  }

  /** The nearest running call of this breaker that {@code chain} holds, or null. */
  private @Nullable Enclosing enclosingCall(@Nullable Enclosing chain) {
    for (Enclosing call = chain; call != null; call = call.outer) {
      if (call.owner == identity && call.running.get()) {
        return call;
      }
    }
    return null;
  }

  /**
   * Borrows {@code enclosing}'s admission, if no other nested guard holds it, nobody has settled
   * it, and the circuit is in the state it was admitted in, and returns the call it was borrowed
   * from, or null when it cannot be.
   */
  private @Nullable Enclosing borrowAdmission(@Nullable Enclosing enclosing) {
    return enclosing != null
            && enclosing.admission.generation == stateRef.get().generation()
            && enclosing.lent.compareAndSet(false, true)
            && enclosing.admission.join()
        ? enclosing
        : null;
  }

  /**
   * Admits a call on its own, and tells the call it is nested in, if any, when it is turned away,
   * since the rejection is counted.
   */
  private long admitWithin(@Nullable Enclosing enclosing) {
    try {
      return admit();
    } catch (CircuitOpenException rejected) {
      markCounted(enclosing);
      throw rejected;
    }
  }

  /**
   * Ends {@code call}: counts its outcome on its admission, unless a guard nested in it counted one
   * or another guard on the admission finished first with one, and tells the call it is nested in.
   * A borrowed admission goes back to its lender, and the last guard to leave an admission that
   * nobody counted frees it.
   */
  private void end(Enclosing call, @Nullable Enclosing enclosing, Supplier<Outcome> outcome) {
    call.running.set(false);
    Ticket admission = call.admission;
    // A guard nearer the dependency counted the call, or turned it away; this one adds nothing
    boolean countsItsOwn = !call.counted.get();
    Outcome ended = countsItsOwn ? outcome.get() : Outcome.CANCELLED;
    boolean settledHere = ended != Outcome.CANCELLED && admission.claim();
    if (settledHere) {
      settle(admission.generation, ended);
    } else if (admission.leave()) {
      // Nobody counted the admission: free its probe place, as a cancelled call does
      settle(admission.generation, Outcome.CANCELLED);
    }
    Enclosing lender = call.lender;
    if (lender != null) {
      // The lender's next nested guard may borrow again; a settled ticket refuses it
      lender.lent.set(false);
    }
    if (settledHere || !countsItsOwn) {
      markCounted(enclosing);
    }
  }

  private static void markCounted(@Nullable Enclosing enclosing) {
    if (enclosing != null) {
      enclosing.counted.set(true);
    }
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
