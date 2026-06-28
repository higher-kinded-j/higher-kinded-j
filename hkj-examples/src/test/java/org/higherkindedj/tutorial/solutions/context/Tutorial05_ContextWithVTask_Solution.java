// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

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
 * Solution for Tutorial05 ContextWithVTask — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial 05: Context with VTask - Solutions")
public class Tutorial05_ContextWithVTask_Solution {

  @Nested
  @DisplayName("Part 1: Context to VTask Conversion")
  class ContextToVTask {

    /**
     * Why this is idiomatic: {@code context.toVTask()} lifts a {@code Context} into a {@code VTask}
     * so structured-concurrency operators apply. The scoped value is read inside the VTask when it
     * runs.
     *
     * <p>Alternative: read the value with {@code .run()} and wrap manually with {@code
     * VTask.succeed}. Same answer; the lift is the named, composable form.
     *
     * <p>Common wrong attempt: convert outside the binding scope. The VTask still needs the scope
     * when it runs; bind the scope around the call to {@code runSafe}.
     */
    @Test
    @DisplayName("Exercise 1: Basic toVTask conversion")
    void exercise1_basicToVTask() throws Exception {
      String traceId = "trace-12345";

      Context<String, String> getTraceId = Context.ask(RequestContext.TRACE_ID);

      // SOLUTION: Use toVTask() to convert
      VTask<String> traceTask = getTraceId.toVTask();

      Try<String> result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId).call(() -> traceTask.runSafe());

      assertThatTry(result).isSuccess();
      assertThat(result.orElse("error")).isEqualTo(traceId);
    }

    /**
     * Why this is idiomatic: build the chain at the {@code Context} level, then convert once. The
     * {@code Context} composition is pure; the {@code VTask} adds the concurrency machinery.
     *
     * <p>Alternative: convert first, then chain at the {@code VTask} level. Equivalent for
     * read-only chains; the {@code Context} side is preferred when both sides support the
     * operation.
     *
     * <p>Common wrong attempt: convert and chain repeatedly. Each conversion adds overhead; convert
     * at the boundary, not in the middle.
     */
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

    /**
     * Why this is idiomatic: {@code VTask.of(supplier)} wraps a side-effecting body that reads
     * {@code ScopedValue} bindings. The VTask captures the binding lookup inside its supplier; the
     * value is read when the task runs.
     *
     * <p>Alternative: pass the value into the {@code VTask.of} closure manually. Loses the
     * scoped-value propagation that makes the pattern useful in the first place.
     *
     * <p>Common wrong attempt: read the scoped value before calling {@code VTask.of}. The supplier
     * captures the value at creation time and the task may run on a different thread without the
     * binding.
     */
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

    /**
     * Why this is idiomatic: {@code Scope.allSucceed().fork(...).fork(...).join()} runs the two
     * tasks concurrently. Both share the parent's scoped values — the trace id propagates into each
     * forked virtual thread.
     *
     * <p>Alternative: spawn raw {@code Thread}s and replicate the bindings by hand. Possible but
     * error-prone; structured concurrency does the propagation for you.
     *
     * <p>Common wrong attempt: assume the scoped value is lost across forks. Modern structured
     * concurrency inherits scoped values on fork; that is the entire point.
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

    /**
     * Why this is idiomatic: bind multiple scoped values (trace id and tenant id) and fork tasks
     * that read different ones. Each task picks up only the bindings it needs; the runtime
     * propagates everything.
     *
     * <p>Alternative: pass each scoped value explicitly into the task closures. Works but
     * undermines the propagation guarantee that scoped values exist to provide.
     *
     * <p>Common wrong attempt: assume tasks see each other's local variables. Each task is a
     * separate scope of execution; share via scoped values, not via captured locals that may race.
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

    /**
     * Why this is idiomatic: {@code Context.toVTask().map(...).flatMap(...)} composes the VTask
     * pipeline directly from a {@code Context}. The scoped value is read at pipeline run time
     * inside the binding scope.
     *
     * <p>Alternative: read the value first and build a pipeline of {@code VTask.succeed} stages.
     * Equivalent for static reads; the toVTask form scales when later stages may also read scoped
     * values.
     *
     * <p>Common wrong attempt: forget the binding scope around {@code runSafe}. The VTask still
     * needs the bindings when it executes; bind once at the boundary.
     */
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
