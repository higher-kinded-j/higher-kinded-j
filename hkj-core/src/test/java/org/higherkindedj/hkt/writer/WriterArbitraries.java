// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the Writer property tests (String log).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Writer<String, Integer>}
 * generator and the function pools are defined once rather than copy-pasted into {@code
 * WriterMonadPropertyTest}.
 */
final class WriterArbitraries {

  private WriterArbitraries() {}

  /**
   * {@code Writer<String, Integer>} kinds: alpha logs (max length 5) over ints in {@code [-100,
   * 100]}.
   */
  static Arbitrary<Kind<WriterKind.Witness<String>, Integer>> writerKinds() {
    Arbitrary<Integer> values = Arbitraries.integers().between(-100, 100);
    Arbitrary<String> logs = Arbitraries.strings().alpha().ofMaxLength(5);
    return Combinators.combine(logs, values).as((log, v) -> WRITER.widen(new Writer<>(log, v)));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> Writer<String, String>} kleisli arrows. */
  static Arbitrary<Function<Integer, Kind<WriterKind.Witness<String>, String>>>
      intToWriterString() {
    return Arbitraries.of(
        i -> WRITER.widen(new Writer<>("f1;", "v:" + i)),
        i -> WRITER.widen(new Writer<>("f2;", String.valueOf(i * 2))),
        i -> WRITER.widen(new Writer<>("", Integer.toBinaryString(i))));
  }

  /** A small pool of {@code String -> Writer<String, String>} kleisli arrows. */
  static Arbitrary<Function<String, Kind<WriterKind.Witness<String>, String>>>
      stringToWriterString() {
    return Arbitraries.of(
        s -> WRITER.widen(new Writer<>("g1;", s + "!")),
        s -> WRITER.widen(new Writer<>("g2;", s.toUpperCase())),
        s -> WRITER.widen(new Writer<>("", "x:" + s)));
  }
}
