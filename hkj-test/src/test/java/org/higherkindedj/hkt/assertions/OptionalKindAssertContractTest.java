// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link OptionalKindAssert}. See {@link AssertContract}. */
@DisplayName("OptionalKindAssert contract")
class OptionalKindAssertContractTest
    extends AssertContract<Kind<OptionalKind.Witness, Integer>, OptionalKindAssert<Integer>> {

  private static final Kind<OptionalKind.Witness, Integer> PRESENT =
      OPTIONAL.widen(Optional.of(42));
  private static final Kind<OptionalKind.Witness, Integer> EMPTY = OPTIONAL.widen(Optional.empty());

  @Override
  protected Function<Kind<OptionalKind.Witness, Integer>, OptionalKindAssert<Integer>> entry() {
    return OptionalKindAssert::assertThatOptionalKind;
  }

  @Override
  protected Stream<Row<Kind<OptionalKind.Witness, Integer>, OptionalKindAssert<Integer>>> rows() {
    return Stream.of(
        row("isPresent", PRESENT, EMPTY, OptionalKindAssert::isPresent),
        row("isEmpty", EMPTY, PRESENT, OptionalKindAssert::isEmpty),
        row("contains match", PRESENT, EMPTY, a -> a.contains(42)),
        row("contains wrong value", PRESENT, OPTIONAL.widen(Optional.of(7)), a -> a.contains(42)),
        passOnly("satisfies passes", PRESENT, a -> a.satisfies(v -> {})),
        failOnly(
            "satisfies inner throws",
            PRESENT,
            a ->
                a.satisfies(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        row(
            "valueMatches",
            PRESENT,
            OPTIONAL.widen(Optional.of(0)),
            a -> a.valueMatches(n -> n > 0)),
        row(
            "containsNonNull",
            PRESENT,
            OPTIONAL.widen(Optional.ofNullable(null)),
            OptionalKindAssert::containsNonNull),
        row("containsInstanceOf match", PRESENT, EMPTY, a -> a.containsInstanceOf(Integer.class)),
        failOnly("containsInstanceOf wrong type", PRESENT, a -> a.containsInstanceOf(String.class)),
        row("hasPresentValue true", PRESENT, EMPTY, a -> a.hasPresentValue(true)),
        row("hasPresentValue false", EMPTY, PRESENT, a -> a.hasPresentValue(false)));
  }
}
