// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.context.VTaskContext;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link VTaskContextAssert}. See {@link AssertContract}. */
@DisplayName("VTaskContextAssert contract")
class VTaskContextAssertContractTest
    extends AssertContract<Supplier<VTaskContext<Integer>>, VTaskContextAssert<Integer>> {

  private static final Supplier<VTaskContext<Integer>> SUCCEED = () -> VTaskContext.pure(42);
  private static final Supplier<VTaskContext<Integer>> SUCCEED_99 = () -> VTaskContext.pure(99);
  private static final Supplier<VTaskContext<Integer>> FAIL_ISE =
      () -> VTaskContext.fail(new IllegalStateException("ise-msg"));
  private static final Supplier<VTaskContext<Integer>> FAIL_IAE =
      () -> VTaskContext.fail(new IllegalArgumentException("iae-msg"));

  @Override
  protected Function<Supplier<VTaskContext<Integer>>, VTaskContextAssert<Integer>> entry() {
    return s -> VTaskContextAssert.assertThatVTaskContext(s.get());
  }

  @Override
  protected Stream<Row<Supplier<VTaskContext<Integer>>, VTaskContextAssert<Integer>>> rows() {
    return Stream.of(
        row("succeeds", SUCCEED, FAIL_ISE, VTaskContextAssert::succeeds),
        row("fails", FAIL_ISE, SUCCEED, VTaskContextAssert::fails),
        row("hasValue match", SUCCEED, SUCCEED_99, a -> a.hasValue(42)),
        passOnly("hasValueSatisfying passes", SUCCEED, a -> a.hasValueSatisfying(v -> {})),
        failOnly(
            "hasValueSatisfying inner throws",
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
            "withExceptionMessage match",
            FAIL_ISE,
            FAIL_IAE,
            a -> a.withExceptionMessage("ise-msg")),
        row(
            "withExceptionMessageContaining match",
            FAIL_ISE,
            FAIL_IAE,
            a -> a.withExceptionMessageContaining("ise")),
        passOnly(
            "completesWithin generous bound",
            SUCCEED,
            a -> a.completesWithin(Duration.ofMinutes(1))),
        failOnly(
            "completesWithin tight bound",
            () ->
                VTaskContext.of(
                    () -> {
                      try {
                        Thread.sleep(20);
                      } catch (InterruptedException ignored) {
                      }
                      return 1;
                    }),
            a -> a.completesWithin(Duration.ofMillis(1))));
  }

  @Test
  void hasUnderlying_compares_run_result() {
    VTaskContext<Integer> ctx = VTaskContext.pure(42);
    VTask<Integer> sameTask = ctx.toVTask();
    VTaskContextAssert.assertThatVTaskContext(ctx).hasUnderlying(sameTask);
  }

  @Test
  void hasUnderlying_fails_for_unrelated_task() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskContextAssert.assertThatVTaskContext(VTaskContext.pure(42))
                    .hasUnderlying(VTask.succeed(0)));
  }

  @Test
  void hasUnderlyingPath_compares_path() {
    VTaskContext<Integer> ctx = VTaskContext.pure(42);
    VTaskContextAssert.assertThatVTaskContext(ctx).hasUnderlyingPath(ctx.toPath());
  }

  @Test
  void hasUnderlyingPath_fails_for_unrelated_path() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskContextAssert.assertThatVTaskContext(VTaskContext.pure(42))
                    .hasUnderlyingPath(Path.vtaskPure(0)));
  }

  @Test
  void isEquivalentTo_passes_for_matching_success() {
    VTaskContextAssert.assertThatVTaskContext(VTaskContext.pure(42))
        .isEquivalentTo(VTaskContext.pure(42));
  }

  @Test
  void isEquivalentTo_passes_for_two_failures() {
    VTaskContextAssert.assertThatVTaskContext(VTaskContext.fail(new RuntimeException("a")))
        .isEquivalentTo(VTaskContext.fail(new RuntimeException("b")));
  }

  @Test
  void isEquivalentTo_fails_when_values_differ() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskContextAssert.assertThatVTaskContext(VTaskContext.pure(42))
                    .isEquivalentTo(VTaskContext.pure(99)));
  }

  @Test
  void isEquivalentTo_fails_when_one_succeeds_and_other_fails() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskContextAssert.assertThatVTaskContext(VTaskContext.pure(42))
                    .isEquivalentTo(VTaskContext.fail(new RuntimeException("b"))));
  }

  @Test
  void isEquivalentTo_fails_when_one_fails_and_other_succeeds() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskContextAssert.assertThatVTaskContext(
                        VTaskContext.fail(new RuntimeException("a")))
                    .isEquivalentTo(VTaskContext.pure(42)));
  }

  @Test
  void hasValue_fails_for_failed_context() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskContextAssert.assertThatVTaskContext(
                        VTaskContext.fail(new RuntimeException("x")))
                    .hasValue(42));
  }
}
