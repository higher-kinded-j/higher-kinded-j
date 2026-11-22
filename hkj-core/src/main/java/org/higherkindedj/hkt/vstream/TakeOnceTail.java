// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * A position in one run of a stream whose run hands it signals through a queue, as a tail of {@link
 * VStreamReactive#fromPublisher}, {@link VStreamPar#parEvalMapUnordered} or {@link
 * VStreamPar#merge} is. Its first pull takes the position's signal, and it keeps the step that
 * gives, or the failure, so its pulled {@code VTask} gives the same answer each time it runs,
 * whichever thread runs it. One pull at a time reads the position: a pull that waits for another
 * then gives what that one kept, or takes the signal itself if an interrupt stopped that one first.
 *
 * <p>A pull that need not wait gives its answer on an interrupted thread too, as a {@link
 * VStream#fromList} tail gives its element: a step the position keeps, or a signal already there. A
 * wait that an interrupt stops, for the signal or for another pull of the position, throws {@link
 * InterruptedException} with the interrupt status set again, as the library's own pull loops leave
 * it, and keeps nothing, so a later pull waits again.
 *
 * @param <S> The type of the run's signals.
 * @param <A> The element type.
 */
abstract class TakeOnceTail<S, A> implements VStream<A> {

  // Apart from the tail, since ImmutabilityRules holds a Kind to final fields
  private final Kept<A> kept = new Kept<>();

  /**
   * Gives this position's signal: one already there whether or not the thread is interrupted, as
   * {@link #pollOrTake} does, and otherwise the one it waits for. The pull sets the interrupt
   * status again when an interrupt stops the wait, so this need not.
   *
   * @throws InterruptedException if an interrupt stops the wait
   */
  abstract S nextSignal() throws InterruptedException;

  /**
   * The step a signal gives this position, run once, holding the position's lock. A failure it
   * throws is kept, and given each time, so it does not throw for an interrupt of this thread.
   */
  abstract Step<A> stepFor(S signal) throws Throwable;

  /** Closes the run this position reads, which each tail does in its own way. */
  @Override
  public abstract VTask<Unit> close();

  /**
   * The queue's next signal: one already there whether or not the thread is interrupted, and
   * otherwise the one it waits for. It polls first, since {@code take} throws on an interrupted
   * thread even with a signal there.
   */
  static <S> S pollOrTake(BlockingQueue<S> signals) throws InterruptedException {
    @Nullable S signal = signals.poll();
    return signal != null ? signal : signals.take();
  }

  @Override
  public final VTask<Step<A>> pull() {
    return this::step;
  }

  private Step<A> step() throws Throwable {
    Kept<A> kept = this.kept;
    lockForPull(kept);
    try {
      if (kept.step == null && kept.failure == null) {
        S signal = awaitSignal();
        try {
          kept.step = Objects.requireNonNull(stepFor(signal), "stepFor gave null");
        } catch (Throwable t) {
          kept.failure = t;
        }
      }
      Throwable failure = kept.failure;
      if (failure != null) {
        // Thrown as it is, an InterruptedException included: it stopped the run, not this thread,
        // so the interrupt status is left alone
        throw failure;
      }
      return Objects.requireNonNull(kept.step);
    } finally {
      kept.unlock();
    }
  }

  /**
   * Takes the position's lock: at once when no other pull holds it, whether or not the thread is
   * interrupted, so a kept step is given again on an interrupted thread, and otherwise once the
   * other pull is done.
   */
  private static void lockForPull(ReentrantLock lock) throws InterruptedException {
    if (lock.tryLock()) {
      return;
    }
    try {
      lock.lockInterruptibly();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw e;
    }
  }

  private S awaitSignal() throws InterruptedException {
    try {
      return nextSignal();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw e;
    }
  }

  /**
   * The position's lock, holding the step or the failure its first pull kept, which it guards. It
   * is its own lock, as a {@code ThreadPoolExecutor} worker is, so a tail makes no more objects
   * than one with a lock and fields of its own.
   */
  private static final class Kept<A> extends ReentrantLock {

    private @Nullable Step<A> step;
    private @Nullable Throwable failure;
  }
}
