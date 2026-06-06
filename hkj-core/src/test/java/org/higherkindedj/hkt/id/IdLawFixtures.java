// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the Id type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#kinds")}, so the {@code Id(…)}
 * fixture stream is defined once rather than copy-pasted into each test. Id has a single inhabitant
 * (no empty/error case), so the fixtures are simply a few wrapped values.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class IdLawFixtures {

  private IdLawFixtures() {}

  /**
   * Shared law equality: narrows both Id kinds and compares the wrapped values with {@code equals}.
   */
  static final BiPredicate<Kind<IdKind.Witness, ?>, Kind<IdKind.Witness, ?>> EQ =
      KindEquivalence.byEqualsAfter(ID::narrow);

  /** {@code Id(0)}, {@code Id(42)}, {@code Id(-1)}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Id(0)", ID.widen(Id.of(0))),
        Arguments.of("Id(42)", ID.widen(Id.of(42))),
        Arguments.of("Id(-1)", ID.widen(Id.of(-1))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
