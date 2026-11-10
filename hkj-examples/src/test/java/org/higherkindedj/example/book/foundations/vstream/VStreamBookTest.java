// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.COURIER;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.FLAT_RATE;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.ORDERS;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.PATCHY_COURIER;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.orders;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.shippingQuotes;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.instances.Witnesses.vstream;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.PagedFeed;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the VStream page makes about pulling, running and recovering a stream. */
@DisplayName("VStream: lazy pull-based streams")
class VStreamBookTest {

  private static VStream<String> paidCustomers(PagedFeed feed) {
    return orders(feed).filter(VStreamBook::paid).map(VStreamBook::customer);
  }

  /**
   * The stream, recording each step it answers a pull with, as the operation that pulls it sees
   * them, with the page the feed fetched for that pull.
   */
  private static <A> VStream<A> recording(VStream<A> stream, PagedFeed feed, List<String> steps) {
    return () ->
        () -> {
          int fetchedBefore = feed.fetched().size();
          VStream.Step<A> step = stream.pull().execute();
          String fetched =
              feed.fetched().size() > fetchedBefore
                  ? " after page " + feed.fetched().getLast()
                  : "";
          return switch (step) {
            case VStream.Step.Emit<A> e -> {
              steps.add("Emit " + e.value() + fetched);
              yield new VStream.Step.Emit<>(e.value(), recording(e.tail(), feed, steps));
            }
            case VStream.Step.Skip<A> skip -> {
              steps.add("Skip" + fetched);
              yield new VStream.Step.Skip<>(recording(skip.tail(), feed, steps));
            }
            case VStream.Step.Fail<A> failed -> {
              steps.add("Fail " + failed.error().getMessage() + fetched);
              yield new VStream.Step.Fail<>(failed.error(), recording(failed.tail(), feed, steps));
            }
            case VStream.Step.Done<A> done -> {
              steps.add("Done");
              yield done;
            }
          };
        };
  }

  @Nested
  @DisplayName("reading a feed a page at a time")
  class ReadingAFeed {

    @Test
    @DisplayName("building the stream fetches nothing, and two paid orders need two pages")
    void fetchesOnlyThePagesItNeeds() {
      PagedFeed feed = new PagedFeed(ORDERS);
      VStream<String> paid = paidCustomers(feed);

      assertThat(feed.fetched()).isEmpty();
      assertThat(paid.take(2).toList().run()).containsExactly("Ada", "Alan");
      assertThat(feed.fetched()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("each run reads the feed again, up to the empty page past the last")
    void eachRunReadsAgain() {
      PagedFeed feed = new PagedFeed(ORDERS);
      VStream<String> paid = paidCustomers(feed);

      paid.take(2).toList().run();
      assertThat(paid.toList().run()).containsExactly("Ada", "Alan", "Edsger", "Ken");
      assertThat(feed.fetched()).containsExactly(1, 2, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("take(2) answers seven pulls: a Skip for each page and each order filtered out")
    void thePullTrace() {
      PagedFeed feed = new PagedFeed(ORDERS);
      List<String> steps = new ArrayList<>();

      List<String> taken = recording(paidCustomers(feed).take(2), feed, steps).toList().run();

      assertThat(taken).containsExactly("Ada", "Alan");
      assertThat(steps)
          .containsExactly(
              "Skip after page 1",
              "Emit Ada",
              "Skip",
              "Skip",
              "Skip after page 2",
              "Emit Alan",
              "Done");
      assertThat(feed.fetched()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("without the filter, Grace's order is emitted where the filter gave a Skip")
    void theFilterGaveTheSkip() {
      PagedFeed feed = new PagedFeed(ORDERS);
      List<String> steps = new ArrayList<>();

      recording(orders(feed).map(VStreamBook::customer), feed, steps).take(2).toList().run();

      assertThat(steps).containsExactly("Skip after page 1", "Emit Ada", "Emit Grace");
    }

    @Test
    @DisplayName("a filter that rejects a hundred thousand elements leaves the stack as it was")
    void rejectingFilterIsStackSafe() {
      assertThat(VStream.range(0, 100_000).filter(n -> n == 99_999).toList().run())
          .containsExactly(99_999);
    }

    @Test
    @DisplayName("take closes the rest of the stream once it has its last element")
    void takeClosesTheRest() {
      AtomicInteger closed = new AtomicInteger();
      VStream<String> paid =
          paidCustomers(new PagedFeed(ORDERS)).onFinalize(VTask.exec(closed::incrementAndGet));

      paid.take(2).toList().run();

      assertThat(closed).hasValue(1);
    }

    @Test
    @DisplayName("fromIterator is used up by a run: a later run carries on where the last stopped")
    void fromIteratorIsUsedUp() {
      VStream<Integer> stream = VStream.fromIterator(List.of(1, 2, 3, 4).iterator());

      assertThat(stream.take(2).toList().run()).containsExactly(1, 2);
      assertThat(stream.toList().run()).containsExactly(3, 4);
      assertThat(stream.toList().run()).isEmpty();
    }

    @Test
    @DisplayName("fromList reads the list as it is when pulled, without copying it")
    void fromListDoesNotCopy() {
      List<String> names = new CopyOnWriteArrayList<>(List.of("Ada"));
      VStream<String> stream = VStream.fromList(names);

      names.add("Alan");

      assertThat(stream.toList().run()).containsExactly("Ada", "Alan");
    }
  }

  @Nested
  @DisplayName("the operators")
  class Operators {

    @Test
    @DisplayName("takeWhile stops at the first element that fails, and closes the rest")
    void takeWhileClosesTheRest() {
      AtomicInteger closed = new AtomicInteger();
      VStream<Integer> numbers =
          VStream.of(1, 2, 3, 1).onFinalize(VTask.exec(closed::incrementAndGet));

      assertThat(numbers.takeWhile(n -> n < 3).toList().run()).containsExactly(1, 2);
      assertThat(closed).hasValue(1);
    }

    @Test
    @DisplayName("drop, dropWhile and distinct choose elements as the table says")
    void dropAndDistinct() {
      assertThat(VStream.of(1, 2, 3, 1).drop(2).toList().run()).containsExactly(3, 1);
      assertThat(VStream.of(1, 2, 3, 1).dropWhile(n -> n < 3).toList().run()).containsExactly(3, 1);
      assertThat(VStream.of(1, 2, 1, 3).distinct().toList().run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("concat does not pull the second stream until the first ends")
    void concatWaitsForTheFirst() {
      AtomicInteger built = new AtomicInteger();
      VStream<String> second =
          VStream.defer(
              () -> {
                built.incrementAndGet();
                return VStream.of("Barbara");
              });

      assertThat(VStream.of("Ada", "Alan").concat(second).take(2).toList().run())
          .containsExactly("Ada", "Alan");
      assertThat(built).hasValue(0);
      assertThat(VStream.of("Ada").concat(second).toList().run()).containsExactly("Ada", "Barbara");
    }

    @Test
    @DisplayName("zipWith stops at the shorter stream, so the other may be endless")
    void zipWithAnEndlessStream() {
      assertThat(
              VStream.of("Ada", "Alan")
                  .zipWith(VStream.iterate(1, n -> n + 1), (name, n) -> n + ". " + name)
                  .toList()
                  .run())
          .containsExactly("1. Ada", "2. Alan");
    }

    @Test
    @DisplayName("interleave takes an element from each stream in turn")
    void interleave() {
      assertThat(VStream.of(1, 3, 5).interleave(VStream.of(2, 4, 6)).toList().run())
          .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName(
        "an operator between mapTask and recover applies to the elements after the failure")
    void operatorBetweenMapTaskAndRecover() {
      VStream<Integer> odd =
          VStream.of(1, 2, 3, 4, 5, 6)
              .mapTask(n -> n == 2 ? VTask.fail(new IOException("line 2")) : VTask.succeed(n))
              .filter(n -> n % 2 == 1);

      // The filter still tests 4 and 6, and take counts the failed element, as the page's note says
      assertThat(odd.recover(error -> -1).toList().run()).containsExactly(1, -1, 3, 5);
      assertThat(odd.take(3).recover(error -> -1).toList().run()).containsExactly(1, -1, 3);
    }
  }

  @Nested
  @DisplayName("a stream never holds null")
  class NoNulls {

    @Test
    @DisplayName("VStream.of(null) throws at the call")
    void ofRefusesNull() {
      assertThatNullPointerException().isThrownBy(() -> VStream.of((String) null));
    }

    @Test
    @DisplayName("a null element in a list fails the stream when it is pulled")
    void nullElementFailsWhenPulled() {
      List<String> withANull = new ArrayList<>();
      withANull.add("Ada");
      withANull.add(null);
      VStream<String> stream = VStream.fromList(withANull);

      assertThatTry(stream.toList().runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error ->
                  assertThat(error)
                      .isInstanceOf(NullPointerException.class)
                      .hasMessage("VStream.fromList element cannot be null"));
    }

    @Test
    @DisplayName("a zipWith or recover function that returns null fails the stream")
    void nullFromZipWithOrRecoverFails() {
      assertThatTry(
              VStream.of(1).zipWith(VStream.of(2), (a, b) -> (Integer) null).toList().runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error -> assertThat(error).isInstanceOf(NullPointerException.class));
      assertThatTry(
              VStream.<String>fail(new IllegalStateException("down"))
                  .recover(error -> null)
                  .toList()
                  .runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error -> assertThat(error).isInstanceOf(NullPointerException.class));
    }

    @Test
    @DisplayName("a map function that returns null fails the stream")
    void nullFromMapFails() {
      assertThatTry(VStream.of("Ada").map(_ -> (String) null).toList().runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error -> assertThat(error).isInstanceOf(NullPointerException.class));
    }
  }

  @Nested
  @DisplayName("running a stream")
  class Running {

    @Test
    @DisplayName("the shipping for four paid orders at 4.99 each is 19.96")
    void foldsTheQuotes() {
      assertThat(
              shippingQuotes(orders(new PagedFeed(ORDERS)), COURIER)
                  .fold(BigDecimal.ZERO, BigDecimal::add)
                  .run())
          .isEqualByComparingTo("19.96");
    }

    @Test
    @DisplayName("the courier is asked only when the stream reaches an order")
    void courierAskedOnlyWhenReached() {
      List<String> asked = new CopyOnWriteArrayList<>();
      VStreamBook.Courier counting =
          order -> {
            asked.add(order.customer().name());
            return new BigDecimal("4.99");
          };

      VStream<BigDecimal> quotes = shippingQuotes(orders(new PagedFeed(ORDERS)), counting);
      assertThat(asked).isEmpty();

      quotes.headOption().run();
      assertThat(asked).containsExactly("Ada");
    }

    @Test
    @DisplayName("exists and find stop at the first match, even on an endless stream")
    void shortCircuits() {
      PagedFeed feed = new PagedFeed(ORDERS);

      assertThat(paidCustomers(feed).exists(name -> name.equals("Alan")).run()).isTrue();
      assertThat(feed.fetched()).containsExactly(1, 2);
      assertThat(VStream.iterate(1, n -> n + 1).find(n -> n > 41).run()).contains(42);
    }

    @Test
    @DisplayName(
        "a pull runs on the thread that runs the terminal task; runAsync uses a virtual one")
    void threads() {
      VStream<Boolean> onAVirtualThread =
          VStream.of(1).map(_ -> Thread.currentThread().isVirtual());

      assertThat(onAVirtualThread.toList().run()).containsExactly(false);
      assertThat(onAVirtualThread.runAsync().join()).containsExactly(true);
    }

    @Test
    @DisplayName("run on a virtual thread, the pulls run on that thread")
    void pullsRunOnTheCallersThread() throws InterruptedException {
      VStream<Boolean> onAVirtualThread =
          VStream.of(1).map(_ -> Thread.currentThread().isVirtual());
      List<List<Boolean>> seen = new CopyOnWriteArrayList<>();

      Thread.ofVirtual().start(() -> seen.add(onAVirtualThread.toList().run())).join();

      assertThat(seen).containsExactly(List.of(true));
    }

    @Test
    @DisplayName("runAsync starts reading at once")
    void runAsyncStartsAtOnce() throws InterruptedException {
      CountDownLatch started = new CountDownLatch(1);
      VStream<Integer> stream =
          VStream.of(1)
              .map(
                  n -> {
                    started.countDown();
                    return n;
                  });

      CompletableFuture<List<Integer>> future = stream.runAsync();

      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(future.join()).containsExactly(1);
    }

    @Test
    @DisplayName(
        "a timeout stops drain() on an endless stream, and fails once the stream is closed")
    void timeoutStopsAnEndlessStream() {
      AtomicInteger finalised = new AtomicInteger();
      VTask<Unit> drained =
          VStream.repeat(1).onFinalize(VTask.exec(finalised::incrementAndGet)).drain();

      assertThatTry(drained.timeout(Duration.ofMillis(50)).runSafe())
          .isFailure()
          .hasExceptionSatisfying(error -> assertThat(error).isInstanceOf(TimeoutException.class));
      assertThat(finalised).hasValue(1);
    }

    @Test
    @DisplayName(
        "a run on a thread that is already interrupted fails before its first pull, closing"
            + " nothing")
    void alreadyInterrupted() {
      List<Integer> read = new ArrayList<>();
      AtomicInteger finalised = new AtomicInteger();
      VStream<Integer> stream =
          VStream.of(1, 2, 3).onFinalize(VTask.exec(finalised::incrementAndGet));

      Thread.currentThread().interrupt();
      try {
        assertThatTry(stream.forEach(read::add).runSafe())
            .isFailure()
            .hasExceptionSatisfying(
                error -> assertThat(error).isInstanceOf(InterruptedException.class));
      } finally {
        Thread.interrupted();
      }
      assertThat(read).isEmpty();
      assertThat(finalised).hasValue(0);
    }

    @Test
    @DisplayName(
        "a recover outside the timeout gives a value, and one inside it gives a value the timeout"
            + " discards")
    void recoverOutsideTheTimeout() {
      List<Integer> outside =
          VStream.repeat(1)
              .toList()
              .timeout(Duration.ofMillis(50))
              .recover(error -> List.of())
              .run();

      assertThat(outside).isEmpty();
      assertThatTry(
              VStream.repeat(1)
                  .toList()
                  .recover(error -> List.of(-1))
                  .timeout(Duration.ofMillis(50))
                  .runSafe())
          .isFailure()
          .hasExceptionSatisfying(error -> assertThat(error).isInstanceOf(TimeoutException.class));
    }
  }

  @Nested
  @DisplayName("when an element fails")
  class Failures {

    @Test
    @DisplayName("runSafe holds the courier's IOException as it was thrown, and run wraps it")
    void runSafeAndRun() {
      VStream<BigDecimal> patchy = shippingQuotes(orders(new PagedFeed(ORDERS)), PATCHY_COURIER);

      assertThatTry(patchy.toList().runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error ->
                  assertThat(error)
                      .isInstanceOf(IOException.class)
                      .hasMessage("no courier serves Edsger's address"));
      assertThatThrownBy(() -> patchy.toList().run())
          .isInstanceOf(VTaskExecutionException.class)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("recover after mapTask gives the flat rate for Edsger and carries on")
    void recoverCarriesOn() {
      List<BigDecimal> quotes =
          shippingQuotes(orders(new PagedFeed(ORDERS)), PATCHY_COURIER)
              .recover(error -> FLAT_RATE)
              .toList()
              .run();

      assertThat(quotes)
          .usingElementComparator(BigDecimal::compareTo)
          .containsExactly(
              new BigDecimal("4.99"), new BigDecimal("4.99"), FLAT_RATE, new BigDecimal("4.99"));
    }

    @Test
    @DisplayName("recoverWith keeps what was read before the feed went down, and fetches no more")
    void recoverWithStopsCleanly() {
      PagedFeed feed = new PagedFeed(ORDERS, 2);

      List<String> read =
          orders(feed)
              .map(VStreamBook::customer)
              .recoverWith(error -> VStream.empty())
              .toList()
              .run();

      assertThat(read).containsExactly("Ada", "Grace");
      assertThat(feed.fetched()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("without a recovery, the feed's IOException fails the stream")
    void feedFailure() {
      assertThatTry(orders(new PagedFeed(ORDERS, 2)).toList().runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error ->
                  assertThat(error)
                      .isInstanceOf(IOException.class)
                      .hasMessage("the order feed is down"));
    }

    @Test
    @DisplayName("recover, recoverWith, mapError and onError let an interrupt through")
    void interruptGoesThrough() {
      InterruptedException stop = new InterruptedException("stop");
      VStream<Integer> interrupted =
          VStream.of(1, 2).mapTask(n -> n == 2 ? VTask.fail(stop) : VTask.succeed(n));
      List<Throwable> seen = new ArrayList<>();
      List<VStream<Integer>> handled =
          List.of(
              interrupted.recover(
                  error -> {
                    seen.add(error);
                    return -1;
                  }),
              interrupted.recoverWith(
                  error -> {
                    seen.add(error);
                    return VStream.of(-1);
                  }),
              interrupted.mapError(
                  error -> {
                    seen.add(error);
                    return new IllegalStateException(error);
                  }),
              interrupted.onError(seen::add));

      for (VStream<Integer> stream : handled) {
        // Each run leaves the interrupt status set, which the next run would stop at
        try {
          assertThatTry(stream.toList().runSafe())
              .isFailure()
              .hasExceptionSatisfying(error -> assertThat(error).isSameAs(stop));
        } finally {
          Thread.interrupted();
        }
      }
      assertThat(seen).as("what the operators' functions saw").isEmpty();
    }

    @Test
    @DisplayName("recover after a failed page gives its value, then the stream ends")
    void recoverEndsAfterAFailedPage() {
      List<String> read =
          orders(new PagedFeed(ORDERS, 2))
              .map(VStreamBook::customer)
              .recover(error -> "?")
              .toList()
              .run();

      assertThat(read).containsExactly("Ada", "Grace", "?");
    }

    @Test
    @DisplayName("recoverWith and recover, when they end the stream, close what they stop reading")
    void recoveriesCloseWhatTheyStopReading() {
      AtomicInteger closed = new AtomicInteger();
      VStream<Integer> source = VStream.of(1, 2, 3).onFinalize(VTask.exec(closed::incrementAndGet));
      VStream<Integer> failsAtTwo =
          source.flatMap(
              n ->
                  n == 2
                      ? VStream.<Integer>fail(new IllegalStateException("down"))
                      : VStream.of(n));

      assertThat(failsAtTwo.recoverWith(error -> VStream.empty()).toList().run())
          .containsExactly(1);
      assertThat(closed).hasValue(1);
      assertThat(failsAtTwo.recover(error -> -1).toList().run()).containsExactly(1, -1);
      assertThat(closed).hasValue(2);
    }

    @Test
    @DisplayName("mapError changes a later element's failure, and onError sees it")
    void mapErrorAndOnError() {
      List<Throwable> seen = new ArrayList<>();

      VStream<BigDecimal> mapped =
          shippingQuotes(orders(new PagedFeed(ORDERS)), PATCHY_COURIER)
              .onError(seen::add)
              .mapError(error -> new IllegalStateException("cannot ship: " + error.getMessage()));

      assertThatTry(mapped.toList().runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error ->
                  assertThat(error)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessage("cannot ship: no courier serves Edsger's address"));
      assertThat(seen).singleElement().isInstanceOf(IOException.class);
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("widen and narrow give back the same stream")
    void widenAndNarrow() {
      VStream<Order> stream = orders(new PagedFeed(ORDERS));

      assertThat(VSTREAM.narrow(VSTREAM.widen(stream))).isSameAs(stream);
    }

    @Test
    @DisplayName("narrow refuses a Kind that is not a VStream")
    void narrowRefusesAnotherType() {
      // A Maybe passed off as a stream, to be refused
      Kind<VStreamKind.Witness, String> notAStream =
          passedOff(MaybeKindHelper.MAYBE.widen(Maybe.just("Ada")));

      assertThatThrownBy(() -> VSTREAM.narrow(notAStream)).isInstanceOf(KindUnwrapException.class);
    }

    @SuppressWarnings("unchecked") // deliberately wrong, so narrow has something to refuse
    private static <T> T passedOff(Object value) {
      return (T) value;
    }

    @Test
    @DisplayName("flatMap through the Monad instance substitutes a stream for each element")
    void flatMapThroughTheInstance() {
      Monad<VStreamKind.Witness> streams = Instances.monad(vstream());

      Kind<VStreamKind.Witness, String> greetings =
          streams.flatMap(
              name -> streams.of("Dear " + name), VSTREAM.widen(VStream.of("Ada", "Alan")));

      assertThat(VSTREAM.narrow(greetings).toList().run()).containsExactly("Dear Ada", "Dear Alan");
    }
  }
}
