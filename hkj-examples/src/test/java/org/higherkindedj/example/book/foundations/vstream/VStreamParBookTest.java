// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.FLAT_RATE;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.ORDERS;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.PATCHY_COURIER;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.customer;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.orders;
import static org.higherkindedj.example.book.foundations.vstream.VStreamParBook.WAREHOUSE;
import static org.higherkindedj.example.book.foundations.vstream.VStreamParBook.ZONED_COURIER;
import static org.higherkindedj.example.book.foundations.vstream.VStreamParBook.paidOrders;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.PagedFeed;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Holds the claims the VStream Parallel Operations page makes. */
@DisplayName("VStream: parallel operations")
// A wait that never ends fails the test rather than hanging the build
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class VStreamParBookTest {

  private static final Duration POLL = Duration.ofMillis(5);
  private static final Duration PATIENCE = Duration.ofSeconds(5);

  /** A quote that waits until {@code release} opens, or until it is cancelled. */
  private static VTask<BigDecimal> heldQuote(
      Order order, CountDownLatch release, AtomicBoolean cancelled) {
    return VTask.of(
        () -> {
          try {
            release.await();
          } catch (InterruptedException e) {
            cancelled.set(true);
            throw e;
          }
          return ZONED_COURIER.quote(order);
        });
  }

  private static boolean isAda(Order order) {
    return customer(order).equals("Ada");
  }

  private static boolean isKen(Order order) {
    return customer(order).equals("Ken");
  }

  /** An endless stream that counts how many of its elements have been read. */
  private static VStream<Integer> counted(AtomicInteger read) {
    return VStream.iterate(1, n -> n + 1).peek(_ -> read.incrementAndGet());
  }

  @Nested
  @DisplayName("parEvalMap")
  class InOrder {

    @Test
    @DisplayName("gives the quotes in the orders' order, though Ada's finishes last")
    void inOrder() {
      // Ada's quote waits for the other three, which all run at once with a concurrency of 4
      CountDownLatch othersQuoted = new CountDownLatch(3);
      List<BigDecimal> quotes =
          VStreamPar.parEvalMap(
                  paidOrders(),
                  4,
                  order ->
                      VTask.of(
                          () -> {
                            BigDecimal quote = ZONED_COURIER.quote(order);
                            if (isAda(order)) {
                              othersQuoted.await(5, TimeUnit.SECONDS);
                            } else {
                              othersQuoted.countDown();
                            }
                            return quote;
                          }))
              .toList()
              .run();

      assertThat(quotes)
          .extracting(BigDecimal::toPlainString)
          .containsExactly("4.99", "6.49", "3.99", "5.49");
    }

    @Test
    @DisplayName("runs the tasks of a batch at once, each on a virtual thread")
    void runsABatchAtOnce() throws Exception {
      CountDownLatch allStarted = new CountDownLatch(4);
      CompletableFuture<List<Boolean>> together =
          VStreamPar.parEvalMap(
                  paidOrders(),
                  4,
                  order ->
                      VTask.of(
                          () -> {
                            allStarted.countDown();
                            return allStarted.await(5, TimeUnit.SECONDS)
                                && Thread.currentThread().isVirtual();
                          }))
              .toList()
              .runAsync();

      assertThat(together.get(5, TimeUnit.SECONDS)).containsExactly(true, true, true, true);
    }

    @Test
    @DisplayName("gives nothing from a batch until its slowest task finishes")
    void waitsForTheSlowest() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      AtomicInteger finished = new AtomicInteger();
      VStream<BigDecimal> quotes =
          VStreamPar.parEvalMap(
              paidOrders(),
              4,
              order ->
                  isKen(order)
                      ? heldQuote(order, release, new AtomicBoolean())
                      : VTask.of(
                          () -> {
                            BigDecimal quote = ZONED_COURIER.quote(order);
                            finished.incrementAndGet();
                            return quote;
                          }));

      CompletableFuture<VStream.Step<BigDecimal>> first = quotes.pull().runAsync();
      try {
        await().pollInterval(POLL).atMost(PATIENCE).until(() -> finished.get() == 3);
        // Ada's quote is ready, but the batch waits for Ken's
        assertThat(first).isNotDone();
      } finally {
        release.countDown();
      }

      VStream.Step<BigDecimal> step = first.get(5, TimeUnit.SECONDS);
      assertThat(step).isInstanceOf(VStream.Step.Emit.class);
      assertThat(((VStream.Step.Emit<BigDecimal>) step).value()).isEqualByComparingTo("4.99");
    }

    @Test
    @DisplayName("answers four pulls from the batch, a Skip to the next batch, then Done")
    void pullTrace() {
      List<String> steps = new ArrayList<>();
      VStream<BigDecimal> current =
          VStreamPar.parEvalMap(
              paidOrders(), 4, order -> VTask.of(() -> ZONED_COURIER.quote(order)));

      boolean done = false;
      while (!done) {
        switch (current.pull().run()) {
          case VStream.Step.Emit<BigDecimal> emit -> {
            steps.add("Emit " + emit.value());
            current = emit.tail();
          }
          case VStream.Step.Skip<BigDecimal> skip -> {
            steps.add("Skip");
            current = skip.tail();
          }
          case VStream.Step.Done<BigDecimal> _ -> {
            steps.add("Done");
            done = true;
          }
          case VStream.Step.Fail<BigDecimal> failed -> throw new AssertionError(failed.error());
        }
      }

      assertThat(steps)
          .containsExactly("Emit 4.99", "Emit 6.49", "Emit 3.99", "Emit 5.49", "Skip", "Done");
    }

    @Test
    @DisplayName("reads the source on the thread that pulls it, a batch at a time")
    void readsABatchOnTheCallersThread() {
      AtomicInteger read = new AtomicInteger();
      List<Thread> readers = new CopyOnWriteArrayList<>();
      VStream<Integer> endless = counted(read).peek(_ -> readers.add(Thread.currentThread()));

      VStreamPar.parEvalMap(endless, 3, VTask::succeed).headOption().run();

      assertThat(read).hasValue(3);
      assertThat(readers).containsOnly(Thread.currentThread());
    }

    @Test
    @DisplayName("a concurrency of one reads and runs one element at a time, on a virtual thread")
    void concurrencyOne() {
      AtomicInteger read = new AtomicInteger();

      List<Boolean> virtual =
          VStreamPar.parEvalMap(
                  VStream.of(1, 2), 1, _ -> VTask.of(() -> Thread.currentThread().isVirtual()))
              .toList()
              .run();
      VStreamPar.parEvalMap(counted(read), 1, VTask::succeed).headOption().run();

      assertThat(virtual).containsExactly(true, true);
      assertThat(read).hasValue(1);
    }

    @Test
    @DisplayName("a concurrency larger than the stream reads it all as one batch")
    void aLargeConcurrency() {
      List<Integer> all =
          VStreamPar.parEvalMap(VStream.range(1, 6), Integer.MAX_VALUE, VTask::succeed)
              .toList()
              .run();

      assertThat(all).containsExactly(1, 2, 3, 4, 5);
    }
  }

  @Nested
  @DisplayName("parEvalMapUnordered")
  class AsTheyArrive {

    @Test
    @DisplayName("gives each quote as it arrives, so a slow one holds back only itself")
    void aSlowQuoteComesLast() {
      CountDownLatch never = new CountDownLatch(1);
      AtomicBoolean adaCancelled = new AtomicBoolean();

      List<String> firstThree =
          VStreamPar.parEvalMapUnordered(
                  paidOrders(),
                  4,
                  order ->
                      isAda(order)
                          ? heldQuote(order, never, adaCancelled).map(_ -> customer(order))
                          : VTask.succeed(customer(order)))
              .take(3)
              .toList()
              .run();

      assertThat(firstThree).containsExactlyInAnyOrder("Alan", "Edsger", "Ken");
      assertThat(adaCancelled).isTrue();
    }

    @Test
    @DisplayName("gives the same quotes as parEvalMap")
    void sameElements() {
      List<String> arrived =
          VStreamPar.parEvalMapUnordered(
                  paidOrders(),
                  4,
                  order -> VTask.of(() -> customer(order) + " " + ZONED_COURIER.quote(order)))
              .toList()
              .run();

      assertThat(arrived)
          .containsExactlyInAnyOrder("Ada 4.99", "Alan 6.49", "Edsger 3.99", "Ken 5.49");
    }

    @Test
    @DisplayName("reads the source on a virtual thread of its own")
    void readsOnItsOwnThread() {
      AtomicReference<Thread> reader = new AtomicReference<>();

      VStreamPar.parEvalMapUnordered(
              VStream.of(1).peek(_ -> reader.set(Thread.currentThread())), 2, VTask::succeed)
          .toList()
          .run();

      assertThat(reader.get()).isNotSameAs(Thread.currentThread());
      assertThat(reader.get().isVirtual()).isTrue();
    }

    @Test
    @DisplayName("reads ahead until concurrency results wait to be pulled")
    void readsAheadUpToItsConcurrency() {
      AtomicInteger read = new AtomicInteger();
      AtomicReference<Thread> reader = new AtomicReference<>();
      VStream<Integer> arriving =
          VStreamPar.parEvalMapUnordered(
              counted(read).peek(_ -> reader.set(Thread.currentThread())), 4, VTask::succeed);

      VStream.Step<Integer> first = arriving.pull().run();
      try {
        // One result taken and four waiting, so five read, and the reader blocks
        await()
            .pollInterval(POLL)
            .atMost(PATIENCE)
            .until(
                () ->
                    reader.get() != null
                        && reader.get().getState() == Thread.State.WAITING
                        && read.get() == 5);
      } finally {
        arriving.close().run();
      }

      assertThat(first).isInstanceOf(VStream.Step.Emit.class);
    }
  }

  @Nested
  @DisplayName("ScopedValue bindings")
  class Bindings {

    private static final ScopedValue<String> CALLER = ScopedValue.newInstance();

    private static <T> T boundToAda(ScopedValue.CallableOp<T, RuntimeException> work) {
      return ScopedValue.where(CALLER, "Ada").call(work);
    }

    @Test
    @DisplayName("parEvalMap's tasks see the caller's; parEvalMapUnordered's and merge's do not")
    void whoSeesThem() {
      List<Boolean> inOrder =
          boundToAda(
              () ->
                  VStreamPar.parEvalMap(VStream.of(1), 2, _ -> VTask.of(CALLER::isBound))
                      .toList()
                      .run());
      List<Boolean> asTheyArrive =
          boundToAda(
              () ->
                  VStreamPar.parEvalMapUnordered(VStream.of(1), 2, _ -> VTask.of(CALLER::isBound))
                      .toList()
                      .run());
      List<Boolean> merged =
          boundToAda(
              () ->
                  VStreamPar.merge(VStream.of(1).map(_ -> CALLER.isBound()), VStream.empty())
                      .toList()
                      .run());

      assertThat(inOrder).containsExactly(true);
      assertThat(asTheyArrive).containsExactly(false);
      assertThat(merged).containsExactly(false);
    }
  }

  @Nested
  @DisplayName("chunk, chunkWhile and mapChunked")
  class Chunks {

    @Test
    @DisplayName("chunk gives batches of up to its size, the last one shorter")
    void chunk() {
      List<Integer> sizes = VStream.range(1, 6).chunk(2).map(batch -> batch.size()).toList().run();
      List<Integer> oneChunk =
          VStream.range(1, 6).chunk(Integer.MAX_VALUE).map(batch -> batch.size()).toList().run();

      assertThat(sizes).containsExactly(2, 2, 1);
      assertThat(oneChunk).containsExactly(5);
      assertThat(VStream.<Integer>empty().chunk(2).toList().run()).isEmpty();
    }

    @Test
    @DisplayName("chunk reads one batch ahead of the consumer")
    void readsOneBatch() {
      AtomicInteger read = new AtomicInteger();

      counted(read).chunk(3).headOption().run();

      assertThat(read).hasValue(3);
    }

    @Test
    @DisplayName("chunkWhile starts a batch where the test fails between neighbours")
    void chunkWhile() {
      List<List<String>> byStatus =
          orders(new PagedFeed(ORDERS))
              .chunkWhile((earlier, later) -> earlier.status() == later.status())
              .map(batch -> batch.map(VStreamBook::customer).toJavaList())
              .toList()
              .run();

      assertThat(byStatus)
          .containsExactly(
              List.of("Ada"),
              List.of("Grace"),
              List.of("Alan", "Edsger"),
              List.of("Barbara"),
              List.of("Ken"));
    }

    @Test
    @DisplayName("a chunk of orders packed in one warehouse call, two calls at once")
    void packed() {
      AtomicInteger calls = new AtomicInteger();
      CountDownLatch bothCalled = new CountDownLatch(2);

      List<List<String>> packed =
          VStreamPar.parEvalMap(
                  paidOrders().chunk(2),
                  2,
                  batch ->
                      VTask.of(
                          () -> {
                            calls.incrementAndGet();
                            bothCalled.countDown();
                            if (!bothCalled.await(5, TimeUnit.SECONDS)) {
                              throw new IllegalStateException("the two calls did not overlap");
                            }
                            return WAREHOUSE.pack(batch.toJavaList());
                          }))
              .toList()
              .run();

      assertThat(packed)
          .containsExactly(
              List.of("box for Ada", "box for Alan"), List.of("box for Edsger", "box for Ken"));
      assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("mapChunked flattens what its function gives for each chunk")
    void mapChunkedFlattens() {
      List<String> sizes =
          paidOrders().mapChunked(3, batch -> List.of("a batch of " + batch.size())).toList().run();

      assertThat(sizes).containsExactly("a batch of 3", "a batch of 1");
    }
  }

  @Nested
  @DisplayName("merge")
  class Merge {

    @Test
    @DisplayName("gives every element of both streams")
    void everyElement() {
      VStream<String> london =
          orders(new PagedFeed(ORDERS.subList(0, 3))).map(VStreamBook::customer);
      VStream<String> edinburgh =
          orders(new PagedFeed(ORDERS.subList(3, 6))).map(VStreamBook::customer);

      assertThat(VStreamPar.merge(london, edinburgh).toList().run())
          .containsExactlyInAnyOrder("Ada", "Grace", "Alan", "Edsger", "Barbara", "Ken");
    }

    @Test
    @DisplayName("gives each element as it arrives, and stopping early interrupts a source")
    void eachAsItArrives() {
      CountDownLatch never = new CountDownLatch(1);
      AtomicBoolean adaCancelled = new AtomicBoolean();
      VStream<String> slow =
          VStream.of(ORDERS.getFirst())
              .mapTask(order -> heldQuote(order, never, adaCancelled).map(_ -> "Ada"));

      assertThat(VStreamPar.merge(slow, VStream.of("Ken")).headOption().run()).contains("Ken");
      assertThat(adaCancelled).isTrue();
    }

    @Test
    @DisplayName("stopping early runs every source's finalisers")
    void stoppingEarlyClosesTheSources() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> first =
          VStream.iterate(1, n -> n + 1).onFinalize(VTask.exec(finalised::incrementAndGet));
      VStream<Integer> second =
          VStream.iterate(1, n -> n + 1).onFinalize(VTask.exec(finalised::incrementAndGet));

      VStreamPar.merge(first, second).take(1).toList().run();

      assertThat(finalised).hasValue(2);
    }

    @Test
    @DisplayName("a failing source stops the merge, and the other source's finalisers run")
    void aFailingSourceStopsTheMerge() {
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> failing =
          VStream.of(1)
              .map(
                  _ -> {
                    throw new IllegalStateException("the feed went down");
                  });
      VStream<Integer> endless =
          VStream.iterate(1, n -> n + 1).onFinalize(VTask.exec(finalised::incrementAndGet));

      Try<List<Integer>> merged = VStreamPar.merge(failing, endless).toList().runSafe();

      assertThatTry(merged).isFailure();
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName("reads its sources ahead of the consumer, with no limit")
    void readsAheadWithoutALimit() {
      AtomicInteger read = new AtomicInteger();
      VStream<Integer> tenThousand = VStream.range(0, 10_000).peek(_ -> read.incrementAndGet());
      VStream<Integer> merged = VStreamPar.merge(tenThousand, VStream.empty());

      // One pull starts the merge; closing its head stops the run it started
      merged.pull().run();
      try {
        await().pollInterval(POLL).atMost(PATIENCE).until(() -> read.get() == 10_000);
      } finally {
        merged.close().run();
      }
    }

    @Test
    @DisplayName("a merge of one stream is that stream")
    void mergeOfOne() {
      VStream<String> london = VStream.of("Ada", "Alan");

      assertThat(VStreamPar.merge(List.of(london))).isSameAs(london);
    }
  }

  @Nested
  @DisplayName("a failed quote")
  class Failures {

    private VStream<BigDecimal> patchy() {
      return VStreamPar.parEvalMap(
          paidOrders(), 4, order -> VTask.of(() -> PATCHY_COURIER.quote(order)));
    }

    /** The paid orders' quotes, with Edsger's a failed element. */
    private VStream<BigDecimal> quotedFirst() {
      return paidOrders().mapTask(order -> VTask.of(() -> PATCHY_COURIER.quote(order)));
    }

    @Test
    @DisplayName("fails the stream with the courier's own exception")
    void failsWithTheException() {
      Try<List<BigDecimal>> held = patchy().toList().runSafe();

      assertThatTry(held)
          .isFailure()
          .hasExceptionSatisfying(
              error ->
                  assertThat(error)
                      .isInstanceOf(IOException.class)
                      .hasMessage("no courier serves Edsger's address"));
    }

    @Test
    @DisplayName("cancels the other tasks still running")
    void cancelsTheRest() {
      AtomicBoolean adaCancelled = new AtomicBoolean();

      Try<List<BigDecimal>> held =
          VStreamPar.parEvalMap(
                  paidOrders(),
                  4,
                  order ->
                      isAda(order)
                          ? heldQuote(order, new CountDownLatch(1), adaCancelled)
                          : VTask.of(() -> PATCHY_COURIER.quote(order)))
              .toList()
              .runSafe();

      assertThatTry(held).isFailure();
      assertThat(adaCancelled).isTrue();
    }

    @Test
    @DisplayName("recover after parEvalMap or parEvalMapUnordered ends the stream")
    void recoverAfterEnds() {
      AtomicInteger read = new AtomicInteger();

      List<BigDecimal> inOrder = patchy().recover(error -> FLAT_RATE).toList().run();
      // An endless source ends only because the recover ends the stream
      List<Integer> asTheyArrive =
          VStreamPar.parEvalMapUnordered(
                  counted(read),
                  2,
                  n -> n == 3 ? VTask.fail(new IOException("no quote for 3")) : VTask.succeed(n))
              .recover(error -> -1)
              .toList()
              .run();

      assertThat(inOrder).containsExactly(FLAT_RATE);
      assertThat(asTheyArrive).endsWith(-1);
    }

    @Test
    @DisplayName("recover inside each task keeps every other quote")
    void recoverInsideKeepsTheRest() {
      List<BigDecimal> quotes =
          VStreamPar.parEvalMap(
                  paidOrders(),
                  4,
                  order -> VTask.of(() -> PATCHY_COURIER.quote(order)).recover(error -> FLAT_RATE))
              .toList()
              .run();

      assertThat(quotes)
          .extracting(BigDecimal::toPlainString)
          .containsExactly("4.99", "4.99", "9.99", "4.99");
    }

    @Test
    @DisplayName("an element failed further up is handed on, so recover after carries on")
    void anEarlierFailureIsHandedOn() {
      List<BigDecimal> inOrder =
          VStreamPar.parEvalMap(quotedFirst(), 4, VTask::succeed)
              .recover(error -> FLAT_RATE)
              .toList()
              .run();
      List<BigDecimal> asTheyArrive =
          VStreamPar.parEvalMapUnordered(quotedFirst(), 4, VTask::succeed)
              .recover(error -> FLAT_RATE)
              .toList()
              .run();
      List<BigDecimal> flatMapped =
          VStreamPar.parEvalFlatMap(quotedFirst(), 4, quote -> VStream.of(quote))
              .recover(error -> FLAT_RATE)
              .toList()
              .run();

      assertThat(inOrder)
          .extracting(BigDecimal::toPlainString)
          .containsExactly("4.99", "4.99", "9.99", "4.99");
      assertThat(asTheyArrive)
          .extracting(BigDecimal::toPlainString)
          .containsExactlyInAnyOrder("4.99", "4.99", "9.99", "4.99");
      assertThat(flatMapped)
          .extracting(BigDecimal::toPlainString)
          .containsExactly("4.99", "4.99", "9.99", "4.99");
    }

    @Test
    @DisplayName("a merge of several streams, or a map that throws further up, ends there")
    void otherFailuresEnd() {
      VStream<Order> unlabelled =
          paidOrders()
              .map(
                  order -> {
                    if (customer(order).equals("Edsger")) {
                      throw new IllegalStateException("no label for Edsger");
                    }
                    return order;
                  });

      List<BigDecimal> merged =
          VStreamPar.merge(quotedFirst(), VStream.empty())
              .recover(error -> FLAT_RATE)
              .toList()
              .run();
      List<BigDecimal> afterAMap =
          VStreamPar.parEvalMap(unlabelled, 4, order -> VTask.of(() -> ZONED_COURIER.quote(order)))
              .recover(error -> FLAT_RATE)
              .toList()
              .run();

      assertThat(merged)
          .extracting(BigDecimal::toPlainString)
          .containsExactly("4.99", "4.99", "9.99");
      assertThat(afterAMap)
          .extracting(BigDecimal::toPlainString)
          .containsExactly("4.99", "6.49", "9.99");
    }
  }
}
