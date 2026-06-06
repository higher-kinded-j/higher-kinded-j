// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the Optional type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#kinds")}, so the {@code
 * Optional.of(…)}/{@code Optional.empty()} fixture stream is defined once rather than copy-pasted
 * into each test.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class OptionalLawFixtures {

  private OptionalLawFixtures() {}

  /**
   * Shared law equality: narrows both Optional kinds and compares the resulting {@code Optional}
   * values with {@code equals}.
   */
  static final BiPredicate<Kind<OptionalKind.Witness, ?>, Kind<OptionalKind.Witness, ?>> EQ =
      KindEquivalence.byEqualsAfter(OPTIONAL::narrow);

  /** {@code Optional.of(0)}, {@code Optional.of(42)}, {@code Optional.of(-1)}, {@code empty}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Optional.of(0)", OPTIONAL.widen(Optional.of(0))),
        Arguments.of("Optional.of(42)", OPTIONAL.widen(Optional.of(42))),
        Arguments.of("Optional.of(-1)", OPTIONAL.widen(Optional.of(-1))),
        Arguments.of("Optional.empty()", OPTIONAL.<Integer>widen(Optional.empty())));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
