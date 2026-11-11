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
   * Fails with an {@link InterruptedException} if the thread is interrupted, leaving the interrupt
   * status set. A loop that pulls more than once on the thread that runs the operation calls this
   * between pulls, through {@link #pulledUnlessInterrupted}: a terminal operation, and an operator
   * that reads ahead within one pull, such as {@code chunk}. So the loop stops at an interrupt even
   * when the pulls never block, as a pull that sleeps or waits on I/O already does.
   *
   * <p>A stop closes only what the run has reached, never a stream another run may be reading:
   *
   * <ul>
   *   <li>a terminal operation also calls this once before it starts, outside the {@code try} that
   *       closes the stream, so a run on an interrupted thread fails without closing anything;
   *   <li>inside a loop it runs only between pulls, never before the first pull of the stream the
   *       loop starts from, except in {@code chunkWhile}'s chunk builder, whose stream came from
   *       the same run's own pull;
   *   <li>a step that hands on a stream not yet reached, such as {@code concat}'s second stream
   *       when the first ends, {@code flatMap}'s stream for an element, or the stream after {@code
   *       prepend}'s element, hands it on unreached ({@code DerivedStream.unreached}, {@code
   *       concatContinuingUnreached}), so a stop before its first pull leaves it as it is.
   * </ul>
   *
   * <p>A reader on a thread of its own, such as a {@code merge} source, stops at its own
   * cancellation instead, which is what interrupts it.
   */
  static void stopIfInterrupted() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException(
          "the thread is interrupted, so the stream stops before its next pull");
    }
  }

  /**
   * Pulls the next step of a stream, as {@link #executed} runs the pull, once {@link
   * #stopIfInterrupted} has found the thread not interrupted. A loop calls it for each pull after
   * its first, and {@code chunkWhile}'s chunk builder for every pull, as {@link #stopIfInterrupted}
   * says.
   */
  static <A> VStream.Step<A> pulledUnlessInterrupted(VStream<A> stream) throws Throwable {
    stopIfInterrupted();
    return executed(stream.pull());
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
