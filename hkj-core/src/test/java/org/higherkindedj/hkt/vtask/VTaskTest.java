// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.assertions.VTaskAssert.assertThatVTask;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.test.fixtures.HeldTask;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VTask Core Operations Test Suite")
class VTaskTest {

  private static final int TEST_VALUE = 42;

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("of() creates VTask from Callable")
    void ofCreatesVTaskFromCallable() {
      VTask<Integer> task = VTask.of(() -> TEST_VALUE);
      assertThat(task.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("of() with null callable throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void ofWithNullCallableThrows() {
      assertThatThrownBy(() -> VTask.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("delay() creates lazy VTask")
    void delayCreatesLazyVTask() {
      AtomicInteger counter = new AtomicInteger(0);

      VTask<Integer> task = VTask.delay(counter::incrementAndGet);

      // Should not have executed yet
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThat(task.run()).isEqualTo(1);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("delay() with null supplier throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void delayWithNullSupplierThrows() {
      assertThatThrownBy(() -> VTask.delay(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("thunk");
    }

    @Test
    @DisplayName("succeed() creates VTask with immediate value")
    void succeedCreatesVTaskWithValue() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);
      assertThat(task.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("succeed() refuses a null value at the call")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void succeedRefusesANullValue() {
      assertThatNullPointerException()
          .isThrownBy(() -> VTask.succeed(null))
          .withMessage("VTask.succeed value cannot be null");
    }

    @Test
    @DisplayName("fail() creates failing VTask")
    void failCreatesFailingVTask() {
      RuntimeException exception = new RuntimeException("Test error");
      VTask<Integer> task = VTask.fail(exception);

      assertThatVTask(task).fails().withMessage("Test error");
    }

    @Test
    @DisplayName("fail() with null throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void failWithNullThrows() {
      assertThatThrownBy(() -> VTask.fail(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("exec() creates VTask from Runnable")
    void execCreatesVTaskFromRunnable() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<Unit> task = VTask.exec(counter::incrementAndGet);

      // Should not have executed yet
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThat(task.run()).isEqualTo(Unit.INSTANCE);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("exec() with null runnable throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void execWithNullRunnableThrows() {
      assertThatThrownBy(() -> VTask.exec(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("blocking() creates VTask from blocking Callable")
    void blockingCreatesVTaskFromBlockingCallable() {
      VTask<Integer> task = VTask.blocking(() -> TEST_VALUE);
      assertThat(task.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("blocking() with null callable throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void blockingWithNullCallableThrows() {
      assertThatThrownBy(() -> VTask.blocking(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("A step that produces null fails the task, naming that step")
  class NullValues {

    private static void failsWith(VTask<?> task, String message) {
      assertThatVTask(task)
          .fails()
          .withExceptionType(NullPointerException.class)
          .withMessage(message);
    }

    @Test
    @DisplayName("of(), delay() and blocking() fail the task when their computation returns null")
    void factoriesFailOnANullResult() {
      failsWith(VTask.<String>of(() -> null), "callable must not return null");
      failsWith(VTask.<String>delay(() -> null), "thunk must not return null");
      failsWith(VTask.<String>blocking(() -> null), "callable must not return null");
    }

    @Test
    @DisplayName("map() and recover() fail the task when their function returns null")
    void functionsFailOnANullResult() {
      failsWith(VTask.succeed(TEST_VALUE).map(_ -> (String) null), "f must not return null");
      failsWith(
          VTask.<String>fail(new RuntimeException("boom")).recover(_ -> null),
          "recoveryFunction must not return null");
    }

    @Test
    @DisplayName("a null result is a Failure from runSafe(), not a Success")
    void runSafeGivesAFailure() {
      assertThatTry(VTask.<String>delay(() -> null).runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              e ->
                  assertThat(e)
                      .isInstanceOf(NullPointerException.class)
                      .hasMessage("thunk must not return null"));
    }

    @Test
    @DisplayName("a hand-written task that returns null fails when run, run async or run safely")
    @SuppressWarnings("DataFlowIssue") // a hand-written task returning null is what is refused
    void aHandWrittenTaskFails() {
      VTask<String> raw = () -> null;

      failsWith(raw, "the task returned null, and a VTask always gives a value");
      assertThatThrownBy(() -> raw.runAsync().join())
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(NullPointerException.class)
          .hasRootCauseMessage("the task returned null, and a VTask always gives a value");
      assertThatTry(raw.runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              e ->
                  assertThat(e)
                      .isInstanceOf(NullPointerException.class)
                      .hasMessage("the task returned null, and a Success always holds a value"));
    }
  }

  @Nested
  @DisplayName("asCallable() Method")
  class AsCallableMethod {

    @Test
    @DisplayName("asCallable() returns result from successful task")
    void asCallableReturnsResultFromSuccessfulTask() throws Exception {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      Callable<Integer> callable = task.asCallable();

      assertThat(callable.call()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("asCallable() throws Exception for failing task")
    void asCallableThrowsExceptionForFailingTask() {
      RuntimeException exception = new RuntimeException("Test error");
      VTask<Integer> task = VTask.fail(exception);

      Callable<Integer> callable = task.asCallable();

      assertThatThrownBy(callable::call)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Test error");
    }

    @Test
    @DisplayName("asCallable() propagates Error directly")
    void asCallablePropagatesErrorDirectly() {
      Error error = new OutOfMemoryError("Test error");
      VTask<Integer> task =
          VTask.of(
              () -> {
                throw error;
              });

      Callable<Integer> callable = task.asCallable();

      assertThatThrownBy(callable::call)
          .isInstanceOf(OutOfMemoryError.class)
          .hasMessage("Test error");
    }

    @Test
    @DisplayName("asCallable() wraps checked Throwable in RuntimeException")
    void asCallableWrapsCheckedThrowableInRuntimeException() {
      // Create a Throwable that is not an Exception or Error
      Throwable customThrowable = new Throwable("Custom throwable");
      // Use VTask.fail() to create a task that fails with the Throwable
      VTask<Integer> task = VTask.fail(customThrowable);

      Callable<Integer> callable = task.asCallable();

      assertThatThrownBy(callable::call)
          .isInstanceOf(RuntimeException.class)
          .hasCause(customThrowable);
    }
  }

  @Nested
  @DisplayName("Execution Methods")
  class ExecutionMethods {

    @Test
    @DisplayName("run() executes the task synchronously")
    void runExecutesTaskSynchronously() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThat(task.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runAsync() executes on virtual thread")
    void runAsyncExecutesOnVirtualThread() throws Exception {
      AtomicReference<Boolean> wasVirtual = new AtomicReference<>();
      VTask<Integer> task =
          VTask.of(
              () -> {
                wasVirtual.set(Thread.currentThread().isVirtual());
                return TEST_VALUE;
              });

      task.runAsync().get();
      assertThat(wasVirtual.get()).isTrue();
    }

    @Test
    @DisplayName("run() propagates exceptions")
    void runPropagatesExceptions() {
      RuntimeException exception = new RuntimeException("Test error");
      VTask<Integer> task = VTask.fail(exception);

      assertThatThrownBy(task::run).isInstanceOf(RuntimeException.class).hasMessage("Test error");
    }

    @Test
    @DisplayName("run() wraps an InterruptedException and sets the interrupt status again")
    void runWrapsInterruptAndSetsStatus() {
      InterruptedException cancelled = new InterruptedException("cancelled");
      VTask<Integer> task = VTask.fail(cancelled);

      try {
        assertThatThrownBy(task::run)
            .isInstanceOf(VTaskExecutionException.class)
            .hasCauseReference(cancelled);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName(
        "runSafe() returns an InterruptedException as the failure, with the status set again")
    void runSafeReturnsInterruptAndSetsStatus() {
      InterruptedException cancelled = new InterruptedException("cancelled");
      VTask<Integer> task = VTask.fail(cancelled);

      try {
        Try<Integer> result = task.runSafe();

        assertThat(result).isEqualTo(Try.failure(cancelled));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName("runSafe() returns Success for successful task")
    void runSafeReturnsSuccessForSuccessfulTask() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      Try<Integer> result = task.runSafe();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(-1)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runSafe() returns Failure for failing task")
    void runSafeReturnsFailureForFailingTask() {
      RuntimeException exception = new RuntimeException("Test error");
      VTask<Integer> task = VTask.fail(exception);

      Try<Integer> result = task.runSafe();

      assertThat(result.isFailure()).isTrue();
    }

    @Test
    @DisplayName("runAsync() returns CompletableFuture")
    void runAsyncReturnsCompletableFuture() throws Exception {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      var future = task.runAsync();

      assertThat(future.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runAsync() completes exceptionally for failing task")
    void runAsyncCompletesExceptionallyForFailingTask() {
      RuntimeException exception = new RuntimeException("Test error");
      VTask<Integer> task = VTask.fail(exception);

      var future = task.runAsync();

      // Wait for completion and verify it completed exceptionally
      assertThatThrownBy(future::join)
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("VTaskExecutionException")
  class VTaskExecutionExceptionTests {

    @Test
    @DisplayName("two-arg constructor preserves message and cause")
    void twoArgConstructorPreservesMessageAndCause() {
      Throwable cause = new Exception("original");
      VTaskExecutionException exception = new VTaskExecutionException("context info", cause);

      assertThat(exception.getMessage()).isEqualTo("context info");
      assertThat(exception.getCause()).isSameAs(cause);
    }
  }

  @Nested
  @DisplayName("Composition Methods")
  class CompositionMethods {

    @Test
    @DisplayName("map() transforms the value")
    void mapTransformsValue() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<String> mapped = task.map(i -> "Value: " + i);

      assertThat(mapped.run()).isEqualTo("Value: " + TEST_VALUE);
    }

    @Test
    @DisplayName("map() is lazy")
    void mapIsLazy() {
      AtomicInteger counter = new AtomicInteger(0);

      VTask<Integer> task = VTask.delay(counter::incrementAndGet);
      task.map(Object::toString);

      // Should not execute yet
      assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("map() with null mapper throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mapWithNullMapperThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.map(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map() propagates exceptions from original task")
    void mapPropagatesExceptionsFromOriginalTask() {
      RuntimeException exception = new RuntimeException("Original error");
      VTask<Integer> task = VTask.fail(exception);

      VTask<String> mapped = task.map(Object::toString);

      assertThatVTask(mapped).fails().withMessage("Original error");
    }

    @Test
    @DisplayName("flatMap() chains VTask computations")
    void flatMapChainsVTaskComputations() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<String> flatMapped = task.flatMap(i -> VTask.succeed("Value: " + i));

      assertThat(flatMapped.run()).isEqualTo("Value: " + TEST_VALUE);
    }

    @Test
    @DisplayName("flatMap() is lazy")
    void flatMapIsLazy() {
      AtomicInteger counter = new AtomicInteger(0);

      VTask<Integer> task = VTask.delay(counter::incrementAndGet);
      task.flatMap(i -> VTask.succeed("Value: " + i));

      // Should not execute yet
      assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("flatMap() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void flatMapWithNullFunctionThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.flatMap(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("via() is alias for flatMap()")
    void viaIsAliasForFlatMap() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<String> viaResult = task.via(i -> VTask.succeed("Value: " + i));

      assertThat(viaResult.run()).isEqualTo("Value: " + TEST_VALUE);
    }

    @Test
    @DisplayName("then() sequences tasks discarding first result")
    void thenSequencesTasks() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<Integer> first = VTask.delay(counter::incrementAndGet);
      VTask<String> second = VTask.succeed("result");

      VTask<String> combined = first.then(() -> second);

      assertThat(combined.run()).isEqualTo("result");
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("then() with null next throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void thenWithNullNextThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.then(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("then() propagates error from first task")
    void thenPropagatesErrorFromFirstTask() {
      RuntimeException exception = new RuntimeException("First task failed");
      VTask<Integer> first = VTask.fail(exception);
      VTask<String> second = VTask.succeed("result");

      VTask<String> combined = first.then(() -> second);

      assertThatVTask(combined).fails().withMessage("First task failed");
    }

    @Test
    @DisplayName("peek() performs side effect without modifying result")
    void peekPerformsSideEffectWithoutModifyingResult() {
      AtomicReference<Integer> observed = new AtomicReference<>();
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<Integer> peeked = task.peek(observed::set);

      assertThat(peeked.run()).isEqualTo(TEST_VALUE);
      assertThat(observed.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("peek() with null action throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void peekWithNullActionThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.peek(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("peek() propagates error from original task")
    void peekPropagatesErrorFromOriginalTask() {
      RuntimeException exception = new RuntimeException("Original error");
      VTask<Integer> task = VTask.fail(exception);

      VTask<Integer> peeked = task.peek(_ -> {});

      assertThatVTask(peeked).fails().withMessage("Original error");
    }
  }

  @Nested
  @DisplayName("Timeout")
  class TimeoutTests {

    /**
     * A task that ignores a cancellation, carries on for a while, and then sets {@code finished}.
     */
    private static VTask<Integer> ignoringCancellation(AtomicBoolean finished) {
      return VTask.of(
          () -> {
            try {
              Thread.sleep(5000);
            } catch (InterruptedException ignored) {
              // this task ignores a cancellation, and carries on
            }
            Thread.sleep(100);
            finished.set(true);
            return TEST_VALUE;
          });
    }

    /** A task that throws {@code failure} when it is cancelled, as a failed rollback would. */
    private static VTask<Integer> failingOnCancellation(RuntimeException failure) {
      return VTask.of(
          () -> {
            try {
              Thread.sleep(5000);
              return TEST_VALUE;
            } catch (InterruptedException e) {
              throw failure;
            }
          });
    }

    @Test
    @DisplayName(
        "detachOnCancel() gives the task's value, and passes its failure through as thrown")
    void detachOnCancelPassesTheOutcomeThrough() {
      IOException disk = new IOException("disk");

      assertThat(VTask.succeed(TEST_VALUE).detachOnCancel().run()).isEqualTo(TEST_VALUE);
      assertThatThrownBy(VTask.<Integer>fail(disk).detachOnCancel()::execute).isSameAs(disk);
    }

    @Test
    @DisplayName(
        "detachOnCancel() under timeout() fails when the time runs out, before the task stops")
    void detachedTimeoutFailsWhenTheTimeRunsOut() throws InterruptedException {
      HeldTask held = new HeldTask();
      try {
        assertThatThrownBy(
                held.task(TEST_VALUE).detachOnCancel().timeout(Duration.ofMillis(20))::execute)
            .isInstanceOf(TimeoutException.class)
            .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
        assertThat(held.finished()).as("the task is still running").isFalse();
        assertThat(held.sawInterrupt()).isTrue();
      } finally {
        held.release();
      }
    }

    @Test
    @DisplayName("detachOnCancel() throws an interrupt of the caller at once, and passes it on")
    void detachOnCancelThrowsTheCallersInterruptAtOnce() throws InterruptedException {
      HeldTask held = new HeldTask();
      Thread.currentThread().interrupt();
      try {
        assertThatThrownBy(held.task(TEST_VALUE).detachOnCancel()::execute)
            .isInstanceOf(InterruptedException.class);
        assertThat(held.finished()).as("the task is still running").isFalse();
        assertThat(held.sawInterrupt()).isTrue();
      } finally {
        Thread.interrupted();
        held.release();
      }
    }

    @Test
    @DisplayName("timeout() allows fast tasks to complete")
    void timeoutAllowsFastTasksToComplete() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);
      VTask<Integer> withTimeout = task.timeout(Duration.ofSeconds(1));

      assertThat(withTimeout.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("timeout() with null duration throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void timeoutWithNullDurationThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.timeout(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("timeout() propagates error from failing task")
    void timeoutPropagatesErrorFromFailingTask() {
      RuntimeException exception = new RuntimeException("Task error");
      VTask<Integer> task = VTask.fail(exception);
      VTask<Integer> withTimeout = task.timeout(Duration.ofSeconds(1));

      assertThatVTask(withTimeout).fails().withMessage("Task error");
    }

    @Test
    @DisplayName("timeout() accepts a duration too long to count in nanoseconds")
    void timeoutAcceptsAHugeDuration() {
      VTask<Integer> withTimeout =
          VTask.succeed(TEST_VALUE).timeout(ChronoUnit.FOREVER.getDuration());

      assertThat(withTimeout.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("timeout() reports a TimeoutException from the task as the time running out")
    void timeoutReportsTasksOwnTimeoutAsExpiry() {
      TimeoutException own = new TimeoutException("the task's own");
      VTask<Integer> withTimeout = VTask.<Integer>fail(own).timeout(Duration.ofSeconds(1));

      assertThatThrownBy(withTimeout::execute)
          .isInstanceOf(TimeoutException.class)
          .hasMessage("VTask timed out after PT1S")
          .hasCause(own);
    }

    @Test
    @DisplayName(
        "timeout() keeps a nested timeout that ran out first, and what it kept, as the cause")
    void timeoutKeepsANestedTimeoutThatRanOutFirst() {
      IllegalStateException rollbackFailed = new IllegalStateException("rollback failed");
      VTask<Integer> nested = failingOnCancellation(rollbackFailed).timeout(Duration.ofMillis(20));

      assertThatThrownBy(nested.timeout(Duration.ofSeconds(10))::execute)
          .isInstanceOf(TimeoutException.class)
          .hasMessage("VTask timed out after PT10S")
          .satisfies(
              e -> {
                assertThat(e.getCause()).hasMessage("VTask timed out after PT0.02S");
                assertThat(e.getCause().getSuppressed()).containsExactly(rollbackFailed);
              });
    }

    @Test
    @DisplayName("timeout() passes the task's own future exceptions through as thrown")
    void timeoutPassesFutureExceptionsThrough() {
      CompletionException completion = new CompletionException(new IOException("disk"));
      CancellationException cancellation = new CancellationException("the task's own");

      assertThatThrownBy(VTask.<Integer>fail(completion).timeout(Duration.ofSeconds(1))::execute)
          .isSameAs(completion);
      assertThatThrownBy(VTask.<Integer>fail(cancellation).timeout(Duration.ofSeconds(1))::execute)
          .isSameAs(cancellation);
    }

    @Test
    @DisplayName(
        "timeout() interrupts the task when the time runs out, and fails once its finally has run")
    void timeoutInterruptsOnExpiryAndWaitsForTheTask() {
      AtomicBoolean interrupted = new AtomicBoolean(false);
      AtomicBoolean cleanedUp = new AtomicBoolean(false);
      VTask<Integer> slowTask =
          VTask.of(
              () -> {
                try {
                  Thread.sleep(5000);
                  return TEST_VALUE;
                } catch (InterruptedException e) {
                  interrupted.set(true);
                  throw e;
                } finally {
                  Thread.sleep(50); // cleanup that takes a while
                  cleanedUp.set(true);
                }
              });

      assertThatThrownBy(slowTask.timeout(Duration.ofMillis(20))::execute)
          .isInstanceOf(TimeoutException.class)
          .hasMessage("VTask timed out after PT0.02S")
          .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
      assertThat(interrupted).isTrue();
      assertThat(cleanedUp).isTrue();
    }

    @Test
    @DisplayName("timeout() waits for a task that ignores the interrupt, which delays the failure")
    void timeoutWaitsForATaskThatIgnoresTheInterrupt() {
      AtomicBoolean finished = new AtomicBoolean(false);

      assertThatThrownBy(ignoringCancellation(finished).timeout(Duration.ofMillis(20))::execute)
          .isInstanceOf(TimeoutException.class);
      assertThat(finished).as("the task has finished, and its value is not returned").isTrue();
    }

    @Test
    @DisplayName("timeout() keeps a failure the task throws once the time runs out as suppressed")
    void timeoutKeepsAFailureAfterExpiryAsSuppressed() {
      IllegalStateException rollbackFailed = new IllegalStateException("rollback failed");

      assertThatThrownBy(
              failingOnCancellation(rollbackFailed).timeout(Duration.ofMillis(20))::execute)
          .isInstanceOf(TimeoutException.class)
          .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(rollbackFailed));
    }

    @Test
    @DisplayName(
        "timeout() keeps waiting through an interrupt once the time runs out, and keeps the status")
    void timeoutKeepsWaitingThroughALaterInterrupt() throws InterruptedException {
      CountDownLatch sawInterrupt = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicBoolean finished = new AtomicBoolean(false);
      VTask<Integer> stubbornTask =
          VTask.of(
              () -> {
                try {
                  Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                  sawInterrupt.countDown(); // this task ignores a cancellation, and waits on
                }
                release.await();
                finished.set(true);
                return TEST_VALUE;
              });
      Thread caller = Thread.currentThread();
      Thread canceller =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      if (sawInterrupt.await(5, TimeUnit.SECONDS)) {
                        caller.interrupt();
                        Thread.sleep(50); // long enough for a wait that gave way to return
                      }
                    } catch (InterruptedException ignored) {
                      // not interrupted: nothing else touches this thread
                    } finally {
                      release.countDown();
                    }
                  });

      try {
        assertThatThrownBy(stubbornTask.timeout(Duration.ofMillis(20))::execute)
            .isInstanceOf(TimeoutException.class);
        assertThat(finished).isTrue();
        assertThat(Thread.interrupted()).as("the interrupt status is set again").isTrue();
      } finally {
        Thread.interrupted();
      }
      canceller.join();
    }

    @Test
    @DisplayName("an outer timeout() fails only once an inner one's task has stopped")
    void outerTimeoutWaitsForTheInnerTask() {
      AtomicBoolean finished = new AtomicBoolean(false);
      // The task carries on past the inner time limit too
      VTask<Integer> inner = ignoringCancellation(finished).timeout(Duration.ofMillis(50));

      assertThatThrownBy(inner.timeout(Duration.ofMillis(20))::execute)
          .isInstanceOf(TimeoutException.class)
          .hasMessage("VTask timed out after PT0.02S");
      assertThat(finished).isTrue();
    }

    @Test
    @DisplayName("an outer timeout() keeps a failed rollback that a nested timeout kept")
    void outerTimeoutKeepsANestedTimeoutsLateFailure() {
      IllegalStateException rollbackFailed = new IllegalStateException("rollback failed");
      VTask<Integer> nested = failingOnCancellation(rollbackFailed).timeout(Duration.ofSeconds(10));
      // run() reports the nested timeout's InterruptedException wrapped
      VTask<Integer> nestedThroughRun = VTask.of(nested::run);

      assertThatThrownBy(nested.timeout(Duration.ofMillis(20))::execute)
          .isInstanceOf(TimeoutException.class)
          .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(rollbackFailed));
      assertThatThrownBy(nestedThroughRun.timeout(Duration.ofMillis(20))::execute)
          .isInstanceOf(TimeoutException.class)
          .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(rollbackFailed));
    }

    @Test
    @DisplayName("timeout() keeps a wrapped failure that is not a cancellation as suppressed")
    void timeoutKeepsAWrappedFailureThatIsNotACancellation() {
      VTaskExecutionException rollbackFailed =
          new VTaskExecutionException(new IOException("rollback failed"));

      assertThatThrownBy(
              failingOnCancellation(rollbackFailed).timeout(Duration.ofMillis(20))::execute)
          .isInstanceOf(TimeoutException.class)
          .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(rollbackFailed));
    }

    @Test
    @DisplayName(
        "timeout() over a Resource whose use keeps the interrupt status lets its release run in full")
    void timeoutLetsAReleaseAfterAReturningUseRunInFull() {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> connection =
          Resource.make(
              () -> "connection",
              _ -> {
                try {
                  Thread.sleep(50); // a release that blocks, as closing a connection can
                  released.set(true);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      VTask<String> use =
          connection.use(
              c ->
                  VTask.of(
                      () -> {
                        try {
                          Thread.sleep(5000);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt(); // keeps the status, and returns
                        }
                        return c;
                      }));

      assertThatThrownBy(use.timeout(Duration.ofMillis(20))::execute)
          .isInstanceOf(TimeoutException.class)
          .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
      assertThat(released).as("the interrupt did not reach the release").isTrue();
    }

    @Test
    @DisplayName(
        "timeout() passes an interrupt of the waiting thread on, and throws it once the task stops")
    void timeoutPassesInterruptOnAndWaitsForTheTask() throws InterruptedException {
      CountDownLatch started = new CountDownLatch(1);
      AtomicBoolean cleanedUp = new AtomicBoolean(false);
      VTask<Integer> slowTask =
          VTask.of(
              () -> {
                started.countDown();
                try {
                  Thread.sleep(3000);
                  return TEST_VALUE;
                } catch (InterruptedException e) {
                  Thread.sleep(100); // cleanup that takes a while
                  cleanedUp.set(true);
                  throw e;
                }
              });
      VTask<Integer> withTimeout = slowTask.timeout(Duration.ofSeconds(10));
      Thread caller = Thread.currentThread();
      Thread canceller =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      started.await();
                      caller.interrupt();
                    } catch (InterruptedException ignored) {
                      // not interrupted: nothing else touches this thread
                    }
                  });

      try {
        assertThatThrownBy(withTimeout::execute).isInstanceOf(InterruptedException.class);
        assertThat(cleanedUp).isTrue();
      } finally {
        Thread.interrupted();
      }
      canceller.join();
    }

    @Test
    @DisplayName("timeout() keeps a failure the cancelled task throws as suppressed")
    void timeoutKeepsLateFailureAsSuppressed() {
      IllegalStateException rollbackFailed = new IllegalStateException("rollback failed");

      Thread.currentThread().interrupt();
      try {
        assertThatThrownBy(
                failingOnCancellation(rollbackFailed).timeout(Duration.ofSeconds(10))::execute)
            .isInstanceOf(InterruptedException.class)
            .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(rollbackFailed));
      } finally {
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName(
        "timeout() waits past the time limit for a task that ignores an interrupt of the caller")
    void timeoutWaitsForATaskThatIgnoresTheCallersInterrupt() {
      AtomicBoolean finished = new AtomicBoolean(false);
      VTask<Integer> withTimeout = ignoringCancellation(finished).timeout(Duration.ofMillis(20));

      Thread.currentThread().interrupt();
      try {
        assertThatThrownBy(withTimeout::execute).isInstanceOf(InterruptedException.class);
      } finally {
        Thread.interrupted();
      }
      assertThat(finished).isTrue();
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("recover() handles failure")
    void recoverHandlesFailure() {
      RuntimeException exception = new RuntimeException("Error");
      VTask<Integer> failing = VTask.fail(exception);

      VTask<Integer> recovered = failing.recover(_ -> -1);

      assertThat(recovered.run()).isEqualTo(-1);
    }

    @Test
    @DisplayName("recover() passes through success")
    void recoverPassesThroughSuccess() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<Integer> recovered = task.recover(_ -> -1);

      assertThat(recovered.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() handles failure with new task")
    void recoverWithHandlesFailure() {
      RuntimeException exception = new RuntimeException("Error");
      VTask<Integer> failing = VTask.fail(exception);

      VTask<Integer> recovered = failing.recoverWith(_ -> VTask.succeed(-1));

      assertThat(recovered.run()).isEqualTo(-1);
    }

    @Test
    @DisplayName("recoverWith() passes through success")
    void recoverWithPassesThroughSuccess() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<Integer> recovered = task.recoverWith(_ -> VTask.succeed(-1));

      assertThat(recovered.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("mapError() transforms error")
    void mapErrorTransformsError() {
      RuntimeException original = new RuntimeException("Original");
      VTask<Integer> failing = VTask.fail(original);

      VTask<Integer> mapped =
          failing.mapError(e -> new IllegalStateException("Wrapped: " + e.getMessage()));

      assertThatVTask(mapped)
          .fails()
          .withExceptionType(IllegalStateException.class)
          .withMessageContaining("Wrapped: Original");
    }

    @Test
    @DisplayName("recover() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void recoverWithNullFunctionThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.recover(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("recoverWith() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void recoverWithWithNullFunctionThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.recoverWith(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("mapError() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mapErrorWithNullFunctionThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.mapError(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("mapError() passes through success")
    void mapErrorPassesThroughSuccess() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<Integer> mapped = task.mapError(_ -> new IllegalStateException("Wrapped"));

      assertThat(mapped.run()).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("asUnit() Method")
  class AsUnitMethod {

    @Test
    @DisplayName("asUnit() discards result and returns Unit")
    void asUnitDiscardsResult() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<Unit> unitTask = task.asUnit();

      assertThat(unitTask.run()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("asUnit() preserves side effects")
    void asUnitPreservesSideEffects() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<Integer> task =
          VTask.delay(
              () -> {
                counter.incrementAndGet();
                return TEST_VALUE;
              });

      VTask<Unit> unitTask = task.asUnit();

      assertThat(counter.get()).isZero();
      unitTask.run();
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("asUnit() propagates exceptions")
    void asUnitPropagatesExceptions() {
      RuntimeException exception = new RuntimeException("Error");
      VTask<Integer> failing = VTask.fail(exception);

      VTask<Unit> unitTask = failing.asUnit();

      assertThatVTask(unitTask).fails().withMessage("Error");
    }
  }

  @Nested
  @DisplayName("Lazy Evaluation Tests")
  class LazyEvaluationTests {

    @Test
    @DisplayName("VTask creation does not execute")
    void vtaskCreationDoesNotExecute() {
      AtomicInteger counter = new AtomicInteger(0);

      VTask<Integer> task1 = VTask.delay(counter::incrementAndGet);
      VTask<Integer> task2 = task1.map(i -> i * 2);
      VTask<String> task3 = task2.flatMap(i -> VTask.succeed("Value: " + i));
      task3.asUnit();

      // None of this should have executed
      assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("Multiple map operations don't execute until run")
    @SuppressWarnings("DataFlowIssue") // non-null in this fixture
    void multipleMapOperationsDontExecuteUntilRun() {
      AtomicInteger executions = new AtomicInteger(0);

      VTask<Integer> task =
          VTask.delay(
                  () -> {
                    executions.incrementAndGet();
                    return 1;
                  })
              .map(
                  i -> {
                    executions.incrementAndGet();
                    return i + 1;
                  })
              .map(
                  i -> {
                    executions.incrementAndGet();
                    return i + 1;
                  });

      assertThat(executions.get()).isZero();

      int result = task.run();

      assertThat(result).isEqualTo(3);
      assertThat(executions.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Complex chain with map, flatMap, and asUnit")
    void complexChainWithMapFlatMapAndAsUnit() {
      AtomicReference<String> log = new AtomicReference<>("");

      VTask<Integer> step1 =
          VTask.delay(
              () -> {
                log.updateAndGet(s -> s + "1");
                return 10;
              });

      VTask<Unit> sequence =
          step1
              .map(
                  i -> {
                    log.updateAndGet(s -> s + "2");
                    return i * 2;
                  })
              .flatMap(
                  i ->
                      VTask.delay(
                          () -> {
                            log.updateAndGet(s -> s + "3");
                            return i + 5;
                          }))
              .asUnit();

      assertThat(log.get()).isEmpty();

      sequence.run();
      assertThat(log.get()).isEqualTo("123");
    }

    @Test
    @DisplayName("Sequential side effects with exec")
    void sequentialSideEffectsWithExec() {
      AtomicReference<String> log = new AtomicReference<>("");

      VTask<Unit> task =
          VTask.exec(() -> log.updateAndGet(s -> s + "A"))
              .flatMap(_ -> VTask.exec(() -> log.updateAndGet(s -> s + "B")))
              .flatMap(_ -> VTask.exec(() -> log.updateAndGet(s -> s + "C")));

      assertThat(log.get()).isEmpty();

      task.run();

      assertThat(log.get()).isEqualTo("ABC");
    }
  }
}
