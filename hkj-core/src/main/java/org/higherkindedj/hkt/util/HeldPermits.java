// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import java.util.concurrent.Semaphore;
import org.jspecify.annotations.Nullable;

/**
 * The bulkhead permits a thread holds, so that a call a {@code Bulkhead} already admitted, guarded
 * by it again, runs on the permit it holds rather than wait for a second. A repository and the
 * service that calls it may each guard one dependency with one bulkhead, and with one permit left a
 * second wait would never end.
 *
 * <p>Each thread keeps its own permits, and a thread it starts does not inherit them: a thread that
 * runs beside the call takes a permit of its own, so the bulkhead still bounds how many threads run
 * at once. That covers a fork, and a task {@code VTask.detachOnCancel} runs, which carries on once
 * its caller stops waiting. A {@code VTask.timeout} is the exception: its caller waits until its
 * task has stopped, so the task's thread stands in for the caller and runs on the caller's permits,
 * through {@link #handOff()}. A permit therefore serves one running thread at a time, and goes back
 * when the guard that took it finishes, after every guard that ran on it.
 *
 * <p>The permits live in a plain {@link ThreadLocal}. A {@code ScopedValue} binding would reach
 * every fork of a {@code StructuredTaskScope}, as {@code Par} and {@code Scope} start, and an
 * inherited chain, as the {@code CircuitBreaker} keeps to count a call's outcome once, would reach
 * every thread the call starts. Either would let those threads run at once on the call's one
 * permit.
 *
 * <p>Used internally: the package is not exported.
 */
public final class HeldPermits {

  /** The permits the current thread holds, innermost first, or null. */
  private static final ThreadLocal<@Nullable Hold> HELD = new ThreadLocal<>();

  /** What a hand-off carries from a thread that holds no permits. */
  private static final HandOff NONE = new HandOff(null);

  private HeldPermits() {}

  /** A permit of {@code semaphore} that a thread holds, and the permits it held before. */
  private record Hold(Semaphore semaphore, @Nullable Hold outer) {}

  /**
   * Runs {@code work} on a permit of {@code semaphore}: the one the current thread holds, if it
   * holds one, or else one that {@code acquire} takes and that goes back once {@code work} has
   * finished.
   *
   * @param semaphore the semaphore whose permit the work runs on
   * @param acquire takes a permit of {@code semaphore}, or throws
   * @param work the work to run
   * @param <A> the type of the work's result
   * @param <X> the type of exception the work may throw
   * @return the work's result
   * @throws X if the work fails
   */
  public static <A extends @Nullable Object, X extends Throwable> A onPermit(
      Semaphore semaphore, Runnable acquire, Cleanup.Work<? extends A, X> work) throws X {
    @Nullable Hold held = HELD.get();
    if (holds(held, semaphore)) {
      return work.get();
    }
    acquire.run();
    try {
      HELD.set(new Hold(semaphore, held));
      return work.get();
    } finally {
      try {
        restore(held);
      } finally {
        semaphore.release();
      }
    }
  }

  private static boolean holds(@Nullable Hold held, Semaphore semaphore) {
    for (Hold hold = held; hold != null; hold = hold.outer()) {
      if (hold.semaphore() == semaphore) {
        return true;
      }
    }
    return false;
  }

  private static void restore(@Nullable Hold held) {
    if (held == null) {
      HELD.remove();
    } else {
      HELD.set(held);
    }
  }

  /**
   * The permits the current thread holds, for a task that runs on a thread of its own while this
   * one waits until it has stopped.
   *
   * @return what the task's thread runs on
   */
  public static HandOff handOff() {
    @Nullable Hold held = HELD.get();
    return held == null ? NONE : new HandOff(held);
  }

  /** The permits of a thread that hands a task over and waits until it has stopped. */
  public static final class HandOff {

    private final @Nullable Hold held;

    private HandOff(@Nullable Hold held) {
      this.held = held;
    }

    /**
     * Runs {@code work} on the permits of the thread that handed it over. The thread it runs on has
     * its own permits back afterwards.
     *
     * @param work the work to run
     * @param <A> the type of the work's result
     * @param <X> the type of exception the work may throw
     * @return the work's result
     * @throws X if the work fails
     */
    public <A extends @Nullable Object, X extends Throwable> A run(
        Cleanup.Work<? extends A, X> work) throws X {
      if (held == null) {
        return work.get();
      }
      @Nullable Hold own = HELD.get();
      HELD.set(held);
      try {
        return work.get();
      } finally {
        restore(own);
      }
    }
  }
}
