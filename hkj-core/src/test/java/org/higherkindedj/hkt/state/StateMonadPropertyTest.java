// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Functor- and Monad-law verification for State (Integer state). Equality is checked
 * by running both sides against a fixed initial state.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class StateMonadPropertyTest {

  private final Monad<StateKind.Witness<Integer>> monad = new StateMonad<>();

  @Provide
  Arbitrary<Kind<StateKind.Witness<Integer>, Integer>> stateKinds() {
    return StateArbitraries.stateKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return StateArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return StateArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<StateKind.Witness<Integer>, String>>> intToStateString() {
    return StateArbitraries.intToStateString();
  }

  @Provide
  Arbitrary<Function<String, Kind<StateKind.Witness<Integer>, String>>> stringToStateString() {
    return StateArbitraries.stringToStateString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("stateKinds") Kind<StateKind.Witness<Integer>, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, StateLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("stateKinds") Kind<StateKind.Witness<Integer>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, StateLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToStateString") Function<Integer, Kind<StateKind.Witness<Integer>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, StateLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("stateKinds") Kind<StateKind.Witness<Integer>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, StateLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("stateKinds") Kind<StateKind.Witness<Integer>, Integer> m,
      @ForAll("intToStateString") Function<Integer, Kind<StateKind.Witness<Integer>, String>> f,
      @ForAll("stringToStateString") Function<String, Kind<StateKind.Witness<Integer>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, StateLawFixtures.EQ);
  }
}
