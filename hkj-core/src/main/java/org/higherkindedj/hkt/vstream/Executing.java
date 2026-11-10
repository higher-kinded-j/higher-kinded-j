// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.Objects;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;

/**
 * How the streams in this package run a task of their own inside a task they give, such as a pull
 * inside a terminal operation, or a close after one stops early. The task runs through {@link
 * VTask#execute()}, so a failure reaches the caller as the task threw it, a checked one included,
 * where {@link VTask#run()} would wrap a checked one in a {@code VTaskExecutionException}.
 */
final class Executing {

  private Executing() {}

  /**
   * Whether a failure is an interrupt: an {@link InterruptedException}, bare or as {@link
   * VTask#run()} wraps it. An interrupt stops a stream, rather than failing one element.
   */
  static boolean isInterrupt(Throwable failure) {
    return failure instanceof InterruptedException
        || (failure instanceof VTaskExecutionException wrapped
            && wrapped.getCause() instanceof InterruptedException);
  }

  /**
   * Runs a task and gives its value, throwing the task's own failure. As {@link VTask#run()} does,
   * it refuses a null result, and sets the interrupt status again for an {@link
   * InterruptedException}.
   */
  static <A> A executed(VTask<A> task) throws Throwable {
    try {
      return Objects.requireNonNull(
          task.execute(), "the task returned null, and a VTask always gives a value");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw e;
    }
  }

  /**
   * Runs an element's task, giving its value, or what it failed with, as a failed element holds it:
   * a null result is a {@link NullPointerException}, and an {@link Error} is held as an exception
   * is. An interrupt, as {@link #isInterrupt} sees one, is thrown instead, since it stops the
   * stream rather than failing one element.
   */
  static <A> Try<A> attempted(VTask<A> task) throws Exception {
    try {
      return Try.success(executed(task));
    } catch (Exception e) {
      if (isInterrupt(e)) {
        throw e;
      }
      return Try.failure(e);
    } catch (Throwable t) {
      return Try.failure(t);
    }
  }
}
