// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies the {@link FunctorLaws} helpers against a known-good and a known-bad functor. */
@DisplayName("FunctorLaws")
class FunctorLawsTest {

  private final Functor<MaybeKind.Witness> goodFunctor = MaybeFunctor.INSTANCE;
  private final Kind<MaybeKind.Witness, Integer> fa = MAYBE.widen(Maybe.just(42));
  private final BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(MAYBE::narrow);

  @Test
  @DisplayName("assertIdentity passes for a lawful functor")
  void identityPasses() {
    FunctorLaws.assertIdentity(goodFunctor, fa, eq);
  }

  @Test
  @DisplayName("assertComposition passes for a lawful functor")
  void compositionPasses() {
    FunctorLaws.assertComposition(goodFunctor, fa, Object::toString, String::length, eq);
  }

  @Test
  @DisplayName("assertIdentity fails when the functor violates the law")
  void identityFails() {
    Functor<MaybeKind.Witness> brokenFunctor =
        new Functor<>() {
          @Override
          public <A, B> Kind<MaybeKind.Witness, B> map(
              Function<? super A, ? extends B> f, Kind<MaybeKind.Witness, A> input) {
            return MAYBE.widen(Maybe.nothing());
          }
        };
    assertThatThrownBy(() -> FunctorLaws.assertIdentity(brokenFunctor, fa, eq))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Functor identity");
  }
}
