// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for async task execution using Spring's @Async support.
 *
 * <p>This configuration creates a custom thread pool executor specifically for async operations in
 * the higher-kinded-j Spring integration, particularly for {@code
 * EitherT<CompletableFuture.Witness, E, A>} return types.
 *
 * <p>The executor is configured with:
 *
 * <ul>
 *   <li>Core pool size: 10 threads
 *   <li>Max pool size: 20 threads
 *   <li>Queue capacity: 100 tasks
 *   <li>Thread name prefix: "hkj-async-" for easy identification
 * </ul>
 *
 * <p>These settings can be customized via application.properties using:
 *
 * <pre>
 * hkj.async.executor-core-pool-size=10
 * hkj.async.executor-max-pool-size=20
 * hkj.async.executor-queue-capacity=100
 * hkj.async.executor-thread-name-prefix=hkj-async-
 * </pre>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  /**
   * Creates the async executor for CompletableFuture-based async operations.
   *
   * <p>This executor is used by:
   *
   * <ul>
   *   <li>AsyncUserService for async database/external service calls
   *   <li>CompletableFuture.supplyAsync() calls in services
   *   <li>EitherT async operation chains
   * </ul>
   *
   * @return configured ThreadPoolTaskExecutor
   */
  @Bean(name = "hkjAsyncExecutor")
  public Executor hkjAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    // Core pool size - minimum number of threads to keep alive
    executor.setCorePoolSize(10);

    // Max pool size - maximum number of threads
    executor.setMaxPoolSize(20);

    // Queue capacity - number of tasks to queue before rejecting
    executor.setQueueCapacity(100);

    // Thread name prefix for easy identification in logs/debugging
    executor.setThreadNamePrefix("hkj-async-");

    // Wait for tasks to complete on shutdown
    executor.setWaitForTasksToCompleteOnShutdown(true);

    // Shutdown timeout
    executor.setAwaitTerminationSeconds(60);

    executor.initialize();
    return executor;
  }
}
