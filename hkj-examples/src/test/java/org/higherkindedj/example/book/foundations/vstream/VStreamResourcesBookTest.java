// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.FLAT_RATE;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.ORDERS;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.PATCHY_COURIER;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.orders;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.shippingQuotes;
import static org.higherkindedj.example.book.foundations.vstream.VStreamResourcesBook.ordersOver;
import static org.higherkindedj.example.book.foundations.vstream.VStreamResourcesBook.quotesOver;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.PagedFeed;
import org.higherkindedj.example.book.foundations.vstream.VStreamResourcesBook.LoggedCourierService;
import org.higherkindedj.example.book.foundations.vstream.VStreamResourcesBook.LoggedFeedService;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Holds the claims the VStream Resource-Safe Streaming page makes. */
@DisplayName("VStream: resource-safe streaming")
// A wait that never ends fails the test rather than hanging the build
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class VStreamResourcesBookTest {

  private final List<String> log = new CopyOnWriteArrayList<>();

  private LoggedFeedService feed() {
    return new LoggedFeedService(log);
  }

  private LoggedFeedService feed(PagedFeed paged) {
    return new LoggedFeedService(log, paged);
  }

  /** The rest of the stream after a step that has one. */
  private static <A> VStream<A> tail(VStream.Step<A> step) {
    return switch (step) {
      case VStream.Step.Emit<A> emit -> emit.tail();
      case VStream.Step.Skip<A> skip -> skip.tail();
      case VStream.Step.Fail<A> failed -> failed.tail();
      case VStream.Step.Done<A> _ -> throw new AssertionError("the stream has ended");
    };
  }

  /** A stream of three that records the thread its release runs on. */
  private static VStream<Integer> recordingRelease(AtomicReference<Thread> releasedOn) {
    return VStream.bracket(
        VTask.succeed("resource"),
        _ -> VStream.of(1, 2, 3),
        _ -> VTask.exec(() -> releasedOn.set(Thread.currentThread())));
  }

  @Nested
  @DisplayName("bracket opens and closes")
  class OpensAndCloses {

    @Test
    @DisplayName("opens nothing until the stream runs")
    void opensNothingUntilRun() {
      ordersOver(feed()).filter(VStreamBook::paid);

      assertThat(log).isEmpty();
    }

    @Test
    @DisplayName("opens the connection, reads every page, then closes it")
    void opensReadsCloses() {
      List<Order> all = ordersOver(feed()).toList().run();

      assertThat(all).hasSize(6);
      assertThat(log)
          .containsExactly("feed opened", "page 1", "page 2", "page 3", "page 4", "feed closed");
    }

    @Test
    @DisplayName("opens a connection afresh for each run, and closes each")
    void eachRunOpensAfresh() {
      VStream<Order> orders = ordersOver(feed());

      orders.toList().run();
      orders.toList().run();

      assertThat(Collections.frequency(log, "feed opened")).isEqualTo(2);
      assertThat(Collections.frequency(log, "feed closed")).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("stopping early")
  class StoppingEarly {

    @Test
    @DisplayName("take closes the connection once it has its elements, and reads no further")
    void takeCloses() {
      List<String> firstTwo =
          ordersOver(feed())
              .filter(VStreamBook::paid)
              .map(VStreamBook::customer)
              .take(2)
              .toList()
              .run();

      assertThat(firstTwo).containsExactly("Ada", "Alan");
      assertThat(log).containsExactly("feed opened", "page 1", "page 2", "feed closed");
    }

    @Test
    @DisplayName(
        "takeWhile, zipWith, headOption, find, exists and forAll each close when they stop")
    void everyEarlyStopCloses() {
      Map<String, Function<VStream<Order>, VTask<?>>> stops =
          Map.of(
              "takeWhile", orders -> orders.takeWhile(VStreamBook::paid).toList(),
              "zipWith", orders -> orders.zipWith(VStream.of(1), (order, n) -> n).toList(),
              "headOption", VStream::headOption,
              "find", orders -> orders.find(VStreamBook::paid),
              "exists", orders -> orders.exists(VStreamBook::paid),
              "forAll", orders -> orders.forAll(VStreamBook::paid));

      stops.forEach(
          (name, stop) -> {
            log.clear();
            stop.apply(ordersOver(feed())).run();
            assertThat(log).as(name).containsExactly("feed opened", "page 1", "feed closed");
          });
    }
  }

  @Nested
  @DisplayName("when something fails")
  class WhenSomethingFails {

    @Test
    @DisplayName("a feed that fails closes the connection, then fails with the feed's exception")
    void aFailureCloses() {
      Try<List<Order>> down = ordersOver(feed(new PagedFeed(ORDERS, 2))).toList().runSafe();

      assertThatTry(down)
          .isFailure()
          .hasExceptionSatisfying(
              error ->
                  assertThat(error)
                      .isInstanceOf(IOException.class)
                      .hasMessage("the order feed is down"));
      assertThat(log).containsExactly("feed opened", "page 1", "page 2", "feed closed");
    }

    @Test
    @DisplayName("a use that throws closes the connection, then fails with its exception")
    void aThrowingUseCloses() {
      VStream<Order> broken =
          VStream.bracket(
              VTask.of(feed()::open),
              _ -> {
                throw new IllegalStateException("no reader for this feed");
              },
              VStreamResourcesBook::closing);

      assertThatTry(broken.toList().runSafe())
          .isFailure()
          .hasExceptionSatisfying(error -> assertThat(error).hasMessage("no reader for this feed"));
      assertThat(log).containsExactly("feed opened", "feed closed");
    }

    @Test
    @DisplayName("a close that fails after a good run fails the operation")
    void aFailedCloseFails() {
      Try<List<Order>> stuck =
          ordersOver(LoggedFeedService.failingToClose(log, new PagedFeed(ORDERS)))
              .toList()
              .runSafe();

      assertThatTry(stuck)
          .isFailure()
          .hasExceptionSatisfying(
              error -> assertThat(error).hasMessage("the connection would not close"));
    }

    @Test
    @DisplayName("a close that fails after a failed run is suppressed onto the run's failure")
    void aFailedCloseIsSuppressed() {
      Try<List<Order>> both =
          ordersOver(LoggedFeedService.failingToClose(log, new PagedFeed(ORDERS, 2)))
              .toList()
              .runSafe();

      assertThatTry(both)
          .isFailure()
          .hasExceptionSatisfying(
              error -> {
                assertThat(error).hasMessage("the order feed is down");
                assertThat(error.getSuppressed())
                    .extracting(Throwable::getMessage)
                    .containsExactly("the connection would not close");
              });
    }

    @Test
    @DisplayName("run() hands a checked failure over inside a VTaskExecutionException")
    void runWrapsACheckedFailure() {
      VStream<Order> down = ordersOver(feed(new PagedFeed(ORDERS, 2)));

      assertThatThrownBy(() -> down.toList().run())
          .isInstanceOf(VTaskExecutionException.class)
          .cause()
          .isInstanceOf(IOException.class)
          .hasMessage("the order feed is down");
    }
  }

  @Nested
  @DisplayName("nested brackets, and one resource after another")
  class NestedAndOneAfterAnother {

    @Test
    @DisplayName("an inner bracket closes before an outer one, on completion and on take")
    void innerFirst() {
      List<BigDecimal> quotes = quotesOver(feed(), new LoggedCourierService(log)).toList().run();
      List<String> fullRun = List.copyOf(log);
      log.clear();
      quotesOver(feed(), new LoggedCourierService(log)).take(1).toList().run();

      assertThat(quotes).hasSize(4);
      assertThat(fullRun)
          .containsExactly(
              "feed opened",
              "courier opened",
              "page 1",
              "page 2",
              "page 3",
              "page 4",
              "courier closed",
              "feed closed");
      assertThat(log).endsWith("courier closed", "feed closed");
    }

    @Test
    @DisplayName("two brackets joined with concat close the first before opening the second")
    void oneAfterAnother() {
      VStream<Order> london = ordersOver(feed(new PagedFeed(ORDERS.subList(0, 2))));
      VStream<Order> edinburgh = ordersOver(feed(new PagedFeed(ORDERS.subList(2, 4))));

      long both = london.concat(edinburgh).count().run();

      assertThat(both).isEqualTo(4);
      assertThat(log)
          .containsExactly(
              "feed opened",
              "page 1",
              "page 2",
              "feed closed",
              "feed opened",
              "page 1",
              "page 2",
              "feed closed");
    }

    @Test
    @DisplayName("a stream joined inside use keeps the first connection open until the end")
    void joinedInsideUseStaysOpen() {
      VStream<Order> next =
          ordersOver(new LoggedFeedService(new CopyOnWriteArrayList<>(), new PagedFeed(ORDERS)))
              .take(2)
              .peek(_ -> log.add("next read"));
      VStream<Order> inside =
          VStream.bracket(
              VTask.of(feed(new PagedFeed(ORDERS.subList(0, 2)))::open),
              connection -> orders(connection).concat(next),
              VStreamResourcesBook::closing);

      inside.toList().run();

      assertThat(log)
          .containsExactly(
              "feed opened", "page 1", "page 2", "next read", "next read", "feed closed");
    }
  }

  @Nested
  @DisplayName("onFinalize")
  class Finalisers {

    private final List<String> reports = new CopyOnWriteArrayList<>();

    private VStream<String> customers(VStream<Order> orders) {
      return orders
          .map(VStreamBook::customer)
          .onFinalize(VTask.exec(() -> reports.add("customers read")))
          .onFinalize(VTask.exec(() -> reports.add("report sent")));
    }

    @Test
    @DisplayName("finalisers run in the order they were attached, when the stream stops early")
    void inOrderOnEarlyStop() {
      List<String> first = customers(orders(new PagedFeed(ORDERS))).take(1).toList().run();

      assertThat(first).containsExactly("Ada");
      assertThat(reports).containsExactly("customers read", "report sent");
    }

    @Test
    @DisplayName("finalisers run when the stream completes, and when it fails")
    void onCompletionAndFailure() {
      customers(orders(new PagedFeed(ORDERS))).toList().run();
      Try<List<String>> failed = customers(orders(new PagedFeed(ORDERS, 2))).toList().runSafe();

      assertThatTry(failed).isFailure();
      assertThat(reports)
          .containsExactly("customers read", "report sent", "customers read", "report sent");
    }

    @Test
    @DisplayName("a finaliser runs once for each run of the stream")
    void oncePerRun() {
      VStream<String> stream = customers(orders(new PagedFeed(ORDERS)));

      stream.toList().run();
      stream.toList().run();

      assertThat(Collections.frequency(reports, "report sent")).isEqualTo(2);
    }

    @Test
    @DisplayName("after a recovered mapTask failure, the finaliser runs when the stream ends")
    void afterARecoveredFailure() {
      shippingQuotes(orders(new PagedFeed(ORDERS)), PATCHY_COURIER)
          .onFinalize(VTask.exec(() -> reports.add("quotes done")))
          .recover(error -> FLAT_RATE)
          .peek(quote -> reports.add("quote " + quote))
          .toList()
          .run();

      assertThat(reports)
          .containsExactly("quote 4.99", "quote 4.99", "quote 9.99", "quote 4.99", "quotes done");
    }

    @Test
    @DisplayName("finalisers that fail: the first is thrown, and the other still runs, suppressed")
    void failingFinalisers() {
      VStream<Integer> stream =
          VStream.of(1, 2)
              .onFinalize(
                  VTask.of(
                      () -> {
                        throw new IllegalStateException("first finaliser");
                      }))
              .onFinalize(
                  VTask.of(
                      () -> {
                        throw new IllegalStateException("second finaliser");
                      }));

      assertThatTry(stream.toList().runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error -> {
                assertThat(error).hasMessage("first finaliser");
                assertThat(error.getSuppressed())
                    .extracting(Throwable::getMessage)
                    .containsExactly("second finaliser");
              });
    }

    @Test
    @DisplayName(
        "a stream the run never reaches is left as it is, unless the finaliser is after it")
    void unreachedStreamLeftAlone() {
      List<String> ran = new CopyOnWriteArrayList<>();
      VStream<Integer> second = VStream.of(2).onFinalize(VTask.exec(() -> ran.add("second")));

      VStream.of(1).concat(second).headOption().run();
      second.prepend(0).headOption().run();
      second.prepend(0).onFinalize(VTask.exec(() -> ran.add("after prepend"))).headOption().run();

      assertThat(ran).containsExactly("after prepend");
    }

    @Test
    @DisplayName("closing a stream that was never pulled runs its finalisers")
    void closingUnpulledRunsFinalisers() {
      customers(orders(new PagedFeed(ORDERS))).close().run();

      assertThat(reports).containsExactly("customers read", "report sent");
    }
  }

  @Nested
  @DisplayName("on a VStreamPath")
  class OnAPath {

    @Test
    @DisplayName("vstreamBracket and onFinalize release, then run the finaliser")
    void pathBracket() {
      long count =
          Path.vstreamBracket(
                  VTask.of(feed()::open), VStreamBook::orders, VStreamResourcesBook::closing)
              .onFinalize(VTask.exec(() -> log.add("orders read")))
              .count()
              .unsafeRun();

      assertThat(count).isEqualTo(6);
      assertThat(log).endsWith("feed closed", "orders read");
    }
  }

  @Nested
  @DisplayName("pulling by hand")
  class ByHand {

    @Test
    @DisplayName(
        "the first pull opens the connection and gives a Skip, and closing its tail closes")
    void aTailClosesTheConnection() {
      VStream.Step<Order> first = ordersOver(feed()).pull().run();

      assertThat(first).isInstanceOf(VStream.Step.Skip.class);
      assertThat(log).containsExactly("feed opened", "page 1");
      tail(first).close().run();
      assertThat(log).endsWith("feed closed");
    }

    @Test
    @DisplayName("closing the stream that was pulled closes the connection too")
    void theHeadClosesTheConnection() {
      VStream<Order> orders = ordersOver(feed());

      orders.pull().run();
      orders.close().run();

      assertThat(log).containsExactly("feed opened", "page 1", "feed closed");
    }

    @Test
    @DisplayName("a pull that fails inside the bracket's stream releases the connection by itself")
    void aFailureInsideReleases() {
      VStream<Order> orders = ordersOver(feed(new PagedFeed(ORDERS, 1)));

      assertThatThrownBy(() -> orders.pull().run()).hasRootCauseInstanceOf(IOException.class);
      assertThat(log).containsExactly("feed opened", "page 1", "feed closed");
    }

    @Test
    @DisplayName("a pull that fails in an operator after the bracket releases it too")
    void aFailureAfterReleases() {
      VStream<String> current =
          ordersOver(feed())
              .map(
                  order -> {
                    throw new IllegalStateException("no name for " + order.id());
                  });
      Throwable failure = null;
      while (failure == null) {
        try {
          current = tail(current.pull().run());
        } catch (RuntimeException e) {
          failure = e;
        }
      }

      assertThat(failure).hasMessageStartingWith("no name for");
      assertThat(log).containsExactly("feed opened", "page 1", "feed closed");
    }

    @Test
    @DisplayName("take closes the rest only at the pull after its last element")
    void takeClosesAtTheNextPull() {
      VStream<Order> current = ordersOver(feed()).take(1);
      VStream.Step<Order> step;
      do {
        step = current.pull().run();
        current = tail(step);
      } while (!(step instanceof VStream.Step.Emit<Order>));

      assertThat(log).doesNotContain("feed closed");
      assertThat(current.pull().run()).isInstanceOf(VStream.Step.Done.class);
      assertThat(log).endsWith("feed closed");
    }

    @Test
    @DisplayName(
        "a Fail step's tail holds the rest: reading on from it reads the rest, then closes")
    void aFailStepsTail() {
      VStream<BigDecimal> current = shippingQuotes(ordersOver(feed()), PATCHY_COURIER);
      VStream.Step<BigDecimal> step;
      do {
        step = current.pull().run();
        current = tail(step);
      } while (!(step instanceof VStream.Step.Fail<BigDecimal>));

      List<BigDecimal> rest = current.toList().run();

      assertThat(rest).containsExactly(new BigDecimal("4.99"));
      assertThat(log).endsWith("feed closed");
    }

    @Test
    @DisplayName("closing a Fail step's tail closes the connection")
    void closingAFailStepsTail() {
      VStream<BigDecimal> current = shippingQuotes(ordersOver(feed()), PATCHY_COURIER);
      VStream.Step<BigDecimal> step;
      do {
        step = current.pull().run();
        current = tail(step);
      } while (!(step instanceof VStream.Step.Fail<BigDecimal>));

      assertThat(log).doesNotContain("feed closed");
      current.close().run();
      assertThat(log).endsWith("feed closed");
    }

    @Test
    @DisplayName("running a pulled task twice opens twice, and closing the head closes the latest")
    void runningAPullTwice() {
      VStream<Order> orders = ordersOver(feed());
      VTask<VStream.Step<Order>> pull = orders.pull();

      VStream.Step<Order> firstRun = pull.run();
      pull.run();
      orders.close().run();

      assertThat(Collections.frequency(log, "feed opened")).isEqualTo(2);
      assertThat(Collections.frequency(log, "feed closed")).isEqualTo(1);
      tail(firstRun).close().run();
      assertThat(Collections.frequency(log, "feed closed")).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("which thread releases")
  class WhichThread {

    @Test
    @DisplayName("the release runs on the thread that runs the terminal operation")
    void releasesOnTheTerminalsThread() throws InterruptedException {
      AtomicReference<Thread> atTheEnd = new AtomicReference<>();
      AtomicReference<Thread> onTake = new AtomicReference<>();
      AtomicReference<Thread> runner = new AtomicReference<>();

      Thread.ofVirtual()
          .start(
              () -> {
                runner.set(Thread.currentThread());
                recordingRelease(atTheEnd).toList().run();
                recordingRelease(onTake).take(1).toList().run();
              })
          .join();

      assertThat(atTheEnd.get()).isSameAs(runner.get()).isNotSameAs(Thread.currentThread());
      assertThat(onTake.get()).isSameAs(runner.get());
    }

    @Test
    @DisplayName("under parEvalMapUnordered or merge, the release runs on the reading thread")
    void parallelReadersRelease() {
      AtomicReference<Thread> unordered = new AtomicReference<>();
      AtomicReference<Thread> merged = new AtomicReference<>();

      VStreamPar.parEvalMapUnordered(recordingRelease(unordered), 2, VTask::succeed).toList().run();
      VStreamPar.merge(recordingRelease(merged), VStream.empty()).toList().run();

      assertThat(unordered.get()).isNotSameAs(Thread.currentThread());
      assertThat(unordered.get().isVirtual()).isTrue();
      assertThat(merged.get()).isNotSameAs(Thread.currentThread());
      assertThat(merged.get().isVirtual()).isTrue();
    }

    @Test
    @DisplayName("an interrupt set before the release is held back, and set again afterwards")
    void anEarlierInterruptIsHeldBack() {
      AtomicReference<Boolean> interruptedDuringRelease = new AtomicReference<>();
      VStream<Integer> held =
          VStream.bracket(
              VTask.succeed("resource"),
              _ -> VStream.of(1, 2, 3),
              _ ->
                  VTask.exec(
                      () -> interruptedDuringRelease.set(Thread.currentThread().isInterrupted())));
      VStream<Integer> rest = tail(held.pull().run());

      Thread.currentThread().interrupt();
      try {
        rest.close().run();
        assertThat(interruptedDuringRelease.get()).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName("an interrupt that arrives while the release runs, as a timeout's can, reaches it")
    void aLaterInterruptReachesTheRelease() throws InterruptedException {
      AtomicBoolean cutShort = new AtomicBoolean();
      CountDownLatch releasing = new CountDownLatch(1);
      VStream<Integer> slowToClose =
          VStream.bracket(
              VTask.succeed("resource"),
              _ -> VStream.of(1, 2, 3),
              _ ->
                  VTask.exec(
                      () -> {
                        releasing.countDown();
                        try {
                          Thread.sleep(Duration.ofSeconds(5));
                        } catch (InterruptedException e) {
                          cutShort.set(true);
                        }
                      }));
      Thread reading = Thread.startVirtualThread(() -> slowToClose.toList().runSafe());

      // Interrupted once the release has begun, as a timeout's interrupt is when it comes then. A
      // real timeout can come before the release on a busy machine, and is then held back, as the
      // test before this one shows
      assertThat(releasing.await(5, TimeUnit.SECONDS)).isTrue();
      reading.interrupt();

      assertThat(reading.join(Duration.ofSeconds(5))).isTrue();
      assertThat(cutShort).isTrue();
    }
  }
}
