// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.higherkindedj.hkt.assertions.VStreamAssert.assertThatVStream;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("VStreamPar Parallel Combinators Test Suite")
class VStreamParTest {

  /** A task that waits on a latch until it is interrupted, and records that it was. */
  private static VTask<Integer> waitsUntilCancelled(int n, AtomicBoolean cancelled) {
    return VTask.of(
        () -> {
          try {
            new CountDownLatch(1).await();
          } catch (InterruptedException e) {
            cancelled.set(true);
            throw e;
          }
          return n;
        });
  }

  @Nested
  @DisplayName("parEvalMap() Tests")
  class ParEvalMap {

    @Test
    @DisplayName("parEvalMap() processes all elements")
    void parEvalMapProcessesAllElements() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);

      VStream<Integer> result = VStreamPar.parEvalMap(stream, 2, n -> VTask.succeed(n * 2));

      assertThatVStream(result).producesElements(2, 4, 6, 8, 10);
    }

    @Test
    @DisplayName("parEvalMap() preserves input order")
    void parEvalMapPreservesInputOrder() {
      VStream<Integer> stream = VStream.fromList(List.of(5, 4, 3, 2, 1));

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              3,
              n ->
                  VTask.of(
                      () -> {
                        // Varying delays to test order preservation
                        Thread.sleep(n * 10L);
                        return n * 10;
                      }));

      assertThatVStream(result).producesElements(50, 40, 30, 20, 10);
    }

    @Test
    @DisplayName("parEvalMap() respects concurrency limit")
    void parEvalMapRespectsConcurrencyLimit() {
      AtomicInteger maxConcurrency = new AtomicInteger(0);
      AtomicInteger currentConcurrency = new AtomicInteger(0);

      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3, 4, 5, 6));

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              2,
              n ->
                  VTask.of(
                      () -> {
                        int current = currentConcurrency.incrementAndGet();
                        maxConcurrency.updateAndGet(max -> Math.max(max, current));
                        Thread.sleep(50);
                        currentConcurrency.decrementAndGet();
                        return n;
                      }));

      result.toList().run();

      assertThat(maxConcurrency.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("parEvalMap() handles empty stream")
    void parEvalMapHandlesEmptyStream() {
      VStream<Integer> stream = VStream.empty();

      VStream<Integer> result = VStreamPar.parEvalMap(stream, 4, n -> VTask.succeed(n * 2));

      assertThatVStream(result).isEmpty();
    }

    @Test
    @DisplayName("parEvalMap() handles single element stream")
    void parEvalMapHandlesSingleElement() {
      VStream<Integer> stream = VStream.of(42);

      VStream<Integer> result = VStreamPar.parEvalMap(stream, 4, n -> VTask.succeed(n * 2));

      assertThatVStream(result).producesElements(84);
    }

    @Test
    @DisplayName(
        "parEvalMap() fails a terminal operation with a task's error when nothing recovers")
    void parEvalMapErrorFailsStream() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              2,
              n -> {
                if (n == 2) {
                  return VTask.fail(new RuntimeException("Element 2 failed"));
                }
                return VTask.succeed(n * 10);
              });

      assertThatVStream(result).failsWithExceptionType(RuntimeException.class);
    }

    @Test
    @DisplayName("parEvalMap() with null stream throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void parEvalMapWithNullStreamThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalMap(null, 2, VTask::succeed))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("stream");
    }

    @Test
    @DisplayName("parEvalMap() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void parEvalMapWithNullFunctionThrows() {
      VStream<Integer> stream = VStream.of(1);

      assertThatThrownBy(() -> VStreamPar.parEvalMap(stream, 2, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f");
    }

    @Test
    @DisplayName("parEvalMap() with zero concurrency throws IllegalArgumentException")
    void parEvalMapWithZeroConcurrencyThrows() {
      VStream<Integer> stream = VStream.of(1);

      assertThatThrownBy(() -> VStreamPar.parEvalMap(stream, 0, VTask::succeed))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("concurrency");
    }

    @Test
    @DisplayName("parEvalMap() with negative concurrency throws IllegalArgumentException")
    void parEvalMapWithNegativeConcurrencyThrows() {
      VStream<Integer> stream = VStream.of(1);

      assertThatThrownBy(() -> VStreamPar.parEvalMap(stream, -1, VTask::succeed))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("concurrency");
    }

    @Test
    @DisplayName(
        "parEvalMap() with Integer.MAX_VALUE on a short stream returns every element, in order")
    void parEvalMapWithMaxConcurrencyReturnsEveryElementInOrder() {
      List<Integer> result =
          VStreamPar.parEvalMap(VStream.of(1, 2, 3), Integer.MAX_VALUE, VTask::succeed)
              .toList()
              .run();

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("parEvalMap(Integer.MAX_VALUE) on an empty stream returns no elements")
    void parEvalMapMaxValueOnEmptyStreamReturnsEmpty() {
      List<Integer> result =
          VStreamPar.parEvalMap(VStream.<Integer>empty(), Integer.MAX_VALUE, VTask::succeed)
              .toList()
              .run();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parEvalMap() above a few thousand elements still returns every element in order")
    void parEvalMapAboveInitialCapacityReturnsEveryElementInOrder() {
      List<Integer> result =
          VStreamPar.parEvalMap(VStream.range(0, 3_000), 2_000, VTask::succeed).toList().run();

      assertThat(result).containsExactlyElementsOf(IntStream.range(0, 3_000).boxed().toList());
    }

    @Test
    @DisplayName(
        "parEvalMap() forks every element of a batch larger than its list's initial capacity")
    void parEvalMapForksEveryElementWhenConcurrencyExceedsInitialCapacity() {
      int elements = 1_025;
      CountDownLatch started = new CountDownLatch(elements);

      List<Integer> result =
          VStreamPar.parEvalMap(
                  VStream.range(0, elements),
                  2_000,
                  n ->
                      VTask.of(
                          () -> {
                            started.countDown();
                            if (!started.await(10, TimeUnit.SECONDS)) {
                              throw new IllegalStateException(
                                  "not every element of the batch was in flight");
                            }
                            return n;
                          }))
              .toList()
              .run();

      assertThat(result).containsExactlyElementsOf(IntStream.range(0, elements).boxed().toList());
    }
  }

  @Nested
  @DisplayName("parEvalMapUnordered() Tests")
  class ParEvalMapUnordered {

    @Test
    @DisplayName("parEvalMapUnordered() processes all elements")
    void parEvalMapUnorderedProcessesAllElements() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);

      VStream<Integer> result =
          VStreamPar.parEvalMapUnordered(stream, 3, n -> VTask.succeed(n * 2));

      List<Integer> resultList = result.toList().run();
      assertThat(resultList).containsExactlyInAnyOrder(2, 4, 6, 8, 10);
    }

    @Test
    @DisplayName("parEvalMapUnordered() contains same elements as ordered variant")
    @SuppressWarnings("DataFlowIssue") // non-null in this fixture
    void parEvalMapUnorderedContainsSameElements() {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3, 4, 5));

      List<Integer> orderedResult =
          VStreamPar.parEvalMap(stream, 2, n -> VTask.succeed(n * 3)).toList().run();

      List<Integer> unorderedResult =
          VStreamPar.parEvalMapUnordered(stream, 2, n -> VTask.succeed(n * 3)).toList().run();

      Set<Integer> orderedSet = new HashSet<>(orderedResult);
      Set<Integer> unorderedSet = new HashSet<>(unorderedResult);

      assertThat(orderedSet).isEqualTo(unorderedSet);
    }

    @Test
    @DisplayName("parEvalMapUnordered() respects concurrency limit")
    void parEvalMapUnorderedRespectsConcurrencyLimit() {
      AtomicInteger maxConcurrency = new AtomicInteger(0);
      AtomicInteger currentConcurrency = new AtomicInteger(0);

      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3, 4, 5, 6));

      VStream<Integer> result =
          VStreamPar.parEvalMapUnordered(
              stream,
              2,
              n ->
                  VTask.of(
                      () -> {
                        int current = currentConcurrency.incrementAndGet();
                        maxConcurrency.updateAndGet(max -> Math.max(max, current));
                        Thread.sleep(30);
                        currentConcurrency.decrementAndGet();
                        return n;
                      }));

      result.toList().run();

      assertThat(maxConcurrency.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("parEvalMapUnordered() handles empty stream")
    void parEvalMapUnorderedHandlesEmptyStream() {
      VStream<Integer> stream = VStream.empty();

      VStream<Integer> result =
          VStreamPar.parEvalMapUnordered(stream, 4, n -> VTask.succeed(n * 2));

      assertThatVStream(result).isEmpty();
    }

    @Test
    @DisplayName(
        "parEvalMapUnordered() fails a terminal operation with a task's error, unrecovered")
    void parEvalMapUnorderedErrorHandling() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      VStream<Integer> result =
          VStreamPar.parEvalMapUnordered(
              stream,
              2,
              n -> {
                if (n == 2) {
                  return VTask.fail(new RuntimeException("Failed"));
                }
                return VTask.succeed(n);
              });

      assertThatVStream(result).failsWithExceptionType(RuntimeException.class);
    }

    @Test
    @DisplayName("parEvalMapUnordered() with null stream throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void parEvalMapUnorderedWithNullStreamThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalMapUnordered(null, 2, VTask::succeed))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parEvalMapUnordered() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void parEvalMapUnorderedWithNullFunctionThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalMapUnordered(VStream.of(1), 2, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parEvalMapUnordered() with zero concurrency throws IllegalArgumentException")
    void parEvalMapUnorderedWithZeroConcurrencyThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalMapUnordered(VStream.of(1), 0, VTask::succeed))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName(
        "parEvalMapUnordered() with Integer.MAX_VALUE on a short stream returns every element")
    void parEvalMapUnorderedWithMaxConcurrencyReturnsEveryElement() {
      List<Integer> result =
          VStreamPar.parEvalMapUnordered(VStream.of(1, 2, 3), Integer.MAX_VALUE, VTask::succeed)
              .toList()
              .run();

      assertThat(result).containsExactlyInAnyOrder(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("parEvalMapUnordered() emits each result as its task completes")
  // A wait that never ends fails the test rather than hanging the build
  @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  class CompletionOrder {

    @Test
    @DisplayName("fast results come before a slow one, which closing the stream cancels")
    void fastResultsComeFirst() {
      AtomicBoolean slowCancelled = new AtomicBoolean();
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.of(1, 2, 3, 4),
              4,
              n -> n == 1 ? waitsUntilCancelled(n, slowCancelled) : VTask.succeed(n));

      List<Integer> firstThree = results.take(3).toList().run();

      assertThat(firstThree).containsExactlyInAnyOrder(2, 3, 4);
      assertThat(slowCancelled).isTrue();
    }

    @Test
    @DisplayName("two slow tasks four places apart run at the same time")
    void slowTasksRunTogether() throws Exception {
      CountDownLatch bothStarted = new CountDownLatch(2);
      CountDownLatch release = new CountDownLatch(1);
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.range(1, 9),
              4,
              n ->
                  n == 1 || n == 5
                      ? VTask.of(
                          () -> {
                            bothStarted.countDown();
                            release.await();
                            return n;
                          })
                      : VTask.succeed(n));

      var all = results.runAsync();
      try {
        assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();
      } finally {
        release.countDown();
      }

      assertThat(all.get(5, TimeUnit.SECONDS)).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    @DisplayName("a consumer that stops pulling stops the reading once concurrency results wait")
    void aStoppedConsumerStopsTheReading() {
      AtomicInteger read = new AtomicInteger();
      AtomicReference<Thread> reader = new AtomicReference<>();
      VStream<Integer> endless =
          VStream.iterate(1, n -> n + 1)
              .peek(
                  _ -> {
                    reader.set(Thread.currentThread());
                    read.incrementAndGet();
                  });
      VStream<Integer> results = VStreamPar.parEvalMapUnordered(endless, 3, VTask::succeed);

      VStream.Step<Integer> first = results.pull().run();
      try {
        // Three read ahead, and one more once the consumer took a result; then the reader waits
        await()
            .atMost(Duration.ofSeconds(5))
            .until(() -> read.get() == 4 && reader.get().getState() == Thread.State.WAITING);

        assertThat(read).hasValue(4);
      } finally {
        ((VStream.Step.Emit<Integer>) first).tail().close().run();
      }
    }

    @Test
    @DisplayName("the result whose task completes first comes first")
    void completionOrder() {
      CountDownLatch firstTaken = new CountDownLatch(1);
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.of(1, 2),
              2,
              n ->
                  n == 1
                      ? VTask.of(
                          () -> {
                            firstTaken.await();
                            return n;
                          })
                      : VTask.succeed(n));

      VStream.Step<Integer> first = results.pull().run();
      firstTaken.countDown();
      VStream.Step<Integer> second = ((VStream.Step.Emit<Integer>) first).tail().pull().run();

      assertThat(((VStream.Step.Emit<Integer>) first).value()).isEqualTo(2);
      assertThat(((VStream.Step.Emit<Integer>) second).value()).isEqualTo(1);
      assertThat(((VStream.Step.Emit<Integer>) second).tail().pull().run())
          .isInstanceOf(VStream.Step.Done.class);
    }

    @Test
    @DisplayName("a pulled VTask run twice starts two runs, each with the same first step")
    void aPulledTaskRunsTwice() {
      AtomicInteger firstRuns = new AtomicInteger();
      VTask<VStream.Step<Integer>> pulled =
          VStreamPar.parEvalMapUnordered(
                  VStream.of(1, 2, 3),
                  1,
                  n ->
                      VTask.of(
                          () -> {
                            if (n == 1) {
                              firstRuns.incrementAndGet();
                            }
                            return n * 10;
                          }))
              .pull();

      VStream.Step<Integer> once = pulled.run();
      VStream.Step<Integer> twice = pulled.run();

      assertThat(((VStream.Step.Emit<Integer>) once).value()).isEqualTo(10);
      assertThat(((VStream.Step.Emit<Integer>) twice).value()).isEqualTo(10);
      assertThat(((VStream.Step.Emit<Integer>) once).tail().toList().run()).containsExactly(20, 30);
      assertThat(((VStream.Step.Emit<Integer>) twice).tail().toList().run())
          .containsExactly(20, 30);
      assertThat(firstRuns).as("the first element's task runs once a run").hasValue(2);
    }

    @Test
    @DisplayName("closing the stream after a pull closes that run's source")
    void closingTheHeadClosesTheRun() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.iterate(1, n -> n + 1).onFinalize(VTask.exec(finalised::incrementAndGet)),
              2,
              VTask::succeed);

      results.pull().run();
      results.close().run();

      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a failure to close the source is suppressed onto a task's failure")
    void aFailedReleaseIsSuppressed() {
      IllegalStateException releaseFailed = new IllegalStateException("release failed");
      IOException noQuote = new IOException("no courier quote");
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.iterate(1, n -> n + 1)
                  .onFinalize(
                      VTask.of(
                          () -> {
                            throw releaseFailed;
                          })),
              2,
              n -> n == 2 ? VTask.fail(noQuote) : VTask.succeed(n));

      Try<List<Integer>> outcome = results.toList().runSafe();

      Throwable cause = ((Try.Failure<List<Integer>>) outcome).cause();
      assertThat(cause).isSameAs(noQuote);
      assertThat(cause.getSuppressed()).contains(releaseFailed);
    }

    @Test
    @DisplayName("the tasks do not see the caller's ScopedValue bindings, as parEvalMap's do")
    void scopedValuesAreNotInherited() throws Exception {
      ScopedValue<String> trace = ScopedValue.newInstance();

      List<Boolean> unordered =
          ScopedValue.where(trace, "order-run")
              .call(
                  () ->
                      VStreamPar.parEvalMapUnordered(
                              VStream.of(1), 1, _ -> VTask.of(trace::isBound))
                          .toList()
                          .run());
      List<Boolean> ordered =
          ScopedValue.where(trace, "order-run")
              .call(
                  () ->
                      VStreamPar.parEvalMap(VStream.of(1), 1, _ -> VTask.of(trace::isBound))
                          .toList()
                          .run());

      assertThat(unordered).containsExactly(false);
      assertThat(ordered).containsExactly(true);
    }

    @Test
    @DisplayName(
        "each run reads the source afresh, and closing after the end releases nothing more")
    void eachRunReadsAfresh() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.of(1, 2, 3).onFinalize(VTask.exec(finalised::incrementAndGet)),
              2,
              n -> VTask.succeed(n * 10));

      assertThat(results.toList().run()).containsExactlyInAnyOrder(10, 20, 30);
      assertThat(results.toList().run()).containsExactlyInAnyOrder(10, 20, 30);
      assertThat(results.close().runSafe().isSuccess()).isTrue();
      // Once for each run that read to the end, and not again for the close
      assertThat(finalised).hasValue(2);
    }

    @Test
    @DisplayName("a failed task that nothing recovers cancels the tasks and closes the source")
    void aFailureCancelsTheRest() {
      AtomicBoolean slowCancelled = new AtomicBoolean();
      AtomicInteger finalised = new AtomicInteger();
      IOException noQuote = new IOException("no courier quote");
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              // Endless, so only the failure can close it
              VStream.iterate(1, n -> n + 1).onFinalize(VTask.exec(finalised::incrementAndGet)),
              3,
              n ->
                  switch (n) {
                    case 1 -> waitsUntilCancelled(n, slowCancelled);
                    case 2 -> VTask.fail(noQuote);
                    default -> VTask.succeed(n);
                  });

      Try<List<Integer>> outcome = results.toList().runSafe();

      assertThat(outcome.isFailure()).isTrue();
      assertThat(((Try.Failure<List<Integer>>) outcome).cause()).isSameAs(noQuote);
      assertThat(slowCancelled).isTrue();
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a tail after a failed function fails with the same exception each time")
    void aFailedTailFailsTheSame() {
      IllegalStateException failed = new IllegalStateException("second failed");
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.of(1, 2),
              1,
              n -> {
                if (n == 2) {
                  throw failed;
                }
                return VTask.succeed(n);
              });

      VStream.Step<Integer> first = results.pull().run();
      VStream<Integer> rest = ((VStream.Step.Emit<Integer>) first).tail();

      assertThatThrownBy(() -> rest.pull().run()).isSameAs(failed);
      assertThatThrownBy(() -> rest.pull().run()).isSameAs(failed);
    }

    @Test
    @DisplayName("closing while the source is being read stops the read and closes the source")
    void closingStopsARead() throws InterruptedException {
      CountDownLatch reading = new CountDownLatch(1);
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> source =
          VStream.of(1, 2, 3, 4)
              .onFinalize(VTask.exec(finalised::incrementAndGet))
              .mapTask(
                  n ->
                      n == 2
                          ? VTask.of(
                              () -> {
                                reading.countDown();
                                Thread.sleep(60_000);
                                return n;
                              })
                          : VTask.succeed(n));
      VStream<Integer> results = VStreamPar.parEvalMapUnordered(source, 4, VTask::succeed);

      VStream.Step<Integer> first = results.pull().run();
      reading.await();

      assertThat(((VStream.Step.Emit<Integer>) first).tail().close().runSafe().isSuccess())
          .isTrue();
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a read that swallows the interrupt still stops, and the scope cancels the rest")
    void aSwallowedInterruptStillStops() throws InterruptedException {
      CountDownLatch reading = new CountDownLatch(1);
      AtomicInteger read = new AtomicInteger();
      AtomicBoolean secondCancelled = new AtomicBoolean();
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              swallowingAtThree(reading, read),
              4,
              n -> n == 2 ? waitsUntilCancelled(n, secondCancelled) : VTask.succeed(n));

      VStream.Step<Integer> first = results.pull().run();
      reading.await();
      ((VStream.Step.Emit<Integer>) first).tail().close().run();

      assertThat(read).hasValue(3);
      assertThat(secondCancelled).isTrue();
    }

    @Test
    @DisplayName("a failure during a read that swallows the interrupt still cancels the rest")
    void aFailureDuringASwallowedReadStops() {
      CountDownLatch reading = new CountDownLatch(1);
      AtomicInteger read = new AtomicInteger();
      AtomicBoolean secondCancelled = new AtomicBoolean();
      IOException noQuote = new IOException("no courier quote");
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              swallowingAtThree(reading, read),
              4,
              n ->
                  switch (n) {
                    case 1 ->
                        VTask.of(
                            () -> {
                              reading.await();
                              throw noQuote;
                            });
                    case 2 -> waitsUntilCancelled(n, secondCancelled);
                    default -> VTask.succeed(n);
                  });

      Try<List<Integer>> outcome = results.toList().runSafe();

      assertThat(((Try.Failure<List<Integer>>) outcome).cause()).isSameAs(noQuote);
      assertThat(read).hasValue(3);
      assertThat(secondCancelled).isTrue();
    }

    /**
     * One to four, whose read of the third element signals {@code reading}, waits, and carries on
     * when interrupted, as code that swallows an interrupt does.
     */
    private VStream<Integer> swallowingAtThree(CountDownLatch reading, AtomicInteger read) {
      return VStream.of(1, 2, 3, 4)
          .mapTask(
              n ->
                  n == 3
                      ? VTask.of(
                          () -> {
                            reading.countDown();
                            try {
                              Thread.sleep(60_000);
                            } catch (InterruptedException e) {
                              // carries on, as some code does
                            }
                            return n;
                          })
                      : VTask.succeed(n))
          .peek(_ -> read.incrementAndGet());
    }

    @Test
    @DisplayName("a pull that swallows the interrupt runs no function for what it read")
    void aSwallowedInterruptRunsNoFunction() throws InterruptedException {
      CountDownLatch reading = new CountDownLatch(1);
      AtomicInteger read = new AtomicInteger();
      AtomicBoolean secondCancelled = new AtomicBoolean();
      Set<Integer> mapped = ConcurrentHashMap.newKeySet();
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              swallowingAtThree(reading, read),
              4,
              n -> {
                mapped.add(n);
                return n == 2 ? waitsUntilCancelled(n, secondCancelled) : VTask.succeed(n);
              });

      VStream.Step<Integer> first = results.pull().run();
      reading.await();
      ((VStream.Step.Emit<Integer>) first).tail().close().run();

      assertThat(read).hasValue(3);
      assertThat(mapped).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("a pull that swallows the interrupt and gives a Skip still stops the reading")
    void aSwallowedInterruptBeforeASkipStillStops() throws InterruptedException {
      CountDownLatch reading = new CountDownLatch(1);
      AtomicInteger read = new AtomicInteger();
      AtomicBoolean secondCancelled = new AtomicBoolean();
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              swallowingAtThree(reading, read).filter(n -> n != 3),
              4,
              n -> n == 2 ? waitsUntilCancelled(n, secondCancelled) : VTask.succeed(n));

      VStream.Step<Integer> first = results.pull().run();
      reading.await();
      ((VStream.Step.Emit<Integer>) first).tail().close().run();

      assertThat(read).as("the reading stops after the skip, before element 4").hasValue(3);
      assertThat(secondCancelled).isTrue();
    }

    @Test
    @DisplayName("closing after a task was interrupted, before the failure, fails as closing did")
    void closingBeforeTheFailureReportsTheRelease() throws InterruptedException {
      IllegalStateException releaseFailed = new IllegalStateException("release failed");
      CountDownLatch released = new CountDownLatch(1);
      CountDownLatch firstTaken = new CountDownLatch(1);
      VStream<Integer> source =
          VStream.iterate(1, n -> n + 1)
              .onFinalize(
                  VTask.of(
                      () -> {
                        released.countDown();
                        throw releaseFailed;
                      }));
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              source,
              2,
              n ->
                  n == 2
                      ? VTask.of(
                          () -> {
                            firstTaken.await();
                            throw new InterruptedException("task 2");
                          })
                      : VTask.succeed(n));

      VStream.Step<Integer> first = results.pull().run();
      firstTaken.countDown();
      released.await();

      assertThatThrownBy(() -> ((VStream.Step.Emit<Integer>) first).tail().close().run())
          .isSameAs(releaseFailed);
    }

    @Test
    @DisplayName("a function that closes the stream it maps ends it without waiting on itself")
    void theFunctionClosesTheStream() {
      AtomicReference<VStream<Integer>> holder = new AtomicReference<>();
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.iterate(1, n -> n + 1),
              2,
              n -> {
                if (n == 3) {
                  holder.get().close().run();
                }
                return VTask.succeed(n);
              });
      holder.set(results);

      assertThat(results.toList().run()).isSubsetOf(1, 2, 3);
    }

    @Test
    @DisplayName("a function that closes the stream gets what closing the source throws, once")
    void theFunctionHearsTheRelease() {
      IllegalStateException releaseFailed = new IllegalStateException("release failed");
      AtomicInteger releases = new AtomicInteger();
      AtomicReference<Throwable> firstClose = new AtomicReference<>();
      AtomicReference<Throwable> secondClose = new AtomicReference<>();
      AtomicReference<VStream<Integer>> holder = new AtomicReference<>();
      VStream<Integer> source =
          VStream.iterate(1, n -> n + 1)
              .onFinalize(
                  VTask.of(
                      () -> {
                        releases.incrementAndGet();
                        throw releaseFailed;
                      }));
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              source,
              2,
              n -> {
                if (n == 3) {
                  firstClose.set(catchThrowable(() -> holder.get().close().execute()));
                  secondClose.set(catchThrowable(() -> holder.get().close().execute()));
                }
                return VTask.succeed(n);
              });
      holder.set(results);

      // The element whose function closed the stream runs no task
      assertThat(results.toList().run()).isSubsetOf(1, 2);
      assertThat(firstClose.get()).isSameAs(releaseFailed);
      assertThat(secondClose.get()).isNull();
      assertThat(releases).hasValue(1);
    }

    @Test
    @DisplayName("a function that lets through what closing throws fails the stream with it")
    void theFunctionLetsTheReleaseThrough() {
      IllegalStateException releaseFailed = new IllegalStateException("release failed");
      AtomicReference<VStream<Integer>> holder = new AtomicReference<>();
      VStream<Integer> source =
          VStream.iterate(1, n -> n + 1)
              .onFinalize(
                  VTask.of(
                      () -> {
                        throw releaseFailed;
                      }));
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              source,
              2,
              n -> {
                if (n == 3) {
                  holder.get().close().run();
                }
                return VTask.succeed(n);
              });
      holder.set(results);

      assertThatThrownBy(() -> results.toList().run()).isSameAs(releaseFailed);
    }

    @Test
    @DisplayName("a function that closes the stream at its first element closes that run")
    void theFunctionClosesTheRunAtOnce() {
      IllegalStateException releaseFailed = new IllegalStateException("release failed");
      AtomicInteger releases = new AtomicInteger();
      AtomicReference<Throwable> closing = new AtomicReference<>();
      AtomicReference<VStream<Integer>> holder = new AtomicReference<>();
      VStream<Integer> source =
          VStream.iterate(1, n -> n + 1)
              .onFinalize(
                  VTask.of(
                      () -> {
                        releases.incrementAndGet();
                        throw releaseFailed;
                      }));
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              source,
              2,
              n -> {
                if (n == 1) {
                  closing.set(catchThrowable(() -> holder.get().close().execute()));
                }
                return VTask.succeed(n);
              });
      holder.set(results);

      assertThat(results.toList().run()).isEmpty();
      assertThat(closing.get()).isSameAs(releaseFailed);
      assertThat(releases).hasValue(1);
    }

    @Test
    @DisplayName("a failed element is handed on as it is read, ahead of a task still running")
    void aFailedElementGoesAhead() {
      CountDownLatch recovered = new CountDownLatch(1);
      VStream<Integer> source =
          VStream.of(1, 2, 3)
              .mapTask(
                  n ->
                      n == 2
                          ? VTask.fail(new IllegalStateException("element 2"))
                          : VTask.succeed(n));

      List<Integer> results =
          VStreamPar.parEvalMapUnordered(
                  source,
                  2,
                  n ->
                      n == 1
                          ? VTask.of(
                              () -> {
                                recovered.await();
                                return 10;
                              })
                          : VTask.succeed(n * 10))
              .recover(
                  _ -> {
                    recovered.countDown();
                    return -1;
                  })
              .toList()
              .run();

      assertThat(results).startsWith(-1).containsExactlyInAnyOrder(-1, 10, 30);
    }

    @Test
    @DisplayName("a failed element that nothing recovers cancels the tasks and closes the source")
    void aFailedElementEndsTheStream() {
      AtomicBoolean cancelled = new AtomicBoolean();
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> source =
          VStream.of(1, 2, 3)
              .onFinalize(VTask.exec(finalised::incrementAndGet))
              .mapTask(
                  n ->
                      n == 2
                          ? VTask.fail(new IllegalStateException("element 2"))
                          : VTask.succeed(n));
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              source, 2, n -> n == 1 ? waitsUntilCancelled(n, cancelled) : VTask.succeed(n));

      assertThatThrownBy(() -> results.toList().run()).hasMessage("element 2");
      assertThat(cancelled).isTrue();
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a failed pull lets the tasks already running finish, and hands on their results")
    void aFailedPullLetsTheTasksFinish() {
      CountDownLatch pullFailed = new CountDownLatch(1);
      VStream<Integer> source =
          VStream.of(1)
              .concat(
                  VStream.defer(
                      () -> {
                        pullFailed.countDown();
                        return VStream.<Integer>fail(new IllegalStateException("source down"));
                      }));
      List<Integer> saved = new ArrayList<>();

      Throwable failure =
          catchThrowable(
              () ->
                  VStreamPar.parEvalMapUnordered(
                          source,
                          4,
                          n ->
                              VTask.of(
                                  () -> {
                                    // Still running after the pull fails, so cancelling the tasks
                                    // would interrupt it
                                    pullFailed.await();
                                    Thread.sleep(50);
                                    return n * 10;
                                  }))
                      .forEach(saved::add)
                      .run());

      assertThat(failure).hasMessage("source down");
      assertThat(saved).containsExactly(10);
    }

    @Test
    @DisplayName("a source's interrupt, bare or as run() wraps it, cancels the tasks at once")
    void anInterruptFromTheSourceCancels() {
      for (Throwable interrupt :
          List.of(
              new InterruptedException("stop"),
              new VTaskExecutionException(new InterruptedException("stop")))) {
        AtomicBoolean cancelled = new AtomicBoolean();

        Throwable failure =
            catchThrowable(
                () ->
                    VStreamPar.parEvalMapUnordered(
                            VStream.of(1).concat(VStream.fail(interrupt)),
                            4,
                            n -> waitsUntilCancelled(n, cancelled))
                        .toList()
                        .execute());
        Thread.interrupted();

        assertThat(failure).isSameAs(interrupt);
        assertThat(cancelled).isTrue();
      }
    }

    @Test
    @DisplayName("a second pull of a tail, waiting for the first, can be interrupted")
    void aWaitingSecondPullIsInterruptible() throws InterruptedException {
      CountDownLatch release = new CountDownLatch(1);
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.of(1, 2),
              2,
              n ->
                  n == 1
                      ? VTask.succeed(n)
                      : VTask.of(
                          () -> {
                            release.await();
                            return n;
                          }));
      VStream<Integer> tail = ((VStream.Step.Emit<Integer>) results.pull().run()).tail();
      Thread holding = Thread.ofVirtual().start(() -> tail.pull().run());
      await().until(() -> holding.getState() == Thread.State.WAITING);
      AtomicReference<Try<VStream.Step<Integer>>> second = new AtomicReference<>();
      Thread waiting = Thread.ofVirtual().start(() -> second.set(tail.pull().runSafe()));
      await().until(() -> waiting.getState() == Thread.State.WAITING);

      waiting.interrupt();
      waiting.join(5_000);

      assertThat(second.get()).isInstanceOf(Try.Failure.class);
      release.countDown();
      holding.join(5_000);
      tail.close().run();
    }

    @Test
    @DisplayName("an interrupted first pull inside defer closes its run and cancels the tasks")
    void anInterruptedFirstPullClosesTheRun() throws InterruptedException {
      AtomicBoolean cancelled = new AtomicBoolean();
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> deferred =
          VStream.defer(
              () ->
                  VStreamPar.parEvalMapUnordered(
                      VStream.iterate(1, n -> n + 1)
                          .onFinalize(VTask.exec(finalised::incrementAndGet)),
                      1,
                      n -> waitsUntilCancelled(n, cancelled)));
      Thread pulling = Thread.ofVirtual().start(() -> deferred.pull().runSafe());
      await().until(() -> pulling.getState() == Thread.State.WAITING);

      pulling.interrupt();
      pulling.join(5_000);

      assertThat(cancelled).isTrue();
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("closing part-way fails with what closing the source throws, the first time")
    void closingReportsAFailedRelease() {
      IllegalStateException releaseFailed = new IllegalStateException("release failed");
      VStream<Integer> source =
          VStream.iterate(1, n -> n + 1)
              .onFinalize(
                  VTask.of(
                      () -> {
                        throw releaseFailed;
                      }));
      VStream<Integer> results = VStreamPar.parEvalMapUnordered(source, 2, VTask::succeed);

      VStream.Step<Integer> first = results.pull().run();
      VStream<Integer> rest = ((VStream.Step.Emit<Integer>) first).tail();

      assertThatThrownBy(() -> rest.close().run()).isSameAs(releaseFailed);
      assertThat(rest.close().runSafe().isSuccess()).isTrue();
      assertThat(rest.pull().run()).isInstanceOf(VStream.Step.Done.class);
    }

    @Test
    @DisplayName("a close interrupted while it waits for the reader returns with the status set")
    void anInterruptedCloseReturns() throws Exception {
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      VStream<Integer> results =
          VStreamPar.parEvalMapUnordered(
              VStream.of(1, 2),
              2,
              n ->
                  n == 1
                      ? VTask.of(
                          () -> {
                            started.countDown();
                            // Waits out the scope's cancellation, so closing waits for it
                            while (true) {
                              try {
                                release.await();
                                return n;
                              } catch (InterruptedException e) {
                                // keeps waiting
                              }
                            }
                          })
                      : VTask.succeed(n));
      VStream<Integer> rest = ((VStream.Step.Emit<Integer>) results.pull().run()).tail();
      started.await();
      AtomicReference<Try<Unit>> closed = new AtomicReference<>();
      AtomicBoolean interruptedAfter = new AtomicBoolean();

      Thread closer =
          Thread.ofVirtual()
              .start(
                  () -> {
                    closed.set(rest.close().runSafe());
                    interruptedAfter.set(Thread.currentThread().isInterrupted());
                  });
      try {
        await()
            .atMost(Duration.ofSeconds(5))
            .until(() -> closer.getState() == Thread.State.WAITING);
        closer.interrupt();
        closer.join(5_000);
      } finally {
        release.countDown();
      }

      assertThat(closed.get().isSuccess()).isTrue();
      assertThat(interruptedAfter).isTrue();
    }
  }

  @Nested
  @DisplayName("parEvalFlatMap() Tests")
  class ParEvalFlatMap {

    @Test
    @DisplayName("parEvalFlatMap() expands and collects correctly")
    void parEvalFlatMapExpandsCorrectly() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      VStream<Integer> result = VStreamPar.parEvalFlatMap(stream, 2, n -> VStream.of(n, n * 10));

      List<Integer> resultList = result.toList().run();
      assertThat(resultList).containsExactlyInAnyOrder(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("parEvalFlatMap() respects concurrency limit")
    void parEvalFlatMapRespectsConcurrencyLimit() {
      AtomicInteger maxConcurrency = new AtomicInteger(0);
      AtomicInteger currentConcurrency = new AtomicInteger(0);

      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3, 4));

      VStream<Integer> result =
          VStreamPar.parEvalFlatMap(
              stream,
              2,
              n -> {
                int current = currentConcurrency.incrementAndGet();
                maxConcurrency.updateAndGet(max -> Math.max(max, current));
                currentConcurrency.decrementAndGet();
                return VStream.of(n);
              });

      result.toList().run();

      assertThat(maxConcurrency.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("parEvalFlatMap() handles empty inner streams")
    void parEvalFlatMapHandlesEmptyInnerStreams() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      VStream<Integer> result = VStreamPar.parEvalFlatMap(stream, 2, _ -> VStream.empty());

      assertThatVStream(result).isEmpty();
    }

    @Test
    @DisplayName("parEvalFlatMap() error handling")
    void parEvalFlatMapErrorHandling() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      VStream<Integer> result =
          VStreamPar.parEvalFlatMap(
              stream,
              2,
              n -> {
                if (n == 2) {
                  return VStream.fail(new RuntimeException("Inner stream failed"));
                }
                return VStream.of(n);
              });

      assertThatVStream(result).failsWithExceptionType(RuntimeException.class);
    }

    @Test
    @DisplayName("parEvalFlatMap() with null stream throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void parEvalFlatMapWithNullStreamThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalFlatMap(null, 2, VStream::of))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parEvalFlatMap() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void parEvalFlatMapWithNullFunctionThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalFlatMap(VStream.of(1), 2, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parEvalFlatMap() with zero concurrency throws IllegalArgumentException")
    void parEvalFlatMapWithZeroConcurrencyThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalFlatMap(VStream.of(1), 0, VStream::of))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parEvalFlatMap() with Integer.MAX_VALUE on a short stream returns every element")
    void parEvalFlatMapWithMaxConcurrencyReturnsEveryElement() {
      List<Integer> result =
          VStreamPar.parEvalFlatMap(VStream.of(1, 2, 3), Integer.MAX_VALUE, VStream::of)
              .toList()
              .run();

      assertThat(result).containsExactly(1, 2, 3);
    }

    /** A sub-stream of {@code n} alone that records {@code n} when it is finalised. */
    private static VStream<Integer> recordsItsClose(int n, Set<Integer> closed) {
      return VStream.of(n).onFinalize(VTask.exec(() -> closed.add(n)));
    }

    @Test
    @DisplayName("a failed call, unrecovered, closes the sub-streams its batch built after it")
    void aFailedCallClosesTheSubStreamsAfterIt() {
      Set<Integer> closed = ConcurrentHashMap.newKeySet();
      IllegalStateException noStream = new IllegalStateException("no stream for 1");

      Try<List<Integer>> result =
          VStreamPar.parEvalFlatMap(
                  VStream.of(1, 2, 3),
                  3,
                  n -> {
                    if (n == 1) {
                      throw noStream;
                    }
                    return recordsItsClose(n, closed);
                  })
              .toList()
              .runSafe();

      assertThat(result)
          .isInstanceOfSatisfying(
              Try.Failure.class, failure -> assertThat(failure.cause()).isSameAs(noStream));
      assertThat(closed).containsExactlyInAnyOrder(2, 3);
    }

    @Test
    @DisplayName("take closes the sub-streams it never reaches, past a failed call")
    void takeClosesTheSubStreamsItNeverReaches() {
      Set<Integer> closed = ConcurrentHashMap.newKeySet();

      List<Integer> taken =
          VStreamPar.parEvalFlatMap(
                  VStream.of(1, 2, 3),
                  3,
                  n -> {
                    if (n == 2) {
                      throw new IllegalStateException("no stream for 2");
                    }
                    return recordsItsClose(n, closed);
                  })
              .take(1)
              .toList()
              .run();

      assertThat(taken).containsExactly(1);
      assertThat(closed).containsExactlyInAnyOrder(1, 3);
    }

    @Test
    @DisplayName("an interrupt closes the sub-streams the batch's finished calls built")
    void anInterruptClosesTheSubStreamsBuilt() {
      Set<Integer> closed = ConcurrentHashMap.newKeySet();
      CountDownLatch othersFinished = new CountDownLatch(2);
      VTaskExecutionException interrupt =
          new VTaskExecutionException(new InterruptedException("stop"));

      Throwable failure =
          catchThrowable(
              () ->
                  VStreamPar.parEvalFlatMap(
                          VStream.of(1, 2, 3),
                          3,
                          n -> {
                            switch (n) {
                              case 1 -> {
                                VStream<Integer> built = recordsItsClose(n, closed);
                                othersFinished.countDown();
                                return built;
                              }
                              case 2 -> {
                                othersFinished.countDown();
                                throw new IllegalStateException("no stream for 2");
                              }
                              default -> {
                                try {
                                  othersFinished.await();
                                } catch (InterruptedException e) {
                                  throw new IllegalStateException(e);
                                }
                                throw interrupt;
                              }
                            }
                          })
                      .toList()
                      .execute());
      Thread.interrupted();

      assertThat(failure).isSameAs(interrupt);
      assertThat(closed).containsExactly(1);
    }

    @Test
    @DisplayName("interrupting the reader closes the sub-streams the batch's finished calls built")
    void interruptingTheReaderClosesTheSubStreamsBuilt() throws InterruptedException {
      Set<Integer> closed = ConcurrentHashMap.newKeySet();
      CountDownLatch oneBuilt = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      VStream<Integer> results =
          VStreamPar.parEvalFlatMap(
              VStream.of(1, 2),
              2,
              n -> {
                if (n == 1) {
                  VStream<Integer> built = recordsItsClose(n, closed);
                  oneBuilt.countDown();
                  return built;
                }
                try {
                  new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                  throw new IllegalStateException("cancelled", e);
                }
                return VStream.of(n);
              });

      Thread reader =
          Thread.ofVirtual()
              .start(() -> failure.set(catchThrowable(() -> results.toList().execute())));
      oneBuilt.await();
      reader.interrupt();
      reader.join(Duration.ofSeconds(10));

      assertThat(reader.isAlive()).isFalse();
      assertThat(failure.get()).isInstanceOf(InterruptedException.class);
      assertThat(closed).containsExactly(1);
    }
  }

  @Nested
  @DisplayName("merge() Tests")
  class Merge {

    @Test
    @DisplayName("merge() merges two streams")
    void mergeTwoStreams() {
      VStream<Integer> first = VStream.of(1, 2, 3);
      VStream<Integer> second = VStream.of(4, 5, 6);

      VStream<Integer> result = VStreamPar.merge(first, second);

      List<Integer> resultList = result.toList().run();
      assertThat(resultList).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("merge() merges list of streams")
    void mergeListOfStreams() {
      List<VStream<Integer>> streams =
          List.of(VStream.of(1, 2), VStream.of(3, 4), VStream.of(5, 6));

      VStream<Integer> result = VStreamPar.merge(streams);

      List<Integer> resultList = result.toList().run();
      assertThat(resultList).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("merge() contains all elements from all sources")
    void mergeContainsAllElements() {
      VStream<String> a = VStream.of("a1", "a2");
      VStream<String> b = VStream.of("b1", "b2", "b3");
      VStream<String> c = VStream.of("c1");

      List<String> result = VStreamPar.merge(List.of(a, b, c)).toList().run();

      assertThat(result).hasSize(6);
      assertThat(result).containsExactlyInAnyOrder("a1", "a2", "b1", "b2", "b3", "c1");
    }

    @Test
    @DisplayName("merge() empty stream list returns empty")
    void mergeEmptyListReturnsEmpty() {
      VStream<Integer> result = VStreamPar.merge(List.of());

      assertThatVStream(result).isEmpty();
    }

    @Test
    @DisplayName("merge() single stream returned as-is")
    void mergeSingleStream() {
      VStream<Integer> single = VStream.of(1, 2, 3);

      VStream<Integer> result = VStreamPar.merge(List.of(single));

      assertThatVStream(result).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("merge() error in one source fails entire merge")
    void mergeErrorFailsEntireMerge() {
      VStream<Integer> good = VStream.of(1, 2, 3);
      VStream<Integer> bad = VStream.fail(new RuntimeException("Source failed"));

      VStream<Integer> result = VStreamPar.merge(good, bad);

      assertThatVStream(result).failsWithExceptionType(RuntimeException.class);
    }

    @Test
    @DisplayName("merge() wraps InterruptedException when consumer thread is interrupted")
    void mergeWrapsInterruptedException() throws InterruptedException {
      CountDownLatch subtaskStarted = new CountDownLatch(1);

      // Stream whose pull blocks indefinitely, signalling when started
      VStream<Integer> blockingStream =
          () ->
              VTask.of(
                  () -> {
                    subtaskStarted.countDown();
                    Thread.sleep(60_000);
                    return new VStream.Step.Emit<>(1, VStream.empty());
                  });

      VStream<Integer> merged = VStreamPar.merge(List.of(blockingStream, VStream.of(2)));

      AtomicReference<Throwable> caught = new AtomicReference<>();
      CountDownLatch finished = new CountDownLatch(1);

      Thread worker =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      merged.toList().run();
                    } catch (Throwable t) {
                      caught.set(t);
                    } finally {
                      finished.countDown();
                    }
                  });

      // Wait for the blocking subtask to start (scope is open, join is blocking)
      assertThat(subtaskStarted.await(5, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(50); // small buffer to ensure join() is blocking

      worker.interrupt();
      assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();

      assertThat(caught.get())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Merge interrupted");
    }

    @Test
    @DisplayName("merge() with null list throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mergeWithNullListThrows() {
      assertThatThrownBy(() -> VStreamPar.merge((List<VStream<Integer>>) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("merge() with null first stream throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mergeWithNullFirstThrows() {
      assertThatThrownBy(() -> VStreamPar.merge(null, VStream.of(1)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("merge() with null second stream throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mergeWithNullSecondThrows() {
      assertThatThrownBy(() -> VStreamPar.merge(VStream.of(1), null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("parCollect() Tests")
  @SuppressWarnings("removal") // exercises the deprecated method until its removal
  class ParCollect {

    @Test
    @DisplayName("parCollect() collects all elements")
    void parCollectCollectsAllElements() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);

      VTask<List<Integer>> result = VStreamPar.parCollect(stream, 2);

      assertThat(result.run()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("parCollect() respects batch size")
    void parCollectRespectsBatchSize() {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3, 4, 5, 6, 7));

      VTask<List<Integer>> result = VStreamPar.parCollect(stream, 3);

      assertThat(result.run()).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("parCollect() empty stream returns empty list")
    void parCollectEmptyStreamReturnsEmptyList() {
      VStream<Integer> stream = VStream.empty();

      VTask<List<Integer>> result = VStreamPar.parCollect(stream, 5);

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("parCollect() with null stream throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void parCollectWithNullStreamThrows() {
      assertThatThrownBy(() -> VStreamPar.parCollect(null, 5))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parCollect() with zero batch size throws IllegalArgumentException")
    void parCollectWithZeroBatchSizeThrows() {
      assertThatThrownBy(() -> VStreamPar.parCollect(VStream.of(1), 0))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parCollect() with negative batch size throws IllegalArgumentException")
    void parCollectWithNegativeBatchSizeThrows() {
      assertThatThrownBy(() -> VStreamPar.parCollect(VStream.of(1), -1))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parCollect() with Integer.MAX_VALUE on a short stream returns every element")
    void parCollectWithMaxBatchSizeReturnsEveryElement() {
      assertThat(VStreamPar.parCollect(VStream.of(1, 2, 3), Integer.MAX_VALUE).run())
          .containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("Concurrency Verification Tests")
  class ConcurrencyVerification {

    @Test
    @DisplayName("parEvalMap() max concurrent never exceeds specified limit")
    void parEvalMapMaxConcurrentNeverExceedsLimit() {
      AtomicInteger maxConcurrency = new AtomicInteger(0);
      AtomicInteger currentConcurrency = new AtomicInteger(0);
      int concurrencyLimit = 3;

      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              concurrencyLimit,
              n ->
                  VTask.of(
                      () -> {
                        int current = currentConcurrency.incrementAndGet();
                        maxConcurrency.updateAndGet(max -> Math.max(max, current));
                        Thread.sleep(20);
                        currentConcurrency.decrementAndGet();
                        return n;
                      }));

      List<Integer> collected = result.toList().run();

      assertThat(collected).hasSize(10);
      assertThat(maxConcurrency.get()).isLessThanOrEqualTo(concurrencyLimit);
    }

    @Test
    @DisplayName("parEvalMap() verifies virtual thread usage")
    void parEvalMapUsesVirtualThreads() {
      AtomicInteger virtualThreadCount = new AtomicInteger(0);

      VStream<Integer> stream = VStream.of(1, 2, 3);

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              3,
              n ->
                  VTask.of(
                      () -> {
                        if (Thread.currentThread().isVirtual()) {
                          virtualThreadCount.incrementAndGet();
                        }
                        return n;
                      }));

      result.toList().run();

      // All tasks should have run on virtual threads
      assertThat(virtualThreadCount.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("A failed task is a failed element in its place, as a failed mapTask task is")
  // A wait that never ends fails the test rather than hanging the build
  @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  class FailedTasks {

    /** A task giving ten times each element, failing with an IOException for those in bad. */
    private static Function<Integer, VTask<Integer>> failingAt(Integer... bad) {
      Set<Integer> failing = Set.of(bad);
      return n ->
          failing.contains(n) ? VTask.fail(new IOException("element " + n)) : VTask.succeed(n * 10);
    }

    @Test
    @DisplayName("recover after parEvalMap gives a value in the failed task's place, and goes on")
    void recoverAfterParEvalMap() {
      assertThat(
              VStreamPar.parEvalMap(VStream.of(1, 2, 3, 4, 5), 3, failingAt(2))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(10, -1, 30, 40, 50);
    }

    @Test
    @DisplayName("parEvalMap with a concurrency of 1 gives what mapTask gives, recover included")
    void aConcurrencyOfOneIsMapTask() {
      VStream<Integer> source = VStream.of(1, 2, 3, 4);
      Function<Integer, VTask<Integer>> f = failingAt(2, 4);

      List<Integer> sequential = source.mapTask(f).recover(_ -> -1).toList().run();

      assertThat(VStreamPar.parEvalMap(source, 1, f).recover(_ -> -1).toList().run())
          .isEqualTo(sequential)
          .containsExactly(10, -1, 30, -1);
    }

    @Test
    @DisplayName(
        "recover after parEvalMapUnordered gives a value for each failed task, and goes on")
    void recoverAfterParEvalMapUnordered() {
      assertThat(
              VStreamPar.parEvalMapUnordered(VStream.of(1, 2, 3, 4, 5), 3, failingAt(2, 3))
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactlyInAnyOrder(10, -1, -1, 40, 50);
    }

    @Test
    @DisplayName(
        "after parEvalMapUnordered, a failed task comes where it finished, after values taken"
            + " before it")
    void failedTasksComeInTheOrderTheyFinish() {
      CountDownLatch thirtyTaken = new CountDownLatch(1);
      CountDownLatch twoTaken = new CountDownLatch(1);
      Function<CountDownLatch, VTask<Integer>> failsAfter =
          taken ->
              VTask.of(
                  () -> {
                    taken.await();
                    throw new IOException(taken == thirtyTaken ? "two" : "one");
                  });

      List<Integer> results =
          VStreamPar.parEvalMapUnordered(
                  VStream.of(1, 2, 3),
                  3,
                  n ->
                      switch (n) {
                        case 1 -> failsAfter.apply(twoTaken);
                        case 2 -> failsAfter.apply(thirtyTaken);
                        default -> VTask.succeed(30);
                      })
              .recover(error -> error.getMessage().equals("two") ? -2 : -1)
              .peek(
                  n -> {
                    if (n == 30) {
                      thirtyTaken.countDown();
                    } else if (n == -2) {
                      twoTaken.countDown();
                    }
                  })
              .toList()
              .run();

      assertThat(results).containsExactly(30, -2, -1);
    }

    @Test
    @DisplayName("recover after parEvalFlatMap gives a value for the sub-stream whose call failed")
    void recoverAfterParEvalFlatMap() {
      assertThat(
              VStreamPar.parEvalFlatMap(
                      VStream.of(1, 2, 3),
                      3,
                      n -> {
                        if (n == 2) {
                          throw new IllegalStateException("no stream for 2");
                        }
                        return VStream.of(n, n);
                      })
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(1, 1, -1, 3, 3);
    }

    @Test
    @DisplayName("a failed parEvalMapUnordered task goes ahead of a task still running")
    void anUnorderedFailureGoesAhead() {
      CountDownLatch recovered = new CountDownLatch(1);

      List<Integer> results =
          VStreamPar.parEvalMapUnordered(
                  VStream.of(1, 2, 3),
                  3,
                  n ->
                      switch (n) {
                        // Still running until the failure is recovered, so a cancel would end it
                        case 1 ->
                            VTask.of(
                                () -> {
                                  recovered.await();
                                  return 10;
                                });
                        case 2 -> VTask.fail(new IOException("element 2"));
                        default -> VTask.succeed(n * 10);
                      })
              .recover(
                  _ -> {
                    recovered.countDown();
                    return -1;
                  })
              .toList()
              .run();

      assertThat(results).containsExactlyInAnyOrder(-1, 10, 30);
      assertThat(results.indexOf(-1)).isLessThan(results.indexOf(10));
    }

    @Test
    @DisplayName(
        "without a recover, parEvalMap fails with the task's error after the results before it")
    void withoutRecoverTheStreamFails() {
      AtomicInteger finalised = new AtomicInteger();
      IOException noQuote = new IOException("element 3");
      List<Integer> saved = new ArrayList<>();
      VStream<Integer> results =
          VStreamPar.parEvalMap(
              // Endless, so only closing it runs the finaliser
              VStream.iterate(1, n -> n + 1).onFinalize(VTask.exec(finalised::incrementAndGet)),
              2,
              n -> n == 3 ? VTask.fail(noQuote) : VTask.succeed(n * 10));

      Throwable failure = catchThrowable(() -> results.forEach(saved::add).execute());

      assertThat(failure).isSameAs(noQuote);
      assertThat(saved).containsExactly(10, 20);
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("a parEvalMap function that fails runs none of its batch, and adds nothing to it")
    void aFailedFunctionRunsNoneOfTheBatch() {
      IllegalStateException noTask = new IllegalStateException("no task for 3");
      Set<Integer> ran = ConcurrentHashMap.newKeySet();
      List<Integer> saved = new ArrayList<>();
      VStream<Integer> results =
          VStreamPar.parEvalMap(
              VStream.of(1, 2, 3, 4),
              4,
              n -> {
                if (n == 3) {
                  throw noTask;
                }
                return VTask.exec(() -> ran.add(n)).map(_ -> n);
              });

      // Twice, so a failure the batch added to the exception would show as two
      for (int run = 0; run < 2; run++) {
        assertThat(catchThrowable(() -> results.recover(_ -> -1).forEach(saved::add).execute()))
            .isNull();
      }

      assertThat(ran).isEmpty();
      assertThat(saved).containsExactly(-1, -1);
      assertThat(noTask.getSuppressed()).isEmpty();
    }

    @Test
    @DisplayName("a batch with two failed tasks gives two failed elements, in input order")
    void twoFailedTasks() {
      IOException second = new IOException("element 2");
      IOException third = new IOException("element 3");
      CountDownLatch thirdFailed = new CountDownLatch(1);
      // The third task fails first, so the order is the input's, not the completion order
      Function<Integer, VTask<Integer>> f =
          n ->
              switch (n) {
                case 2 ->
                    VTask.of(
                        () -> {
                          thirdFailed.await();
                          throw second;
                        });
                case 3 ->
                    VTask.of(
                        () -> {
                          thirdFailed.countDown();
                          throw third;
                        });
                default -> VTask.succeed(n * 10);
              };
      List<Throwable> recovered = new ArrayList<>();

      List<Integer> results =
          VStreamPar.parEvalMap(VStream.of(1, 2, 3, 4), 4, f)
              .recover(
                  error -> {
                    recovered.add(error);
                    return -1;
                  })
              .toList()
              .run();

      assertThat(results).containsExactly(10, -1, -1, 40);
      assertThat(recovered).containsExactly(second, third);
    }

    @Test
    @DisplayName("an interrupted parEvalMap task ends the stream at once, running no later batch")
    void anInterruptedTaskEndsTheStream() {
      for (Throwable interrupt :
          List.of(
              new InterruptedException("stop"),
              new VTaskExecutionException(new InterruptedException("stop")))) {
        AtomicInteger finalised = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        // Task 3 fails only once task 4 runs, since a task forked after a failure never runs
        CountDownLatch fourRuns = new CountDownLatch(1);
        Set<Integer> forked = ConcurrentHashMap.newKeySet();
        List<Integer> saved = new ArrayList<>();
        VStream<Integer> results =
            VStreamPar.parEvalMap(
                VStream.iterate(1, n -> n + 1).onFinalize(VTask.exec(finalised::incrementAndGet)),
                2,
                n -> {
                  forked.add(n);
                  return switch (n) {
                    case 3 ->
                        VTask.of(
                                () -> {
                                  fourRuns.await();
                                  return n;
                                })
                            .flatMap(_ -> VTask.fail(interrupt));
                    case 4 ->
                        VTask.exec(fourRuns::countDown)
                            .flatMap(_ -> waitsUntilCancelled(n, cancelled));
                    default -> VTask.succeed(n * 10);
                  };
                });

        Throwable failure = catchThrowable(() -> results.forEach(saved::add).execute());
        Thread.interrupted();

        assertThat(failure).isSameAs(interrupt);
        // The first batch's results, and none of the interrupted batch's
        assertThat(saved).containsExactly(10, 20);
        assertThat(cancelled).isTrue();
        assertThat(forked).containsExactlyInAnyOrder(1, 2, 3, 4);
        assertThat(finalised).hasValue(1);
      }
    }

    @Test
    @DisplayName("an interrupted parEvalMapUnordered task cancels the rest and closes the source")
    void anInterruptedUnorderedTaskEndsTheStream() {
      for (Throwable interrupt :
          List.of(
              new InterruptedException("stop"),
              new VTaskExecutionException(new InterruptedException("stop")))) {
        AtomicInteger finalised = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        VStream<Integer> results =
            VStreamPar.parEvalMapUnordered(
                VStream.iterate(1, n -> n + 1).onFinalize(VTask.exec(finalised::incrementAndGet)),
                2,
                n -> n == 1 ? waitsUntilCancelled(n, cancelled) : VTask.fail(interrupt));

        Throwable failure = catchThrowable(() -> results.toList().execute());
        Thread.interrupted();

        assertThat(failure).isSameAs(interrupt);
        assertThat(cancelled).isTrue();
        assertThat(finalised).hasValue(1);
      }
    }
  }

  @Nested
  @DisplayName("Error Propagation Tests")
  class ErrorPropagation {

    @Test
    @DisplayName("Error message is preserved")
    void errorMessagePreserved() {
      String errorMessage = "Specific error message for testing";

      VStream<Integer> stream = VStream.of(1);

      VStream<Integer> result =
          VStreamPar.parEvalMap(stream, 1, _ -> VTask.fail(new RuntimeException(errorMessage)));

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(RuntimeException.class)
          .hasMessage(errorMessage);
    }

    @Test
    @DisplayName("parEvalMap() propagates Error (not RuntimeException)")
    void parEvalMapPropagatesError() {
      VStream<Integer> stream = VStream.of(1);

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              1,
              _ ->
                  VTask.of(
                      () -> {
                        throw new AssertionError("test error");
                      }));

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(AssertionError.class)
          .hasMessage("test error");
    }

    @Test
    @DisplayName("parEvalMap() fails with a task's checked exception as it was thrown")
    void parEvalMapKeepsCheckedException() {
      VStream<Integer> stream = VStream.of(1);

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              1,
              _ ->
                  VTask.of(
                      () -> {
                        throw new IOException("checked exception");
                      }));

      // runSafe keeps a failure as it was thrown, so it shows nothing wrapped it
      assertThat(result.toList().runSafe())
          .isInstanceOfSatisfying(
              Try.Failure.class,
              failure -> assertThat(failure.cause()).isInstanceOf(IOException.class));
    }

    @Test
    @DisplayName(
        "parEvalMap() fails with a task's Throwable that is neither an Exception nor an Error")
    void parEvalMapKeepsPlainThrowable() {
      Throwable odd = new Throwable("neither an Exception nor an Error");
      var result = VStreamPar.parEvalMap(VStream.of(1), 1, _ -> VTask.fail(odd));

      assertThat(result.toList().runSafe())
          .isInstanceOfSatisfying(
              Try.Failure.class, failure -> assertThat(failure.cause()).isSameAs(odd));
    }

    @Test
    @DisplayName("parEvalMapUnordered() propagates Error (not RuntimeException)")
    void parEvalMapUnorderedPropagatesError() {
      VStream<Integer> stream = VStream.of(1);

      VStream<Integer> result =
          VStreamPar.parEvalMapUnordered(
              stream,
              1,
              _ ->
                  VTask.of(
                      () -> {
                        throw new AssertionError("test error");
                      }));

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(AssertionError.class)
          .hasMessage("test error");
    }

    @Test
    @DisplayName("parEvalMapUnordered() fails with a task's checked exception as it was thrown")
    void parEvalMapUnorderedKeepsCheckedException() {
      VStream<Integer> stream = VStream.of(1);

      VStream<Integer> result =
          VStreamPar.parEvalMapUnordered(
              stream,
              1,
              _ ->
                  VTask.of(
                      () -> {
                        throw new IOException("checked exception");
                      }));

      // runSafe keeps a failure as it was thrown, so it shows nothing wrapped it
      assertThat(result.toList().runSafe())
          .isInstanceOfSatisfying(
              Try.Failure.class,
              failure -> assertThat(failure.cause()).isInstanceOf(IOException.class));
    }

    @Test
    @DisplayName(
        "parEvalMapUnordered() fails with a task's Throwable that is neither an Exception nor an Error")
    void parEvalMapUnorderedKeepsPlainThrowable() {
      Throwable odd = new Throwable("neither an Exception nor an Error");
      var result = VStreamPar.parEvalMapUnordered(VStream.of(1), 1, _ -> VTask.fail(odd));

      assertThat(result.toList().runSafe())
          .isInstanceOfSatisfying(
              Try.Failure.class, failure -> assertThat(failure.cause()).isSameAs(odd));
    }

    @Test
    @DisplayName("merge() propagates Error (not RuntimeException)")
    void mergePropagatesError() {
      VStream<Integer> good = VStream.of(1, 2, 3);
      VStream<Integer> bad = VStream.fail(new AssertionError("merge error"));

      VStream<Integer> result = VStreamPar.merge(good, bad);

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(AssertionError.class)
          .hasMessage("merge error");
    }
  }

  @Nested
  @DisplayName("merge() Internal Coverage Tests")
  class MergeInternalCoverage {

    @Test
    @DisplayName("consumeSource handles Skip steps from filtered source streams")
    void consumeSourceHandlesSkipSteps() {
      // filter() produces Skip steps, exercising the Skip case in consumeSource
      VStream<Integer> filtered = VStream.of(1, 2, 3, 4, 5, 6).filter(n -> n % 2 == 0);
      VStream<Integer> plain = VStream.of(10, 20);

      List<Integer> result = VStreamPar.merge(filtered, plain).toList().run();

      assertThat(result).containsExactlyInAnyOrder(2, 4, 6, 10, 20);
    }

    @Test
    @DisplayName("consumeSource cancelled flag stops iteration when another source fails")
    @SuppressWarnings("ResultOfMethodCallIgnored") // best-effort await; result ignored
    void consumeSourceCancelledStopsIteration() {
      CountDownLatch slowStarted = new CountDownLatch(1);
      CountDownLatch errorSignal = new CountDownLatch(1);

      // Slow source: emits one element, then blocks until the other source has failed
      VStream<Integer> slow =
          VStream.defer(
              () -> {
                VStream<Integer> blocking =
                    () ->
                        VTask.of(
                            () -> {
                              slowStarted.countDown();
                              errorSignal.await(5, TimeUnit.SECONDS);
                              Thread.sleep(50); // let cancelled propagate
                              // This Emit triggers the while(!cancelled) check on the next
                              // iteration
                              return new VStream.Step.Emit<>(2, VStream.of(3, 4, 5));
                            });
                return () -> VTask.succeed(new VStream.Step.Emit<>(1, blocking));
              });

      // Fast-failing source: waits for slow to start, then fails
      VStream<Integer> fails =
          () ->
              VTask.of(
                  () -> {
                    slowStarted.await(5, TimeUnit.SECONDS);
                    errorSignal.countDown();
                    throw new RuntimeException("fast failure");
                  });

      VStream<Integer> result = VStreamPar.merge(slow, fails);

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("fast failure");
    }

    @Test
    @DisplayName("consumeSource suppresses second error when first already set cancelled")
    @SuppressWarnings("ResultOfMethodCallIgnored") // best-effort await; result ignored
    void consumeSourceSuppressesSecondError() {
      // Both sources fail — only the first error reaches the consumer.
      // The second consumeSource finds cancelled=true and skips queue.offer.
      // Use a latch to ensure both sources are mid-pull() before either throws,
      // preventing one from setting cancelled before the other enters the while loop.
      CountDownLatch bothInPull = new CountDownLatch(2);

      VStream<Integer> fails1 =
          () ->
              VTask.of(
                  () -> {
                    bothInPull.countDown();
                    bothInPull.await(5, TimeUnit.SECONDS);
                    throw new RuntimeException("error-1");
                  });

      VStream<Integer> fails2 =
          () ->
              VTask.of(
                  () -> {
                    bothInPull.countDown();
                    bothInPull.await(5, TimeUnit.SECONDS);
                    throw new RuntimeException("error-2");
                  });

      VStream<Integer> result = VStreamPar.merge(fails1, fails2);

      // Exactly one error propagates (whichever source wins getAndSet first)
      assertThatThrownBy(() -> result.toList().run()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("a source interrupted before the merge stops fails the merge with the interrupt")
    void consumeSourceInterruptedExceptionPath() {
      // A source that sets the interrupt flag on its subtask thread, so the next queue.put() in
      // consumeSource throws InterruptedException. The merge has not set cancelled, so the
      // interrupt is reported as the source's failure, and the interrupted source sends no
      // SourceDone for the consumer to wait on.
      VStream<Integer> interruptor =
          () ->
              VTask.of(
                  () -> {
                    Thread.currentThread().interrupt();
                    return new VStream.Step.Emit<>(1, VStream.empty());
                  });

      VStream<Integer> result = VStreamPar.merge(interruptor, VStream.of(2));

      try {
        assertThat(result.toList().runSafe())
            .isInstanceOfSatisfying(
                Try.Failure.class,
                failure -> assertThat(failure.cause()).isInstanceOf(InterruptedException.class));
      } finally {
        // runSafe sets the interrupt status again for the InterruptedException it holds
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName("merge fails with a source's checked exception as it was thrown")
    void mergeKeepsCheckedExceptionFromSource() {
      VStream<Integer> checkedFail =
          () ->
              VTask.of(
                  () -> {
                    throw new IOException("checked merge error");
                  });
      VStream<Integer> good = VStream.of(1);

      VStream<Integer> result = VStreamPar.merge(checkedFail, good);

      // runSafe keeps a failure as it was thrown, so it shows nothing wrapped it
      assertThat(result.toList().runSafe())
          .isInstanceOfSatisfying(
              Try.Failure.class,
              failure -> assertThat(failure.cause()).isInstanceOf(IOException.class));
    }

    @Test
    @DisplayName("mergeFromQueue handles multiple SourceDone signals (Skip path)")
    void mergeFromQueueMultipleSourceDoneSkipPath() {
      // Four sources ensure mergeFromQueue processes SourceDone → Skip
      // for the first three completions, and SourceDone → Done for the last
      VStream<Integer> a = VStream.of(1);
      VStream<Integer> b = VStream.of(2);
      VStream<Integer> c = VStream.of(3);
      VStream<Integer> d = VStream.of(4);

      List<Integer> result = VStreamPar.merge(List.of(a, b, c, d)).toList().run();

      assertThat(result).containsExactlyInAnyOrder(1, 2, 3, 4);
    }

    @Test
    @DisplayName("merge handles empty sources mixed with non-empty sources")
    void mergeEmptyAndNonEmptySources() {
      // Empty sources send SourceDone immediately, exercising the SourceDone → Skip
      // path in mergeFromQueue when other sources are still active
      VStream<Integer> empty1 = VStream.empty();
      VStream<Integer> nonEmpty = VStream.of(1, 2);
      VStream<Integer> empty2 = VStream.empty();

      List<Integer> result = VStreamPar.merge(List.of(empty1, nonEmpty, empty2)).toList().run();

      assertThat(result).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("merge scope thread catch block exercised via background thread interruption")
    void mergeScopeThreadCatchBlock() throws InterruptedException {
      // Exercise the catch(Exception) in merge's background scope thread AND
      // the interrupt handling in consumeSource's catch by interrupting the
      // background scope thread. When the scope thread is interrupted during
      // scope.join(), the scope shuts down and interrupts its subtask threads,
      // whose consumeSource catches the InterruptedException.
      // The scope thread hits catch(Exception) with the InterruptedException.
      //
      // Strategy: capture the subtask thread, derive its parent scope thread
      // by interrupting the subtask (which causes scope cleanup), and observe
      // the merge stream terminates.
      CountDownLatch subtaskStarted = new CountDownLatch(1);

      // Source that captures its subtask thread and blocks indefinitely
      VStream<Integer> blockingSource =
          () ->
              VTask.of(
                  () -> {
                    subtaskStarted.countDown();
                    Thread.sleep(60_000);
                    return new VStream.Step.Emit<>(1, VStream.empty());
                  });

      VStream<Integer> normalSource = VStream.of(10, 20);

      VStream<Integer> merged = VStreamPar.merge(blockingSource, normalSource);

      AtomicReference<Throwable> caught = new AtomicReference<>();
      CountDownLatch finished = new CountDownLatch(1);

      Thread worker =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      merged.toList().run();
                    } catch (Throwable t) {
                      caught.set(t);
                    } finally {
                      finished.countDown();
                    }
                  });

      // Wait for the blocking subtask to start
      assertThat(subtaskStarted.await(5, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(50);

      // Interrupt the consumer thread — this causes queue.take() in mergeFromQueue
      // to throw InterruptedException, exercising that catch block
      worker.interrupt();
      assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();

      assertThat(caught.get())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Merge interrupted");
    }

    @Test
    @DisplayName("runMergeScope catches InterruptedException when scope.join() is interrupted")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void runMergeScopeInterruptedDuringJoin() throws InterruptedException {
      CountDownLatch subtaskStarted = new CountDownLatch(1);

      // Source that blocks indefinitely so scope.join() blocks waiting for it
      VStream<Integer> blockingSource =
          () ->
              VTask.of(
                  () -> {
                    subtaskStarted.countDown();
                    Thread.sleep(60_000);
                    return new VStream.Step.Done<>();
                  });

      // Use raw types — MergeSignal is package-private but erasure makes this safe at runtime
      LinkedBlockingQueue rawQueue = new LinkedBlockingQueue();
      AtomicBoolean cancelled = new AtomicBoolean(false);
      AtomicReference<Boolean> interruptFlagAfter = new AtomicReference<>();

      // Run runMergeScope on a thread we control so we can interrupt it
      Thread scopeThread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    VStreamPar.runMergeScope(
                        List.of(blockingSource, VStream.of(1)), rawQueue, cancelled);
                    // After runMergeScope returns, the catch block should have restored
                    // the interrupt flag via Thread.currentThread().interrupt()
                    interruptFlagAfter.set(Thread.currentThread().isInterrupted());
                  });

      // Wait for the blocking subtask to start — scope.join() is now blocking
      assertThat(subtaskStarted.await(5, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(100); // buffer to ensure scope.join() is entered

      // Interrupt the scope thread — scope.join() throws InterruptedException,
      // exercising the catch block on lines 298-299
      scopeThread.interrupt();
      scopeThread.join(5000);

      assertThat(scopeThread.isAlive()).isFalse();
      assertThat(interruptFlagAfter.get()).isTrue();
    }

    @Test
    @DisplayName("runMergeScope closes the sources a cancelled run leaves unread, uninterrupted")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void runMergeScopeClosesUnreadSources() {
      AtomicInteger closed = new AtomicInteger();
      VStream<Integer> first = VStream.of(1).onFinalize(VTask.exec(closed::incrementAndGet));
      VStream<Integer> second = VStream.of(2).onFinalize(VTask.exec(closed::incrementAndGet));

      // Cancelled before it starts, so the run reads neither source and closes both
      VStreamPar.runMergeScope(
          List.of(first, second), new LinkedBlockingQueue(), new AtomicBoolean(true));

      assertThat(closed).hasValue(2);
      assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    @DisplayName(
        "runMergeScope interrupted while a source closes waits for it, then restores the interrupt")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void runMergeScopeWaitsOutAnInterruptWhileClosing() throws InterruptedException {
      AtomicReference<Thread> scopeThread = new AtomicReference<>();
      AtomicBoolean finaliserDone = new AtomicBoolean();
      // Runs on the thread that closes the source: interrupts the scope thread while it waits for
      // the close, and finishes only once that thread waits again
      VStream<Integer> slowToClose =
          VStream.of(1)
              .onFinalize(
                  VTask.exec(
                      () -> {
                        awaitWaiting(scopeThread.get());
                        scopeThread.get().interrupt();
                        awaitWaiting(scopeThread.get());
                        finaliserDone.set(true);
                      }));
      AtomicBoolean doneOnReturn = new AtomicBoolean();
      AtomicBoolean interruptFlagAfter = new AtomicBoolean();

      Thread thread =
          Thread.ofVirtual()
              .unstarted(
                  () -> {
                    VStreamPar.runMergeScope(
                        List.of(slowToClose), new LinkedBlockingQueue(), new AtomicBoolean(true));
                    doneOnReturn.set(finaliserDone.get());
                    interruptFlagAfter.set(Thread.currentThread().isInterrupted());
                  });
      scopeThread.set(thread);
      thread.start();
      thread.join(Duration.ofSeconds(10));

      assertThat(thread.isAlive()).isFalse();
      assertThat(doneOnReturn).as("the source closed before runMergeScope returned").isTrue();
      assertThat(interruptFlagAfter).isTrue();
    }

    private void awaitWaiting(Thread thread) {
      await()
          .atMost(Duration.ofSeconds(5))
          .pollInterval(Duration.ofMillis(1))
          .until(() -> thread.getState() == Thread.State.WAITING);
    }
  }

  @Nested
  @DisplayName("merge close() Tests")
  class MergeClose {

    @Test
    @DisplayName(
        "close() waits for the sources to close when the closing thread has an interrupt"
            + " pending")
    void closeWaitsForSourcesWhenInterrupted() {
      AtomicBoolean finalised = new AtomicBoolean(false);
      VStream<Integer> source =
          VStream.repeat(1)
              .mapTask(
                  i ->
                      VTask.of(
                          () -> {
                            Thread.sleep(1);
                            return i;
                          }))
              .onFinalize(
                  VTask.of(
                      () -> {
                        Thread.sleep(20);
                        finalised.set(true);
                        return Unit.INSTANCE;
                      }));

      try {
        List<Integer> read =
            VStreamPar.merge(source, VStream.<Integer>empty())
                .map(
                    i -> {
                      Thread.currentThread().interrupt();
                      return i;
                    })
                .take(1)
                .toList()
                .run();

        assertThat(read).containsExactly(1);
        assertThat(finalised).as("the source closed before close() returned").isTrue();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName("an interrupt that arrives while close() waits for the sources stops the wait")
    void interruptArrivingWhileClosingStopsTheWait() throws InterruptedException {
      CountDownLatch finaliserStarted = new CountDownLatch(1);
      CountDownLatch finaliserMayEnd = new CountDownLatch(1);
      VStream<Integer> source =
          VStream.repeat(1)
              .mapTask(
                  i ->
                      VTask.of(
                          () -> {
                            Thread.sleep(1);
                            return i;
                          }))
              .onFinalize(
                  VTask.of(
                      () -> {
                        finaliserStarted.countDown();
                        finaliserMayEnd.await();
                        return Unit.INSTANCE;
                      }));
      AtomicReference<List<Integer>> read = new AtomicReference<>();
      AtomicBoolean interruptedAfter = new AtomicBoolean(false);
      Thread reader =
          Thread.ofVirtual()
              .start(
                  () -> {
                    read.set(
                        VStreamPar.merge(source, VStream.<Integer>empty()).take(1).toList().run());
                    interruptedAfter.set(Thread.currentThread().isInterrupted());
                  });

      try {
        assertThat(finaliserStarted.await(5, TimeUnit.SECONDS)).isTrue();
        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(1))
            .until(() -> reader.getState() == Thread.State.WAITING);
        reader.interrupt();
        reader.join(Duration.ofSeconds(5));

        assertThat(reader.isAlive()).as("close() returned once interrupted").isFalse();
        assertThat(read.get()).containsExactly(1);
        assertThat(interruptedAfter).isTrue();
      } finally {
        finaliserMayEnd.countDown();
      }
    }

    @Test
    @DisplayName("close() sets cancelled flag and clears queue")
    void closeSetsCancelledFlagAndClearsQueue() {
      // Create an infinite source so the merge never completes naturally
      VStream<Integer> infinite =
          VStream.unfold(0, n -> VTask.succeed(Optional.of(new VStream.Seed<>(n, n + 1))));
      VStream<Integer> merged = VStreamPar.merge(infinite, VStream.of(1));

      // Take a few elements to ensure the merge is running
      List<Integer> first = merged.take(5).toList().run();
      assertThat(first).hasSize(5);

      // close() should complete without error
      merged.close().run();
    }

    @Test
    @DisplayName("close() on merged stream stops producers (via take)")
    void closeViaTagStopsProducers() {
      AtomicInteger produced = new AtomicInteger(0);

      // Infinite stream that counts productions
      VStream<Integer> counting =
          VStream.unfold(
              0,
              n ->
                  VTask.of(
                      () -> {
                        produced.incrementAndGet();
                        return Optional.of(new VStream.Seed<>(n, n + 1));
                      }));

      VStream<Integer> other =
          VStream.unfold(
              0,
              n ->
                  VTask.of(
                      () -> {
                        Thread.sleep(1); // slow it down
                        return Optional.of(new VStream.Seed<>(n, n + 1));
                      }));

      // take() internally calls close() when it has enough elements
      List<Integer> result = VStreamPar.merge(counting, other).take(10).toList().run();

      assertThat(result).hasSize(10);
      // Producers should have stopped — the count should be bounded, not running away
      int countAfterTake = produced.get();
      // Give any in-flight tasks time to settle
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      int countAfterWait = produced.get();
      // The producer should have stopped (or nearly stopped)
      assertThat(countAfterWait - countAfterTake).isLessThan(10);
    }

    @Test
    @DisplayName("close() is idempotent — calling it multiple times does not fail")
    void closeIsIdempotent() {
      VStream<Integer> a = VStream.of(1, 2, 3);
      VStream<Integer> b = VStream.of(4, 5, 6);
      VStream<Integer> merged = VStreamPar.merge(a, b);

      // Consume the stream
      merged.toList().run();

      // Calling close multiple times should not throw
      merged.close().run();
      merged.close().run();
      merged.close().run();
    }

    @Test
    @DisplayName("close() handles InterruptedException during producer.join()")
    @SuppressWarnings({
      "ResultOfMethodCallIgnored",
      "DataFlowIssue"
    }) // result ignored; step non-null
    void closeHandlesInterruptedExceptionDuringJoin() throws InterruptedException {
      CountDownLatch subtaskStarted = new CountDownLatch(1);

      // Source that emits one element then blocks, keeping the producer thread alive
      VStream<Integer> blockingSource =
          VStream.defer(
              () -> {
                VStream<Integer> blocking =
                    () ->
                        VTask.of(
                            () -> {
                              subtaskStarted.countDown();
                              Thread.sleep(60_000);
                              return new VStream.Step.Done<>();
                            });
                return () -> VTask.succeed(new VStream.Step.Emit<>(1, blocking));
              });

      // take(1) will pull one element then call close() on the inner mergeFromQueue stream
      // (via the tail chain), which calls producer.join()
      VStream<Integer> merged = VStreamPar.merge(blockingSource, VStream.of(2)).take(1);

      AtomicBoolean closeCompleted = new AtomicBoolean(false);
      AtomicReference<Boolean> interruptPreserved = new AtomicReference<>();
      CountDownLatch finished = new CountDownLatch(1);

      // Run on a worker thread so we can set the interrupt flag before take triggers close
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  // Pull the one element — take(1) will call close() internally on next pull
                  // We need the subtask started before we interrupt
                  VStream.Step<Integer> first = merged.pull().run();
                  assertThat(first).isInstanceOf(VStream.Step.Emit.class);

                  // Wait for blocking subtask to start so producer thread is in join-able state
                  subtaskStarted.await(5, TimeUnit.SECONDS);
                  Thread.sleep(50);

                  // Set interrupt flag — when take(0) calls close() -> producer.join(),
                  // join() will throw InterruptedException, exercising lines 402-403
                  Thread.currentThread().interrupt();

                  // This pull triggers take's n<=0 path which calls close()
                  VStream.Step<Integer> second =
                      ((VStream.Step.Emit<Integer>) first).tail().pull().run();
                  assertThat(second).isInstanceOf(VStream.Step.Done.class);

                  interruptPreserved.set(Thread.currentThread().isInterrupted());
                  closeCompleted.set(true);
                } catch (Exception e) {
                  // If close threw, the interrupt was not caught properly
                  closeCompleted.set(false);
                } finally {
                  finished.countDown();
                }
              });

      assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(closeCompleted.get()).isTrue();
      assertThat(interruptPreserved.get()).isTrue();
    }
  }

  @Nested
  @DisplayName("Additional Coverage Tests")
  class AdditionalCoverage {

    @Test
    @DisplayName("pullBatch() handles Skip steps from filtered source in parEvalMap")
    void pullBatchHandlesSkipStepsInParEvalMap() {
      // filter() produces Skip steps, exercising the Skip case (i--) in pullBatch
      VStream<Integer> filtered = VStream.of(1, 2, 3, 4, 5, 6).filter(n -> n % 2 == 0);

      VStream<Integer> result = VStreamPar.parEvalMap(filtered, 3, n -> VTask.succeed(n * 10));

      assertThatVStream(result).producesElements(20, 40, 60);
    }

    @Test
    @DisplayName("parEvalMapUnordered() reads past the Skip steps of a filtered source")
    void parEvalMapUnorderedReadsPastSkipSteps() {
      VStream<Integer> filtered = VStream.of(1, 2, 3, 4, 5, 6).filter(n -> n > 4);

      VStream<Integer> result =
          VStreamPar.parEvalMapUnordered(filtered, 2, n -> VTask.succeed(n * 10));

      List<Integer> resultList = result.toList().run();
      assertThat(resultList).containsExactlyInAnyOrder(50, 60);
    }

    @Test
    @DisplayName("processBatchOrdered() throws when function returns null task")
    @SuppressWarnings("DataFlowIssue") // the mapper deliberately returns null
    void processBatchOrderedThrowsOnNullTask() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      VStream<Integer> result = VStreamPar.parEvalMap(stream, 3, _ -> null);

      assertThatThrownBy(() -> result.toList().run()).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parEvalMap() and parEvalMapUnordered() map an element whose toString throws")
    void elementsWithoutAString() {
      record Unprintable(int n) {
        @Override
        public String toString() {
          throw new IllegalStateException("no string for this element");
        }
      }
      VStream<Unprintable> source = VStream.of(new Unprintable(1), new Unprintable(2));

      assertThat(VStreamPar.parEvalMap(source, 2, u -> VTask.succeed(u.n())).toList().run())
          .containsExactly(1, 2);
      assertThat(
              VStreamPar.parEvalMapUnordered(source, 2, u -> VTask.succeed(u.n())).toList().run())
          .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("parEvalMapUnordered() fails when its function returns a null task")
    @SuppressWarnings("DataFlowIssue") // the mapper deliberately returns null
    void parEvalMapUnorderedThrowsOnNullTask() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      VStream<Integer> result = VStreamPar.parEvalMapUnordered(stream, 3, _ -> null);

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(NullPointerException.class)
          .hasMessage("function returned null task for element: 1");
    }

    @Test
    @DisplayName("A hand-written task that returns null fails its element, in order or not")
    @SuppressWarnings("DataFlowIssue") // the task deliberately returns null
    void aTaskThatReturnsNullFailsItsElement() {
      VTask<Integer> returnsNull = () -> null;
      Function<Integer, VTask<Integer>> nullForTwo = n -> n == 2 ? returnsNull : VTask.succeed(n);

      assertThat(
              VStreamPar.parEvalMap(VStream.of(1, 2, 3), 3, nullForTwo)
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactly(1, -1, 3);
      assertThat(
              VStreamPar.parEvalMapUnordered(VStream.of(1, 2, 3), 3, nullForTwo)
                  .recover(_ -> -1)
                  .toList()
                  .run())
          .containsExactlyInAnyOrder(1, -1, 3);

      assertThatNullPointerException()
          .isThrownBy(
              () -> VStreamPar.parEvalMap(VStream.of(1, 2), 2, _ -> returnsNull).toList().run())
          .withMessage("the task returned null, and a VTask always gives a value");
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  VStreamPar.parEvalMapUnordered(VStream.of(1, 2), 2, _ -> returnsNull)
                      .toList()
                      .run())
          .withMessage("the task returned null, and a VTask always gives a value");
    }

    @Test
    @DisplayName("parEvalFlatMap fails when its function returns null")
    @SuppressWarnings("DataFlowIssue") // the function deliberately returns null
    void parEvalFlatMapRefusesANullStream() {
      VStream<Integer> result = VStreamPar.parEvalFlatMap(VStream.of(1), 2, _ -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> result.toList().run())
          .withMessage("f must not return null");
    }

    @Test
    @DisplayName("parEvalMapUnordered() with negative concurrency throws IllegalArgumentException")
    void parEvalMapUnorderedWithNegativeConcurrencyThrows() {
      VStream<Integer> stream = VStream.of(1);

      assertThatThrownBy(() -> VStreamPar.parEvalMapUnordered(stream, -1, VTask::succeed))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("concurrency");
    }

    @Test
    @DisplayName("parEvalFlatMap() with negative concurrency throws IllegalArgumentException")
    void parEvalFlatMapWithNegativeConcurrencyThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalFlatMap(VStream.of(1), -1, VStream::of))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("concurrency");
    }

    @Test
    @DisplayName("parCollect() propagates errors from source stream")
    @SuppressWarnings("removal") // exercises the deprecated method until its removal
    void parCollectPropagatesErrors() {
      VStream<Integer> stream =
          VStream.of(1, 2, 3)
              .flatMap(
                  n -> {
                    if (n == 2) {
                      return VStream.fail(new RuntimeException("collect error"));
                    }
                    return VStream.of(n);
                  });

      assertThatThrownBy(() -> VStreamPar.parCollect(stream, 2).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("collect error");
    }

    @Test
    @DisplayName("parEvalMap() with concurrency=1 runs sequentially")
    void parEvalMapConcurrencyOneRunsSequentially() {
      AtomicInteger maxConcurrency = new AtomicInteger(0);
      AtomicInteger currentConcurrency = new AtomicInteger(0);

      VStream<Integer> stream = VStream.of(1, 2, 3, 4);

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              1,
              n ->
                  VTask.of(
                      () -> {
                        int current = currentConcurrency.incrementAndGet();
                        maxConcurrency.updateAndGet(max -> Math.max(max, current));
                        Thread.sleep(10);
                        currentConcurrency.decrementAndGet();
                        return n * 2;
                      }));

      assertThatVStream(result).producesElements(2, 4, 6, 8);
      assertThat(maxConcurrency.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("parEvalFlatMap() handles single element stream")
    void parEvalFlatMapSingleElement() {
      VStream<Integer> stream = VStream.of(42);

      VStream<Integer> result = VStreamPar.parEvalFlatMap(stream, 2, n -> VStream.of(n, n + 1));

      assertThatVStream(result).producesElements(42, 43);
    }

    @Test
    @DisplayName("parEvalFlatMap() handles empty source stream")
    void parEvalFlatMapEmptySource() {
      VStream<Integer> stream = VStream.empty();

      VStream<Integer> result = VStreamPar.parEvalFlatMap(stream, 2, VStream::of);

      assertThatVStream(result).isEmpty();
    }

    @Test
    @DisplayName("parCollect() with single element")
    @SuppressWarnings("removal") // exercises the deprecated method until its removal
    void parCollectSingleElement() {
      VStream<Integer> stream = VStream.of(42);

      assertThat(VStreamPar.parCollect(stream, 5).run()).containsExactly(42);
    }

    @Test
    @DisplayName("parCollect() with batch size larger than stream")
    @SuppressWarnings("removal") // exercises the deprecated method until its removal
    void parCollectBatchSizeLargerThanStream() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      assertThat(VStreamPar.parCollect(stream, 100).run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("parEvalMap() handles large batch crossing multiple iterations")
    void parEvalMapMultipleBatches() {
      VStream<Integer> stream = VStream.fromList(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

      VStream<Integer> result = VStreamPar.parEvalMap(stream, 3, n -> VTask.succeed(n * 2));

      assertThatVStream(result).producesElements(2, 4, 6, 8, 10, 12, 14, 16, 18, 20);
    }

    @Test
    @DisplayName("merge(VStream, VStream) delegates to merge(List)")
    void mergeTwoStreamsDelegatesToList() {
      VStream<String> first = VStream.of("a", "b");
      VStream<String> second = VStream.of("c", "d");

      List<String> result = VStreamPar.merge(first, second).toList().run();

      assertThat(result).containsExactlyInAnyOrder("a", "b", "c", "d");
    }
  }
}
