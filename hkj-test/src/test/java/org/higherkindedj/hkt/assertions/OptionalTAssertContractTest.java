// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.OptionalTAssert.OptionalTOptionalAssert;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.optional_t.OptionalTKind;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link OptionalTAssert}. See {@link AssertContract}. */
@DisplayName("OptionalTAssert contract")
class OptionalTAssertContractTest
    extends AssertContract<
        Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>,
        OptionalTOptionalAssert<OptionalKind.Witness, Integer>> {

  private static final OptionalMonad MONAD = OptionalMonad.INSTANCE;

  private static <T> Optional<T> unwrap(Kind<OptionalKind.Witness, T> k) {
    return OPTIONAL.narrow(k);
  }

  private static Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> some(int v) {
    return OPTIONAL_T.widen(OptionalT.some(MONAD, v));
  }

  private static Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> none() {
    return OPTIONAL_T.widen(OptionalT.none(MONAD));
  }

  private static Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> empty() {
    Kind<OptionalKind.Witness, Optional<Integer>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return OPTIONAL_T.widen(OptionalT.fromKind(emptyOuter));
  }

  private static final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> S_42 = some(42);
  private static final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> S_99 = some(99);
  private static final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> NONE = none();
  private static final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> EMPTY = empty();

  @Override
  protected Function<
          Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>,
          OptionalTOptionalAssert<OptionalKind.Witness, Integer>>
      entry() {
    return k -> OptionalTAssert.assertThatOptionalT(k, OptionalTAssertContractTest::unwrap);
  }

  @Override
  protected Stream<
          Row<
              Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>,
              OptionalTOptionalAssert<OptionalKind.Witness, Integer>>>
      rows() {
    return Stream.of(
        passOnly(
            "private constructor accessible via reflection",
            S_42,
            a -> {
              try {
                var c = OptionalTAssert.class.getDeclaredConstructor();
                c.setAccessible(true);
                c.newInstance();
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            }),
        row("isEmpty", EMPTY, S_42, OptionalTOptionalAssert::isEmpty),
        row("isPresent", S_42, EMPTY, OptionalTOptionalAssert::isPresent),
        row("isPresentSome", S_42, NONE, OptionalTOptionalAssert::isPresentSome),
        row("isPresentSome wrong outer state", S_42, EMPTY, OptionalTOptionalAssert::isPresentSome),
        row("isPresentNone", NONE, S_42, OptionalTOptionalAssert::isPresentNone),
        row("isPresentNone wrong outer state", NONE, EMPTY, OptionalTOptionalAssert::isPresentNone),
        row("hasSomeValue match", S_42, S_99, a -> a.hasSomeValue(42)),
        row("hasSomeValue wrong state", S_42, NONE, a -> a.hasSomeValue(42)),
        passOnly("satisfiesSome passes", S_42, a -> a.satisfiesSome(v -> {})),
        failOnly(
            "satisfiesSome inner fails",
            S_42,
            a ->
                a.satisfiesSome(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        row("someMatches predicate", S_42, S_99, a -> a.someMatches(v -> v == 42)),
        passOnly("hasNonNullSomeValue", S_42, OptionalTOptionalAssert::hasNonNullSomeValue),
        row("hasSomeValueOfType match", S_42, NONE, a -> a.hasSomeValueOfType(Integer.class)),
        failOnly("hasSomeValueOfType wrong type", S_42, a -> a.hasSomeValueOfType(String.class)),
        row("isEqualToOptionalT match", S_42, S_99, a -> a.isEqualToOptionalT(S_42)),
        failOnly("isEqualToOptionalT null other", S_42, a -> a.isEqualToOptionalT(null)));
  }
}
