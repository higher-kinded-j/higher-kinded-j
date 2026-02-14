// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Test suite for Scope - the fluent builder for structured concurrent computations. */
@DisplayName("Scope<T, R> Test Suite")
class ScopeTest {

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("allSucceed() creates scope with allSucceed joiner")
    void allSucceedCreatesScope() {
      Scope<String, List<String>> scope = Scope.allSucceed();

      assertThat(scope).isNotNull();
      assertThat(scope.taskCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("anySucceed() creates scope with anySucceed joiner")
    void anySucceedCreatesScope() {
      Scope<String, String> scope = Scope.anySucceed();

      assertThat(scope).isNotNull();
      assertThat(scope.taskCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("firstComplete() creates scope with firstComplete joiner")
    void firstCompleteCreatesScope() {
      Scope<String, String> scope = Scope.firstComplete();

      assertThat(scope).isNotNull();
    }

    @Test
    @DisplayName("accumulating() creates scope that collects errors")
    void accumulatingCreatesScope() {
      Scope<String, Validated<List<String>, List<String>>> scope =
          Scope.accumulating(Throwable::getMessage);

      assertThat(scope).isNotNull();
    }

    @Test
    @DisplayName("accumulating() validates non-null errorMapper")
    void accumulatingValidatesNonNullErrorMapper() {
      assertThatNullPointerException()
          .isThrownBy(() -> Scope.accumulating(null))
          .withMessageContaining("errorMapper must not be null");
    }

    @Test
    @DisplayName("withJoiner() creates scope with custom joiner")
    void withJoinerCreatesScope() {
      ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();
      Scope<String, List<String>> scope = Scope.withJoiner(joiner);

      assertThat(scope).isNotNull();
    }

    @Test
    @DisplayName("withJoiner() validates non-null joiner")
    void withJoinerValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Scope.withJoiner(null))
          .withMessageContaining("joiner must not be null");
    }
  }

  @Nested
  @DisplayName("Fork Operations")
  class ForkOperationsTests {

    @Test
    @DisplayName("fork() adds a single task")
    void forkAddsSingleTask() {
      Scope<String, List<String>> scope = Scope.<String>allSucceed().fork(VTask.succeed("hello"));

      assertThat(scope.taskCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("fork() can chain multiple tasks")
    void forkChainsMultipleTasks() {
      Scope<String, List<String>> scope =
          Scope.<String>allSucceed()
              .fork(VTask.succeed("first"))
              .fork(VTask.succeed("second"))
              .fork(VTask.succeed("third"));

      assertThat(scope.taskCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("fork() validates non-null task")
    void forkValidatesNonNull() {
      Scope<String, List<String>> scope = Scope.allSucceed();

      assertThatNullPointerException()
          .isThrownBy(() -> scope.fork(null))
          .withMessageContaining("task must not be null");
    }

    @Test
    @DisplayName("forkAll() adds multiple tasks at once")
    void forkAllAddsMultipleTasks() {
      List<VTask<String>> tasks =
          List.of(VTask.succeed("first"), VTask.succeed("second"), VTask.succeed("third"));

      Scope<String, List<String>> scope = Scope.<String>allSucceed().forkAll(tasks);

      assertThat(scope.taskCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("forkAll() validates non-null tasks list")
    void forkAllValidatesNonNullTasks() {
      Scope<String, List<String>> scope = Scope.allSucceed();

      assertThatNullPointerException()
          .isThrownBy(() -> scope.forkAll(null))
          .withMessageContaining("tasksToFork must not be null");
    }
  }

  @Nested
  @DisplayName("Configuration Methods")
  class ConfigurationMethodsTests {

    @Test
    @DisplayName("timeout() sets the timeout")
    void timeoutSetsTimeout() {
      Scope<String, List<String>> scope = Scope.<String>allSucceed().timeout(Duration.ofSeconds(5));

      assertThat(scope.hasTimeout()).isTrue();
      assertThat(scope.getTimeout().isJust()).isTrue();
      assertThat(scope.getTimeout().orElse(null)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("hasTimeout() returns false when no timeout set")
    void hasTimeoutReturnsFalseWhenNotSet() {
      Scope<String, List<String>> scope = Scope.allSucceed();

      assertThat(scope.hasTimeout()).isFalse();
    }

    @Test
    @DisplayName("getTimeout() returns Nothing when no timeout set")
    void getTimeoutReturnsNothingWhenNotSet() {
      Scope<String, List<String>> scope = Scope.allSucceed();

      assertThat(scope.getTimeout().isNothing()).isTrue();
    }

    @Test
    @DisplayName("timeout() validates non-null duration")
    void timeoutValidatesNonNull() {
      Scope<String, List<String>> scope = Scope.allSucceed();

      assertThatNullPointerException()
          .isThrownBy(() -> scope.timeout(null))
          .withMessageContaining("timeout must not be null");
    }

    @Test
    @DisplayName("named() sets the scope name")
    void namedSetsName() {
      Scope<String, List<String>> scope = Scope.<String>allSucceed().named("test-scope");

      // Name is used internally for debugging; verify method works
      assertThat(scope).isNotNull();
    }
  }

  @Nested
  @DisplayName("Join Operations - AllSucceed")
  class JoinAllSucceedTests {

    @Test
    @DisplayName("join() collects all successful results")
    void joinCollectsAllResults() {
      VTask<List<String>> result =
          Scope.<String>allSucceed()
              .fork(VTask.succeed("first"))
              .fork(VTask.succeed("second"))
              .fork(VTask.succeed("third"))
              .join();

      List<String> values = result.run();

      assertThat(values).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("join() executes tasks in parallel")
    void joinExecutesInParallel() {
      AtomicInteger counter = new AtomicInteger(0);

      VTask<List<Integer>> result =
          Scope.<Integer>allSucceed()
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(10);
                        return counter.incrementAndGet();
                      }))
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(10);
                        return counter.incrementAndGet();
                      }))
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(10);
                        return counter.incrementAndGet();
                      }))
              .join();

      List<Integer> values = result.run();

      // All three tasks should have executed
      assertThat(values).hasSize(3);
      assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("join() fails if any task fails")
    void joinFailsIfAnyTaskFails() {
      VTask<List<String>> result =
          Scope.<String>allSucceed()
              .fork(VTask.succeed("success"))
              .fork(VTask.fail(new RuntimeException("task failed")))
              .join();

      assertThatThrownBy(result::run)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("task failed");
    }

    @Test
    @DisplayName("join() with empty scope returns empty list")
    void joinWithEmptyScopeReturnsEmptyList() {
      VTask<List<String>> result = Scope.<String>allSucceed().join();

      List<String> values = result.run();

      assertThat(values).isEmpty();
    }
  }

  @Nested
  @DisplayName("Join Operations - AnySucceed")
  class JoinAnySucceedTests {

    @Test
    @DisplayName("join() returns first successful result")
    void joinReturnsFirstSuccess() {
      VTask<String> result =
          Scope.<String>anySucceed()
              .fork(VTask.succeed("fast"))
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(1000);
                        return "slow";
                      }))
              .join();

      String value = result.run();

      assertThat(value).isEqualTo("fast");
    }
  }

  @Nested
  @DisplayName("Join Operations - FirstComplete")
  class JoinFirstCompleteTests {

    @Test
    @DisplayName("join() returns first completed success")
    void joinReturnsFirstCompletedSuccess() {
      VTask<String> result =
          Scope.<String>firstComplete()
              .fork(VTask.succeed("fast"))
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(1000);
                        return "slow";
                      }))
              .join();

      String value = result.run();

      assertThat(value).isEqualTo("fast");
    }

    @Test
    @DisplayName("join() returns first completed failure")
    void joinReturnsFirstCompletedFailure() {
      VTask<String> result =
          Scope.<String>firstComplete()
              .fork(VTask.fail(new RuntimeException("fast failure")))
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(1000);
                        return "slow success";
                      }))
              .join();

      assertThatThrownBy(result::run)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("fast failure");
    }
  }

  @Nested
  @DisplayName("Timeout Operations")
  class TimeoutOperationsTests {

    @Test
    @DisplayName("join() times out when tasks exceed timeout")
    void joinTimesOut() {
      VTask<List<String>> result =
          Scope.<String>allSucceed()
              .timeout(Duration.ofMillis(50))
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(5000);
                        return "slow";
                      }))
              .join();

      assertThatThrownBy(result::run)
          .isInstanceOf(VTaskExecutionException.class)
          .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("join() completes within timeout")
    void joinCompletesWithinTimeout() {
      VTask<List<String>> result =
          Scope.<String>allSucceed()
              .timeout(Duration.ofSeconds(5))
              .fork(VTask.succeed("fast"))
              .join();

      List<String> values = result.run();

      assertThat(values).containsExactly("fast");
    }
  }

  @Nested
  @DisplayName("Join Operations - Accumulating")
  class JoinAccumulatingTests {

    @Test
    @DisplayName("join() returns Valid when all succeed")
    void joinReturnsValidWhenAllSucceed() {
      VTask<Validated<List<String>, List<String>>> result =
          Scope.<String, String>accumulating(Throwable::getMessage)
              .fork(VTask.succeed("first"))
              .fork(VTask.succeed("second"))
              .join();

      Validated<List<String>, List<String>> validated = result.run();

      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    @DisplayName("join() accumulates all errors")
    void joinAccumulatesAllErrors() {
      VTask<Validated<List<String>, List<String>>> result =
          Scope.<String, String>accumulating(Throwable::getMessage)
              .fork(VTask.succeed("success"))
              .fork(VTask.fail(new RuntimeException("error1")))
              .fork(VTask.fail(new RuntimeException("error2")))
              .join();

      Validated<List<String>, List<String>> validated = result.run();

      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).containsExactlyInAnyOrder("error1", "error2");
    }
  }

  @Nested
  @DisplayName("Safe Join Operations")
  class SafeJoinTests {

    @Test
    @DisplayName("joinSafe() returns Try.success on success")
    void joinSafeReturnsSuccessOnSuccess() {
      VTask<Try<List<String>>> result =
          Scope.<String>allSucceed().fork(VTask.succeed("test")).joinSafe();

      Try<List<String>> tryResult = result.run();

      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).containsExactly("test");
    }

    @Test
    @DisplayName("joinSafe() returns Try.failure on failure")
    void joinSafeReturnsFailureOnFailure() {
      VTask<Try<List<String>>> result =
          Scope.<String>allSucceed().fork(VTask.fail(new RuntimeException("error"))).joinSafe();

      Try<List<String>> tryResult = result.run();

      assertThat(tryResult.isFailure()).isTrue();
      assertThat(((Try.Failure<List<String>>) tryResult).cause()).hasMessageContaining("error");
    }

    @Test
    @DisplayName("joinEither() returns Right on success")
    void joinEitherReturnsRightOnSuccess() {
      VTask<Either<Throwable, List<String>>> result =
          Scope.<String>allSucceed().fork(VTask.succeed("test")).joinEither();

      Either<Throwable, List<String>> either = result.run();

      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).containsExactly("test");
    }

    @Test
    @DisplayName("joinEither() returns Left on failure")
    void joinEitherReturnsLeftOnFailure() {
      VTask<Either<Throwable, List<String>>> result =
          Scope.<String>allSucceed().fork(VTask.fail(new RuntimeException("error"))).joinEither();

      Either<Throwable, List<String>> either = result.run();

      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).hasMessageContaining("error");
    }

    @Test
    @DisplayName("joinMaybe() returns Just on success")
    void joinMaybeReturnsJustOnSuccess() {
      VTask<Maybe<List<String>>> result =
          Scope.<String>allSucceed().fork(VTask.succeed("test")).joinMaybe();

      Maybe<List<String>> maybe = result.run();

      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse(null)).containsExactly("test");
    }

    @Test
    @DisplayName("joinMaybe() returns Nothing on failure")
    void joinMaybeReturnsNothingOnFailure() {
      VTask<Maybe<List<String>>> result =
          Scope.<String>allSucceed().fork(VTask.fail(new RuntimeException("error"))).joinMaybe();

      Maybe<List<String>> maybe = result.run();

      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Real-World Patterns")
  class RealWorldPatternsTests {

    @Test
    @DisplayName("fetch multiple resources in parallel")
    void fetchMultipleResourcesInParallel() {
      // Simulate fetching user data from multiple sources
      VTask<String> fetchName = VTask.of(() -> "John");
      VTask<String> fetchEmail = VTask.of(() -> "john@example.com");
      VTask<String> fetchRole = VTask.of(() -> "admin");

      VTask<List<String>> allData =
          Scope.<String>allSucceed().fork(fetchName).fork(fetchEmail).fork(fetchRole).join();

      List<String> result = allData.run();

      assertThat(result).containsExactly("John", "john@example.com", "admin");
    }

    @Test
    @DisplayName("validation with error accumulation")
    void validationWithErrorAccumulation() {
      record ValidationError(String field, String message) {}

      VTask<Validated<List<ValidationError>, List<String>>> validation =
          Scope.<ValidationError, String>accumulating(
                  t -> new ValidationError("unknown", t.getMessage()))
              .fork(VTask.succeed("valid1"))
              .fork(VTask.fail(new RuntimeException("invalid name")))
              .fork(VTask.fail(new RuntimeException("invalid email")))
              .join();

      Validated<List<ValidationError>, List<String>> result = validation.run();

      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).hasSize(2);
    }

    @Test
    @DisplayName("racing multiple service calls")
    void racingMultipleServiceCalls() {
      AtomicBoolean slowCalled = new AtomicBoolean(false);

      VTask<String> result =
          Scope.<String>anySucceed()
              .fork(VTask.succeed("fast-response"))
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(100);
                        slowCalled.set(true);
                        return "slow-response";
                      }))
              .join();

      String value = result.run();

      assertThat(value).isEqualTo("fast-response");
      // Note: The slow task may or may not complete depending on timing
    }
  }
}
