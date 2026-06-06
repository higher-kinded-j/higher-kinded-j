// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Property-based Functor- and Monad-law verification for StateT over Optional. Equality runs both
 * sides against a fixed initial state. Fixtures mix pure (no state change), state-modifying, and
 * empty-outer states.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class StateTMonadPropertyTest {

  private final Monad<OptionalKind.Witness> outerMonad = Instances.monadError(optional());
  private final Monad<StateTKind.Witness<String, OptionalKind.Witness>> stateTMonad =
      Instances.stateT(outerMonad);

  private final BiPredicate<
          Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>,
          Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>>
      eq = StateTLawFixtures.EQ;

  @Provide
  Arbitrary<Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer>> stateTKinds() {
    return StateTArbitraries.stateTKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return StateTArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return StateTArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>>
      intToTKindString() {
    return StateTArbitraries.intToTKindString();
  }

  @Provide
  Arbitrary<Function<String, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>>
      stringToTKindString() {
    return StateTArbitraries.stringToTKindString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("stateTKinds") Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa) {
    FunctorLaws.assertIdentity(stateTMonad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("stateTKinds") Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(stateTMonad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTKindString")
          Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>> f) {
    MonadLaws.assertLeftIdentity(stateTMonad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("stateTKinds") Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> m) {
    MonadLaws.assertRightIdentity(stateTMonad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("stateTKinds") Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> m,
      @ForAll("intToTKindString")
          Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>> f,
      @ForAll("stringToTKindString")
          Function<String, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>> g) {
    MonadLaws.assertAssociativity(stateTMonad, m, f, g, eq);
  }
}
