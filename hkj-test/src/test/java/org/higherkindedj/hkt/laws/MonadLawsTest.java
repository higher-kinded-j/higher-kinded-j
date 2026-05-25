// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies the {@link MonadLaws} helpers against a known-good and a known-bad monad. */
@DisplayName("MonadLaws")
class MonadLawsTest {

  private final Monad<MaybeKind.Witness> goodMonad = MaybeMonad.INSTANCE;
  private final Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(42));
  private final Function<Integer, Kind<MaybeKind.Witness, String>> f =
      i -> MAYBE.widen(Maybe.just("f:" + i));
  private final Function<String, Kind<MaybeKind.Witness, String>> g =
      s -> MAYBE.widen(Maybe.just(s + "!"));
  private final BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(MAYBE::narrow);

  @Test
  @DisplayName("assertLeftIdentity passes for a lawful monad")
  void leftIdentityPasses() {
    MonadLaws.assertLeftIdentity(goodMonad, 7, f, eq);
  }

  @Test
  @DisplayName("assertRightIdentity passes for a lawful monad")
  void rightIdentityPasses() {
    MonadLaws.assertRightIdentity(goodMonad, ma, eq);
  }

  @Test
  @DisplayName("assertAssociativity passes for a lawful monad")
  void associativityPasses() {
    MonadLaws.assertAssociativity(goodMonad, ma, f, g, eq);
  }

  @Test
  @DisplayName("assertLeftIdentity fails when the monad violates the law")
  void leftIdentityFails() {
    Monad<MaybeKind.Witness> broken = BrokenMonad.INSTANCE;
    assertThatThrownBy(() -> MonadLaws.assertLeftIdentity(broken, 7, f, eq))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Monad left identity");
  }

  @Test
  @DisplayName("assertRightIdentity fails when the monad violates the law")
  void rightIdentityFails() {
    Monad<MaybeKind.Witness> broken = BrokenMonad.INSTANCE;
    assertThatThrownBy(() -> MonadLaws.assertRightIdentity(broken, ma, eq))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Monad right identity");
  }

  /**
   * Monad that always returns Nothing — violates left/right identity but satisfies associativity
   * vacuously.
   */
  private enum BrokenMonad implements Monad<MaybeKind.Witness> {
    INSTANCE;

    @Override
    public <A> Kind<MaybeKind.Witness, A> of(A value) {
      return MAYBE.widen(Maybe.nothing());
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

    @Override
    public <A, B> Kind<MaybeKind.Witness, B> flatMap(
        Function<? super A, ? extends Kind<MaybeKind.Witness, B>> fn,
        Kind<MaybeKind.Witness, A> input) {
      return MAYBE.widen(Maybe.nothing());
    }
  }
}
