// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the Validated type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#kinds")}. Centralising
 * the fixture streams keeps every algebra's law verification driven by the same inputs and removes
 * the copy-pasted {@code fixtures()}/{@code values()} providers that previously lived in each test.
 */
// Referenced only via fully-qualified @MethodSource strings, which IntelliJ's usage search misses.
@SuppressWarnings("unused")
final class ValidatedLawFixtures {

  private ValidatedLawFixtures() {}

  /** {@code Valid(0)}, {@code Valid(42)}, {@code Valid(-1)}, {@code Invalid("err")}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Valid(0)", VALIDATED.widen(Validated.<String, Integer>valid(0))),
        Arguments.of("Valid(42)", VALIDATED.widen(Validated.<String, Integer>valid(42))),
        Arguments.of("Valid(-1)", VALIDATED.widen(Validated.<String, Integer>valid(-1))),
        Arguments.of(
            "Invalid(\"err\")", VALIDATED.widen(Validated.<String, Integer>invalid("err"))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
