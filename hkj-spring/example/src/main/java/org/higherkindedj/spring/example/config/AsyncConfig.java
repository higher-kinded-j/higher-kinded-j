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
 * <p>This configuration creates the thread pool executor used by {@link
 * org.higherkindedj.spring.example.service.AsyncUserService} for {@code CompletableFuturePath}
 * operations. The starter does <b>not</b> create or configure this pool — defining an executor bean
 * in the application (and passing it to {@code CompletableFuture.supplyAsync}) is the documented
 * pattern. Naming the bean {@code hkjAsyncExecutor} also enables the optional {@code hkj-async}
 * actuator health indicator.
 *
 * <p>The executor is hardcoded here with:
 *
 * <ul>
 *   <li>Core pool size: 10 threads
 *   <li>Max pool size: 20 threads
 *   <li>Queue capacity: 100 tasks
 *   <li>Thread name prefix: "hkj-async-" for easy identification
 * </ul>
 *
 * <p>To change these settings, edit this bean (there are no {@code hkj.async.executor-*}
 * configuration properties). The only async property the starter reads is {@code
 * hkj.async.default-timeout-ms}, the response timeout for {@code CompletableFuturePath} requests.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  /** Creates an AsyncConfig instance. */
  public AsyncConfig() {}

  /**
   * Creates the async executor for CompletableFuture-based async operations.
   *
   * <p>This executor is used by:
   *
   * <ul>
   *   <li>AsyncUserService for async database/external service calls
   *   <li>CompletableFuture.supplyAsync() calls in services
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
