// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.vtask.VTaskAssert.assertThatVTask;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Par Parallel Combinators Test Suite")
class ParTest {

  private static final int TEST_VALUE_A = 1;
  private static final int TEST_VALUE_B = 2;
  private static final int TEST_VALUE_C = 3;

  @Nested
  @DisplayName("zip() Tests")
  class ZipTests {

    @Test
    @DisplayName("zip() combines two successful tasks")
    void zipCombinesTwoSuccessfulTasks() {
      VTask<Integer> taskA = VTask.succeed(TEST_VALUE_A);
      VTask<Integer> taskB = VTask.succeed(TEST_VALUE_B);

      VTask<Par.Tuple2<Integer, Integer>> combined = Par.zip(taskA, taskB);

      Par.Tuple2<Integer, Integer> result = combined.run();
      assertThat(result.first()).isEqualTo(TEST_VALUE_A);
      assertThat(result.second()).isEqualTo(TEST_VALUE_B);
    }

    @Test
    @DisplayName("zip() executes tasks in parallel")
    void zipExecutesTasksInParallel() {
      AtomicLong threadA = new AtomicLong();
      AtomicLong threadB = new AtomicLong();

      VTask<Integer> taskA =
          VTask.of(
              () -> {
                threadA.set(Thread.currentThread().threadId());
                Thread.sleep(50);
                return TEST_VALUE_A;
              });

      VTask<Integer> taskB =
          VTask.of(
              () -> {
                threadB.set(Thread.currentThread().threadId());
                Thread.sleep(50);
                return TEST_VALUE_B;
              });

      long startTime = System.currentTimeMillis();
      Par.zip(taskA, taskB).run();
      long duration = System.currentTimeMillis() - startTime;

      // Should run in parallel, so total time ~50ms, not ~100ms
      assertThat(duration).isLessThan(90);
      // Should be on different threads
      assertThat(threadA.get()).isNotEqualTo(threadB.get());
    }

    @Test
    @DisplayName("zip() propagates error from first task")
    void zipPropagatesErrorFromFirstTask() {
      RuntimeException exception = new RuntimeException("Task A failed");
      VTask<Integer> taskA = VTask.fail(exception);
      VTask<Integer> taskB = VTask.succeed(TEST_VALUE_B);

      VTask<Par.Tuple2<Integer, Integer>> combined = Par.zip(taskA, taskB);

      assertThatVTask(combined).fails().withMessage("Task A failed");
    }

    @Test
    @DisplayName("zip() propagates error from second task")
    void zipPropagatesErrorFromSecondTask() {
      RuntimeException exception = new RuntimeException("Task B failed");
      VTask<Integer> taskA = VTask.succeed(TEST_VALUE_A);
      VTask<Integer> taskB = VTask.fail(exception);

      VTask<Par.Tuple2<Integer, Integer>> combined = Par.zip(taskA, taskB);

      assertThatVTask(combined).fails().withMessage("Task B failed");
    }

    @Test
    @DisplayName("zip() with null first task throws NullPointerException")
    void zipWithNullFirstTaskThrows() {
      VTask<Integer> taskB = VTask.succeed(TEST_VALUE_B);

      assertThatThrownBy(() -> Par.zip(null, taskB)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("zip() with null second task throws NullPointerException")
    void zipWithNullSecondTaskThrows() {
      VTask<Integer> taskA = VTask.succeed(TEST_VALUE_A);

      assertThatThrownBy(() -> Par.zip(taskA, null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("zip3() Tests")
  class Zip3Tests {

    @Test
    @DisplayName("zip3() combines three successful tasks")
    void zip3CombinesThreeSuccessfulTasks() {
      VTask<Integer> taskA = VTask.succeed(TEST_VALUE_A);
      VTask<Integer> taskB = VTask.succeed(TEST_VALUE_B);
      VTask<Integer> taskC = VTask.succeed(TEST_VALUE_C);

      VTask<Par.Tuple3<Integer, Integer, Integer>> combined = Par.zip3(taskA, taskB, taskC);

      Par.Tuple3<Integer, Integer, Integer> result = combined.run();
      assertThat(result.first()).isEqualTo(TEST_VALUE_A);
      assertThat(result.second()).isEqualTo(TEST_VALUE_B);
      assertThat(result.third()).isEqualTo(TEST_VALUE_C);
    }

    @Test
    @DisplayName("zip3() propagates error from any task")
    void zip3PropagatesErrorFromAnyTask() {
      RuntimeException exception = new RuntimeException("Task B failed");
      VTask<Integer> taskA = VTask.succeed(TEST_VALUE_A);
      VTask<Integer> taskB = VTask.fail(exception);
      VTask<Integer> taskC = VTask.succeed(TEST_VALUE_C);

      VTask<Par.Tuple3<Integer, Integer, Integer>> combined = Par.zip3(taskA, taskB, taskC);

      assertThatVTask(combined).fails().withMessage("Task B failed");
    }

    @Test
    @DisplayName("zip3() with null first task throws NullPointerException")
    void zip3WithNullFirstTaskThrows() {
      VTask<Integer> taskB = VTask.succeed(TEST_VALUE_B);
      VTask<Integer> taskC = VTask.succeed(TEST_VALUE_C);

      assertThatThrownBy(() -> Par.zip3(null, taskB, taskC))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("zip3() with null second task throws NullPointerException")
    void zip3WithNullSecondTaskThrows() {
      VTask<Integer> taskA = VTask.succeed(TEST_VALUE_A);
      VTask<Integer> taskC = VTask.succeed(TEST_VALUE_C);

      assertThatThrownBy(() -> Par.zip3(taskA, null, taskC))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("zip3() with null third task throws NullPointerException")
    void zip3WithNullThirdTaskThrows() {
      VTask<Integer> taskA = VTask.succeed(TEST_VALUE_A);
      VTask<Integer> taskB = VTask.succeed(TEST_VALUE_B);

      assertThatThrownBy(() -> Par.zip3(taskA, taskB, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("map2() Tests")
  class Map2Tests {

    @Test
    @DisplayName("map2() applies function to parallel results")
    void map2AppliesFunctionToParallelResults() {
      VTask<Integer> taskA = VTask.succeed(10);
      VTask<Integer> taskB = VTask.succeed(20);

      VTask<Integer> combined = Par.map2(taskA, taskB, Integer::sum);

      assertThat(combined.run()).isEqualTo(30);
    }

    @Test
    @DisplayName("map2() executes tasks in parallel")
    void map2ExecutesTasksInParallel() {
      VTask<Integer> taskA =
          VTask.of(
              () -> {
                Thread.sleep(50);
                return 10;
              });

      VTask<Integer> taskB =
          VTask.of(
              () -> {
                Thread.sleep(50);
                return 20;
              });

      long startTime = System.currentTimeMillis();
      Integer result = Par.map2(taskA, taskB, Integer::sum).run();
      long duration = System.currentTimeMillis() - startTime;

      assertThat(result).isEqualTo(30);
      // Should run in parallel
      assertThat(duration).isLessThan(90);
    }

    @Test
    @DisplayName("map2() propagates error from tasks")
    void map2PropagatesErrorFromTasks() {
      RuntimeException exception = new RuntimeException("Task failed");
      VTask<Integer> taskA = VTask.fail(exception);
      VTask<Integer> taskB = VTask.succeed(20);

      VTask<Integer> combined = Par.map2(taskA, taskB, Integer::sum);

      assertThatVTask(combined).fails().withMessage("Task failed");
    }

    @Test
    @DisplayName("map2() with null first task throws NullPointerException")
    void map2WithNullFirstTaskThrows() {
      VTask<Integer> taskB = VTask.succeed(20);

      assertThatThrownBy(() -> Par.map2(null, taskB, Integer::sum))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map2() with null second task throws NullPointerException")
    void map2WithNullSecondTaskThrows() {
      VTask<Integer> taskA = VTask.succeed(10);

      assertThatThrownBy(() -> Par.map2(taskA, null, Integer::sum))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map2() with null combiner throws NullPointerException")
    void map2WithNullCombinerThrows() {
      VTask<Integer> taskA = VTask.succeed(10);
      VTask<Integer> taskB = VTask.succeed(20);

      assertThatThrownBy(() -> Par.map2(taskA, taskB, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("map3() Tests")
  class Map3Tests {

    @Test
    @DisplayName("map3() applies function to three parallel results")
    void map3AppliesFunctionToThreeParallelResults() {
      VTask<Integer> taskA = VTask.succeed(10);
      VTask<Integer> taskB = VTask.succeed(20);
      VTask<Integer> taskC = VTask.succeed(30);

      VTask<Integer> combined = Par.map3(taskA, taskB, taskC, (a, b, c) -> a + b + c);

      assertThat(combined.run()).isEqualTo(60);
    }

    @Test
    @DisplayName("map3() propagates error from any task")
    void map3PropagatesErrorFromAnyTask() {
      RuntimeException exception = new RuntimeException("Task C failed");
      VTask<Integer> taskA = VTask.succeed(10);
      VTask<Integer> taskB = VTask.succeed(20);
      VTask<Integer> taskC = VTask.fail(exception);

      VTask<Integer> combined = Par.map3(taskA, taskB, taskC, (a, b, c) -> a + b + c);

      assertThatVTask(combined).fails().withMessage("Task C failed");
    }

    @Test
    @DisplayName("map3() with null first task throws NullPointerException")
    void map3WithNullFirstTaskThrows() {
      VTask<Integer> taskB = VTask.succeed(20);
      VTask<Integer> taskC = VTask.succeed(30);

      assertThatThrownBy(
              () -> Par.map3(null, taskB, taskC, (Integer a, Integer b, Integer c) -> a + b + c))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map3() with null combiner throws NullPointerException")
    void map3WithNullCombinerThrows() {
      VTask<Integer> taskA = VTask.succeed(10);
      VTask<Integer> taskB = VTask.succeed(20);
      VTask<Integer> taskC = VTask.succeed(30);

      assertThatThrownBy(() -> Par.map3(taskA, taskB, taskC, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("race() Tests")
  class RaceTests {

    @Test
    @DisplayName("race() returns first completing task")
    void raceReturnsFirstCompletingTask() {
      VTask<Integer> slow =
          VTask.of(
              () -> {
                Thread.sleep(200);
                return 1;
              });

      VTask<Integer> fast =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return 2;
              });

      VTask<Integer> fastest = VTask.succeed(3);

      VTask<Integer> result = Par.race(List.of(slow, fast, fastest));

      // The succeed task should win
      assertThat(result.run()).isEqualTo(3);
    }

    @Test
    @DisplayName("race() with empty list throws IllegalArgumentException")
    void raceWithEmptyListThrows() {
      assertThatThrownBy(() -> Par.race(List.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("race() with null throws NullPointerException")
    void raceWithNullThrows() {
      assertThatThrownBy(() -> Par.race(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("race() returns result even if others fail")
    void raceReturnsResultEvenIfOthersFail() {
      VTask<Integer> failing1 =
          VTask.of(
              () -> {
                Thread.sleep(100);
                throw new RuntimeException("Failed 1");
              });

      VTask<Integer> failing2 =
          VTask.of(
              () -> {
                Thread.sleep(100);
                throw new RuntimeException("Failed 2");
              });

      VTask<Integer> succeeding = VTask.succeed(42);

      VTask<Integer> result = Par.race(List.of(failing1, failing2, succeeding));

      assertThat(result.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("race() fails when all tasks fail")
    void raceFailsWhenAllTasksFail() {
      RuntimeException exception1 = new RuntimeException("Failed 1");
      RuntimeException exception2 = new RuntimeException("Failed 2");

      VTask<Integer> failing1 = VTask.fail(exception1);
      VTask<Integer> failing2 = VTask.fail(exception2);

      VTask<Integer> result = Par.race(List.of(failing1, failing2));

      assertThatVTask(result).fails().withExceptionType(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("all() Tests")
  class AllTests {

    @Test
    @DisplayName("all() collects all results in order")
    void allCollectsAllResultsInOrder() {
      List<VTask<Integer>> tasks = List.of(VTask.succeed(1), VTask.succeed(2), VTask.succeed(3));

      VTask<List<Integer>> combined = Par.all(tasks);

      assertThat(combined.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("all() executes tasks in parallel")
    void allExecutesTasksInParallel() {
      AtomicInteger maxConcurrency = new AtomicInteger(0);
      AtomicInteger currentConcurrency = new AtomicInteger(0);

      List<VTask<Integer>> tasks =
          List.of(
              VTask.of(
                  () -> {
                    int current = currentConcurrency.incrementAndGet();
                    maxConcurrency.updateAndGet(max -> Math.max(max, current));
                    Thread.sleep(50);
                    currentConcurrency.decrementAndGet();
                    return 1;
                  }),
              VTask.of(
                  () -> {
                    int current = currentConcurrency.incrementAndGet();
                    maxConcurrency.updateAndGet(max -> Math.max(max, current));
                    Thread.sleep(50);
                    currentConcurrency.decrementAndGet();
                    return 2;
                  }),
              VTask.of(
                  () -> {
                    int current = currentConcurrency.incrementAndGet();
                    maxConcurrency.updateAndGet(max -> Math.max(max, current));
                    Thread.sleep(50);
                    currentConcurrency.decrementAndGet();
                    return 3;
                  }));

      Par.all(tasks).run();

      // All tasks should have run concurrently
      assertThat(maxConcurrency.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("all() with empty list returns empty list")
    void allWithEmptyListReturnsEmptyList() {
      VTask<List<Integer>> combined = Par.all(List.of());

      assertThat(combined.run()).isEmpty();
    }

    @Test
    @DisplayName("all() propagates first error")
    void allPropagatesFirstError() {
      RuntimeException exception = new RuntimeException("Task 2 failed");
      List<VTask<Integer>> tasks =
          List.of(VTask.succeed(1), VTask.fail(exception), VTask.succeed(3));

      VTask<List<Integer>> combined = Par.all(tasks);

      assertThatVTask(combined).fails().withMessage("Task 2 failed");
    }

    @Test
    @DisplayName("all() with null list throws NullPointerException")
    void allWithNullListThrows() {
      assertThatThrownBy(() -> Par.all(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("traverse() Tests")
  class TraverseTests {

    @Test
    @DisplayName("traverse() applies function and collects results")
    void traverseAppliesFunctionAndCollectsResults() {
      List<Integer> items = List.of(1, 2, 3);

      VTask<List<Integer>> result = Par.traverse(items, i -> VTask.succeed(i * 2));

      assertThat(result.run()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("traverse() executes in parallel")
    void traverseExecutesInParallel() {
      List<Integer> items = List.of(1, 2, 3, 4, 5);

      long startTime = System.currentTimeMillis();
      VTask<List<Integer>> result =
          Par.traverse(
              items,
              i ->
                  VTask.of(
                      () -> {
                        Thread.sleep(50);
                        return i * 2;
                      }));
      result.run();
      long duration = System.currentTimeMillis() - startTime;

      // Should run in parallel, so ~50ms, not ~250ms
      assertThat(duration).isLessThan(150);
    }

    @Test
    @DisplayName("traverse() with empty list returns empty list")
    void traverseWithEmptyListReturnsEmptyList() {
      List<Integer> items = List.of();

      VTask<List<Integer>> result = Par.traverse(items, i -> VTask.succeed(i * 2));

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("traverse() propagates error from function")
    void traversePropagatesErrorFromFunction() {
      RuntimeException exception = new RuntimeException("Transform failed");
      List<Integer> items = List.of(1, 2, 3);

      VTask<List<Integer>> result =
          Par.traverse(
              items,
              i -> {
                if (i == 2) {
                  return VTask.fail(exception);
                }
                return VTask.succeed(i * 2);
              });

      assertThatVTask(result).fails().withMessage("Transform failed");
    }

    @Test
    @DisplayName("traverse() with null items throws NullPointerException")
    void traverseWithNullItemsThrows() {
      assertThatThrownBy(() -> Par.traverse(null, i -> VTask.succeed(i)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("traverse() with null function throws NullPointerException")
    void traverseWithNullFunctionThrows() {
      List<Integer> items = List.of(1, 2, 3);

      assertThatThrownBy(() -> Par.traverse(items, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("traverse() throws when function returns null task")
    void traverseThrowsWhenFunctionReturnsNullTask() {
      List<Integer> items = List.of(1, 2, 3);

      VTask<List<Integer>> result =
          Par.traverse(
              items,
              i -> {
                if (i == 2) {
                  return null;
                }
                return VTask.succeed(i * 2);
              });

      assertThatVTask(result).fails().withExceptionType(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Cancellation Tests")
  class CancellationTests {

    @Test
    @DisplayName("zip() cancels remaining task on failure")
    void zipCancelsRemainingTaskOnFailure() throws InterruptedException {
      AtomicBoolean taskBCompleted = new AtomicBoolean(false);

      VTask<Integer> taskA =
          VTask.of(
              () -> {
                Thread.sleep(10);
                throw new RuntimeException("Task A failed");
              });

      VTask<Integer> taskB =
          VTask.of(
              () -> {
                Thread.sleep(100);
                taskBCompleted.set(true);
                return 2;
              });

      VTask<Par.Tuple2<Integer, Integer>> combined = Par.zip(taskA, taskB);

      try {
        combined.run();
      } catch (RuntimeException e) {
        // Expected
      }

      // Give some time for potential completion
      Thread.sleep(150);

      // Task B should have been cancelled and not completed
      assertThat(taskBCompleted.get()).isFalse();
    }
  }
}
