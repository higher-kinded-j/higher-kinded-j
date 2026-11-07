// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.Cleanup;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * The stream {@link VStreamPar#parEvalMapUnordered} gives: up to {@code concurrency} of its
 * elements' tasks run at once, and each result is emitted as its task completes.
 *
 * <p>Each run of its pulled {@code VTask} starts a window of its own, on a virtual thread that
 * reads the source, forks a task for each element, and reads the next element when the consumer
 * takes a result. The tasks running and the results not yet taken are never more than {@code
 * concurrency} together, so a consumer that stops pulling stops the reading. The stream remembers
 * its latest window, so closing it after a pull closes that run, as a {@link RememberingStream}
 * does, and it keeps no step the window gave, so holding it holds nothing already read.
 *
 * <p>Each step a window gives is kept by the stream that gave it, so a tail pulled again gives the
 * same step. A failure of a task, of the function or of a pull from the source cancels the tasks
 * still running and closes the source where the window got to, and the stream then fails with it,
 * after any results already given. Closing the stream part-way cancels the tasks still running and
 * closes the source, and fails with what closing the source throws. A run that is neither read to
 * its end nor closed leaves its thread waiting, holding the results not yet taken, so close the
 * stream you stop reading.
 *
 * <p>The source is read, and the tasks run, on threads the window starts, which do not see the
 * caller's {@code ScopedValue} bindings.
 *
 * @param <A> The type of elements in the source.
 * @param <B> The type of elements emitted.
 */
final class CompletionOrderStream<A, B> implements VStream<B> {

  private static final String NULL_TASK = "function returned null task for element: ";

  private final VStream<A> source;
  private final int concurrency;
  private final Function<? super A, VTask<B>> f;
  private final AtomicReference<@Nullable Window<A, B>> latest = new AtomicReference<>();

  CompletionOrderStream(VStream<A> source, int concurrency, Function<? super A, VTask<B>> f) {
    this.source = source;
    this.concurrency = concurrency;
    this.f = f;
  }

  @Override
  public VTask<Step<B>> pull() {
    return () -> {
      Window<A, B> window = new Window<>(source, concurrency, f);
      latest.set(window);
      window.start();
      return new Taken<>(window).pull().execute();
    };
  }

  @Override
  public VTask<Unit> close() {
    return () -> {
      Window<A, B> window = latest.get();
      return (window == null ? source.close() : window.close()).execute();
    };
  }

  /** What a window hands its consumer: a task's result, the failure that stopped it, or its end. */
  private sealed interface Signal<B> {

    record Result<B>(B value) implements Signal<B> {}

    record Failed<B>(Throwable cause) implements Signal<B> {}

    record End<B>() implements Signal<B> {}
  }

  /** One run: the thread reading the source, the tasks it forked, and the results not yet taken. */
  private static final class Window<A, B> {

    private final LinkedBlockingQueue<Signal<B>> signals = new LinkedBlockingQueue<>();
    // Each element read holds one until the consumer takes its result
    private final Semaphore permits;
    private final Function<? super A, VTask<B>> f;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
    private final AtomicReference<@Nullable Throwable> closeFailure = new AtomicReference<>();
    private final Thread reader;
    // Held while interrupting the reader, so no interrupt reaches it once it closes the source
    private final Object interrupting = new Object();
    private boolean finishing;
    // Where the reading got to, null once the source is done; only the reader touches it
    private @Nullable VStream<A> position;

    Window(VStream<A> source, int concurrency, Function<? super A, VTask<B>> f) {
      this.permits = new Semaphore(concurrency);
      this.f = f;
      this.position = source;
      this.reader = Thread.ofVirtual().unstarted(this::read);
    }

    void start() {
      reader.start();
    }

    /** Interrupts the reader, unless it has stopped reading to close the source. */
    private void stopReading() {
      synchronized (interrupting) {
        if (!finishing) {
          reader.interrupt();
        }
      }
    }

    /**
     * Reads the source and forks a task for each element until the source ends, a failure stops it
     * or the window is closed, then waits for the tasks still running. An interrupt, from closing
     * or from a failed task, makes the join return at once, and the scope then cancels them.
     */
    @SuppressWarnings("preview")
    private void read() {
      boolean ended = false;
      Throwable readFailure = null;
      // A task never fails its subtask, so the scope is cancelled only by the interrupt
      try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<Object>awaitAll())) {
        try {
          ended = readSource(scope);
        } catch (Throwable t) {
          readFailure = t;
          // A pull that closing or a failed task interrupted is not a failure of its own
          if (!closed.get()) {
            failure.compareAndSet(null, t);
          }
        }
        // The interrupt that stopped the reading may have been swallowed by the source or the
        // function, so interrupt again, for the join to return at once and the scope to cancel
        if (closed.get() || failure.get() != null) {
          Thread.currentThread().interrupt();
        }
        try {
          scope.join();
        } catch (InterruptedException e) {
          ended = false;
        }
      } finally {
        synchronized (interrupting) {
          finishing = true;
        }
        Thread.interrupted();
        finish(ended, readFailure);
      }
    }

    @SuppressWarnings("preview")
    private boolean readSource(StructuredTaskScope<Object, ?> scope) throws Throwable {
      while (true) {
        permits.acquire();
        if (closed.get() || failure.get() != null) {
          return false;
        }
        VStream<A> current = Objects.requireNonNull(position);
        switch (Executing.executed(current.pull())) {
          case Step.Emit<A> e -> {
            position = e.tail();
            VTask<B> task = Objects.requireNonNull(f.apply(e.value()), NULL_TASK + e.value());
            scope.fork(() -> complete(task));
          }
          case Step.Skip<A> s -> {
            position = s.tail();
            permits.release();
          }
          case Step.Done<A> _ -> {
            position = null;
            return true;
          }
        }
      }
    }

    /** Runs one element's task, on a thread of the scope, and hands its result to the consumer. */
    private void complete(VTask<B> task) {
      try {
        signals.add(new Signal.Result<>(Executing.executed(task)));
      } catch (Throwable t) {
        // A task the scope cancelled after a failure or a close is not a failure of its own
        if (!closed.get() && failure.compareAndSet(null, t)) {
          stopReading();
        }
      }
    }

    /**
     * Closes what the reading left unread, then tells the consumer how the window ended. After a
     * failure, a failure to close is suppressed onto it; after a close, it is kept for the close to
     * throw.
     */
    private void finish(boolean ended, @Nullable Throwable readFailure) {
      List<VStream<?>> unread = new ArrayList<>(2);
      if (position != null) {
        unread.add(position);
      }
      VStream<?> carried = readFailure == null ? null : Closing.markedRest(readFailure);
      if (carried != null) {
        unread.add(carried);
      }
      VTask<Unit> closing = Closing.closeAll(unread.toArray(VStream<?>[]::new));
      Throwable failed = failure.get();
      if (failed != null) {
        Closing.runAfterFailure(closing, failed);
        signals.add(new Signal.Failed<>(failed));
        return;
      }
      if (!ended) {
        try {
          Executing.executed(closing);
        } catch (Throwable t) {
          closeFailure.set(t);
        }
      }
      signals.add(new Signal.End<>());
    }

    /**
     * Stops the reading and cancels the tasks still running, then waits while the reader closes the
     * source, with an interrupt already pending held back, as cleanup runs. A pull after this gives
     * {@code Done}. Fails with what closing the source threw, the first time only. If the wait is
     * interrupted, it returns at once with the interrupt status set, and leaves the reader to
     * finish closing the source.
     */
    VTask<Unit> close() {
      return () -> {
        closed.set(true);
        stopReading();
        try {
          Cleanup.withInterruptCleared(reader::join);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        signals.clear();
        signals.add(new Signal.End<>());
        Throwable failed = closeFailure.getAndSet(null);
        if (failed != null) {
          throw failed;
        }
        return Unit.INSTANCE;
      };
    }
  }

  /**
   * The rest of a window after the results taken so far. Its first pull takes the next signal, and
   * it keeps the step that gives, so pulling it again gives the same step.
   */
  private static final class Taken<B> implements VStream<B> {

    private final Window<?, B> window;
    private final ReentrantLock lock = new ReentrantLock();
    private @Nullable Step<B> step;
    private @Nullable Throwable failure;

    Taken(Window<?, B> window) {
      this.window = window;
    }

    @Override
    public VTask<Step<B>> pull() {
      return () -> {
        lock.lock();
        try {
          if (step == null && failure == null && window.closed.get()) {
            step = new Step.Done<>();
          }
          if (step == null && failure == null) {
            switch (window.signals.take()) {
              case Signal.Result<B> r -> {
                window.permits.release();
                step = new Step.Emit<>(r.value(), new Taken<>(window));
              }
              case Signal.Failed<B> failed -> failure = failed.cause();
              case Signal.End<B> _ -> step = new Step.Done<>();
            }
          }
          if (failure != null) {
            throw failure;
          }
          return Objects.requireNonNull(step);
        } finally {
          lock.unlock();
        }
      };
    }

    @Override
    public VTask<Unit> close() {
      return window.close();
    }
  }
}
