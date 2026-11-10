// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vstream.VStreamReactive;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Holds the claims the VStream Performance page makes about where each cost falls. The costs
 * themselves are measured by {@code VStreamBenchmark}, not here.
 */
@DisplayName("VStream: performance")
// A wait that never ends fails the test rather than hanging the build
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class VStreamPerformanceBookTest {

  /** Notes the thread an element is seen on, and passes the element through. */
  private static int seenOn(List<Thread> threads, int n) {
    threads.add(Thread.currentThread());
    return n;
  }

  @Nested
  @DisplayName("what a pull costs")
  class WhatAPullCosts {

    @Test
    @DisplayName("building a pipeline runs none of its functions")
    void buildingRunsNothing() {
      AtomicInteger called = new AtomicInteger();

      VStream.range(0, 100)
          .map(n -> called.incrementAndGet())
          .filter(n -> called.incrementAndGet() > 0)
          .mapTask(n -> VTask.of(called::incrementAndGet))
          .flatMap(n -> VStream.of(called.incrementAndGet()));

      assertThat(called).hasValue(0);
    }
  }

  @Nested
  @DisplayName("against a Java Stream")
  class AgainstAJavaStream {

    @Test
    @DisplayName("a stream read from a Java Stream is used up by its first run")
    void aJavaStreamIsUsedUp() {
      VStream<Integer> fromStream = VStream.fromStream(Stream.of(1, 2, 3));

      assertThat(fromStream.toList().run()).containsExactly(1, 2, 3);
      assertThat(fromStream.toList().run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("where threads start")
  class WhereThreadsStart {

    @Test
    @DisplayName("map, filter, flatMap, mapTask, chunk and take run on the terminal's own thread")
    void pullsRunOnTheCallersThread() {
      List<Thread> threads = new CopyOnWriteArrayList<>();

      VStream.range(0, 10)
          .map(n -> seenOn(threads, n))
          .filter(n -> seenOn(threads, n) >= 0)
          .flatMap(n -> VStream.of(seenOn(threads, n)))
          .mapTask(n -> VTask.of(() -> seenOn(threads, n)))
          .chunk(5)
          .peek(batch -> seenOn(threads, batch.size()))
          .take(1)
          .toList()
          .run();

      assertThat(threads).isNotEmpty().containsOnly(Thread.currentThread());
    }

    @Test
    @DisplayName("runAsync runs the whole run on one virtual thread")
    void runAsyncUsesOneThread() throws Exception {
      List<Thread> threads = new CopyOnWriteArrayList<>();

      VStream.range(0, 10)
          .map(n -> seenOn(threads, n))
          .toList()
          .runAsync()
          .get(5, TimeUnit.SECONDS);

      assertThat(threads.stream().distinct()).hasSize(1);
      assertThat(threads.getFirst().isVirtual()).isTrue();
    }

    @Test
    @DisplayName("parEvalMap runs each task on a virtual thread of its own")
    void parEvalMapStartsAThreadForEachTask() {
      List<Thread> threads = new CopyOnWriteArrayList<>();

      VStreamPar.parEvalMap(VStream.range(0, 8), 4, n -> VTask.of(() -> seenOn(threads, n)))
          .toList()
          .run();

      assertThat(threads).hasSize(8).allMatch(Thread::isVirtual);
      assertThat(threads.stream().distinct()).hasSize(8);
    }

    @Test
    @DisplayName("parEvalFlatMap builds each stream on a task's thread, and reads them all here")
    void parEvalFlatMapReadsHere() {
      List<Thread> builders = new CopyOnWriteArrayList<>();
      List<Thread> readers = new CopyOnWriteArrayList<>();

      VStreamPar.parEvalFlatMap(
              VStream.range(0, 4),
              2,
              n -> {
                seenOn(builders, n);
                return VStream.of(n).peek(element -> seenOn(readers, element));
              })
          .toList()
          .run();

      assertThat(builders).hasSize(4).allMatch(Thread::isVirtual);
      assertThat(readers).hasSize(4).containsOnly(Thread.currentThread());
    }

    @Test
    @DisplayName(
        "parEvalMapUnordered reads on one thread of its own, and runs each task on another")
    void unorderedReadsOnItsOwnThread() {
      List<Thread> readers = new CopyOnWriteArrayList<>();
      List<Thread> tasks = new CopyOnWriteArrayList<>();

      VStreamPar.parEvalMapUnordered(
              VStream.range(0, 4).peek(n -> seenOn(readers, n)),
              2,
              n -> VTask.of(() -> seenOn(tasks, n)))
          .toList()
          .run();

      assertThat(readers.stream().distinct()).hasSize(1);
      assertThat(readers.getFirst().isVirtual()).isTrue();
      assertThat(tasks).hasSize(4).allMatch(Thread::isVirtual).doesNotContain(readers.getFirst());
      assertThat(tasks.stream().distinct()).hasSize(4);
    }

    @Test
    @DisplayName("merge of two streams reads each on a virtual thread of its own")
    void mergeReadsEachSourceOnItsOwnThread() {
      List<Thread> first = new CopyOnWriteArrayList<>();
      List<Thread> second = new CopyOnWriteArrayList<>();

      VStreamPar.merge(
              VStream.range(0, 3).peek(n -> seenOn(first, n)),
              VStream.range(0, 3).peek(n -> seenOn(second, n)))
          .toList()
          .run();

      assertThat(first).hasSize(3).allMatch(Thread::isVirtual);
      assertThat(second).hasSize(3).allMatch(Thread::isVirtual);
      assertThat(first.getFirst()).isNotSameAs(second.getFirst());
    }
  }

  @Nested
  @DisplayName("where threads start, beyond the reading")
  class BeyondTheReading {

    @Test
    @DisplayName("merge closes the sources it stopped reading on a thread of its own")
    void mergeClosesOnItsOwnThread() {
      List<Thread> closedOn = new CopyOnWriteArrayList<>();
      VStream<Integer> endless =
          VStream.iterate(1, n -> n + 1)
              .onFinalize(VTask.exec(() -> closedOn.add(Thread.currentThread())));

      VStreamPar.merge(endless, VStream.of(0)).take(1).toList().run();

      assertThat(closedOn).hasSize(1);
      assertThat(closedOn.getFirst().isVirtual()).isTrue();
      assertThat(closedOn.getFirst()).isNotSameAs(Thread.currentThread());
    }

    @Test
    @DisplayName("toPublisher pulls for its subscriber on a virtual thread")
    void toPublisherPullsOnAVirtualThread() throws InterruptedException {
      List<Thread> pulledOn = new CopyOnWriteArrayList<>();
      CountDownLatch done = new CountDownLatch(1);
      Flow.Publisher<Integer> publisher =
          VStreamReactive.toPublisher(VStream.range(0, 3).peek(n -> seenOn(pulledOn, n)));

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
              done.countDown();
            }

            @Override
            public void onComplete() {
              done.countDown();
            }
          });

      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(pulledOn).hasSize(3).allMatch(Thread::isVirtual);
      assertThat(pulledOn).doesNotContain(Thread.currentThread());
    }
  }

  @Nested
  @DisplayName("what chunking saves")
  class WhatChunkingSaves {

    @Test
    @DisplayName("chunk still pulls the source once for every element")
    void chunkPullsEveryElement() {
      AtomicInteger pulled = new AtomicInteger();

      List<Integer> sizes =
          VStream.range(0, 100)
              .peek(_ -> pulled.incrementAndGet())
              .chunk(10)
              .map(batch -> batch.size())
              .toList()
              .run();

      assertThat(sizes).hasSize(10).containsOnly(10);
      assertThat(pulled).hasValue(100);
    }

    @Test
    @DisplayName("chunk then parEvalMap runs one task, on one thread, for each batch")
    void oneTaskForEachBatch() {
      List<Thread> threads = new CopyOnWriteArrayList<>();

      List<Integer> sizes =
          VStreamPar.parEvalMap(
                  VStream.range(0, 100).chunk(10),
                  4,
                  batch -> VTask.of(() -> seenOn(threads, batch.size())))
              .toList()
              .run();

      assertThat(sizes).hasSize(10).containsOnly(10);
      assertThat(threads).hasSize(10);
      assertThat(threads.stream().distinct()).hasSize(10);
    }
  }
}
