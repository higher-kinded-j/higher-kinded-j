// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.EitherFAssert.assertThatEitherF;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.EitherFAssert}. */
@DisplayName("EitherFAssert showcase")
class EitherFAssertExample {

  @Test
  @DisplayName("isLeft() asserts the EitherF holds the left functor")
  void leftBranch() {
    Kind<MaybeKind.Witness, String> leftValue = MAYBE.widen(Maybe.just("err"));
    EitherF<MaybeKind.Witness, MaybeKind.Witness, String> ef = EitherF.left(leftValue);

    assertThatEitherF(ef).isLeft();
  }

  @Test
  @DisplayName("isRight() asserts the EitherF holds the right functor")
  void rightBranch() {
    Kind<MaybeKind.Witness, String> rightValue = MAYBE.widen(Maybe.just("ok"));
    EitherF<MaybeKind.Witness, MaybeKind.Witness, String> ef = EitherF.right(rightValue);

    assertThatEitherF(ef).isRight();
  }
}
