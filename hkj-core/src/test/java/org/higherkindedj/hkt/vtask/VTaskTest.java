// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.vtask.VTaskAssert.assertThatVTask;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VTask Core Operations Test Suite")
class VTaskTest {

  private static final int TEST_VALUE = 42;
  private static final String TEST_STRING = "test";

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
    void ofWithNullCallableThrows() {
      assertThatThrownBy(() -> VTask.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("delay() creates lazy VTask")
    void delayCreatesLazyVTask() {
      AtomicInteger counter = new AtomicInteger(0);

      VTask<Integer> task = VTask.delay(() -> counter.incrementAndGet());

      // Should not have executed yet
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThat(task.run()).isEqualTo(1);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("delay() with null supplier throws NullPointerException")
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
    @DisplayName("succeed() can create VTask with null value")
    void succeedCanCreateVTaskWithNullValue() {
      VTask<String> task = VTask.succeed(null);
      assertThat(task.run()).isNull();
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
    void failWithNullThrows() {
      assertThatThrownBy(() -> VTask.fail(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("exec() creates VTask from Runnable")
    void execCreatesVTaskFromRunnable() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<Unit> task = VTask.exec(() -> counter.incrementAndGet());

      // Should not have executed yet
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThat(task.run()).isEqualTo(Unit.INSTANCE);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("exec() with null runnable throws NullPointerException")
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
    void blockingWithNullCallableThrows() {
      assertThatThrownBy(() -> VTask.blocking(null)).isInstanceOf(NullPointerException.class);
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

      VTask<Integer> task = VTask.delay(() -> counter.incrementAndGet());
      VTask<String> mapped = task.map(Object::toString);

      // Should not execute yet
      assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("map() with null mapper throws NullPointerException")
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

      VTask<Integer> task = VTask.delay(() -> counter.incrementAndGet());
      VTask<String> flatMapped = task.flatMap(i -> VTask.succeed("Value: " + i));

      // Should not execute yet
      assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("flatMap() with null function throws NullPointerException")
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
      VTask<Integer> first = VTask.delay(() -> counter.incrementAndGet());
      VTask<String> second = VTask.succeed("result");

      VTask<String> combined = first.then(() -> second);

      assertThat(combined.run()).isEqualTo("result");
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("then() with null next throws NullPointerException")
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
    void peekWithNullActionThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.peek(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("peek() propagates error from original task")
    void peekPropagatesErrorFromOriginalTask() {
      RuntimeException exception = new RuntimeException("Original error");
      VTask<Integer> task = VTask.fail(exception);

      VTask<Integer> peeked = task.peek(v -> {});

      assertThatVTask(peeked).fails().withMessage("Original error");
    }
  }

  @Nested
  @DisplayName("Timeout")
  class TimeoutTests {

    @Test
    @DisplayName("timeout() allows fast tasks to complete")
    void timeoutAllowsFastTasksToComplete() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);
      VTask<Integer> withTimeout = task.timeout(Duration.ofSeconds(1));

      assertThat(withTimeout.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("timeout() fails slow tasks")
    void timeoutFailsSlowTasks() {
      VTask<Integer> slowTask =
          VTask.of(
              () -> {
                Thread.sleep(5000);
                return TEST_VALUE;
              });
      VTask<Integer> withTimeout = slowTask.timeout(Duration.ofMillis(50));

      assertThatVTask(withTimeout).fails().withExceptionType(TimeoutException.class);
    }

    @Test
    @DisplayName("timeout() with null duration throws NullPointerException")
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
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("recover() handles failure")
    void recoverHandlesFailure() {
      RuntimeException exception = new RuntimeException("Error");
      VTask<Integer> failing = VTask.fail(exception);

      VTask<Integer> recovered = failing.recover(e -> -1);

      assertThat(recovered.run()).isEqualTo(-1);
    }

    @Test
    @DisplayName("recover() passes through success")
    void recoverPassesThroughSuccess() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<Integer> recovered = task.recover(e -> -1);

      assertThat(recovered.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() handles failure with new task")
    void recoverWithHandlesFailure() {
      RuntimeException exception = new RuntimeException("Error");
      VTask<Integer> failing = VTask.fail(exception);

      VTask<Integer> recovered = failing.recoverWith(e -> VTask.succeed(-1));

      assertThat(recovered.run()).isEqualTo(-1);
    }

    @Test
    @DisplayName("recoverWith() passes through success")
    void recoverWithPassesThroughSuccess() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<Integer> recovered = task.recoverWith(e -> VTask.succeed(-1));

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
    void recoverWithNullFunctionThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.recover(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("recoverWith() with null function throws NullPointerException")
    void recoverWithWithNullFunctionThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.recoverWith(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("mapError() with null function throws NullPointerException")
    void mapErrorWithNullFunctionThrows() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      assertThatThrownBy(() -> task.mapError(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("mapError() passes through success")
    void mapErrorPassesThroughSuccess() {
      VTask<Integer> task = VTask.succeed(TEST_VALUE);

      VTask<Integer> mapped = task.mapError(e -> new IllegalStateException("Wrapped"));

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

      VTask<Integer> task1 = VTask.delay(() -> counter.incrementAndGet());
      VTask<Integer> task2 = task1.map(i -> i * 2);
      VTask<String> task3 = task2.flatMap(i -> VTask.succeed("Value: " + i));
      VTask<Unit> task4 = task3.asUnit();

      // None of this should have executed
      assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("Multiple map operations don't execute until run")
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
              .flatMap(u -> VTask.exec(() -> log.updateAndGet(s -> s + "B")))
              .flatMap(u -> VTask.exec(() -> log.updateAndGet(s -> s + "C")));

      assertThat(log.get()).isEmpty();

      task.run();

      assertThat(log.get()).isEqualTo("ABC");
    }
  }
}
