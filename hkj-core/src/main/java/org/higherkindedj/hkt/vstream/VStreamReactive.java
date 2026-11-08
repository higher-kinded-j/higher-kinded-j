// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

/**
 * Bridge between {@link VStream} and {@link java.util.concurrent.Flow} reactive streams.
 *
 * <p>This utility class provides bidirectional conversion between VStream's pull-based model and
 * Java's {@link Flow.Publisher}/{@link Flow.Subscriber} push-based reactive model.
 *
 * <h2>toPublisher</h2>
 *
 * <p>Converts a VStream to a {@link Flow.Publisher}. Each subscriber receives all elements.
 * Backpressure is respected: elements are pulled when the subscriber requests them via {@link
 * Flow.Subscription#request(long)}, and on until the next element or the end, to notice the end of
 * the stream.
 *
 * <h2>fromPublisher</h2>
 *
 * <p>Converts a {@link Flow.Publisher} to a VStream. Each run of the stream subscribes to the
 * publisher at its first pull, and buffers the incoming elements in a bounded queue. The VStream's
 * {@code pull()} reads from this queue, providing pull-based access to the push-based source, and
 * each element taken asks the publisher for one more. Closing the VStream cancels the run's
 * subscription.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Convert VStream to Publisher
 * VStream<String> stream = VStream.of("a", "b", "c");
 * Flow.Publisher<String> publisher = VStreamReactive.toPublisher(stream);
 *
 * // Convert Publisher to VStream
 * VStream<String> fromPub = VStreamReactive.fromPublisher(publisher);
 * List<String> result = fromPub.toList().run();
 * // result: ["a", "b", "c"]
 * }</pre>
 *
 * @see VStream
 * @see Flow.Publisher
 */
public final class VStreamReactive {

  /** Default buffer size for {@link #fromPublisher(Flow.Publisher)}. */
  private static final int DEFAULT_BUFFER_SIZE = 256;

  private VStreamReactive() {}

  /**
   * Converts a {@link VStream} to a {@link Flow.Publisher}.
   *
   * <p>Each subscriber receives all elements from the stream. Backpressure is respected: the stream
   * is pulled while the subscriber has outstanding demand, and when the demand runs out it is
   * pulled on until it gives an element or ends, so that its end is noticed without more demand. An
   * element pulled that way is held until the subscriber asks for it. Elements are pulled on
   * virtual threads.
   *
   * <p>If the stream produces an error, {@link Flow.Subscriber#onError(Throwable)} is called. When
   * the stream completes, {@link Flow.Subscriber#onComplete()} is called.
   *
   * <p>When a subscriber cancels, or the stream fails, what is left of the stream is closed, so its
   * finalisers run, as {@link VStream#close()} describes. A cancel interrupts a pull that is
   * waiting on a quiet stream, and nothing that pull returns is delivered. A subscriber that
   * cancels before it has requested anything closes nothing, so it does not end another
   * subscriber's reading of a shared stream.
   *
   * @param stream the VStream to convert; must not be null
   * @param <A> the element type
   * @return a Flow.Publisher that publishes the stream's elements; never null
   * @throws NullPointerException if stream is null
   */
  public static <A> Flow.Publisher<A> toPublisher(VStream<A> stream) {
    Objects.requireNonNull(stream, "stream must not be null");
    return subscriber -> {
      Objects.requireNonNull(subscriber, "subscriber must not be null");
      subscriber.onSubscribe(new VStreamSubscription<>(stream, subscriber));
    };
  }

  /**
   * Creates a {@link VStream} from a {@link Flow.Publisher} with the specified buffer size.
   *
   * <p>Each run of the stream subscribes to the publisher afresh, when its first pull runs, so
   * building the stream subscribes to nothing, and a stream read twice subscribes twice, as a
   * {@link VStream#bracket} acquires its resource for each run. A cold publisher sends each run its
   * elements from the start. A hot one, such as a {@link java.util.concurrent.SubmissionPublisher},
   * sends a run only what it publishes after the run subscribes, so what it publishes between
   * building the stream and reading it is lost to that run: publish once the run has subscribed.
   *
   * <p>A run first asks the publisher for {@code bufferSize} elements, and buffers them in a queue
   * as they arrive. When the stream is pulled, an element is taken from the queue, and the
   * publisher is asked for one more. So a publisher that keeps to its demand never sends more than
   * the queue holds, and never waits for the stream to make room.
   *
   * <p>Closing the VStream cancels the subscription of its latest run, so a stream that stops
   * early, through {@link VStream#take(long)} or {@link VStream#headOption()} for example, stops
   * the publisher, and a pull waiting for an element gives {@code Done}. A tail's pulled {@code
   * VTask} gives the same step each time it runs; running the head's again starts another run,
   * which subscribes again. Holding the stream holds nothing a run has read.
   *
   * <p>The Flow rules forbid a null element. If the publisher sends one, the subscription is
   * cancelled, the stream fails with a {@link NullPointerException} when it is pulled that far, and
   * the subscriber throws one back to the publisher, as those rules ask.
   *
   * @param publisher the Flow.Publisher to convert; must not be null
   * @param bufferSize the maximum number of elements to buffer; must be positive
   * @param <A> the element type
   * @return a VStream that pulls from the publisher's output; never null
   * @throws NullPointerException if publisher is null
   * @throws IllegalArgumentException if bufferSize is not positive
   */
  public static <A> VStream<A> fromPublisher(Flow.Publisher<A> publisher, int bufferSize) {
    Objects.requireNonNull(publisher, "publisher must not be null");
    if (bufferSize <= 0) {
      throw new IllegalArgumentException("bufferSize must be positive, got: " + bufferSize);
    }
    // Each run subscribes at its first pull, and closing the head after a pull cancels that run's
    // subscription
    return new RememberingStream<>(VTask.delay(() -> subscribed(publisher, bufferSize)));
  }

  /** Subscribes to the publisher for one run, and gives the stream that reads what it sends. */
  private static <A> VStream<A> subscribed(Flow.Publisher<A> publisher, int bufferSize) {
    // Room for every element requested and the signal that ends the run, so a publisher that
    // keeps to its demand never waits for room
    LinkedBlockingQueue<Signal<A>> queue =
        new LinkedBlockingQueue<>(bufferSize == Integer.MAX_VALUE ? bufferSize : bufferSize + 1);
    Upstream upstream = new Upstream();

    publisher.subscribe(
        new Flow.Subscriber<>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            upstream.attach(subscription, bufferSize);
          }

          @Override
          public void onNext(@Nullable A item) {
            if (item == null) {
              // The Flow rules forbid a null element: fail the stream, and throw to the publisher
              NullPointerException refused =
                  new NullPointerException("VStream.fromPublisher element cannot be null");
              upstream.cancel();
              onError(refused);
              throw refused;
            }
            if (upstream.closed()) {
              // Sent after the stream was closed, so nothing will read it
              return;
            }
            try {
              // The stream asks for one more as it takes each element, not here
              queue.put(new Signal.Element<>(item));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              upstream.cancel();
              // Fail the run, rather than leave it waiting for an element that is gone
              queue.offer(new Signal.Error<>(e));
            }
          }

          @Override
          public void onError(Throwable throwable) {
            try {
              queue.put(new Signal.Error<>(throwable));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              queue.offer(new Signal.Error<>(throwable));
            }
          }

          @Override
          public void onComplete() {
            try {
              queue.put(new Signal.Complete<>());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              queue.offer(new Signal.Complete<>());
            }
          }
        });

    // The run's head keeps no step, so a stream held for a run holds nothing the run has read
    Queued<A> closing = new Queued<>(queue, upstream);
    return new VStream<>() {
      @Override
      public VTask<VStream.Step<A>> pull() {
        return new Queued<>(queue, upstream).pull();
      }

      @Override
      public VTask<Unit> close() {
        return closing.close();
      }
    };
  }

  /**
   * Creates a {@link VStream} from a {@link Flow.Publisher} with the default buffer size of 256.
   *
   * @param publisher the Flow.Publisher to convert; must not be null
   * @param <A> the element type
   * @return a VStream that pulls from the publisher's output; never null
   * @throws NullPointerException if publisher is null
   */
  public static <A> VStream<A> fromPublisher(Flow.Publisher<A> publisher) {
    return fromPublisher(publisher, DEFAULT_BUFFER_SIZE);
  }

  // ===== Internal helpers =====

  /**
   * A tail of a {@link #fromPublisher} run. Its pull takes the next signal from the run's queue
   * once, and gives the same step each time it runs; taking an element asks the publisher for one
   * more, so the queue holds no more than the buffer.
   */
  private static final class Queued<A> implements VStream<A> {

    private final LinkedBlockingQueue<Signal<A>> queue;
    private final Upstream upstream;
    private final ReentrantLock lock = new ReentrantLock();
    // Guarded by lock
    private VStream.@Nullable Step<A> step;
    private @Nullable Throwable failure;

    Queued(LinkedBlockingQueue<Signal<A>> queue, Upstream upstream) {
      this.queue = queue;
      this.upstream = upstream;
    }

    @Override
    public VTask<VStream.Step<A>> pull() {
      return () -> {
        lock.lockInterruptibly();
        try {
          if (step == null && failure == null) {
            switch (queue.take()) {
              case Signal.Element<A> e -> {
                step = new VStream.Step.Emit<>(e.value(), new Queued<>(queue, upstream));
                askForOneMore();
              }
              case Signal.Complete<A> _ -> step = new VStream.Step.Done<>();
              // The publisher's error, as it gave it
              case Signal.Error<A> err -> failure = err.error();
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

    /**
     * Asks the publisher for one more. The Flow rules forbid {@code request} to throw, but if it
     * does, the element just taken is kept and the next pull fails with what it threw.
     */
    private void askForOneMore() {
      try {
        upstream.request(1);
      } catch (RuntimeException e) {
        upstream.cancel();
        queue.offer(new Signal.Error<>(e));
      }
    }

    @Override
    public VTask<Unit> close() {
      return VTask.delay(
          () -> {
            upstream.cancel();
            // Make room for an element the publisher may be blocked delivering
            queue.clear();
            // Wakes a pull waiting on the queue, and a pull after this gives Done
            queue.offer(new Signal.Complete<>());
            return Unit.INSTANCE;
          });
    }
  }

  /**
   * The subscription a {@link #fromPublisher} run reads through, cancelled when the stream is
   * closed. The publisher may hand the subscription over after the stream is closed, so whichever
   * of the two comes second cancels it.
   *
   * <p>The Flow rules ask a subscriber to call its subscription's {@code request} and {@code
   * cancel} one at a time. The first request comes from {@code onSubscribe}, on whichever thread
   * the publisher calls it, and the later ones from the thread reading the stream. So each call is
   * recorded, and whichever thread finds no call in progress makes the calls recorded so far, one
   * after another. No thread waits for another, and nothing is called holding a lock, so a
   * publisher that holds a lock of its own while it signals cannot deadlock against the stream.
   */
  private static final class Upstream {

    private final AtomicInteger calling = new AtomicInteger();
    private final AtomicLong pending = new AtomicLong();
    private volatile boolean cancelRequested;
    private volatile Flow.@Nullable Subscription subscription;
    // Read and written only by the thread that holds calling above zero
    private boolean cancelled;

    /**
     * Records the subscription the publisher handed over and asks for the first elements, or
     * cancels it if the stream was closed first. A second subscription is cancelled, as the Flow
     * rules ask.
     */
    void attach(Flow.Subscription handedOver, long first) {
      if (subscription != null) {
        handedOver.cancel();
        return;
      }
      pending.addAndGet(first);
      subscription = handedOver;
      call();
    }

    void cancel() {
      cancelRequested = true;
      call();
    }

    /** Whether the stream has been closed, so an element sent now has no reader. */
    boolean closed() {
      return cancelRequested;
    }

    /** Asks the publisher for {@code n} more, unless the stream has been closed. */
    void request(long n) {
      pending.addAndGet(n);
      call();
    }

    /**
     * Makes the calls recorded so far, unless another thread is making them, in which case it makes
     * this one too. A {@code request} or {@code cancel} that throws, which the Flow rules forbid,
     * ends the calls to this subscription, and is thrown once the calls are made.
     */
    private void call() {
      if (calling.getAndIncrement() != 0) {
        return;
      }
      RuntimeException thrown = null;
      int missed = 1;
      do {
        Flow.Subscription held = subscription;
        if (held != null && !cancelled) {
          try {
            if (cancelRequested) {
              cancelled = true;
              held.cancel();
            } else {
              long n;
              while ((n = pending.getAndSet(0)) > 0) {
                held.request(n);
              }
            }
          } catch (RuntimeException e) {
            cancelled = true;
            thrown = e;
          }
        }
        missed = calling.addAndGet(-missed);
      } while (missed != 0);
      if (thrown != null) {
        throw thrown;
      }
    }
  }

  /** Internal signal type for the queue between publisher and VStream. */
  private sealed interface Signal<A> {
    record Element<A>(A value) implements Signal<A> {}

    record Complete<A>() implements Signal<A> {}

    record Error<A>(Throwable error) implements Signal<A> {}
  }

  /**
   * Subscription implementation that bridges VStream pulling to subscriber demand.
   *
   * <p>When the subscriber calls {@code request(n)}, n elements are pulled from the VStream on a
   * virtual thread and delivered to the subscriber. A drain holds the {@code reading} lock while it
   * pulls, so the stream is closed only once no drain is reading it.
   */
  private static final class VStreamSubscription<A> implements Flow.Subscription {

    private final Flow.Subscriber<? super A> subscriber;
    private final AtomicLong demand = new AtomicLong(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final ReentrantLock reading = new ReentrantLock();
    // Guarded by reading
    private VStream<A> current;
    private boolean pulled;
    // The drain thread while it pulls, which may wait on a quiet stream for as long as it stays
    // quiet
    private volatile @Nullable Thread puller;

    VStreamSubscription(VStream<A> stream, Flow.Subscriber<? super A> subscriber) {
      this.current = stream;
      this.subscriber = subscriber;
    }

    @Override
    public void request(long n) {
      if (n <= 0) {
        cancel();
        subscriber.onError(new IllegalArgumentException("request must be positive, got: " + n));
        return;
      }

      long prev = demand.getAndAdd(n);
      if (prev == 0) {
        drain();
      }
    }

    @Override
    public void cancel() {
      if (cancelled.compareAndSet(false, true)) {
        // A pull may wait on a quiet stream for as long as it stays quiet, so stop it
        Thread pulling = puller;
        if (pulling != null) {
          pulling.interrupt();
        }
        // Off the caller's thread, since closing waits for a running drain to stop
        Thread.startVirtualThread(() -> closeRemaining().runSafe());
      }
    }

    /**
     * Closes what is left of the stream, once no drain is reading it, so its finalisers run. Closes
     * nothing if this subscription never pulled the stream, which may be shared.
     */
    private VTask<Unit> closeRemaining() {
      return () -> {
        reading.lock();
        try {
          return pulled ? current.close().execute() : Unit.INSTANCE;
        } finally {
          reading.unlock();
        }
      };
    }

    private void drain() {
      if (!draining.compareAndSet(false, true)) {
        return;
      }

      Thread.startVirtualThread(
          () -> {
            reading.lock();
            try {
              readWhileDemanded();
            } catch (Throwable t) {
              // A cancel meanwhile closes the stream itself, the rest after a failed element
              // included
              if (cancelled.compareAndSet(false, true)) {
                Closing.runAfterFailure(closeRemaining(), t);
                subscriber.onError(t);
              }
            } finally {
              reading.unlock();
              draining.set(false);
              if (!cancelled.get() && demand.get() > 0) {
                // More demand arrived while we were draining
                drain();
              }
            }
          });
    }

    /** Pulls and delivers elements while there is demand. Called holding {@code reading}. */
    private void readWhileDemanded() throws Throwable {
      while (demand.get() > 0) {
        VStream.Step<A> step = pullUnlessCancelled();
        if (step == null) {
          return;
        }
        switch (step) {
          case VStream.Step.Emit<A> e -> {
            current = e.tail();
            demand.decrementAndGet();
            subscriber.onNext(e.value());
          }
          case VStream.Step.Skip<A> s -> current = s.tail();
          case VStream.Step.Fail<A> failed -> {
            // The drain's catch closes the stream after the failed element and signals onError
            current = failed.tail();
            throw failed.error();
          }
          case VStream.Step.Done<A> _ -> {
            cancelled.set(true);
            subscriber.onComplete();
            return;
          }
        }
      }
      // Demand exhausted — probe for stream completion so onComplete is called
      // even when there is no outstanding demand.
      while (demand.get() == 0) {
        VStream.Step<A> step = pullUnlessCancelled();
        if (step == null) {
          return;
        }
        switch (step) {
          case VStream.Step.Done<A> _ -> {
            cancelled.set(true);
            subscriber.onComplete();
            return;
          }
          case VStream.Step.Skip<A> s -> current = s.tail();
          case VStream.Step.Fail<A> failed -> {
            current = failed.tail();
            throw failed.error();
          }
          case VStream.Step.Emit<A> e -> {
            // Buffer this element for future demand
            current = DerivedStream.concatContinuing(VStream.of(e.value()), e.tail());
            return;
          }
        }
      }
    }

    /**
     * Pulls the stream where a cancel can interrupt the pull, since the stream may stay quiet for
     * as long as it likes. Returns null, delivering nothing, if the subscription is cancelled
     * before or during the pull; a step pulled meanwhile is moved past, so closing reaches what is
     * left. Called holding {@code reading}.
     */
    private VStream.@Nullable Step<A> pullUnlessCancelled() throws Throwable {
      VStream.Step<A> step;
      // Published before the cancelled check, so a cancel either stops the pull here or
      // interrupts it; once it is cleared, a cancel can no longer interrupt this thread
      puller = Thread.currentThread();
      try {
        if (cancelled.get()) {
          return null;
        }
        pulled = true;
        step = Executing.executed(current.pull());
      } finally {
        puller = null;
      }
      if (cancelled.get()) {
        current = restAfter(step);
        return null;
      }
      return step;
    }

    /** What is left of the stream after a step: its tail, or the stream itself once it is done. */
    private VStream<A> restAfter(VStream.Step<A> step) {
      return switch (step) {
        case VStream.Step.Emit<A> e -> e.tail();
        case VStream.Step.Skip<A> s -> s.tail();
        case VStream.Step.Fail<A> failed -> failed.tail();
        case VStream.Step.Done<A> _ -> current;
      };
    }
  }
}
