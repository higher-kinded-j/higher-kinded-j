// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the Lazy type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.lazy.LazyLawFixtures#kinds")}, so the {@code
 * Lazy.defer(…)} fixture stream is defined once rather than copy-pasted into each test. The law
 * equality checker forces both sides, so deferred fixtures are fine.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class LazyLawFixtures {

  private LazyLawFixtures() {}

  /** Forces both sides and compares — Lazy equality is value-based after evaluation. */
  static final BiPredicate<Kind<LazyKind.Witness, ?>, Kind<LazyKind.Witness, ?>> EQ =
      (k1, k2) -> {
        try {
          return Objects.equals(LAZY.force(k1), LAZY.force(k2));
        } catch (Throwable e) {
          return false;
        }
      };

  /** {@code Lazy.defer(0)}, {@code Lazy.defer(42)}, {@code Lazy.defer(-1)}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Lazy.defer(0)", LAZY.widen(Lazy.defer(() -> 0))),
        Arguments.of("Lazy.defer(42)", LAZY.widen(Lazy.defer(() -> 42))),
        Arguments.of("Lazy.defer(-1)", LAZY.widen(Lazy.defer(() -> -1))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
