// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
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
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Property-based Functor- and Monad-law verification for StateT over Optional. Equality runs both
 * sides against a fixed initial state. Fixtures mix pure (no state change), state-modifying, and
 * empty-outer states.
 */
class StateTMonadPropertyTest {

  private static final String INITIAL = "initial";

  private final Monad<OptionalKind.Witness> outerMonad = Instances.monadError(optional());
  private final Monad<StateTKind.Witness<String, OptionalKind.Witness>> stateTMonad =
      Instances.stateT(outerMonad);

  private <A> Optional<StateTuple<String, A>> unwrapKind(
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> kind) {
    if (kind == null) return null;
    var stateT = STATE_T.narrow(kind);
    Kind<OptionalKind.Witness, StateTuple<String, A>> outerKind = stateT.runStateT(INITIAL);
    return OPTIONAL.narrow(outerKind);
  }

  private final BiPredicate<
          Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>,
          Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>>
      eq = KindEquivalence.byEqualsAfter(this::unwrapKind);

  private <A> StateT<String, OptionalKind.Witness, A> createStateT(
      Function<String, StateTuple<String, A>> fn) {
    return StateT.create(s -> outerMonad.of(fn.apply(s)), outerMonad);
  }

  private <A> Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> pureT(A value) {
    return STATE_T.widen(createStateT(s -> StateTuple.of(s, value)));
  }

  private <A> Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> stateModifying(
      A value, String suffix) {
    return STATE_T.widen(createStateT(s -> StateTuple.of(s + suffix, value)));
  }

  private <A> Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> emptyT() {
    return STATE_T.widen(
        StateT.create(s -> OPTIONAL.widen(Optional.<StateTuple<String, A>>empty()), outerMonad));
  }

  @Provide
  Arbitrary<Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer>> stateTKinds() {
    // Mix of pure (state-preserving), state-modifying, and empty-outer Optional states.
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(this.<Integer>emptyT());
              }
              if (i % 4 == 0) {
                return Arbitraries.just(stateModifying(i, "_mod"));
              }
              return Arbitraries.just(pureT(i));
            });
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
  Arbitrary<Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> pureT("v:" + i),
        i -> stateModifying("mod:" + i, "_a"),
        i -> i == 0 ? emptyT() : pureT(String.valueOf(i)),
        i -> stateModifying(i + "!", "_b"));
  }

  @Provide
  Arbitrary<Function<String, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> pureT(s + "!"),
        s -> stateModifying(s.toUpperCase(), "_c"),
        s -> s.isEmpty() ? emptyT() : pureT(s + ":done"));
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
