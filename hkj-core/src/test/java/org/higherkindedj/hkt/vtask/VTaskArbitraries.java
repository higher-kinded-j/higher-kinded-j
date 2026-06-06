// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the VTask property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code VTask<Integer>} generators
 * (both successful and failing) and the function/kleisli pools are defined once rather than inlined
 * in {@code VTaskPropertyTest}. The generators yield widened {@code Kind<VTaskKind.Witness, ?>}
 * values so the law bodies can call the shipped law helpers directly.
 */
final class VTaskArbitraries {

  private VTaskArbitraries() {}

  /** Arbitrary {@code VTask<Integer>} values — successful, with ~20% failing. */
  static Arbitrary<Kind<VTaskKind.Witness, Integer>> vtaskKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .flatMap(
            i -> {
              if (i % 5 == 0) {
                return Arbitraries.of(
                        new RuntimeException("error: validation failed"),
                        new IllegalArgumentException("error: invalid input"),
                        new IllegalStateException("error: bad state"))
                    .map(e -> VTASK.widen(VTask.fail(e)));
              }
              return Arbitraries.just(VTASK.widen(VTask.succeed(i)));
            });
  }

  /** Arbitrary successful {@code VTask<Integer>} values. */
  static Arbitrary<Kind<VTaskKind.Witness, Integer>> successfulVtaskKinds() {
    return Arbitraries.integers().between(-100, 100).map(i -> VTASK.widen(VTask.succeed(i)));
  }

  /** A small pool of {@code Integer -> VTask<String>} kleisli arrows (some failing). */
  static Arbitrary<Function<Integer, Kind<VTaskKind.Witness, String>>> intToVTaskString() {
    return Arbitraries.of(
        i ->
            VTASK.widen(
                i % 2 == 0 ? VTask.succeed("even:" + i) : VTask.fail(new RuntimeException("odd"))),
        i ->
            VTASK.widen(
                i > 0
                    ? VTask.succeed("positive:" + i)
                    : VTask.fail(new RuntimeException("non-positive"))),
        i -> VTASK.widen(VTask.succeed("value:" + i)),
        i ->
            VTASK.widen(
                i == 0
                    ? VTask.fail(new RuntimeException("zero"))
                    : VTask.succeed(String.valueOf(i))));
  }

  /** A small pool of {@code String -> VTask<String>} kleisli arrows (some failing). */
  static Arbitrary<Function<String, Kind<VTaskKind.Witness, String>>> stringToVTaskString() {
    return Arbitraries.of(
        s ->
            VTASK.widen(
                s.isEmpty()
                    ? VTask.fail(new RuntimeException("empty"))
                    : VTask.succeed(s.toUpperCase())),
        s ->
            VTASK.widen(
                s.length() > 3
                    ? VTask.succeed("long:" + s)
                    : VTask.fail(new RuntimeException("short"))),
        s -> VTASK.widen(VTask.succeed("transformed:" + s)));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(
        Object::toString,
        i -> "val:" + i,
        i -> String.valueOf(i * 2),
        i -> i >= 0 ? "+" + i : String.valueOf(i));
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(
        String::length, s -> s.hashCode() % 100, s -> s.isEmpty() ? 0 : (int) s.charAt(0));
  }
}
