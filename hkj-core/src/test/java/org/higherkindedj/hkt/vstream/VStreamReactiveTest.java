// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              this.subscription = subscription;
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
  class FromPublisherTests {

    @Test
    @DisplayName("all publisher elements available in VStream")
    void allPublisherElementsAvailable() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 16);

      // Publish elements in background
      Thread.startVirtualThread(
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

      Thread.startVirtualThread(publisher::close);

      List<Integer> result = stream.toList().run();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("error maps to failed VTask")
    void errorMapsToFailedVTask() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 16);

      Thread.startVirtualThread(
          () -> publisher.closeExceptionally(new RuntimeException("pub error")));

      assertThatThrownBy(() -> stream.toList().run()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("buffer respects configured size")
    void bufferRespectsConfiguredSize() {
      SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>();

      // Small buffer
      VStream<Integer> stream = VStreamReactive.fromPublisher(publisher, 2);

      Thread.startVirtualThread(
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

      Thread.startVirtualThread(
          () -> {
            publisher.submit("a");
            publisher.close();
          });

      List<String> result = stream.toList().run();

      assertThat(result).containsExactly("a");
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
          new VStream<>() {
            @Override
            public VTask<Step<Integer>> pull() {
              return VTask.of(
                  () -> {
                    Thread.sleep(200);
                    return new Step.Emit<>(2, VStream.of(3));
                  });
            }
          };
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
    @DisplayName("onNext InterruptedException cancels subscription")
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

      VStreamReactive.fromPublisher(publisher, 1);

      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(cancelCalled).isTrue();
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

      VStreamReactive.fromPublisher(publisher, 1);

      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
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

      VStreamReactive.fromPublisher(publisher, 1);

      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
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
          new VStream<>() {
            @Override
            public VTask<Step<Integer>> pull() {
              return VTask.of(
                  () -> {
                    pullStarted.countDown();
                    assertThat(cancelDone.await(5, TimeUnit.SECONDS)).isTrue();
                    throw new RuntimeException("error after cancel");
                  });
            }
          };

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
  @DisplayName("wrapIfChecked Coverage")
  class WrapIfCheckedTests {

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
    @DisplayName("publisher error with checked exception is wrapped in RuntimeException")
    void publisherErrorWithCheckedExceptionIsWrapped() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

      VStream<String> stream = VStreamReactive.fromPublisher(publisher, 16);

      Thread.startVirtualThread(
          () -> publisher.closeExceptionally(new java.io.IOException("checked error")));

      assertThatThrownBy(() -> stream.toList().run())
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(java.io.IOException.class);
    }
  }

  @Nested
  @DisplayName("Null Validation")
  class NullValidationTests {

    @Test
    @DisplayName("toPublisher() validates non-null stream")
    void toPublisherValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> VStreamReactive.toPublisher(null))
          .withMessageContaining("stream must not be null");
    }

    @Test
    @DisplayName("fromPublisher() validates non-null publisher")
    void fromPublisherValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> VStreamReactive.fromPublisher(null, 16))
          .withMessageContaining("publisher must not be null");
    }

    @Test
    @DisplayName("fromPublisher() validates positive buffer size")
    void fromPublisherValidatesPositiveBufferSize() {
      SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

      assertThatIllegalArgumentException()
          .isThrownBy(() -> VStreamReactive.fromPublisher(publisher, 0))
          .withMessageContaining("bufferSize must be positive");
    }
  }
}
