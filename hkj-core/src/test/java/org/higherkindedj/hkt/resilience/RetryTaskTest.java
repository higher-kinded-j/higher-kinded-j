// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for VTask-native retry methods in {@link Retry}.
 *
 * <p>Tests cover retryTask, retryTaskWithFallback, retryTaskWithRecovery, retry event callbacks,
 * linear backoff, and lazy evaluation semantics.
 */
@DisplayName("Retry VTask Test Suite")
class RetryTaskTest {

  @Nested
  @DisplayName("retryTask() basic behaviour")
  class VTaskRetryTests {

    @Test
    @DisplayName("retryTask() succeeds immediately when task does not fail")
    void retryTaskSucceedsImmediately() {
      AtomicInteger attempts = new AtomicInteger(0);
      VTask<String> task =
          () -> {
            attempts.incrementAndGet();
            return "ok";
          };

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<String> retried = Retry.retryTask(task, policy);

      String result = retried.run();

      assertThat(result).isEqualTo("ok");
      assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("retryTask() succeeds after transient failures")
    void retryTaskSucceedsAfterRetry() {
      AtomicInteger attempts = new AtomicInteger(0);
      VTask<String> task =
          () -> {
            if (attempts.incrementAndGet() < 3) {
              throw new RuntimeException("transient");
            }
            return "recovered";
          };

      RetryPolicy policy = RetryPolicy.fixed(5, Duration.ofMillis(1));
      VTask<String> retried = Retry.retryTask(task, policy);

      String result = retried.run();

      assertThat(result).isEqualTo("recovered");
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("retryTask() throws RetryExhaustedException when all attempts fail")
    void retryTaskThrowsWhenExhausted() {
      AtomicInteger attempts = new AtomicInteger(0);
      VTask<String> task =
          () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("always fails");
          };

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<String> retried = Retry.retryTask(task, policy);

      assertThatThrownBy(retried::run)
          .isInstanceOf(RetryExhaustedException.class)
          .hasMessageContaining("3 attempts")
          .hasCauseInstanceOf(RuntimeException.class);

      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("retryTask() with maxAttempts convenience uses exponential backoff")
    void retryTaskConvenienceWithMaxAttempts() {
      AtomicInteger attempts = new AtomicInteger(0);
      VTask<String> task =
          () -> {
            if (attempts.incrementAndGet() < 2) {
              throw new RuntimeException("transient");
            }
            return "done";
          };

      VTask<String> retried = Retry.retryTask(task, 3);

      String result = retried.run();

      assertThat(result).isEqualTo("done");
      assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("retryTask() validates null arguments")
    void retryTaskValidatesNullArguments() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<String> task = () -> "value";

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.retryTask(null, policy))
          .withMessageContaining("task must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.retryTask(task, (RetryPolicy) null))
          .withMessageContaining("policy must not be null");
    }
  }

  @Nested
  @DisplayName("retryTaskWithFallback() behaviour")
  class VTaskRetryWithFallbackTests {

    @Test
    @DisplayName("retryTaskWithFallback() returns fallback value on exhaustion")
    void retryTaskWithFallbackReturnsFallbackOnExhaustion() {
      AtomicInteger attempts = new AtomicInteger(0);
      VTask<String> task =
          () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("always fails");
          };

      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));
      VTask<String> retried =
          Retry.retryTaskWithFallback(task, policy, cause -> "fallback: " + cause.getMessage());

      String result = retried.run();

      assertThat(result).isEqualTo("fallback: always fails");
      assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("retryTaskWithFallback() returns normal result when task succeeds")
    void retryTaskWithFallbackReturnsNormalResultOnSuccess() {
      VTask<String> task = () -> "success";

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<String> retried = Retry.retryTaskWithFallback(task, policy, cause -> "fallback");

      String result = retried.run();

      assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName(
        "retryTaskWithFallback() fallback receives the original cause, not RetryExhaustedException")
    void retryTaskWithFallbackReceivesOriginalCause() {
      RuntimeException originalCause = new RuntimeException("root cause");
      VTask<String> task =
          () -> {
            throw originalCause;
          };

      RetryPolicy policy = RetryPolicy.fixed(1, Duration.ofMillis(1));
      List<Throwable> capturedCauses = new ArrayList<>();
      VTask<String> retried =
          Retry.retryTaskWithFallback(
              task,
              policy,
              cause -> {
                capturedCauses.add(cause);
                return "recovered";
              });

      retried.run();

      assertThat(capturedCauses).hasSize(1);
      assertThat(capturedCauses.get(0)).isSameAs(originalCause);
    }

    @Test
    @DisplayName("retryTaskWithFallback() validates null arguments")
    void retryTaskWithFallbackValidatesNullArguments() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<String> task = () -> "value";

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.retryTaskWithFallback(null, policy, cause -> "fb"))
          .withMessageContaining("task must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.retryTaskWithFallback(task, null, cause -> "fb"))
          .withMessageContaining("policy must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.retryTaskWithFallback(task, policy, null))
          .withMessageContaining("fallback must not be null");
    }
  }

  @Nested
  @DisplayName("retryTaskWithRecovery() behaviour")
  class VTaskRetryWithRecoveryTests {

    @Test
    @DisplayName("retryTaskWithRecovery() runs recovery task on exhaustion")
    void retryTaskWithRecoveryRunsRecoveryOnExhaustion() {
      AtomicInteger attempts = new AtomicInteger(0);
      VTask<String> task =
          () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("always fails");
          };

      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));
      VTask<String> retried =
          Retry.retryTaskWithRecovery(
              task, policy, cause -> () -> "recovered via: " + cause.getMessage());

      String result = retried.run();

      assertThat(result).isEqualTo("recovered via: always fails");
      assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("retryTaskWithRecovery() returns normal result when task succeeds")
    void retryTaskWithRecoveryReturnsNormalResultOnSuccess() {
      VTask<String> task = () -> "success";

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<String> retried = Retry.retryTaskWithRecovery(task, policy, cause -> () -> "recovery");

      String result = retried.run();

      assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("retryTaskWithRecovery() recovery receives original cause")
    void retryTaskWithRecoveryReceivesOriginalCause() {
      RuntimeException originalCause = new RuntimeException("root");
      VTask<String> task =
          () -> {
            throw originalCause;
          };

      RetryPolicy policy = RetryPolicy.fixed(1, Duration.ofMillis(1));
      List<Throwable> capturedCauses = new ArrayList<>();
      VTask<String> retried =
          Retry.retryTaskWithRecovery(
              task,
              policy,
              cause -> {
                capturedCauses.add(cause);
                return () -> "recovered";
              });

      retried.run();

      assertThat(capturedCauses).hasSize(1);
      assertThat(capturedCauses.get(0)).isSameAs(originalCause);
    }

    @Test
    @DisplayName("retryTaskWithRecovery() validates null arguments")
    void retryTaskWithRecoveryValidatesNullArguments() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<String> task = () -> "value";

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.retryTaskWithRecovery(null, policy, cause -> () -> "r"))
          .withMessageContaining("task must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.retryTaskWithRecovery(task, null, cause -> () -> "r"))
          .withMessageContaining("policy must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> Retry.retryTaskWithRecovery(task, policy, null))
          .withMessageContaining("recovery must not be null");
    }
  }

  @Nested
  @DisplayName("RetryEvent callback invocation")
  class RetryEventTests {

    @Test
    @DisplayName("onRetry listener is invoked on each retry attempt")
    void onRetryListenerIsInvokedOnEachRetry() {
      AtomicInteger attempts = new AtomicInteger(0);
      List<RetryEvent> events = new ArrayList<>();

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1)).onRetry(events::add);

      VTask<String> task =
          () -> {
            if (attempts.incrementAndGet() < 3) {
              throw new RuntimeException("fail #" + attempts.get());
            }
            return "ok";
          };

      VTask<String> retried = Retry.retryTask(task, policy);
      String result = retried.run();

      assertThat(result).isEqualTo("ok");
      // Listener is called after each failed attempt except the last successful one
      // Attempts 1 and 2 fail, triggering 2 events
      assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("RetryEvent contains correct attemptNumber")
    void retryEventContainsCorrectAttemptNumber() {
      List<RetryEvent> events = new ArrayList<>();
      AtomicInteger attempts = new AtomicInteger(0);

      RetryPolicy policy = RetryPolicy.fixed(4, Duration.ofMillis(1)).onRetry(events::add);

      VTask<String> task =
          () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("always fails");
          };

      VTask<String> retried = Retry.retryTask(task, policy);

      assertThatThrownBy(retried::run).isInstanceOf(RetryExhaustedException.class);

      // 3 retry events for attempts 1, 2, 3 (attempt 4 is the last, no event after it)
      assertThat(events).hasSize(3);
      assertThat(events.get(0).attemptNumber()).isEqualTo(1);
      assertThat(events.get(1).attemptNumber()).isEqualTo(2);
      assertThat(events.get(2).attemptNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("RetryEvent contains the exception that triggered the retry")
    void retryEventContainsLastException() {
      List<RetryEvent> events = new ArrayList<>();
      AtomicInteger attempts = new AtomicInteger(0);

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1)).onRetry(events::add);

      VTask<String> task =
          () -> {
            int attempt = attempts.incrementAndGet();
            throw new RuntimeException("error-" + attempt);
          };

      VTask<String> retried = Retry.retryTask(task, policy);

      assertThatThrownBy(retried::run).isInstanceOf(RetryExhaustedException.class);

      assertThat(events).hasSize(2);
      assertThat(events.get(0).lastException()).hasMessage("error-1");
      assertThat(events.get(1).lastException()).hasMessage("error-2");
    }

    @Test
    @DisplayName("RetryEvent has a non-null timestamp")
    void retryEventHasTimestamp() {
      List<RetryEvent> events = new ArrayList<>();

      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1)).onRetry(events::add);

      VTask<String> task =
          () -> {
            throw new RuntimeException("fail");
          };

      VTask<String> retried = Retry.retryTask(task, policy);

      assertThatThrownBy(retried::run).isInstanceOf(RetryExhaustedException.class);

      assertThat(events).hasSize(1);
      assertThat(events.get(0).timestamp()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Linear backoff strategy")
  class LinearBackoffTests {

    @Test
    @DisplayName("retryTask() with linear policy uses linearly increasing delays")
    void retryTaskWithLinearBackoff() {
      AtomicInteger attempts = new AtomicInteger(0);
      List<RetryEvent> events = new ArrayList<>();

      RetryPolicy policy = RetryPolicy.linear(4, Duration.ofMillis(10)).onRetry(events::add);

      VTask<String> task =
          () -> {
            if (attempts.incrementAndGet() < 4) {
              throw new RuntimeException("fail");
            }
            return "ok";
          };

      VTask<String> retried = Retry.retryTask(task, policy);
      String result = retried.run();

      assertThat(result).isEqualTo("ok");
      assertThat(attempts.get()).isEqualTo(4);
      // 3 retry events
      assertThat(events).hasSize(3);

      // Linear delay: initialDelay * attemptNumber
      // Attempt 1 delay: 10ms (delayForAttempt(1) = initialDelay)
      // Attempt 2 delay: 20ms (delayForAttempt(2) = 10 * 2)
      // Attempt 3 delay: 30ms (delayForAttempt(3) = 10 * 3)
      assertThat(events.get(0).nextDelay()).isEqualTo(Duration.ofMillis(10));
      assertThat(events.get(1).nextDelay()).isEqualTo(Duration.ofMillis(20));
      assertThat(events.get(2).nextDelay()).isEqualTo(Duration.ofMillis(30));
    }

    @Test
    @DisplayName("linear policy delays increase linearly via delayForAttempt")
    void linearPolicyDelaysIncreaseLinearly() {
      RetryPolicy policy = RetryPolicy.linear(5, Duration.ofMillis(10));

      assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMillis(10));
      assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofMillis(20));
      assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMillis(30));
      assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMillis(40));
    }
  }

  @Nested
  @DisplayName("Lazy evaluation semantics")
  class LazyEvaluationTests {

    @Test
    @DisplayName("retryTask() does not execute until run() is called")
    void retryTaskIsLazy() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<String> task =
          () -> {
            counter.incrementAndGet();
            return "value";
          };

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      // Creating the retry task should not execute anything
      VTask<String> retried = Retry.retryTask(task, policy);

      assertThat(counter.get()).isEqualTo(0);

      // Only when run() is called should it execute
      retried.run();

      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("retryTaskWithFallback() does not execute until run() is called")
    void retryTaskWithFallbackIsLazy() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<String> task =
          () -> {
            counter.incrementAndGet();
            return "value";
          };

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<String> retried = Retry.retryTaskWithFallback(task, policy, cause -> "fallback");

      assertThat(counter.get()).isEqualTo(0);

      retried.run();

      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("retryTaskWithRecovery() does not execute until run() is called")
    void retryTaskWithRecoveryIsLazy() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<String> task =
          () -> {
            counter.incrementAndGet();
            return "value";
          };

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<String> retried = Retry.retryTaskWithRecovery(task, policy, cause -> () -> "recovery");

      assertThat(counter.get()).isEqualTo(0);

      retried.run();

      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("retryTask() re-executes on each run() call")
    void retryTaskReExecutesOnEachRun() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<Integer> task = counter::incrementAndGet;

      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      VTask<Integer> retried = Retry.retryTask(task, policy);

      Integer first = retried.run();
      Integer second = retried.run();

      assertThat(first).isEqualTo(1);
      assertThat(second).isEqualTo(2);
      assertThat(counter.get()).isEqualTo(2);
    }
  }
}
