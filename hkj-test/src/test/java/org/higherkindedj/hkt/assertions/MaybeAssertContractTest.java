// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link MaybeAssert}. See {@link AssertContract}. */
@DisplayName("MaybeAssert contract")
class MaybeAssertContractTest extends AssertContract<Maybe<Integer>, MaybeAssert<Integer>> {

  private static final Maybe<Integer> JUST_42 = Maybe.just(42);
  private static final Maybe<Integer> JUST_99 = Maybe.just(99);
  private static final Maybe<Integer> NOTHING = Maybe.nothing();

  @Override
  protected Function<Maybe<Integer>, MaybeAssert<Integer>> entry() {
    return MaybeAssert::assertThatMaybe;
  }

  @Override
  protected Stream<Row<Maybe<Integer>, MaybeAssert<Integer>>> rows() {
    return Stream.of(
        row("isJust", JUST_42, NOTHING, MaybeAssert::isJust),
        row("isNothing", NOTHING, JUST_42, MaybeAssert::isNothing),
        row("hasValue match", JUST_42, JUST_99, a -> a.hasValue(42)),
        row("hasValue wrong state", JUST_42, NOTHING, a -> a.hasValue(42)),
        row("hasValueSatisfying wrong state", JUST_42, NOTHING, a -> a.hasValueSatisfying(v -> {})),
        failOnly(
            "hasValueSatisfying inner fails",
            JUST_42,
            a ->
                a.hasValueSatisfying(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        row("hasValueNonNull", JUST_42, NOTHING, MaybeAssert::hasValueNonNull));
  }
}
