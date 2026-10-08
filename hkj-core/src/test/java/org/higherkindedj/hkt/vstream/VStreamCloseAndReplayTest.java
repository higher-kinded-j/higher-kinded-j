// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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

@DisplayName("VStream close() forwarding and replay")
class VStreamCloseAndReplayTest {

  /** A four-element stream that counts how often its finaliser runs. */
  private static VStream<Integer> finalised(AtomicInteger runs) {
    return VStream.of(1, 2, 3, 4).onFinalize(VTask.exec(runs::incrementAndGet));
  }

  /** An endless stream that counts how often its finaliser runs. */
  private static VStream<Integer> endless(AtomicInteger runs) {
    return VStream.repeat(1).onFinalize(VTask.exec(runs::incrementAndGet));
  }

  /** A four-element stream over a resource whose release is counted. */
  private static VStream<Integer> bracketed(AtomicInteger released) {
    return VStream.bracket(
        VTask.succeed("resource"),
        _ -> VStream.of(1, 2, 3, 4),
        _ -> VTask.exec(released::incrementAndGet));
  }

  /** A deferred stream over {@link #finalised}, which keeps nothing it builds. */
  private static VStream<Integer> deferredFinalised(AtomicInteger runs) {
    return VStream.defer(() -> finalised(runs));
  }

  /** A function that throws, for an operator to apply to the first element. */
  private static <T> T boom() {
    throw new IllegalStateException("function failed");
  }

  private static VTask<Unit> failing(String message) {
    return VTask.of(
        () -> {
          throw new IllegalStateException(message);
        });
  }

  /** The source with a map that fails on its first element. */
  private static VStream<Integer> failingAtFirst(VStream<Integer> source) {
    return source.map(
        x -> {
          if (x == 1) {
            throw new IllegalStateException("map failed");
          }
          return x;
        });
  }

  /** The source with a map that fails on its second element. */
  private static VStream<Integer> failingAtSecond(VStream<Integer> source) {
    return source.map(
        x -> {
          if (x == 2) {
            throw new IllegalStateException("map failed");
          }
          return x;
        });
  }

  /** The source, with a pull that fails at the given element. */
  private static VStream<Integer> pullFailingAt(int element, VStream<Integer> source) {
    return source.mapTask(
        x ->
            x == element ? VTask.fail(new IllegalStateException("pull failed")) : VTask.succeed(x));
  }

  /** A two-element stream whose finaliser fails with a checked exception. */
  private static VStream<Integer> checkedFinaliser() {
    return VStream.of(1, 2)
        .onFinalize(
            VTask.of(
                () -> {
                  throw new IOException("finaliser failed");
                }));
  }

  /** An endless stream of its positions, each of which records its position when closed. */
  private static VStream<Integer> recordingCloses(int position, List<Integer> closed) {
    return new VStream<>() {
      @Override
      public VTask<VStream.Step<Integer>> pull() {
        return VTask.succeed(
            new VStream.Step.Emit<>(position, recordingCloses(position + 1, closed)));
      }

      @Override
      public VTask<Unit> close() {
        return VTask.exec(() -> closed.add(position));
      }
    };
  }

  /** A subscriber that requests on subscribing and hands each signal to the given actions. */
  private static <A> Flow.Subscriber<A> subscriber(
      long request, Consumer<Flow.Subscription> onNext, Consumer<Throwable> onError) {
    return new Flow.Subscriber<>() {
      private Flow.Subscription subscription;

      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        if (request > 0) {
          subscription.request(request);
        } else {
          subscription.cancel();
        }
      }

      @Override
      public void onNext(A item) {
        onNext.accept(subscription);
      }

      @Override
      public void onError(Throwable throwable) {
        onError.accept(throwable);
      }

      @Override
      public void onComplete() {}
    };
  }

  /** A publisher of 1, 2 and 3 that records whether its subscription was cancelled. */
  private static Flow.Publisher<Integer> recordingPublisher(AtomicBoolean cancelled) {
    return subscriber ->
        subscriber.onSubscribe(
            new Flow.Subscription() {
              private int next = 1;
              private boolean completed;

              @Override
              public void request(long n) {
                for (long i = 0; i < n && next <= 3; i++) {
                  subscriber.onNext(next++);
                }
                if (next > 3 && !completed) {
                  completed = true;
                  subscriber.onComplete();
                }
              }

              @Override
              public void cancel() {
                cancelled.set(true);
              }
            });
  }

  private static <A> A emitted(VStream.Step<A> step) {
    if (step instanceof VStream.Step.Emit<A> emit) {
      return emit.value();
    }
    throw new AssertionError("expected an Emit, got " + step);
  }

  private static <A> VStream<A> tailOf(VStream.Step<A> step) {
    if (step instanceof VStream.Step.Emit<A> emit) {
      return emit.tail();
    }
    throw new AssertionError("expected an Emit, got " + step);
  }

  /** A stream with nothing in it, which records its name when closed. */
  private static VStream<Integer> closedAs(String name, List<String> closed) {
    return new VStream<>() {
      @Override
      public VTask<VStream.Step<Integer>> pull() {
        return VTask.succeed(new VStream.Step.Done<>());
      }

      @Override
      public VTask<Unit> close() {
        return VTask.exec(() -> closed.add(name));
      }
    };
  }

  static Stream<Arguments> stepsPulledWhileCancelling() {
    return Stream.of(
        arguments(
            named(
                "Emit",
                (Function<VStream<Integer>, VStream.Step<Integer>>)
                    rest -> new VStream.Step.Emit<>(1, rest)),
            "rest"),
        arguments(
            named(
                "Skip",
                (Function<VStream<Integer>, VStream.Step<Integer>>)
                    rest -> new VStream.Step.Skip<>(rest)),
            "rest"),
        arguments(
            named(
                "Done",
                (Function<VStream<Integer>, VStream.Step<Integer>>)
                    rest -> new VStream.Step.Done<>()),
            "head"));
  }

  static Stream<Arguments> operators() {
    return Stream.of(
        operator("map", s -> s.map(x -> x + 1)),
        operator("map, then map", s -> s.map(x -> x + 1).map(x -> x * 2)),
        operator("mapTask", s -> s.mapTask(VTask::succeed)),
        operator("flatMap", s -> s.flatMap(VStream::of)),
        operator("via", s -> s.via(VStream::of)),
        operator("filter", s -> s.filter(x -> true)),
        operator("takeWhile", s -> s.takeWhile(x -> true)),
        operator("dropWhile", s -> s.dropWhile(x -> false)),
        operator("drop", s -> s.drop(1)),
        operator("distinct", VStream::distinct),
        operator("chunk", s -> s.chunk(2)),
        operator("chunkWhile", s -> s.chunkWhile((a, b) -> false)),
        operator("mapChunked", s -> s.mapChunked(2, chunk -> chunk)),
        operator("concat", s -> s.concat(VStream.of(9))),
        operator("append", s -> s.append(9)),
        operator("zipWith", s -> s.zipWith(VStream.of(1, 2, 3, 4), Integer::sum)),
        operator("interleave", s -> s.interleave(VStream.of(5, 6, 7, 8))),
        operator("peek", s -> s.peek(x -> {})),
        operator("onComplete", s -> s.onComplete(() -> {})),
        operator("recover", s -> s.recover(e -> 0)),
        operator("recoverWith", s -> s.recoverWith(e -> VStream.empty())),
        operator("mapError", s -> s.mapError(e -> e)),
        operator("onError", s -> s.onError(e -> {})),
        operator("asUnit", VStream::asUnit),
        operator("throttle", s -> VStreamThrottle.throttle(s, 10, Duration.ofMinutes(1))),
        operator("metered", s -> VStreamThrottle.metered(s, Duration.ZERO)),
        operator("parEvalMap", s -> VStreamPar.parEvalMap(s, 2, VTask::succeed)),
        operator("parEvalMapUnordered", s -> VStreamPar.parEvalMapUnordered(s, 2, VTask::succeed)),
        operator("parEvalFlatMap", s -> VStreamPar.parEvalFlatMap(s, 2, VStream::of)),
        operator("merge", s -> VStreamPar.merge(s, VStream.empty())));
  }

  private static Arguments operator(String name, Function<VStream<Integer>, VStream<?>> op) {
    return arguments(named(name, op));
  }

  static Stream<Arguments> terminals() {
    return Stream.of(
        terminal("toList", VStream::toList),
        terminal("headOption", VStream::headOption),
        terminal("lastOption", VStream::lastOption),
        terminal("count", VStream::count),
        terminal("exists", s -> s.exists(x -> x > 9)),
        terminal("forAll", s -> s.forAll(x -> x < 9)),
        terminal("find", s -> s.find(x -> x > 9)),
        terminal("drain", VStream::drain));
  }

  private static Arguments terminal(String name, Function<VStream<Integer>, VTask<?>> op) {
    return arguments(named(name, op));
  }

  static Stream<Arguments> userFunctions() {
    return Stream.of(
        operator("map", s -> s.map(x -> boom())),
        operator("filter", s -> s.filter(x -> boom())),
        operator("takeWhile", s -> s.takeWhile(x -> boom())),
        operator("dropWhile", s -> s.dropWhile(x -> boom())),
        operator("peek", s -> s.peek(x -> boom())),
        operator("flatMap", s -> s.flatMap(x -> boom())),
        operator("zipWith", s -> s.zipWith(VStream.of(1, 2, 3, 4), (a, b) -> boom())),
        operator("chunkWhile", s -> s.chunkWhile((a, b) -> boom())));
  }

  static Stream<Arguments> checkedFailurePaths() {
    return Stream.of(
        terminal("toList", VStream::toList),
        terminal("headOption", VStream::headOption),
        terminal("take(1).toList", s -> s.take(1).toList()),
        terminal("find", s -> s.find(x -> x == 1)),
        terminal("close", VStream::close));
  }

  @Nested
  @DisplayName("Closing reaches an upstream finaliser")
  class CloseReachesUpstream {

    @ParameterizedTest(name = "take(1) after {0}")
    @MethodSource("org.higherkindedj.hkt.vstream.VStreamCloseAndReplayTest#operators")
    @DisplayName("take(1) after an operator runs the upstream finaliser once")
    void takeAfterOperatorRunsFinaliser(Function<VStream<Integer>, VStream<?>> op) {
      AtomicInteger runs = new AtomicInteger();

      op.apply(finalised(runs)).take(1).toList().run();

      assertThat(runs).hasValue(1);
    }

    @ParameterizedTest(name = "headOption after {0}")
    @MethodSource("org.higherkindedj.hkt.vstream.VStreamCloseAndReplayTest#operators")
    @DisplayName("headOption after an operator runs the upstream finaliser once")
    void headOptionAfterOperatorRunsFinaliser(Function<VStream<Integer>, VStream<?>> op) {
      AtomicInteger runs = new AtomicInteger();

      op.apply(finalised(runs)).headOption().run();

      assertThat(runs).hasValue(1);
    }

    @ParameterizedTest(name = "close() before pulling {0}")
    @MethodSource("org.higherkindedj.hkt.vstream.VStreamCloseAndReplayTest#operators")
    @DisplayName("close() on an operator before any pull runs the upstream finaliser once")
    void closeBeforePullRunsFinaliser(Function<VStream<Integer>, VStream<?>> op) {
      AtomicInteger runs = new AtomicInteger();

      op.apply(finalised(runs)).close().run();

      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("prepend reaches the upstream finaliser once its own element is read")
    void prependReachesUpstreamAfterItsElement() {
      AtomicInteger taken = new AtomicInteger();
      AtomicInteger head = new AtomicInteger();

      finalised(taken).prepend(0).drop(1).take(1).toList().run();
      finalised(head).prepend(0).headOption().run();

      assertThat(taken).hasValue(1);
      assertThat(head).hasValue(1);
    }

    @Test
    @DisplayName("the second stream of zipWith and interleave is closed too")
    void secondStreamIsClosed() {
      AtomicInteger zipped = new AtomicInteger();
      AtomicInteger interleaved = new AtomicInteger();

      VStream.of(1, 2, 3, 4).zipWith(finalised(zipped), Integer::sum).take(1).toList().run();
      VStream.of(5, 6, 7, 8).interleave(finalised(interleaved)).take(3).toList().run();

      assertThat(zipped).hasValue(1);
      assertThat(interleaved).hasValue(1);
    }

    @Test
    @DisplayName("concat leaves a second stream it has not reached, and closes it once reached")
    void concatClosesOnlyWhatItReads() {
      AtomicInteger unreached = new AtomicInteger();
      AtomicInteger reached = new AtomicInteger();

      VStream.of(0).concat(finalised(unreached)).take(1).toList().run();
      VStream.of(0).concat(finalised(reached)).take(2).toList().run();

      assertThat(unreached).hasValue(0);
      assertThat(reached).hasValue(1);
    }

    @Test
    @DisplayName("closing runs the innermost finaliser first, as completing does")
    void closeOrderMatchesCompletion() {
      List<String> completed = new ArrayList<>();
      List<String> closed = new ArrayList<>();

      nested(completed).toList().run();
      nested(closed).headOption().run();

      assertThat(completed).containsExactly("inner", "outer");
      assertThat(closed).containsExactly("inner", "outer");
    }

    @Test
    @DisplayName("a failure between two finalised layers releases the inner layer first")
    void failureBetweenLayersReleasesInnerFirst() {
      List<String> order = new ArrayList<>();
      VStream<Integer> stream =
          failingAtFirst(VStream.of(1, 2).onFinalize(VTask.exec(() -> order.add("inner"))))
              .onFinalize(VTask.exec(() -> order.add("outer")));

      assertThatThrownBy(() -> stream.toList().run()).hasMessage("map failed");
      assertThat(order).containsExactly("inner", "outer");
    }

    private static VStream<Integer> nested(List<String> order) {
      return VStream.of(1, 2)
          .onFinalize(VTask.exec(() -> order.add("inner")))
          .map(x -> x)
          .onFinalize(VTask.exec(() -> order.add("outer")));
    }
  }

  @Nested
  @DisplayName("Operations that stop early close what they leave")
  class EarlyStopsClose {

    @Test
    @DisplayName("takeWhile closes the upstream when its predicate first fails")
    void takeWhileClosesUpstream() {
      AtomicInteger runs = new AtomicInteger();

      List<Integer> taken = finalised(runs).takeWhile(x -> x < 2).toList().run();

      assertThat(taken).containsExactly(1);
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("zipWith closes the longer stream when the shorter one ends")
    void zipWithClosesLongerStream() {
      AtomicInteger longerRight = new AtomicInteger();
      AtomicInteger longerLeft = new AtomicInteger();

      VStream.of(1).zipWith(finalised(longerRight), Integer::sum).toList().run();
      finalised(longerLeft).zipWith(VStream.of(1), Integer::sum).toList().run();

      assertThat(longerRight).hasValue(1);
      assertThat(longerLeft).hasValue(1);
    }

    @Test
    @DisplayName("zipWith and interleave leave a stream they have not read from")
    void unreadSideIsLeft() {
      AtomicInteger zipped = new AtomicInteger();
      AtomicInteger interleaved = new AtomicInteger();

      VStream.<Integer>empty().zipWith(finalised(zipped), Integer::sum).toList().run();
      VStream.of(9).interleave(finalised(interleaved)).headOption().run();

      assertThat(zipped).hasValue(0);
      assertThat(interleaved).hasValue(0);
    }

    @Test
    @DisplayName(
        "zipWith closes only the first stream when it has skipped before reading the second")
    void zipWithClosesOnlyTheSideItRead() {
      AtomicInteger left = new AtomicInteger();
      AtomicInteger right = new AtomicInteger();
      VStream<Integer> zipped =
          finalised(left).filter(x -> x > 1).zipWith(finalised(right), Integer::sum);

      VStream.Step<Integer> step = zipped.pull().run();
      assertThat(step).isInstanceOf(VStream.Step.Skip.class);
      ((VStream.Step.Skip<Integer>) step).tail().close().run();

      assertThat(left).hasValue(1);
      assertThat(right).hasValue(0);
    }

    @Test
    @DisplayName("find, exists and forAll close the stream when they stop early")
    void shortCircuitingTerminalsClose() {
      AtomicInteger found = new AtomicInteger();
      AtomicInteger existed = new AtomicInteger();
      AtomicInteger counterExample = new AtomicInteger();

      Optional<Integer> match = finalised(found).find(x -> x == 2).run();
      boolean any = finalised(existed).exists(x -> x == 2).run();
      boolean all = finalised(counterExample).forAll(x -> x < 2).run();

      assertThat(match).contains(2);
      assertThat(any).isTrue();
      assertThat(all).isFalse();
      assertThat(found).hasValue(1);
      assertThat(existed).hasValue(1);
      assertThat(counterExample).hasValue(1);
    }

    @Test
    @DisplayName("a toPublisher subscription closes the stream when it is cancelled")
    void reactiveCancelCloses() throws InterruptedException {
      CountDownLatch finalised = new CountDownLatch(1);
      VStream<Integer> stream = VStream.repeat(1).onFinalize(VTask.exec(finalised::countDown));

      VStreamReactive.toPublisher(stream)
          .subscribe(
              new Flow.Subscriber<Integer>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                  this.subscription = subscription;
                  subscription.request(1);
                }

                @Override
                public void onNext(Integer item) {
                  subscription.cancel();
                }

                @Override
                public void onError(Throwable throwable) {}

                @Override
                public void onComplete() {}
              });

      assertThat(finalised.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("a short-circuit terminal whose close fails closes the rest once")
    void shortCircuitCloseFailureClosesOnce() {
      AtomicInteger closes = new AtomicInteger();
      VStream<Integer> rest =
          new VStream<>() {
            @Override
            public VTask<VStream.Step<Integer>> pull() {
              return VTask.succeed(new VStream.Step.Done<>());
            }

            @Override
            public VTask<Unit> close() {
              return VTask.of(
                  () -> {
                    closes.incrementAndGet();
                    throw new IllegalStateException("close failed");
                  });
            }
          };
      VStream<Integer> stream = () -> VTask.succeed(new VStream.Step.Emit<>(1, rest));

      assertThatThrownBy(() -> stream.headOption().run()).hasMessage("close failed");
      assertThat(closes).hasValue(1);
    }
  }

  @Nested
  @DisplayName("A failure while consuming closes the stream")
  class FailureCloses {

    @Test
    @DisplayName(
        "a failing map function still runs the upstream finaliser, and the failure is kept")
    void failingMapRunsFinaliser() {
      AtomicInteger runs = new AtomicInteger();
      VStream<Integer> stream =
          finalised(runs)
              .map(
                  x -> {
                    if (x == 2) {
                      throw new IllegalStateException("map failed");
                    }
                    return x;
                  });

      assertThatThrownBy(() -> stream.toList().run()).hasMessage("map failed");
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("a failing forEach action or foldLeft function still runs the finaliser")
    void failingTerminalFunctionRunsFinaliser() {
      AtomicInteger forEachRuns = new AtomicInteger();
      AtomicInteger foldRuns = new AtomicInteger();

      assertThatThrownBy(
              () ->
                  finalised(forEachRuns)
                      .forEach(
                          x -> {
                            throw new IllegalStateException("action failed");
                          })
                      .run())
          .hasMessage("action failed");
      assertThatThrownBy(
              () ->
                  finalised(foldRuns)
                      .foldLeft(
                          0,
                          (acc, x) -> {
                            throw new IllegalStateException("fold failed");
                          })
                      .run())
          .hasMessage("fold failed");

      assertThat(forEachRuns).hasValue(1);
      assertThat(foldRuns).hasValue(1);
    }

    @Test
    @DisplayName("an interrupted wait in metered closes the rest of the stream")
    void interruptedMeteredWaitCloses() throws InterruptedException {
      AtomicInteger runs = new AtomicInteger();
      VStream<Integer> metered =
          VStreamThrottle.metered(deferredFinalised(runs), Duration.ofMinutes(1));

      Thread consumer = Thread.ofVirtual().start(() -> metered.toList().runSafe());
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> consumer.getState() == Thread.State.TIMED_WAITING);
      consumer.interrupt();
      consumer.join(Duration.ofSeconds(10));

      assertThat(consumer.isAlive()).isFalse();
      assertThat(runs).hasValue(1);
    }
  }

  @Nested
  @DisplayName("Each terminal operation closes the stream when a pull fails")
  class TerminalFailureCloses {

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.hkt.vstream.VStreamCloseAndReplayTest#terminals")
    @DisplayName("the upstream finaliser runs once, and the failure is kept")
    void terminalRunsFinaliserOnFailure(Function<VStream<Integer>, VTask<?>> op) {
      AtomicInteger runs = new AtomicInteger();

      VTask<?> task = op.apply(failingAtFirst(finalised(runs)));

      assertThatThrownBy(task::run).hasMessage("map failed");
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("a finaliser failure while closing after a failure is suppressed onto it")
    void closeFailureIsSuppressedOntoTheFirst() {
      VStream<Integer> stream = failingAtFirst(VStream.of(1, 2).onFinalize(failing("fin")));

      assertThatThrownBy(() -> stream.toList().run())
          .hasMessage("map failed")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .extracting(Throwable::getMessage)
                      .containsExactly("fin"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.hkt.vstream.VStreamCloseAndReplayTest#checkedFailurePaths")
    @DisplayName("a checked failure from a finaliser arrives wrapped, whichever path runs it")
    void checkedFinaliserFailureIsWrapped(Function<VStream<Integer>, VTask<?>> op) {
      // runSafe keeps a failure as it was thrown, so it shows whether the path wrapped it
      Try<?> result = op.apply(checkedFinaliser()).runSafe();

      assertThat(result)
          .isInstanceOfSatisfying(
              Try.Failure.class,
              failure ->
                  assertThat(failure.cause())
                      .isInstanceOf(VTaskExecutionException.class)
                      .hasCauseInstanceOf(IOException.class));
    }
  }

  @Nested
  @DisplayName("Closing several streams closes each")
  class ClosingSeveralStreams {

    @Test
    @DisplayName("an inner finaliser runs when the outer one throws")
    void innerRunsWhenOuterThrows() {
      AtomicInteger inner = new AtomicInteger();
      VStream<Integer> stream =
          VStream.of(1, 2)
              .onFinalize(VTask.exec(inner::incrementAndGet))
              .onFinalize(failing("outer"));

      assertThatThrownBy(() -> stream.close().run()).hasMessage("outer");
      assertThat(inner).hasValue(1);
    }

    @Test
    @DisplayName(
        "an outer finaliser runs when the inner one throws, and the inner failure is thrown")
    void outerRunsWhenInnerThrows() {
      AtomicInteger outer = new AtomicInteger();
      VStream<Integer> stream =
          VStream.of(1, 2)
              .onFinalize(failing("inner"))
              .onFinalize(VTask.exec(outer::incrementAndGet));

      assertThatThrownBy(() -> stream.close().run()).hasMessage("inner");
      assertThat(outer).hasValue(1);
    }

    @Test
    @DisplayName("when both finalisers throw, the outer failure is suppressed onto the inner one")
    void bothFinaliserFailuresAreKept() {
      VStream<Integer> stream =
          VStream.of(1, 2).onFinalize(failing("inner")).onFinalize(failing("outer"));

      assertThatThrownBy(() -> stream.close().run())
          .hasMessage("inner")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .extracting(Throwable::getMessage)
                      .containsExactly("outer"));
    }

    @Test
    @DisplayName("zipWith closes its second stream when closing the first fails")
    void zipClosesBothWhenFirstFails() {
      AtomicInteger second = new AtomicInteger();
      VStream<Integer> stream =
          VStream.of(1).onFinalize(failing("first")).zipWith(finalised(second), Integer::sum);

      assertThatThrownBy(() -> stream.close().run()).hasMessage("first");
      assertThat(second).hasValue(1);
    }

    @Test
    @DisplayName("when both streams fail to close, the second failure is suppressed onto the first")
    void bothCloseFailuresAreKept() {
      VStream<Integer> stream =
          VStream.of(1)
              .onFinalize(failing("first"))
              .zipWith(VStream.of(2).onFinalize(failing("second")), Integer::sum);

      assertThatThrownBy(() -> stream.close().run())
          .hasMessage("first")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .extracting(Throwable::getMessage)
                      .containsExactly("second"));
    }

    @Test
    @DisplayName("one exception thrown by two finalisers is reported once")
    void sharedFailureIsNotSuppressedOntoItself() {
      IllegalStateException shared = new IllegalStateException("shared");
      VTask<Unit> fails = VTask.fail(shared);
      VStream<Integer> stream =
          VStream.of(1).onFinalize(fails).zipWith(VStream.of(2).onFinalize(fails), Integer::sum);

      assertThatThrownBy(() -> stream.close().run()).isSameAs(shared);
      assertThat(shared.getSuppressed()).isEmpty();
    }
  }

  @Nested
  @DisplayName("closeAfterFailure closes a stream whose pull failed")
  class CloseAfterFailure {

    @Test
    @DisplayName(
        "it closes the rest a failed mapTask task carries, which closing the stream misses")
    void closesTheCarriedRest() {
      AtomicInteger runs = new AtomicInteger();
      VStream<Integer> stream = pullFailingAt(1, deferredFinalised(runs));
      Throwable failure = catchThrowable(() -> stream.pull().run());

      stream.close().run();
      assertThat(runs).as("runs once the pulled stream is closed").hasValue(0);
      VStream.closeAfterFailure(stream, failure).run();

      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("it closes the stream alone when the failure carries nothing")
    void closesTheStreamAlone() {
      List<String> closed = new ArrayList<>();

      VStream.closeAfterFailure(closedAs("stream", closed), new IllegalStateException("failed"))
          .run();

      assertThat(closed).containsExactly("stream");
    }

    @Test
    @DisplayName("a failure to close fails the task, and leaves the pull's failure as it is")
    void closeFailureFailsTheTask() {
      IllegalStateException failure = new IllegalStateException("pull failed");
      VStream<Integer> stream =
          new VStream<>() {
            @Override
            public VTask<VStream.Step<Integer>> pull() {
              return VTask.succeed(new VStream.Step.Done<>());
            }

            @Override
            public VTask<Unit> close() {
              return failing("close failed");
            }
          };

      assertThatThrownBy(() -> VStream.closeAfterFailure(stream, failure).run())
          .hasMessage("close failed");
      assertThat(failure.getSuppressed()).isEmpty();
    }

    @Test
    @DisplayName("it rejects a null stream or failure")
    void rejectsNulls() {
      IllegalStateException failure = new IllegalStateException("failed");

      assertThatThrownBy(() -> VStream.closeAfterFailure(null, failure))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("stream must not be null");
      assertThatThrownBy(() -> VStream.closeAfterFailure(VStream.empty(), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("failure must not be null");
    }
  }

  @Nested
  @DisplayName("An operator whose function throws closes the rest of the stream")
  class UserFunctionFailureCloses {

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.hkt.vstream.VStreamCloseAndReplayTest#userFunctions")
    @DisplayName("the finaliser runs although only that step held the rest of the stream")
    void throwingFunctionClosesTheRest(Function<VStream<Integer>, VStream<?>> op) {
      AtomicInteger runs = new AtomicInteger();

      VStream<?> stream = op.apply(deferredFinalised(runs));

      assertThatThrownBy(() -> stream.toList().run()).hasMessage("function failed");
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName(
        "the function's failure is still reported when the rest's close() breaks its contract and"
            + " returns null")
    void functionFailureSurvivesANullClose() {
      VStream<Integer> nullClosing =
          new VStream<>() {
            @Override
            public VTask<VStream.Step<Integer>> pull() {
              return VTask.succeed(new VStream.Step.Emit<>(1, VStream.empty()));
            }

            @Override
            @SuppressWarnings(
                "DataFlowIssue") // null is returned deliberately to break the contract
            public VTask<Unit> close() {
              return null;
            }
          };

      VStream<Integer> mapped =
          nullClosing.map(
              _ -> {
                throw new IllegalStateException("map failed");
              });

      assertThatThrownBy(() -> mapped.toList().run())
          .hasMessage("map failed")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .isNotEmpty()
                      .allSatisfy(s -> assertThat(s).isInstanceOf(NullPointerException.class)));
    }

    @Test
    @DisplayName("recover after a throwing map emits the recovery, and the upstream is closed")
    void recoverAfterThrowingMapCloses() {
      AtomicInteger runs = new AtomicInteger();

      List<Integer> recovered = failingAtSecond(finalised(runs)).recover(e -> -1).toList().run();

      assertThat(recovered).containsExactly(1, -1);
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("zipWith closes a right stream whose first pull fails after a left skip")
    void zipClosesRightWhoseFirstPullFails() {
      AtomicInteger right = new AtomicInteger();
      VStream<Integer> failingRight =
          finalised(right).mapTask(x -> VTask.fail(new IllegalStateException("right failed")));

      VStream<Integer> zipped =
          VStream.of(1, 2, 3).filter(x -> x > 2).zipWith(failingRight, Integer::sum);

      assertThatThrownBy(() -> zipped.toList().run()).hasMessage("right failed");
      assertThat(right).hasValue(1);
    }

    @Test
    @DisplayName("zipWith closes both streams when its combiner throws after a left skip")
    void zipCombinerFailureClosesBoth() {
      AtomicInteger left = new AtomicInteger();
      AtomicInteger right = new AtomicInteger();

      VStream<Integer> zipped =
          finalised(left).filter(x -> x > 1).zipWith(finalised(right), (a, b) -> boom());

      assertThatThrownBy(() -> zipped.toList().run()).hasMessage("function failed");
      assertThat(left).hasValue(1);
      assertThat(right).hasValue(1);
    }

    @Test
    @DisplayName("interleave closes a second stream whose first pull fails")
    void interleaveClosesSecondWhoseFirstPullFails() {
      AtomicInteger runs = new AtomicInteger();
      VStream<Integer> failing =
          finalised(runs).mapTask(x -> VTask.fail(new IllegalStateException("second failed")));

      VStream<Integer> interleaved = VStream.of(1, 2).interleave(failing);

      assertThatThrownBy(() -> interleaved.toList().run()).hasMessage("second failed");
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("mapTask and flatMap close the rest when their function returns null")
    void nullFromFunctionCloses() {
      AtomicInteger mapTaskRuns = new AtomicInteger();
      AtomicInteger flatMapRuns = new AtomicInteger();

      VStream<Integer> mapTask = deferredFinalised(mapTaskRuns).mapTask(x -> null);
      VStream<Integer> flatMap = deferredFinalised(flatMapRuns).flatMap(x -> null);

      assertThatThrownBy(() -> mapTask.toList().run()).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> flatMap.toList().run()).isInstanceOf(NullPointerException.class);
      assertThat(mapTaskRuns).hasValue(1);
      assertThat(flatMapRuns).hasValue(1);
    }

    @Test
    @DisplayName("a failed mapTask task closes the rest it carries, when nothing resumes from it")
    void failedTaskClosesCarriedRest() {
      AtomicInteger runs = new AtomicInteger();

      VStream<Integer> stream = pullFailingAt(1, deferredFinalised(runs));

      assertThatThrownBy(() -> stream.toList().run()).hasMessage("pull failed");
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("a mapTask task failing with a checked exception closes the rest it carries")
    void checkedFailureClosesCarriedRest() {
      AtomicInteger runs = new AtomicInteger();

      // run() wraps the checked failure, which is the one carrying the rest
      VStream<Integer> stream =
          deferredFinalised(runs).mapTask(x -> VTask.fail(new IOException("pull failed")));

      assertThatThrownBy(() -> stream.toList().run())
          .isInstanceOf(VTaskExecutionException.class)
          .hasCauseInstanceOf(IOException.class);
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("recoverWith closes the rest a failed mapTask task carries before it substitutes")
    void recoverWithClosesCarriedRest() {
      AtomicInteger released = new AtomicInteger();

      // recoverWith acts on the first pull, so the task fails there
      List<Integer> recovered =
          pullFailingAt(1, bracketed(released)).recoverWith(e -> VStream.of(-1)).toList().run();

      assertThat(recovered).containsExactly(-1);
      assertThat(released).hasValue(1);
    }

    @Test
    @DisplayName("recover closes the rest it would resume from when its own function throws")
    void throwingRecoveryClosesTheRest() {
      AtomicInteger runs = new AtomicInteger();

      VStream<Integer> stream = pullFailingAt(1, deferredFinalised(runs)).recover(e -> boom());

      assertThatThrownBy(() -> stream.toList().run()).hasMessage("function failed");
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("zipWith closes the first stream's rest when the second fails as it is first read")
    void zipClosesLeftWhenRightFails() {
      AtomicInteger left = new AtomicInteger();

      VStream<Integer> zipped =
          VStream.defer(
              () ->
                  finalised(left)
                      .zipWith(
                          VStream.<Integer>fail(new IllegalStateException("right failed")),
                          Integer::sum));

      assertThatThrownBy(() -> zipped.toList().run()).hasMessage("right failed");
      assertThat(left).hasValue(1);
    }
  }

  @Nested
  @DisplayName("A batch that fails part-way closes where it got to")
  class BatchFailureCloses {

    // The batch starts in of(0) and fails past the end of it, which closing its start cannot reach

    @Test
    @DisplayName("chunk")
    void chunkClosesWhereItGotTo() {
      AtomicInteger runs = new AtomicInteger();

      VStream<List<Integer>> chunked =
          VStream.of(0).concat(pullFailingAt(2, finalised(runs))).chunk(10);

      assertThatThrownBy(() -> chunked.toList().run()).hasMessage("pull failed");
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("chunkWhile, on the first chunk and a later one")
    void chunkWhileClosesWhereItGotTo() {
      AtomicInteger first = new AtomicInteger();
      AtomicInteger later = new AtomicInteger();

      VStream<List<Integer>> firstChunk =
          VStream.<Integer>empty()
              .concat(pullFailingAt(1, finalised(first)))
              .chunkWhile((a, b) -> true);
      VStream<List<Integer>> laterChunk =
          VStream.of(0).concat(pullFailingAt(2, finalised(later))).chunkWhile((a, b) -> true);

      assertThatThrownBy(() -> firstChunk.toList().run()).hasMessage("pull failed");
      assertThatThrownBy(() -> laterChunk.toList().run()).hasMessage("pull failed");
      assertThat(first).hasValue(1);
      assertThat(later).hasValue(1);
    }

    @Test
    @DisplayName("parEvalMap and parEvalMapUnordered, when a pull or the function fails")
    void parEvalMapClosesWhereItGotTo() {
      AtomicInteger pulled = new AtomicInteger();
      AtomicInteger ordered = new AtomicInteger();
      AtomicInteger unordered = new AtomicInteger();
      Function<Integer, VTask<Integer>> failsAtTwo =
          x -> x == 2 ? VTask.fail(new IllegalStateException("f failed")) : VTask.succeed(x);

      VStream<Integer> pullFails =
          VStreamPar.parEvalMap(
              VStream.of(0).concat(pullFailingAt(2, finalised(pulled))), 10, VTask::succeed);
      // A batch of three ends inside the second stream, short of its end
      VStream<Integer> orderedFails =
          VStreamPar.parEvalMap(VStream.of(0).concat(finalised(ordered)), 3, failsAtTwo);
      VStream<Integer> unorderedFails =
          VStreamPar.parEvalMapUnordered(VStream.of(0).concat(finalised(unordered)), 3, failsAtTwo);

      assertThatThrownBy(() -> pullFails.toList().run()).hasMessage("pull failed");
      assertThatThrownBy(() -> orderedFails.toList().run()).hasMessageContaining("f failed");
      assertThatThrownBy(() -> unorderedFails.toList().run()).hasMessageContaining("f failed");
      assertThat(pulled).hasValue(1);
      assertThat(ordered).hasValue(1);
      assertThat(unordered).hasValue(1);
    }
  }

  @Nested
  @DisplayName("A pulled VTask gives the same step each time it runs")
  class PulledTaskRunsAgain {

    @Test
    @DisplayName("defer calls its supplier when the pulled VTask runs, once per run")
    void deferCallsSupplierPerRun() {
      AtomicInteger supplied = new AtomicInteger();
      VStream<Integer> deferred =
          VStream.defer(
              () -> {
                supplied.incrementAndGet();
                return VStream.of(1);
              });

      VTask<VStream.Step<Integer>> pulled = deferred.pull();
      assertThat(supplied).hasValue(0);

      pulled.run();
      pulled.run();
      assertThat(supplied).hasValue(2);
    }

    @Test
    @DisplayName("generate, iterate and unfold call their functions when the pulled VTask runs")
    void factoriesCallFunctionsWhenRun() {
      AtomicInteger generated = new AtomicInteger();
      AtomicInteger iterated = new AtomicInteger();
      AtomicInteger unfolded = new AtomicInteger();

      VTask<VStream.Step<Integer>> generate = VStream.generate(generated::incrementAndGet).pull();
      VTask<VStream.Step<Integer>> iterate =
          VStream.iterate(0, n -> n + iterated.incrementAndGet()).pull();
      VTask<VStream.Step<Integer>> unfold =
          VStream.<Integer, Integer>unfold(
                  0,
                  n -> {
                    unfolded.incrementAndGet();
                    return VTask.succeed(Optional.of(new VStream.Seed<>(n, n + 1)));
                  })
              .pull();

      assertThat(List.of(generated.get(), iterated.get(), unfolded.get())).containsOnly(0);

      assertThat(emitted(generate.run())).isEqualTo(1);
      assertThat(emitted(iterate.run())).isEqualTo(0);
      assertThat(emitted(unfold.run())).isEqualTo(0);
      assertThat(List.of(generated.get(), iterated.get(), unfolded.get())).containsOnly(1);
    }

    @Test
    @DisplayName("distinct, chunk and chunkWhile give the same step on a second run")
    void statefulOperatorsRepeat() {
      VTask<VStream.Step<Integer>> distinct = VStream.of(1, 2).distinct().pull();
      VTask<VStream.Step<List<Integer>>> chunk = VStream.of(1, 2, 3).chunk(2).pull();
      VTask<VStream.Step<List<Integer>>> chunkWhile =
          VStream.of(1, 1, 2).chunkWhile(Objects::equals).pull();

      assertThat(emitted(distinct.run())).isEqualTo(emitted(distinct.run()));
      assertThat(emitted(chunk.run())).isEqualTo(emitted(chunk.run())).isEqualTo(List.of(1, 2));
      assertThat(emitted(chunkWhile.run()))
          .isEqualTo(emitted(chunkWhile.run()))
          .isEqualTo(List.of(1, 1));
    }
  }

  @Nested
  @DisplayName("A tail pulled again replays the same elements")
  class TailReplays {

    @Test
    @DisplayName("a distinct tail consumed twice gives the same elements")
    void distinctTailReplays() {
      VStream<Integer> tail = tailOf(VStream.of(1, 2, 1, 3).distinct().pull().run());

      assertThat(tail.toList().run()).containsExactly(2, 3);
      assertThat(tail.toList().run()).containsExactly(2, 3);
    }

    @Test
    @DisplayName("distinct keeps one null, and passes over skipped steps")
    void distinctHandlesNullAndSkips() {
      VStream<Integer> withNulls = VStream.fromList(Arrays.asList(null, 1, null, 1));
      VStream<Integer> withSkips = VStream.of(1, 2, 1, 3).filter(x -> x != 2);

      assertThat(withNulls.distinct().toList().run()).containsExactly(null, 1);
      assertThat(withSkips.distinct().toList().run()).containsExactly(1, 3);
    }

    @Test
    @DisplayName("a chunk already emitted does not change when its stream is pulled again")
    void chunkWhileChunkStaysFixed() {
      VStream<List<Integer>> rest =
          tailOf(VStream.of(1, 2, 2, 2).chunkWhile(Objects::equals).pull().run());

      // Follow the skips to the stream whose pull emits the second chunk
      VStream<List<Integer>> emitter = rest;
      VStream.Step<List<Integer>> step = emitter.pull().run();
      while (step instanceof VStream.Step.Skip<List<Integer>> skip) {
        emitter = skip.tail();
        step = emitter.pull().run();
      }
      List<Integer> second = emitted(step);
      emitter.pull().run();

      assertThat(second).containsExactly(2, 2, 2);
    }
  }

  @Nested
  @DisplayName("A head built when its pulled VTask runs closes what that run built")
  class BuiltHeadsClose {

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.hkt.vstream.VStreamCloseAndReplayTest#terminals")
    @DisplayName("a terminal failing on a bracket's first element releases it")
    void terminalFailingOnFirstElementReleasesBracket(Function<VStream<Integer>, VTask<?>> op) {
      AtomicInteger released = new AtomicInteger();

      VTask<?> task = op.apply(failingAtFirst(bracketed(released)));

      assertThatThrownBy(task::run).hasMessage("map failed");
      assertThat(released).hasValue(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.hkt.vstream.VStreamCloseAndReplayTest#terminals")
    @DisplayName("a terminal failing on a deferred stream's first element closes what it built")
    void terminalFailingOnFirstElementClosesDeferred(Function<VStream<Integer>, VTask<?>> op) {
      AtomicInteger runs = new AtomicInteger();

      VTask<?> task = op.apply(failingAtFirst(VStream.defer(() -> finalised(runs))));

      assertThatThrownBy(task::run).hasMessage("map failed");
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("forEach and find failing on a bracket's first element release it")
    void userFunctionFailingOnFirstElementReleasesBracket() {
      AtomicInteger forEachReleased = new AtomicInteger();
      AtomicInteger findReleased = new AtomicInteger();

      assertThatThrownBy(
              () ->
                  bracketed(forEachReleased)
                      .forEach(
                          x -> {
                            throw new IllegalStateException("action failed");
                          })
                      .run())
          .hasMessage("action failed");
      assertThatThrownBy(
              () ->
                  bracketed(findReleased)
                      .find(
                          x -> {
                            throw new IllegalStateException("predicate failed");
                          })
                      .run())
          .hasMessage("predicate failed");

      assertThat(forEachReleased).hasValue(1);
      assertThat(findReleased).hasValue(1);
    }

    @Test
    @DisplayName("closing a bracket's head after a pull releases what that pull acquired")
    void closingPulledBracketHeadReleases() {
      AtomicInteger released = new AtomicInteger();
      VStream<Integer> stream = bracketed(released);

      stream.pull().run();
      stream.close().run();

      assertThat(released).hasValue(1);
    }

    @Test
    @DisplayName("a use function that throws releases the resource, and its failure is kept")
    void throwingUseReleases() {
      AtomicInteger released = new AtomicInteger();
      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"),
              _ -> {
                throw new IllegalStateException("use failed");
              },
              _ -> VTask.exec(released::incrementAndGet));

      assertThatThrownBy(() -> stream.toList().run()).hasMessage("use failed");
      assertThat(released).hasValue(1);
    }

    @Test
    @DisplayName("a release that fails after use throws is suppressed onto the use failure")
    void releaseFailureAfterThrowingUseIsSuppressed() {
      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"),
              _ -> {
                throw new IllegalStateException("use failed");
              },
              _ -> failing("release failed"));

      assertThatThrownBy(() -> stream.toList().run())
          .hasMessage("use failed")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .extracting(Throwable::getMessage)
                      .containsExactly("release failed"));
    }
  }

  @Nested
  @DisplayName("What a deferred head keeps")
  class DeferredHeads {

    @Test
    @DisplayName("defer keeps nothing it built, so its tail is what closes the run")
    void deferKeepsNothing() {
      AtomicInteger runs = new AtomicInteger();
      VStream<Integer> deferred = deferredFinalised(runs);

      VStream<Integer> tail = tailOf(deferred.pull().run());
      deferred.close().run();
      assertThat(runs).as("closing the head").hasValue(0);

      tail.close().run();
      assertThat(runs).as("closing the tail").hasValue(1);
    }

    @Test
    @DisplayName(
        "a bracket run whose acquire fails leaves an earlier run for its own tail to close")
    void failedRunLeavesEarlierRun() {
      AtomicInteger acquired = new AtomicInteger();
      AtomicInteger released = new AtomicInteger();
      VStream<Integer> stream =
          VStream.bracket(
              VTask.of(
                  () -> {
                    if (acquired.incrementAndGet() == 2) {
                      throw new IllegalStateException("acquire failed");
                    }
                    return "resource";
                  }),
              _ -> VStream.of(1, 2),
              _ -> VTask.exec(released::incrementAndGet));

      VStream<Integer> firstRunTail = tailOf(stream.pull().run());
      assertThat(stream.pull().runSafe().isFailure()).isTrue();
      stream.close().run();
      assertThat(released).as("closing the head after the failed run").hasValue(0);

      firstRunTail.close().run();
      assertThat(released).as("closing the first run's tail").hasValue(1);
    }

    @Test
    @DisplayName("a bracket whose use function returns null releases the resource")
    void nullFromUseReleases() {
      AtomicInteger released = new AtomicInteger();
      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"), _ -> null, _ -> VTask.exec(released::incrementAndGet));

      assertThatThrownBy(() -> stream.toList().run())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("use must not return null");
      assertThat(released).hasValue(1);
    }
  }

  @Nested
  @DisplayName("merge closes the sources it stops reading")
  class MergeCloses {

    @Test
    @DisplayName("take(3) after merge runs each endless source's finaliser once")
    void takeAfterMergeRunsEachFinaliser() {
      AtomicInteger first = new AtomicInteger();
      AtomicInteger second = new AtomicInteger();

      VStreamPar.merge(endless(first), endless(second)).take(3).toList().run();

      assertThat(first).hasValue(1);
      assertThat(second).hasValue(1);
    }

    @Test
    @DisplayName("headOption after merge returns while another source is blocked in a pull")
    void headOptionReturnsPastAnIdleSource() throws InterruptedException {
      VStream<Integer> idle =
          () ->
              VTask.of(
                  () -> {
                    new CountDownLatch(1).await();
                    return new VStream.Step.Done<>();
                  });
      List<Optional<Integer>> result = new ArrayList<>();

      Thread consumer =
          Thread.ofVirtual()
              .start(() -> result.add(VStreamPar.merge(VStream.of(1), idle).headOption().run()));
      consumer.join(Duration.ofSeconds(10));

      assertThat(consumer.isAlive()).as("headOption should not wait for the idle source").isFalse();
      assertThat(result).containsExactly(Optional.of(1));
    }

    @Test
    @DisplayName("a failure past merge stops its producer, and closes each source once")
    void failurePastMergeStopsProducer() {
      AtomicInteger pulls = new AtomicInteger();
      AtomicInteger pullsWhenClosed = new AtomicInteger(-1);
      AtomicInteger otherRuns = new AtomicInteger();
      VStream<Integer> counted =
          VStream.generate(pulls::incrementAndGet)
              .onFinalize(VTask.exec(() -> pullsWhenClosed.set(pulls.get())));
      VStream<Integer> merged = VStreamPar.merge(counted, endless(otherRuns));

      assertThatThrownBy(() -> failingAtFirst(merged).toList().run()).hasMessage("map failed");

      assertThat(pullsWhenClosed.get()).as("pulls when the source closed").isPositive();
      assertThat(pulls).as("no pull after the source closed").hasValue(pullsWhenClosed.get());
      assertThat(otherRuns).hasValue(1);
    }

    @Test
    @DisplayName(
        "a source's finaliser that waits is not interrupted when the merge is closed again")
    void waitingSourceFinaliserIsNotInterrupted() throws InterruptedException {
      CountDownLatch finalising = new CountDownLatch(1);
      CountDownLatch proceed = new CountDownLatch(1);
      AtomicBoolean interrupted = new AtomicBoolean();
      VStream<Integer> slowToClose =
          VStream.repeat(1)
              .onFinalize(
                  VTask.exec(
                      () -> {
                        finalising.countDown();
                        try {
                          proceed.await();
                        } catch (InterruptedException e) {
                          interrupted.set(true);
                        }
                      }));
      VStream<Integer> merged = VStreamPar.merge(slowToClose, VStream.repeat(2));

      Thread consumer = Thread.ofVirtual().start(() -> merged.take(1).toList().run());
      assertThat(finalising.await(10, TimeUnit.SECONDS)).isTrue();
      Thread closer = Thread.ofVirtual().start(() -> merged.close().run());
      // Once the second close waits for the producer, it has interrupted it
      await().atMost(Duration.ofSeconds(10)).until(() -> closer.getState() == Thread.State.WAITING);
      proceed.countDown();
      consumer.join(Duration.ofSeconds(10));
      closer.join(Duration.ofSeconds(10));

      assertThat(interrupted).isFalse();
      assertThat(consumer.isAlive()).isFalse();
      assertThat(closer.isAlive()).isFalse();
    }

    @Test
    @DisplayName("a source failing stops the merge run, closing the other sources")
    void sourceFailureStopsTheRun() throws InterruptedException {
      CountDownLatch quietClosed = new CountDownLatch(1);
      VStream<Integer> quiet =
          VStream.<Integer>repeat(1)
              .mapTask(
                  x ->
                      VTask.of(
                          () -> {
                            new CountDownLatch(1).await();
                            return x;
                          }))
              .onFinalize(VTask.exec(quietClosed::countDown));
      VStream<Integer> merged =
          VStream.defer(
              () ->
                  VStreamPar.merge(
                      VStream.<Integer>fail(new IllegalStateException("source failed")), quiet));

      assertThatThrownBy(() -> merged.toList().run()).hasMessage("source failed");
      assertThat(quietClosed.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("each source's failed mapTask task has its rest closed, the discarded one too")
    void failedSourceTasksCloseCarriedRests() {
      AtomicInteger first = new AtomicInteger();
      AtomicInteger second = new AtomicInteger();
      // Both tasks fail together, so merge reports one failure and discards the other
      CountDownLatch bothRunning = new CountDownLatch(2);
      Function<Integer, VTask<Integer>> failTogether =
          x ->
              VTask.of(
                  () -> {
                    bothRunning.countDown();
                    bothRunning.await(5, TimeUnit.SECONDS);
                    throw new IllegalStateException("pull failed");
                  });

      VStream<Integer> merged =
          VStreamPar.merge(
              deferredFinalised(first).mapTask(failTogether),
              deferredFinalised(second).mapTask(failTogether));

      assertThatThrownBy(() -> merged.toList().run()).hasMessage("pull failed");
      assertThat(first).hasValue(1);
      assertThat(second).hasValue(1);
    }
  }

  @Nested
  @DisplayName("A toPublisher subscription closes the stream it read, once")
  class PublisherCloses {

    @Test
    @DisplayName("a subscriber that cancels before requesting closes nothing")
    void cancelBeforeRequestClosesNothing() {
      List<Integer> closed = new CopyOnWriteArrayList<>();
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(recordingCloses(0, closed));

      publisher.subscribe(subscriber(0, _ -> {}, _ -> {}));
      publisher.subscribe(subscriber(1, Flow.Subscription::cancel, _ -> {}));

      await().atMost(Duration.ofSeconds(5)).until(() -> !closed.isEmpty());
      assertThat(closed).as("only the second subscriber's position is closed").containsExactly(1);
    }

    @Test
    @DisplayName("cancelling twice closes the stream once")
    void cancelTwiceClosesOnce() {
      List<Integer> closed = new CopyOnWriteArrayList<>();

      VStreamReactive.toPublisher(recordingCloses(0, closed))
          .subscribe(
              subscriber(
                  1,
                  subscription -> {
                    subscription.cancel();
                    subscription.cancel();
                  },
                  _ -> {}));

      await().atMost(Duration.ofSeconds(5)).until(() -> !closed.isEmpty());
      assertThat(closed).containsExactly(1);
    }

    @Test
    @DisplayName("a failing stream is closed before the subscriber hears of the failure")
    void failureClosesBeforeOnError() throws InterruptedException {
      AtomicInteger runs = new AtomicInteger();
      AtomicInteger runsAtError = new AtomicInteger(-1);
      CountDownLatch errored = new CountDownLatch(1);

      VStreamReactive.toPublisher(failingAtFirst(finalised(runs)))
          .subscribe(
              subscriber(
                  4,
                  _ -> {},
                  _ -> {
                    runsAtError.set(runs.get());
                    errored.countDown();
                  }));

      assertThat(errored.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(runsAtError).hasValue(1);
    }

    @Test
    @DisplayName("a mapTask task failing as the subscription is cancelled has its rest closed")
    void taskFailingWhileCancellingClosesCarriedRest() {
      AtomicInteger runs = new AtomicInteger();
      AtomicReference<Flow.Subscription> held = new AtomicReference<>();
      List<Throwable> errors = new CopyOnWriteArrayList<>();
      // The cancel closes the stream, which reaches nothing past the deferred head
      VStream<Integer> stream =
          deferredFinalised(runs)
              .mapTask(
                  x ->
                      VTask.of(
                          () -> {
                            held.get().cancel();
                            throw new IllegalStateException("pull failed");
                          }));

      VStreamReactive.toPublisher(stream)
          .subscribe(
              new Flow.Subscriber<Integer>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                  held.set(subscription);
                  subscription.request(1);
                }

                @Override
                public void onNext(Integer item) {}

                @Override
                public void onError(Throwable throwable) {
                  errors.add(throwable);
                }

                @Override
                public void onComplete() {}
              });

      await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() == 1);
      assertThat(errors).as("a cancelled subscriber hears of no failure").isEmpty();
    }

    @Test
    @DisplayName("a failed mapTask task has the rest it carries closed before onError")
    void failedTaskClosesCarriedRestBeforeOnError() throws InterruptedException {
      AtomicInteger runs = new AtomicInteger();
      AtomicInteger runsAtError = new AtomicInteger(-1);
      CountDownLatch errored = new CountDownLatch(1);

      VStreamReactive.toPublisher(pullFailingAt(1, deferredFinalised(runs)))
          .subscribe(
              subscriber(
                  4,
                  _ -> {},
                  _ -> {
                    runsAtError.set(runs.get());
                    errored.countDown();
                  }));

      assertThat(errored.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(runsAtError).hasValue(1);
    }

    @Test
    @DisplayName("a finaliser failure while closing a failed stream is suppressed onto the failure")
    void closeFailureIsSuppressedOntoTheFailure() throws InterruptedException {
      List<Throwable> errors = new ArrayList<>();
      CountDownLatch errored = new CountDownLatch(1);
      VStream<Integer> stream = VStream.of(1, 2).onFinalize(failing("fin"));

      VStreamReactive.toPublisher(failingAtSecond(stream))
          .subscribe(
              subscriber(
                  4,
                  _ -> {},
                  error -> {
                    errors.add(error);
                    errored.countDown();
                  }));

      assertThat(errored.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(errors)
          .singleElement()
          .satisfies(
              e -> {
                assertThat(e).hasMessage("map failed");
                assertThat(e.getSuppressed())
                    .extracting(Throwable::getMessage)
                    .containsExactly("fin");
              });
    }

    @Test
    @DisplayName("a toPublisher cancel closes the stream while a probe waits on a quiet stream")
    void cancelStopsAWaitingProbe() throws InterruptedException {
      CountDownLatch finalised = new CountDownLatch(1);
      CountDownLatch waiting = new CountDownLatch(1);
      VStream<Integer> quiet =
          () ->
              VTask.of(
                  () -> {
                    waiting.countDown();
                    new CountDownLatch(1).await();
                    return new VStream.Step.Done<>();
                  });
      VStream<Integer> stream =
          VStream.of(1).concat(quiet).onFinalize(VTask.exec(finalised::countDown));
      List<Flow.Subscription> subscriptions = new CopyOnWriteArrayList<>();
      CountDownLatch received = new CountDownLatch(1);

      VStreamReactive.toPublisher(stream)
          .subscribe(
              subscriber(
                  1,
                  subscription -> {
                    subscriptions.add(subscription);
                    received.countDown();
                  },
                  _ -> {}));
      assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
      // The drain now probes for the end with no demand, and the quiet stream never answers
      assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
      subscriptions.getFirst().cancel();

      assertThat(finalised.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("a toPublisher cancel closes the stream while a demanded pull waits on it")
    void cancelStopsAWaitingDemandedPull() throws InterruptedException {
      CountDownLatch finalised = new CountDownLatch(1);
      CountDownLatch waiting = new CountDownLatch(1);
      VStream<Integer> quiet =
          () ->
              VTask.of(
                  () -> {
                    waiting.countDown();
                    new CountDownLatch(1).await();
                    return new VStream.Step.Done<>();
                  });
      VStream<Integer> stream =
          VStream.of(1).concat(quiet).onFinalize(VTask.exec(finalised::countDown));
      List<Flow.Subscription> subscriptions = new CopyOnWriteArrayList<>();

      VStreamReactive.toPublisher(stream).subscribe(subscriber(2, subscriptions::add, _ -> {}));
      // The drain has one element delivered and one more demanded, which the quiet stream holds
      assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
      subscriptions.getFirst().cancel();

      assertThat(finalised.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(
        "org.higherkindedj.hkt.vstream.VStreamCloseAndReplayTest#stepsPulledWhileCancelling")
    @DisplayName(
        "a step pulled while the subscription is cancelled is not delivered, and is closed")
    void stepPulledWhileCancellingIsNotDelivered(
        Function<VStream<Integer>, VStream.Step<Integer>> step, String closedPart) {
      AtomicReference<Flow.Subscription> held = new AtomicReference<>();
      List<String> closed = new CopyOnWriteArrayList<>();
      List<String> delivered = new CopyOnWriteArrayList<>();
      VStream<Integer> rest = closedAs("rest", closed);
      VStream<Integer> head =
          new VStream<>() {
            @Override
            public VTask<VStream.Step<Integer>> pull() {
              return VTask.of(
                  () -> {
                    held.get().cancel();
                    return step.apply(rest);
                  });
            }

            @Override
            public VTask<Unit> close() {
              return VTask.exec(() -> closed.add("head"));
            }
          };

      VStreamReactive.toPublisher(head)
          .subscribe(
              new Flow.Subscriber<Integer>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                  held.set(subscription);
                  subscription.request(1);
                }

                @Override
                public void onNext(Integer item) {
                  delivered.add("next");
                }

                @Override
                public void onError(Throwable throwable) {
                  delivered.add("error");
                }

                @Override
                public void onComplete() {
                  delivered.add("complete");
                }
              });

      await().atMost(Duration.ofSeconds(5)).until(() -> !closed.isEmpty());
      assertThat(closed).containsExactly(closedPart);
      assertThat(delivered).isEmpty();
    }

    @Test
    @DisplayName("a cancel that interrupts a merge does not cut short the merge's own closing")
    void interruptedMergeStillClosesItsSources() throws InterruptedException {
      List<String> order = new CopyOnWriteArrayList<>();
      CountDownLatch outerClosed = new CountDownLatch(1);
      VStream<Integer> quiet =
          VStream.<Integer>repeat(1)
              .mapTask(
                  x ->
                      VTask.of(
                          () -> {
                            new CountDownLatch(1).await();
                            return x;
                          }))
              .onFinalize(VTask.exec(() -> order.add("source")));
      VStream<Integer> stream =
          VStreamPar.merge(VStream.of(1), quiet)
              .onFinalize(
                  VTask.exec(
                      () -> {
                        order.add("outer");
                        outerClosed.countDown();
                      }));
      List<Flow.Subscription> subscriptions = new CopyOnWriteArrayList<>();
      AtomicReference<Thread> drain = new AtomicReference<>();
      CountDownLatch received = new CountDownLatch(1);

      VStreamReactive.toPublisher(stream)
          .subscribe(
              subscriber(
                  1,
                  subscription -> {
                    subscriptions.add(subscription);
                    // onNext runs on the drain thread
                    drain.set(Thread.currentThread());
                    received.countDown();
                  },
                  _ -> {}));
      assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
      // The drain now probes the merge, and waits for the quiet source
      await()
          .atMost(Duration.ofSeconds(5))
          .until(() -> drain.get().getState() == Thread.State.WAITING);
      subscriptions.getFirst().cancel();

      assertThat(outerClosed.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(order).containsExactly("source", "outer");
    }
  }

  @Nested
  @DisplayName("Closing a fromPublisher stream cancels its subscription")
  class PublisherStreamCancels {

    @Test
    @DisplayName("take(1) cancels the subscription, and closing again is safe")
    void takeCancelsSubscription() {
      AtomicBoolean cancelled = new AtomicBoolean();
      VStream<Integer> stream = VStreamReactive.fromPublisher(recordingPublisher(cancelled), 16);

      List<Integer> taken = stream.take(1).toList().run();
      stream.close().run();

      assertThat(taken).containsExactly(1);
      assertThat(cancelled).isTrue();
    }

    @Test
    @DisplayName("a subscription handed over after the stream is closed is cancelled at once")
    void lateSubscriptionIsCancelled() {
      List<Flow.Subscriber<? super Integer>> subscribers = new ArrayList<>();
      AtomicBoolean cancelled = new AtomicBoolean();
      AtomicBoolean requested = new AtomicBoolean();
      VStream<Integer> stream = VStreamReactive.fromPublisher(subscribers::add, 16);

      stream.close().run();
      subscribers
          .getFirst()
          .onSubscribe(
              new Flow.Subscription() {
                @Override
                public void request(long n) {
                  requested.set(true);
                }

                @Override
                public void cancel() {
                  cancelled.set(true);
                }
              });

      assertThat(cancelled).isTrue();
      assertThat(requested).isFalse();
    }
  }
}
