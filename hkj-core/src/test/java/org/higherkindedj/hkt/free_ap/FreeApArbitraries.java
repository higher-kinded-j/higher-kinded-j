// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * Shared jqwik arbitraries for the {@code FreeAp} property test (interpreted over {@code Maybe}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code FreeAp} generator and the
 * function pools are defined once. The generator mixes {@code pure}, {@code lift(just)} and a
 * one-level {@code Ap}-node so the laws exercise both leaves and structural nodes.
 */
final class FreeApArbitraries {

  private FreeApArbitraries() {}

  /**
   * {@code FreeAp} values over ints in {@code [-100, 100]}: a third {@code pure}, a third {@code
   * lift(just)} and a third a one-level {@code Ap} node ({@code lift(just(i)).ap(pure(id))}).
   */
  static Arbitrary<Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer>> freeApKinds() {
    Arbitrary<Integer> ints = Arbitraries.integers().between(-100, 100);
    return Arbitraries.oneOf(
        ints.map(FreeApArbitraries::pureOf),
        ints.map(FreeApArbitraries::liftOf),
        ints.map(FreeApArbitraries::apOf));
  }

  private static Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> pureOf(int i) {
    return FREE_AP.widen(FreeAp.pure(i));
  }

  private static Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> liftOf(int i) {
    return FREE_AP.widen(FreeAp.lift(MAYBE.just(i)));
  }

  private static Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> apOf(int i) {
    FreeAp<MaybeKind.Witness, Function<Integer, Integer>> id = FreeAp.pure(x -> x);
    return FREE_AP.widen(FreeAp.lift(MAYBE.just(i)).ap(id));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }
}
