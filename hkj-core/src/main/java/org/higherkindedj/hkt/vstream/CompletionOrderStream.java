// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

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
 * same step. A failed element of the source is handed on as it is read, ahead of the results still
 * running, and a task that fails hands on a failed element as it completes, as a result is handed
 * on; the window reads on after either. An interrupted task, or a failure of the function, cancels
 * the tasks still running and closes the source where the window got to, and the stream then fails
 * with it, after any results already given. A failed pull from the source stops the reading, but
 * the tasks already running finish, and their results come before the failure; an interrupt cancels
 * them at once. Closing the stream part-way cancels the tasks still running and closes the source,
 * and fails with what closing the source throws. Closing and an interrupted task interrupt the
 * reader, so a source finaliser that runs inside a pull, as one does at the source's end, can be
 * cut short, as with {@link VStreamPar#merge}. A function that closes the stream closes the source
 * itself, on the reader's thread, so it gets what closing throws. A run that is neither read to its
 * end nor closed leaves its thread waiting, holding the results not yet taken, so close the stream
 * you stop reading.
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
      // Started before it is published, so a close that finds it waits for its reader, and the
      // reader waits in turn for the window to be published, so the function's close finds it
      window.start();
      latest.set(window);
      window.published();
      try {
        return new Taken<>(window).pull().execute();
      } catch (InterruptedException e) {
        // A defer around this stream keeps nothing it builds, so no close would reach the window
        Closing.runAfterFailure(window.close(), e);
        throw e;
      }
    };
  }

  @Override
  public VTask<Unit> close() {
    return () -> {
      Window<A, B> window = latest.get();
      return (window == null ? source.close() : window.close()).execute();
    };
  }

  /**
   * What a window hands its consumer: a task's result, a failed element (of the source, or for a
   * task that failed), the failure that stopped it, or its end.
   */
  private sealed interface Signal<B> {

    record Result<B>(B value) implements Signal<B> {}

    record FailedElement<B>(Throwable error) implements Signal<B> {}

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
    // Released once the stream has published the window, for the reader to begin
    private final Semaphore publication = new Semaphore(0);
    private final Thread reader;
    // Held while interrupting the reader, so no interrupt reaches it once it closes the source
    private final Object interrupting = new Object();
    private boolean finishing;
    // Where the reading got to, null once the source is done or closed; only the reader touches it
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

    /** Lets the reader begin, once a close of the stream finds this window. */
    void published() {
      publication.release();
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
     * Once the window is published, reads the source and forks a task for each element until the
     * source ends, a failure stops it or the window is closed, then waits for the tasks still
     * running. An interrupt, from closing or from an interrupted task, makes the join return at
     * once, and the scope then cancels them. A failed pull stops only the reading, so the join
     * waits for the tasks to finish.
     */
    @SuppressWarnings("preview")
    private void read() {
      // A close's interrupt that comes while it waits stays set, so the reading stops at once
      publication.acquireUninterruptibly();
      boolean ended = false;
      Throwable pullFailure = null;
      // A task never fails its subtask, so the scope is cancelled only by the interrupt
      try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<Object>awaitAll())) {
        try {
          ended = readSource(scope);
        } catch (Throwable t) {
          // A pull that closing or an interrupted task interrupted is not a failure of its own
          if (!closed.get()) {
            if (Executing.isInterrupt(t)) {
              failure.compareAndSet(null, t);
            } else {
              pullFailure = t;
            }
          }
        }
        // The interrupt that stopped the reading may have been swallowed by the source or the
        // function, so interrupt again, for the join to return at once and the scope to cancel
        if (stopped()) {
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
        finish(ended, pullFailure);
      }
    }

    @SuppressWarnings("preview")
    private boolean readSource(StructuredTaskScope<Object, ?> scope) throws Throwable {
      while (true) {
        permits.acquire();
        if (stopped()) {
          return false;
        }
        VStream<A> current = Objects.requireNonNull(position);
        switch (Executing.executed(current.pull())) {
          case Step.Emit<A> e -> {
            position = e.tail();
            // A pull that swallowed the interrupt stops here, before the function runs
            if (stopped()) {
              return false;
            }
            VTask<B> task;
            try {
              task = Objects.requireNonNull(f.apply(e.value()), () -> NULL_TASK + e.value());
            } catch (Throwable t) {
              // A function that fails stops the window as an interrupted task does, even after it
              // closed the stream, since the consumer hears of it from no one else
              failure.compareAndSet(null, t);
              return false;
            }
            // The function may have closed the stream
            if (stopped()) {
              return false;
            }
            scope.fork(() -> complete(task));
          }
          case Step.Skip<A> s -> {
            position = s.tail();
            permits.release();
          }
          case Step.Fail<A> failed -> {
            // Its permit is held until the consumer takes it, as a result's is
            position = failed.tail();
            signals.add(new Signal.FailedElement<>(failed.error()));
          }
          case Step.Done<A> _ -> {
            position = null;
            return true;
          }
        }
      }
    }

    /** Whether the window is closed, or has failed, so the reading stops. */
    private boolean stopped() {
      return closed.get() || failure.get() != null;
    }

    /**
     * Runs one element's task, on a thread of the scope, and hands its result to the consumer. A
     * task that fails, or gives null, hands on a failed element in its place, as a failed {@link
     * VStream#mapTask} task does, and the window reads on. An interrupt, bare or as {@link
     * VTask#run()} wraps it, stops the window instead.
     */
    private void complete(VTask<B> task) {
      try {
        signals.add(new Signal.Result<>(Executing.executed(task)));
      } catch (Throwable t) {
        // A task the scope cancelled after a failure or a close is not a failure of its own
        if (stopped()) {
          return;
        }
        if (Executing.isInterrupt(t)) {
          failure.compareAndSet(null, t);
          stopReading();
        } else {
          signals.add(new Signal.FailedElement<>(t));
        }
      }
    }

    /**
     * Closes what the reading left unread, then tells the consumer how the window ended. A failure
     * to close is kept for a close to throw, and after a failure it is also suppressed onto it, so
     * a consumer that closes the stream before reaching the failure still hears of it. An
     * interrupted task or a failed function is the failure to report, as it is in a batch of {@link
     * VStreamPar#parEvalMap}, and otherwise a failed pull. After a close, results that tasks gave
     * as they stopped are dropped.
     */
    private void finish(boolean ended, @Nullable Throwable pullFailure) {
      VStream<A> unread = position;
      VTask<Unit> keepingFailure =
          () -> {
            try {
              return unread == null ? Unit.INSTANCE : unread.close().execute();
            } catch (Throwable t) {
              closeFailure.set(t);
              throw t;
            }
          };
      Throwable stopping = failure.get();
      Throwable failed = stopping != null ? stopping : pullFailure;
      if (failed != null) {
        Closing.runAfterFailure(keepingFailure, failed);
      } else if (!ended) {
        try {
          Executing.executed(keepingFailure);
        } catch (Throwable _) {
          // Kept for the close to throw
        }
      }
      if (closed.get()) {
        signals.clear();
      }
      signals.add(failed == null ? new Signal.End<>() : new Signal.Failed<>(failed));
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
        if (Thread.currentThread() == reader) {
          return closeFromTheFunction();
        }
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

    /**
     * A close from the function, on the reader's thread. The reader cannot wait for itself, and it
     * stops once the function returns, so this closes the source at once, and the function gets
     * what closing throws, as it would from any other stream. The reader then cancels the tasks and
     * tells the consumer how the window ended, so a failure the function lets through fails the
     * stream. Closing again closes nothing.
     */
    private Unit closeFromTheFunction() throws Throwable {
      VStream<A> unread = position;
      position = null;
      return unread == null ? Unit.INSTANCE : unread.close().execute();
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
        lock.lockInterruptibly();
        try {
          if (step == null && failure == null) {
            switch (window.signals.take()) {
              case Signal.Result<B> r -> {
                window.permits.release();
                step = new Step.Emit<>(r.value(), new Taken<>(window));
              }
              case Signal.FailedElement<B> failed -> {
                window.permits.release();
                step = new Step.Fail<>(failed.error(), new Taken<>(window));
              }
              // How the window ended stays for any other tail pulled after this one
              case Signal.Failed<B> failed -> {
                window.signals.add(failed);
                failure = failed.cause();
              }
              case Signal.End<B> end -> {
                window.signals.add(end);
                step = new Step.Done<>();
              }
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
