// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.fixtures;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * A task that ignores a cancellation and finishes only once the test releases it, within five
 * seconds.
 *
 * <p>It shows whether a cancellation waited for its task: while the task is held, {@link
 * #finished()} stays false, and {@link #sawInterrupt()} tells whether the task was interrupted.
 * Create one per test, and call {@link #release()} in a {@code finally} block.
 */
public final class HeldTask {

  private final CountDownLatch interrupted = new CountDownLatch(1);
  private final CountDownLatch released = new CountDownLatch(1);
  private final AtomicBoolean finished = new AtomicBoolean(false);

  /**
   * A task that sleeps until interrupted, ignores the interrupt, and gives {@code value} once
   * released.
   *
   * @param value what the task gives once released
   * @param <A> the type of the value
   * @return the held task
   */
  public <A> VTask<A> task(A value) {
    return VTask.of(
        () -> {
          try {
            Thread.sleep(5000);
          } catch (InterruptedException ignored) {
            interrupted.countDown(); // this task ignores a cancellation
          }
          released.await(5, TimeUnit.SECONDS);
          finished.set(true);
          return value;
        });
  }

  /**
   * Whether the task saw an interrupt, waiting up to five seconds for one.
   *
   * @return true once the task has been interrupted
   * @throws InterruptedException if the test thread is interrupted while it waits
   */
  public boolean sawInterrupt() throws InterruptedException {
    return interrupted.await(5, TimeUnit.SECONDS);
  }

  /**
   * Whether the task has finished, which it does only after {@link #release()}.
   *
   * @return true once the task has given its value
   */
  public boolean finished() {
    return finished.get();
  }

  /** Lets the task finish. */
  public void release() {
    released.countDown();
  }
}
