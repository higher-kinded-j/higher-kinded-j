// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code Trampoline} type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#kinds")}, so the
 * fixture stream is defined once rather than copy-pasted into each test. {@code Trampoline} defers
 * its computation, so the {@link #EQ} checker runs both trampolines and compares the results —
 * deferred ({@code More}) fixtures are therefore fine.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class TrampolineLawFixtures {

  private TrampolineLawFixtures() {}

  /** Run both trampolines and compare their results. */
  static final BiPredicate<Kind<TrampolineKind.Witness, ?>, Kind<TrampolineKind.Witness, ?>> EQ =
      (k1, k2) -> Objects.equals(TRAMPOLINE.narrow(k1).run(), TRAMPOLINE.narrow(k2).run());

  /**
   * Representative trampolines: completed {@code done(0/42/-1)} plus a deferred {@code More} that
   * resolves to {@code done(7)}.
   */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("done(0)", TRAMPOLINE.widen(Trampoline.done(0))),
        Arguments.of("done(42)", TRAMPOLINE.widen(Trampoline.done(42))),
        Arguments.of("done(-1)", TRAMPOLINE.widen(Trampoline.done(-1))),
        Arguments.of(
            "defer(done(7))", TRAMPOLINE.widen(Trampoline.defer(() -> Trampoline.done(7)))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }

  /** Scalar law strings {@code {"", "a", "trampoline"}}. */
  static Stream<Arguments> strings() {
    return Stream.of(Arguments.of(""), Arguments.of("a"), Arguments.of("trampoline"));
  }
}
