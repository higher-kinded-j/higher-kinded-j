// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.vstream.VStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link VStreamAssert}. See {@link AssertContract}. */
@DisplayName("VStreamAssert contract")
class VStreamAssertContractTest extends AssertContract<VStream<Integer>, VStreamAssert<Integer>> {

  private static final VStream<Integer> S_123 = VStream.of(1, 2, 3);
  private static final VStream<Integer> S_456 = VStream.of(4, 5, 6);
  private static final VStream<Integer> S_12 = VStream.of(1, 2);
  private static final VStream<Integer> EMPTY = VStream.empty();
  private static final VStream<Integer> FAIL_ISE = VStream.fail(new IllegalStateException("ise"));

  @Override
  protected Function<VStream<Integer>, VStreamAssert<Integer>> entry() {
    return VStreamAssert::assertThatVStream;
  }

  @Override
  protected Stream<Row<VStream<Integer>, VStreamAssert<Integer>>> rows() {
    return Stream.of(
        row("producesElements match", S_123, S_456, a -> a.producesElements(1, 2, 3)),
        row(
            "producesElementsInOrder match",
            S_123,
            S_456,
            a -> a.producesElementsInOrder(List.of(1, 2, 3))),
        row("isEmpty", EMPTY, S_123, VStreamAssert::isEmpty),
        row("hasCount match", S_123, S_12, a -> a.hasCount(3)),
        row("firstElement match", S_123, S_456, a -> a.firstElement(1)),
        row("failsOnPull", FAIL_ISE, S_123, VStreamAssert::failsOnPull),
        row(
            "failsWithExceptionType match",
            FAIL_ISE,
            S_123,
            a -> a.failsWithExceptionType(IllegalStateException.class)),
        passOnly(
            "hasNotExecuted on zero counter", S_123, a -> a.hasNotExecuted(new AtomicInteger(0))));
  }

  @Test
  void materialise_is_idempotent_under_chained_assertions() {
    // First call to materialise sets hasMaterialised=true; subsequent calls
    // (via further assertions on the same instance) skip the materialisation
    // block. Covers the `if (!hasMaterialised)` false branch.
    VStreamAssert.assertThatVStream(S_123).producesElements(1, 2, 3).hasCount(3);
  }

  @Test
  void hasNotExecuted_fails_for_nonzero_counter() {
    AtomicInteger nonzero = new AtomicInteger(5);
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VStreamAssert.assertThatVStream(S_123).hasNotExecuted(nonzero));
  }

  @Test
  void firstElement_fails_on_empty_stream() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VStreamAssert.assertThatVStream(EMPTY).firstElement(1));
  }

  @Test
  void hasCount_fails_on_failing_stream() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VStreamAssert.assertThatVStream(FAIL_ISE).hasCount(3));
  }

  @Test
  void firstElement_fails_on_failing_stream() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VStreamAssert.assertThatVStream(FAIL_ISE).firstElement(1));
  }

  @Test
  void isEmpty_fails_on_failing_stream() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VStreamAssert.assertThatVStream(FAIL_ISE).isEmpty());
  }

  @Test
  void producesElements_fails_on_failing_stream() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> VStreamAssert.assertThatVStream(FAIL_ISE).producesElements(1));
  }
}
