// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for the {@link Bulkhead} utility class.
 *
 * <p>Tests cover concurrency limiting, wait queue behaviour, timeout rejection, metrics accuracy,
 * fairness configuration, and generic type protection.
 */
@DisplayName("Bulkhead Test Suite")
class BulkheadTest {

  @Nested
  @DisplayName("Concurrency Limit Tests")
  class ConcurrencyLimitTests {

    @Test
    @DisplayName("protect() limits concurrent executions to maxConcurrent")
    void protectLimitsConcurrentExecutions() throws Exception {
      int maxConcurrent = 2;
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(maxConcurrent);

      AtomicInteger concurrentCount = new AtomicInteger(0);
      AtomicInteger maxObservedConcurrent = new AtomicInteger(0);
      CountDownLatch allStarted = new CountDownLatch(maxConcurrent);
      CountDownLatch proceed = new CountDownLatch(1);
      CountDownLatch allDone = new CountDownLatch(4);

      for (int i = 0; i < 4; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    VTask<String> protectedTask =
                        bulkhead.protect(
                            VTask.of(
                                () -> {
                                  int current = concurrentCount.incrementAndGet();
                                  maxObservedConcurrent.updateAndGet(
                                      prev -> Math.max(prev, current));
                                  allStarted.countDown();
                                  proceed.await(1, TimeUnit.SECONDS);
                                  concurrentCount.decrementAndGet();
                                  return "done";
                                }));
                    protectedTask.run();
                  } catch (Exception e) {
                    // Swallow for this test
                  } finally {
                    allDone.countDown();
                  }
                });
      }

      // Wait for the first batch to be running
      allStarted.await(1, TimeUnit.SECONDS);
      // Let them all finish
      proceed.countDown();
      allDone.await(2, TimeUnit.SECONDS);

      assertThat(maxObservedConcurrent.get()).isLessThanOrEqualTo(maxConcurrent);
    }

    @Test
    @DisplayName("protect() allows sequential tasks to reuse permits")
    void protectAllowsSequentialPermitReuse() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(1);

      for (int i = 0; i < 5; i++) {
        VTask<Integer> task = bulkhead.protect(VTask.succeed(i));
        int result = task.run();
        assertThat(result).isEqualTo(i);
      }

      assertThat(bulkhead.availablePermits()).isEqualTo(1);
    }

    @Test
    @DisplayName("protect() releases permit even when task throws")
    void protectReleasesPermitOnTaskFailure() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(1);

      VTask<String> failingTask =
          bulkhead.protect(
              VTask.of(
                  () -> {
                    throw new RuntimeException("boom");
                  }));

      assertThatThrownBy(failingTask::run).isInstanceOf(RuntimeException.class).hasMessage("boom");

      // Permit should be released, so next task should succeed
      VTask<String> nextTask = bulkhead.protect(VTask.succeed("ok"));
      assertThat(nextTask.run()).isEqualTo("ok");
      assertThat(bulkhead.availablePermits()).isEqualTo(1);
    }

    @Test
    @DisplayName("protect() enforces concurrency with CyclicBarrier")
    void protectEnforcesConcurrencyWithBarrier() throws Exception {
      int maxConcurrent = 3;
      int totalTasks = 3;
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(maxConcurrent);

      CyclicBarrier barrier = new CyclicBarrier(totalTasks);
      AtomicInteger completedCount = new AtomicInteger(0);
      CountDownLatch allDone = new CountDownLatch(totalTasks);

      for (int i = 0; i < totalTasks; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    VTask<String> task =
                        bulkhead.protect(
                            VTask.of(
                                () -> {
                                  barrier.await(1, TimeUnit.SECONDS);
                                  return "done";
                                }));
                    task.run();
                    completedCount.incrementAndGet();
                  } catch (Exception e) {
                    // Swallow
                  } finally {
                    allDone.countDown();
                  }
                });
      }

      allDone.await(2, TimeUnit.SECONDS);
      assertThat(completedCount.get()).isEqualTo(totalTasks);
    }
  }

  @Nested
  @DisplayName("Wait Queue Tests")
  class WaitQueueTests {

    @Test
    @DisplayName("maxWait=0 allows unlimited waiting callers")
    void maxWaitZeroAllowsUnlimitedWaiting() throws Exception {
      BulkheadConfig config =
          BulkheadConfig.builder()
              .maxConcurrent(1)
              .maxWait(0)
              .waitTimeout(Duration.ofMillis(500))
              .build();
      Bulkhead bulkhead = Bulkhead.create(config);

      CountDownLatch taskStarted = new CountDownLatch(1);
      CountDownLatch taskFinish = new CountDownLatch(1);
      CountDownLatch allDone = new CountDownLatch(3);
      AtomicInteger completed = new AtomicInteger(0);

      // First task: holds the permit
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  VTask<String> task =
                      bulkhead.protect(
                          VTask.of(
                              () -> {
                                taskStarted.countDown();
                                taskFinish.await(2, TimeUnit.SECONDS);
                                return "first";
                              }));
                  task.run();
                  completed.incrementAndGet();
                } catch (Exception e) {
                  // Swallow
                } finally {
                  allDone.countDown();
                }
              });

      taskStarted.await(1, TimeUnit.SECONDS);

      // Launch two more tasks that must wait
      for (int i = 0; i < 2; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    VTask<String> task = bulkhead.protect(VTask.succeed("waited"));
                    task.run();
                    completed.incrementAndGet();
                  } catch (Exception e) {
                    // Swallow
                  } finally {
                    allDone.countDown();
                  }
                });
      }

      // Let the first task finish so waiters can proceed
      Thread.sleep(50);
      taskFinish.countDown();
      allDone.await(2, TimeUnit.SECONDS);

      assertThat(completed.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("maxWait rejects callers when wait queue is full")
    void maxWaitRejectsWhenQueueFull() throws Exception {
      BulkheadConfig config =
          BulkheadConfig.builder()
              .maxConcurrent(1)
              .maxWait(1)
              .waitTimeout(Duration.ofMillis(500))
              .build();
      Bulkhead bulkhead = Bulkhead.create(config);

      CountDownLatch taskStarted = new CountDownLatch(1);
      CountDownLatch taskFinish = new CountDownLatch(1);
      CountDownLatch waiterQueued = new CountDownLatch(1);
      AtomicInteger rejectedCount = new AtomicInteger(0);

      // First task: holds the permit
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  VTask<String> task =
                      bulkhead.protect(
                          VTask.of(
                              () -> {
                                taskStarted.countDown();
                                taskFinish.await(2, TimeUnit.SECONDS);
                                return "holder";
                              }));
                  task.run();
                } catch (Exception e) {
                  // Swallow
                }
              });

      taskStarted.await(1, TimeUnit.SECONDS);

      // Second task: waits for the permit (fills the wait queue to 1)
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  waiterQueued.countDown();
                  VTask<String> task = bulkhead.protect(VTask.succeed("waiter"));
                  task.run();
                } catch (Exception e) {
                  // Swallow
                }
              });

      waiterQueued.await(1, TimeUnit.SECONDS);
      // Brief pause to ensure the waiter is actually in tryAcquire
      Thread.sleep(50);

      // Third task: should be rejected because maxWait=1 and queue is full
      VTask<String> rejected = bulkhead.protect(VTask.succeed("rejected"));
      try {
        rejected.run();
      } catch (BulkheadFullException e) {
        rejectedCount.incrementAndGet();
        assertThat(e.maxConcurrent()).isEqualTo(1);
      }

      taskFinish.countDown();
      assertThat(rejectedCount.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Timeout Tests")
  class TimeoutTests {

    @Test
    @DisplayName("protect() throws BulkheadFullException when timeout expires")
    void protectThrowsBulkheadFullExceptionOnTimeout() throws Exception {
      BulkheadConfig config =
          BulkheadConfig.builder().maxConcurrent(1).waitTimeout(Duration.ofMillis(50)).build();
      Bulkhead bulkhead = Bulkhead.create(config);

      CountDownLatch taskStarted = new CountDownLatch(1);
      CountDownLatch taskFinish = new CountDownLatch(1);

      // Hold the only permit
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  VTask<String> task =
                      bulkhead.protect(
                          VTask.of(
                              () -> {
                                taskStarted.countDown();
                                taskFinish.await(2, TimeUnit.SECONDS);
                                return "holder";
                              }));
                  task.run();
                } catch (Exception e) {
                  // Swallow
                }
              });

      taskStarted.await(1, TimeUnit.SECONDS);

      // This should time out waiting for the permit
      VTask<String> timedOutTask = bulkhead.protect(VTask.succeed("should-not-run"));

      assertThatThrownBy(timedOutTask::run)
          .isInstanceOf(BulkheadFullException.class)
          .satisfies(
              e -> {
                BulkheadFullException bfe = (BulkheadFullException) e;
                assertThat(bfe.maxConcurrent()).isEqualTo(1);
              });

      taskFinish.countDown();
    }

    @Test
    @DisplayName("protectWithTimeout() uses custom timeout instead of config timeout")
    void protectWithTimeoutUsesCustomTimeout() throws Exception {
      BulkheadConfig config =
          BulkheadConfig.builder().maxConcurrent(1).waitTimeout(Duration.ofSeconds(10)).build();
      Bulkhead bulkhead = Bulkhead.create(config);

      CountDownLatch taskStarted = new CountDownLatch(1);
      CountDownLatch taskFinish = new CountDownLatch(1);

      // Hold the only permit
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  VTask<String> task =
                      bulkhead.protect(
                          VTask.of(
                              () -> {
                                taskStarted.countDown();
                                taskFinish.await(2, TimeUnit.SECONDS);
                                return "holder";
                              }));
                  task.run();
                } catch (Exception e) {
                  // Swallow
                }
              });

      taskStarted.await(1, TimeUnit.SECONDS);

      // Use a short custom timeout that should expire quickly
      VTask<String> timedOutTask =
          bulkhead.protectWithTimeout(VTask.succeed("should-not-run"), Duration.ofMillis(50));

      assertThatThrownBy(timedOutTask::run).isInstanceOf(BulkheadFullException.class);

      taskFinish.countDown();
    }

    @Test
    @DisplayName("BulkheadFullException contains correct metadata")
    void bulkheadFullExceptionContainsCorrectMetadata() {
      BulkheadFullException exception = new BulkheadFullException(5, 3);

      assertThat(exception.maxConcurrent()).isEqualTo(5);
      assertThat(exception.currentWaiting()).isEqualTo(3);
      assertThat(exception.getMessage()).contains("maxConcurrent=5");
      assertThat(exception.getMessage()).contains("waiting=3");
    }
  }

  @Nested
  @DisplayName("Metrics Tests")
  class MetricsTests {

    @Test
    @DisplayName("availablePermits() returns maxConcurrent initially")
    void availablePermitsReturnsMaxConcurrentInitially() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(5);

      assertThat(bulkhead.availablePermits()).isEqualTo(5);
    }

    @Test
    @DisplayName("activeCount() is zero initially")
    void activeCountIsZeroInitially() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(3);

      assertThat(bulkhead.activeCount()).isZero();
    }

    @Test
    @DisplayName("availablePermits() decreases while tasks are running")
    void availablePermitsDecreasesWhileTasksRunning() throws Exception {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(3);

      CountDownLatch taskStarted = new CountDownLatch(2);
      CountDownLatch taskFinish = new CountDownLatch(1);
      CountDownLatch allDone = new CountDownLatch(2);

      for (int i = 0; i < 2; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    VTask<String> task =
                        bulkhead.protect(
                            VTask.of(
                                () -> {
                                  taskStarted.countDown();
                                  taskFinish.await(2, TimeUnit.SECONDS);
                                  return "running";
                                }));
                    task.run();
                  } catch (Exception e) {
                    // Swallow
                  } finally {
                    allDone.countDown();
                  }
                });
      }

      taskStarted.await(1, TimeUnit.SECONDS);

      assertThat(bulkhead.availablePermits()).isEqualTo(1);
      assertThat(bulkhead.activeCount()).isEqualTo(2);

      taskFinish.countDown();
      allDone.await(2, TimeUnit.SECONDS);

      assertThat(bulkhead.availablePermits()).isEqualTo(3);
      assertThat(bulkhead.activeCount()).isZero();
    }

    @Test
    @DisplayName("metrics are restored after task completion")
    void metricsAreRestoredAfterTaskCompletion() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);

      VTask<String> task = bulkhead.protect(VTask.succeed("value"));
      task.run();

      assertThat(bulkhead.availablePermits()).isEqualTo(2);
      assertThat(bulkhead.activeCount()).isZero();
    }

    @Test
    @DisplayName("metrics are restored after task failure")
    void metricsAreRestoredAfterTaskFailure() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);

      VTask<String> failingTask =
          bulkhead.protect(
              VTask.of(
                  () -> {
                    throw new RuntimeException("fail");
                  }));

      assertThatThrownBy(failingTask::run).isInstanceOf(RuntimeException.class);

      assertThat(bulkhead.availablePermits()).isEqualTo(2);
      assertThat(bulkhead.activeCount()).isZero();
    }
  }

  @Nested
  @DisplayName("Fairness Tests")
  class FairnessTests {

    @Test
    @DisplayName("fairness=true creates a working bulkhead")
    void fairnessTrueCreatesWorkingBulkhead() {
      BulkheadConfig config = BulkheadConfig.builder().maxConcurrent(2).fairness(true).build();
      Bulkhead bulkhead = Bulkhead.create(config);

      VTask<String> task = bulkhead.protect(VTask.succeed("fair"));
      assertThat(task.run()).isEqualTo("fair");
    }

    @Test
    @DisplayName("fairness=false creates a working bulkhead")
    void fairnessFalseCreatesWorkingBulkhead() {
      BulkheadConfig config = BulkheadConfig.builder().maxConcurrent(2).fairness(false).build();
      Bulkhead bulkhead = Bulkhead.create(config);

      VTask<String> task = bulkhead.protect(VTask.succeed("unfair"));
      assertThat(task.run()).isEqualTo("unfair");
    }

    @Test
    @DisplayName("fair bulkhead handles concurrent access correctly")
    void fairBulkheadHandlesConcurrentAccess() throws Exception {
      BulkheadConfig config =
          BulkheadConfig.builder()
              .maxConcurrent(1)
              .fairness(true)
              .waitTimeout(Duration.ofMillis(500))
              .build();
      Bulkhead bulkhead = Bulkhead.create(config);

      AtomicInteger completedCount = new AtomicInteger(0);
      CountDownLatch allDone = new CountDownLatch(3);

      for (int i = 0; i < 3; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    VTask<String> task =
                        bulkhead.protect(
                            VTask.of(
                                () -> {
                                  Thread.sleep(10);
                                  return "done";
                                }));
                    task.run();
                    completedCount.incrementAndGet();
                  } catch (Exception e) {
                    // Swallow
                  } finally {
                    allDone.countDown();
                  }
                });
      }

      allDone.await(2, TimeUnit.SECONDS);
      assertThat(completedCount.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Generic Protect Tests")
  class GenericProtectTests {

    @Test
    @DisplayName("protect() works with String return type")
    void protectWorksWithStringType() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);

      VTask<String> task = bulkhead.protect(VTask.succeed("hello"));
      assertThat(task.run()).isEqualTo("hello");
    }

    @Test
    @DisplayName("protect() works with Integer return type")
    void protectWorksWithIntegerType() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);

      VTask<Integer> task = bulkhead.protect(VTask.succeed(42));
      assertThat(task.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("protect() works with Boolean return type")
    void protectWorksWithBooleanType() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);

      VTask<Boolean> task = bulkhead.protect(VTask.succeed(true));
      assertThat(task.run()).isTrue();
    }

    @Test
    @DisplayName("protect() works with custom record type")
    void protectWorksWithCustomRecordType() {
      record Payload(String name, int value) {}

      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);

      Payload expected = new Payload("test", 99);
      VTask<Payload> task = bulkhead.protect(VTask.succeed(expected));
      assertThat(task.run()).isEqualTo(expected);
    }

    @Test
    @DisplayName("same bulkhead protects tasks of different types")
    void sameBulkheadProtectsDifferentTypes() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(3);

      VTask<String> stringTask = bulkhead.protect(VTask.succeed("text"));
      VTask<Integer> intTask = bulkhead.protect(VTask.succeed(123));
      VTask<Double> doubleTask = bulkhead.protect(VTask.succeed(3.14));

      assertThat(stringTask.run()).isEqualTo("text");
      assertThat(intTask.run()).isEqualTo(123);
      assertThat(doubleTask.run()).isEqualTo(3.14);

      assertThat(bulkhead.availablePermits()).isEqualTo(3);
    }

    @Test
    @DisplayName("protect() validates null task argument")
    void protectValidatesNullTask() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);

      assertThatNullPointerException()
          .isThrownBy(() -> bulkhead.protect(null))
          .withMessageContaining("task must not be null");
    }

    @Test
    @DisplayName("protectWithTimeout() validates null task argument")
    void protectWithTimeoutValidatesNullTask() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);

      assertThatNullPointerException()
          .isThrownBy(() -> bulkhead.protectWithTimeout(null, Duration.ofMillis(100)))
          .withMessageContaining("task must not be null");
    }

    @Test
    @DisplayName("protectWithTimeout() validates null timeout argument")
    void protectWithTimeoutValidatesNullTimeout() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);

      assertThatNullPointerException()
          .isThrownBy(() -> bulkhead.protectWithTimeout(VTask.succeed("x"), null))
          .withMessageContaining("waitTimeout must not be null");
    }

    @Test
    @DisplayName("create() validates null config argument")
    void createValidatesNullConfig() {
      assertThatNullPointerException()
          .isThrownBy(() -> Bulkhead.create(null))
          .withMessageContaining("config must not be null");
    }
  }

  @Nested
  @DisplayName("Config Validation Tests")
  class ConfigValidationTests {

    @Test
    @DisplayName("BulkheadConfig rejects maxConcurrent less than 1")
    void bulkheadConfigRejectsInvalidMaxConcurrent() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> BulkheadConfig.builder().maxConcurrent(0).build())
          .withMessageContaining("maxConcurrent must be at least 1");

      assertThatIllegalArgumentException()
          .isThrownBy(() -> BulkheadConfig.builder().maxConcurrent(-5).build())
          .withMessageContaining("maxConcurrent must be at least 1");
    }

    @Test
    @DisplayName("BulkheadConfig rejects negative maxWait")
    void bulkheadConfigRejectsNegativeMaxWait() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> BulkheadConfig.builder().maxWait(-1).build())
          .withMessageContaining("maxWait must not be negative");
    }

    @Test
    @DisplayName("withMaxConcurrent rejects value less than 1")
    void withMaxConcurrentRejectsInvalidValue() {
      assertThatIllegalArgumentException().isThrownBy(() -> Bulkhead.withMaxConcurrent(0));
    }
  }

  @Nested
  @DisplayName("InterruptedException Tests")
  class InterruptionTests {

    @Test
    @DisplayName("protect() throws BulkheadFullException when thread is interrupted while waiting")
    void protectThrowsBulkheadFullExceptionOnInterrupt() throws Exception {
      BulkheadConfig config =
          BulkheadConfig.builder().maxConcurrent(1).waitTimeout(Duration.ofSeconds(5)).build();
      Bulkhead bulkhead = Bulkhead.create(config);

      CountDownLatch taskStarted = new CountDownLatch(1);
      CountDownLatch taskFinish = new CountDownLatch(1);

      // Hold the only permit
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  VTask<String> task =
                      bulkhead.protect(
                          VTask.of(
                              () -> {
                                taskStarted.countDown();
                                taskFinish.await(5, TimeUnit.SECONDS);
                                return "holder";
                              }));
                  task.run();
                } catch (Exception e) {
                  // Swallow
                }
              });

      taskStarted.await(1, TimeUnit.SECONDS);

      // Start a thread that will be interrupted while waiting for the permit
      CountDownLatch waiterReady = new CountDownLatch(1);
      AtomicInteger exceptionCount = new AtomicInteger(0);
      Thread waiterThread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    waiterReady.countDown();
                    try {
                      VTask<String> task = bulkhead.protect(VTask.succeed("should-not-run"));
                      task.run();
                    } catch (BulkheadFullException e) {
                      exceptionCount.incrementAndGet();
                    }
                  });

      waiterReady.await(1, TimeUnit.SECONDS);
      Thread.sleep(50); // Let it enter tryAcquire
      waiterThread.interrupt();
      waiterThread.join(2000);

      taskFinish.countDown();

      assertThat(exceptionCount.get()).isEqualTo(1);
    }
  }

  // ==========================================================================
  // Audit Issue #6: maxWait must be enforced atomically (TOCTOU race)
  // ==========================================================================

  @Nested
  @DisplayName("Atomic maxWait Enforcement (audit issue #6)")
  class AtomicMaxWaitTests {

    @Test
    @DisplayName("maxWait limit is enforced even under concurrent pressure")
    void maxWaitLimitEnforcedUnderConcurrency() throws Exception {
      int maxConcurrent = 1;
      int maxWait = 2;
      BulkheadConfig config =
          BulkheadConfig.builder()
              .maxConcurrent(maxConcurrent)
              .maxWait(maxWait)
              .waitTimeout(Duration.ofSeconds(2))
              .fairness(true)
              .build();
      Bulkhead bulkhead = Bulkhead.create(config);

      CountDownLatch holderStarted = new CountDownLatch(1);
      CountDownLatch holderFinish = new CountDownLatch(1);

      // Hold the only permit
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  bulkhead
                      .protect(
                          VTask.of(
                              () -> {
                                holderStarted.countDown();
                                holderFinish.await(5, TimeUnit.SECONDS);
                                return "holder";
                              }))
                      .run();
                } catch (Exception ignored) {
                }
              });

      holderStarted.await(1, TimeUnit.SECONDS);

      // Fill the wait queue with maxWait=2 waiters
      CountDownLatch waitersQueued = new CountDownLatch(maxWait);
      for (int i = 0; i < maxWait; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    waitersQueued.countDown();
                    bulkhead.protect(VTask.succeed("waiter")).run();
                  } catch (Exception ignored) {
                  }
                });
      }

      waitersQueued.await(1, TimeUnit.SECONDS);
      Thread.sleep(100); // Let waiters enter tryAcquire

      // Concurrently attempt many more callers — all should be rejected
      int extraCallers = 20;
      CyclicBarrier barrier = new CyclicBarrier(extraCallers);
      AtomicInteger rejectedCount = new AtomicInteger(0);
      AtomicInteger acceptedCount = new AtomicInteger(0);
      CountDownLatch extraDone = new CountDownLatch(extraCallers);

      for (int i = 0; i < extraCallers; i++) {
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    barrier.await(2, TimeUnit.SECONDS);
                    bulkhead.protect(VTask.succeed("extra")).run();
                    acceptedCount.incrementAndGet();
                  } catch (BulkheadFullException e) {
                    rejectedCount.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    extraDone.countDown();
                  }
                });
      }

      extraDone.await(5, TimeUnit.SECONDS);
      holderFinish.countDown();

      assertThat(rejectedCount.get())
          .as("All extra callers beyond maxWait should be rejected")
          .isEqualTo(extraCallers);
    }
  }
}
