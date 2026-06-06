// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the Writer type-class tests (String log).
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#kinds")}. Centralising the
 * fixture streams keeps every algebra's law verification driven by the same inputs and removes the
 * copy-pasted {@code fixtures()}/{@code values()} providers that previously lived in each test.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class WriterLawFixtures {

  private WriterLawFixtures() {}

  /**
   * Shared law equality: narrows both Writer kinds and compares the resulting {@code Writer}
   * records (log + value) with {@code equals}.
   */
  static final BiPredicate<Kind<WriterKind.Witness<String>, ?>, Kind<WriterKind.Witness<String>, ?>>
      EQ = KindEquivalence.byEqualsAfter(WRITER::narrow);

  /** {@code Writer("", 0)}, {@code Writer("log", 42)}, {@code Writer("x", -1)}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Writer(\"\", 0)", WRITER.widen(new Writer<>("", 0))),
        Arguments.of("Writer(\"log\", 42)", WRITER.widen(new Writer<>("log", 42))),
        Arguments.of("Writer(\"x\", -1)", WRITER.widen(new Writer<>("x", -1))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
