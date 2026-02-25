// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.vstream.VStreamAssert.assertThatVStream;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VStreamPar Parallel Combinators Test Suite")
class VStreamParTest {

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
    @DisplayName("parEvalMap() error in one element fails entire stream")
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
    void parEvalMapWithNullStreamThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalMap(null, 2, n -> VTask.succeed(n)))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("stream");
    }

    @Test
    @DisplayName("parEvalMap() with null function throws NullPointerException")
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

      assertThatThrownBy(() -> VStreamPar.parEvalMap(stream, 0, n -> VTask.succeed(n)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("concurrency");
    }

    @Test
    @DisplayName("parEvalMap() with negative concurrency throws IllegalArgumentException")
    void parEvalMapWithNegativeConcurrencyThrows() {
      VStream<Integer> stream = VStream.of(1);

      assertThatThrownBy(() -> VStreamPar.parEvalMap(stream, -1, n -> VTask.succeed(n)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("concurrency");
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
    @DisplayName("parEvalMapUnordered() error handling")
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
    void parEvalMapUnorderedWithNullStreamThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalMapUnordered(null, 2, n -> VTask.succeed(n)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parEvalMapUnordered() with null function throws NullPointerException")
    void parEvalMapUnorderedWithNullFunctionThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalMapUnordered(VStream.of(1), 2, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parEvalMapUnordered() with zero concurrency throws IllegalArgumentException")
    void parEvalMapUnorderedWithZeroConcurrencyThrows() {
      assertThatThrownBy(
              () -> VStreamPar.parEvalMapUnordered(VStream.of(1), 0, n -> VTask.succeed(n)))
          .isInstanceOf(IllegalArgumentException.class);
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

      VStream<Integer> result = VStreamPar.parEvalFlatMap(stream, 2, n -> VStream.empty());

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
    void parEvalFlatMapWithNullStreamThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalFlatMap(null, 2, n -> VStream.of(n)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parEvalFlatMap() with null function throws NullPointerException")
    void parEvalFlatMapWithNullFunctionThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalFlatMap(VStream.of(1), 2, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parEvalFlatMap() with zero concurrency throws IllegalArgumentException")
    void parEvalFlatMapWithZeroConcurrencyThrows() {
      assertThatThrownBy(() -> VStreamPar.parEvalFlatMap(VStream.of(1), 0, n -> VStream.of(n)))
          .isInstanceOf(IllegalArgumentException.class);
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
    void mergeWithNullListThrows() {
      assertThatThrownBy(() -> VStreamPar.merge((List<VStream<Integer>>) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("merge() with null first stream throws NullPointerException")
    void mergeWithNullFirstThrows() {
      assertThatThrownBy(() -> VStreamPar.merge(null, VStream.of(1)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("merge() with null second stream throws NullPointerException")
    void mergeWithNullSecondThrows() {
      assertThatThrownBy(() -> VStreamPar.merge(VStream.of(1), null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("parCollect() Tests")
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
  @DisplayName("Error Propagation Tests")
  class ErrorPropagation {

    @Test
    @DisplayName("Error in any element cancels remaining")
    void errorCancelsRemaining() {
      AtomicInteger completedCount = new AtomicInteger(0);

      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              5,
              n ->
                  VTask.of(
                      () -> {
                        if (n == 1) {
                          throw new RuntimeException("First element failed");
                        }
                        Thread.sleep(100);
                        completedCount.incrementAndGet();
                        return n;
                      }));

      assertThatVStream(result).failsWithExceptionType(RuntimeException.class);
    }

    @Test
    @DisplayName("Error message is preserved")
    void errorMessagePreserved() {
      String errorMessage = "Specific error message for testing";

      VStream<Integer> stream = VStream.of(1);

      VStream<Integer> result =
          VStreamPar.parEvalMap(stream, 1, n -> VTask.fail(new RuntimeException(errorMessage)));

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
              n ->
                  VTask.of(
                      () -> {
                        throw new AssertionError("test error");
                      }));

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(AssertionError.class)
          .hasMessage("test error");
    }

    @Test
    @DisplayName("parEvalMap() wraps checked exception in RuntimeException")
    void parEvalMapWrapsCheckedException() {
      VStream<Integer> stream = VStream.of(1);

      VStream<Integer> result =
          VStreamPar.parEvalMap(
              stream,
              1,
              n ->
                  VTask.of(
                      () -> {
                        throw new java.io.IOException("checked exception");
                      }));

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(java.io.IOException.class);
    }

    @Test
    @DisplayName("parEvalMapUnordered() propagates Error (not RuntimeException)")
    void parEvalMapUnorderedPropagatesError() {
      VStream<Integer> stream = VStream.of(1);

      VStream<Integer> result =
          VStreamPar.parEvalMapUnordered(
              stream,
              1,
              n ->
                  VTask.of(
                      () -> {
                        throw new AssertionError("test error");
                      }));

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(AssertionError.class)
          .hasMessage("test error");
    }

    @Test
    @DisplayName("parEvalMapUnordered() wraps checked exception in RuntimeException")
    void parEvalMapUnorderedWrapsCheckedException() {
      VStream<Integer> stream = VStream.of(1);

      VStream<Integer> result =
          VStreamPar.parEvalMapUnordered(
              stream,
              1,
              n ->
                  VTask.of(
                      () -> {
                        throw new java.io.IOException("checked exception");
                      }));

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(java.io.IOException.class);
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
    void consumeSourceCancelledStopsIteration() throws InterruptedException {
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
    void consumeSourceSuppressesSecondError() {
      // Both sources fail — only the first error reaches the consumer.
      // The second consumeSource finds cancelled=true and skips queue.put.
      VStream<Integer> fails1 = VStream.fail(new RuntimeException("error-1"));
      VStream<Integer> fails2 = VStream.fail(new RuntimeException("error-2"));

      VStream<Integer> result = VStreamPar.merge(fails1, fails2);

      // Exactly one error propagates (whichever source is consumed first)
      assertThatThrownBy(() -> result.toList().run()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("consumeSource InterruptedException path via thread interrupt flag")
    void consumeSourceInterruptedExceptionPath() throws InterruptedException {
      // A source that sets the interrupt flag on its subtask thread.
      // The next queue.put() in consumeSource will throw InterruptedException
      // because lockInterruptibly() checks the flag before acquiring.
      // consumeSource catches it, restores the flag, and returns null.
      //
      // Because the interrupted source never sends SourceDone, the consumer
      // would block on queue.take(). We use a second, normally-failing source
      // that runs after a small delay so the consumer sees SourceError and exits.
      VStream<Integer> interruptor =
          () ->
              VTask.of(
                  () -> {
                    Thread.currentThread().interrupt();
                    // Returning Emit triggers queue.put() in consumeSource,
                    // which throws InterruptedException due to the set flag.
                    return new VStream.Step.Emit<>(1, VStream.empty());
                  });

      VStream<Integer> failAfterDelay =
          () ->
              VTask.of(
                  () -> {
                    Thread.sleep(100); // give interruptor time to trigger IE
                    throw new RuntimeException("delayed failure");
                  });

      VStream<Integer> result = VStreamPar.merge(interruptor, failAfterDelay);

      assertThatThrownBy(() -> result.toList().run()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("merge wraps checked exception from source in RuntimeException")
    void mergeWrapsCheckedExceptionFromSource() {
      VStream<Integer> checkedFail =
          () ->
              VTask.of(
                  () -> {
                    throw new java.io.IOException("checked merge error");
                  });
      VStream<Integer> good = VStream.of(1);

      VStream<Integer> result = VStreamPar.merge(checkedFail, good);

      assertThatThrownBy(() -> result.toList().run())
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(java.io.IOException.class);
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
    @DisplayName("merge handles null element values through the merge queue")
    void mergeHandlesNullElementValues() {
      // VStream.Step.Emit allows @Nullable value; exercises MergeSignal.Element(@Nullable)
      VStream<String> withNull =
          VStream.defer(
              () -> {
                VStream<String> tail = VStream.of("b");
                return () -> VTask.succeed(new VStream.Step.Emit<>(null, tail));
              });
      VStream<String> plain = VStream.of("c");

      List<String> result = VStreamPar.merge(withNull, plain).toList().run();

      assertThat(result).containsExactlyInAnyOrder(null, "b", "c");
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
      // the catch(InterruptedException) in consumeSource by interrupting the
      // background scope thread. When the scope thread is interrupted during
      // scope.join(), the scope shuts down and interrupts its subtask threads.
      // The subtask threads' consumeSource hits catch(InterruptedException).
      // The scope thread hits catch(Exception) with the InterruptedException.
      //
      // Strategy: capture the subtask thread, derive its parent scope thread
      // by interrupting the subtask (which causes scope cleanup), and observe
      // the merge stream terminates.
      CountDownLatch subtaskStarted = new CountDownLatch(1);
      AtomicReference<Thread> subtaskThread = new AtomicReference<>();

      // Source that captures its subtask thread and blocks indefinitely
      VStream<Integer> blockingSource =
          () ->
              VTask.of(
                  () -> {
                    subtaskThread.set(Thread.currentThread());
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
  }
}
