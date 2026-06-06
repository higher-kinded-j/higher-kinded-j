// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.reader.ReaderTestBase.TestConfig;

/**
 * Shared jqwik arbitraries for the Reader property tests (environment = {@link TestConfig}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Reader<TestConfig, Integer>}
 * generator and the function/kleisli pools are defined once rather than copy-pasted across {@code
 * ReaderMonadPropertyTest} and {@code ReaderSelectivePropertyTest}.
 */
final class ReaderArbitraries {

  private ReaderArbitraries() {}

  /**
   * {@code Reader<TestConfig, Integer>} kinds: a mix of constant and environment-reading functions.
   */
  static Arbitrary<Kind<ReaderKind.Witness<TestConfig>, Integer>> readerKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .map(
            i ->
                Arbitraries.of(
                    (Function<TestConfig, Integer>) _ -> i,
                    env -> i + env.maxConnections(),
                    env -> i ^ env.url().length()))
        .flatMap(arb -> arb.map(fn -> READER.widen(Reader.of(fn))));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> Reader<TestConfig, String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<ReaderKind.Witness<TestConfig>, String>>>
      intToReaderString() {
    return Arbitraries.of(
        i -> READER.widen(Reader.of(_ -> "v:" + i)),
        i -> READER.widen(Reader.of(env -> i + ":" + env.url())),
        i -> READER.widen(Reader.of(env -> String.valueOf(i + env.maxConnections()))));
  }

  /** A small pool of {@code String -> Reader<TestConfig, String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<ReaderKind.Witness<TestConfig>, String>>>
      stringToReaderString() {
    return Arbitraries.of(
        s -> READER.widen(Reader.of(_ -> s + "!")),
        s -> READER.widen(Reader.of(_ -> s.toUpperCase())),
        s -> READER.widen(Reader.of(env -> s + ":" + env.maxConnections())));
  }
}
