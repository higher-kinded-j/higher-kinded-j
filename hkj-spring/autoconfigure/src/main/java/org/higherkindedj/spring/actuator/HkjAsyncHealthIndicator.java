// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Health indicator for the higher-kinded-j async executor.
 *
 * <p>Monitors the async thread pool used by EitherT operations to ensure:
 *
 * <ul>
 *   <li>Thread pool is not exhausted
 *   <li>Queue is not full
 *   <li>Active threads are within expected range
 * </ul>
 *
 * <p>Health status:
 *
 * <ul>
 *   <li>UP - Thread pool healthy, queue has capacity
 *   <li>DOWN - Thread pool shutdown or queue full
 *   <li>OUT_OF_SERVICE - Executor unavailable
 * </ul>
 *
 * <p>Example health response:
 *
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "hkjAsync": {
 *       "status": "UP",
 *       "details": {
 *         "activeCount": 2,
 *         "poolSize": 10,
 *         "corePoolSize": 10,
 *         "maxPoolSize": 20,
 *         "queueSize": 5,
 *         "queueCapacity": 100,
 *         "queueRemainingCapacity": 95
 *       }
 *     }
 *   }
 * }
 * }</pre>
 */
public class HkjAsyncHealthIndicator implements HealthIndicator {

  private final @Nullable Executor executor;

  /**
   * Creates a new HkjAsyncHealthIndicator.
   *
   * <p>Accepts any {@link Executor}: full pool statistics are reported for a {@link
   * ThreadPoolTaskExecutor}, while any other executor type (e.g. a virtual-thread-per-task
   * executor) reports {@code UP} with a {@code type} detail rather than failing — the named {@code
   * hkjAsyncExecutor} bean is not required to be a {@code ThreadPoolTaskExecutor}.
   *
   * @param executor the async executor to monitor (may be null if not configured)
   */
  public HkjAsyncHealthIndicator(@Nullable Executor executor) {
    this.executor = executor;
  }

  @Override
  public Health health() {
    if (executor == null) {
      return Health.outOfService().withDetail("reason", "Async executor not configured").build();
    }

    if (!(executor instanceof ThreadPoolTaskExecutor poolExecutor)) {
      // Pool statistics are only available for ThreadPoolTaskExecutor. A shutdown ExecutorService
      // (including a closed virtual-thread executor) can still be reported as unhealthy, since
      // submissions would be rejected — check that before the type-only UP.
      if (executor instanceof ExecutorService executorService && executorService.isShutdown()) {
        return Health.down()
            .withDetail("type", executor.getClass().getSimpleName())
            .withDetail("reason", "Executor is shutdown")
            .build();
      }
      // Otherwise report UP with the type so a live virtual-thread (or other) executor does not
      // read as unhealthy or fail startup.
      return Health.up()
          .withDetail("type", executor.getClass().getSimpleName())
          .withDetail("detail", "pool statistics unavailable for this executor type")
          .build();
    }

    try {
      var threadPoolExecutor = poolExecutor.getThreadPoolExecutor();

      if (threadPoolExecutor == null || threadPoolExecutor.isShutdown()) {
        return Health.down().withDetail("reason", "Thread pool is shutdown").build();
      }

      int activeCount = threadPoolExecutor.getActiveCount();
      int poolSize = threadPoolExecutor.getPoolSize();
      int corePoolSize = threadPoolExecutor.getCorePoolSize();
      int maxPoolSize = threadPoolExecutor.getMaximumPoolSize();
      int queueSize = threadPoolExecutor.getQueue().size();
      int queueCapacity = poolExecutor.getQueueCapacity();
      int queueRemainingCapacity = threadPoolExecutor.getQueue().remainingCapacity();

      // Health check: queue should not be full
      boolean queueFull = queueRemainingCapacity == 0 && queueCapacity > 0;

      Health.Builder builder = queueFull ? Health.down() : Health.up();

      return builder
          .withDetail("activeCount", activeCount)
          .withDetail("poolSize", poolSize)
          .withDetail("corePoolSize", corePoolSize)
          .withDetail("maxPoolSize", maxPoolSize)
          .withDetail("queueSize", queueSize)
          .withDetail("queueCapacity", queueCapacity)
          .withDetail("queueRemainingCapacity", queueRemainingCapacity)
          .build();

    } catch (Exception e) {
      return Health.down().withDetail("error", e.getMessage()).build();
    }
  }
}
