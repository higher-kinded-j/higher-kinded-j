// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.RequestContext;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: Context with VTask - Concurrent Context Propagation
 *
 * <p>Learn how Context integrates with VTask and Scope for concurrent operations. ScopedValue
 * bindings automatically propagate to child virtual threads, making Context ideal for structured
 * concurrency.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Context.toVTask() converts a Context to a VTask
 *   <li>ScopedValue bindings inherit to child virtual threads
 *   <li>Scope operations can read from inherited context
 *   <li>Parallel tasks share the same context automatically
 * </ul>
 *
 * <p>Requirements: Java 25+ (ScopedValue and structured concurrency)
 *
 * <p>Estimated time: 25-30 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 05: Context with VTask")
public class Tutorial05_ContextWithVTask {

  /** Helper method for incomplete exercises. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Converting Context to VTask
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Context to VTask Conversion")
  class ContextToVTask {

    /**
     * Exercise 1: Convert Context to VTask
     *
     * <p>Context.toVTask() creates a VTask that, when run, executes the Context computation.
     *
     * <p>Task: Convert a Context to VTask and run it within a scope
     */
    @Test
    @DisplayName("Exercise 1: Basic toVTask conversion")
    void exercise1_basicToVTask() throws Exception {
      String traceId = "trace-12345";

      Context<String, String> getTraceId = Context.ask(RequestContext.TRACE_ID);

      // TODO: Replace answerRequired() with getTraceId.toVTask()
      VTask<String> traceTask = answerRequired();

      // Run the VTask within a scope binding
      Try<String> result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId).call(() -> traceTask.runSafe());

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse("error")).isEqualTo(traceId);
    }

    /**
     * Exercise 2: Chain Context operations then convert to VTask
     *
     * <p>You can build up a Context with map/flatMap, then convert the final result to VTask.
     *
     * <p>Task: Create a context chain and convert to VTask
     */
    @Test
    @DisplayName("Exercise 2: Chain then convert")
    void exercise2_chainThenConvert() throws Exception {
      String traceId = "abc-123";

      // Build up the context computation
      Context<String, String> formatted =
          Context.ask(RequestContext.TRACE_ID).map(id -> "[trace=" + id + "]");

      // TODO: Replace answerRequired() with formatted.toVTask()
      VTask<String> task = answerRequired();

      Try<String> result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId).call(() -> task.runSafe());

      assertThat(result.orElse("error")).isEqualTo("[trace=abc-123]");
    }
  }

  // ===========================================================================
  // Part 2: Context Propagation in VTask
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Context Propagation")
  class ContextPropagation {

    /**
     * Exercise 3: Access context within VTask.of()
     *
     * <p>When you create a VTask.of() within a scope, the task's virtual thread inherits the scoped
     * values.
     *
     * <p>Task: Create a VTask that reads context directly
     */
    @Test
    @DisplayName("Exercise 3: Read context in VTask")
    void exercise3_readContextInVTask() throws Exception {
      String traceId = "test-trace";

      Try<String> result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId)
              .call(
                  () -> {
                    // TODO: Replace answerRequired() with:
                    // VTask.of(() -> RequestContext.TRACE_ID.get())
                    VTask<String> task = answerRequired();
                    return task.runSafe();
                  });

      assertThat(result.orElse("error")).isEqualTo(traceId);
    }

    /**
     * Exercise 4: Context propagates to parallel tasks
     *
     * <p>When you fork tasks in a Scope, each task's virtual thread inherits the scoped values from
     * the parent.
     *
     * <p>Task: Verify that multiple parallel tasks see the same context
     */
    @Test
    @DisplayName("Exercise 4: Parallel tasks share context")
    void exercise4_parallelTasksShareContext() throws Exception {
      String traceId = "shared-trace";

      List<String> results =
          ScopedValue.where(RequestContext.TRACE_ID, traceId)
              .call(
                  () -> {
                    VTask<String> task1 =
                        VTask.of(
                            () -> {
                              Thread.sleep(10);
                              return "T1:" + RequestContext.TRACE_ID.get();
                            });

                    VTask<String> task2 =
                        VTask.of(
                            () -> {
                              Thread.sleep(10);
                              return "T2:" + RequestContext.TRACE_ID.get();
                            });

                    // TODO: Replace answerRequired() with:
                    // Scope.<String>allSucceed().fork(task1).fork(task2).join()
                    VTask<List<String>> combined = answerRequired();

                    return combined.runSafe().orElse(List.of());
                  });

      assertThat(results).hasSize(2);
      assertThat(results).allMatch(s -> s.contains(traceId));
    }
  }

  // ===========================================================================
  // Part 3: Context with Scope Operations
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Context with Scope")
  class ContextWithScope {

    /**
     * Exercise 5: Use context for logging in parallel tasks
     *
     * <p>Task: Create parallel tasks that include trace ID in their results
     */
    @Test
    @DisplayName("Exercise 5: Context-aware parallel tasks")
    void exercise5_contextAwareParallelTasks() throws Exception {
      String traceId = RequestContext.generateTraceId();
      String tenantId = "acme-corp";

      List<String> results =
          ScopedValue.where(RequestContext.TRACE_ID, traceId)
              .where(RequestContext.TENANT_ID, tenantId)
              .call(
                  () -> {
                    // Each task reads from inherited context
                    VTask<String> fetchUser =
                        VTask.of(
                            () -> {
                              String trace = RequestContext.TRACE_ID.get();
                              Thread.sleep(20);
                              return "User[" + trace.substring(0, 8) + "]";
                            });

                    VTask<String> fetchOrders =
                        VTask.of(
                            () -> {
                              // TODO: Replace answerRequired() with RequestContext.TENANT_ID.get()
                              String tenant = answerRequired();
                              Thread.sleep(20);
                              return "Orders[" + tenant + "]";
                            });

                    VTask<List<String>> scope =
                        Scope.<String>allSucceed().fork(fetchUser).fork(fetchOrders).join();

                    return scope.runSafe().orElse(List.of());
                  });

      assertThat(results).hasSize(2);
      assertThat(results.get(0)).startsWith("User[");
      assertThat(results.get(1)).isEqualTo("Orders[acme-corp]");
    }

    /**
     * Exercise 6: Convert Context result to VTask in pipeline
     *
     * <p>Task: Build a pipeline that starts with Context and flows through VTask
     */
    @Test
    @DisplayName("Exercise 6: Context in VTask pipeline")
    void exercise6_contextInPipeline() throws Exception {
      String traceId = "pipeline-trace";

      Try<String> result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId)
              .call(
                  () -> {
                    // Start with context
                    Context<String, String> getTrace = Context.ask(RequestContext.TRACE_ID);

                    // Convert to VTask and continue the pipeline
                    // TODO: Replace answerRequired() with:
                    // getTrace.toVTask()
                    //   .map(trace -> "Processing: " + trace)
                    //   .flatMap(msg -> VTask.succeed(msg + " [complete]"))
                    VTask<String> pipeline = answerRequired();

                    return pipeline.runSafe();
                  });

      assertThat(result.orElse("error")).isEqualTo("Processing: pipeline-trace [complete]");
    }
  }

  // ===========================================================================
  // Bonus: Complete Concurrent Workflow
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Workflow")
  class CompleteWorkflow {

    /** This test demonstrates a complete workflow combining Context, VTask, and Scope. */
    @Test
    @DisplayName("Complete concurrent workflow with context")
    void completeConcurrentWorkflow() throws Exception {
      String traceId = RequestContext.generateTraceId();
      String tenantId = "shop-xyz";

      record DashboardData(String user, List<String> orders, int notificationCount) {}

      DashboardData dashboard =
          ScopedValue.where(RequestContext.TRACE_ID, traceId)
              .where(RequestContext.TENANT_ID, tenantId)
              .call(
                  () -> {
                    // Three parallel fetches, all sharing context
                    VTask<String> userTask =
                        VTask.of(
                            () -> {
                              String tenant = RequestContext.TENANT_ID.get();
                              Thread.sleep(30);
                              return "User@" + tenant;
                            });

                    VTask<List<String>> ordersTask =
                        VTask.of(
                            () -> {
                              Thread.sleep(30);
                              return List.of("ORD-001", "ORD-002");
                            });

                    VTask<Integer> notificationsTask =
                        VTask.of(
                            () -> {
                              Thread.sleep(30);
                              return 5;
                            });

                    // Run all in parallel using Scope
                    VTask<List<Object>> all =
                        Scope.<Object>allSucceed()
                            .fork(userTask)
                            .fork(ordersTask)
                            .fork(notificationsTask)
                            .join();

                    List<Object> results =
                        all.runSafe()
                            .fold(
                                v -> v,
                                e -> {
                                  throw new RuntimeException(e);
                                });

                    @SuppressWarnings("unchecked")
                    List<String> orders = (List<String>) results.get(1);

                    return new DashboardData(
                        (String) results.get(0), orders, (Integer) results.get(2));
                  });

      assertThat(dashboard.user()).isEqualTo("User@shop-xyz");
      assertThat(dashboard.orders()).containsExactly("ORD-001", "ORD-002");
      assertThat(dashboard.notificationCount()).isEqualTo(5);
    }
  }

  /**
   * Congratulations! You've completed Tutorial 05: Context with VTask
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to convert Context to VTask with toVTask()
   *   <li>✓ How ScopedValue bindings propagate to child virtual threads
   *   <li>✓ How parallel tasks in Scope share the same context
   *   <li>✓ How to build pipelines that flow from Context to VTask
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>toVTask() bridges Context into VTask workflows
   *   <li>Virtual threads automatically inherit scoped values
   *   <li>Parallel tasks in the same scope see the same context
   *   <li>Context is ideal for request tracing in concurrent operations
   * </ul>
   *
   * <p>Next: Tutorial 06 - Advanced Context Patterns
   */
}
