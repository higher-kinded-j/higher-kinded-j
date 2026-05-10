// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link EitherFAssert}. See {@link AssertContract}. */
@DisplayName("EitherFAssert contract")
class EitherFAssertContractTest
    extends AssertContract<
        EitherF<MaybeKind.Witness, MaybeKind.Witness, String>,
        EitherFAssert<MaybeKind.Witness, MaybeKind.Witness, String>> {

  private static final Kind<MaybeKind.Witness, String> JUST_OK = MAYBE.widen(Maybe.just("ok"));
  private static final Kind<MaybeKind.Witness, String> JUST_ERR = MAYBE.widen(Maybe.just("err"));

  private static final EitherF<MaybeKind.Witness, MaybeKind.Witness, String> LEFT =
      EitherF.left(JUST_ERR);
  private static final EitherF<MaybeKind.Witness, MaybeKind.Witness, String> RIGHT =
      EitherF.right(JUST_OK);

  @Override
  protected Function<
          EitherF<MaybeKind.Witness, MaybeKind.Witness, String>,
          EitherFAssert<MaybeKind.Witness, MaybeKind.Witness, String>>
      entry() {
    return EitherFAssert::assertThatEitherF;
  }

  @Override
  protected Stream<
          Row<
              EitherF<MaybeKind.Witness, MaybeKind.Witness, String>,
              EitherFAssert<MaybeKind.Witness, MaybeKind.Witness, String>>>
      rows() {
    return Stream.of(
        row("isLeft", LEFT, RIGHT, EitherFAssert::isLeft),
        row("isRight", RIGHT, LEFT, EitherFAssert::isRight),
        passOnly("hasLeftSatisfying passes", LEFT, a -> a.hasLeftSatisfying(k -> {})),
        failOnly(
            "hasLeftSatisfying inner fails",
            LEFT,
            a ->
                a.hasLeftSatisfying(
                    k -> {
                      throw new AssertionError("inner");
                    })),
        failOnly("hasLeftSatisfying wrong state", RIGHT, a -> a.hasLeftSatisfying(k -> {})),
        passOnly("hasRightSatisfying passes", RIGHT, a -> a.hasRightSatisfying(k -> {})),
        failOnly(
            "hasRightSatisfying inner fails",
            RIGHT,
            a ->
                a.hasRightSatisfying(
                    k -> {
                      throw new AssertionError("inner");
                    })),
        failOnly("hasRightSatisfying wrong state", LEFT, a -> a.hasRightSatisfying(k -> {})));
  }

  @Test
  void hasLeftSatisfying_null_requirements_throws_npe() {
    Assertions.assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> EitherFAssert.assertThatEitherF(LEFT).hasLeftSatisfying(null));
  }

  @Test
  void hasRightSatisfying_null_requirements_throws_npe() {
    Assertions.assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> EitherFAssert.assertThatEitherF(RIGHT).hasRightSatisfying(null));
  }
}
