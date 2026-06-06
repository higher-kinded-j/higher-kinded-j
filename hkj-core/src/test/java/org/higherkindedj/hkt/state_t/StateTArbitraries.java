// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Shared jqwik arbitraries for the StateT property tests (inner monad = {@code Optional}, state =
 * {@code String}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code StateT} generator and the
 * function pools are defined once rather than copy-pasted into {@code StateTMonadPropertyTest}.
 */
final class StateTArbitraries {

  private StateTArbitraries() {}

  private static final Monad<OptionalKind.Witness> OUTER = Instances.monadError(optional());

  private static <A> StateT<String, OptionalKind.Witness, A> createStateT(
      Function<String, StateTuple<String, A>> fn) {
    return StateT.create(s -> OUTER.of(fn.apply(s)), OUTER);
  }

  private static <A> Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> pureT(A value) {
    return STATE_T.widen(createStateT(s -> StateTuple.of(s, value)));
  }

  private static <A> Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> stateModifying(
      A value, String suffix) {
    return STATE_T.widen(createStateT(s -> StateTuple.of(s + suffix, value)));
  }

  private static <A> Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> emptyT() {
    return STATE_T.widen(StateT.create(_ -> OPTIONAL.widen(Optional.empty()), OUTER));
  }

  /** Mix of pure (state-preserving), state-modifying, and empty-outer Optional states. */
  static Arbitrary<Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer>> stateTKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(emptyT());
              }
              if (i % 4 == 0) {
                return Arbitraries.just(stateModifying(i, "_mod"));
              }
              return Arbitraries.just(pureT(i));
            });
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> StateT<String, Optional, String>} kleisli arrows. */
  static Arbitrary<
          Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> pureT("v:" + i),
        i -> stateModifying("mod:" + i, "_a"),
        i -> i == 0 ? emptyT() : pureT(String.valueOf(i)),
        i -> stateModifying(i + "!", "_b"));
  }

  /** A small pool of {@code String -> StateT<String, Optional, String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> pureT(s + "!"),
        s -> stateModifying(s.toUpperCase(), "_c"),
        s -> s.isEmpty() ? emptyT() : pureT(s + ":done"));
  }
}
