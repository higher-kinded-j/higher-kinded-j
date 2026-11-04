// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.scope.ScopeBook.COURIERS;
import static org.higherkindedj.example.book.foundations.scope.ScopeBook.DOWN;
import static org.higherkindedj.example.book.foundations.scope.ScopeBook.ORDER;
import static org.higherkindedj.example.book.foundations.scope.ScopeBook.SHORT;
import static org.higherkindedj.example.book.foundations.scope.ScopeBook.WELL_STOCKED;
import static org.higherkindedj.example.book.foundations.scope.ScopeBook.allQuotes;
import static org.higherkindedj.example.book.foundations.scope.ScopeBook.checkLines;
import static org.higherkindedj.example.book.foundations.scope.ScopeBook.firstQuote;
import static org.higherkindedj.example.book.foundations.scope.ScopeBook.quoteFrom;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.ScopeJoiner;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Structured Concurrency page makes about Scope and its joiners. */
@DisplayName("Scope: structured concurrency")
class ScopeBookTest {

  /** Counts down, then waits for every task to do the same, so the tasks must run at once. */
  private static void meet(CountDownLatch latch) throws InterruptedException {
    latch.countDown();
    if (!latch.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("the tasks did not run at the same time");
    }
  }

  /** A courier that waits until it is cancelled, counting the cancellation. */
  private static ScopeBook.Courier waitsUntilCancelled(AtomicInteger cancelled) {
    return order -> {
      try {
        if (!new CountDownLatch(1).await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("the courier was never cancelled");
        }
        return BigDecimal.ONE;
      } catch (InterruptedException e) {
        cancelled.incrementAndGet();
        Thread.currentThread().interrupt();
        throw new IOException("cancelled", e);
      }
    };
  }

  /** Waits, boundedly, until the latch is released. */
  private static void awaitRelease(CountDownLatch latch) throws IOException {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("the latch was never released");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted", e);
    }
  }

  @Nested
  @DisplayName("forking and joining")
  class ForkingAndJoining {

    @Test
    @DisplayName("allSucceed keeps every quote in fork order, whatever order they finish in")
    void allSucceedKeepsForkOrder() {
      CountDownLatch lastAnswered = new CountDownLatch(1);
      ScopeBook.Courier answersLast =
          order -> {
            awaitRelease(lastAnswered);
            return new BigDecimal("7.99");
          };
      ScopeBook.Courier releases =
          order -> {
            lastAnswered.countDown();
            return new BigDecimal("9.20");
          };

      assertThat(allQuotes(List.of(answersLast, COURIERS.get(1), releases), ORDER).run())
          .containsExactly(new BigDecimal("7.99"), new BigDecimal("6.50"), new BigDecimal("9.20"));
      assertThat(allQuotes(COURIERS, ORDER).run())
          .containsExactly(new BigDecimal("7.99"), new BigDecimal("6.50"), new BigDecimal("9.20"));
    }

    @Test
    @DisplayName("the scope's tasks run at the same time, each on a virtual thread")
    void tasksRunAtTheSameTime() {
      CountDownLatch all = new CountDownLatch(3);
      AtomicInteger onVirtualThreads = new AtomicInteger();
      ScopeBook.Courier meeting =
          order -> {
            if (Thread.currentThread().isVirtual()) {
              onVirtualThreads.incrementAndGet();
            }
            try {
              meet(all);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IOException("interrupted", e);
            }
            return BigDecimal.ONE;
          };

      assertThat(allQuotes(List.of(meeting, meeting, meeting), ORDER).run()).hasSize(3);
      assertThat(onVirtualThreads).hasValue(3);
    }

    @Test
    @DisplayName("building a scope runs nothing, and each run forks its tasks again")
    void buildingRunsNothing() {
      AtomicInteger asked = new AtomicInteger();
      ScopeBook.Courier counting =
          order -> {
            asked.incrementAndGet();
            return BigDecimal.ONE;
          };

      VTask<List<BigDecimal>> quotes = allQuotes(List.of(counting, counting), ORDER);
      assertThat(asked).hasValue(0);
      quotes.run();
      quotes.run();
      assertThat(asked).hasValue(4);
    }

    @Test
    @DisplayName("allSucceed fails with a failing task's exception, and cancels the rest")
    void allSucceedFailsAndCancels() {
      AtomicInteger cancelled = new AtomicInteger();

      assertThatTry(allQuotes(List.of(waitsUntilCancelled(cancelled), DOWN), ORDER).runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              error ->
                  assertThat(error)
                      .isInstanceOf(IOException.class)
                      .hasMessage("the courier is down"));
      assertThat(cancelled).hasValue(1);
    }

    @Test
    @DisplayName("anySucceed keeps the first quote, and cancels the couriers still waiting")
    void anySucceedKeepsTheFirstAndCancels() {
      AtomicInteger cancelled = new AtomicInteger();
      ScopeBook.Courier waiting = waitsUntilCancelled(cancelled);

      assertThat(firstQuote(List.of(waiting, waiting, COURIERS.get(1)), ORDER).run())
          .isEqualByComparingTo("6.50");
      assertThat(cancelled).hasValue(2);
    }

    @Test
    @DisplayName("anySucceed fails with one of the exceptions when every courier fails")
    void anySucceedFailsWhenAllFail() {
      assertThatTry(firstQuote(List.of(DOWN, DOWN), ORDER).runSafe())
          .isFailure()
          .hasExceptionOfType(IOException.class);
    }

    @Test
    @DisplayName("firstComplete lets the first to finish decide, even with a failure")
    void firstCompleteLetsTheFirstDecide() {
      AtomicInteger cancelled = new AtomicInteger();
      ScopeBook.Courier waiting = waitsUntilCancelled(cancelled);

      assertThatTry(ScopeBook.firstAnswer(List.of(waiting, DOWN), ORDER).runSafe())
          .isFailure()
          .hasExceptionOfType(IOException.class);
      assertThat(cancelled).hasValue(1);
      assertThat(ScopeBook.firstAnswer(List.of(waiting, COURIERS.get(1)), ORDER).run())
          .isEqualByComparingTo("6.50");
      assertThat(cancelled).hasValue(2);
    }
  }

  @Nested
  @DisplayName("keeping every failure")
  class KeepingEveryFailure {

    @Test
    @DisplayName("accumulating keeps every shortfall, in fork order, whatever order they finish in")
    void accumulatingKeepsEveryShortfall() {
      CountDownLatch shadeChecked = new CountDownLatch(1);
      ScopeBook.Warehouse shadeFirst =
          sku -> {
            if (sku.equals("LAMP")) {
              awaitRelease(shadeChecked);
              return 1;
            }
            shadeChecked.countDown();
            return 0;
          };

      assertThat(checkLines(shadeFirst, ORDER).run())
          .isEqualTo(
              Validated.invalid(
                  List.of("LAMP: 2 ordered, 1 in stock", "SHADE: 1 ordered, 0 in stock")));
      assertThat(checkLines(SHORT, ORDER).run())
          .isEqualTo(
              Validated.invalid(
                  List.of("LAMP: 2 ordered, 1 in stock", "SHADE: 1 ordered, 0 in stock")));
    }

    @Test
    @DisplayName("accumulating gives every line when all are in stock")
    void accumulatingGivesEveryLine() {
      assertThat(checkLines(WELL_STOCKED, ORDER).run()).isEqualTo(Validated.valid(ORDER.lines()));
    }

    @Test
    @DisplayName("accumulating waits for every task, cancelling none after a failure")
    void accumulatingWaitsForEveryTask() {
      CountDownLatch failed = new CountDownLatch(1);
      AtomicInteger finished = new AtomicInteger();

      Validated<List<String>, List<String>> outcome =
          Scope.<String, String>accumulating(Throwable::getMessage)
              .fork(
                  VTask.of(
                      () -> {
                        failed.countDown();
                        throw new IllegalStateException("first failed");
                      }))
              .fork(
                  VTask.of(
                      () -> {
                        awaitRelease(failed);
                        finished.incrementAndGet();
                        return "second finished";
                      }))
              .join()
              .run();

      assertThat(outcome).isEqualTo(Validated.invalid(List.of("first failed")));
      assertThat(finished).hasValue(1);
    }
  }

  @Nested
  @DisplayName("timeouts and results")
  class TimeoutsAndResults {

    @Test
    @DisplayName("a timeout cancels the tasks still running, and fails with TimeoutException")
    void aTimeoutCancelsTheTasks() {
      AtomicInteger cancelled = new AtomicInteger();

      assertThatTry(
              Scope.<BigDecimal>allSucceed()
                  .timeout(Duration.ofMillis(20))
                  .fork(quoteFrom(waitsUntilCancelled(cancelled), ORDER))
                  .joinSafe()
                  .run())
          .isFailure()
          .hasExceptionOfType(TimeoutException.class);
      assertThat(cancelled).hasValue(1);
    }

    @Test
    @DisplayName("joinSafe, joinEither and joinMaybe hold a failure as a value")
    void theSafeJoinsHoldAFailure() {
      Scope<BigDecimal, List<BigDecimal>> refused =
          Scope.<BigDecimal>allSucceed().fork(quoteFrom(DOWN, ORDER));

      assertThatTry(refused.joinSafe().run()).isFailure().hasExceptionOfType(IOException.class);
      assertThat(refused.joinEither().run().getLeft()).isInstanceOf(IOException.class);
      assertThat(refused.joinMaybe().run()).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("the scope's tasks see the ScopedValue bindings of the thread that runs it")
    void tasksSeeTheScopedValues() throws Exception {
      ScopedValue<String> customer = ScopedValue.newInstance();

      List<String> seen =
          ScopedValue.where(customer, "Ada")
              .call(
                  () ->
                      Scope.<String>allSucceed()
                          .fork(VTask.of(customer::get))
                          .fork(VTask.of(customer::get))
                          .join()
                          .run());

      assertThat(seen).containsExactly("Ada", "Ada");
    }
  }

  @Nested
  @DisplayName("joiners")
  class Joiners {

    @Test
    @DisplayName("withJoiner joins as the factory does, and joiner() gives a new Joiner each call")
    void withJoinerAndJoiner() {
      ScopeJoiner<BigDecimal, List<BigDecimal>> everyQuote = ScopeJoiner.allSucceed();

      assertThat(
              Scope.withJoiner(everyQuote)
                  .forkAll(ScopeBook.quotesFrom(COURIERS, ORDER))
                  .join()
                  .run())
          .isEqualTo(allQuotes(COURIERS, ORDER).run());
      assertThat(everyQuote.joiner()).isNotSameAs(everyQuote.joiner());
    }

    @Test
    @DisplayName("firstSuccessEither keeps the first Right, or every Left in fork order")
    void firstSuccessEither() {
      ScopeJoiner<Either<String, BigDecimal>, Either<List<String>, BigDecimal>> firstRight =
          ScopeJoiner.firstSuccessEither();

      assertThat(
              Scope.withJoiner(firstRight)
                  .fork(VTask.succeed(Either.<String, BigDecimal>left("A has no van")))
                  .fork(VTask.succeed(Either.<String, BigDecimal>right(new BigDecimal("6.50"))))
                  .join()
                  .run())
          .isEqualTo(Either.right(new BigDecimal("6.50")));
      CountDownLatch bAnswered = new CountDownLatch(1);
      assertThat(
              Scope.withJoiner(firstRight)
                  .fork(
                      VTask.of(
                          () -> {
                            awaitRelease(bAnswered);
                            return Either.<String, BigDecimal>left("A has no van");
                          }))
                  .fork(
                      VTask.of(
                          () -> {
                            bAnswered.countDown();
                            return Either.<String, BigDecimal>left("B is closed");
                          }))
                  .join()
                  .run())
          .isEqualTo(Either.left(List.of("A has no van", "B is closed")));
    }
  }
}
