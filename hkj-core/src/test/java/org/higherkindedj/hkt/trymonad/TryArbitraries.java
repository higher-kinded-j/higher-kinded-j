// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * Shared jqwik arbitraries for the Try property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Try<Integer>} generator and
 * the function/kleisli pools are defined once rather than copy-pasted into {@code
 * TryMonadPropertyTest}.
 *
 * <p>{@link #FAIL_A} and {@link #FAIL_B} are reused across generated kinds so that record-based
 * {@code Try.Failure} equality holds under associativity (where the same failure must compare equal
 * to itself on both sides of the law).
 */
final class TryArbitraries {

  private TryArbitraries() {}

  /** Shared failure causes; reused so {@code Try.Failure} equality is stable under the laws. */
  static final RuntimeException FAIL_A = new IllegalStateException("a");

  static final RuntimeException FAIL_B = new ArithmeticException("b");

  /**
   * {@code Try<Integer>} kinds: mostly {@code Success(i)}, with ~15% {@code Failure} (from injected
   * nulls) and every multiple of five mapped to a {@code Failure}, so both inhabitants are
   * exercised.
   *
   * @param bound the (inclusive) magnitude of the generated integers
   */
  static Arbitrary<Kind<TryKind.Witness, Integer>> tryKinds(int bound) {
    return Arbitraries.integers()
        .between(-bound, bound)
        .injectNull(0.15)
        .map(TryArbitraries::toTryKind);
  }

  /**
   * Widens a possibly-{@code null} integer: {@code null} collapses to {@code Failure(FAIL_A)},
   * every multiple of five to {@code Failure(FAIL_B)}, everything else to {@code Success(i)}.
   */
  private static Kind<TryKind.Witness, Integer> toTryKind(@Nullable Integer i) {
    if (i == null) return TRY.widen(Try.failure(FAIL_A));
    if (i % 5 == 0) return TRY.widen(Try.failure(FAIL_B));
    return TRY.widen(Try.success(i));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> Try<String>} kleisli arrows (mix of success/failure). */
  static Arbitrary<Function<Integer, Kind<TryKind.Witness, String>>> intToTryString() {
    return Arbitraries.of(
        i -> TRY.widen(i % 2 == 0 ? Try.success("even:" + i) : Try.failure(FAIL_A)),
        i -> TRY.widen(i > 0 ? Try.success("positive:" + i) : Try.failure(FAIL_B)),
        i -> TRY.widen(Try.success("value:" + i)));
  }

  /** A small pool of {@code String -> Try<String>} kleisli arrows (mix of success/failure). */
  static Arbitrary<Function<String, Kind<TryKind.Witness, String>>> stringToTryString() {
    return Arbitraries.of(
        s -> TRY.widen(s.isEmpty() ? Try.failure(FAIL_A) : Try.success(s.toUpperCase())),
        s -> TRY.widen(s.length() > 3 ? Try.success("long:" + s) : Try.failure(FAIL_B)),
        s -> TRY.widen(Try.success("transformed:" + s)));
  }
}
