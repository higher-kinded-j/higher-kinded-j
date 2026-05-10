// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.MaybeTAssert.MaybeTOptionalAssert;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link MaybeTAssert}. See {@link AssertContract}. */
@DisplayName("MaybeTAssert contract")
class MaybeTAssertContractTest
    extends AssertContract<
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>,
        MaybeTOptionalAssert<OptionalKind.Witness, Integer>> {

  private static final OptionalMonad MONAD = OptionalMonad.INSTANCE;

  private static <T> Optional<T> unwrap(Kind<OptionalKind.Witness, T> k) {
    return OPTIONAL.narrow(k);
  }

  private static Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> just(int v) {
    return MAYBE_T.widen(MaybeT.just(MONAD, v));
  }

  private static Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> nothing() {
    return MAYBE_T.widen(MaybeT.nothing(MONAD));
  }

  private static Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> empty() {
    Kind<OptionalKind.Witness, Maybe<Integer>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return MAYBE_T.widen(MaybeT.fromKind(emptyOuter));
  }

  private static final Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> J_42 = just(42);
  private static final Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> J_99 = just(99);
  private static final Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> NOTHING = nothing();
  private static final Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> EMPTY = empty();

  @Override
  protected Function<
          Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>,
          MaybeTOptionalAssert<OptionalKind.Witness, Integer>>
      entry() {
    return k -> MaybeTAssert.assertThatMaybeT(k, MaybeTAssertContractTest::unwrap);
  }

  @Override
  protected Stream<
          Row<
              Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>,
              MaybeTOptionalAssert<OptionalKind.Witness, Integer>>>
      rows() {
    return Stream.of(
        passOnly(
            "private constructor accessible via reflection",
            J_42,
            a -> {
              try {
                var c = MaybeTAssert.class.getDeclaredConstructor();
                c.setAccessible(true);
                c.newInstance();
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            }),
        row("isEmpty", EMPTY, J_42, MaybeTOptionalAssert::isEmpty),
        row("isPresent", J_42, EMPTY, MaybeTOptionalAssert::isPresent),
        row("isPresentJust", J_42, NOTHING, MaybeTOptionalAssert::isPresentJust),
        row("isPresentJust wrong outer state", J_42, EMPTY, MaybeTOptionalAssert::isPresentJust),
        row("isPresentNothing", NOTHING, J_42, MaybeTOptionalAssert::isPresentNothing),
        row(
            "isPresentNothing wrong outer state",
            NOTHING,
            EMPTY,
            MaybeTOptionalAssert::isPresentNothing),
        row("hasJustValue match", J_42, J_99, a -> a.hasJustValue(42)),
        row("hasJustValue wrong state", J_42, NOTHING, a -> a.hasJustValue(42)),
        passOnly("satisfiesJust passes", J_42, a -> a.satisfiesJust(v -> {})),
        failOnly(
            "satisfiesJust inner fails",
            J_42,
            a ->
                a.satisfiesJust(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        row("justMatches predicate", J_42, J_99, a -> a.justMatches(v -> v == 42)),
        passOnly("hasNonNullJustValue", J_42, MaybeTOptionalAssert::hasNonNullJustValue),
        row("hasJustValueOfType match", J_42, NOTHING, a -> a.hasJustValueOfType(Integer.class)),
        failOnly("hasJustValueOfType wrong type", J_42, a -> a.hasJustValueOfType(String.class)),
        row("isEqualToMaybeT match", J_42, J_99, a -> a.isEqualToMaybeT(J_42)),
        failOnly("isEqualToMaybeT null other", J_42, a -> a.isEqualToMaybeT(null)));
  }
}
