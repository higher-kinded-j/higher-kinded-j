// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link VStreamPathAssert}. See {@link AssertContract}. */
@DisplayName("VStreamPathAssert contract")
class VStreamPathAssertContractTest
    extends AssertContract<Supplier<VStreamPath<Integer>>, VStreamPathAssert<Integer>> {

  private static final Supplier<VStreamPath<Integer>> ONE_TWO_THREE = () -> Path.vstreamOf(1, 2, 3);
  private static final Supplier<VStreamPath<Integer>> ONE_TWO_FOUR = () -> Path.vstreamOf(1, 2, 4);
  private static final Supplier<VStreamPath<Integer>> EMPTY = Path::vstreamEmpty;
  private static final Supplier<VStreamPath<Integer>> FAILS =
      () -> Path.vstream(VStream.fail(new IllegalStateException("boom")));

  @Override
  protected Function<Supplier<VStreamPath<Integer>>, VStreamPathAssert<Integer>> entry() {
    return s -> VStreamPathAssert.assertThatVStreamPath(s.get());
  }

  @Override
  protected Stream<Row<Supplier<VStreamPath<Integer>>, VStreamPathAssert<Integer>>> rows() {
    return Stream.of(
        row(
            "producesElements match",
            ONE_TWO_THREE,
            ONE_TWO_FOUR,
            a -> a.producesElements(1, 2, 3)),
        row(
            "producesElementsInOrder match",
            ONE_TWO_THREE,
            ONE_TWO_FOUR,
            a -> a.producesElementsInOrder(List.of(1, 2, 3))),
        row("isEmpty", EMPTY, ONE_TWO_THREE, VStreamPathAssert::isEmpty),
        row("hasCount match", ONE_TWO_THREE, EMPTY, a -> a.hasCount(3)),
        passOnly("satisfies passes", ONE_TWO_THREE, a -> a.satisfies(list -> {})),
        passOnly("failsOnMaterialise", FAILS, VStreamPathAssert::failsOnMaterialise),
        failOnly(
            "failsOnMaterialise on success", ONE_TWO_THREE, VStreamPathAssert::failsOnMaterialise));
  }

  @Test
  void producesElements_fails_when_materialise_throws() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () -> VStreamPathAssert.assertThatVStreamPath(FAILS.get()).producesElements(1, 2, 3));
  }

  @Test
  void producesElementsInOrder_fails_when_materialise_throws() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VStreamPathAssert.assertThatVStreamPath(FAILS.get())
                    .producesElementsInOrder(List.of(1)));
  }

  @Test
  void isEmpty_fails_when_materialise_throws() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VStreamPathAssert.assertThatVStreamPath(FAILS.get()).isEmpty());
  }

  @Test
  void hasCount_fails_when_materialise_throws() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VStreamPathAssert.assertThatVStreamPath(FAILS.get()).hasCount(0));
  }

  @Test
  void satisfies_fails_when_materialise_throws() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () -> VStreamPathAssert.assertThatVStreamPath(FAILS.get()).satisfies(list -> {}));
  }

  @Test
  void satisfies_propagates_inner_assertion_failure() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                VStreamPathAssert.assertThatVStreamPath(ONE_TWO_THREE.get())
                    .satisfies(
                        list -> {
                          throw new AssertionError("inner");
                        }));
  }
}
