// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiPredicate;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.either.EitherKind2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link BifunctorLaws} helpers against a lawful bifunctor and a violating equality.
 */
@DisplayName("BifunctorLaws")
class BifunctorLawsTest {

  private final Bifunctor<EitherKind2.Witness> bifunctor = EitherBifunctor.INSTANCE;
  private final Kind2<EitherKind2.Witness, String, Integer> fab = EITHER.widen2(Either.right(42));
  // A Left inhabitant so the first-parameter (left) mapper is actually exercised — on a Right,
  // Either's bimap only runs the second mapper, leaving the first-side law paths uncovered.
  private final Kind2<EitherKind2.Witness, String, Integer> fabLeft =
      EITHER.widen2(Either.left("err"));
  private final BiPredicate<Kind2<EitherKind2.Witness, ?, ?>, Kind2<EitherKind2.Witness, ?, ?>> eq =
      (k1, k2) -> EITHER.narrow2(k1).equals(EITHER.narrow2(k2));

  @Test
  @DisplayName("assertIdentity passes for a lawful bifunctor (both inhabitants)")
  void identityPasses() {
    BifunctorLaws.assertIdentity(bifunctor, fab, eq);
    BifunctorLaws.assertIdentity(bifunctor, fabLeft, eq);
  }

  @Test
  @DisplayName("assertComposition passes for a lawful bifunctor")
  void compositionPasses() {
    BifunctorLaws.assertComposition(
        bifunctor, fab, String::length, i -> "#" + i, n -> "Value:" + n, s -> s + "!", eq);
  }

  @Test
  @DisplayName("assertFirstConsistency passes for a lawful bifunctor")
  void firstConsistencyPasses() {
    BifunctorLaws.assertFirstConsistency(bifunctor, fab, String::length, eq);
  }

  @Test
  @DisplayName("assertSecondConsistency passes for a lawful bifunctor (both inhabitants)")
  void secondConsistencyPasses() {
    BifunctorLaws.assertSecondConsistency(bifunctor, fab, n -> "Value:" + n, eq);
    BifunctorLaws.assertSecondConsistency(bifunctor, fabLeft, n -> "Value:" + n, eq);
  }

  @Test
  @DisplayName("assertIdentity fails when the identity law is observed to be violated")
  void identityFails() {
    BiPredicate<Kind2<EitherKind2.Witness, ?, ?>, Kind2<EitherKind2.Witness, ?, ?>> neverEqual =
        (k1, k2) -> false;
    assertThatThrownBy(() -> BifunctorLaws.assertIdentity(bifunctor, fab, neverEqual))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Bifunctor identity");
  }
}
