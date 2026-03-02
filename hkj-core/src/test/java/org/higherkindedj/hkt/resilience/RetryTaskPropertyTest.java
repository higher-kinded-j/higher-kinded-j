// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.*;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Property-based tests for {@link Retry#retryTask(VTask, RetryPolicy)}.
 *
 * <p>Verifies that retry behaviour respects maximum attempts, exception filters, backoff delay
 * calculations, and the onRetry listener contract.
 */
class RetryTaskPropertyTest {

  // ==================== Never Exceeds Max Attempts ====================

  /**
   * Property: A VTask that always fails is attempted at most {@code maxAttempts} times.
   *
   * <p>After exhaustion a {@link RetryExhaustedException} is thrown whose {@code getAttempts()}
   * equals {@code maxAttempts}.
   */
  @Property
  @Label("retryTask never exceeds max attempts")
  void retryTaskNeverExceedsMaxAttempts(
      @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 10) int maxAttempts) {

    AtomicInteger attempts = new AtomicInteger(0);
    RetryPolicy policy = RetryPolicy.fixed(maxAttempts, Duration.ZERO);

    VTask<String> alwaysFails = VTask.fail(new RuntimeException("always fails"));

    VTask<String> retried =
        Retry.retryTask(
            () -> {
              attempts.incrementAndGet();
              return alwaysFails.run();
            },
            policy);

    assertThatThrownBy(retried::run)
        .isInstanceOf(RetryExhaustedException.class)
        .satisfies(
            ex -> assertThat(((RetryExhaustedException) ex).getAttempts()).isEqualTo(maxAttempts));

    assertThat(attempts.get()).isEqualTo(maxAttempts);
  }

  // ==================== Only Retries Matching Exceptions ====================

  /**
   * Property: When a retryOn filter is set, exceptions that do not match are thrown immediately
   * without further retries.
   *
   * <p>Here we configure the policy to retry only on {@link IllegalStateException}. A task that
   * throws {@link IllegalArgumentException} should fail after a single attempt.
   */
  @Property
  @Label("Only retries matching exceptions (retryOn filter)")
  void onlyRetriesMatchingExceptions(
      @ForAll @net.jqwik.api.constraints.IntRange(min = 2, max = 10) int maxAttempts) {

    AtomicInteger attempts = new AtomicInteger(0);

    RetryPolicy policy =
        RetryPolicy.fixed(maxAttempts, Duration.ZERO).retryOn(IllegalStateException.class);

    VTask<String> task =
        () -> {
          attempts.incrementAndGet();
          throw new IllegalArgumentException("non-retryable");
        };

    VTask<String> retried = Retry.retryTask(task, policy);

    // IllegalArgumentException does not match the retryOn filter, so it should be thrown directly
    assertThatThrownBy(retried::run)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("non-retryable");

    // Only one attempt because the exception was not retryable
    assertThat(attempts.get()).isEqualTo(1);
  }

  // ==================== Linear Delay Calculation ====================

  /**
   * Property: For a linear backoff policy, the delay for attempt N equals {@code initialDelay * N}.
   *
   * <p>This verifies the delay calculation without actually sleeping by inspecting {@link
   * RetryPolicy#delayForAttempt(int)}.
   */
  @Property
  @Label("Delays follow linear backoff strategy")
  void delaysFollowLinearBackoff(
      @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 10) int attemptNumber,
      @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 200) int delayMillis) {

    RetryPolicy policy = RetryPolicy.linear(10, Duration.ofMillis(delayMillis));

    Duration delay = policy.delayForAttempt(attemptNumber);

    long expectedMillis;
    if (attemptNumber <= 1) {
      // First attempt returns initialDelay
      expectedMillis = delayMillis;
    } else {
      // Linear: initialDelay * attemptNumber, capped at maxDelay
      expectedMillis = (long) delayMillis * attemptNumber;
      long maxDelayMillis = Duration.ofMinutes(5).toMillis();
      expectedMillis = Math.min(expectedMillis, maxDelayMillis);
    }

    assertThat(delay).isEqualTo(Duration.ofMillis(expectedMillis));
  }

  // ==================== onRetry Called Exactly (attempts - 1) Times ====================

  /**
   * Property: When a task always fails and all attempts are exhausted, the {@code onRetry} listener
   * is called exactly {@code maxAttempts - 1} times.
   *
   * <p>The listener fires after each failed attempt except the last, because no retry follows the
   * final failure.
   */
  @Property
  @Label("onRetry called exactly (attempts - 1) times on exhaustion")
  void onRetryCalledCorrectNumberOfTimes(
      @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 10) int maxAttempts) {

    AtomicInteger retryListenerCalls = new AtomicInteger(0);

    RetryPolicy policy =
        RetryPolicy.fixed(maxAttempts, Duration.ZERO)
            .onRetry(_ -> retryListenerCalls.incrementAndGet());

    VTask<String> alwaysFails =
        () -> {
          throw new RuntimeException("always fails");
        };

    VTask<String> retried = Retry.retryTask(alwaysFails, policy);

    try {
      retried.run();
    } catch (RetryExhaustedException ignored) {
      // expected
    } catch (Throwable ignored) {
      // maxAttempts == 1 means no retries, original exception propagates
    }

    int expectedListenerCalls = Math.max(0, maxAttempts - 1);
    assertThat(retryListenerCalls.get()).isEqualTo(expectedListenerCalls);
  }
}
