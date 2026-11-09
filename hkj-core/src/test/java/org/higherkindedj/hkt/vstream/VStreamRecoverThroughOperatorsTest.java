// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream.Step;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("recover resumes through the operators between it and a failed mapTask")
class VStreamRecoverThroughOperatorsTest {

  /** The elements of {@code source}, through a task that fails at {@code bad}. */
  private static VStream<Integer> failsAt(int bad, VStream<Integer> source) {
    return source.mapTask(
        n -> n == bad ? VTask.fail(new IOException("element " + n)) : VTask.succeed(n));
  }

  private static VStream<Integer> failsAt(int bad, Integer... values) {
    return failsAt(bad, VStream.of(values));
  }

  /** What a task failed with, as {@link VTask#runSafe()} keeps it. */
  private static Throwable failureOf(VTask<?> task) {
    Try<?> result = task.runSafe();
    assertThat(result).isInstanceOf(Try.Failure.class);
    return ((Try.Failure<?>) result).cause();
  }

  private static <A> Step.Emit<A> emitted(VStream<A> stream) {
    Step<A> step = stream.pull().run();
    assertThat(step).isInstanceOf(Step.Emit.class);
    return (Step.Emit<A>) step;
  }

  /** Waits for a latch, failing the test rather than hanging if it is not counted down. */
  private static void awaitWithin(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).as("latch released within 5 seconds").isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /**
   * The elements of {@code source}, through a task that fails for each element up to {@code last}.
   */
  private static VStream<Integer> failsAtOrBelow(int last, VStream<Integer> source) {
    return source.mapTask(
        n -> n <= last ? VTask.fail(new IOException("element " + n)) : VTask.succeed(n));
  }

  @Nested
  @DisplayName("Each operator applies to the elements after the failure")
  class ElementOperators {

    @Test
    @DisplayName("map applies to them, a map that changes the type included")
    void map() {
      assertThat(failsAt(2, 1, 2, 3).map(n -> n * 10).recover(_ -> -1).toList().run())
          .containsExactly(10, -1, 30);
      assertThat(failsAt(2, 1, 2, 3).map(n -> "#" + n).recover(_ -> "lost").toList().run())
          .containsExactly("#1", "lost", "#3");
    }

    @Test
    @DisplayName("filter tests them")
    void filter() {
      assertThat(
              failsAt(2, 1, 2, 3, 4, 5, 6).filter(n -> n % 2 == 1).recover(_ -> -1).toList().run())
          .containsExactly(1, -1, 3, 5);
    }

    @Test
    @DisplayName("takeWhile tests them, and still stops")
    void takeWhile() {
      assertThat(failsAt(2, 1, 2, 3, 4, 5, 6).takeWhile(n -> n < 5).recover(_ -> -1).toList().run())
          .containsExactly(1, -1, 3, 4);
    }

    @Test
    @DisplayName("dropWhile goes on dropping them, and drops a failed element within the prefix")
    void dropWhile() {
      assertThat(failsAt(2, 1, 2, 3, 4, 5, 6).dropWhile(n -> n < 4).recover(_ -> -1).toList().run())
          .containsExactly(4, 5, 6);
      assertThat(failsAt(5, 1, 2, 3, 4, 5, 6).dropWhile(n -> n < 4).recover(_ -> -1).toList().run())
          .containsExactly(4, -1, 6);
    }

    @Test
    @DisplayName("take counts the failed element, and still ends an endless stream")
    void take() {
      assertThat(failsAt(2, 1, 2, 3, 4, 5, 6).take(3).recover(_ -> -1).toList().run())
          .containsExactly(1, -1, 3);
      assertThat(
              failsAt(2, VStream.iterate(1, n -> n + 1))
                  .take(3)
                  .recover(_ -> -1)
                  .take(10)
                  .toList()
                  .run())
          .containsExactly(1, -1, 3);
    }

    @Test
    @DisplayName("take whose last element fails closes the rest")
    void takeEndingAtTheFailure() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> source =
          VStream.of(1, 2, 3, 4).onFinalize(VTask.exec(finalised::incrementAndGet));

      assertThat(failsAt(2, source).take(2).recover(_ -> -1).toList().run()).containsExactly(1, -1);
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("drop drops a failed element within the prefix, as one of the n")
    void drop() {
      assertThat(failsAt(2, 1, 2, 3, 4, 5, 6).drop(3).recover(_ -> -1).toList().run())
          .containsExactly(4, 5, 6);
      assertThat(failsAt(5, 1, 2, 3, 4, 5, 6).drop(3).recover(_ -> -1).toList().run())
          .containsExactly(4, -1, 6);
    }

    @Test
    @DisplayName(
        "take and drop, or takeWhile and dropWhile, split the stream with the failure once")
    void splittingTheStream() {
      VStream<Integer> stream = failsAt(2, 1, 2, 3, 4, 5, 6);

      assertThat(stream.take(3).concat(stream.drop(3)).recover(_ -> -1).toList().run())
          .containsExactly(1, -1, 3, 4, 5, 6);
      assertThat(
              stream
                  .takeWhile(n -> n < 4)
                  .concat(stream.dropWhile(n -> n < 4))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(1, -1, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("distinct remembers the elements before the failure")
    void distinct() {
      assertThat(failsAt(2, 1, 2, 1, 3, 3).distinct().recover(_ -> -1).toList().run())
          .containsExactly(1, -1, 3);
    }

    @Test
    @DisplayName("peek sees them, and onComplete runs once, at the end")
    void peekAndOnComplete() {
      List<Integer> seen = new ArrayList<>();
      AtomicInteger completed = new AtomicInteger();

      List<Integer> result =
          failsAt(2, 1, 2, 3)
              .peek(seen::add)
              .onComplete(completed::incrementAndGet)
              .recover(_ -> -1)
              .toList()
              .run();

      assertThat(result).containsExactly(1, -1, 3);
      assertThat(seen).containsExactly(1, 3);
      assertThat(completed).hasValue(1);
    }

    @Test
    @DisplayName("a second mapTask runs its task for them")
    void mapTaskAfterMapTask() {
      assertThat(
              failsAt(2, 1, 2, 3)
                  .mapTask(n -> VTask.succeed(n * 10))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(10, -1, 30);
    }

    @Test
    @DisplayName("throttle and metered pace them, and the failed element as one of them")
    void throttleAndMetered() {
      // One element a window, and the failed element holds its place, so it waits for the window
      // that 1 opened, and 3 for the one it opened
      long start = System.nanoTime();
      assertThat(
              VStreamThrottle.throttle(failsAt(2, 1, 2, 3), 1, Duration.ofMillis(40))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(1, -1, 3);
      assertThat(Duration.ofNanos(System.nanoTime() - start))
          .isGreaterThanOrEqualTo(Duration.ofMillis(80));

      // metered waits before each element it emits, the failed one included
      start = System.nanoTime();
      assertThat(
              VStreamThrottle.metered(failsAt(2, 1, 2, 3), Duration.ofMillis(30))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(1, -1, 3);
      assertThat(Duration.ofNanos(System.nanoTime() - start))
          .isGreaterThanOrEqualTo(Duration.ofMillis(90));
    }
  }

  @Nested
  @DisplayName("A stream that combines streams goes on with the rest of each")
  class Combinations {

    @Test
    @DisplayName("concat goes on to its second stream")
    void concat() {
      assertThat(failsAt(2, 1, 2, 3).concat(VStream.of(9)).recover(_ -> -1).toList().run())
          .containsExactly(1, -1, 3, 9);
    }

    @Test
    @DisplayName("flatMap goes on with the outer stream after a failed outer element")
    void flatMapOuter() {
      assertThat(
              failsAt(2, 1, 2, 3)
                  .flatMap(n -> VStream.of(n, n * 10))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(1, 10, -1, 3, 30);
    }

    @Test
    @DisplayName("flatMap goes on with the outer stream after a failed inner element")
    void flatMapInner() {
      assertThat(
              VStream.of(1, 2)
                  .flatMap(x -> failsAt(2, 1, 2, 3).map(n -> x * 10 + n))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(11, -1, 13, 21, -1, 23);
    }

    @Test
    @DisplayName("zipWith pairs a failed left element with the right one beside it")
    void zipWithLeft() {
      assertThat(
              failsAt(2, 1, 2, 3)
                  .zipWith(VStream.of("a", "b", "c"), (n, s) -> n + s)
                  .recover(_ -> "lost")
                  .toList()
                  .run())
          .containsExactly("1a", "lost", "3c");
    }

    @Test
    @DisplayName("zipWith pairs a failed right element with the left one beside it")
    void zipWithRight() {
      assertThat(
              VStream.of("a", "b", "c")
                  .zipWith(failsAt(2, 1, 2, 3), (s, n) -> s + n)
                  .recover(_ -> "lost")
                  .toList()
                  .run())
          .containsExactly("a1", "lost", "c3");
      assertThat(
              VStream.of("a", "b")
                  .zipWith(failsAt(1, 1, 2), (s, n) -> s + n)
                  .recover(_ -> "lost")
                  .toList()
                  .run())
          .containsExactly("lost", "b2");
    }

    @Test
    @DisplayName("interleave gives the other stream the next turn after a failed element")
    void interleave() {
      assertThat(
              failsAt(3, 1, 3, 5).interleave(VStream.of(2, 4, 6)).recover(_ -> -1).toList().run())
          .containsExactly(1, 2, -1, 4, 5, 6);
      assertThat(failsAt(1, 1, 3).interleave(VStream.of(2, 4)).recover(_ -> -1).toList().run())
          .containsExactly(-1, 2, 3, 4);
      assertThat(VStream.of(1, 3).interleave(failsAt(2, 2, 4)).recover(_ -> -1).toList().run())
          .containsExactly(1, -1, 3, 4);
      assertThat(
              VStream.of(1, 3, 5).interleave(failsAt(4, 2, 4, 6)).recover(_ -> -1).toList().run())
          .containsExactly(1, 2, 3, -1, 5, 6);
    }
  }

  @Nested
  @DisplayName("An operator that reads ahead emits what it read before the failure first")
  class ReadingAhead {

    @Test
    @DisplayName("chunk emits the chunk so far, then chunks again after the failure")
    void chunk() {
      assertThat(
              failsAt(2, 1, 2, 3, 4, 5).chunk(2).recover(_ -> NonEmptyList.of(-1)).toList().run())
          .containsExactly(
              NonEmptyList.of(1), NonEmptyList.of(-1), NonEmptyList.of(3, 4), NonEmptyList.of(5));
      assertThat(
              failsAt(3, 1, 2, 3, 4, 5).chunk(2).recover(_ -> NonEmptyList.of(-1)).toList().run())
          .containsExactly(NonEmptyList.of(1, 2), NonEmptyList.of(-1), NonEmptyList.of(4, 5));
    }

    @Test
    @DisplayName("chunkWhile emits the chunk so far, then chunks again after the failure")
    void chunkWhile() {
      assertThat(
              failsAt(2, 1, 2, 3, 1, 4)
                  .chunkWhile((a, b) -> a <= b)
                  .recover(_ -> NonEmptyList.of(-1))
                  .toList()
                  .run())
          .containsExactly(
              NonEmptyList.of(1), NonEmptyList.of(-1), NonEmptyList.of(3), NonEmptyList.of(1, 4));
      assertThat(
              failsAt(1, 1, 2, 2)
                  .chunkWhile(Objects::equals)
                  .recover(_ -> NonEmptyList.of(-1))
                  .toList()
                  .run())
          .containsExactly(NonEmptyList.of(-1), NonEmptyList.of(2, 2));
    }

    @Test
    @DisplayName("parEvalMap and parEvalMapUnordered emit the batch so far, then map the rest")
    void parEvalMap() {
      assertThat(
              VStreamPar.parEvalMap(failsAt(3, 1, 2, 3, 4, 5), 4, n -> VTask.succeed(n * 10))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(10, 20, -1, 40, 50);
      assertThat(
              VStreamPar.parEvalMap(failsAt(1, 1, 2, 3), 4, n -> VTask.succeed(n * 10))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(-1, 20, 30);
      assertThat(
              VStreamPar.parEvalMapUnordered(
                      failsAt(3, 1, 2, 3, 4, 5), 4, n -> VTask.succeed(n * 10))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactlyInAnyOrder(10, 20, -1, 40, 50);
    }

    @Test
    @DisplayName("parEvalFlatMap emits the batch so far, then maps the rest")
    void parEvalFlatMap() {
      assertThat(
              VStreamPar.parEvalFlatMap(failsAt(2, 1, 2, 3), 4, n -> VStream.of(n, n))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(1, 1, -1, 3, 3);
    }

    @Test
    @DisplayName(
        "a failed task of a parEvalMap batch comes before the failed element read after it")
    void parEvalMapBatchFailing() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> source =
          VStream.of(1, 2, 3, 4).onFinalize(VTask.exec(finalised::incrementAndGet));

      assertThat(
              VStreamPar.parEvalMap(
                      failsAt(3, source),
                      4,
                      n ->
                          n == 1
                              ? VTask.fail(new IllegalStateException("task 1"))
                              : VTask.succeed(n))
                  .recover(error -> error instanceof IOException ? -3 : -1)
                  .toList()
                  .run())
          .containsExactly(-1, 2, -3, 4);
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a stream stopped before the failed element still closes the stream after it")
    void stoppedBeforeTheFailure() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> source =
          VStream.of(1, 2, 3, 4).onFinalize(VTask.exec(finalised::incrementAndGet));

      assertThat(failsAt(2, source).chunk(2).take(1).toList().run())
          .containsExactly(NonEmptyList.of(1));
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("the failed element after the part read ahead is the same each time it is pulled")
    void failureReadAgain() {
      VStream<Integer> mapped =
          VStreamPar.parEvalMap(failsAt(3, 1, 2, 3, 4, 5), 4, n -> VTask.succeed(n * 10))
              .map(n -> n + 1);
      VStream<Integer> atTheFailure = emitted(emitted(mapped).tail()).tail();

      // The failed element is a step like any other, so the map after parEvalMap applies once
      assertThat(atTheFailure.recover(_ -> -1).toList().run()).containsExactly(-1, 41, 51);
      assertThat(atTheFailure.recover(_ -> -1).toList().run()).containsExactly(-1, 41, 51);
    }
  }

  @Nested
  @DisplayName("A stream that cannot resume ends there")
  class Ending {

    @Test
    @DisplayName("merge stops every source at a failure, so recover ends the stream")
    void merge() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> source =
          VStream.of(1, 2, 3).onFinalize(VTask.exec(finalised::incrementAndGet));

      assertThat(
              VStreamPar.merge(List.of(failsAt(1, source), VStream.empty()))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(-1);
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a recover after ap, which reads its argument stream itself, ends the stream")
    void afterAp() {
      VStream<String> applied =
          VSTREAM.narrow(
              VStreamApplicative.INSTANCE.ap(
                  VSTREAM.widen(VStream.<Function<Integer, String>>of(n -> "#" + n)),
                  VSTREAM.widen(failsAt(2, 1, 2, 3))));

      assertThat(applied.recover(_ -> "lost").toList().run()).containsExactly("lost");
    }
  }

  @Nested
  @DisplayName("A finaliser between them runs once, when the resumed stream ends")
  class Finalisers {

    @Test
    @DisplayName("onFinalize runs after the elements after the failure, not at the failure")
    void onFinalize() {
      List<String> log = new ArrayList<>();

      List<Integer> result =
          failsAt(2, VStream.of(1, 2, 3).peek(n -> log.add("read " + n)))
              .onFinalize(VTask.exec(() -> log.add("finaliser")))
              .recover(_ -> -1)
              .toList()
              .run();

      assertThat(result).containsExactly(1, -1, 3);
      assertThat(log).containsExactly("read 1", "read 2", "read 3", "finaliser");
    }

    @Test
    @DisplayName("onFinalize runs once when the resumed stream is closed early")
    void onFinalizeClosedEarly() {
      AtomicInteger finalised = new AtomicInteger();

      List<Integer> result =
          failsAt(2, 1, 2, 3, 4)
              .onFinalize(VTask.exec(finalised::incrementAndGet))
              .recover(_ -> -1)
              .take(2)
              .toList()
              .run();

      assertThat(result).containsExactly(1, -1);
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a bracket releases after the elements after the failure")
    void bracket() {
      List<String> log = new ArrayList<>();

      List<Integer> result =
          VStream.bracket(
                  VTask.succeed("session"),
                  _ -> failsAt(2, VStream.of(1, 2, 3).peek(n -> log.add("read " + n))),
                  _ -> VTask.exec(() -> log.add("released")))
              .recover(_ -> -1)
              .toList()
              .run();

      assertThat(result).containsExactly(1, -1, 3);
      assertThat(log).containsExactly("read 1", "read 2", "read 3", "released");
    }

    @Test
    @DisplayName("onFinalize still runs before an unrecovered failure reaches the caller")
    void onFinalizeWithoutRecover() {
      AtomicInteger finalised = new AtomicInteger();

      Throwable failure =
          failureOf(
              failsAt(2, 1, 2, 3)
                  .onFinalize(VTask.exec(finalised::incrementAndGet))
                  .map(n -> n * 10)
                  .toList());

      assertThat(failure).isInstanceOf(IOException.class).hasMessage("element 2");
      assertThat(finalised).hasValue(1);
    }
  }

  @Nested
  @DisplayName("zipWith keeps its pairs in step")
  class ZipInStep {

    @Test
    @DisplayName("a failure on each side at one place is one failed pair, with the left error")
    void bothSidesFail() {
      VStream<Integer> right =
          VStream.of(1, 2, 3, 4, 5)
              .mapTask(
                  n ->
                      n == 3 ? VTask.fail(new IllegalStateException("right 3")) : VTask.succeed(n));

      assertThat(
              failsAt(3, 1, 2, 3, 4, 5)
                  .zipWith(right, (a, b) -> a + "/" + b)
                  .recover(Throwable::getMessage)
                  .toList()
                  .run())
          .containsExactly("1/1", "2/2", "element 3", "4/4", "5/5");
    }

    @Test
    @DisplayName("a failed first left element passes over the first right one")
    void firstLeftFails() {
      assertThat(
              failsAt(1, 1, 2, 3)
                  .zipWith(VStream.of("a", "b", "c"), (n, s) -> n + s)
                  .recover(_ -> "lost")
                  .toList()
                  .run())
          .containsExactly("lost", "2b", "3c");
    }

    @Test
    @DisplayName(
        "a failed left element pairs with the next right element, and ends where right does")
    void passingOverSkipsAndEnds() {
      assertThat(
              failsAt(2, 1, 2, 3)
                  .zipWith(VStream.of(0, 10, 20, 30).filter(n -> n != 10), Integer::sum)
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(1, -1, 33);

      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> left =
          VStream.of(1, 2, 3).onFinalize(VTask.exec(finalised::incrementAndGet));
      // No right element is left to pair with the failed one, so the zip ends before it
      assertThat(
              failsAt(2, left)
                  .zipWith(VStream.of(10), Integer::sum)
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(11);
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a right pull that fails beside a failed left element ends the zip")
    void passedOverRightFailsWithNoRest() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<String> right =
          VStream.<String>fail(new IllegalStateException("right down"))
              .onFinalize(VTask.exec(finalised::incrementAndGet));

      assertThat(
              failsAt(1, 1, 2).zipWith(right, (n, s) -> n + s).recover(_ -> "lost").toList().run())
          .containsExactly("lost");
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("many failed left elements in a row do not deepen the stack")
    void manyLeftFailuresInARow() {
      int failures = 10_000;
      List<Integer> result =
          failsAtOrBelow(failures, VStream.range(1, failures + 3))
              .zipWith(VStream.range(1, failures + 3), Integer::sum)
              .recover(_ -> -1)
              .toList()
              .run();

      assertThat(result).hasSize(failures + 2).endsWith(2 * (failures + 1), 2 * (failures + 2));
    }
  }

  @Nested
  @DisplayName("Any failure hands on what a batch read before it")
  class AnyFailureHandsOnTheBatch {

    private VStream<Integer> sourceFailingAfter(Integer... values) {
      return VStream.of(values).concat(VStream.fail(new IllegalStateException("source down")));
    }

    @Test
    @DisplayName("chunk and chunkWhile hand on the chunk so far, then fail")
    void chunks() {
      List<NonEmptyList<Integer>> chunked = new ArrayList<>();
      Throwable failure = failureOf(sourceFailingAfter(1, 2).chunk(3).forEach(chunked::add));
      assertThat(failure).hasMessage("source down");
      assertThat(chunked).containsExactly(NonEmptyList.of(1, 2));

      List<NonEmptyList<Integer>> runs = new ArrayList<>();
      failure = failureOf(sourceFailingAfter(1, 1).chunkWhile(Objects::equals).forEach(runs::add));
      assertThat(failure).hasMessage("source down");
      assertThat(runs).containsExactly(NonEmptyList.of(1, 1));
    }

    @Test
    @DisplayName("chunkWhile over a source that fails at once fails, closing it")
    void chunkWhileFailingAtOnce() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> source =
          VStream.<Integer>fail(new IllegalStateException("source down"))
              .onFinalize(VTask.exec(finalised::incrementAndGet));

      assertThat(failureOf(source.chunkWhile(Objects::equals).toList())).hasMessage("source down");
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a failed task of the batch read before a failed pull comes before the pull's")
    void parEvalMapBatchFailingAfterTheSource() {
      assertThat(
              VStreamPar.parEvalMap(
                      sourceFailingAfter(1, 2),
                      4,
                      n ->
                          n == 1
                              ? VTask.fail(new IllegalStateException("task 1"))
                              : VTask.succeed(n))
                  .recover(error -> "task 1".equals(error.getMessage()) ? -1 : -9)
                  .toList()
                  .run())
          // The pull's failure ends the stream, so its recovery value is the last
          .containsExactly(-1, 2, -9);
    }

    @Test
    @DisplayName("parEvalMap runs the tasks for the batch so far and hands on their results")
    void parEvalMap() {
      List<Integer> saved = new ArrayList<>();
      Throwable failure =
          failureOf(
              VStreamPar.parEvalMap(sourceFailingAfter(1, 2), 4, n -> VTask.succeed(n * 10))
                  .forEach(saved::add));

      assertThat(failure).hasMessage("source down");
      assertThat(saved).containsExactly(10, 20);
    }

    @Test
    @DisplayName("an interrupt while chunk reads ends the stream at once, handing on nothing")
    void interruptWhileChunking() {
      List<NonEmptyList<Integer>> chunked = new ArrayList<>();

      Throwable failure =
          failureOf(
              VStream.of(1, 2)
                  .concat(VStream.fail(new InterruptedException("stop")))
                  .chunk(3)
                  .forEach(chunked::add));
      Thread.interrupted();

      assertThat(failure).isInstanceOf(InterruptedException.class);
      assertThat(chunked).isEmpty();
    }

    @Test
    @DisplayName("an interrupt while parEvalMap reads ends the stream at once, running no task")
    void interruptWhileBatching() {
      AtomicInteger ran = new AtomicInteger();

      Throwable failure =
          failureOf(
              VStreamPar.parEvalMap(
                      VStream.of(1, 2).concat(VStream.fail(new InterruptedException("stop"))),
                      4,
                      n -> VTask.exec(ran::incrementAndGet).map(_ -> n))
                  .toList());
      Thread.interrupted();

      assertThat(failure).isInstanceOf(InterruptedException.class);
      assertThat(ran).hasValue(0);
    }
  }

  @Nested
  @DisplayName("Only a failed element is stepped past: a function that throws ends the stream")
  class FunctionFailures {

    private static Void failing() {
      throw new IllegalStateException("function failed");
    }

    @Test
    @DisplayName("a chunkWhile predicate that fails ends the stream and closes what it read")
    void chunkWhilePredicate() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<String> source =
          VStream.of("a", "b", "c").onFinalize(VTask.exec(finalised::incrementAndGet));

      List<NonEmptyList<String>> result =
          source
              .chunkWhile(
                  (_, _) -> {
                    failing();
                    return true;
                  })
              .recover(_ -> NonEmptyList.of("r"))
              .toList()
              .run();

      assertThat(result).containsExactly(NonEmptyList.of("r"));
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a zipWith combiner that fails ends the stream")
    void zipWithCombiner() {
      List<String> result =
          VStream.of("a", "b")
              .zipWith(
                  VStream.of("x", "y"),
                  (_, _) -> {
                    failing();
                    return "never";
                  })
              .recover(_ -> "r")
              .toList()
              .run();

      assertThat(result).containsExactly("r");
    }

    @Test
    @DisplayName("an onFinalize finaliser that fails at the end ends the stream")
    void finaliserAtTheEnd() {
      List<String> result =
          VStream.of("a")
              .onFinalize(VTask.exec(FunctionFailures::failing))
              .recover(_ -> "r")
              .toList()
              .run();

      assertThat(result).containsExactly("a", "r");
    }

    @Test
    @DisplayName(
        "an interrupted mapTask task ends the stream, closing the rest, and keeps the interrupt")
    void interruptedTask() {
      AtomicInteger finalised = new AtomicInteger();
      // defer keeps nothing it builds, so only the close of the rest reaches the finaliser
      VStream<Integer> interruptedAtOne =
          VStream.defer(
                  () -> VStream.of(1, 2, 3).onFinalize(VTask.exec(finalised::incrementAndGet)))
              .mapTask(
                  n -> n == 1 ? VTask.fail(new InterruptedException("stop")) : VTask.succeed(n));

      List<Integer> result = interruptedAtOne.recover(_ -> -1).toList().run();
      boolean interruptStatus = Thread.interrupted();

      assertThat(result).containsExactly(-1);
      assertThat(finalised).hasValue(1);
      assertThat(interruptStatus).isTrue();
    }

    @Test
    @DisplayName("an interrupt that run() wraps ends the stream too")
    void wrappedInterrupt() {
      VStream<Integer> interruptedAtTwo =
          VStream.of(1, 2, 3)
              .mapTask(
                  n ->
                      n == 2
                          ? VTask.fail(
                              new VTaskExecutionException(new InterruptedException("stop")))
                          : VTask.succeed(n));

      List<Integer> result = interruptedAtTwo.recover(_ -> -1).toList().run();
      boolean interruptStatus = Thread.interrupted();

      assertThat(result).containsExactly(1, -1);
      assertThat(interruptStatus).isTrue();
    }

    @Test
    @DisplayName("a wrapped failure that is not an interrupt is a failed element like any other")
    void wrappedFailure() {
      assertThat(
              VStream.of(1, 2, 3)
                  .mapTask(
                      n ->
                          n == 2
                              ? VTask.fail(
                                  new VTaskExecutionException(new IOException("element 2")))
                              : VTask.succeed(n))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(1, -1, 3);
    }
  }

  @Nested
  @DisplayName("One exception instance does not lead one stream into another")
  class SharedExceptions {

    @Test
    @DisplayName("streams on two threads failing with one instance each go on with their own")
    void twoThreads() throws Exception {
      IllegalStateException shared = new IllegalStateException("shared");
      CountDownLatch firstFailed = new CountDownLatch(1);
      CountDownLatch secondDone = new CountDownLatch(1);
      AtomicReference<List<Integer>> first = new AtomicReference<>();
      AtomicReference<List<Integer>> second = new AtomicReference<>();

      // The first stream stops in onError while the second fails and recovers with the same
      // instance, then goes on through its own map and recover
      Thread one =
          Thread.ofVirtual()
              .start(
                  () ->
                      first.set(
                          VStream.of(1, 2, 3)
                              .mapTask(n -> n == 1 ? VTask.<Integer>fail(shared) : VTask.succeed(n))
                              .onError(
                                  _ -> {
                                    firstFailed.countDown();
                                    awaitWithin(secondDone);
                                  })
                              .map(n -> n * 10)
                              .recover(_ -> -1)
                              .toList()
                              .run()));
      awaitWithin(firstFailed);
      second.set(
          VStream.of(100, 200, 300)
              .mapTask(n -> n == 100 ? VTask.<Integer>fail(shared) : VTask.succeed(n))
              .map(n -> n + 1)
              .recover(_ -> -2)
              .toList()
              .run());
      secondDone.countDown();
      one.join(5_000);

      assertThat(first.get()).containsExactly(-1, 20, 30);
      assertThat(second.get()).containsExactly(-2, 201, 301);
    }

    @Test
    @DisplayName("an instance thrown again after its stream ended does not resume that stream")
    void thrownAgainAfterTheStreamEnded() {
      IllegalStateException boom = new IllegalStateException("boom");
      VStream<Integer> first =
          VStream.of(1, 2, 3).mapTask(n -> n == 1 ? VTask.<Integer>fail(boom) : VTask.succeed(n));

      assertThat(failureOf(first.toList())).isSameAs(boom);
      assertThat(VStream.<Integer>fail(boom).recover(_ -> -1).toList().run()).containsExactly(-1);
      assertThat(VStream.<Integer>fail(boom).concat(VStream.of(9)).recover(_ -> -1).toList().run())
          .containsExactly(-1);
    }

    @Test
    @DisplayName("a stream onError runs, failing with the same instance, leaves the first alone")
    void streamRunInOnError() {
      IllegalStateException shared = new IllegalStateException("shared");
      AtomicReference<List<Integer>> inside = new AtomicReference<>();

      List<Integer> outside =
          VStream.of(1, 2, 3)
              .mapTask(n -> n == 1 ? VTask.<Integer>fail(shared) : VTask.succeed(n))
              .onError(
                  _ ->
                      inside.set(
                          VStream.of(100, 200)
                              .mapTask(
                                  n -> n == 100 ? VTask.<Integer>fail(shared) : VTask.succeed(n))
                              .recover(_ -> -2)
                              .toList()
                              .run()))
              .map(n -> n * 10)
              .recover(_ -> -1)
              .toList()
              .run();

      assertThat(inside.get()).containsExactly(-2, 200);
      assertThat(outside).containsExactly(-1, 20, 30);
    }

    @Test
    @DisplayName("a pull that fails with the instance, inside onError, ends only its own stream")
    void failedPullInOnError() {
      IllegalStateException shared = new IllegalStateException("shared");
      AtomicReference<List<Integer>> inside = new AtomicReference<>();

      List<Integer> outside =
          VStream.of(1, 2, 3)
              .mapTask(n -> n == 1 ? VTask.<Integer>fail(shared) : VTask.succeed(n))
              .onError(
                  _ ->
                      inside.set(
                          VStream.<Integer>fail(shared)
                              .concat(VStream.of(9))
                              .recover(_ -> -2)
                              .toList()
                              .run()))
              .recover(_ -> -1)
              .toList()
              .run();

      assertThat(inside.get()).containsExactly(-2);
      assertThat(outside).containsExactly(-1, 2, 3);
    }

    @Test
    @DisplayName("one instance failing many elements leaves its suppressed list as it was")
    void suppressedListUnchanged() {
      IllegalStateException shared = new IllegalStateException("shared");

      List<Integer> result =
          VStream.range(1, 101)
              .mapTask(n -> n % 2 == 0 ? VTask.<Integer>fail(shared) : VTask.succeed(n))
              .recover(_ -> -1)
              .toList()
              .run();

      assertThat(result).hasSize(100).startsWith(1, -1, 3).endsWith(99, -1);
      assertThat(shared.getSuppressed()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Code that pulls by hand gets a failed element as a step")
  class PullingByHand {

    @Test
    @DisplayName("a failed element holds the stream after it, which closing runs the finaliser")
    void closingTheTail() throws Exception {
      AtomicInteger finalised = new AtomicInteger();
      // defer keeps nothing it builds, so only the failed element's tail reaches the finaliser
      VStream<Integer> stream =
          VStream.defer(() -> failsAt(1, 1, 2).onFinalize(VTask.exec(finalised::incrementAndGet)));
      AtomicReference<Step<Integer>> step = new AtomicReference<>();

      Thread puller = Thread.ofVirtual().start(() -> step.set(stream.pull().run()));
      puller.join(5_000);

      Step.Fail<Integer> failed = (Step.Fail<Integer>) step.get();
      assertThat(failed.error()).isInstanceOf(IOException.class).hasMessage("element 1");
      assertThat(finalised).hasValue(0);
      // On another thread than the pull's, as on its own
      failed.tail().close().run();
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a failed element's tail reads on from the next element")
    void readingOn() {
      Step<Integer> first = failsAt(1, 1, 2).pull().run();

      assertThat(first).isInstanceOf(Step.Fail.class);
      assertThat(emitted(((Step.Fail<Integer>) first).tail()).value()).isEqualTo(2);
    }

    @Test
    @DisplayName("a pull by hand inside onError keeps its own stream after its failed element")
    void inOnError() {
      IllegalStateException shared = new IllegalStateException("shared");
      AtomicInteger insideFinalised = new AtomicInteger();
      VStream<Integer> inside =
          VStream.defer(
              () ->
                  VStream.of(100, 200)
                      .mapTask(n -> n == 100 ? VTask.<Integer>fail(shared) : VTask.succeed(n))
                      .onFinalize(VTask.exec(insideFinalised::incrementAndGet)));
      AtomicReference<Step<Integer>> insideStep = new AtomicReference<>();

      List<Integer> outside =
          VStream.of(1, 2, 3)
              .mapTask(n -> n == 1 ? VTask.<Integer>fail(shared) : VTask.succeed(n))
              .onError(_ -> insideStep.set(inside.pull().run()))
              .map(n -> n * 10)
              .recover(_ -> -1)
              .toList()
              .run();

      assertThat(outside).containsExactly(-1, 20, 30);
      assertThat(insideFinalised).hasValue(0);
      ((Step.Fail<Integer>) insideStep.get()).tail().close().run();
      assertThat(insideFinalised).hasValue(1);
    }
  }

  @Nested
  @DisplayName("Failing again, and running again, goes through the same operators")
  class Again {

    @Test
    @DisplayName("a second failure after a resume resumes through the same operators")
    void secondFailure() {
      VStream<Integer> twoFailures =
          VStream.of(1, 2, 3, 4, 5, 6)
              .mapTask(
                  n ->
                      n % 2 == 0 && n < 6
                          ? VTask.fail(new IOException("element " + n))
                          : VTask.succeed(n));

      assertThat(twoFailures.take(5).recover(_ -> -1).toList().run())
          .containsExactly(1, -1, 3, -1, 5);
      assertThat(twoFailures.map(n -> n * 10).recover(_ -> -1).toList().run())
          .containsExactly(10, -1, 30, -1, 50, 60);
      assertThat(twoFailures.chunk(2).recover(_ -> NonEmptyList.of(-1)).toList().run())
          .containsExactly(
              NonEmptyList.of(1),
              NonEmptyList.of(-1),
              NonEmptyList.of(3),
              NonEmptyList.of(-1),
              NonEmptyList.of(5, 6));
    }

    @Test
    @DisplayName("running the stream again resumes through the operators again, once each")
    void runAgain() {
      VStream<Integer> stream =
          failsAt(2, 1, 2, 3).map(n -> n * 10).filter(n -> n > 0).recover(_ -> -1);

      assertThat(stream.toList().run()).containsExactly(10, -1, 30);
      assertThat(stream.toList().run()).containsExactly(10, -1, 30);
    }
  }
}
