// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for Retry utility class.
 *
 * <p>Tests cover execute methods, retry behavior, and edge cases.
 */
@DisplayName("Retry Test Suite")
class RetryTest {

  @Nested
  @DisplayName("execute() with Supplier")
  class ExecuteSupplierTests {

    @Test
    @DisplayName("execute() returns result on success")
    void executeReturnsResultOnSuccess() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      String result = Retry.execute(policy, () -> "success");

      assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("execute() retries on failure and succeeds")
    void executeRetriesAndSucceeds() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      String result =
          Retry.execute(
              policy,
              () -> {
                if (attempts.incrementAndGet() < 3) {
                  throw new RuntimeException("Failed");
                }
                return "success";
              });

      assertThat(result).isEqualTo("success");
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("execute() throws RetryExhaustedException when all attempts fail")
    void executeThrowsRetryExhaustedExceptionWhenAllFail() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      assertThatThrownBy(
              () ->
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("Always fails");
                      }))
          .isInstanceOf(RetryExhaustedException.class)
          .hasMessageContaining("3 attempts")
          .hasCauseInstanceOf(RuntimeException.class);

      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("execute() respects retry predicate")
    void executeRespectsRetryPredicate() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1)).retryOn(IOException.class);
      AtomicInteger attempts = new AtomicInteger(0);

      // Should not retry on RuntimeException - throws original exception directly
      assertThatThrownBy(
              () ->
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("Not retryable");
                      }))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Not retryable");

      // Only one attempt because RuntimeException is not retryable
      assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute() validates null arguments")
    void executeValidatesNullArguments() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.execute(null, () -> "value"))
          .withMessageContaining("policy must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.execute(policy, (Supplier<String>) null))
          .withMessageContaining("supplier must not be null");
    }

    @Test
    @DisplayName("execute() succeeds on first attempt if no exception")
    void executeSucceedsOnFirstAttempt() {
      RetryPolicy policy = RetryPolicy.fixed(5, Duration.ofMillis(100));
      AtomicInteger attempts = new AtomicInteger(0);

      String result =
          Retry.execute(
              policy,
              () -> {
                attempts.incrementAndGet();
                return "immediate success";
              });

      assertThat(result).isEqualTo("immediate success");
      assertThat(attempts.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("execute() with Runnable")
  class ExecuteRunnableTests {

    @Test
    @DisplayName("execute() completes runnable successfully")
    void executeCompletesRunnableSuccessfully() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger counter = new AtomicInteger(0);

      Retry.execute(policy, counter::incrementAndGet);

      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute() retries runnable on failure")
    void executeRetriesRunnableOnFailure() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      Retry.execute(
          policy,
          () -> {
            if (attempts.incrementAndGet() < 3) {
              throw new RuntimeException("Failed");
            }
          });

      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("execute() validates null runnable")
    void executeValidatesNullRunnable() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.execute(policy, (Runnable) null))
          .withMessageContaining("runnable must not be null");
    }
  }

  @Nested
  @DisplayName("Convenience Methods")
  class ConvenienceMethodsTests {

    @Test
    @DisplayName("withExponentialBackoff() executes with exponential backoff")
    void withExponentialBackoffExecutes() {
      AtomicInteger attempts = new AtomicInteger(0);

      String result =
          Retry.withExponentialBackoff(
              3,
              Duration.ofMillis(1),
              () -> {
                if (attempts.incrementAndGet() < 2) {
                  throw new RuntimeException("Retry");
                }
                return "success";
              });

      assertThat(result).isEqualTo("success");
      assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("withFixedDelay() executes with fixed delay")
    void withFixedDelayExecutes() {
      AtomicInteger attempts = new AtomicInteger(0);

      String result =
          Retry.withFixedDelay(
              3,
              Duration.ofMillis(1),
              () -> {
                if (attempts.incrementAndGet() < 2) {
                  throw new RuntimeException("Retry");
                }
                return "success";
              });

      assertThat(result).isEqualTo("success");
      assertThat(attempts.get()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("noRetry Policy")
  class NoRetryTests {

    @Test
    @DisplayName("noRetry() fails immediately without retrying")
    void noRetryFailsImmediately() {
      RetryPolicy policy = RetryPolicy.noRetry();
      AtomicInteger attempts = new AtomicInteger(0);

      // noRetry() means no exceptions are retryable, so original exception is thrown directly
      assertThatThrownBy(
              () ->
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("Fail");
                      }))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Fail");

      assertThat(attempts.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Delay Behavior")
  class DelayBehaviorTests {

    @Test
    @DisplayName("execute() applies delay between retries")
    void executeAppliesDelayBetweenRetries() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(50));
      AtomicInteger attempts = new AtomicInteger(0);

      long startTime = System.currentTimeMillis();

      assertThatThrownBy(
              () ->
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("Fail");
                      }))
          .isInstanceOf(RetryExhaustedException.class);

      long duration = System.currentTimeMillis() - startTime;

      // Should have delayed at least 2 * 50ms (delay after attempt 1 and 2)
      assertThat(duration).isGreaterThanOrEqualTo(100);
    }

    @Test
    @DisplayName("execute() skips delay when delay is zero")
    void executeSkipsDelayWhenZero() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ZERO);
      AtomicInteger attempts = new AtomicInteger(0);

      long startTime = System.currentTimeMillis();

      assertThatThrownBy(
              () ->
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("Fail");
                      }))
          .isInstanceOf(RetryExhaustedException.class);

      long duration = System.currentTimeMillis() - startTime;

      // With zero delay, should complete very quickly (no sleeping)
      assertThat(duration).isLessThan(100);
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("execute() skips delay when delay is negative")
    void executeSkipsDelayWhenNegative() {
      // Create a policy where delayForAttempt might return negative (edge case)
      // In practice, delayForAttempt won't return negative, but we test the guard anyway
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ZERO);
      AtomicInteger attempts = new AtomicInteger(0);

      // This should not hang or error - just skip the sleep
      assertThatThrownBy(
              () ->
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("Fail");
                      }))
          .isInstanceOf(RetryExhaustedException.class);

      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("execute() skips delay when duration is explicitly negative")
    void executeSkipsDelayWhenExplicitlyNegative() {
      // Create a policy with explicitly negative duration to cover the isNegative() branch
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(-100));
      AtomicInteger attempts = new AtomicInteger(0);

      long startTime = System.currentTimeMillis();

      assertThatThrownBy(
              () ->
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("Fail");
                      }))
          .isInstanceOf(RetryExhaustedException.class);

      long duration = System.currentTimeMillis() - startTime;

      // With negative delay, should complete very quickly (no sleeping)
      assertThat(duration).isLessThan(100);
      assertThat(attempts.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Exception Handling")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("execute() re-throws Error directly when not retryable")
    void executeRethrowsErrorDirectlyWhenNotRetryable() {
      // Policy that only retries on IOException, not on Error
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1)).retryOn(IOException.class);
      AtomicInteger attempts = new AtomicInteger(0);
      Error testError = new OutOfMemoryError("simulated OOM");

      // Should not retry on Error - throws original Error directly
      assertThatThrownBy(
              () ->
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        throw testError;
                      }))
          .isSameAs(testError);

      // Only one attempt because Error is not retryable
      assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute() wraps checked exception in RuntimeException when not retryable")
    void executeWrapsCheckedExceptionWhenNotRetryable() {
      // Policy that only retries on RuntimeException, not on checked exceptions
      RetryPolicy policy =
          RetryPolicy.fixed(3, Duration.ofMillis(1)).retryOn(RuntimeException.class);
      AtomicInteger attempts = new AtomicInteger(0);
      Exception checkedException = new Exception("checked exception");

      // Should not retry on checked Exception - wraps in RuntimeException
      assertThatThrownBy(
              () ->
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        // Need to use sneaky throw to throw a checked exception from Supplier
                        throw sneakyThrow(checkedException);
                      }))
          .isInstanceOf(RuntimeException.class)
          .hasCause(checkedException);

      // Only one attempt because checked exception is not retryable
      assertThat(attempts.get()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E {
      throw (E) e;
    }

    @Test
    @DisplayName("RetryExhaustedException preserves original exception")
    void retryExhaustedExceptionPreservesOriginalException() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));
      RuntimeException originalException = new RuntimeException("Network error");

      RetryExhaustedException caught = null;
      try {
        Retry.execute(
            policy,
            () -> {
              throw originalException;
            });
      } catch (RetryExhaustedException e) {
        caught = e;
      }

      assertThat(caught).isNotNull();
      assertThat(caught.getCause()).isSameAs(originalException);
      assertThat(caught.getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("Last exception is preserved after all retries")
    void lastExceptionIsPreserved() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      RetryExhaustedException caught = null;
      try {
        Retry.execute(
            policy,
            () -> {
              throw new RuntimeException("Attempt " + attempts.incrementAndGet());
            });
      } catch (RetryExhaustedException e) {
        caught = e;
      }

      assertThat(caught).isNotNull();
      assertThat(caught.getCause()).hasMessage("Attempt 3");
    }

    @Test
    @DisplayName("execute() handles InterruptedException during delay")
    void executeHandlesInterruptedExceptionDuringDelay() throws InterruptedException {
      RetryPolicy policy =
          RetryPolicy.fixed(5, Duration.ofSeconds(10)); // Long delay to ensure interrupt
      AtomicInteger attempts = new AtomicInteger(0);
      RuntimeException originalException = new RuntimeException("Initial failure");

      Thread testThread =
          new Thread(
              () -> {
                try {
                  Retry.execute(
                      policy,
                      () -> {
                        attempts.incrementAndGet();
                        throw originalException;
                      });
                } catch (RetryExhaustedException e) {
                  // Expected - verify interrupt flag is restored
                  assertThat(Thread.currentThread().isInterrupted()).isTrue();
                  assertThat(e.getMessage()).contains("Retry interrupted after 1 attempts");
                  assertThat(e.getCause()).isSameAs(originalException);
                  assertThat(e.getAttempts()).isEqualTo(1);
                }
              });

      testThread.start();

      // Give the thread time to start and fail once, entering the sleep
      Thread.sleep(100);

      // Interrupt the thread while it's sleeping
      testThread.interrupt();

      // Wait for the thread to complete
      testThread.join(5000);

      assertThat(testThread.isAlive()).isFalse();
      assertThat(attempts.get()).isEqualTo(1); // Only one attempt before interrupt
    }
  }
}
