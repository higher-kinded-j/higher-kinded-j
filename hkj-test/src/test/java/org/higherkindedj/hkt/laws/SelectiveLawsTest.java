// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeSelective;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies the {@link SelectiveLaws} helpers against a known-good and a known-bad selective. */
@DisplayName("SelectiveLaws")
class SelectiveLawsTest {

  private final Selective<MaybeKind.Witness> good = MaybeSelective.INSTANCE;
  private final BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(MAYBE::narrow);
  private final Function<Integer, String> f = i -> "v" + i;

  @Test
  @DisplayName("assertLeftPure passes for a lawful selective")
  void leftPurePasses() {
    SelectiveLaws.assertLeftPure(good, 42, good.of(f), eq);
  }

  @Test
  @DisplayName("assertRightPure passes for a lawful selective")
  void rightPurePasses() {
    SelectiveLaws.assertRightPure(good, "already-done", good.of(f), eq);
  }

  @Test
  @DisplayName("assertLeftPure passes for a lawful selective with an effectful function value")
  void leftPurePassesWithEffectfulFunction() {
    // Demonstrates the generalised power: ff is an effectful Kind<F, Function<A, B>>
    // (a function inside a Just(...)), not a pure-lifted one.
    Kind<MaybeKind.Witness, Function<Integer, String>> effectfulFf = MAYBE.widen(Maybe.just(f));
    SelectiveLaws.assertLeftPure(good, 42, effectfulFf, eq);
  }

  @Test
  @DisplayName("assertRightPure passes for a lawful selective with an effectful function value")
  void rightPurePassesWithEffectfulFunction() {
    // Demonstrates the generalised power: ff is an effectful Kind<F, Function<A, B>>
    // (a function inside a Just(...)), not a pure-lifted one.
    Kind<MaybeKind.Witness, Function<Integer, String>> effectfulFf = MAYBE.widen(Maybe.just(f));
    SelectiveLaws.assertRightPure(good, "already-done", effectfulFf, eq);
  }

  @Test
  @DisplayName("assertLeftPure fails when the selective violates the law")
  void leftPureFails() {
    Selective<MaybeKind.Witness> broken = BrokenSelective.INSTANCE;
    assertThatThrownBy(() -> SelectiveLaws.assertLeftPure(broken, 42, broken.of(f), eq))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Selective left-pure");
  }

  @Test
  @DisplayName("assertRightPure fails when the selective violates the law")
  void rightPureFails() {
    Selective<MaybeKind.Witness> broken = BrokenSelective.INSTANCE;
    assertThatThrownBy(() -> SelectiveLaws.assertRightPure(broken, "x", broken.of(f), eq))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Selective right-pure");
  }

  /**
   * Selective whose select() always returns Nothing, but whose of/map/ap are lawful — violates the
   * left-pure law (Nothing != ap(ff, of(a))) and the right-pure law (Nothing != of(b)).
   */
  private enum BrokenSelective implements Selective<MaybeKind.Witness> {
    INSTANCE;

    @Override
    public <A> Kind<MaybeKind.Witness, A> of(A value) {
      return MaybeSelective.INSTANCE.of(value);
    }

    @Override
    public <A, B> Kind<MaybeKind.Witness, B> map(
        Function<? super A, ? extends B> fn, Kind<MaybeKind.Witness, A> input) {
      return MaybeSelective.INSTANCE.map(fn, input);
    }

    @Override
    public <A, B> Kind<MaybeKind.Witness, B> ap(
        Kind<MaybeKind.Witness, ? extends Function<A, B>> ff, Kind<MaybeKind.Witness, A> fa) {
      return MaybeSelective.INSTANCE.ap(ff, fa);
    }

    @Override
    public <A, B> Kind<MaybeKind.Witness, B> select(
        Kind<MaybeKind.Witness, Choice<A, B>> fab, Kind<MaybeKind.Witness, Function<A, B>> ff) {
      return MAYBE.widen(Maybe.nothing());
    }
  }
}
