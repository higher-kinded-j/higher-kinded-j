// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the Try type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#kinds")}, so the {@code
 * Success(…)}/{@code Failure} fixture stream is defined once rather than copy-pasted into each
 * test.
 *
 * <p>The {@code Failure} fixture reuses a single exception instance per stream so that record-based
 * {@code Try.Failure} equality holds when a law compares a failure against itself.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class TryLawFixtures {

  private TryLawFixtures() {}

  /**
   * Shared law equality: narrows both Try kinds and compares the resulting {@code Try} values with
   * {@code equals}.
   */
  static final BiPredicate<Kind<TryKind.Witness, ?>, Kind<TryKind.Witness, ?>> EQ =
      KindEquivalence.byEqualsAfter(TRY::narrow);

  /** {@code Success("")}, {@code Success("hi")}, {@code Success("abcdef")}, {@code Failure(ex)}. */
  static Stream<Arguments> kinds() {
    RuntimeException ex = new RuntimeException("law fixture failure");
    return Stream.of(
        Arguments.of("Success(\"\")", TRY.widen(Try.success(""))),
        Arguments.of("Success(\"hi\")", TRY.widen(Try.success("hi"))),
        Arguments.of("Success(\"abcdef\")", TRY.widen(Try.success("abcdef"))),
        Arguments.of("Failure(ex)", TRY.<String>widen(Try.failure(ex))));
  }

  /** Scalar law values {@code {"", "hi", "abcdef"}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(""), Arguments.of("hi"), Arguments.of("abcdef"));
  }
}
