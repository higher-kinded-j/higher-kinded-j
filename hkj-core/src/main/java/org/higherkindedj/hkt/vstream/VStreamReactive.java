// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
 * Backpressure is respected: elements are only pulled when the subscriber requests them via {@link
 * Flow.Subscription#request(long)}.
 *
 * <h2>fromPublisher</h2>
 *
 * <p>Converts a {@link Flow.Publisher} to a VStream. The publisher is subscribed to, and incoming
 * elements are buffered in a bounded queue. The VStream's {@code pull()} reads from this queue,
 * providing pull-based access to the push-based source. Closing the VStream cancels the
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
   * is only pulled when the subscriber has outstanding demand. Elements are pulled on virtual
   * threads.
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
   * <p>The publisher is subscribed to immediately. Incoming elements are buffered in a bounded
   * queue. When the VStream is pulled, elements are taken from this queue. Backpressure is applied
   * to the publisher by requesting elements in batches based on available buffer space.
   *
   * <p>Closing the VStream cancels the subscription, so a stream that stops early, through {@link
   * VStream#take(long)} or {@link VStream#headOption()} for example, stops the publisher.
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

    LinkedBlockingQueue<Signal<A>> queue = new LinkedBlockingQueue<>(bufferSize);
    Upstream upstream = new Upstream();

    publisher.subscribe(
        new Flow.Subscriber<>() {
          private Flow.Subscription subscription;

          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (upstream.attach(subscription)) {
              subscription.request(bufferSize);
            }
          }

          @Override
          public void onNext(A item) {
            try {
              queue.put(new Signal.Element<>(item));
              subscription.request(1);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              subscription.cancel();
            }
          }

          @Override
          public void onError(Throwable throwable) {
            try {
              queue.put(new Signal.Error<>(throwable));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }

          @Override
          public void onComplete() {
            try {
              queue.put(new Signal.Complete<>());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        });

    return pullFromQueue(queue, upstream);
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

  private static <A> VStream<A> pullFromQueue(
      LinkedBlockingQueue<Signal<A>> queue, Upstream upstream) {
    return new VStream<>() {
      @Override
      public VTask<VStream.Step<A>> pull() {
        return VTask.of(
            () -> {
              Signal<A> signal = queue.take();
              return switch (signal) {
                case Signal.Element<A> e ->
                    new VStream.Step.Emit<>(e.value(), pullFromQueue(queue, upstream));
                case Signal.Complete<A> _ -> new VStream.Step.Done<>();
                case Signal.Error<A> err -> throw wrapIfChecked(err.error());
              };
            });
      }

      @Override
      public VTask<Unit> close() {
        return VTask.delay(
            () -> {
              upstream.cancel();
              // Make room for an element the publisher may be blocked delivering
              queue.clear();
              return Unit.INSTANCE;
            });
      }
    };
  }

  private static RuntimeException wrapIfChecked(Throwable t) {
    if (t instanceof RuntimeException re) return re;
    if (t instanceof Error e) throw e;
    return new RuntimeException(t);
  }

  /**
   * The subscription a {@link #fromPublisher} stream reads through, cancelled when the stream is
   * closed. The publisher may hand the subscription over after the stream is closed, so whichever
   * of the two comes second cancels it.
   */
  private static final class Upstream {

    private final AtomicReference<Flow.@Nullable Subscription> subscription =
        new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Records the subscription the publisher handed over.
     *
     * @return false if the stream was already closed, in which case the subscription is cancelled
     */
    boolean attach(Flow.Subscription handedOver) {
      subscription.set(handedOver);
      if (cancelled.get()) {
        handedOver.cancel();
        return false;
      }
      return true;
    }

    void cancel() {
      if (cancelled.compareAndSet(false, true)) {
        Flow.Subscription held = subscription.get();
        if (held != null) {
          held.cancel();
        }
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
    private void readWhileDemanded() {
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
    private VStream.@Nullable Step<A> pullUnlessCancelled() {
      VStream.Step<A> step;
      // Published before the cancelled check, so a cancel either stops the pull here or
      // interrupts it; once it is cleared, a cancel can no longer interrupt this thread
      puller = Thread.currentThread();
      try {
        if (cancelled.get()) {
          return null;
        }
        pulled = true;
        step = current.pull().run();
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
        case VStream.Step.Done<A> _ -> current;
      };
    }
  }
}
