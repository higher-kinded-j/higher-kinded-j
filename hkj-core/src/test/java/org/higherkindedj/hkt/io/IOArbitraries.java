// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the IO property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code IO<Integer>} generator and
 * the function/kleisli pools are defined once rather than copy-pasted across {@code
 * IOMonadPropertyTest} and {@code IOSelectivePropertyTest}.
 */
final class IOArbitraries {

  private IOArbitraries() {}

  /** {@code IO<Integer>} kinds wrapping (deferred) integers in {@code [-100, 100]}. */
  static Arbitrary<Kind<IOKind.Witness, Integer>> ioKinds() {
    return Arbitraries.integers().between(-100, 100).map(i -> IO_OP.widen(IO.delay(() -> i)));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> IO<String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<IOKind.Witness, String>>> intToIOString() {
    return Arbitraries.of(
        i -> IO_OP.widen(IO.delay(() -> "a:" + i)),
        i -> IO_OP.widen(IO.delay(() -> "b:" + (i * 2))),
        i -> IO_OP.widen(IO.delay(() -> String.valueOf(i))));
  }

  /** A small pool of {@code String -> IO<String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<IOKind.Witness, String>>> stringToIOString() {
    return Arbitraries.of(
        s -> IO_OP.widen(IO.delay(s::toUpperCase)),
        s -> IO_OP.widen(IO.delay(() -> "len:" + s.length())),
        s -> IO_OP.widen(IO.delay(() -> "transformed:" + s)));
  }
}
