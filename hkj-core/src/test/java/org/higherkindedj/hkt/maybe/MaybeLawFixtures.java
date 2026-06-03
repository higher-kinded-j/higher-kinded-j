// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the Maybe type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#kinds")}, so the {@code
 * Just(…)}/{@code Nothing} fixture stream is defined once rather than copy-pasted into each test.
 */
// Referenced only via fully-qualified @MethodSource strings, which IntelliJ's usage search misses.
@SuppressWarnings("unused")
final class MaybeLawFixtures {

  private MaybeLawFixtures() {}

  /** {@code Just(0)}, {@code Just(42)}, {@code Just(-1)}, {@code Nothing}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Just(0)", MAYBE.widen(Maybe.just(0))),
        Arguments.of("Just(42)", MAYBE.widen(Maybe.just(42))),
        Arguments.of("Just(-1)", MAYBE.widen(Maybe.just(-1))),
        Arguments.of("Nothing", MAYBE.<Integer>widen(Maybe.nothing())));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
