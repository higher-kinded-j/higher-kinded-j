// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

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

/** Solutions for Tutorial 05: Context with VTask */
@DisplayName("Tutorial 05: Context with VTask - Solutions")
public class Tutorial05_ContextWithVTask_Solution {

  @Nested
  @DisplayName("Part 1: Context to VTask Conversion")
  class ContextToVTask {

    @Test
    @DisplayName("Exercise 1: Basic toVTask conversion")
    void exercise1_basicToVTask() throws Exception {
      String traceId = "trace-12345";

      Context<String, String> getTraceId = Context.ask(RequestContext.TRACE_ID);

      // SOLUTION: Use toVTask() to convert
      VTask<String> traceTask = getTraceId.toVTask();

      Try<String> result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId).call(() -> traceTask.runSafe());

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse("error")).isEqualTo(traceId);
    }

    @Test
    @DisplayName("Exercise 2: Chain then convert")
    void exercise2_chainThenConvert() throws Exception {
      String traceId = "abc-123";

      Context<String, String> formatted =
          Context.ask(RequestContext.TRACE_ID).map(id -> "[trace=" + id + "]");

      // SOLUTION: Convert the chained context to VTask
      VTask<String> task = formatted.toVTask();

      Try<String> result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId).call(() -> task.runSafe());

      assertThat(result.orElse("error")).isEqualTo("[trace=abc-123]");
    }
  }

  @Nested
  @DisplayName("Part 2: Context Propagation")
  class ContextPropagation {

    @Test
    @DisplayName("Exercise 3: Read context in VTask")
    void exercise3_readContextInVTask() throws Exception {
      String traceId = "test-trace";

      Try<String> result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId)
              .call(
                  () -> {
                    // SOLUTION: Use VTask.of() with ScopedValue access
                    VTask<String> task = VTask.of(() -> RequestContext.TRACE_ID.get());
                    return task.runSafe();
                  });

      assertThat(result.orElse("error")).isEqualTo(traceId);
    }

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

                    // SOLUTION: Use Scope.allSucceed() to fork parallel tasks
                    VTask<List<String>> combined =
                        Scope.<String>allSucceed().fork(task1).fork(task2).join();

                    return combined.runSafe().orElse(List.of());
                  });

      assertThat(results).hasSize(2);
      assertThat(results).allMatch(s -> s.contains(traceId));
    }
  }

  @Nested
  @DisplayName("Part 3: Context with Scope")
  class ContextWithScope {

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
                              // SOLUTION: Use RequestContext.TENANT_ID.get()
                              String tenant = RequestContext.TENANT_ID.get();
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

    @Test
    @DisplayName("Exercise 6: Context in VTask pipeline")
    void exercise6_contextInPipeline() throws Exception {
      String traceId = "pipeline-trace";

      Try<String> result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId)
              .call(
                  () -> {
                    Context<String, String> getTrace = Context.ask(RequestContext.TRACE_ID);

                    // SOLUTION: Convert and chain VTask operations
                    VTask<String> pipeline =
                        getTrace
                            .toVTask()
                            .map(trace -> "Processing: " + trace)
                            .flatMap(msg -> VTask.succeed(msg + " [complete]"));

                    return pipeline.runSafe();
                  });

      assertThat(result.orElse("error")).isEqualTo("Processing: pipeline-trace [complete]");
    }
  }
}
