// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.RetryExhaustedException;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for {@link RailwayRetry}, the typed-error retry lowering shared by {@link EitherPath}
 * and {@link VResultPath}.
 */
@DisplayName("RailwayRetry Test Suite")
class RailwayRetryTest {

  @Nested
  @DisplayName("executeEither() - railway-aware retry")
  class ExecuteEitherTests {

    private final RetryPolicy immediate = RetryPolicy.fixed(3, Duration.ZERO);

    @Test
    @DisplayName("returns a Right immediately without retrying")
    void rightReturnsImmediately() {
      AtomicInteger attempts = new AtomicInteger();
      Either<String, Integer> result =
          RailwayRetry.executeEither(
              immediate,
              e -> true,
              () -> {
                attempts.incrementAndGet();
                return Either.right(42);
              });
      assertThat(result.getRight()).isEqualTo(42);
      assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("a Left the predicate does not select is returned immediately - never retried")
    void unselectedLeftNeverRetried() {
      AtomicInteger attempts = new AtomicInteger();
      Either<String, Integer> result =
          RailwayRetry.executeEither(
              immediate,
              "transient"::equals,
              () -> {
                attempts.incrementAndGet();
                return Either.left("card declined");
              });
      assertThat(result.getLeft()).isEqualTo("card declined");
      assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("a selected Left is retried until a Right appears")
    void selectedLeftRetriedUntilRight() {
      AtomicInteger attempts = new AtomicInteger();
      Either<String, Integer> result =
          RailwayRetry.executeEither(
              immediate,
              "transient"::equals,
              () -> attempts.incrementAndGet() < 3 ? Either.left("transient") : Either.right(7));
      assertThat(result.getRight()).isEqualTo(7);
      assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("exhaustion while retrying a selected Left returns the last Left, not a throw")
    void selectedLeftExhaustionReturnsLastLeft() {
      AtomicInteger attempts = new AtomicInteger();
      Either<String, Integer> result =
          RailwayRetry.executeEither(
              immediate, e -> true, () -> Either.left("attempt-" + attempts.incrementAndGet()));
      assertThat(result.getLeft()).isEqualTo("attempt-3");
      assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("thrown exceptions still retry per the policy predicate")
    void thrownExceptionRetries() {
      AtomicInteger attempts = new AtomicInteger();
      Either<String, Integer> result =
          RailwayRetry.executeEither(
              immediate,
              e -> false,
              () -> {
                if (attempts.incrementAndGet() < 2) {
                  throw new IllegalStateException("flaky");
                }
                return Either.right(1);
              });
      assertThat(result.getRight()).isEqualTo(1);
      assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("a non-retryable thrown exception is rethrown immediately")
    void nonRetryableExceptionRethrown() {
      RetryPolicy noIse = immediate.retryIf(t -> !(t instanceof IllegalStateException));
      AtomicInteger attempts = new AtomicInteger();
      assertThatExceptionOfType(IllegalStateException.class)
          .isThrownBy(
              () ->
                  RailwayRetry.executeEither(
                      noIse,
                      e -> true,
                      () -> {
                        attempts.incrementAndGet();
                        throw new IllegalStateException("fatal");
                      }));
      assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("exhaustion on a thrown exception still throws RetryExhaustedException")
    void exceptionExhaustionStillThrows() {
      assertThatExceptionOfType(RetryExhaustedException.class)
          .isThrownBy(
              () ->
                  RailwayRetry.executeEither(
                      immediate,
                      e -> true,
                      () -> {
                        throw new RuntimeException("always down");
                      }))
          .withCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("channels interleave: selected Left, then exception, then Right")
    void channelsInterleave() {
      AtomicInteger attempts = new AtomicInteger();
      Either<String, Integer> result =
          RailwayRetry.executeEither(
              immediate,
              "transient"::equals,
              () ->
                  switch (attempts.incrementAndGet()) {
                    case 1 -> Either.left("transient");
                    case 2 -> throw new IllegalStateException("blip");
                    default -> Either.right(99);
                  });
      assertThat(result.getRight()).isEqualTo(99);
      assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("retry listeners fire for typed retries, naming the selected error")
    void listenerSeesTypedRetries() {
      List<String> messages = new ArrayList<>();
      RetryPolicy listening =
          immediate.onRetry(event -> messages.add(event.lastException().getMessage()));
      RailwayRetry.executeEither(listening, e -> true, () -> Either.left("boom"));
      assertThat(messages)
          .hasSize(2)
          .allMatch(m -> m.contains("typed error selected for retry: boom"));
    }

    @Test
    @DisplayName("interruption mid-typed-retry returns the last Left with the flag restored")
    void interruptionMidTypedRetryReturnsLastLeft() throws InterruptedException {
      AtomicInteger attempts = new AtomicInteger();
      RetryPolicy slow = RetryPolicy.fixed(3, Duration.ofSeconds(5));
      Thread testThread =
          new Thread(
              () -> {
                Either<String, Integer> result =
                    RailwayRetry.executeEither(
                        slow,
                        e -> true,
                        () -> {
                          attempts.incrementAndGet();
                          return Either.left("typed");
                        });
                assertThat(result.getLeft()).isEqualTo("typed");
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
              });
      testThread.start();
      Thread.sleep(100);
      testThread.interrupt();
      testThread.join(5000);
      assertThat(testThread.isAlive()).isFalse();
      assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a null outcome from the supplier is rejected (via the retry loop)")
    void nullOutcomeRejected() {
      assertThatExceptionOfType(RetryExhaustedException.class)
          .isThrownBy(() -> RailwayRetry.executeEither(immediate, e -> true, () -> null))
          .withCauseInstanceOf(NullPointerException.class)
          .satisfies(e -> assertThat(e.getCause()).hasMessage("supplier returned null"));
    }

    @Test
    @DisplayName("null arguments are rejected")
    void nullArgumentsRejected() {
      Supplier<Either<String, Integer>> step = () -> Either.right(1);
      assertThatNullPointerException()
          .isThrownBy(() -> RailwayRetry.executeEither(null, e -> true, step))
          .withMessage("policy must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> RailwayRetry.executeEither(immediate, null, step))
          .withMessage("retryOn must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> RailwayRetry.executeEither(immediate, e -> true, null))
          .withMessage("supplier must not be null");
    }
  }

  @Nested
  @DisplayName("retryTaskEither() - lazy railway-aware retry")
  class RetryTaskEitherTests {

    private final RetryPolicy immediate = RetryPolicy.fixed(3, Duration.ZERO);

    @Test
    @DisplayName("is lazy: nothing runs until the task is run")
    void isLazy() {
      AtomicInteger attempts = new AtomicInteger();
      VTask<Either<String, Integer>> task =
          RailwayRetry.retryTaskEither(
              VTask.of(
                  () -> {
                    attempts.incrementAndGet();
                    return Either.right(1);
                  }),
              e -> true,
              immediate);
      assertThat(attempts).hasValue(0);
      assertThat(task.run().getRight()).isEqualTo(1);
      assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("retries a selected Left through the task channel")
    void retriesSelectedLeft() {
      AtomicInteger attempts = new AtomicInteger();
      VTask<Either<String, Integer>> task =
          RailwayRetry.retryTaskEither(
              VTask.of(
                  () ->
                      attempts.incrementAndGet() < 2 ? Either.left("transient") : Either.right(5)),
              "transient"::equals,
              immediate);
      assertThat(task.run().getRight()).isEqualTo(5);
      assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("null arguments are rejected")
    void nullArgumentsRejected() {
      VTask<Either<String, Integer>> task = VTask.succeed(Either.right(1));
      assertThatNullPointerException()
          .isThrownBy(() -> RailwayRetry.retryTaskEither(null, e -> true, immediate))
          .withMessage("task must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> RailwayRetry.retryTaskEither(task, null, immediate))
          .withMessage("retryOn must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> RailwayRetry.retryTaskEither(task, e -> true, null))
          .withMessage("policy must not be null");
    }
  }
}
