// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("VStream reports a failure at any element, as its task threw it")
class VStreamErrorReportingTest {

  /** Elements 1, 2 and 3, each multiplied by 10, except {@code bad}, whose task fails. */
  private static VStream<Integer> failsAt(int bad, Throwable failure) {
    return VStream.of(1, 2, 3).mapTask(n -> n == bad ? VTask.fail(failure) : VTask.succeed(n * 10));
  }

  private static VStream<Integer> failsAt(int bad) {
    return failsAt(bad, new IllegalStateException("line " + bad));
  }

  private static VStream<Integer> checkedFailureAt(int bad) {
    return failsAt(bad, new IOException("feed down"));
  }

  /** What a task failed with, as {@link VTask#runSafe()} keeps it. */
  private static Throwable failureOf(VTask<?> task) {
    Try<?> result = task.runSafe();
    assertThat(result).isInstanceOf(Try.Failure.class);
    return ((Try.Failure<?>) result).cause();
  }

  @Nested
  @DisplayName("recoverWith, mapError and onError act on every pull")
  class ErrorOperatorsActOnEveryPull {

    @Test
    @DisplayName("recoverWith replaces the rest when a later element fails")
    void recoverWithAtALaterElement() {
      assertThat(failsAt(2).recoverWith(_ -> VStream.of(-1)).toList().run())
          .containsExactly(10, -1);
      assertThat(failsAt(3).recoverWith(_ -> VStream.of(-1)).toList().run())
          .containsExactly(10, 20, -1);
    }

    @Test
    @DisplayName("recoverWith still recovers after a skipped element")
    void recoverWithAfterASkip() {
      VStream<Integer> stream = failsAt(3).filter(n -> n != 10).recoverWith(_ -> VStream.of(-1));

      assertThat(stream.toList().run()).containsExactly(20, -1);
    }

    @Test
    @DisplayName("recoverWith does not recover a failure of the recovery stream")
    void recoverWithDoesNotRecoverItsRecovery() {
      IllegalStateException fallbackDown = new IllegalStateException("fallback down");
      List<Throwable> handled = new ArrayList<>();

      // The recovery emits before it fails, so only a recovery stream left unwrapped stops there
      VStream<Integer> stream =
          failsAt(2)
              .recoverWith(
                  e -> {
                    handled.add(e);
                    return VStream.of(-1).concat(VStream.fail(fallbackDown));
                  });

      assertThat(failureOf(stream.toList())).isSameAs(fallbackDown);
      assertThat(handled).hasSize(1);
    }

    @Test
    @DisplayName("recoverWith refuses a null recovery stream")
    void recoverWithRefusesNull() {
      assertThat(failureOf(failsAt(2).recoverWith(_ -> null).toList()))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("recoveryFunction must not return null");
    }

    @Test
    @DisplayName("mapError maps a failure at a later element, after a skip too")
    void mapErrorAtALaterElement() {
      Function<Throwable, Throwable> toDomain =
          e -> new UnsupportedOperationException("mapped " + e.getMessage());

      assertThat(failureOf(failsAt(2).mapError(toDomain).toList()))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessage("mapped line 2");
      assertThat(failureOf(failsAt(3).filter(n -> n != 10).mapError(toDomain).toList()))
          .hasMessage("mapped line 3");
    }

    @Test
    @DisplayName("mapError keeps the rest a mapTask failure carries, so recover still resumes")
    void mapErrorKeepsTheRest() {
      List<Throwable> recovered = new ArrayList<>();

      VStream<Integer> stream =
          failsAt(2)
              .mapError(e -> new UnsupportedOperationException("mapped", e))
              .recover(
                  e -> {
                    recovered.add(e);
                    return -1;
                  });

      assertThat(stream.toList().run()).containsExactly(10, -1, 30);
      assertThat(recovered).singleElement().isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("mapError that gives back the same failure keeps its rest")
    void mapErrorGivingTheSameFailure() {
      assertThat(failsAt(2).mapError(e -> e).recover(_ -> -1).toList().run())
          .containsExactly(10, -1, 30);
    }

    @Test
    @DisplayName("mapError to an error carrying an older rest resumes from this failure's rest")
    void mapErrorOverAnOlderRest() {
      IllegalStateException alreadyCarrying = new IllegalStateException("carries an older rest");
      alreadyCarrying.addSuppressed(new StreamTailMarker(VStream.of(99)));

      assertThat(failsAt(2).mapError(_ -> alreadyCarrying).recover(_ -> -1).toList().run())
          .containsExactly(10, -1, 30);
    }

    @Test
    @DisplayName("mapError to one shared error resumes from each failure in turn")
    void mapErrorToASharedError() {
      IllegalStateException shared = new IllegalStateException("odd line");

      VStream<Integer> stream =
          VStream.of(1, 2, 3, 4)
              .mapTask(n -> n % 2 == 1 ? VTask.fail(new IOException("odd " + n)) : VTask.succeed(n))
              .mapError(_ -> shared)
              .recover(_ -> -1);

      // take bounds the stream, should a stale rest make it repeat itself
      assertThat(stream.take(8).toList().run()).containsExactly(-1, 2, -1, 4);
    }

    @Test
    @DisplayName("mapTask failing with one shared error resumes from each failure in turn")
    void mapTaskWithASharedError() {
      IllegalStateException shared = new IllegalStateException("even line");

      VStream<Integer> stream =
          VStream.of(1, 2, 3, 4, 5)
              .mapTask(n -> n % 2 == 0 ? VTask.fail(shared) : VTask.succeed(n))
              .recover(_ -> -1);

      assertThat(stream.take(10).toList().run()).containsExactly(1, -1, 3, -1, 5);
    }

    @Test
    @DisplayName("mapError and onError stay on the rest a recover resumes from")
    void mapErrorAndOnErrorStayOnTheResumedRest() {
      List<String> mapped = new ArrayList<>();
      List<String> observed = new ArrayList<>();
      VStream<Integer> oddsFail =
          VStream.of(1, 2, 3, 4)
              .mapTask(
                  n -> n % 2 == 1 ? VTask.fail(new IOException("odd " + n)) : VTask.succeed(n));

      List<Integer> afterMapError =
          oddsFail
              .mapError(e -> new IllegalStateException("mapped " + e.getMessage()))
              .recover(
                  e -> {
                    mapped.add(e.getMessage());
                    return -1;
                  })
              .toList()
              .run();
      List<Integer> afterOnError =
          oddsFail.onError(e -> observed.add(e.getMessage())).recover(_ -> -1).toList().run();

      assertThat(afterMapError).containsExactly(-1, 2, -1, 4);
      assertThat(mapped).containsExactly("mapped odd 1", "mapped odd 3");
      assertThat(afterOnError).containsExactly(-1, 2, -1, 4);
      assertThat(observed).containsExactly("odd 1", "odd 3");
    }

    @Test
    @DisplayName("mapError refuses a null error")
    void mapErrorRefusesNull() {
      assertThat(failureOf(failsAt(2).mapError(_ -> null).toList()))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("f must not return null");
    }

    @Test
    @DisplayName("onError observes a failure at a later element, after a skip too")
    void onErrorAtALaterElement() {
      List<String> seen = new ArrayList<>();

      assertThat(failureOf(failsAt(2).onError(e -> seen.add(e.getMessage())).toList()))
          .hasMessage("line 2");
      assertThat(
              failureOf(
                  failsAt(3).filter(n -> n != 10).onError(e -> seen.add(e.getMessage())).toList()))
          .hasMessage("line 3");
      assertThat(seen).containsExactly("line 2", "line 3");
    }
  }

  static Stream<Arguments> terminalOperations() {
    return Stream.of(
        terminal("toList", VStream::toList),
        terminal("fold", s -> s.fold(0, Integer::sum)),
        terminal("foldLeft", s -> s.foldLeft("", (acc, n) -> acc + n)),
        terminal("count", VStream::count),
        terminal("headOption", VStream::headOption),
        terminal("lastOption", VStream::lastOption),
        terminal("exists", s -> s.exists(n -> n > 100)),
        terminal("forAll", s -> s.forAll(n -> n < 100)),
        terminal("find", s -> s.find(n -> n > 100)),
        terminal("forEach", s -> s.forEach(_ -> {})),
        terminal("drain", VStream::drain),
        terminal("chunk", s -> s.chunk(2).toList()),
        terminal("chunkWhile", s -> s.chunkWhile((a, b) -> true).toList()),
        terminal("throttle", s -> VStreamThrottle.throttle(s, 10, Duration.ofSeconds(1)).toList()),
        terminal("metered", s -> VStreamThrottle.metered(s, Duration.ofMillis(1)).toList()),
        terminal("parEvalMap source", s -> VStreamPar.parEvalMap(s, 2, VTask::succeed).toList()),
        terminal("parCollect", s -> VStreamPar.parCollect(s, 2)),
        terminal(
            "parEvalFlatMap source",
            s -> VStreamPar.parEvalFlatMap(s, 2, n -> VStream.of(n)).toList()),
        terminal(
            "bracket use",
            s ->
                VStream.bracket(VTask.succeed("session"), _ -> s, _ -> VTask.succeed(Unit.INSTANCE))
                    .toList()),
        terminal("merge", s -> VStreamPar.merge(List.of(s, VStream.of(7, 8))).toList()),
        terminal(
            "ap",
            s ->
                VSTREAM
                    .narrow(
                        VStreamApplicative.INSTANCE.ap(
                            VSTREAM.widen(VStream.<Function<Integer, Integer>>of(n -> n + 1)),
                            VSTREAM.widen(s)))
                    .toList()));
  }

  private static Arguments terminal(String name, Function<VStream<Integer>, VTask<?>> op) {
    return arguments(named(name, op));
  }

  @Nested
  @DisplayName("A checked failure arrives as the task threw it")
  class CheckedFailures {

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.hkt.vstream.VStreamErrorReportingTest#terminalOperations")
    @DisplayName("each terminal operation fails with the task's IOException")
    void terminalOperationsReportTheTaskException(Function<VStream<Integer>, VTask<?>> op) {
      assertThat(failureOf(op.apply(checkedFailureAt(1))))
          .isInstanceOf(IOException.class)
          .hasMessage("feed down");
    }

    @Test
    @DisplayName("chunkWhile fails with the task's IOException at a later element")
    void chunkWhileAtALaterElement() {
      assertThat(failureOf(checkedFailureAt(2).chunkWhile((a, b) -> true).toList()))
          .isInstanceOf(IOException.class)
          .hasMessage("feed down");
    }

    @Test
    @DisplayName("run() wraps it only on the way out, in a VTaskExecutionException")
    void runWrapsItOnTheWayOut() {
      assertThatThrownBy(() -> checkedFailureAt(2).toList().run())
          .isInstanceOf(VTaskExecutionException.class)
          .cause()
          .isInstanceOf(IOException.class)
          .hasMessage("feed down");
    }

    @Test
    @DisplayName("a recovery after chunk sees the task's IOException")
    void recoveryAfterChunkSeesTheTaskException() {
      List<Throwable> recovered = new ArrayList<>();

      VStream<Integer> stream =
          checkedFailureAt(2)
              .chunk(1)
              .map(chunk -> chunk.head())
              .recoverWith(
                  e -> {
                    recovered.add(e);
                    return VStream.of(-1);
                  });

      assertThat(stream.toList().run()).containsExactly(10, -1);
      assertThat(recovered).singleElement().isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("parEvalMap and parEvalMapUnordered fail with a task's IOException")
    void parallelTasksReportTheTaskException() {
      Function<Integer, VTask<Integer>> courier =
          n -> n == 2 ? VTask.fail(new IOException("courier down")) : VTask.succeed(n);

      assertThat(failureOf(VStreamPar.parEvalMap(VStream.of(1, 2, 3), 2, courier).toList()))
          .isInstanceOf(IOException.class)
          .hasMessage("courier down");
      assertThat(
              failureOf(VStreamPar.parEvalMapUnordered(VStream.of(1, 2, 3), 2, courier).toList()))
          .isInstanceOf(IOException.class)
          .hasMessage("courier down");
    }

    @Test
    @DisplayName("a publisher's subscriber receives the task's IOException")
    void publisherReportsTheTaskException() throws Exception {
      CompletableFuture<Throwable> received = new CompletableFuture<>();
      VStreamReactive.toPublisher(checkedFailureAt(1))
          .subscribe(
              new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                  subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(Integer item) {}

                @Override
                public void onError(Throwable throwable) {
                  received.complete(throwable);
                }

                @Override
                public void onComplete() {
                  received.complete(null);
                }
              });

      assertThat(received.get(5, TimeUnit.SECONDS))
          .isInstanceOf(IOException.class)
          .hasMessage("feed down");
    }
  }

  @Nested
  @DisplayName("An interrupted pull")
  class InterruptedPulls {

    @Test
    @DisplayName("fails the terminal operation with the InterruptedException, interrupt status set")
    void interruptedPull() {
      InterruptedException interrupted = new InterruptedException("pull interrupted");
      VStream<Integer> stream = () -> VTask.fail(interrupted);

      try {
        // execute(), unlike runSafe(), does not set the status, so only the pull's handling can
        assertThatThrownBy(() -> stream.toList().execute()).isSameAs(interrupted);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName("of a merged source fails the merge, as any source failure does")
    void interruptedMergeSource() {
      InterruptedException interrupted = new InterruptedException("source pull interrupted");
      VStream<Integer> source = () -> VTask.fail(interrupted);

      try {
        assertThat(failureOf(VStreamPar.merge(List.of(source, VStream.of(1, 2))).toList()))
            .isSameAs(interrupted);
      } finally {
        Thread.interrupted();
      }
    }
  }

  @Test
  @DisplayName("recover resumes from a rest wrapped by a stream that pulled with run()")
  void recoverResumesThroughAWrapper() {
    VStream<Integer> source = checkedFailureAt(1);
    VStream<Integer> pulledWithRun = () -> () -> source.pull().run();

    assertThat(pulledWithRun.recover(_ -> -1).toList().run()).containsExactly(-1, 20, 30);
  }
}
