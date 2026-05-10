// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.EitherTAssert.EitherTOptionalAssert;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link EitherTAssert}. See {@link AssertContract}. */
@DisplayName("EitherTAssert contract")
class EitherTAssertContractTest
    extends AssertContract<
        Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer>,
        EitherTOptionalAssert<OptionalKind.Witness, String, Integer>> {

  private static final OptionalMonad MONAD = OptionalMonad.INSTANCE;

  private static <T> Optional<T> unwrap(Kind<OptionalKind.Witness, T> k) {
    return OPTIONAL.narrow(k);
  }

  private static Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> right(int v) {
    return EITHER_T.widen(EitherT.right(MONAD, v));
  }

  private static Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> left(String e) {
    return EITHER_T.widen(EitherT.left(MONAD, e));
  }

  private static Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> empty() {
    Kind<OptionalKind.Witness, Either<String, Integer>> emptyOuter =
        OPTIONAL.widen(Optional.empty());
    return EITHER_T.widen(EitherT.fromKind(emptyOuter));
  }

  private static final Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> R_42 =
      right(42);
  private static final Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> R_99 =
      right(99);
  private static final Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> L_ERR =
      left("err");
  private static final Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> L_OTHER =
      left("other");
  private static final Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> EMPTY =
      empty();

  private static Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> rightNull() {
    Kind<OptionalKind.Witness, Either<String, Integer>> outer =
        OPTIONAL.widen(Optional.of(Either.right(null)));
    return EITHER_T.widen(EitherT.fromKind(outer));
  }

  private static final Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> L_NULL =
      left(null);
  private static final Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> R_NULL =
      rightNull();

  @Override
  protected Function<
          Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer>,
          EitherTOptionalAssert<OptionalKind.Witness, String, Integer>>
      entry() {
    return k -> EitherTAssert.assertThatEitherT(k, EitherTAssertContractTest::unwrap);
  }

  @Override
  protected Stream<
          Row<
              Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer>,
              EitherTOptionalAssert<OptionalKind.Witness, String, Integer>>>
      rows() {
    return Stream.of(
        // Synthetic-constructor coverage: invoke the private no-arg via reflection.
        passOnly(
            "private constructor accessible via reflection",
            R_42,
            a -> {
              try {
                var c = EitherTAssert.class.getDeclaredConstructor();
                c.setAccessible(true);
                c.newInstance();
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            }),
        row("isEmpty", EMPTY, R_42, EitherTOptionalAssert::isEmpty),
        row("isPresent", R_42, EMPTY, EitherTOptionalAssert::isPresent),
        row("isPresentRight", R_42, L_ERR, EitherTOptionalAssert::isPresentRight),
        row("isPresentRight wrong outer state", R_42, EMPTY, EitherTOptionalAssert::isPresentRight),
        row("isPresentLeft", L_ERR, R_42, EitherTOptionalAssert::isPresentLeft),
        row("isPresentLeft wrong outer state", L_ERR, EMPTY, EitherTOptionalAssert::isPresentLeft),
        row("hasRightValue match", R_42, R_99, a -> a.hasRightValue(42)),
        row("hasRightValue wrong state", R_42, L_ERR, a -> a.hasRightValue(42)),
        row("hasLeftValue match", L_ERR, L_OTHER, a -> a.hasLeftValue("err")),
        row("hasLeftValue wrong state", L_ERR, R_42, a -> a.hasLeftValue("err")),
        passOnly("satisfiesRight passes", R_42, a -> a.satisfiesRight(v -> {})),
        failOnly(
            "satisfiesRight inner fails",
            R_42,
            a ->
                a.satisfiesRight(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        passOnly("satisfiesLeft passes", L_ERR, a -> a.satisfiesLeft(v -> {})),
        failOnly(
            "satisfiesLeft inner fails",
            L_ERR,
            a ->
                a.satisfiesLeft(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        row("rightMatches predicate", R_42, R_99, a -> a.rightMatches(v -> v == 42)),
        row("leftMatches predicate", L_ERR, L_OTHER, a -> a.leftMatches("err"::equals)),
        passOnly("hasNonNullRightValue", R_42, EitherTOptionalAssert::hasNonNullRightValue),
        passOnly("hasNonNullLeftValue", L_ERR, EitherTOptionalAssert::hasNonNullLeftValue),
        row("hasRightValueOfType match", R_42, L_ERR, a -> a.hasRightValueOfType(Integer.class)),
        failOnly("hasRightValueOfType wrong type", R_42, a -> a.hasRightValueOfType(String.class)),
        // Right(null) → ternary's "null" branch is taken when message is built
        failOnly(
            "hasRightValueOfType null Right", R_NULL, a -> a.hasRightValueOfType(String.class)),
        row("hasLeftValueOfType match", L_ERR, R_42, a -> a.hasLeftValueOfType(String.class)),
        failOnly("hasLeftValueOfType wrong type", L_ERR, a -> a.hasLeftValueOfType(Integer.class)),
        failOnly("hasLeftValueOfType null Left", L_NULL, a -> a.hasLeftValueOfType(Integer.class)),
        row("isEqualToEitherT match", R_42, R_99, a -> a.isEqualToEitherT(R_42)),
        failOnly("isEqualToEitherT null other", R_42, a -> a.isEqualToEitherT(null)));
  }
}
