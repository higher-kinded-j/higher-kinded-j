// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the {@link NonEmptyList} property tests.
 *
 * <p>Generators never produce an empty list (a {@code NonEmptyList} cannot be empty), and the
 * kleisli pools only return non-empty results, since {@code flatMap}'s function must.
 */
final class NonEmptyListArbitraries {

  private NonEmptyListArbitraries() {}

  /**
   * {@code NonEmptyList<Integer>} kinds of size {@code [1, 5]} over ints in {@code [-100, 100]}.
   */
  static Arbitrary<Kind<NonEmptyListKind.Witness, Integer>> nonEmptyListKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .list()
        .ofMinSize(1)
        .ofMaxSize(5)
        .map(l -> NON_EMPTY_LIST.widen(NonEmptyList.of(l.get(0), l.subList(1, l.size()))));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> NonEmptyList<String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<NonEmptyListKind.Witness, String>>> intToNelString() {
    return Arbitraries.of(
        i -> NON_EMPTY_LIST.widen(NonEmptyList.single("a:" + i)),
        i -> NON_EMPTY_LIST.widen(NonEmptyList.of("a:" + i, "b:" + i)),
        i -> NON_EMPTY_LIST.widen(NonEmptyList.single(String.valueOf(i))));
  }

  /** A small pool of {@code String -> NonEmptyList<String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<NonEmptyListKind.Witness, String>>> stringToNelString() {
    return Arbitraries.of(
        s -> NON_EMPTY_LIST.widen(NonEmptyList.single(s.toUpperCase())),
        s -> NON_EMPTY_LIST.widen(NonEmptyList.of(s + "!", s + "?")),
        s -> NON_EMPTY_LIST.widen(NonEmptyList.single("len:" + s.length())));
  }
}
