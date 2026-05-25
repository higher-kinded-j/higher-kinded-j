// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
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
class StateMonadPropertyTest {

  private static final int INITIAL = 7;

  private final Monad<StateKind.Witness<Integer>> monad = new StateMonad<>();

  private final BiPredicate<
          Kind<StateKind.Witness<Integer>, ?>, Kind<StateKind.Witness<Integer>, ?>>
      eq = (k1, k2) -> Objects.equals(STATE.runState(k1, INITIAL), STATE.runState(k2, INITIAL));

  @Provide
  Arbitrary<Kind<StateKind.Witness<Integer>, Integer>> stateKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .map(i -> STATE.widen(State.<Integer, Integer>of(s -> new StateTuple<>(i + s, s + 1))));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Provide
  Arbitrary<Function<Integer, Kind<StateKind.Witness<Integer>, String>>> intToStateString() {
    return Arbitraries.of(
        i -> STATE.widen(State.of(s -> new StateTuple<>("v:" + i, s + 1))),
        i -> STATE.widen(State.of(s -> new StateTuple<>(String.valueOf(i * 2), s * 2))),
        i -> STATE.widen(State.of(s -> new StateTuple<>(i + ":" + s, s))));
  }

  @Provide
  Arbitrary<Function<String, Kind<StateKind.Witness<Integer>, String>>> stringToStateString() {
    return Arbitraries.of(
        s -> STATE.widen(State.of(st -> new StateTuple<>(s + "!", st + 1))),
        s -> STATE.widen(State.of(st -> new StateTuple<>(s.toUpperCase(), st - 1))),
        s -> STATE.widen(State.of(st -> new StateTuple<>(s + ":" + st, st))));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("stateKinds") Kind<StateKind.Witness<Integer>, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("stateKinds") Kind<StateKind.Witness<Integer>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToStateString") Function<Integer, Kind<StateKind.Witness<Integer>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("stateKinds") Kind<StateKind.Witness<Integer>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("stateKinds") Kind<StateKind.Witness<Integer>, Integer> m,
      @ForAll("intToStateString") Function<Integer, Kind<StateKind.Witness<Integer>, String>> f,
      @ForAll("stringToStateString") Function<String, Kind<StateKind.Witness<Integer>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
