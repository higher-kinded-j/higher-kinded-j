// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.vtask.VTaskBook.CACHED_RATE;
import static org.higherkindedj.example.book.foundations.vtask.VTaskBook.COURIERS;
import static org.higherkindedj.example.book.foundations.vtask.VTaskBook.LIVE_RATES;
import static org.higherkindedj.example.book.foundations.vtask.VTaskBook.ORDER;
import static org.higherkindedj.example.book.foundations.vtask.VTaskBook.RATES_DOWN;
import static org.higherkindedj.example.book.foundations.vtask.VTaskBook.SHIPPED;
import static org.higherkindedj.example.book.foundations.vtask.VTaskBook.confirmCollection;
import static org.higherkindedj.example.book.foundations.vtask.VTaskBook.priceInEuros;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the VTask page makes about tasks, their Paths and the instance. */
@DisplayName("VTask: deferred tasks and virtual threads")
class VTaskBookTest {

  private static final MonadError<VTaskKind.Witness, Throwable> TASKS =
      Instances.monadError(vtask());

  /** Quotes as COURIER does, without counting in QUOTES, which the page's main prints. */
  private static final VTaskBook.Courier QUOTING = order -> new BigDecimal("7.99");

  /** Waits for the latch to reach zero, failing the task if it does not do so in time. */
  private static void meet(CountDownLatch latch) throws InterruptedException {
    latch.countDown();
    if (!latch.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("the tasks did not run at the same time");
    }
  }

  @Nested
  @DisplayName("on a VTaskPath")
  class OnAVTaskPath {

    @Test
    @DisplayName("priceInEuros uses the live rate, or the cached one when the service is down")
    void priceInEurosRecovers() {
      assertThat(priceInEuros(ORDER, LIVE_RATES, QUOTING).unsafeRun())
          .isEqualByComparingTo("117.57");
      assertThat(priceInEuros(ORDER, RATES_DOWN, QUOTING).unsafeRun())
          .isEqualByComparingTo("115.56");
    }

    @Test
    @DisplayName("building the path asks neither service, and each run asks again")
    void buildingAsksNothing() {
      AtomicInteger rates = new AtomicInteger();
      AtomicInteger quotes = new AtomicInteger();
      VTaskBook.Rates countingRates =
          () -> {
            rates.incrementAndGet();
            return new BigDecimal("1.17");
          };
      VTaskBook.Courier countingCourier =
          order -> {
            quotes.incrementAndGet();
            return new BigDecimal("7.99");
          };

      VTaskPath<BigDecimal> price = priceInEuros(ORDER, countingRates, countingCourier);
      assertThat(rates).hasValue(0);
      assertThat(quotes).hasValue(0);
      price.unsafeRun();
      price.unsafeRun();
      assertThat(rates).hasValue(2);
      assertThat(quotes).hasValue(2);
    }

    @Test
    @DisplayName("parZipWith runs the two lookups at the same time, each on a virtual thread")
    void parZipWithRunsBothAtOnce() {
      CountDownLatch both = new CountDownLatch(2);
      AtomicInteger onVirtualThreads = new AtomicInteger();
      VTaskBook.Rates rates =
          () -> {
            awaitTogether(both, onVirtualThreads);
            return new BigDecimal("1.17");
          };
      VTaskBook.Courier courier =
          order -> {
            awaitTogether(both, onVirtualThreads);
            return new BigDecimal("7.99");
          };

      assertThat(priceInEuros(ORDER, rates, courier).unsafeRun()).isEqualByComparingTo("117.57");
      assertThat(onVirtualThreads).hasValue(2);
    }

    private static void awaitTogether(CountDownLatch both, AtomicInteger onVirtualThreads)
        throws IOException {
      if (Thread.currentThread().isVirtual()) {
        onVirtualThreads.incrementAndGet();
      }
      try {
        meet(both);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("interrupted", e);
      }
    }

    @Test
    @DisplayName("unsafeRun runs the path on the calling thread")
    void unsafeRunUsesTheCallingThread() {
      Thread caller = Thread.currentThread();

      assertThat(Path.vtask(Thread::currentThread).unsafeRun()).isSameAs(caller);
    }

    @Test
    @DisplayName("a timed-out task keeps running, unobserved")
    void aTimedOutTaskKeepsRunning() throws InterruptedException {
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch finished = new CountDownLatch(1);
      VTaskPath<String> slow =
          Path.vtask(
              () -> {
                release.await();
                finished.countDown();
                return "late";
              });

      Try<String> outcome = slow.timeout(Duration.ofMillis(10)).run().runSafe();
      release.countDown();

      assertThatTry(outcome).isFailure().hasExceptionOfType(TimeoutException.class);
      assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("timeout runs the task on a virtual thread of its own")
    void timeoutRunsTheTaskOnAVirtualThread() {
      boolean onAVirtualThread =
          VTask.of(() -> Thread.currentThread().isVirtual()).timeout(Duration.ofSeconds(5)).run();

      assertThat(onAVirtualThread).isTrue();
    }
  }

  @Nested
  @DisplayName("on a VResultPath")
  class OnAVResultPath {

    @Test
    @DisplayName("confirmCollection books a paid order, and refuses one that has shipped")
    void confirmCollectionRefusesAShippedOrder() {
      assertThat(confirmCollection(ORDER).run().run())
          .isEqualTo(Either.right("collection booked for Ada, confirmed"));
      assertThat(confirmCollection(SHIPPED).run().run())
          .isEqualTo(Either.left(OrderError.alreadyShipped(ORDER.id())));
    }

    @Test
    @DisplayName("via stops at the first Left, so the next step never runs")
    void viaStopsAtTheFirstLeft() {
      AtomicInteger confirmations = new AtomicInteger();
      VResultPath<OrderError, String> confirmed =
          VTaskBook.bookCollection(SHIPPED)
              .via(
                  booking -> {
                    confirmations.incrementAndGet();
                    return Path.<OrderError, String>vresultRight(booking);
                  });

      assertThat(confirmed.run().run().isLeft()).isTrue();
      assertThat(confirmations).hasValue(0);
    }

    @Test
    @DisplayName("withTimeout gives a typed Left, and leaves the task running")
    void withTimeoutLeavesTheTaskRunning() throws InterruptedException {
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch booked = new CountDownLatch(1);
      VResultPath<OrderError, String> slow =
          Path.vresult(
              VTask.of(
                  () -> {
                    release.await();
                    booked.countDown();
                    return Either.<OrderError, String>right("collection booked");
                  }));

      Either<OrderError, String> outcome =
          slow.withTimeout(Duration.ofMillis(10), () -> OrderError.notFound(ORDER.id()))
              .run()
              .run();
      release.countDown();

      assertThat(outcome).isEqualTo(Either.left(OrderError.notFound(ORDER.id())));
      assertThat(booked.await(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Nested
  @DisplayName("running a task")
  class RunningATask {

    @Test
    @DisplayName("run and runSafe use the calling thread, and runAsync a new virtual thread")
    void whichThreadRunsATask() {
      Thread caller = Thread.currentThread();
      VTask<Thread> thread = VTask.of(Thread::currentThread);

      assertThat(thread.run()).isSameAs(caller);
      assertThat(thread.runSafe()).isEqualTo(Try.success(caller));
      Thread async = thread.runAsync().join();
      assertThat(async).isNotSameAs(caller);
      assertThat(async.isVirtual()).isTrue();
    }

    @Test
    @DisplayName("runSafe holds a checked exception itself, and run wraps it")
    void runSafeHoldsTheCheckedException() {
      VTask<BigDecimal> rate = VTask.of(RATES_DOWN::toEuros);

      assertThatTry(rate.runSafe())
          .isFailure()
          .hasExceptionOfType(IOException.class)
          .hasExceptionSatisfying(
              error -> assertThat(error).hasMessage("the rate service is down"));
      assertThatThrownBy(rate::run)
          .isInstanceOf(VTaskExecutionException.class)
          .cause()
          .isInstanceOf(IOException.class)
          .hasMessage("the rate service is down");
    }

    @Test
    @DisplayName("runAsync's future fails with the task's exception")
    void runAsyncFailsWithTheTasksException() {
      VTask<BigDecimal> rate = VTask.of(RATES_DOWN::toEuros);

      assertThatThrownBy(() -> rate.runAsync().join())
          .isInstanceOf(CompletionException.class)
          .cause()
          .isInstanceOf(IOException.class);
      assertThatThrownBy(() -> rate.runAsync().get())
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("run throws an unchecked exception as it is")
    void runThrowsAnUncheckedExceptionAsItIs() {
      VTask<String> closed = VTask.fail(new IllegalStateException("closed"));

      assertThatThrownBy(closed::run)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("closed");
    }

    @Test
    @DisplayName("a task always gives a value: succeed(null) throws, a null result fails the task")
    void aTaskAlwaysGivesAValue() {
      assertThatNullPointerException().isThrownBy(() -> VTask.succeed(null));

      assertThatTry(VTask.of(() -> null).runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error ->
                  assertThat(error)
                      .isInstanceOf(NullPointerException.class)
                      .hasMessage("callable must not return null"));
      assertThat(VTask.exec(() -> {}).run()).isEqualTo(Unit.INSTANCE);
    }
  }

  @Nested
  @DisplayName("running tasks at the same time")
  class RunningTasksAtTheSameTime {

    @Test
    @DisplayName("traverse runs the three quotes at once and keeps their order")
    void traverseRunsTheQuotesAtOnce() {
      CountDownLatch all = new CountDownLatch(COURIERS.size());

      List<BigDecimal> quotes =
          Par.traverse(
                  COURIERS,
                  courier ->
                      VTask.of(
                          () -> {
                            meet(all);
                            return courier.quote(ORDER);
                          }))
              .run();

      assertThat(quotes)
          .containsExactly(new BigDecimal("7.99"), new BigDecimal("6.50"), new BigDecimal("9.20"));
    }

    @Test
    @DisplayName("the cheapest of the three quotes is 6.50")
    void theCheapestQuote() {
      BigDecimal best =
          Par.traverse(COURIERS, courier -> VTask.of(() -> courier.quote(ORDER)))
              .map(Collections::min)
              .run();

      assertThat(best).isEqualByComparingTo("6.50");
    }

    @Test
    @DisplayName("when one task fails, all cancels the others and fails with its exception")
    void allCancelsTheOthersOnAFailure() throws InterruptedException {
      CountDownLatch never = new CountDownLatch(1);
      CountDownLatch cancelled = new CountDownLatch(1);
      VTask<String> waiting =
          VTask.of(
              () -> {
                try {
                  never.await();
                  return "never";
                } catch (InterruptedException e) {
                  cancelled.countDown();
                  throw e;
                }
              });
      IllegalStateException down = new IllegalStateException("the courier is down");

      Try<List<String>> outcome = Par.all(List.of(waiting, VTask.<String>fail(down))).runSafe();

      assertThat(outcome).isEqualTo(Try.failure(down));
      assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("race keeps the first task to succeed")
    void raceKeepsTheFirstSuccess() {
      String first =
          Par.race(
                  List.of(
                      VTask.<String>fail(new IllegalStateException("down")),
                      VTask.succeed("booked")))
              .run();

      assertThat(first).isEqualTo("booked");
    }
  }

  @Nested
  @DisplayName("through the instance")
  class ThroughTheInstance {

    @Test
    @DisplayName("handleErrorWith receives the exception the task threw")
    void handleErrorWithReceivesTheException() {
      AtomicReference<Throwable> seen = new AtomicReference<>();

      Kind<VTaskKind.Witness, BigDecimal> rate =
          TASKS.handleErrorWith(
              VTASK.widen(VTask.of(RATES_DOWN::toEuros)),
              error -> {
                seen.set(error);
                return TASKS.of(CACHED_RATE);
              });

      assertThat(VTASK.run(rate)).isEqualByComparingTo("1.15");
      assertThat(seen.get()).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("recover gives the same constant fallback")
    void recoverGivesTheConstant() {
      Kind<VTaskKind.Witness, BigDecimal> rate =
          TASKS.recover(VTASK.widen(VTask.of(RATES_DOWN::toEuros)), CACHED_RATE);

      assertThat(VTASK.run(rate)).isEqualByComparingTo("1.15");
    }

    @Test
    @DisplayName("map2 runs its tasks one after another, so a failure stops the second")
    void map2RunsItsTasksInTurn() {
      AtomicInteger secondRuns = new AtomicInteger();
      Kind<VTaskKind.Witness, String> second =
          VTASK.widen(VTask.of(() -> "run " + secondRuns.incrementAndGet()));

      Kind<VTaskKind.Witness, String> both =
          TASKS.map2(
              VTASK.widen(VTask.<String>fail(new IllegalStateException("first failed"))),
              second,
              (a, b) -> a + b);

      assertThat(VTASK.runSafe(both).isFailure()).isTrue();
      assertThat(secondRuns).hasValue(0);
    }

    @Test
    @DisplayName("widening returns the same object")
    void wideningAllocatesNothing() {
      VTask<BigDecimal> rate = VTask.of(LIVE_RATES::toEuros);

      assertThat(VTASK.widen(rate)).isSameAs(rate);
      assertThat(VTASK.narrow(VTASK.widen(rate))).isSameAs(rate);
    }

    @Test
    @DisplayName("the instance's map builds a task and runs nothing")
    void mapRunsNothing() {
      AtomicBoolean ran = new AtomicBoolean();
      Kind<VTaskKind.Witness, Integer> task =
          VTASK.widen(
              VTask.of(
                  () -> {
                    ran.set(true);
                    return 1;
                  }));

      Kind<VTaskKind.Witness, Integer> doubled = TASKS.map(n -> n * 2, task);
      assertThat(ran).isFalse();
      assertThat(VTASK.run(doubled)).isEqualTo(2);
      assertThat(ran).isTrue();
    }
  }
}
