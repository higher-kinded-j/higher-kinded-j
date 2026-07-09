// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link VResultPathAssert}. See {@link AssertContract}. */
@DisplayName("VResultPathAssert contract")
class VResultPathAssertContractTest
    extends AssertContract<
        Supplier<VResultPath<String, Integer>>, VResultPathAssert<String, Integer>> {

  // Suppliers ensure each row gets a fresh path; the assertion caches execution state.
  private static final Supplier<VResultPath<String, Integer>> RIGHT = () -> Path.vresultRight(42);
  private static final Supplier<VResultPath<String, Integer>> RIGHT_99 =
      () -> Path.vresultRight(99);
  private static final Supplier<VResultPath<String, Integer>> LEFT = () -> Path.vresultLeft("bad");
  private static final Supplier<VResultPath<String, Integer>> LEFT_OTHER =
      () -> Path.vresultLeft("other");
  private static final Supplier<VResultPath<String, Integer>> DEFECT_ISE =
      () ->
          Path.vresult(
              VTask.of(
                  () -> {
                    throw new IllegalStateException("ise-msg");
                  }));
  private static final Supplier<VResultPath<String, Integer>> DEFECT_IAE =
      () ->
          Path.vresult(
              VTask.of(
                  () -> {
                    throw new IllegalArgumentException("iae-msg");
                  }));
  private static final Supplier<VResultPath<String, Integer>> SLOW =
      () ->
          Path.vresultDefer(
              () -> {
                try {
                  Thread.sleep(20);
                } catch (InterruptedException ignored) {
                  Thread.currentThread().interrupt();
                }
                return Either.right(1);
              });

  @Override
  protected Function<Supplier<VResultPath<String, Integer>>, VResultPathAssert<String, Integer>>
      entry() {
    return s -> VResultPathAssert.assertThatVResultPath(s.get());
  }

  @Override
  protected Stream<Row<Supplier<VResultPath<String, Integer>>, VResultPathAssert<String, Integer>>>
      rows() {
    return Stream.of(
        row("isRight", RIGHT, LEFT, VResultPathAssert::isRight),
        row("isLeft", LEFT, RIGHT, VResultPathAssert::isLeft),
        failOnly("isRight on defect", DEFECT_ISE, VResultPathAssert::isRight),
        failOnly("isLeft on defect", DEFECT_ISE, VResultPathAssert::isLeft),
        // Chained call exercises the cached-execution branch of ensureExecuted.
        row("hasRight match", RIGHT, RIGHT_99, a -> a.isRight().hasRight(42)),
        row("hasLeft match", LEFT, LEFT_OTHER, a -> a.isLeft().hasLeft("bad")),
        failOnly("hasRight on Left", LEFT, a -> a.hasRight(42)),
        failOnly("hasLeft on Right", RIGHT, a -> a.hasLeft("bad")),
        passOnly("hasRightSatisfying passes", RIGHT, a -> a.hasRightSatisfying(v -> {})),
        failOnly(
            "hasRightSatisfying inner throws",
            RIGHT,
            a ->
                a.hasRightSatisfying(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        passOnly("hasLeftSatisfying passes", LEFT, a -> a.hasLeftSatisfying(e -> {})),
        failOnly(
            "hasLeftSatisfying inner throws",
            LEFT,
            a ->
                a.hasLeftSatisfying(
                    e -> {
                      throw new AssertionError("inner");
                    })),
        row("hasDefect", DEFECT_ISE, RIGHT, VResultPathAssert::hasDefect),
        failOnly("hasDefect on Left", LEFT, VResultPathAssert::hasDefect),
        row(
            "withDefectType match",
            DEFECT_ISE,
            DEFECT_IAE,
            a -> a.withDefectType(IllegalStateException.class)),
        row(
            "withDefectMessageContaining match",
            DEFECT_ISE,
            DEFECT_IAE,
            a -> a.withDefectMessageContaining("ise")),
        passOnly(
            "completesWithin generous bound", RIGHT, a -> a.completesWithin(Duration.ofMinutes(1))),
        failOnly(
            "completesWithin tight bound", SLOW, a -> a.completesWithin(Duration.ofMillis(1))));
  }
}
