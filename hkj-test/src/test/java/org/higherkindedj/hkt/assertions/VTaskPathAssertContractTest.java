// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link VTaskPathAssert}. See {@link AssertContract}. */
@DisplayName("VTaskPathAssert contract")
class VTaskPathAssertContractTest
    extends AssertContract<Supplier<VTaskPath<Integer>>, VTaskPathAssert<Integer>> {

  // Suppliers ensure each row gets a fresh path; the assertion caches execution state.
  private static final Supplier<VTaskPath<Integer>> SUCCEED = () -> Path.vtaskPure(42);
  private static final Supplier<VTaskPath<Integer>> SUCCEED_99 = () -> Path.vtaskPure(99);
  private static final Supplier<VTaskPath<Integer>> FAIL_ISE =
      () -> Path.vtaskFail(new IllegalStateException("ise-msg"));
  private static final Supplier<VTaskPath<Integer>> FAIL_IAE =
      () -> Path.vtaskFail(new IllegalArgumentException("iae-msg"));

  @Override
  protected Function<Supplier<VTaskPath<Integer>>, VTaskPathAssert<Integer>> entry() {
    return s -> VTaskPathAssert.assertThatVTaskPath(s.get());
  }

  @Override
  protected Stream<Row<Supplier<VTaskPath<Integer>>, VTaskPathAssert<Integer>>> rows() {
    return Stream.of(
        row("succeeds", SUCCEED, FAIL_ISE, VTaskPathAssert::succeeds),
        row("fails", FAIL_ISE, SUCCEED, VTaskPathAssert::fails),
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
                Path.vtask(
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
  void hasUnderlyingTask_compares_run_result() {
    VTaskPath<Integer> path = Path.vtaskPure(42);
    VTask<Integer> sameTask = path.run();
    VTaskPathAssert.assertThatVTaskPath(path).hasUnderlyingTask(sameTask);
  }

  @Test
  void hasUnderlyingTask_fails_for_unrelated_task() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskPathAssert.assertThatVTaskPath(Path.vtaskPure(42))
                    .hasUnderlyingTask(VTask.succeed(0)));
  }

  @Test
  void isEquivalentTo_passes_for_two_succeeding_paths_with_same_value() {
    VTaskPathAssert.assertThatVTaskPath(Path.vtaskPure(42)).isEquivalentTo(Path.vtaskPure(42));
  }

  @Test
  void isEquivalentTo_passes_for_two_failing_paths() {
    VTaskPathAssert.assertThatVTaskPath(Path.vtaskFail(new RuntimeException("a")))
        .isEquivalentTo(Path.vtaskFail(new RuntimeException("b")));
  }

  @Test
  void isEquivalentTo_fails_when_values_differ() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskPathAssert.assertThatVTaskPath(Path.vtaskPure(42))
                    .isEquivalentTo(Path.vtaskPure(99)));
  }

  @Test
  void isEquivalentTo_fails_when_one_succeeds_and_other_fails() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskPathAssert.assertThatVTaskPath(Path.vtaskPure(42))
                    .isEquivalentTo(Path.vtaskFail(new RuntimeException("b"))));
  }

  @Test
  void isEquivalentTo_fails_when_one_fails_and_other_succeeds() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskPathAssert.assertThatVTaskPath(Path.vtaskFail(new RuntimeException("a")))
                    .isEquivalentTo(Path.vtaskPure(42)));
  }

  @Test
  void hasValue_fails_for_failed_path() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VTaskPathAssert.assertThatVTaskPath(Path.vtaskFail(new RuntimeException("x")))
                    .hasValue(42));
  }
}
