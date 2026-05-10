// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.stream.StreamKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link StreamAssert}. See {@link AssertContract}. */
@DisplayName("StreamAssert contract")
class StreamAssertContractTest
    extends AssertContract<Supplier<Kind<StreamKind.Witness, Integer>>, StreamAssert<Integer>> {

  // Streams are single-use; suppliers give each row a fresh stream.
  private static final Supplier<Kind<StreamKind.Witness, Integer>> EMPTY =
      () -> STREAM.widen(Stream.empty());
  private static final Supplier<Kind<StreamKind.Witness, Integer>> ONE =
      () -> STREAM.widen(Stream.of(1));
  private static final Supplier<Kind<StreamKind.Witness, Integer>> ONE_TWO_THREE =
      () -> STREAM.widen(Stream.of(1, 2, 3));
  private static final Supplier<Kind<StreamKind.Witness, Integer>> ONE_TWO_FOUR =
      () -> STREAM.widen(Stream.of(1, 2, 4));
  private static final Supplier<Kind<StreamKind.Witness, Integer>> NINE_EIGHT_SEVEN =
      () -> STREAM.widen(Stream.of(9, 8, 7));

  @Override
  protected Function<Supplier<Kind<StreamKind.Witness, Integer>>, StreamAssert<Integer>> entry() {
    return s -> StreamAssert.assertThatStream(s.get());
  }

  @Override
  protected Stream<Row<Supplier<Kind<StreamKind.Witness, Integer>>, StreamAssert<Integer>>> rows() {
    return Stream.of(
        row("isEmpty", EMPTY, ONE, StreamAssert::isEmpty),
        row("isNotEmpty", ONE, EMPTY, StreamAssert::isNotEmpty),
        row("hasSize", ONE_TWO_THREE, ONE, a -> a.hasSize(3)),
        row("containsExactly", ONE_TWO_THREE, ONE_TWO_FOUR, a -> a.containsExactly(1, 2, 3)),
        row("contains varargs", ONE_TWO_THREE, EMPTY, a -> a.contains(1, 3)),
        row("doesNotContain", ONE, ONE_TWO_THREE, a -> a.doesNotContain(2)),
        row(
            "allMatch",
            () -> STREAM.widen(Stream.of(2, 4)),
            ONE_TWO_THREE,
            a -> a.allMatch(n -> n % 2 == 0, "even")),
        row("anyMatch", ONE_TWO_THREE, ONE, a -> a.anyMatch(n -> n > 2, "at least one > 2")),
        row(
            "noneMatch",
            () -> STREAM.widen(Stream.of(1, 3, 5)),
            ONE_TWO_THREE,
            a -> a.noneMatch(n -> n % 2 == 0, "no evens")),
        passOnly("satisfies passes", ONE_TWO_THREE, a -> a.satisfies(list -> {})),
        row("startsWith", ONE_TWO_THREE, NINE_EIGHT_SEVEN, a -> a.startsWith(1, 2)),
        failOnly("startsWith too short", ONE, a -> a.startsWith(1, 2, 3)));
  }

  @Test
  void satisfies_propagates_inner_assertion_failure() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                StreamAssert.assertThatStream(STREAM.widen(Stream.of(1)))
                    .satisfies(
                        list -> {
                          throw new AssertionError("inner");
                        }));
  }

  @Test
  void satisfies_rejects_null_consumer() {
    Consumer<List<Integer>> nullConsumer = null;
    Assertions.assertThatNullPointerException()
        .isThrownBy(
            () ->
                StreamAssert.assertThatStream(STREAM.widen(Stream.of(1))).satisfies(nullConsumer));
  }

  @Test
  void evaluatedList_is_cached_across_chained_calls() {
    // Force evaluation once via hasSize, then re-use via isNotEmpty: both must succeed
    // without re-collecting the (already exhausted) stream.
    StreamAssert.assertThatStream(STREAM.widen(Stream.of(1, 2, 3))).hasSize(3).isNotEmpty();
  }
}
