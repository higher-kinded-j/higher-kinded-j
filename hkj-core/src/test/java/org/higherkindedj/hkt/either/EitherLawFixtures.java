// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the Either type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.either.EitherLawFixtures#kinds")}. Centralising the
 * fixture streams keeps every algebra's law verification driven by the same inputs and removes the
 * copy-pasted {@code fixtures()}/{@code values()} providers that previously lived in each test.
 */
// Referenced only via fully-qualified @MethodSource strings, which IntelliJ's usage search misses.
@SuppressWarnings("unused")
final class EitherLawFixtures {

  private EitherLawFixtures() {}

  /** {@code Right(0)}, {@code Right(42)}, {@code Right(-1)}, {@code Left("err")}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Right(0)", EITHER.widen(Either.<String, Integer>right(0))),
        Arguments.of("Right(42)", EITHER.widen(Either.<String, Integer>right(42))),
        Arguments.of("Right(-1)", EITHER.widen(Either.<String, Integer>right(-1))),
        Arguments.of("Left(\"err\")", EITHER.widen(Either.<String, Integer>left("err"))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
