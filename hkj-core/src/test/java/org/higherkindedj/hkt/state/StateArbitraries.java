// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the State property tests (Integer state).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code State<Integer, Integer>}
 * generator and the function/kleisli pools are defined once rather than copy-pasted into {@code
 * StateMonadPropertyTest}.
 */
final class StateArbitraries {

  private StateArbitraries() {}

  /** {@code State<Integer, Integer>} kinds threading the state ({@code s -> (i + s, s + 1)}). */
  static Arbitrary<Kind<StateKind.Witness<Integer>, Integer>> stateKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .map(i -> STATE.widen(State.of(s -> new StateTuple<>(i + s, s + 1))));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> State<Integer, String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<StateKind.Witness<Integer>, String>>> intToStateString() {
    return Arbitraries.of(
        i -> STATE.widen(State.of(s -> new StateTuple<>("v:" + i, s + 1))),
        i -> STATE.widen(State.of(s -> new StateTuple<>(String.valueOf(i * 2), s * 2))),
        i -> STATE.widen(State.of(s -> new StateTuple<>(i + ":" + s, s))));
  }

  /** A small pool of {@code String -> State<Integer, String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<StateKind.Witness<Integer>, String>>>
      stringToStateString() {
    return Arbitraries.of(
        s -> STATE.widen(State.of(st -> new StateTuple<>(s + "!", st + 1))),
        s -> STATE.widen(State.of(st -> new StateTuple<>(s.toUpperCase(), st - 1))),
        s -> STATE.widen(State.of(st -> new StateTuple<>(s + ":" + st, st))));
  }
}
