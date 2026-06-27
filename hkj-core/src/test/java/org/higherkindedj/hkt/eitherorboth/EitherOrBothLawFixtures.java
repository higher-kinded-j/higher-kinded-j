// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@link EitherOrBoth} type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}. The fixtures span all three cases ({@code Left}, {@code Right}, {@code
 * Both}) so the laws are exercised on every inhabitant.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class EitherOrBothLawFixtures {

  private EitherOrBothLawFixtures() {}

  /** Shared law equality: narrows both kinds and compares the results with {@code equals}. */
  static final BiPredicate<
          Kind<EitherOrBothKind.Witness<String>, ?>, Kind<EitherOrBothKind.Witness<String>, ?>>
      EQ = KindEquivalence.byEqualsAfter(EITHER_OR_BOTH::narrow);

  /** {@code Left("e1")}, {@code Right(42)}, {@code Both("w1", 7)}, {@code Right(-1)}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Left(e1)", EITHER_OR_BOTH.widen(EitherOrBoth.<String, Integer>left("e1"))),
        Arguments.of("Right(42)", EITHER_OR_BOTH.widen(EitherOrBoth.<String, Integer>right(42))),
        Arguments.of(
            "Both(w1,7)", EITHER_OR_BOTH.widen(EitherOrBoth.<String, Integer>both("w1", 7))),
        Arguments.of("Right(-1)", EITHER_OR_BOTH.widen(EitherOrBoth.<String, Integer>right(-1))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
