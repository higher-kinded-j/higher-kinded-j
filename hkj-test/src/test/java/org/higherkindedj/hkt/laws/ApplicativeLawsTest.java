// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link ApplicativeLaws} helpers against a known-good and a known-bad applicative.
 */
@DisplayName("ApplicativeLaws")
class ApplicativeLawsTest {

  private final Applicative<MaybeKind.Witness> good = MaybeMonad.INSTANCE;
  private final BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(MAYBE::narrow);
  private final Kind<MaybeKind.Witness, Integer> v = MAYBE.widen(Maybe.just(42));
  private final Kind<MaybeKind.Witness, Function<Integer, String>> u =
      MAYBE.widen(Maybe.just(i -> "v:" + i));
  private final Kind<MaybeKind.Witness, Function<String, Integer>> u2 =
      MAYBE.widen(Maybe.just(String::length));
  private final Kind<MaybeKind.Witness, Function<Integer, String>> v2 =
      MAYBE.widen(Maybe.just(i -> "x" + i));

  @Test
  @DisplayName("assertIdentity passes for a lawful applicative")
  void identityPasses() {
    ApplicativeLaws.assertIdentity(good, v, eq);
  }

  @Test
  @DisplayName("assertHomomorphism passes for a lawful applicative")
  void homomorphismPasses() {
    ApplicativeLaws.assertHomomorphism(good, 7, (Function<Integer, String>) i -> "v:" + i, eq);
  }

  @Test
  @DisplayName("assertInterchange passes for a lawful applicative")
  void interchangePasses() {
    ApplicativeLaws.assertInterchange(good, u, 99, eq);
  }

  @Test
  @DisplayName("assertComposition passes for a lawful applicative")
  void compositionPasses() {
    ApplicativeLaws.assertComposition(good, u2, v2, v, eq);
  }

  @Test
  @DisplayName("assertIdentity fails when the applicative violates the law")
  void identityFails() {
    // BrokenApplicative whose of() always returns Nothing — ap(Nothing, v) returns Nothing
    Applicative<MaybeKind.Witness> broken = BrokenApplicative.INSTANCE;
    assertThatThrownBy(() -> ApplicativeLaws.assertIdentity(broken, v, eq))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Applicative identity");
  }

  @Test
  @DisplayName("assertHomomorphism fails when of(f(x)) differs from ap(of(f), of(x))")
  void homomorphismFails() {
    Applicative<MaybeKind.Witness> broken = BrokenApplicative.INSTANCE;
    assertThatThrownBy(
            () ->
                ApplicativeLaws.assertHomomorphism(
                    broken, 7, (Function<Integer, String>) i -> "v:" + i, eq))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Applicative homomorphism");
  }

  /**
   * Applicative whose of() returns Just(value) but ap() always returns Nothing — breaks identity &
   * interchange.
   */
  private enum BrokenApplicative implements Applicative<MaybeKind.Witness> {
    INSTANCE;

    @Override
    public <A> Kind<MaybeKind.Witness, A> of(A value) {
      return MAYBE.widen(Maybe.just(value));
    }

    @Override
    public <A, B> Kind<MaybeKind.Witness, B> map(
        Function<? super A, ? extends B> fn, Kind<MaybeKind.Witness, A> input) {
      return MAYBE.widen(Maybe.nothing());
    }

    @Override
    public <A, B> Kind<MaybeKind.Witness, B> ap(
        Kind<MaybeKind.Witness, ? extends Function<A, B>> ff, Kind<MaybeKind.Witness, A> fa) {
      return MAYBE.widen(Maybe.nothing());
    }
  }
}
