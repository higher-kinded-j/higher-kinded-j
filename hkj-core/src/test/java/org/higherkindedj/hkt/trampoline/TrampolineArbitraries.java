// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the Trampoline property test.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Trampoline<Integer>}
 * generator and the function pools are defined once rather than copy-pasted into {@code
 * TrampolineMonadPropertyTest}.
 */
final class TrampolineArbitraries {

  private TrampolineArbitraries() {}

  /**
   * {@code Trampoline<Integer>} kinds over ints in {@code [-100, 100]}, mixing completed {@code
   * done} and deferred {@code defer(done)} forms so the property test exercises both shapes.
   */
  static Arbitrary<Kind<TrampolineKind.Witness, Integer>> trampolineKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .map(
            i ->
                i % 2 == 0
                    ? TRAMPOLINE.widen(Trampoline.done(i))
                    : TRAMPOLINE.widen(Trampoline.defer(() -> Trampoline.done(i))));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> Kind<Trampoline, String>} functions, mixing done/defer. */
  static Arbitrary<Function<Integer, Kind<TrampolineKind.Witness, String>>>
      intToTrampolineString() {
    return Arbitraries.of(
        i -> TRAMPOLINE.widen(Trampoline.done("v:" + i)),
        i -> TRAMPOLINE.widen(Trampoline.defer(() -> Trampoline.done(String.valueOf(i * 2)))),
        i -> TRAMPOLINE.widen(Trampoline.done(Integer.toBinaryString(i))));
  }

  /** A small pool of {@code String -> Kind<Trampoline, String>} functions, mixing done/defer. */
  static Arbitrary<Function<String, Kind<TrampolineKind.Witness, String>>>
      stringToTrampolineString() {
    return Arbitraries.of(
        s -> TRAMPOLINE.widen(Trampoline.done(s + "!")),
        s -> TRAMPOLINE.widen(Trampoline.defer(() -> Trampoline.done(s.toUpperCase()))),
        s -> TRAMPOLINE.widen(Trampoline.done("x:" + s)));
  }
}
