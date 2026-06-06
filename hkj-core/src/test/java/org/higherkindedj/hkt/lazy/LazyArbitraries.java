// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the Lazy property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Lazy<Integer>} generator and
 * the function/kleisli pools are defined once rather than copy-pasted into {@code
 * LazyMonadPropertyTest}.
 */
final class LazyArbitraries {

  private LazyArbitraries() {}

  /** {@code Lazy<Integer>} kinds (already-computed) wrapping integers in {@code [-100, 100]}. */
  static Arbitrary<Kind<LazyKind.Witness, Integer>> lazyKinds() {
    return Arbitraries.integers().between(-100, 100).map(i -> LAZY.widen(Lazy.now(i)));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> Lazy<String>} kleisli arrows (mix of now/defer). */
  static Arbitrary<Function<Integer, Kind<LazyKind.Witness, String>>> intToLazyString() {
    return Arbitraries.of(
        i -> LAZY.widen(Lazy.now("v:" + i)),
        i -> LAZY.widen(Lazy.defer(() -> String.valueOf(i * 2))),
        i -> LAZY.widen(Lazy.now(Integer.toBinaryString(i))));
  }

  /** A small pool of {@code String -> Lazy<String>} kleisli arrows (mix of now/defer). */
  static Arbitrary<Function<String, Kind<LazyKind.Witness, String>>> stringToLazyString() {
    return Arbitraries.of(
        s -> LAZY.widen(Lazy.now(s + "!")),
        s -> LAZY.widen(Lazy.defer(s::toUpperCase)),
        s -> LAZY.widen(Lazy.now("x:" + s)));
  }
}
