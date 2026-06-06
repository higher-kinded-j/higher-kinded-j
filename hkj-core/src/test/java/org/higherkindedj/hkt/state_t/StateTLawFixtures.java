// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code StateT} type-class tests (inner monad = {@code Optional},
 * state = {@code String}).
 *
 * <p>Referenced from the {@code *LawTests} blocks via a fully-qualified {@code @MethodSource}. The
 * kinds span the transformer states — pure (state-preserving), state-modifying, and an empty outer
 * {@code Optional} — so each law is verified over all of them. Equality runs both sides against a
 * fixed initial state.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class StateTLawFixtures {

  private StateTLawFixtures() {}

  private static final Monad<OptionalKind.Witness> OUTER = Instances.monadError(optional());

  private static final String INITIAL = "initial";

  private static <A> Optional<StateTuple<String, A>> unwrapKind(
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> kind) {
    var stateT = STATE_T.narrow(kind);
    Kind<OptionalKind.Witness, StateTuple<String, A>> outerKind = stateT.runStateT(INITIAL);
    return OPTIONAL.narrow(outerKind);
  }

  /**
   * Shared law equality: run/unwrap to {@code Optional<StateTuple<String, ·>>} against a fixed
   * initial state and compare.
   */
  static final BiPredicate<
          Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>,
          Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>>
      EQ = KindEquivalence.byEqualsAfter(StateTLawFixtures::unwrapKind);

  private static <A> StateT<String, OptionalKind.Witness, A> createStateT(
      Function<String, StateTuple<String, A>> fn) {
    return StateT.create(s -> OUTER.of(fn.apply(s)), OUTER);
  }

  private static Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> pureT(int value) {
    return STATE_T.widen(createStateT(s -> StateTuple.of(s, value)));
  }

  @SuppressWarnings("SameParameterValue") // one state-modifying fixture today; kept parameterised
  private static Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> stateModifyingT(
      int value) {
    return STATE_T.widen(createStateT(s -> StateTuple.of(s + "_mod", value)));
  }

  private static Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> emptyT() {
    return STATE_T.widen(StateT.create(s -> OPTIONAL.widen(Optional.empty()), OUTER));
  }

  /** pure(0/42/-1), a state-modifying StateT, and an empty outer {@code Optional}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("pureT(0)", pureT(0)),
        Arguments.of("pureT(42)", pureT(42)),
        Arguments.of("pureT(-1)", pureT(-1)),
        Arguments.of("stateModifyingT", stateModifyingT(7)),
        Arguments.of("emptyT", emptyT()));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }

  /** Scalar law strings {@code {"a", "hello"}}. */
  static Stream<Arguments> strings() {
    return Stream.of(Arguments.of("a"), Arguments.of("hello"));
  }
}
