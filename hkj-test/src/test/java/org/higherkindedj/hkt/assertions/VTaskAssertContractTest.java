// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link VTaskAssert}. See {@link AssertContract}. */
@DisplayName("VTaskAssert contract")
class VTaskAssertContractTest
    extends AssertContract<Supplier<VTask<Integer>>, VTaskAssert<Integer>> {

  // Each subject is a Supplier<VTask<Integer>> so each test gets a fresh VTask
  // (executing one VTask captures state in the assert and would taint subsequent rows).
  private static final Supplier<VTask<Integer>> SUCCEED = () -> VTask.succeed(42);
  private static final Supplier<VTask<Integer>> SUCCEED_99 = () -> VTask.succeed(99);
  private static final Supplier<VTask<Integer>> SUCCEED_NULL = () -> VTask.succeed(null);
  private static final Supplier<VTask<Integer>> FAIL_ISE =
      () -> VTask.fail(new IllegalStateException("ise-msg"));
  private static final Supplier<VTask<Integer>> FAIL_IAE =
      () -> VTask.fail(new IllegalArgumentException("iae-msg"));

  @Override
  protected Function<Supplier<VTask<Integer>>, VTaskAssert<Integer>> entry() {
    return s -> VTaskAssert.assertThatVTask(s.get());
  }

  @Override
  protected Stream<Row<Supplier<VTask<Integer>>, VTaskAssert<Integer>>> rows() {
    return Stream.of(
        row("succeeds", SUCCEED, FAIL_ISE, VTaskAssert::succeeds),
        row("fails", FAIL_ISE, SUCCEED, VTaskAssert::fails),
        row("hasValue match", SUCCEED, SUCCEED_99, a -> a.hasValue(42)),
        row("hasValue wrong state", SUCCEED, FAIL_ISE, a -> a.hasValue(42)),
        row("hasNullValue", SUCCEED_NULL, SUCCEED, VTaskAssert::hasNullValue),
        passOnly("hasValueSatisfying passes", SUCCEED, a -> a.hasValueSatisfying(v -> {})),
        failOnly(
            "hasValueSatisfying inner fails",
            SUCCEED,
            a ->
                a.hasValueSatisfying(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        row(
            "withExceptionType match",
            FAIL_ISE,
            FAIL_IAE,
            a -> a.withExceptionType(IllegalStateException.class)),
        row(
            "withExceptionType wrong state",
            FAIL_ISE,
            SUCCEED,
            a -> a.withExceptionType(IllegalStateException.class)),
        row("withMessage match", FAIL_ISE, FAIL_IAE, a -> a.fails().withMessage("ise-msg")),
        failOnly("withMessage missing exception", SUCCEED, a -> a.withMessage("anything")),
        row(
            "withMessageContaining match",
            FAIL_ISE,
            FAIL_IAE,
            a -> a.fails().withMessageContaining("ise")),
        failOnly(
            "withMessageContaining missing exception",
            SUCCEED,
            a -> a.withMessageContaining("anything")),
        passOnly(
            "completesWithin generous bound",
            SUCCEED,
            a -> a.completesWithin(Duration.ofMinutes(1))),
        failOnly(
            "completesWithin tight bound",
            () ->
                VTask.delay(
                    () -> {
                      try {
                        Thread.sleep(20);
                      } catch (InterruptedException ignored) {
                      }
                      return 1;
                    }),
            a -> a.completesWithin(Duration.ofMillis(1))),
        // VTask.succeed runs synchronously on the test thread (not virtual), so
        // runsOnVirtualThread fails. Pass-side covered by VTask.blocking() in dedicated test below.
        failOnly(
            "runsOnVirtualThread on platform thread", SUCCEED, VTaskAssert::runsOnVirtualThread),
        passOnly("runSafeSucceeds", SUCCEED, VTaskAssert::runSafeSucceeds),
        passOnly("runSafeFails", FAIL_ISE, VTaskAssert::runSafeFails));
  }

  @Test
  void runsOnVirtualThread_passes_when_assert_invoked_on_virtual_thread() throws Exception {
    // The assertion checks the thread that ran the peek callback, which is the
    // thread that executed VTask.run(). Run the assertion itself on a virtual
    // thread so that wasExecutedOnVirtualThread is true.
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread t =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    VTaskAssert.assertThatVTask(SUCCEED.get()).runsOnVirtualThread();
                  } catch (Throwable th) {
                    failure.set(th);
                  }
                });
    t.join();
    if (failure.get() != null) throw new RuntimeException(failure.get());
  }

  @Test
  void runSafeSucceeds_fails_for_failed_task() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VTaskAssert.assertThatVTask(FAIL_ISE.get()).runSafeSucceeds());
  }

  @Test
  void runSafeFails_fails_for_succeeding_task() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VTaskAssert.assertThatVTask(SUCCEED.get()).runSafeFails());
  }

  @Test
  void runSafe_caches_result_across_chained_calls() {
    // Chaining runSafeSucceeds() twice on the same assert must not re-execute
    // the underlying VTask: the second call hits the runSafeOnce cache.
    java.util.concurrent.atomic.AtomicInteger runs =
        new java.util.concurrent.atomic.AtomicInteger();
    VTask<Integer> counted = VTask.delay(runs::incrementAndGet);
    VTaskAssert.assertThatVTask(counted).runSafeSucceeds().runSafeSucceeds();
    Assertions.assertThat(runs.get()).isEqualTo(1);
  }

  @Test
  void getException_and_getValue_after_run() {
    VTaskAssert<Integer> ok = VTaskAssert.assertThatVTask(SUCCEED.get());
    Assertions.assertThat(ok.getValue()).isEqualTo(42);
    Assertions.assertThat(ok.getException()).isNull();

    VTaskAssert<Integer> bad = VTaskAssert.assertThatVTask(FAIL_ISE.get());
    Assertions.assertThat(bad.getException()).isInstanceOf(IllegalStateException.class);
    Assertions.assertThat(bad.getValue()).isNull();
  }

  @Test
  void whenRun_unwraps_VTaskExecutionException_to_original_checked_cause() {
    // VTask.of accepts a Callable, which can throw checked exceptions. The default run()
    // wraps the checked exception in a VTaskExecutionException; the assertion's whenRun
    // catches that and exposes the original cause.
    IOException original = new IOException("checked-fail");
    VTask<Integer> task =
        VTask.of(
            () -> {
              throw original;
            });
    VTaskAssert<Integer> a = VTaskAssert.assertThatVTask(task);
    Assertions.assertThat(a.getException()).isSameAs(original);
  }

  @Test
  void getExecutionTimeMillis_returns_minus_one_before_run() {
    VTaskAssert<Integer> a = VTaskAssert.assertThatVTask(SUCCEED.get());
    Assertions.assertThat(a.getExecutionTimeMillis()).isEqualTo(-1);
    a.whenRun();
    Assertions.assertThat(a.getExecutionTimeMillis()).isGreaterThanOrEqualTo(0);
  }
}
