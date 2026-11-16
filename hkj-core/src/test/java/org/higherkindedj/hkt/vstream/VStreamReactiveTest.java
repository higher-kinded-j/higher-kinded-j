// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for {@link VStreamReactive} Flow.Publisher bridge.
 *
 * <p>Verifies bidirectional conversion between VStream and Flow.Publisher, including element
 * delivery, error propagation, backpressure, and round-trip consistency.
 */
@DisplayName("VStreamReactive Test Suite")
class VStreamReactiveTest {

  @Nested
  @DisplayName("toPublisher")
  class ToPublisherTests {

    @Test
    @DisplayName("all elements delivered to subscriber")
    void allElementsDelivered() throws Exception {
      VStream<String> stream = VStream.of("a", "b", "c");
      Flow.Publisher<String> publisher = VStreamReactive.toPublisher(stream);

      List<String> received = new ArrayList<>();
      CountDownLatch completed = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
              received.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
              completed.countDown();
            }

            @Override
            public void onComplete() {
              completed.countDown();
            }
          });

      assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("onComplete called after last element")
    void onCompleteCalledAfterLastElement() throws Exception {
      VStream<Integer> stream = VStream.of(1, 2);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      AtomicBoolean completeCalled = new AtomicBoolean(false);
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Integer item) {}

            @Override
            public void onError(Throwable throwable) {
              latch.countDown();
            }

            @Override
            public void onComplete() {
              completeCalled.set(true);
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(completeCalled).isTrue();
    }

    @Test
    @DisplayName("onError called on stream failure")
    void onErrorCalledOnStreamFailure() throws Exception {
      RuntimeException error = new RuntimeException("stream failed");
      VStream<String> stream = VStream.fail(error);
      Flow.Publisher<String> publisher = VStreamReactive.toPublisher(stream);

      AtomicReference<Throwable> receivedError = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable throwable) {
              receivedError.set(throwable);
              latch.countDown();
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(receivedError.get()).isNotNull();
    }

    @Test
    @DisplayName("backpressure: request(1) delivers one element at a time")
    void backpressureRequestOneDelivers() throws Exception {
      VStream<Integer> stream = VStream.of(1, 2, 3);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            private int remaining = 3;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              this.subscription = subscription;
              subscription.request(1);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
              remaining--;
              if (remaining > 0) {
                subscription.request(1);
              }
            }

            @Override
            public void onError(Throwable throwable) {
              latch.countDown();
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("cancel stops delivery")
    void cancelStopsDelivery() throws Exception {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      CountDownLatch firstReceived = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              this.subscription = subscription;
              subscription.request(1);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
              firstReceived.countDown();
              subscription.cancel();
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
          });

      assertThat(firstReceived.await(5, TimeUnit.SECONDS)).isTrue();
      // Give time for any additional elements to arrive (they shouldn't)
      Thread.sleep(100);
      assertThat(received).containsExactly(1);
    }

    @Test
    @DisplayName("empty stream triggers onComplete immediately")
    void emptyStreamTriggersOnCompleteImmediately() throws Exception {
      VStream<String> stream = VStream.empty();
      Flow.Publisher<String> publisher = VStreamReactive.toPublisher(stream);

      AtomicBoolean completeCalled = new AtomicBoolean(false);
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable throwable) {
              latch.countDown();
            }

            @Override
            public void onComplete() {
              completeCalled.set(true);
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(completeCalled).isTrue();
    }
  }

  @Nested
  @DisplayName("fromPublisher")
  // A wait that never ends fails the test rather than hanging the build
  @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  class FromPublisherTests {

    @Test
    @DisplayName("all publisher elements available in VStream")
    void allPublisherElementsAvailable() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 16);

      // Publish elements in background
      whenSubscribed(
          publisher,
          () -> {
            publisher.submit("x");
            publisher.submit("y");
            publisher.submit("z");
            publisher.close();
          });

      List<String> result = stream.toList().run();

      assertThat(result).containsExactly("x", "y", "z");
    }

    @Test
    @DisplayName("completion maps to Done")
    void completionMapsToDone() {
      SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>();

      VStream<Integer> stream = VStreamReactive.fromPublisher(publisher, 16);

      whenSubscribed(publisher, publisher::close);

      List<Integer> result = stream.toList().run();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("error maps to failed VTask")
    void errorMapsToFailedVTask() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 16);

      whenSubscribed(
          publisher, () -> publisher.closeExceptionally(new RuntimeException("pub error")));

      assertThatThrownBy(() -> stream.toList().run()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("buffer respects configured size")
    void bufferRespectsConfiguredSize() {
      SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>();

      // Small buffer
      VStream<Integer> stream = VStreamReactive.fromPublisher(publisher, 2);

      whenSubscribed(
          publisher,
          () -> {
            publisher.submit(1);
            publisher.submit(2);
            publisher.submit(3);
            publisher.close();
          });

      List<Integer> result = stream.toList().run();

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("default buffer size is used when not specified")
    void defaultBufferSizeUsed() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

      VStream<String> stream = VStreamReactive.fromPublisher(publisher);

      whenSubscribed(
          publisher,
          () -> {
            publisher.submit("a");
            publisher.close();
          });

      List<String> result = stream.toList().run();

      assertThat(result).containsExactly("a");
    }
  }

  /**
   * Starts a run of the stream on a virtual thread, whose first pull subscribes to its publisher.
   */
  private static Thread startRun(VStream<?> stream) {
    return Thread.startVirtualThread(() -> stream.pull().runSafe());
  }

  /**
   * Publishes once the stream's run has subscribed. A run subscribes at its first pull, and a hot
   * publisher such as {@link SubmissionPublisher} sends a subscriber only what it submits after
   * that subscriber subscribes.
   */
  private static void whenSubscribed(SubmissionPublisher<?> publisher, Runnable publishing) {
    Thread.startVirtualThread(
        () -> {
          await()
              .atMost(Duration.ofSeconds(5))
              .pollInterval(Duration.ofMillis(1))
              .until(() -> publisher.getNumberOfSubscribers() > 0);
          publishing.run();
        });
  }

  /**
   * A publisher that sends one element at the first request, then nothing more, and runs {@code
   * onCancel} when cancelled, so a pull after the first waits until its run is closed.
   */
  private static Flow.Publisher<Integer> oneThenQuiet(Runnable onCancel) {
    return subscriber -> {
      AtomicBoolean sent = new AtomicBoolean();
      subscriber.onSubscribe(
          new Flow.Subscription() {
            @Override
            public void request(long n) {
              if (sent.compareAndSet(false, true)) {
                subscriber.onNext(1);
              }
            }

            @Override
            public void cancel() {
              onCancel.run();
            }
          });
    };
  }

  /**
   * A cold publisher of {@code count} integers from 1, sent as they are requested, on the
   * requesting thread. It counts its subscriptions and the elements requested of it.
   */
  private static Flow.Publisher<Integer> counting(
      int count, AtomicInteger subscriptions, AtomicLong requested, AtomicBoolean cancelled) {
    return subscriber -> {
      subscriptions.incrementAndGet();
      AtomicInteger sent = new AtomicInteger();
      AtomicBoolean sending = new AtomicBoolean();
      AtomicLong owed = new AtomicLong();
      subscriber.onSubscribe(
          new Flow.Subscription() {
            @Override
            public void request(long n) {
              requested.addAndGet(n);
              owed.addAndGet(n);
              // Send on the outermost request only, so a request made from onNext adds to the
              // demand rather than recursing
              if (!sending.compareAndSet(false, true)) {
                return;
              }
              try {
                while (owed.get() > 0 && sent.get() < count && !cancelled.get()) {
                  owed.decrementAndGet();
                  subscriber.onNext(sent.incrementAndGet());
                }
                if (sent.get() == count && !cancelled.get()) {
                  subscriber.onComplete();
                }
              } finally {
                sending.set(false);
              }
            }

            @Override
            public void cancel() {
              cancelled.set(true);
            }
          });
    };
  }

  @Nested
  @DisplayName("fromPublisher runs")
  // A wait that never ends fails the test rather than hanging the build
  @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  class FromPublisherRuns {

    private final AtomicInteger subscriptions = new AtomicInteger();
    private final AtomicLong requested = new AtomicLong();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    @Test
    @DisplayName(
        "building the stream and taking its first pull subscribe to nothing; running that pull"
            + " subscribes")
    void subscribesAtTheFirstPull() {
      VStream<Integer> stream =
          VStreamReactive.fromPublisher(counting(3, subscriptions, requested, cancelled), 4);
      VTask<VStream.Step<Integer>> firstPull = stream.pull();

      assertThat(subscriptions).hasValue(0);
      VStream.Step<Integer> first = firstPull.run();
      assertThat(first).isInstanceOf(VStream.Step.Emit.class);
      assertThat(subscriptions).hasValue(1);
      ((VStream.Step.Emit<Integer>) first).tail().close().run();
    }

    @Test
    @DisplayName("each run subscribes afresh, so a cold publisher's stream reads the same twice")
    void eachRunSubscribesAfresh() {
      VStream<Integer> stream =
          VStreamReactive.fromPublisher(counting(3, subscriptions, requested, cancelled), 4);

      assertThat(stream.toList().run()).containsExactly(1, 2, 3);
      assertThat(stream.toList().run()).containsExactly(1, 2, 3);
      assertThat(subscriptions).hasValue(2);
    }

    @Test
    @DisplayName("a hot publisher sends a run only what it publishes after the run subscribes")
    void aHotPublisherSendsWhatComesAfter() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 4);

      publisher.submit("before any run");
      whenSubscribed(
          publisher,
          () -> {
            publisher.submit("after the run subscribed");
            publisher.close();
          });

      assertThat(stream.toList().run()).containsExactly("after the run subscribed");
    }

    @Test
    @DisplayName("the run asks for bufferSize elements, then one more for each element taken")
    void demandFollowsWhatIsTaken() {
      VStream<Integer> stream =
          VStreamReactive.fromPublisher(counting(10, subscriptions, requested, cancelled), 4);

      VStream.Step<Integer> first = stream.pull().run();
      long afterOneTaken = requested.get();
      assertThat(first).isInstanceOf(VStream.Step.Emit.class);
      VStream.Step<Integer> second = ((VStream.Step.Emit<Integer>) first).tail().pull().run();

      assertThat(second).isInstanceOf(VStream.Step.Emit.class);
      assertThat(afterOneTaken).isEqualTo(5);
      assertThat(requested).hasValue(6);
      ((VStream.Step.Emit<Integer>) second).tail().close().run();
    }

    @Test
    @DisplayName("a pulled VTask gives the same step each time it runs")
    void aPulledTaskRunsAgain() {
      VStream<Integer> stream =
          VStreamReactive.fromPublisher(counting(3, subscriptions, requested, cancelled), 4);
      VStream<Integer> tail = ((VStream.Step.Emit<Integer>) stream.pull().run()).tail();

      VTask<VStream.Step<Integer>> pulled = tail.pull();
      VStream.Step<Integer> once = pulled.run();
      VStream.Step<Integer> twice = pulled.run();

      assertThat(((VStream.Step.Emit<Integer>) once).value()).isEqualTo(2);
      assertThat(twice).isSameAs(once);
      tail.close().run();
    }

    @Test
    @DisplayName(
        "closing the tail a pull gave cancels that run's subscription, and closing the stream"
            + " itself does not")
    void closingAfterAPullCancels() {
      VStream<Integer> stream =
          VStreamReactive.fromPublisher(counting(10, subscriptions, requested, cancelled), 4);

      VStream<Integer> tail = ((VStream.Step.Emit<Integer>) stream.pull().run()).tail();
      stream.close().run();
      assertThat(cancelled).as("closing the stream itself").isFalse();

      tail.close().run();
      assertThat(cancelled).isTrue();
    }

    @Test
    @DisplayName("a tail remembers an error, and throws the same one each time it is pulled")
    void aTailRemembersAnError() {
      IOException down = new IOException("the feed is down");
      Flow.Publisher<Integer> failing =
          subscriber -> {
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {}

                  @Override
                  public void cancel() {}
                });
            subscriber.onNext(1);
            subscriber.onError(down);
          };
      VStream<Integer> stream = VStreamReactive.fromPublisher(failing, 4);
      VStream.Step<Integer> first = stream.pull().run();
      assertThat(first).isInstanceOf(VStream.Step.Emit.class);
      VTask<VStream.Step<Integer>> pulled = ((VStream.Step.Emit<Integer>) first).tail().pull();

      Try<VStream.Step<Integer>> once = pulled.runSafe();
      Try<VStream.Step<Integer>> twice = pulled.runSafe();

      assertThat(((Try.Failure<VStream.Step<Integer>>) once).cause()).isSameAs(down);
      assertThat(((Try.Failure<VStream.Step<Integer>>) twice).cause()).isSameAs(down);
    }

    @Test
    @DisplayName("an element sent after the run is closed is dropped, so it cannot fill the buffer")
    void anElementSentAfterCloseIsDropped() {
      AtomicReference<Flow.Subscriber<? super Integer>> reading = new AtomicReference<>();
      Flow.Publisher<Integer> publisher =
          subscriber -> {
            reading.set(subscriber);
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {
                    if (requested.getAndAdd(n) == 0) {
                      subscriber.onNext(1);
                    }
                  }

                  @Override
                  public void cancel() {
                    cancelled.set(true);
                  }
                });
          };
      VStream<Integer> stream = VStreamReactive.fromPublisher(publisher, 1);

      VStream.Step<Integer> first = stream.pull().run();
      assertThat(first).isInstanceOf(VStream.Step.Emit.class);
      ((VStream.Step.Emit<Integer>) first).tail().close().run();
      assertThat(cancelled).isTrue();

      // The buffer has room for one element and the end, and the end is already in it, so a
      // second element kept rather than dropped would block this thread
      assertTimeoutPreemptively(
          Duration.ofSeconds(5),
          () -> {
            reading.get().onNext(2);
            reading.get().onNext(3);
          });
    }

    @Test
    @DisplayName("a signal that ends a run after the run is closed is dropped, so never waits")
    void anEndAfterCloseIsDropped() {
      AtomicReference<Flow.Subscriber<? super Integer>> reading = new AtomicReference<>();
      Flow.Publisher<Integer> publisher =
          subscriber -> {
            reading.set(subscriber);
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {
                    if (requested.getAndAdd(n) == 0) {
                      subscriber.onNext(1);
                    }
                  }

                  @Override
                  public void cancel() {}
                });
          };
      VStream<Integer> stream = VStreamReactive.fromPublisher(publisher, 1);

      VStream.Step<Integer> first = stream.pull().run();
      assertThat(first).isInstanceOf(VStream.Step.Emit.class);
      ((VStream.Step.Emit<Integer>) first).tail().close().run();

      // The buffer has room for one element and the end, and the close's end is already in it.
      // The error takes the room left, as an element sent while the stream closed can, so the end
      // after it would block this thread if signals after the close were kept
      assertTimeoutPreemptively(
          Duration.ofSeconds(5),
          () -> {
            reading.get().onError(new IOException("too late"));
            reading.get().onComplete();
          });
    }

    @Test
    @DisplayName(
        "a request that throws fails the run once the element just taken has been read, and"
            + " cancels")
    void aRequestThatThrowsFailsTheRun() {
      IllegalStateException refused = new IllegalStateException("request refused");
      IllegalStateException cancelRefused = new IllegalStateException("cancel refused");
      Flow.Publisher<Integer> publisher =
          subscriber ->
              subscriber.onSubscribe(
                  new Flow.Subscription() {
                    private final AtomicInteger calls = new AtomicInteger();

                    @Override
                    public void request(long n) {
                      if (calls.getAndIncrement() > 0) {
                        throw refused;
                      }
                      subscriber.onNext(1);
                    }

                    @Override
                    public void cancel() {
                      cancelled.set(true);
                      throw cancelRefused;
                    }
                  });
      VStream<Integer> stream = VStreamReactive.fromPublisher(publisher, 1);

      VStream.Step<Integer> first = stream.pull().run();

      assertThat(first).isInstanceOf(VStream.Step.Emit.class);
      assertThat(((VStream.Step.Emit<Integer>) first).value()).isEqualTo(1);
      Try<VStream.Step<Integer>> next =
          ((VStream.Step.Emit<Integer>) first).tail().pull().runSafe();
      assertThat(next).isEqualTo(Try.failure(refused));
      assertThat(cancelled).isTrue();
      // The cancel threw as well, and the run's failure keeps it
      assertThat(refused.getSuppressed()).containsExactly(cancelRefused);
    }

    @Test
    @DisplayName("a request that throws on the publisher's thread fails the run there too")
    void aRequestThatThrowsOnThePublishersThread() {
      IllegalStateException refused = new IllegalStateException("request refused");
      AtomicBoolean returned = new AtomicBoolean();
      Flow.Publisher<Integer> publisher =
          subscriber ->
              Thread.startVirtualThread(
                  () -> {
                    subscriber.onSubscribe(
                        new Flow.Subscription() {
                          @Override
                          public void request(long n) {
                            throw refused;
                          }

                          @Override
                          public void cancel() {
                            cancelled.set(true);
                          }
                        });
                    returned.set(true);
                  });

      Try<List<Integer>> read = VStreamReactive.fromPublisher(publisher, 4).toList().runSafe();

      assertThat(read).isEqualTo(Try.failure(refused));
      assertThat(cancelled).isTrue();
      // The Flow rules ask onSubscribe to return normally
      await().atMost(Duration.ofSeconds(5)).untilTrue(returned);
    }

    @Test
    @DisplayName("a second onSubscribe is cancelled, and nothing is requested from it")
    void aSecondSubscriptionIsCancelled() {
      AtomicBoolean secondRequested = new AtomicBoolean();
      AtomicBoolean secondCancelled = new AtomicBoolean();
      Flow.Publisher<Integer> publisher =
          subscriber -> {
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {
                    subscriber.onNext(1);
                    subscriber.onComplete();
                  }

                  @Override
                  public void cancel() {}
                });
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {
                    secondRequested.set(true);
                  }

                  @Override
                  public void cancel() {
                    secondCancelled.set(true);
                  }
                });
          };

      assertThat(VStreamReactive.fromPublisher(publisher, 4).toList().run()).containsExactly(1);
      assertThat(secondCancelled).isTrue();
      assertThat(secondRequested).isFalse();
    }

    @Test
    @DisplayName("holding the stream holds nothing a run has read")
    void holdingTheStreamHoldsNothingRead() {
      List<WeakReference<Object>> sent = new CopyOnWriteArrayList<>();
      Flow.Publisher<Object> publisher =
          subscriber -> {
            AtomicBoolean done = new AtomicBoolean();
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {
                    if (done.compareAndSet(false, true)) {
                      for (int i = 0; i < 3; i++) {
                        Object element = new byte[1024];
                        sent.add(new WeakReference<>(element));
                        subscriber.onNext(element);
                      }
                      subscriber.onComplete();
                    }
                  }

                  @Override
                  public void cancel() {}
                });
          };
      VStream<Object> stream = VStreamReactive.fromPublisher(publisher, 4);

      assertThat(stream.count().run()).isEqualTo(3);
      await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () -> {
                System.gc();
                return sent.stream().allMatch(element -> element.get() == null);
              });
      Reference.reachabilityFence(stream);
    }

    @Test
    @DisplayName("closing a run's tail wakes a pull that waits for an element, which gives Done")
    void closingWakesAWaitingPull() throws Exception {
      VStream<Integer> stream = VStreamReactive.fromPublisher(oneThenQuiet(() -> {}), 4);
      VStream<Integer> rest = ((VStream.Step.Emit<Integer>) stream.pull().run()).tail();
      AtomicReference<Try<VStream.Step<Integer>>> outcome = new AtomicReference<>();
      Thread pulling = Thread.startVirtualThread(() -> outcome.set(rest.pull().runSafe()));
      await().atMost(Duration.ofSeconds(5)).until(() -> pulling.getState() == Thread.State.WAITING);

      rest.close().run();
      pulling.join(Duration.ofSeconds(5));

      assertThat(outcome.get().isSuccess()).isTrue();
      assertThat(((Try.Success<VStream.Step<Integer>>) outcome.get()).value())
          .isInstanceOf(VStream.Step.Done.class);
    }

    @Test
    @DisplayName("a cancel that throws still wakes a waiting pull, and the close fails with it")
    void aCancelThatThrowsStillWakesAPull() throws Exception {
      IllegalStateException refused = new IllegalStateException("cancel refused");
      VStream<Integer> stream =
          VStreamReactive.fromPublisher(
              oneThenQuiet(
                  () -> {
                    throw refused;
                  }),
              4);
      VStream<Integer> rest = ((VStream.Step.Emit<Integer>) stream.pull().run()).tail();
      AtomicReference<Try<VStream.Step<Integer>>> outcome = new AtomicReference<>();
      Thread pulling = Thread.startVirtualThread(() -> outcome.set(rest.pull().runSafe()));
      await().atMost(Duration.ofSeconds(5)).until(() -> pulling.getState() == Thread.State.WAITING);

      assertThat(rest.close().runSafe()).isEqualTo(Try.failure(refused));
      pulling.join(Duration.ofSeconds(5));

      assertThat(outcome.get().isSuccess()).isTrue();
      assertThat(((Try.Success<VStream.Step<Integer>>) outcome.get()).value())
          .isInstanceOf(VStream.Step.Done.class);
    }

    @Test
    @DisplayName("a buffer of Integer.MAX_VALUE works")
    void aBufferOfMaxValue() {
      VStream<Integer> stream =
          VStreamReactive.fromPublisher(
              counting(3, subscriptions, requested, cancelled), Integer.MAX_VALUE);

      assertThat(stream.toList().run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("a publisher that holds its own lock while it signals cannot deadlock the stream")
    void aLockingPublisherCannotDeadlock() {
      Thread reader = Thread.currentThread();
      Object lock = new Object();
      Flow.Publisher<String> locking =
          subscriber -> {
            long[] demand = {0};
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {
                    synchronized (lock) {
                      demand[0] += n;
                      lock.notifyAll();
                    }
                  }

                  @Override
                  public void cancel() {}
                });
            Thread.startVirtualThread(
                () -> {
                  synchronized (lock) {
                    try {
                      while (demand[0] == 0) {
                        lock.wait();
                      }
                    } catch (InterruptedException e) {
                      return;
                    }
                    subscriber.onNext("a");
                    // Still holding the lock, wait until the reader is blocked asking for more,
                    // then send a null, which makes the stream cancel
                    while (reader.getState() != Thread.State.BLOCKED) {
                      Thread.onSpinWait();
                    }
                    try {
                      subscriber.onNext(null);
                    } catch (NullPointerException refused) {
                      // The Flow rules: the subscriber throws one back
                    }
                  }
                });
          };

      assertThatNullPointerException()
          .isThrownBy(() -> VStreamReactive.fromPublisher(locking, 4).toList().run())
          .withMessage("VStream.fromPublisher element cannot be null");
    }

    @Test
    @DisplayName("closing a stream that was never run cancels nothing, since nothing subscribed")
    void closingBeforeARunCancelsNothing() {
      VStream<Integer> stream =
          VStreamReactive.fromPublisher(counting(3, subscriptions, requested, cancelled), 4);

      stream.close().run();

      assertThat(subscriptions).hasValue(0);
      assertThat(stream.toList().run()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("Round-trip")
  class RoundTripTests {

    @Test
    @DisplayName("VStream -> Publisher -> VStream preserves elements")
    void vstreamPublisherVstreamRoundTrip() {
      VStream<Integer> original = VStream.of(10, 20, 30);

      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(original);
      VStream<Integer> roundTripped = VStreamReactive.fromPublisher(publisher, 16);

      List<Integer> result = roundTripped.toList().run();

      assertThat(result).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("empty round-trip preserves emptiness")
    void emptyRoundTrip() {
      VStream<String> original = VStream.empty();

      Flow.Publisher<String> publisher = VStreamReactive.toPublisher(original);
      VStream<String> roundTripped = VStreamReactive.fromPublisher(publisher, 16);

      List<String> result = roundTripped.toList().run();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Subscription Edge Cases")
  class SubscriptionEdgeCaseTests {

    @Test
    @DisplayName("request(0) triggers onError with IllegalArgumentException")
    void requestZeroTriggersOnError() throws Exception {
      VStream<Integer> stream = VStream.of(1, 2, 3);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      AtomicReference<Throwable> receivedError = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              subscription.request(0);
            }

            @Override
            public void onNext(Integer item) {}

            @Override
            public void onError(Throwable throwable) {
              receivedError.set(throwable);
              latch.countDown();
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(receivedError.get())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("request must be positive");
    }

    @Test
    @DisplayName("request(-1) triggers onError with IllegalArgumentException")
    void requestNegativeTriggersOnError() throws Exception {
      VStream<Integer> stream = VStream.of(1, 2, 3);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      AtomicReference<Throwable> receivedError = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              subscription.request(-1);
            }

            @Override
            public void onNext(Integer item) {}

            @Override
            public void onError(Throwable throwable) {
              receivedError.set(throwable);
              latch.countDown();
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(receivedError.get())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("request must be positive");
    }

    @Test
    @DisplayName("demand exhausted probe detects Done and calls onComplete")
    void demandExhaustedProbeDetectsDone() throws Exception {
      // Request exactly the number of elements - drain exhausts demand,
      // then probes for completion which sees Done and calls onComplete
      VStream<Integer> stream = VStream.of(1, 2);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      AtomicBoolean completeCalled = new AtomicBoolean(false);
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              // Request exactly 2 — matches element count, demand hits 0
              // then drain probes for Done
              subscription.request(2);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
              latch.countDown();
            }

            @Override
            public void onComplete() {
              completeCalled.set(true);
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(1, 2);
      assertThat(completeCalled).isTrue();
    }

    @Test
    @DisplayName("demand exhausted probe buffers Emit for later request")
    void demandExhaustedProbeBuffersEmit() throws Exception {
      // Request 1 at a time with more elements available. After delivering the first
      // element, demand drops to 0 and the probe loop encounters the second Emit,
      // which it buffers. When more demand arrives, the buffered element is delivered.
      VStream<Integer> stream = VStream.of(1, 2, 3);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              this.subscription = subscription;
              subscription.request(1);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
              // After first element, request more to drain remaining
              if (received.size() < 3) {
                subscription.request(1);
              }
            }

            @Override
            public void onError(Throwable throwable) {
              latch.countDown();
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("demand exhausted probe skips Skip steps before finding Done")
    void demandExhaustedProbeSkipsSkipSteps() throws Exception {
      // filter() produces Skip steps. When demand is exhausted but the stream
      // has trailing Skip steps before Done, the probe loop must skip them.
      VStream<Integer> stream = VStream.of(1, 2, 3).filter(n -> n <= 1);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      AtomicBoolean completeCalled = new AtomicBoolean(false);
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              subscription.request(1);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
              latch.countDown();
            }

            @Override
            public void onComplete() {
              completeCalled.set(true);
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(1);
      assertThat(completeCalled).isTrue();
    }

    @Test
    @DisplayName("probe loop exits when cancelled during Skip processing")
    void probeLoopExitsWhenCancelledDuringSkip() throws Exception {
      // Exercises the branch where the probe while-condition (!cancelled && demand==0)
      // becomes false because cancelled is set to true while processing a Skip.
      CountDownLatch probeSkipStarted = new CountDownLatch(1);
      CountDownLatch cancelDone = new CountDownLatch(1);

      // A stream tail whose pull() is slow, giving time for cancel to happen
      VStream<Integer> slowSkipTail =
          () ->
              VTask.of(
                  () -> {
                    probeSkipStarted.countDown();
                    assertThat(cancelDone.await(5, TimeUnit.SECONDS)).isTrue();
                    return new VStream.Step.Skip<>(VStream.of(99));
                  });

      // Stream: emit(1) -> slow skip -> emit(99)
      // Request exactly 1 so demand=0 after delivering 1, then probe loop starts.
      VStream<Integer> stream = () -> VTask.succeed(new VStream.Step.Emit<>(1, slowSkipTail));

      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      AtomicReference<Flow.Subscription> subRef = new AtomicReference<>();

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
              subRef.set(s);
              s.request(1);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onComplete() {}
          });

      // Wait for probe loop to start pulling the slow skip
      assertThat(probeSkipStarted.await(5, TimeUnit.SECONDS)).isTrue();
      // Cancel the subscription while probe is blocked
      subRef.get().cancel();
      // Let the slow skip complete — probe re-checks condition, sees cancelled=true, exits
      cancelDone.countDown();

      Thread.sleep(200);
      // Only element 1 was delivered; element 99 was not reached due to cancel
      assertThat(received).containsExactly(1);
    }

    @Test
    @DisplayName("probe loop exits when demand arrives during Skip processing")
    void probeLoopExitsWhenDemandArrivesDuringSkip() throws Exception {
      // Exercises the branch where the probe while-condition (!cancelled && demand==0)
      // becomes false because demand > 0 after processing a Skip. The finally block
      // then triggers a re-drain that delivers remaining elements.
      CountDownLatch probeSkipStarted = new CountDownLatch(1);
      CountDownLatch demandAdded = new CountDownLatch(1);

      VStream<Integer> slowSkipTail =
          () ->
              VTask.of(
                  () -> {
                    probeSkipStarted.countDown();
                    assertThat(demandAdded.await(5, TimeUnit.SECONDS)).isTrue();
                    return new VStream.Step.Skip<>(VStream.of(2));
                  });

      VStream<Integer> stream = () -> VTask.succeed(new VStream.Step.Emit<>(1, slowSkipTail));

      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      AtomicReference<Flow.Subscription> subRef = new AtomicReference<>();
      CountDownLatch allDone = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
              subRef.set(s);
              s.request(1);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
            }

            @Override
            public void onError(Throwable t) {
              allDone.countDown();
            }

            @Override
            public void onComplete() {
              allDone.countDown();
            }
          });

      // Wait for probe loop to start pulling the slow skip
      assertThat(probeSkipStarted.await(5, TimeUnit.SECONDS)).isTrue();
      // Add demand while probe is blocked
      subRef.get().request(10);
      // Let the slow skip complete — probe re-checks condition, sees demand>0, exits
      demandAdded.countDown();

      assertThat(allDone.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(1, 2);
    }

    @Test
    @DisplayName("toPublisher validates non-null subscriber")
    void toPublisherValidatesNonNullSubscriber() {
      VStream<Integer> stream = VStream.of(1);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      assertThatNullPointerException()
          .isThrownBy(() -> publisher.subscribe(null))
          .withMessageContaining("subscriber must not be null");
    }
  }

  @Nested
  @DisplayName("Drain Skip Steps")
  class DrainSkipStepTests {

    @Test
    @DisplayName("drain processes Skip steps from filtered stream")
    void drainProcessesSkipSteps() throws Exception {
      // filter() produces Skip steps, exercising the Skip case in drain's main while loop
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5).filter(n -> n % 2 == 0);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
              latch.countDown();
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(2, 4);
    }

    @Test
    @DisplayName("drain exits when cancelled during processing")
    void drainExitsWhenCancelled() throws Exception {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              this.subscription = subscription;
              subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
              if (item == 2) {
                subscription.cancel();
              }
            }

            @Override
            public void onError(Throwable throwable) {
              latch.countDown();
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }
          });

      // Give time for cancellation to take effect
      Thread.sleep(200);

      // Should have stopped processing after cancel
      assertThat(received.size()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("drain handles error in stream pull")
    void drainHandlesErrorInStreamPull() throws Exception {
      // Stream fails after first element
      VStream<Integer> stream =
          VStream.defer(
              () -> {
                VStream<Integer> tail = VStream.fail(new RuntimeException("pull error"));
                return () -> VTask.succeed(new VStream.Step.Emit<>(1, tail));
              });

      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      AtomicReference<Throwable> receivedError = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Integer item) {}

            @Override
            public void onError(Throwable throwable) {
              receivedError.set(throwable);
              latch.countDown();
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(receivedError.get()).isNotNull();
    }

    @Test
    @DisplayName("drain re-enters when demand arrives during draining")
    void drainReentersWhenDemandArrives() throws Exception {
      // This test exercises the finally block re-drain path:
      // request(1) -> drain starts -> emits 1 element, demand=0 -> enters probe loop
      // Meanwhile subscriber calls request(2) -> finally block sees demand>0, re-drains
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      CountDownLatch latch = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              this.subscription = subscription;
              subscription.request(1); // start with demand=1
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
              if (received.size() < 5) {
                subscription.request(1); // request more one at a time
              }
            }

            @Override
            public void onError(Throwable throwable) {
              latch.countDown();
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }
          });

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(1, 2, 3, 4, 5);
    }
  }

  @Nested
  @DisplayName("Probe Loop Emit Buffering")
  class ProbeEmitBufferingTests {

    @Test
    @DisplayName("probe buffers Emit when demand exhausted and delivers on later request")
    void probeBuffersEmitDeliversOnLaterRequest() throws Exception {
      // Stream with 3 elements. Request only 1. Don't request more from onNext.
      // After delivering element 1, demand=0. Probe loop pulls element 2 → Emit → buffer.
      // Then a delayed request delivers the rest.
      VStream<Integer> stream = VStream.of(1, 2, 3);
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      CountDownLatch firstReceived = new CountDownLatch(1);
      CountDownLatch allDone = new CountDownLatch(1);
      AtomicReference<Flow.Subscription> subRef = new AtomicReference<>();

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
              subRef.set(s);
              s.request(1); // Only request 1
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
              if (received.size() == 1) {
                firstReceived.countDown();
              }
              // Do NOT request more from here — let the probe buffer the next element
            }

            @Override
            public void onError(Throwable throwable) {
              allDone.countDown();
            }

            @Override
            public void onComplete() {
              allDone.countDown();
            }
          });

      // Wait for first element
      assertThat(firstReceived.await(5, TimeUnit.SECONDS)).isTrue();
      // Give time for probe to buffer element 2
      Thread.sleep(100);
      // Now request remaining elements
      subRef.get().request(Long.MAX_VALUE);

      assertThat(allDone.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("probe buffers Emit and finally block re-drains when demand arrives during probe")
    void probeBuffersEmitFinallyRedrains() throws Exception {
      // Use a slow-pulling stream so demand arrives DURING the probe pull.
      // This exercises the finally block's recursive drain() call (line 275).
      // A stream element whose pull() is slow, giving time for demand to arrive
      VStream<Integer> slowElement =
          () ->
              VTask.of(
                  () -> {
                    Thread.sleep(200);
                    return new VStream.Step.Emit<>(2, VStream.of(3));
                  });
      VStream<Integer> stream = VStream.of(1).concat(slowElement);

      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      List<Integer> received = new ArrayList<>();
      CountDownLatch firstReceived = new CountDownLatch(1);
      CountDownLatch allDone = new CountDownLatch(1);
      AtomicReference<Flow.Subscription> subRef = new AtomicReference<>();

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
              subRef.set(s);
              s.request(1);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
              if (received.size() == 1) {
                firstReceived.countDown();
              }
            }

            @Override
            public void onError(Throwable throwable) {
              allDone.countDown();
            }

            @Override
            public void onComplete() {
              allDone.countDown();
            }
          });

      // Wait for first element to be delivered
      assertThat(firstReceived.await(5, TimeUnit.SECONDS)).isTrue();
      // Request more while probe is blocked on the slow pull
      Thread.sleep(50);
      subRef.get().request(Long.MAX_VALUE);

      assertThat(allDone.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("InterruptedException Handlers")
  class InterruptedExceptionHandlerTests {

    @Test
    @DisplayName("onNext InterruptedException fails the run and cancels the subscription")
    void onNextInterruptedException() throws Exception {
      AtomicBoolean cancelCalled = new AtomicBoolean(false);
      CountDownLatch done = new CountDownLatch(1);

      Flow.Publisher<String> publisher =
          subscriber -> {
            Flow.Subscription sub =
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {}

                  @Override
                  public void cancel() {
                    cancelCalled.set(true);
                    done.countDown();
                  }
                };
            subscriber.onSubscribe(sub);
            Thread.startVirtualThread(
                () -> {
                  Thread.currentThread().interrupt();
                  subscriber.onNext("item");
                });
          };
      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 1);

      // A run subscribes at its first pull, which then waits for an element
      AtomicReference<Try<VStream.Step<String>>> outcome = new AtomicReference<>();
      Thread run = Thread.startVirtualThread(() -> outcome.set(stream.pull().runSafe()));

      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      boolean ended = run.join(Duration.ofSeconds(5));
      if (!ended) {
        run.interrupt();
      }
      assertThat(ended).isTrue();
      assertThat(cancelCalled).isTrue();
      assertThat(outcome.get())
          .isInstanceOfSatisfying(
              Try.Failure.class,
              failure -> assertThat(failure.cause()).isInstanceOf(InterruptedException.class));
    }

    @Test
    @DisplayName("onError InterruptedException restores interrupt")
    void onErrorInterruptedException() throws Exception {
      AtomicBoolean wasInterrupted = new AtomicBoolean(false);
      CountDownLatch done = new CountDownLatch(1);

      Flow.Publisher<String> publisher =
          subscriber -> {
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {}

                  @Override
                  public void cancel() {}
                });
            Thread.startVirtualThread(
                () -> {
                  Thread.currentThread().interrupt();
                  subscriber.onError(new RuntimeException("test error"));
                  wasInterrupted.set(Thread.currentThread().isInterrupted());
                  done.countDown();
                });
          };

      // A run subscribes at its first pull, which then waits for an element
      Thread run = startRun(VStreamReactive.fromPublisher(publisher, 1));

      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      run.interrupt();
      assertThat(wasInterrupted).isTrue();
    }

    @Test
    @DisplayName("onComplete InterruptedException restores interrupt")
    void onCompleteInterruptedException() throws Exception {
      AtomicBoolean wasInterrupted = new AtomicBoolean(false);
      CountDownLatch done = new CountDownLatch(1);

      Flow.Publisher<String> publisher =
          subscriber -> {
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {}

                  @Override
                  public void cancel() {}
                });
            Thread.startVirtualThread(
                () -> {
                  Thread.currentThread().interrupt();
                  subscriber.onComplete();
                  wasInterrupted.set(Thread.currentThread().isInterrupted());
                  done.countDown();
                });
          };

      // A run subscribes at its first pull, which then waits for an element
      Thread run = startRun(VStreamReactive.fromPublisher(publisher, 1));

      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      run.interrupt();
      assertThat(wasInterrupted).isTrue();
    }
  }

  @Nested
  @DisplayName("Error After Cancel")
  class ErrorAfterCancelTests {

    @Test
    @DisplayName("exception after cancel does not call onError")
    void exceptionAfterCancelDoesNotCallOnError() throws Exception {
      AtomicBoolean errorCalled = new AtomicBoolean(false);
      AtomicReference<Flow.Subscription> subRef = new AtomicReference<>();
      CountDownLatch pullStarted = new CountDownLatch(1);
      CountDownLatch cancelDone = new CountDownLatch(1);

      // Stream whose first pull signals the test, waits for cancel, then throws
      VStream<Integer> stream =
          () ->
              VTask.of(
                  () -> {
                    pullStarted.countDown();
                    assertThat(cancelDone.await(5, TimeUnit.SECONDS)).isTrue();
                    throw new RuntimeException("error after cancel");
                  });

      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
              subRef.set(s);
              s.request(1);
            }

            @Override
            public void onNext(Integer item) {}

            @Override
            public void onError(Throwable t) {
              errorCalled.set(true);
            }

            @Override
            public void onComplete() {}
          });

      // Wait for pull to start
      assertThat(pullStarted.await(5, TimeUnit.SECONDS)).isTrue();
      // Cancel the subscription
      subRef.get().cancel();
      // Let the pull proceed and throw
      cancelDone.countDown();

      Thread.sleep(200); // Give time for exception to be processed
      assertThat(errorCalled).isFalse();
    }
  }

  @Nested
  @DisplayName("A publisher's error fails the stream as the publisher gave it")
  class PublisherErrorTests {

    @Test
    @DisplayName("publisher error with Error subclass is thrown directly")
    void publisherErrorWithErrorIsThrown() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 16);

      Thread.startVirtualThread(
          () -> publisher.closeExceptionally(new AssertionError("test error")));

      assertThatThrownBy(() -> stream.toList().run())
          .isInstanceOf(AssertionError.class)
          .hasMessage("test error");
    }

    @Test
    @DisplayName("publisher error with checked exception arrives unwrapped")
    void publisherErrorWithCheckedExceptionIsNotWrapped() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 16);

      Thread.startVirtualThread(
          () -> publisher.closeExceptionally(new IOException("checked error")));

      Try<List<String>> result = stream.toList().runSafe();

      assertThat(result)
          .isInstanceOfSatisfying(
              Try.Failure.class,
              failure ->
                  assertThat(failure.cause())
                      .isInstanceOf(IOException.class)
                      .hasMessage("checked error"));
    }
  }

  @Nested
  @DisplayName("Null Validation")
  class NullValidationTests {

    @Test
    @DisplayName("toPublisher() validates non-null stream")
    @SuppressWarnings({
      "DataFlowIssue",
      "ResultOfMethodCallIgnored"
    }) // null verifies rejection; unused
    void toPublisherValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> VStreamReactive.toPublisher(null))
          .withMessageContaining("stream must not be null");
    }

    @Test
    @DisplayName("fromPublisher() validates non-null publisher")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void fromPublisherValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> VStreamReactive.fromPublisher(null, 16))
          .withMessageContaining("publisher must not be null");
    }

    @Test
    @DisplayName("fromPublisher() fails the stream at a null element, and throws one back")
    @SuppressWarnings("DataFlowIssue") // the publisher deliberately sends null
    void fromPublisherRefusesANullElement() {
      AtomicBoolean cancelled = new AtomicBoolean(false);
      AtomicReference<Throwable> thrownBack = new AtomicReference<>();
      Flow.Publisher<String> publisher =
          subscriber -> {
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {}

                  @Override
                  public void cancel() {
                    cancelled.set(true);
                  }
                });
            subscriber.onNext("a");
            try {
              subscriber.onNext(null);
            } catch (NullPointerException e) {
              thrownBack.set(e);
            }
          };

      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 4);

      // The run subscribes at its first pull, and reads "a" before the null
      assertThatNullPointerException()
          .isThrownBy(() -> stream.toList().run())
          .withMessage("VStream.fromPublisher element cannot be null");
      assertThat(thrownBack.get())
          .isInstanceOf(NullPointerException.class)
          .hasMessage("VStream.fromPublisher element cannot be null");
      assertThat(cancelled).isTrue();
    }

    @Test
    @DisplayName("fromPublisher() validates positive buffer size")
    void fromPublisherValidatesPositiveBufferSize() {
      try (SubmissionPublisher<String> publisher = new SubmissionPublisher<>()) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> VStreamReactive.fromPublisher(publisher, 0))
            .withMessageContaining("bufferSize must be positive");
      }
    }
  }

  // ==========================================================================
  // Audit Issue #3: VStreamSubscription.current visibility across virtual threads
  // ==========================================================================

  @Nested
  @DisplayName("Cross-thread Visibility Tests (audit issue #3)")
  class CrossThreadVisibilityTests {

    @Test
    @DisplayName("toPublisher delivers all elements correctly across thread boundary")
    void toPublisherDeliversAllElements() throws Exception {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3, 4, 5));
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      CopyOnWriteArrayList<Integer> received = new CopyOnWriteArrayList<>();
      CountDownLatch completed = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            Flow.Subscription sub;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              this.sub = subscription;
              subscription.request(5);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
              completed.countDown();
            }

            @Override
            public void onComplete() {
              completed.countDown();
            }
          });

      assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("toPublisher handles incremental requests correctly")
    void toPublisherHandlesIncrementalRequests() throws Exception {
      VStream<Integer> stream = VStream.fromList(List.of(10, 20, 30));
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(stream);

      CopyOnWriteArrayList<Integer> received = new CopyOnWriteArrayList<>();
      CountDownLatch completed = new CountDownLatch(1);

      publisher.subscribe(
          new Flow.Subscriber<>() {
            Flow.Subscription sub;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              this.sub = subscription;
              // Request one at a time
              subscription.request(1);
            }

            @Override
            public void onNext(Integer item) {
              received.add(item);
              // Request next after receiving current
              sub.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
              completed.countDown();
            }

            @Override
            public void onComplete() {
              completed.countDown();
            }
          });

      assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(received).containsExactly(10, 20, 30);
    }
  }
}
