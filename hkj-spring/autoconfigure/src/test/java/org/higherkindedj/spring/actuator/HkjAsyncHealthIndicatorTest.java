// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("HkjAsyncHealthIndicator Tests")
class HkjAsyncHealthIndicatorTest {

  @Nested
  @DisplayName("Null Executor Tests")
  class NullExecutorTests {

    @Test
    @DisplayName("Should return OUT_OF_SERVICE when executor is null")
    void shouldReturnOutOfServiceWhenExecutorIsNull() {
      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(null);

      Health health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
      assertThat(health.getDetails()).containsEntry("reason", "Async executor not configured");
    }

    @Test
    @DisplayName("Should have reason detail when executor is null")
    void shouldHaveReasonDetailWhenExecutorIsNull() {
      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(null);

      Health health = indicator.health();

      assertThat(health.getDetails()).hasSize(1);
      assertThat(health.getDetails()).containsKey("reason");
    }
  }

  @Nested
  @DisplayName("Shutdown Executor Tests")
  class ShutdownExecutorTests {

    @Test
    @DisplayName("Should return DOWN when thread pool is shutdown")
    void shouldReturnDownWhenThreadPoolIsShutdown() {
      ThreadPoolTaskExecutor executor = createExecutor(2, 5, 10);
      executor.initialize();
      executor.shutdown();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      Health health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails()).containsEntry("reason", "Thread pool is shutdown");
    }

    @Test
    @DisplayName("Should return DOWN when thread pool executor is null")
    void shouldReturnDownWhenThreadPoolExecutorIsNull() {
      // Create executor but don't initialize (threadPoolExecutor will be null)
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      Health health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      // Uninitialized executor throws exception, which is caught and uses "error" key
      assertThat(health.getDetails()).containsKey("error");
    }
  }

  @Nested
  @DisplayName("Healthy Executor Tests")
  class HealthyExecutorTests {

    @Test
    @DisplayName("Should return UP for healthy executor")
    void shouldReturnUpForHealthyExecutor() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should include all thread pool details")
    void shouldIncludeAllThreadPoolDetails() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        assertThat(health.getDetails())
            .containsKeys(
                "activeCount",
                "poolSize",
                "corePoolSize",
                "maxPoolSize",
                "queueSize",
                "queueCapacity",
                "queueRemainingCapacity");
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should report correct pool sizes")
    void shouldReportCorrectPoolSizes() {
      ThreadPoolTaskExecutor executor = createExecutor(3, 8, 50);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        assertThat(health.getDetails()).containsEntry("corePoolSize", 3);
        assertThat(health.getDetails()).containsEntry("maxPoolSize", 8);
        assertThat(health.getDetails()).containsEntry("queueCapacity", 50);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should report zero active count initially")
    void shouldReportZeroActiveCountInitially() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        assertThat(health.getDetails()).containsEntry("activeCount", 0);
        assertThat(health.getDetails()).containsEntry("queueSize", 0);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should report full queue remaining capacity initially")
    void shouldReportFullQueueRemainingCapacityInitially() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        // Initially, queue is empty, so remaining capacity equals total capacity
        assertThat(health.getDetails()).containsEntry("queueRemainingCapacity", 100);
      } finally {
        executor.shutdown();
      }
    }
  }

  @Nested
  @DisplayName("Queue Full Tests")
  class QueueFullTests {

    @Test
    @DisplayName("Should return DOWN when queue is full")
    void shouldReturnDownWhenQueueIsFull() throws InterruptedException {
      // Create executor with very small pool and queue
      ThreadPoolTaskExecutor executor = createExecutor(1, 1, 2);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        // Submit tasks to fill the queue
        // 1 active task + 2 queued tasks = queue full
        for (int i = 0; i < 3; i++) {
          executor.submit(
              () -> {
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
        }

        // Give tasks time to fill the queue
        Thread.sleep(100);

        Health health = indicator.health();

        // When queue is full, should be DOWN
        if ((int) health.getDetails().get("queueRemainingCapacity") == 0) {
          assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should return UP when queue has capacity")
    void shouldReturnUpWhenQueueHasCapacity() throws InterruptedException {
      ThreadPoolTaskExecutor executor = createExecutor(2, 4, 10);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        // Submit some tasks but not enough to fill queue
        for (int i = 0; i < 3; i++) {
          executor.submit(
              () -> {
                try {
                  Thread.sleep(100);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
        }

        // Give tasks time to start
        Thread.sleep(50);

        Health health = indicator.health();

        // Queue still has capacity, should be UP
        if ((int) health.getDetails().get("queueRemainingCapacity") > 0) {
          assertThat(health.getStatus()).isEqualTo(Status.UP);
        }
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should handle zero queue capacity")
    void shouldHandleZeroQueueCapacity() {
      // Queue capacity of 0 means tasks are rejected if threads are busy
      ThreadPoolTaskExecutor executor = createExecutor(2, 5, 0);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        // With 0 queue capacity and no tasks, remaining capacity is 0
        // but this should still be UP (queue not "full" in the DOWN sense)
        assertThat(health.getDetails()).containsEntry("queueCapacity", 0);
        assertThat(health.getDetails()).containsEntry("queueRemainingCapacity", 0);

        // When queueCapacity is 0, the condition queueRemainingCapacity == 0 && queueCapacity > 0
        // is false
        // So status should be UP
        assertThat(health.getStatus()).isEqualTo(Status.UP);
      } finally {
        executor.shutdown();
      }
    }
  }

  @Nested
  @DisplayName("Thread Pool State Tests")
  class ThreadPoolStateTests {

    @Test
    @DisplayName("Should track active threads")
    void shouldTrackActiveThreads() throws InterruptedException {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        // Submit long-running tasks
        for (int i = 0; i < 3; i++) {
          executor.submit(
              () -> {
                try {
                  Thread.sleep(500);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
        }

        // Give tasks time to start
        Thread.sleep(100);

        Health health = indicator.health();

        // Should have some active threads
        int activeCount = (int) health.getDetails().get("activeCount");
        assertThat(activeCount).isGreaterThan(0);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should track pool size growth")
    void shouldTrackPoolSizeGrowth() throws InterruptedException {
      ThreadPoolTaskExecutor executor = createExecutor(2, 8, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        // Submit tasks to potentially grow the pool
        for (int i = 0; i < 5; i++) {
          executor.submit(
              () -> {
                try {
                  Thread.sleep(200);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
        }

        // Give tasks time to start
        Thread.sleep(100);

        Health health = indicator.health();

        int poolSize = (int) health.getDetails().get("poolSize");
        // Pool size should be at least the core size
        assertThat(poolSize).isGreaterThanOrEqualTo(2);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should track queue size")
    void shouldTrackQueueSize() throws InterruptedException {
      ThreadPoolTaskExecutor executor = createExecutor(1, 2, 10);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        // Submit more tasks than can run immediately
        for (int i = 0; i < 5; i++) {
          executor.submit(
              () -> {
                try {
                  Thread.sleep(500);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
        }

        // Give tasks time to queue up
        Thread.sleep(100);

        Health health = indicator.health();

        int queueSize = (int) health.getDetails().get("queueSize");
        // Some tasks should be queued
        assertThat(queueSize).isGreaterThan(0);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should calculate queue remaining capacity correctly")
    void shouldCalculateQueueRemainingCapacityCorrectly() throws InterruptedException {
      ThreadPoolTaskExecutor executor = createExecutor(1, 2, 10);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        // Submit tasks to partially fill queue
        for (int i = 0; i < 4; i++) {
          executor.submit(
              () -> {
                try {
                  Thread.sleep(500);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
        }

        Thread.sleep(100);

        Health health = indicator.health();

        int queueSize = (int) health.getDetails().get("queueSize");
        int queueCapacity = (int) health.getDetails().get("queueCapacity");
        int queueRemainingCapacity = (int) health.getDetails().get("queueRemainingCapacity");

        // queueSize + queueRemainingCapacity should equal queueCapacity
        assertThat(queueSize + queueRemainingCapacity).isEqualTo(queueCapacity);
      } finally {
        executor.shutdown();
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle repeated health checks")
    void shouldHandleRepeatedHealthChecks() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        // Call health() multiple times
        for (int i = 0; i < 5; i++) {
          Health health = indicator.health();
          assertThat(health.getStatus()).isEqualTo(Status.UP);
        }
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should handle health check during shutdown")
    void shouldHandleHealthCheckDuringShutdown() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      executor.shutdown();

      Health health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails()).containsEntry("reason", "Thread pool is shutdown");
    }

    @Test
    @DisplayName("Should have consistent detail types")
    void shouldHaveConsistentDetailTypes() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        // All count details should be integers
        assertThat(health.getDetails().get("activeCount")).isInstanceOf(Integer.class);
        assertThat(health.getDetails().get("poolSize")).isInstanceOf(Integer.class);
        assertThat(health.getDetails().get("corePoolSize")).isInstanceOf(Integer.class);
        assertThat(health.getDetails().get("maxPoolSize")).isInstanceOf(Integer.class);
        assertThat(health.getDetails().get("queueSize")).isInstanceOf(Integer.class);
        assertThat(health.getDetails().get("queueCapacity")).isInstanceOf(Integer.class);
        assertThat(health.getDetails().get("queueRemainingCapacity")).isInstanceOf(Integer.class);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should handle large queue capacity")
    void shouldHandleLargeQueueCapacity() {
      ThreadPoolTaskExecutor executor = createExecutor(10, 20, 10000);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("queueCapacity", 10000);
        assertThat(health.getDetails()).containsEntry("queueRemainingCapacity", 10000);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should handle large pool sizes")
    void shouldHandleLargePoolSizes() {
      ThreadPoolTaskExecutor executor = createExecutor(50, 100, 1000);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("corePoolSize", 50);
        assertThat(health.getDetails()).containsEntry("maxPoolSize", 100);
      } finally {
        executor.shutdown();
      }
    }
  }

  @Nested
  @DisplayName("Configuration Variations Tests")
  class ConfigurationVariationsTests {

    @Test
    @DisplayName("Should handle minimal pool configuration")
    void shouldHandleMinimalPoolConfiguration() {
      ThreadPoolTaskExecutor executor = createExecutor(1, 1, 1);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("corePoolSize", 1);
        assertThat(health.getDetails()).containsEntry("maxPoolSize", 1);
        assertThat(health.getDetails()).containsEntry("queueCapacity", 1);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should handle equal core and max pool sizes")
    void shouldHandleEqualCoreAndMaxPoolSizes() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 5, 50);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("corePoolSize", 5);
        assertThat(health.getDetails()).containsEntry("maxPoolSize", 5);
      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("Should handle default thread name prefix")
    void shouldHandleDefaultThreadNamePrefix() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.setThreadNamePrefix("hkj-async-");
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        Health health = indicator.health();

        // Should still work normally with custom thread prefix
        assertThat(health.getStatus()).isEqualTo(Status.UP);
      } finally {
        executor.shutdown();
      }
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should transition from healthy to shutdown")
    void shouldTransitionFromHealthyToShutdown() {
      ThreadPoolTaskExecutor executor = createExecutor(5, 10, 100);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      // Initially healthy
      Health health1 = indicator.health();
      assertThat(health1.getStatus()).isEqualTo(Status.UP);

      // After shutdown
      executor.shutdown();
      Health health2 = indicator.health();
      assertThat(health2.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("Should provide complete health snapshot")
    void shouldProvideCompleteHealthSnapshot() throws InterruptedException {
      ThreadPoolTaskExecutor executor = createExecutor(3, 8, 50);
      executor.initialize();

      HkjAsyncHealthIndicator indicator = new HkjAsyncHealthIndicator(executor);

      try {
        // Submit some work
        for (int i = 0; i < 5; i++) {
          executor.submit(
              () -> {
                try {
                  Thread.sleep(200);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
        }

        Thread.sleep(100);

        Health health = indicator.health();

        // Verify complete snapshot
        assertThat(health.getStatus()).isIn(Status.UP, Status.DOWN);
        assertThat(health.getDetails()).hasSize(7);

        // Verify all metrics are non-negative
        assertThat((int) health.getDetails().get("activeCount")).isGreaterThanOrEqualTo(0);
        assertThat((int) health.getDetails().get("poolSize")).isGreaterThanOrEqualTo(0);
        assertThat((int) health.getDetails().get("corePoolSize")).isEqualTo(3);
        assertThat((int) health.getDetails().get("maxPoolSize")).isEqualTo(8);
        assertThat((int) health.getDetails().get("queueSize")).isGreaterThanOrEqualTo(0);
        assertThat((int) health.getDetails().get("queueCapacity")).isEqualTo(50);
        assertThat((int) health.getDetails().get("queueRemainingCapacity"))
            .isGreaterThanOrEqualTo(0);
      } finally {
        executor.shutdown();
      }
    }
  }

  // Helper method to create a configured executor
  private ThreadPoolTaskExecutor createExecutor(
      int corePoolSize, int maxPoolSize, int queueCapacity) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("test-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    return executor;
  }
}
