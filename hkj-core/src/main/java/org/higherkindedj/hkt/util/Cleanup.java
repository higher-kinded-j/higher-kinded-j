// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * How cleanup after some work reports a failure, by the rule try-with-resources follows. When the
 * work fails and its cleanup fails too, the work's failure is the one reported, with the cleanup's
 * suppressed onto it. When the work succeeds, a failure of its cleanup is reported on its own.
 * {@code IOPath}, {@code VTaskPath}, {@code Resource} and {@code VStream} report the failures of
 * their cleanup through this class, so the rule is the same for all of them.
 *
 * <p>Cleanup after a failure runs with the thread's interrupt status cleared, so cleanup after a
 * cancelled computation is not cut short, and the status is restored afterwards.
 */
public final class Cleanup {

  private Cleanup() {}

  /**
   * A step of cleanup, which may throw a checked exception, as {@link AutoCloseable#close()} may.
   */
  @FunctionalInterface
  public interface Action {

    /**
     * Runs the cleanup.
     *
     * @throws Exception if the cleanup fails
     */
    void run() throws Exception;
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
      afterFailure(failure, cleanup::run);
      throw failure;
    }
    cleanup.run();
    return result;
  }

  /**
   * Runs cleanup after a failure, which stays the one reported: anything the cleanup throws is
   * suppressed onto it. The caller goes on to throw the failure. The interrupt status is cleared
   * while the cleanup runs, and restored afterwards.
   *
   * @param failure the failure the cleanup follows
   * @param cleanup the cleanup to run
   */
  public static void afterFailure(Throwable failure, Action cleanup) {
    boolean interrupted = Thread.interrupted();
    try {
      cleanup.run();
    } catch (Throwable later) {
      suppress(failure, later);
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * The failure to report of two: the first, with the later one suppressed onto it, or the later
   * one when there was no first.
   *
   * @param first the failure so far, or null if there was none
   * @param later the failure that followed it
   * @return the failure to report
   */
  public static Throwable keep(@Nullable Throwable first, Throwable later) {
    if (first == null) {
      return later;
    }
    suppress(first, later);
    return first;
  }

  /**
   * Suppresses a later failure onto the first, unless it is the first or one of its causes, where
   * it would appear twice. The same instance thrown twice, as a shared {@code VTask.fail} throws
   * it, is kept once.
   *
   * @param first the failure reported
   * @param later the failure that followed it
   */
  public static void suppress(Throwable first, Throwable later) {
    if (!inCauseChain(first, later)) {
      first.addSuppressed(later);
    }
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
