// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the Id property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Id<Integer>} generator and
 * the function/kleisli pools are defined once rather than copy-pasted into {@code
 * IdMonadPropertyTest}.
 */
final class IdArbitraries {

  private IdArbitraries() {}

  /**
   * {@code Id<Integer>} kinds wrapping integers in {@code [-100, 100]}. Id always holds exactly one
   * value, so there is no empty inhabitant to generate.
   */
  static Arbitrary<Kind<IdKind.Witness, Integer>> idKinds() {
    return Arbitraries.integers().between(-100, 100).map(i -> ID.widen(Id.of(i)));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> Id<String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<IdKind.Witness, String>>> intToIdString() {
    return Arbitraries.of(
        i -> ID.widen(Id.of("v:" + i)),
        i -> ID.widen(Id.of(String.valueOf(i * 2))),
        i -> ID.widen(Id.of(Integer.toBinaryString(i))));
  }

  /** A small pool of {@code String -> Id<String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<IdKind.Witness, String>>> stringToIdString() {
    return Arbitraries.of(
        s -> ID.widen(Id.of(s + "!")),
        s -> ID.widen(Id.of(s.toUpperCase())),
        s -> ID.widen(Id.of("x:" + s)));
  }
}
