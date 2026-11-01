// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.future.CompletableFutureBook.CACHED_RATE;
import static org.higherkindedj.example.book.foundations.future.CompletableFutureBook.ORDER;
import static org.higherkindedj.example.book.foundations.future.CompletableFutureBook.priceInEuros;
import static org.higherkindedj.example.book.foundations.future.CompletableFutureBook.rateServiceDown;
import static org.higherkindedj.example.book.foundations.future.CompletableFutureBook.rateToEuros;
import static org.higherkindedj.example.book.foundations.future.CompletableFutureBook.shippingQuote;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the CompletableFuture page makes about its instance and its Path. */
@DisplayName("CompletableFuture: Java's asynchronous result")
class CompletableFutureBookTest {

  private static final MonadError<CompletableFutureKind.Witness, Throwable> FUTURES =
      Instances.monadError(completableFuture());

  @Nested
  @DisplayName("through the instance")
  class ThroughTheInstance {

    @Test
    @DisplayName("map2 joins the two lookups into a price in euros")
    void map2JoinsTheLookups() {
      Kind<CompletableFutureKind.Witness, BigDecimal> price =
          FUTURES.map2(
              FUTURE.widen(rateToEuros()),
              FUTURE.widen(shippingQuote(ORDER)),
              (rate, shipping) -> CompletableFutureBook.inEuros(ORDER, rate, shipping));

      assertThat(FUTURE.join(price)).isEqualByComparingTo("117.57");
    }

    @Test
    @DisplayName("map2 neither starts nor waits for the futures it is given")
    void map2DoesNotWait() {
      CompletableFuture<BigDecimal> rate = new CompletableFuture<>();
      CompletableFuture<BigDecimal> shipping = new CompletableFuture<>();

      Kind<CompletableFutureKind.Witness, BigDecimal> price =
          FUTURES.map2(FUTURE.widen(rate), FUTURE.widen(shipping), BigDecimal::add);

      assertThat(FUTURE.narrow(price).isDone()).isFalse();
      rate.complete(BigDecimal.ONE);
      shipping.complete(BigDecimal.TEN);
      assertThat(FUTURE.join(price)).isEqualByComparingTo("11");
    }

    @Test
    @DisplayName("join keeps a checked cause wrapped, where the handler receives it")
    void joinKeepsACheckedCauseWrapped() {
      Kind<CompletableFutureKind.Witness, String> failed =
          FUTURE.widen(CompletableFuture.failedFuture(new IOException("feed unreadable")));
      AtomicReference<Throwable> seen = new AtomicReference<>();

      assertThatThrownBy(() -> FUTURE.join(failed)).hasCauseInstanceOf(IOException.class);
      FUTURE.join(
          FUTURES.handleErrorWith(
              failed,
              error -> {
                seen.set(error);
                return FUTURES.of("recovered");
              }));
      assertThat(seen.get()).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("handleErrorWith receives the cause, not a CompletionException")
    void handleErrorWithReceivesTheCause() {
      AtomicReference<Throwable> seen = new AtomicReference<>();

      BigDecimal rate =
          FUTURE.join(
              FUTURES.handleErrorWith(
                  FUTURE.widen(rateServiceDown()),
                  error -> {
                    seen.set(error);
                    return FUTURE.widen(CompletableFuture.completedFuture(CACHED_RATE));
                  }));

      assertThat(rate).isEqualByComparingTo("1.15");
      assertThat(seen.get())
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("the rate service is down");
    }

    @Test
    @DisplayName("join waits for the future and rethrows its cause")
    void joinRethrowsTheCause() {
      assertThatThrownBy(() -> FUTURE.join(FUTURE.widen(rateServiceDown())))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("the rate service is down");
    }

    @Test
    @DisplayName("raiseError gives a failed future")
    void raiseErrorGivesAFailedFuture() {
      Kind<CompletableFutureKind.Witness, BigDecimal> failed =
          FUTURES.raiseError(new IllegalStateException("closed"));

      assertThat(FUTURE.narrow(failed).isCompletedExceptionally()).isTrue();
    }

    @Test
    @DisplayName("the instance's of keeps the JDK's null")
    void ofKeepsNull() {
      Object completed = FUTURE.join(FUTURES.of(null));

      assertThat(completed).isNull();
    }

    @Test
    @DisplayName("widening wraps the future in a new holder, and narrowing unwraps it")
    void wideningAllocatesAHolder() {
      CompletableFuture<BigDecimal> rate = rateToEuros();
      Kind<CompletableFutureKind.Witness, BigDecimal> once = FUTURE.widen(rate);

      assertThat(once).isNotSameAs(FUTURE.widen(rate));
      assertThat(FUTURE.narrow(once)).isSameAs(rate);
    }
  }

  @Nested
  @DisplayName("on a CompletableFuturePath")
  class OnACompletableFuturePath {

    @Test
    @DisplayName("priceInEuros uses the live rate, or the cached one when the service is down")
    void priceInEurosRecovers() {
      assertThat(priceInEuros(ORDER, rateToEuros()).join()).isEqualByComparingTo("117.57");
      assertThat(priceInEuros(ORDER, rateServiceDown()).join()).isEqualByComparingTo("115.56");
    }

    @Test
    @DisplayName("toEitherPath turns a failure into a Left holding the exception")
    void toEitherPathGivesALeft() {
      assertThat(Path.future(rateServiceDown()).toEitherPath().run().getLeft())
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("the rate service is down");
      assertThat(Path.future(rateToEuros()).toEitherPath().run().isRight()).isTrue();
    }

    @Test
    @DisplayName("a future that completes with null fails the path")
    void aNullFailsThePath() {
      assertThatThrownBy(() -> Path.future(CompletableFuture.completedFuture(null)).join())
          .hasCauseInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a future of Void, mapped to Unit first, gives a Path that succeeds")
    void aVoidFutureMappedToUnit() {
      CompletableFuture<Void> sent = CompletableFuture.runAsync(() -> {});

      assertThat(Path.future(sent.thenApply(_ -> Unit.INSTANCE)).join()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("race keeps the first success, and via chains a step on the result")
    void raceAndVia() {
      assertThat(
              Path.<String>futureFailed(new IllegalStateException("down"))
                  .race(Path.futureAsync(() -> "ok"))
                  .join())
          .isEqualTo("ok");
      assertThat(Path.futureCompleted(2).via(n -> Path.futureCompleted(n * 10)).join())
          .isEqualTo(20);
    }

    @Test
    @DisplayName("supplyAsyncWithRetry runs the work again, where a failed future stays failed")
    void supplyAsyncWithRetryRunsTheWorkAgain() {
      AtomicInteger attempts = new AtomicInteger();

      String result =
          CompletableFuturePath.supplyAsyncWithRetry(
                  () -> {
                    if (attempts.incrementAndGet() < 3) {
                      throw new IllegalStateException("busy");
                    }
                    return "booked";
                  },
                  3,
                  Duration.ofMillis(1))
              .join();

      assertThat(result).isEqualTo("booked");
      assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("withTimeout fails a future that does not complete in time")
    void withTimeoutFails() {
      assertThatThrownBy(
              () ->
                  Path.future(new CompletableFuture<String>())
                      .withTimeout(Duration.ofMillis(10))
                      .join())
          .isInstanceOf(RuntimeException.class);
    }
  }
}
