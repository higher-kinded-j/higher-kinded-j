// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.awaitility.core.ConditionFactory;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("The tails of fromPublisher, parEvalMapUnordered and merge follow one interrupt rule")
// A wait that never ends fails the test rather than hanging the build
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TakeOnceTailTest {

  private static final Duration WAIT = Duration.ofSeconds(5);

  /** A wait that looks at once, and then often, so a test spends no fixed time waiting. */
  private static ConditionFactory soon() {
    return await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(1)).atMost(WAIT);
  }

  /** A stream whose runs read what a test puts on a queue, through one of the three tails. */
  private record Fed(String name, Function<BlockingQueue<Integer>, VStream<Integer>> over) {

    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Fed> tails() {
    return Stream.of(
        new Fed("fromPublisher", feed -> VStreamReactive.fromPublisher(publishing(feed))),
        new Fed(
            "parEvalMapUnordered",
            feed -> VStreamPar.parEvalMapUnordered(fed(feed), 1, VTask::succeed)),
        new Fed("merge", feed -> VStreamPar.merge(fed(feed), idle())));
  }

  /** An endless stream of what is put on the queue, each pull waiting for the next. */
  private static VStream<Integer> fed(BlockingQueue<Integer> feed) {
    return () -> VTask.of(() -> new VStream.Step.Emit<>(feed.take(), fed(feed)));
  }

  /** A stream whose first pull waits until it is interrupted. */
  private static VStream<Integer> idle() {
    return () ->
        VTask.of(
            () -> {
              new CountDownLatch(1).await();
              return new VStream.Step.Done<>();
            });
  }

  /**
   * A publisher that sends what is put on the queue, from a thread of its own, until it is
   * cancelled. A test sends fewer elements than the stream first asks for, so it ignores demand.
   */
  private static Flow.Publisher<Integer> publishing(BlockingQueue<Integer> feed) {
    return subscriber -> {
      AtomicReference<@Nullable Thread> sender = new AtomicReference<>();
      subscriber.onSubscribe(
          new Flow.Subscription() {
            @Override
            public void request(long n) {}

            @Override
            public void cancel() {
              Thread sending = sender.get();
              if (sending != null) {
                sending.interrupt();
              }
            }
          });
      sender.set(
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      while (true) {
                        subscriber.onNext(feed.take());
                      }
                    } catch (InterruptedException _) {
                      // Cancelled
                    }
                  }));
    };
  }

  /**
   * A publisher of 1 to {@code last} that sends the next element for each one asked for, on the
   * thread that asks, as a synchronous publisher does.
   */
  private static Flow.Publisher<Integer> sendingWhenAsked(int last) {
    return subscriber -> {
      AtomicInteger next = new AtomicInteger(1);
      subscriber.onSubscribe(
          new Flow.Subscription() {
            @Override
            public void request(long n) {
              for (long i = 0; i < n && next.get() <= last; i++) {
                subscriber.onNext(next.getAndIncrement());
              }
            }

            @Override
            public void cancel() {}
          });
    };
  }

  private static VStream<Integer> tailOf(VStream.Step<Integer> step) {
    return ((VStream.Step.Emit<Integer>) step).tail();
  }

  private static Integer emitted(VStream.Step<Integer> step) {
    return ((VStream.Step.Emit<Integer>) step).value();
  }

  /** What a task gave on an interrupted thread, and whether the thread was still interrupted. */
  private record Interrupted<T>(T result, boolean statusSet) {}

  private static <T> Interrupted<T> onAnInterruptedThread(VTask<T> task) throws Throwable {
    Thread.currentThread().interrupt();
    try {
      T result = task.execute();
      return new Interrupted<>(result, Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  /** As {@link #onAnInterruptedThread}, or null when the task stops at the interrupt. */
  private static <T> @Nullable Interrupted<T> unlessStopped(VTask<T> task) {
    try {
      return onAnInterruptedThread(task);
    } catch (InterruptedException _) {
      return null;
    } catch (Throwable t) {
      throw new AssertionError("the pull failed", t);
    }
  }

  /** How a pull that an interrupt stopped ended: what it threw, and whether the status was set. */
  private record Stopped(@Nullable Throwable failure, boolean statusSet) {}

  /**
   * Pulls the tail on a thread of its own, interrupts the pull once it waits, and says how it
   * ended.
   */
  private static Stopped interruptedPull(VStream<Integer> tail) throws InterruptedException {
    AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
    AtomicBoolean statusSet = new AtomicBoolean();
    Thread waiting =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    tail.pull().execute();
                  } catch (Throwable t) {
                    failure.set(t);
                    statusSet.set(Thread.currentThread().isInterrupted());
                  }
                });
    soon().until(() -> waiting.getState() == Thread.State.WAITING);
    waiting.interrupt();
    waiting.join(WAIT);
    return new Stopped(failure.get(), statusSet.get());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("tails")
  @DisplayName("a tail that keeps its step gives it again on an interrupted thread")
  void keptStepOnAnInterruptedThread(Fed fed) throws Throwable {
    VStream<Integer> rest =
        tailOf(fed.over().apply(new LinkedBlockingQueue<>(List.of(1, 2))).pull().run());
    VStream.Step<Integer> kept = rest.pull().run();

    Interrupted<VStream.Step<Integer>> again = onAnInterruptedThread(rest.pull());
    rest.close().run();

    assertThat(again.result()).isSameAs(kept);
    assertThat(again.statusSet()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("tails")
  @DisplayName("a tail whose signal is already there gives its step on an interrupted thread")
  void signalAlreadyThereOnAnInterruptedThread(Fed fed) {
    VStream<Integer> rest =
        tailOf(fed.over().apply(new LinkedBlockingQueue<>(List.of(1, 2))).pull().run());

    // The second element reaches the run in its own time; until then, a pull stops at the
    // interrupt, keeping nothing
    Interrupted<VStream.Step<Integer>> pulled =
        soon().until(() -> unlessStopped(rest.pull()), Objects::nonNull);
    rest.close().run();

    assertThat(emitted(pulled.result())).isEqualTo(2);
    assertThat(pulled.statusSet()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("tails")
  @DisplayName(
      "an interrupted wait for the signal throws with the status set, and keeps nothing, so the"
          + " tail waits again")
  void interruptedWaitForTheSignal(Fed fed) throws InterruptedException {
    BlockingQueue<Integer> feed = new LinkedBlockingQueue<>(List.of(1));
    VStream<Integer> rest = tailOf(fed.over().apply(feed).pull().run());

    Stopped stopped = interruptedPull(rest);
    feed.add(2);
    VStream.Step<Integer> next = rest.pull().run();
    rest.close().run();

    assertThat(stopped.failure()).isInstanceOf(InterruptedException.class);
    assertThat(stopped.statusSet()).isTrue();
    assertThat(emitted(next)).isEqualTo(2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("tails")
  @DisplayName(
      "a pull waiting for another pull of the same tail stops at an interrupt, with the status set")
  void interruptedWaitForAnotherPull(Fed fed) throws InterruptedException {
    BlockingQueue<Integer> feed = new LinkedBlockingQueue<>(List.of(1));
    VStream<Integer> rest = tailOf(fed.over().apply(feed).pull().run());
    AtomicReference<VStream.Step<Integer>> read = new AtomicReference<>();

    Thread reading = Thread.ofVirtual().start(() -> read.set(rest.pull().run()));
    soon().until(() -> reading.getState() == Thread.State.WAITING);
    Stopped stopped = interruptedPull(rest);
    feed.add(2);
    reading.join(WAIT);
    VStream.Step<Integer> again = rest.pull().run();
    rest.close().run();

    assertThat(stopped.failure()).isInstanceOf(InterruptedException.class);
    assertThat(stopped.statusSet()).isTrue();
    assertThat(emitted(read.get())).isEqualTo(2);
    assertThat(again).isSameAs(read.get());
  }

  @Test
  @DisplayName(
      "an element a publisher sends on an interrupted pulling thread, as it asks for one more, is"
          + " there for the next pull")
  void elementSentOnAnInterruptedPullingThread() throws Throwable {
    // A buffer of one, so the second element is buffered and the third is sent as it is taken
    VStream<Integer> rest =
        tailOf(VStreamReactive.fromPublisher(sendingWhenAsked(3), 1).pull().run());

    Interrupted<VStream.Step<Integer>> second = onAnInterruptedThread(rest.pull());
    VStream.Step<Integer> third = tailOf(second.result()).pull().run();

    assertThat(emitted(second.result())).isEqualTo(2);
    assertThat(second.statusSet()).isTrue();
    assertThat(emitted(third)).isEqualTo(3);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("tails")
  @DisplayName("a tail first pulled after its run is closed gives Done on an interrupted thread")
  void closedRunOnAnInterruptedThread(Fed fed) throws Throwable {
    VStream<Integer> rest =
        tailOf(fed.over().apply(new LinkedBlockingQueue<>(List.of(1))).pull().run());
    rest.close().run();

    Interrupted<VStream.Step<Integer>> pulled = onAnInterruptedThread(rest.pull());

    assertThat(pulled.result()).isInstanceOf(VStream.Step.Done.class);
    assertThat(pulled.statusSet()).isTrue();
  }
}
