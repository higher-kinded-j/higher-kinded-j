// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the State type-class tests (Integer state).
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.state.StateLawFixtures#kinds")}. Centralising the
 * fixture streams keeps every algebra's law verification driven by the same inputs and removes the
 * copy-pasted {@code fixtures()}/{@code values()} providers that previously lived in each test. A
 * State is a function, so the law equality checker runs both sides against a fixed initial state.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class StateLawFixtures {

  private StateLawFixtures() {}

  /** Fixed initial state the law equality checker runs both sides against. */
  static final int INITIAL = 7;

  /**
   * State equality: run both sides against the fixed {@link #INITIAL} state and compare the
   * resulting {@code StateTuple}s. Shared by the unit law blocks and the property test.
   */
  static final BiPredicate<Kind<StateKind.Witness<Integer>, ?>, Kind<StateKind.Witness<Integer>, ?>>
      EQ = (k1, k2) -> Objects.equals(STATE.runState(k1, INITIAL), STATE.runState(k2, INITIAL));

  /** {@code State(s -> (s, 42))}, {@code State(s -> (s+1, s))}, {@code State(s -> (s*2, -s))}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of(
            "State(s -> (s, 42))",
            STATE.widen(State.<Integer, Integer>of(s -> new StateTuple<>(s, 42)))),
        Arguments.of(
            "State(s -> (s+1, s))",
            STATE.widen(State.<Integer, Integer>of(s -> new StateTuple<>(s + 1, s)))),
        Arguments.of(
            "State(s -> (s*2, -s))",
            STATE.widen(State.<Integer, Integer>of(s -> new StateTuple<>(s * 2, -s)))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
