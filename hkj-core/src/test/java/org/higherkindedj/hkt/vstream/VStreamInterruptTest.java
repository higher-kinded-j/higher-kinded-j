// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream.Step;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A loop that reads a stream checks the interrupt status before it starts and between pulls, so it
 * stops even when the pulls never block. The endless streams here interrupt their own thread as
 * they hand on a given element, so a test knows where the reading must stop. They give up long
 * after it, so a loop that missed the interrupt ends, and fails its test, rather than running for
 * ever.
 *
 * <p>Under the class's separate-thread timeout each test runs on a thread of its own, so an
 * interrupt status a test leaves set ends with that thread.
 */
@DisplayName("A loop that reads a stream stops at an interrupt, even when its pulls never block")
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class VStreamInterruptTest {

  /** The element whose step interrupts the thread, so the next pull is the one to stop. */
  private static final long INTERRUPTED_AT = 1_000;

  /** Where the stream ends if the reading misses the interrupt. */
  private static final long GIVING_UP_AT = 10_000_000;

  /** What a task failed with, as {@link VTask#runSafe()} keeps it. */
  private static Throwable failureOf(Try<?> result) {
    assertThat(result).isInstanceOf(Try.Failure.class);
    return ((Try.Failure<?>) result).cause();
  }

  /**
   * How an operation over an interrupted stream ended: its result, whether it left the interrupt
   * status set, how many elements the stream handed on, and how often its finaliser ran.
   */
  private record Outcome(Try<?> result, boolean interruptStatus, long read, int finalised) {}

  /**
   * Runs {@code operation} over an endless stream of 1s, whose pulls never block and which
   * interrupts the thread as it hands on its {@value #INTERRUPTED_AT}th element, then clears the
   * interrupt status.
   */
  private static Outcome readUntilInterrupted(Function<VStream<Integer>, VTask<?>> operation) {
    AtomicLong read = new AtomicLong();
    AtomicInteger finalised = new AtomicInteger();
    VStream<Integer> endless =
        VStream.repeat(1)
            .takeWhile(_ -> read.get() < GIVING_UP_AT)
            .peek(
                _ -> {
                  if (read.incrementAndGet() == INTERRUPTED_AT) {
                    Thread.currentThread().interrupt();
                  }
                })
            .onFinalize(VTask.exec(finalised::incrementAndGet));
    try {
      Try<?> result = operation.apply(endless).runSafe();
      return new Outcome(
          result, Thread.currentThread().isInterrupted(), read.get(), finalised.get());
    } finally {
      Thread.interrupted();
    }
  }

  /** Checks that an operation stopped at the pull after the interrupt and closed the stream. */
  private static void assertStoppedAtTheInterrupt(Outcome outcome) {
    assertThat(failureOf(outcome.result())).isInstanceOf(InterruptedException.class);
    assertThat(outcome.interruptStatus()).as("interrupt status left set").isTrue();
    assertThat(outcome.read()).as("elements handed on").isEqualTo(INTERRUPTED_AT);
    assertThat(outcome.finalised()).as("finaliser runs").isEqualTo(1);
  }

  private static Arguments operation(String name, Function<VStream<Integer>, VTask<?>> operation) {
    return arguments(named(name, operation));
  }

  @Nested
  @DisplayName("Each terminal operation")
  class TerminalOperations {

    static Stream<Arguments> terminalOperations() {
      return Stream.of(
          operation("drain()", VStream::drain),
          operation("toList()", VStream::toList),
          operation("fold", s -> s.fold(0, Integer::sum)),
          operation("foldLeft", s -> s.foldLeft(0L, (sum, n) -> sum + n)),
          operation("forEach", s -> s.forEach(_ -> {})),
          operation("count()", VStream::count),
          operation("exists, with no match", s -> s.exists(n -> n < 0)),
          operation("forAll, with every element matching", s -> s.forAll(n -> n > 0)),
          operation("find, with no match", s -> s.find(n -> n < 0)),
          operation("lastOption()", VStream::lastOption),
          operation("headOption(), with nothing emitted", s -> s.filter(n -> n < 0).headOption()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("terminalOperations")
    @DisplayName("fails with an InterruptedException at the next pull, and closes the stream")
    void stopsAtAnInterrupt(Function<VStream<Integer>, VTask<?>> operation) {
      assertStoppedAtTheInterrupt(readUntilInterrupted(operation));
    }

    @Test
    @DisplayName("on an endless stream, under a timeout, fails with the TimeoutException")
    void timeoutStopsAnEndlessStream() {
      AtomicInteger finalised = new AtomicInteger();
      VTask<?> drained =
          VStream.repeat(1).onFinalize(VTask.exec(finalised::incrementAndGet)).drain();

      Try<?> outcome = drained.timeout(Duration.ofMillis(50)).runSafe();

      assertThat(failureOf(outcome)).isInstanceOf(TimeoutException.class);
      // The timeout returns once the task has stopped, so the stream is already closed
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName(
        "started with the interrupt status set fails before reading, closes nothing, and keeps the"
            + " status")
    void startedInterrupted() {
      AtomicInteger finalised = new AtomicInteger();
      List<Integer> read = new ArrayList<>();
      VStream<Integer> stream =
          VStream.of(1, 2, 3).onFinalize(VTask.exec(finalised::incrementAndGet));

      Thread.currentThread().interrupt();
      try {
        assertThatThrownBy(() -> stream.forEach(read::add).execute())
            .isInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
      assertThat(read).isEmpty();
      // It read nothing, so it has nothing to close
      assertThat(finalised).hasValue(0);
    }
  }

  @Nested
  @DisplayName("An operator that reads ahead within one pull")
  class ReadingAhead {

    static Stream<Arguments> readingAhead() {
      return Stream.of(
          operation("chunk, filling a chunk", s -> s.chunk(Integer.MAX_VALUE).drain()),
          operation("chunk, past skipped elements", s -> s.filter(n -> n < 0).chunk(2).drain()),
          operation("chunkWhile, filling a chunk", s -> s.chunkWhile((_, _) -> true).drain()),
          operation(
              "chunkWhile, past skipped elements",
              s -> s.filter(n -> n < 0).chunkWhile((_, _) -> true).drain()),
          operation(
              "VStreamPar.parEvalMap, past skipped elements",
              s -> VStreamPar.parEvalMap(s.filter(n -> n < 0), 4, VTask::succeed).drain()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readingAhead")
    @DisplayName("stops at the pull after the interrupt, and closes the stream")
    void stopsAtAnInterrupt(Function<VStream<Integer>, VTask<?>> operation) {
      assertStoppedAtTheInterrupt(readUntilInterrupted(operation));
    }
  }

  @Nested
  @DisplayName("A step that hands on a stream not yet reached")
  class HandOvers {

    private static Arguments handOver(
        String name, Function<VStream<Integer>, VStream<Integer>> handingOn) {
      return arguments(named(name, handingOn));
    }

    /** Each shape interrupts its thread in the pull whose step hands on {@code unreached}. */
    static Stream<Arguments> handOvers() {
      return Stream.of(
          handOver(
              "concat, as its first stream ends",
              unreached ->
                  VStream.<Integer>empty()
                      .onComplete(() -> Thread.currentThread().interrupt())
                      .concat(unreached)),
          handOver(
              "interleave, as its first stream ends",
              unreached ->
                  VStream.<Integer>empty()
                      .onComplete(() -> Thread.currentThread().interrupt())
                      .interleave(unreached)),
          handOver(
              "flatMap, into the stream for an element",
              unreached ->
                  VStream.of(1)
                      .peek(_ -> Thread.currentThread().interrupt())
                      .flatMap(_ -> unreached)),
          handOver(
              "prepend, after its element",
              unreached -> unreached.prepend(0).peek(_ -> Thread.currentThread().interrupt())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("handOvers")
    @DisplayName("leaves it alone at a stop there, so a finaliser on it does not run")
    void leavesTheHandedOnStreamAlone(Function<VStream<Integer>, VStream<Integer>> handingOn) {
      AtomicInteger finalised = new AtomicInteger();
      // No run has pulled it, so closing it would run its finaliser
      VStream<Integer> unreached =
          VStream.of(1, 2, 3).onFinalize(VTask.exec(finalised::incrementAndGet));

      Try<?> outcome = handingOn.apply(unreached).toList().runSafe();
      Thread.interrupted();

      assertThat(failureOf(outcome)).isInstanceOf(InterruptedException.class);
      assertThat(finalised).as("the stream handed on is left as it is").hasValue(0);
    }

    @Test
    @DisplayName(
        "parEvalFlatMap closes a sub-stream its batch built at a stop there, as it closes those"
            + " it has not handed on")
    void parEvalFlatMapClosesWhatItsBatchBuilt() {
      List<String> finalised = new ArrayList<>();
      VStream<Integer> flattened =
          VStreamPar.parEvalFlatMap(
              VStream.of(1, 2),
              2,
              n -> VStream.of(n * 10).onFinalize(VTask.exec(() -> finalised.add("sub " + n))));
      // The first step hands on the stream built for 1, which nothing has read yet
      VStream<Integer> handedOn = ((Step.Skip<Integer>) flattened.pull().run()).tail();

      handedOn.close().run();

      assertThat(finalised).containsExactlyInAnyOrder("sub 1", "sub 2");
    }

    static Stream<Arguments> plainHandOvers() {
      return Stream.of(
          handOver("concat, as its first stream ends", s -> VStream.<Integer>empty().concat(s)),
          handOver(
              "interleave, as its first stream ends", s -> VStream.<Integer>empty().interleave(s)),
          handOver("flatMap, into the stream for an element", s -> VStream.of(1).flatMap(_ -> s)),
          handOver("prepend, after its element", s -> s.prepend(0)));
    }

    /** A stream whose only record of being closed is {@code log}, and whose pull fails. */
    private static VStream<Integer> failingToPull(List<String> log, boolean asItBuildsThePull) {
      return new VStream<>() {
        @Override
        public VTask<Step<Integer>> pull() {
          IllegalStateException down = new IllegalStateException("down");
          if (asItBuildsThePull) {
            throw down;
          }
          return VTask.fail(down);
        }

        @Override
        public VTask<Unit> close() {
          return VTask.exec(() -> log.add("closed"));
        }
      };
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("plainHandOvers")
    @DisplayName("closes the stream once if its first pull fails, as the reading would have")
    void closesTheStreamWhenItsFirstPullFails(
        Function<VStream<Integer>, VStream<Integer>> handingOn) {
      List<String> log = new ArrayList<>();

      Try<?> outcome = handingOn.apply(failingToPull(log, false)).toList().runSafe();

      assertThat(failureOf(outcome)).hasMessage("down");
      assertThat(log).containsExactly("closed");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("plainHandOvers")
    @DisplayName("closes the stream once if building its first pull fails")
    void closesTheStreamWhenBuildingItsFirstPullFails(
        Function<VStream<Integer>, VStream<Integer>> handingOn) {
      List<String> log = new ArrayList<>();

      Try<?> outcome = handingOn.apply(failingToPull(log, true)).toList().runSafe();

      assertThat(failureOf(outcome)).hasMessage("down");
      assertThat(log).containsExactly("closed");
    }
  }

  @Nested
  @DisplayName("An error operator lets an interrupt through")
  class ErrorOperators {

    /** An interrupt as a stream's task fails with it: bare, or as {@link VTask#run()} wraps it. */
    enum Form {
      BARE,
      WRAPPED;

      Throwable interrupt() {
        InterruptedException stop = new InterruptedException("stop");
        return this == BARE ? stop : new VTaskExecutionException(stop);
      }
    }

    /** An error operator over a stream, whose function calls {@code called} when it runs. */
    @FunctionalInterface
    interface ErrorOperator {
      VStream<Integer> over(VStream<Integer> stream, Runnable called);
    }

    private static Named<ErrorOperator> errorOperator(String name, ErrorOperator operator) {
      return named(name, operator);
    }

    static List<Named<ErrorOperator>> errorOperators() {
      return List.of(
          errorOperator(
              "recover",
              (s, called) ->
                  s.recover(
                      _ -> {
                        called.run();
                        return -1;
                      })),
          errorOperator(
              "recoverWith",
              (s, called) ->
                  s.recoverWith(
                      _ -> {
                        called.run();
                        return VStream.of(-1);
                      })),
          errorOperator(
              "mapError",
              (s, called) ->
                  s.mapError(
                      error -> {
                        called.run();
                        return new IllegalStateException(error);
                      })),
          errorOperator("onError", (s, called) -> s.onError(_ -> called.run())));
    }

    static Stream<Arguments> operatorsAndForms() {
      return errorOperators().stream()
          .flatMap(operator -> Stream.of(Form.values()).map(form -> arguments(operator, form)));
    }

    /** Elements 1, 2 and 3, through a task that fails with {@code interrupt} at element 2. */
    private static VStream<Integer> interruptedAtTwo(Throwable interrupt, AtomicInteger finalised) {
      // defer keeps nothing it builds, so only a close of the rest reaches the finaliser
      return VStream.defer(
              () -> VStream.of(1, 2, 3).onFinalize(VTask.exec(finalised::incrementAndGet)))
          .mapTask(n -> n == 2 ? VTask.fail(interrupt) : VTask.succeed(n));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("operatorsAndForms")
    @DisplayName(
        "the run fails with the interrupt unchanged, and the operator's function never runs")
    void passesTheInterruptThrough(ErrorOperator operator, Form form) {
      AtomicInteger finalised = new AtomicInteger();
      AtomicInteger called = new AtomicInteger();
      Throwable interrupt = form.interrupt();

      Try<?> outcome =
          operator
              .over(interruptedAtTwo(interrupt, finalised), called::incrementAndGet)
              .toList()
              .runSafe();

      assertThat(failureOf(outcome)).isSameAs(interrupt);
      assertThat(called).as("the operator's function runs").hasValue(0);
      assertThat(finalised).as("the source's finaliser runs").hasValue(1);
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("operatorsAndForms")
    @DisplayName("leaves the interrupt status as it would be without the operator")
    void leavesTheStatusAlone(ErrorOperator operator, Form form) {
      VStream<Integer> source = interruptedAtTwo(form.interrupt(), new AtomicInteger());

      failureOf(source.toList().runSafe());
      boolean withoutOperator = Thread.interrupted();
      failureOf(operator.over(source, () -> {}).toList().runSafe());
      boolean withOperator = Thread.interrupted();

      assertThat(withOperator).isEqualTo(withoutOperator);
    }

    private static Arguments placement(String name, UnaryOperator<VStream<Integer>> placingAround) {
      return arguments(named(name, placingAround));
    }

    static Stream<Arguments> placements() {
      return Stream.of(
          placement("inside flatMap", s -> VStream.of(0).flatMap(_ -> s)),
          placement("second in concat", s -> VStream.of(0).concat(s)),
          placement("on the right of zipWith", s -> VStream.of(0, 0, 0).zipWith(s, Integer::sum)),
          placement("under chunk", s -> s.chunk(2).map(NonEmptyList::head)),
          placement("under parEvalMap", s -> VStreamPar.parEvalMap(s, 2, VTask::succeed)),
          placement("as a merge source", s -> VStreamPar.merge(s, VStream.empty())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placements")
    @DisplayName("the operator holding a recover closes the source once, as after any failed pull")
    void holderCloses(UnaryOperator<VStream<Integer>> placement) {
      AtomicInteger finalised = new AtomicInteger();
      AtomicInteger recovered = new AtomicInteger();
      Throwable interrupt = Form.BARE.interrupt();
      VStream<Integer> recovering =
          interruptedAtTwo(interrupt, finalised)
              .recover(
                  _ -> {
                    recovered.incrementAndGet();
                    return -1;
                  });

      Try<?> outcome = placement.apply(recovering).toList().runSafe();

      assertThat(failureOf(outcome)).isSameAs(interrupt);
      assertThat(recovered).hasValue(0);
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("mapError, onError and recover act on a failed element, which is not an interrupt")
    void elementFailuresHandled() {
      List<Throwable> seen = new ArrayList<>();
      VStream<Integer> failedAtTwo =
          VStream.of(1, 2, 3)
              .mapTask(n -> n == 2 ? VTask.fail(new IOException("down")) : VTask.succeed(n));

      List<Integer> read =
          failedAtTwo
              .mapError(IllegalStateException::new)
              .onError(seen::add)
              .recover(_ -> -1)
              .toList()
              .run();

      assertThat(read).containsExactly(1, -1, 3);
      assertThat(seen).singleElement().isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a recover outside what stops the run gives a value, and one inside it does not")
    void recoverOutsideTheStop() {
      List<Integer> outside =
          VStream.repeat(1).toList().timeout(Duration.ofMillis(50)).recover(_ -> List.of()).run();
      Try<List<Integer>> inside =
          VStream.repeat(1)
              .toList()
              .recover(_ -> List.of(-1))
              .timeout(Duration.ofMillis(50))
              .runSafe();

      assertThat(outside).isEmpty();
      assertThat(failureOf(inside)).isInstanceOf(TimeoutException.class);
    }
  }
}
