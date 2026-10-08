// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Test suite for Scope - the fluent builder for structured concurrent computations. */
@DisplayName("Scope<T, R> Test Suite")
class ScopeTest {

  private static final ScopedValue<String> REQUEST = ScopedValue.newInstance();

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
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void forkAllValidatesNonNullTasks() {
      Scope<String, List<String>> scope = Scope.allSucceed();

      assertThatNullPointerException()
          .isThrownBy(() -> scope.forkAll(null))
          .withMessageContaining("tasksToFork must not be null");
    }

    @Test
    @DisplayName("forkAll() rejects a null task")
    void forkAllRejectsANullTask() {
      List<VTask<String>> withNull = new ArrayList<>();
      withNull.add(VTask.succeed("fine"));
      withNull.add(null);

      assertThatNullPointerException()
          .isThrownBy(() -> Scope.<String>allSucceed().forkAll(withNull))
          .withMessage("tasksToFork must not contain null");
    }
  }

  @Nested
  @DisplayName("Configuration Methods")
  class ConfigurationMethodsTests {

    @Test
    @DisplayName("timeout() sets the timeout")
    @SuppressWarnings("DataFlowIssue") // present is asserted first, so the null fallback is unused
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
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void timeoutValidatesNonNull() {
      Scope<String, List<String>> scope = Scope.allSucceed();

      assertThatNullPointerException()
          .isThrownBy(() -> scope.timeout(null))
          .withMessageContaining("timeout must not be null");
    }

    @Test
    @DisplayName("named() names the scope in a thread dump")
    void namedNamesTheScopeInAThreadDump(@TempDir Path dir) throws IOException {
      Path dump = dir.resolve("threads.json");
      HotSpotDiagnosticMXBean diagnostics =
          ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
      VTask<String> dumpThreads =
          VTask.of(
              () -> {
                diagnostics.dumpThreads(
                    dump.toAbsolutePath().toString(),
                    HotSpotDiagnosticMXBean.ThreadDumpFormat.JSON);
                return "dumped";
              });

      Scope.<String>allSucceed().named("quote-fan-out").fork(dumpThreads).join().run();

      assertThat(Files.readString(dump)).contains("quote-fan-out");
    }

    @Test
    @DisplayName("named() validates non-null name")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void namedValidatesNonNull() {
      Scope<String, List<String>> scope = Scope.allSucceed();

      assertThatNullPointerException()
          .isThrownBy(() -> scope.named(null))
          .withMessage("name must not be null");
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

    @Test
    @DisplayName("firstComplete cancels the subtasks still running")
    void firstCompleteCancelsTheSubtasksStillRunning() {
      CountDownLatch slowStarted = new CountDownLatch(1);
      CountDownLatch neverReleased = new CountDownLatch(1);
      AtomicBoolean slowCancelled = new AtomicBoolean();
      VTask<String> fast =
          VTask.of(
              () -> {
                slowStarted.await();
                return "fast";
              });
      VTask<String> slow =
          VTask.of(
              () -> {
                slowStarted.countDown();
                try {
                  neverReleased.await();
                  return "slow";
                } catch (InterruptedException e) {
                  slowCancelled.set(true);
                  throw e;
                }
              });

      try {
        String winner =
            Scope.<String>firstComplete()
                .fork(fast)
                .fork(slow)
                .join()
                .timeout(Duration.ofSeconds(10))
                .run();

        assertThat(winner).isEqualTo("fast");
        assertThat(slowCancelled).isTrue();
      } finally {
        neverReleased.countDown();
      }
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
    @DisplayName("a timeout cancels the subtasks still running")
    void aTimeoutCancelsTheSubtasksStillRunning() {
      CountDownLatch neverReleased = new CountDownLatch(1);
      AtomicBoolean cancelled = new AtomicBoolean();
      VTask<List<String>> join =
          Scope.<String>allSucceed()
              .timeout(Duration.ofMillis(200))
              .fork(VTask.succeed("fast"))
              .fork(
                  VTask.of(
                      () -> {
                        try {
                          neverReleased.await();
                          return "slow";
                        } catch (InterruptedException e) {
                          cancelled.set(true);
                          throw e;
                        }
                      }))
              .join();

      try {
        assertThatThrownBy(join::run)
            .isInstanceOf(VTaskExecutionException.class)
            .cause()
            .isInstanceOf(TimeoutException.class)
            .hasMessage("Scope timed out after PT0.2S");
        assertThat(cancelled).isTrue();
      } finally {
        neverReleased.countDown();
      }
    }

    @Test
    @DisplayName("a timeout fires while every common-pool thread waits on a timed scope")
    void aTimeoutFiresWhileEveryCommonPoolThreadWaits() throws Exception {
      CountDownLatch neverReleased = new CountDownLatch(1);
      VTask<List<String>> join =
          Scope.<String>allSucceed()
              .timeout(Duration.ofMillis(200))
              .fork(
                  VTask.of(
                      () -> {
                        neverReleased.await();
                        return "slow";
                      }))
              .join();
      List<ForkJoinTask<Try<List<String>>>> runs = new ArrayList<>();

      try {
        for (int i = 0; i < ForkJoinPool.getCommonPoolParallelism(); i++) {
          runs.add(ForkJoinPool.commonPool().submit(() -> join.runSafe()));
        }
        for (ForkJoinTask<Try<List<String>>> run : runs) {
          assertThat(run.get(10, TimeUnit.SECONDS))
              .isInstanceOfSatisfying(
                  Try.Failure.class,
                  failure -> assertThat(failure.cause()).isInstanceOf(TimeoutException.class));
        }
      } finally {
        neverReleased.countDown();
      }
    }

    @Test
    @DisplayName("a timed scope that finishes early does not wait for its timeout")
    void aTimedScopeThatFinishesEarlyDoesNotWaitForItsTimeout() {
      VTask<List<String>> join =
          Scope.<String>allSucceed()
              .timeout(Duration.ofMinutes(1))
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(50);
                        return "done";
                      }))
              .join();

      List<String> result = assertTimeoutPreemptively(Duration.ofSeconds(10), join::run);

      assertThat(result).containsExactly("done");
    }

    @Test
    @DisplayName("a timed scope with nothing forked returns at once")
    void aTimedScopeWithNothingForkedReturnsAtOnce() {
      VTask<List<String>> join = Scope.<String>allSucceed().timeout(Duration.ofMinutes(1)).join();

      List<String> result = assertTimeoutPreemptively(Duration.ofSeconds(10), join::run);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("a timed race returns its winner and cancels the rest")
    void aTimedRaceReturnsItsWinnerAndCancelsTheRest() {
      CountDownLatch neverReleased = new CountDownLatch(1);
      VTask<String> race =
          Scope.<String>anySucceed()
              .timeout(Duration.ofMinutes(1))
              .fork(
                  VTask.of(
                      () -> {
                        neverReleased.await();
                        return "slow";
                      }))
              .fork(VTask.succeed("fast"))
              .join();

      try {
        String winner = assertTimeoutPreemptively(Duration.ofSeconds(10), race::run);

        assertThat(winner).isEqualTo("fast");
      } finally {
        neverReleased.countDown();
      }
    }

    @Test
    @DisplayName("the subtasks of a timed scope see the caller's scoped values")
    void theSubtasksOfATimedScopeSeeTheCallersScopedValues() {
      VTask<List<String>> join =
          Scope.<String>allSucceed()
              .timeout(Duration.ofSeconds(5))
              .fork(VTask.of(REQUEST::get))
              .join();

      List<String> seen = ScopedValue.where(REQUEST, "request-1").call(join::run);

      assertThat(seen).containsExactly("request-1");
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
  @SuppressWarnings("DataFlowIssue") // non-null in this fixture
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
  @SuppressWarnings("DataFlowIssue") // non-null in this fixture
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
    @SuppressWarnings("DataFlowIssue") // non-null in this fixture
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
      VTask<String> result =
          Scope.<String>anySucceed()
              .fork(VTask.succeed("fast-response"))
              .fork(
                  VTask.of(
                      () -> {
                        Thread.sleep(100);
                        return "slow-response";
                      }))
              .join();

      String value = result.run();

      assertThat(value).isEqualTo("fast-response");
      // Note: The slow task may or may not complete depending on timing
    }
  }

  @Nested
  @DisplayName("Each run of the VTask from join() has its own Joiner")
  class EachRunHasItsOwnJoinerTests {

    @Test
    @DisplayName("allSucceed gives the same list on a second run")
    void allSucceedGivesTheSameListOnASecondRun() {
      VTask<List<Integer>> join =
          Scope.<Integer>allSucceed().fork(VTask.succeed(1)).fork(VTask.succeed(2)).join();

      assertThat(join.run()).containsExactly(1, 2);
      assertThat(join.run()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("accumulating gives the same errors on a second run")
    @SuppressWarnings("DataFlowIssue") // non-null in this fixture
    void accumulatingGivesTheSameErrorsOnASecondRun() {
      VTask<Validated<List<String>, List<String>>> join =
          Scope.<String, String>accumulating(Throwable::getMessage)
              .fork(VTask.succeed("fine"))
              .fork(VTask.fail(new RuntimeException("broke")))
              .join();

      assertThat(join.run().getError()).containsExactly("broke");
      assertThat(join.run().getError()).containsExactly("broke");
    }

    @Test
    @DisplayName("anySucceed and firstComplete answer from the run that asked")
    void racesAnswerFromTheRunThatAsked() {
      AtomicInteger anyCalls = new AtomicInteger();
      VTask<Integer> any =
          Scope.<Integer>anySucceed().fork(VTask.of(anyCalls::incrementAndGet)).join();
      AtomicInteger firstCalls = new AtomicInteger();
      VTask<Integer> first =
          Scope.<Integer>firstComplete().fork(VTask.of(firstCalls::incrementAndGet)).join();

      assertThat(List.of(any.run(), any.run())).containsExactly(1, 2);
      assertThat(List.of(first.run(), first.run())).containsExactly(1, 2);
    }

    @Test
    @DisplayName("withJoiner answers from the run that asked")
    void withJoinerAnswersFromTheRunThatAsked() {
      AtomicInteger calls = new AtomicInteger();
      VTask<Either<List<String>, Integer>> race =
          Scope.withJoiner(ScopeJoiner.<String, Integer>firstSuccessEither())
              .fork(VTask.of(() -> Either.<String, Integer>right(calls.incrementAndGet())))
              .join();

      assertThat(List.of(race.run(), race.run())).containsExactly(Either.right(1), Either.right(2));
    }

    @Test
    @DisplayName("a run after a failed one gives its own answer")
    void aRunAfterAFailedOneGivesItsOwnAnswer() {
      VTask<List<Integer>> join =
          Scope.<Integer>allSucceed().fork(failsOnFirstCall()).fork(VTask.succeed(2)).join();

      assertThatThrownBy(join::run)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("transient");
      assertThat(join.run()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("runs at the same time each get their own result")
    void runsAtTheSameTimeEachGetTheirOwnResult() throws Exception {
      int runs = 8;
      CountDownLatch everySubtaskStarted = new CountDownLatch(runs * 2);
      VTask<Integer> one = waitingForEveryOther(everySubtaskStarted, 1);
      VTask<Integer> two = waitingForEveryOther(everySubtaskStarted, 2);
      VTask<List<Integer>> join = Scope.<Integer>allSucceed().fork(one).fork(two).join();

      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Future<List<Integer>>> results =
            IntStream.range(0, runs).mapToObj(_ -> executor.submit(() -> join.run())).toList();
        for (Future<List<Integer>> result : results) {
          assertThat(result.get(10, TimeUnit.SECONDS)).containsExactly(1, 2);
        }
      }
    }

    private static VTask<Integer> failsOnFirstCall() {
      AtomicInteger calls = new AtomicInteger();
      return VTask.of(
          () -> {
            if (calls.incrementAndGet() == 1) {
              throw new IllegalStateException("transient");
            }
            return 1;
          });
    }

    private static VTask<Integer> waitingForEveryOther(
        CountDownLatch everySubtaskStarted, int value) {
      return VTask.of(
          () -> {
            everySubtaskStarted.countDown();
            assertThat(everySubtaskStarted.await(10, TimeUnit.SECONDS)).isTrue();
            return value;
          });
    }
  }
}
