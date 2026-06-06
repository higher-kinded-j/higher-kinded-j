// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * Shared jqwik arbitraries for the Optional property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Optional<Integer>} generator
 * and the function/kleisli pools are defined once rather than copy-pasted across {@code
 * OptionalMonadPropertyTest} and {@code OptionalSelectivePropertyTest}.
 */
final class OptionalArbitraries {

  private OptionalArbitraries() {}

  /**
   * {@code Optional<Integer>} kinds: mostly present, with injected nulls collapsed to {@code
   * Optional.empty()}, so both inhabitants are exercised.
   *
   * @param bound the (inclusive) magnitude of the generated integers
   * @param nullFraction the proportion of generated values injected as {@code empty}
   */
  static Arbitrary<Kind<OptionalKind.Witness, Integer>> optionalKinds(
      int bound, double nullFraction) {
    return Arbitraries.integers()
        .between(-bound, bound)
        .injectNull(nullFraction)
        .map(OptionalArbitraries::toOptionalKind);
  }

  /** Widens a possibly-{@code null} integer, with {@code null} collapsing to {@code empty}. */
  private static Kind<OptionalKind.Witness, Integer> toOptionalKind(@Nullable Integer i) {
    return OPTIONAL.widen(Optional.ofNullable(i));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> Optional<String>} kleisli arrows (mix of present/empty). */
  static Arbitrary<Function<Integer, Kind<OptionalKind.Witness, String>>> intToOptionalString() {
    return Arbitraries.of(
        i -> OPTIONAL.widen(i % 2 == 0 ? Optional.of("even:" + i) : Optional.empty()),
        i -> OPTIONAL.widen(i > 0 ? Optional.of("positive:" + i) : Optional.empty()),
        i -> OPTIONAL.widen(Optional.of("value:" + i)));
  }

  /** A small pool of {@code String -> Optional<String>} kleisli arrows (mix of present/empty). */
  static Arbitrary<Function<String, Kind<OptionalKind.Witness, String>>> stringToOptionalString() {
    return Arbitraries.of(
        s -> OPTIONAL.widen(s.isEmpty() ? Optional.empty() : Optional.of(s.toUpperCase())),
        s -> OPTIONAL.widen(s.length() > 3 ? Optional.of("long:" + s) : Optional.empty()),
        s -> OPTIONAL.widen(Optional.of("transformed:" + s)));
  }
}
