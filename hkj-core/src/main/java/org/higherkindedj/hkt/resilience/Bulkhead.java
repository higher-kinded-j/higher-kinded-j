// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * A bulkhead that limits the number of concurrent executions of {@link VTask} operations.
 *
 * <p>The bulkhead uses a {@link Semaphore} to enforce a maximum number of concurrent callers
 * to a shared resource. When the limit is reached, additional callers either wait (up to
 * the configured timeout) or are immediately rejected with {@link BulkheadFullException}.
 *
 * <p><b>Relationship to VStreamPar:</b> {@code VStreamPar} handles bounded concurrency for
 * stream pipelines (e.g., "process this stream with at most 4 in-flight elements").
 * {@code Bulkhead} protects a shared service or resource at the VTask level (e.g., "this
 * database allows at most 10 concurrent connections"). A Bulkhead can be used inside
 * VStreamPar processing to share a concurrency limit across multiple streams accessing the
 * same service.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Bulkhead dbBulkhead = Bulkhead.withMaxConcurrent(10);
 *
 * VTask<Result> protectedQuery = dbBulkhead.protect(
 *     VTask.of(() -> database.query(sql)));
 * }</pre>
 *
 * @see BulkheadConfig
 * @see BulkheadFullException
 */
public final class Bulkhead {

  private final BulkheadConfig config;
  private final Semaphore semaphore;
  private final AtomicInteger waitingCount = new AtomicInteger();

  private Bulkhead(BulkheadConfig config) {
    this.config = config;
    this.semaphore = new Semaphore(config.maxConcurrent(), config.fairness());
  }

  // ===== Factory Methods =====

  /**
   * Creates a bulkhead with the given configuration.
   *
   * @param config the configuration; must not be null
   * @return a new Bulkhead
   * @throws NullPointerException if config is null
   */
  public static Bulkhead create(BulkheadConfig config) {
    Objects.requireNonNull(config, "config must not be null");
    return new Bulkhead(config);
  }

  /**
   * Creates a bulkhead with the given maximum concurrent executions and default settings
   * for everything else.
   *
   * @param maxConcurrent the maximum number of concurrent executions
   * @return a new Bulkhead
   * @throws IllegalArgumentException if maxConcurrent is less than 1
   */
  public static Bulkhead withMaxConcurrent(int maxConcurrent) {
    return new Bulkhead(BulkheadConfig.builder().maxConcurrent(maxConcurrent).build());
  }

  // ===== Protection =====

  /**
   * Returns a new {@link VTask} protected by this bulkhead.
   *
   * <p>When the task is executed, it will first acquire a permit from the semaphore. If no
   * permit is available, it waits up to the configured timeout. If the timeout expires or
   * the maximum number of waiters is exceeded, a {@link BulkheadFullException} is thrown.
   * The permit is always released after the task completes (success or failure).
   *
   * @param task the task to protect; must not be null
   * @param <A> the result type
   * @return a new VTask protected by this bulkhead
   * @throws NullPointerException if task is null
   */
  public <A> VTask<A> protect(VTask<A> task) {
    Objects.requireNonNull(task, "task must not be null");
    return protectWithTimeout(task, config.waitTimeout());
  }

  /**
   * Returns a new {@link VTask} protected by this bulkhead with a custom wait timeout.
   *
   * @param task the task to protect; must not be null
   * @param waitTimeout how long to wait for a permit; must not be null
   * @param <A> the result type
   * @return a new VTask protected by this bulkhead
   * @throws NullPointerException if task or waitTimeout is null
   */
  public <A> VTask<A> protectWithTimeout(VTask<A> task, Duration waitTimeout) {
    Objects.requireNonNull(task, "task must not be null");
    Objects.requireNonNull(waitTimeout, "waitTimeout must not be null");
    return () -> {
      // Check if we can accept another waiter
      int currentWaiters = waitingCount.get();
      if (config.maxWait() > 0 && currentWaiters >= config.maxWait()
          && semaphore.availablePermits() == 0) {
        throw new BulkheadFullException(config.maxConcurrent(), currentWaiters);
      }

      waitingCount.incrementAndGet();
      try {
        boolean acquired = semaphore.tryAcquire(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!acquired) {
          throw new BulkheadFullException(config.maxConcurrent(), waitingCount.get() - 1);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BulkheadFullException(config.maxConcurrent(), waitingCount.get() - 1);
      } finally {
        waitingCount.decrementAndGet();
      }

      try {
        return task.run();
      } finally {
        semaphore.release();
      }
    };
  }

  // ===== Metrics =====

  /**
   * Returns the number of permits currently available.
   *
   * @return the available permits
   */
  public int availablePermits() {
    return semaphore.availablePermits();
  }

  /**
   * Returns the number of callers currently executing within the bulkhead.
   *
   * @return the number of active callers
   */
  public int activeCount() {
    return config.maxConcurrent() - semaphore.availablePermits();
  }
}
