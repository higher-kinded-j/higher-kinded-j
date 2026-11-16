// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.awaitility.core.ConditionFactory;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream.Step;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Several runs may read one stream at once, each starting at a pull of its head, so what a run
 * opens is reached only through the tails that run holds. Here one run reads a shared stream
 * part-way while another stops without reading it, which closes the head, and the first run reads
 * on as if the second had never run.
 *
 * <p>Under the class's separate-thread timeout, a run left waiting for a producer that another run
 * stopped fails its test rather than hanging the build.
 */
@DisplayName("A stream several runs read at once: closing its head ends no run")
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class VStreamSharedHeadTest {

  /** A stream over 1, 2 and 3, each of whose runs counts in {@code released} as it is closed. */
  @FunctionalInterface
  private interface Shared {
    VStream<Integer> over(AtomicInteger released);
  }

  /** A run that stops without reading the shared stream. */
  @FunctionalInterface
  private interface Stopping {
    Try<List<Integer>> run(VStream<Integer> shared);
  }

  private static VTask<Unit> counting(AtomicInteger released) {
    return VTask.exec(released::incrementAndGet);
  }

  private static Named<Shared> bracket() {
    return Named.of(
        "bracket",
        released ->
            VStream.bracket(
                VTask.succeed("resource"), _ -> VStream.of(1, 2, 3), _ -> counting(released)));
  }

  private static Named<Shared> parEvalMapUnordered() {
    // One at a time, so the window reads the source to its end only as the run takes its results
    return Named.of(
        "parEvalMapUnordered",
        released ->
            VStreamPar.parEvalMapUnordered(
                VStream.of(1, 2, 3).onFinalize(counting(released)), 1, VTask::succeed));
  }

  private static Named<Shared> onFinalize() {
    return Named.of("onFinalize", released -> VStream.of(1, 2, 3).onFinalize(counting(released)));
  }

  /** The shared streams whose runs keep what they opened until the run ends or is closed. */
  private static Stream<Named<Shared>> holdingUntilClosed() {
    return Stream.of(bracket(), parEvalMapUnordered(), onFinalize());
  }

  /**
   * Every shared stream. A merge's producer and a publisher read ahead of the run, so their sources
   * may end before the run does, and what shows that another run stopped them is the run's own
   * elements. The merge's other source is empty, so the run's first element comes from the source
   * with the finaliser, and the run has pulled that source when the other run stops.
   */
  private static Stream<Named<Shared>> allShared() {
    return Stream.concat(
        holdingUntilClosed(),
        Stream.of(
            Named.of(
                "merge",
                released ->
                    VStreamPar.merge(
                        VStream.of(1, 2, 3).onFinalize(counting(released)), VStream.empty())),
            Named.of(
                "fromPublisher",
                released ->
                    VStreamReactive.fromPublisher(
                        VStreamReactive.toPublisher(
                            VStream.of(1, 2, 3).onFinalize(counting(released))),
                        1))));
  }

  private static Stream<Named<Stopping>> stoppingRuns() {
    return Stream.of(
        Named.of("take(0)", shared -> shared.take(0).toList().runSafe()),
        Named.of(
            "close() on the stream itself",
            shared -> shared.close().map(_ -> List.<Integer>of()).runSafe()),
        Named.of(
            "zipWith, whose first stream fails before it reads the shared one",
            shared ->
                VStream.<Integer>fail(new IllegalStateException("first failed"))
                    .zipWith(shared, Integer::sum)
                    .toList()
                    .runSafe()));
  }

  private static Stream<Arguments> combined(Stream<Named<Shared>> shapes) {
    List<Named<Stopping>> stopping = stoppingRuns().toList();
    return shapes.flatMap(shape -> stopping.stream().map(stop -> arguments(shape, stop)));
  }

  /** The first element a run gives, as a hand puller reads it. */
  private static Step.Emit<Integer> firstElement(VStream<Integer> stream) {
    Step<Integer> step = stream.pull().run();
    while (step instanceof Step.Skip<Integer> skip) {
      step = skip.tail().pull().run();
    }
    if (step instanceof Step.Emit<Integer> emit) {
      return emit;
    }
    throw new AssertionError("expected an element, got " + step);
  }

  /** An await that checks at once, then often, so a condition already met costs nothing. */
  private static ConditionFactory promptly() {
    return await()
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(1))
        .atMost(Duration.ofSeconds(5));
  }

  /** Reads a run on from its first element, failing rather than waiting for ever. */
  private static List<Integer> readOn(Step.Emit<Integer> first) {
    List<Integer> read = new ArrayList<>(List.of(first.value()));
    read.addAll(first.tail().toList().timeout(Duration.ofSeconds(5)).run());
    return read;
  }

  @Nested
  @DisplayName("Another run that stops without reading the shared stream")
  class AnotherRunStops {

    static Stream<Arguments> everyShapeAndStop() {
      return combined(allShared());
    }

    static Stream<Arguments> holdingShapesAndStops() {
      return combined(holdingUntilClosed());
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("everyShapeAndStop")
    @DisplayName("leaves a run part-way through it to read on to its end")
    void theRunReadsOn(Shared shape, Stopping stopping) {
      AtomicInteger released = new AtomicInteger();
      VStream<Integer> shared = shape.over(released);
      Step.Emit<Integer> first = firstElement(shared);

      stopping.run(shared);

      assertThat(readOn(first)).containsExactlyInAnyOrder(1, 2, 3);
      // A publisher's source ends on the thread that drains it
      promptly().untilAsserted(() -> assertThat(released).hasValue(1));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("holdingShapesAndStops")
    @DisplayName("leaves open what a run part-way through it opened")
    void theRunKeepsWhatItOpened(Shared shape, Stopping stopping) {
      AtomicInteger released = new AtomicInteger();
      VStream<Integer> shared = shape.over(released);
      Step.Emit<Integer> first = firstElement(shared);

      stopping.run(shared);
      assertThat(released).as("released while the run reads on").hasValue(0);

      first.tail().close().run();
      assertThat(released).hasValue(1);
    }

    @Test
    @DisplayName("take(0) gives nothing, and opens nothing of its own")
    void takeZeroGivesNothing() {
      AtomicInteger acquired = new AtomicInteger();
      VStream<Integer> shared =
          VStream.bracket(
              VTask.of(acquired::incrementAndGet),
              _ -> VStream.of(1, 2, 3),
              _ -> VTask.succeed(Unit.INSTANCE));
      Step.Emit<Integer> first = firstElement(shared);

      assertThat(shared.take(0).toList().run()).isEmpty();

      assertThat(acquired).hasValue(1);
      first.tail().close().run();
    }
  }

  @Nested
  @DisplayName("Another run whose first pull fails")
  class AnotherRunFails {

    /**
     * A stream whose first run fails at its first pull, once {@code otherRunStarted} lets it, and
     * whose later runs give 1, 2 and 3.
     */
    private static VStream<Integer> failingFirstRun(
        CountDownLatch firstRunPulling, CountDownLatch otherRunStarted) {
      AtomicInteger runs = new AtomicInteger();
      return new VStream<>() {
        @Override
        public VTask<Step<Integer>> pull() {
          return () -> {
            if (runs.incrementAndGet() == 1) {
              firstRunPulling.countDown();
              otherRunStarted.await();
              throw new IllegalStateException("first run failed");
            }
            return VStream.of(1, 2, 3).pull().execute();
          };
        }
      };
    }

    /** A stream over {@code source}, each of whose runs counts in {@code released} as it ends. */
    @FunctionalInterface
    private interface Holding {
      VStream<Integer> around(VStream<Integer> source, AtomicInteger released);
    }

    static Stream<Named<Holding>> holders() {
      return Stream.of(
          Named.of(
              "bracket",
              (source, released) ->
                  VStream.bracket(VTask.succeed("resource"), _ -> source, _ -> counting(released))),
          Named.of("onFinalize", (source, released) -> source.onFinalize(counting(released))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("holders")
    @DisplayName("releases only what it opened, though another run started during its first pull")
    void releasesOnlyItsOwn(Holding holding) throws InterruptedException {
      CountDownLatch firstRunPulling = new CountDownLatch(1);
      CountDownLatch otherRunStarted = new CountDownLatch(1);
      AtomicInteger released = new AtomicInteger();
      VStream<Integer> shared =
          holding.around(failingFirstRun(firstRunPulling, otherRunStarted), released);
      AtomicReference<Try<List<Integer>>> failed = new AtomicReference<>();
      Thread failing = Thread.ofVirtual().start(() -> failed.set(shared.toList().runSafe()));
      assertThat(firstRunPulling.await(5, TimeUnit.SECONDS)).isTrue();

      // A second run starts while the first is still in its first pull
      Step.Emit<Integer> first = firstElement(shared);
      otherRunStarted.countDown();
      assertThat(failing.join(Duration.ofSeconds(5))).isTrue();

      assertThat(failed.get().isFailure()).isTrue();
      assertThat(released).as("the failed run's own release").hasValue(1);
      assertThat(readOn(first)).containsExactly(1, 2, 3);
      assertThat(released).hasValue(2);
    }
  }

  @Nested
  @DisplayName("A run whose first pull fails closes what it opened itself")
  class FailedFirstPull {

    /** A stream whose first element never comes, which counts {@code closed} down as it closes. */
    private static VStream<Integer> quiet(CountDownLatch closed) {
      return VStream.<Integer>repeat(1)
          .mapTask(
              x ->
                  VTask.of(
                      () -> {
                        new CountDownLatch(1).await();
                        return x;
                      }))
          .onFinalize(VTask.exec(closed::countDown));
    }

    @Test
    @DisplayName("merge, interrupted as it waits for a first element, stops its producer")
    void mergeInterruptedStopsItsProducer() throws InterruptedException {
      CountDownLatch quietClosed = new CountDownLatch(2);
      VStream<Integer> merged = VStreamPar.merge(quiet(quietClosed), quiet(quietClosed));
      AtomicReference<Try<Step<Integer>>> outcome = new AtomicReference<>();
      Thread pulling = Thread.ofVirtual().start(() -> outcome.set(merged.pull().runSafe()));
      promptly().until(() -> pulling.getState() == Thread.State.WAITING);

      pulling.interrupt();
      assertThat(pulling.join(Duration.ofSeconds(5))).isTrue();

      assertThat(outcome.get().isFailure()).isTrue();
      assertThat(quietClosed.await(5, TimeUnit.SECONDS)).isTrue();
    }

    /** A source whose first pull fails, which counts in {@code closes} each time it is closed. */
    private static VStream<Integer> failingSource(AtomicInteger closes) {
      return new VStream<>() {
        @Override
        public VTask<Step<Integer>> pull() {
          return VTask.fail(new IllegalStateException("source failed"));
        }

        @Override
        public VTask<Unit> close() {
          return VTask.exec(closes::incrementAndGet);
        }
      };
    }

    static Stream<Named<UnaryOperator<VStream<Integer>>>> readingTheSource() {
      return Stream.of(
          // The other source never gives a step, so the first pull fails with the source
          Named.of("merge", source -> VStreamPar.merge(source, quiet(new CountDownLatch(1)))),
          Named.of(
              "parEvalMapUnordered",
              source -> VStreamPar.parEvalMapUnordered(source, 1, VTask::succeed)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readingTheSource")
    @DisplayName("closes its source once, and closing the stream after closes it no more")
    void closesItsSourceOnce(UnaryOperator<VStream<Integer>> reading) {
      AtomicInteger closes = new AtomicInteger();
      VStream<Integer> stream = reading.apply(failingSource(closes));

      // toList closes the stream itself after its first pull fails
      assertThat(stream.toList().runSafe().isFailure()).isTrue();

      assertThat(closes).hasValue(1);
    }
  }
}
