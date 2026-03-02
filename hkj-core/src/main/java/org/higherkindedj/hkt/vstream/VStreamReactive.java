// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.higherkindedj.hkt.vtask.VTask;

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
 * providing pull-based access to the push-based source.
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

    publisher.subscribe(
        new Flow.Subscriber<>() {
          private Flow.Subscription subscription;

          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(bufferSize);
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

    return pullFromQueue(queue);
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

  private static <A> VStream<A> pullFromQueue(LinkedBlockingQueue<Signal<A>> queue) {
    return () ->
        VTask.of(
            () -> {
              Signal<A> signal = queue.take();
              return switch (signal) {
                case Signal.Element<A> e ->
                    new VStream.Step.Emit<>(e.value(), pullFromQueue(queue));
                case Signal.Complete<A> _ -> new VStream.Step.Done<>();
                case Signal.Error<A> err -> throw wrapIfChecked(err.error());
              };
            });
  }

  private static RuntimeException wrapIfChecked(Throwable t) {
    if (t instanceof RuntimeException re) return re;
    if (t instanceof Error e) throw e;
    return new RuntimeException(t);
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
   * virtual thread and delivered to the subscriber.
   */
  private static final class VStreamSubscription<A> implements Flow.Subscription {

    private VStream<A> current;
    private final Flow.Subscriber<? super A> subscriber;
    private final AtomicLong demand = new AtomicLong(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);

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
      cancelled.set(true);
    }

    private void drain() {
      if (!draining.compareAndSet(false, true)) {
        return;
      }

      Thread.startVirtualThread(
          () -> {
            try {
              while (!cancelled.get() && demand.get() > 0) {
                VStream.Step<A> step = current.pull().run();
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
              while (!cancelled.get() && demand.get() == 0) {
                VStream.Step<A> step = current.pull().run();
                switch (step) {
                  case VStream.Step.Done<A> _ -> {
                    cancelled.set(true);
                    subscriber.onComplete();
                    return;
                  }
                  case VStream.Step.Skip<A> s -> current = s.tail();
                  case VStream.Step.Emit<A> e -> {
                    // Buffer this element for future demand
                    final A value = e.value();
                    final VStream<A> tail = e.tail();
                    current = () -> VTask.succeed(new VStream.Step.Emit<>(value, tail));
                    return;
                  }
                }
              }
            } catch (Throwable t) {
              if (!cancelled.get()) {
                cancelled.set(true);
                subscriber.onError(t);
              }
            } finally {
              draining.set(false);
              // Check if more demand arrived while we were draining
              if (!cancelled.get() && demand.get() > 0) {
                drain();
              }
            }
          });
    }
  }
}
