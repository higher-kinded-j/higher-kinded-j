// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the List property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code List<Integer>} generator and
 * the function/kleisli pools are defined once rather than copy-pasted across {@code
 * ListMonadPropertyTest} and {@code ListSelectivePropertyTest}.
 */
final class ListArbitraries {

  private ListArbitraries() {}

  /** {@code List<Integer>} kinds of size {@code [0, 5]} over ints in {@code [-100, 100]}. */
  static Arbitrary<Kind<ListKind.Witness, Integer>> listKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .list()
        .ofMinSize(0)
        .ofMaxSize(5)
        .map(l -> LIST.widen(new ArrayList<>(l)));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> List<String>} kleisli arrows (some empty). */
  static Arbitrary<Function<Integer, Kind<ListKind.Witness, String>>> intToListString() {
    return Arbitraries.of(
        i -> LIST.widen(List.of("a:" + i)),
        i -> LIST.widen(List.of("a:" + i, "b:" + i)),
        _ -> LIST.widen(List.of()),
        i -> LIST.widen(List.of(String.valueOf(i))));
  }

  /** A small pool of {@code String -> List<String>} kleisli arrows (some empty). */
  static Arbitrary<Function<String, Kind<ListKind.Witness, String>>> stringToListString() {
    return Arbitraries.of(
        s -> LIST.widen(List.of(s.toUpperCase())),
        s -> LIST.widen(List.of(s + "!", s + "?")),
        _ -> LIST.widen(List.of()),
        s -> LIST.widen(List.of("len:" + s.length())));
  }
}
