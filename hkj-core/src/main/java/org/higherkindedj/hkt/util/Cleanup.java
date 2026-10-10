// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Which failure is reported when some work and the cleanup after it both fail, by the rule
 * try-with-resources follows: the work's failure, with the cleanup's suppressed onto it. When the
 * work succeeds, a failure of its cleanup is reported on its own. {@code IOPath}, {@code
 * VTaskPath}, {@code Resource} and {@code VStream} report a failing cleanup through this class, so
 * the rule is the same for all of them. Each keeps its own form for a checked exception, and its
 * own order among several releases, such as the composed releases of a {@code Resource}. {@code
 * VResultPath.bracketOutcome} reports its release's defect instead, keeping any pending failure
 * with {@link #keep}, and runs its release through {@link #withInterruptCleared}.
 *
 * <p>Cleanup after a failure runs with the thread's interrupt status cleared, so cleanup after a
 * cancelled computation is not cut short. The status is restored afterwards. When {@link
 * #afterFailure} suppresses an {@link InterruptedException} from the cleanup, it sets the status;
 * one wrapped in another exception, as a composed {@code Resource} or a {@code VStream} wraps it,
 * does not set it.
 *
 * <p>An exception that more than one run throws, such as the one a {@code VTask.fail} holds,
 * gathers the suppressed exceptions of every run whose cleanup fails, as it would under
 * try-with-resources. An exception already suppressed onto it is not added again.
 *
 * <p>Used internally: the package is not exported.
 */
public final class Cleanup {

  private Cleanup() {}

  /**
   * A step of cleanup, which may throw a checked exception, as {@link AutoCloseable#close()} may,
   * or any {@link Throwable}, as a {@code VTask} may.
   */
  @FunctionalInterface
  public interface Action {

    /**
     * Runs the cleanup.
     *
     * @throws Throwable if the cleanup fails
     */
    void run() throws Throwable;
  }

  /**
   * Runs {@code work}, then {@code cleanup}, whether the work succeeded or failed. If the work
   * fails, its failure is rethrown once the cleanup has run, with anything the cleanup throws
   * suppressed onto it. If the work succeeds, a failure of the cleanup is the one thrown.
   *
   * @param work the work to run
   * @param cleanup the cleanup to run after it
   * @param <A> the type of the work's result
   * @return the work's result
   */
  public static <A extends @Nullable Object> A guarantee(
      Supplier<? extends A> work, Runnable cleanup) {
    A result;
    try {
      result = work.get();
    } catch (Throwable failure) {
      afterFailure(failure, () -> cleanup.run());
      throw failure;
    }
    cleanup.run();
    return result;
  }

  /**
   * Runs cleanup after a failure, which stays the one reported: anything the cleanup throws is
   * suppressed onto it. The caller goes on to throw the failure. The interrupt status is cleared
   * while the cleanup runs. It is restored afterwards, and set if the cleanup itself throws an
   * {@link InterruptedException}, so an interrupt that arrives during the cleanup is not lost.
   *
   * @param failure the failure the cleanup follows
   * @param cleanup the cleanup to run
   */
  public static void afterFailure(Throwable failure, Action cleanup) {
    try {
      withInterruptCleared(cleanup);
    } catch (Throwable secondary) {
      if (secondary instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      suppress(failure, secondary);
    }
  }

  /**
   * Runs cleanup with the interrupt status cleared, and restores the status afterwards. Unlike
   * {@link #afterFailure}, anything the cleanup throws is thrown on unchanged, and the status is
   * only restored: an {@link InterruptedException} from the cleanup does not set it, since the
   * caller receives the exception itself.
   *
   * @param cleanup the cleanup to run
   * @throws Throwable what the cleanup throws
   */
  public static void withInterruptCleared(Action cleanup) throws Throwable {
    boolean interrupted = Thread.interrupted();
    try {
      cleanup.run();
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * The failure to report of two: {@code primary}, with {@code secondary} suppressed onto it, or
   * {@code secondary} when there is no primary.
   *
   * @param primary the failure to report, or null if there is none yet
   * @param secondary the failure to keep among its suppressed exceptions
   * @return the failure to report
   */
  public static Throwable keep(@Nullable Throwable primary, Throwable secondary) {
    if (primary == null) {
      return secondary;
    }
    suppress(primary, secondary);
    return primary;
  }

  /**
   * Suppresses {@code secondary} onto {@code primary}, unless it would appear twice: when it is
   * {@code primary} or one of its causes, as the same instance thrown twice by a shared {@code
   * VTask.fail} is, or is already among {@code primary}'s suppressed exceptions.
   *
   * @param primary the failure reported
   * @param secondary the failure to keep among its suppressed exceptions
   */
  public static void suppress(Throwable primary, Throwable secondary) {
    if (!inCauseChain(primary, secondary) && !isSuppressedOnto(primary, secondary)) {
      primary.addSuppressed(secondary);
    }
  }

  /** Whether {@code secondary} is already among {@code primary}'s suppressed exceptions. */
  private static boolean isSuppressedOnto(Throwable primary, Throwable secondary) {
    for (Throwable suppressed : primary.getSuppressed()) {
      if (suppressed == secondary) {
        return true;
      }
    }
    return false;
  }

  /** Whether {@code target} is {@code start} or one of its causes; a cyclic chain ends the walk. */
  private static boolean inCauseChain(Throwable start, Throwable target) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable t = start; t != null && seen.add(t); t = t.getCause()) {
      if (t == target) {
        return true;
      }
    }
    return false;
  }
}
