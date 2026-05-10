// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.StateTAssert.StateTOptionalAssert;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link StateTAssert}. See {@link AssertContract}. */
@DisplayName("StateTAssert contract")
class StateTAssertContractTest
    extends AssertContract<
        Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>,
        StateTOptionalAssert<Integer, OptionalKind.Witness, String>> {

  private static final OptionalMonad MONAD = OptionalMonad.INSTANCE;

  private static <T> Optional<T> unwrap(Kind<OptionalKind.Witness, T> k) {
    return OPTIONAL.narrow(k);
  }

  // Increments state, produces "v"+s
  private static Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> mkOk() {
    return STATE_T.widen(StateT.create(s -> MONAD.of(StateTuple.of(s + 1, "v" + s)), MONAD));
  }

  // Increments state, produces "diff"+s (different value)
  private static Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> mkOther() {
    return STATE_T.widen(StateT.create(s -> MONAD.of(StateTuple.of(s + 2, "diff" + s)), MONAD));
  }

  // Always returns empty Optional
  private static Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> mkEmpty() {
    return STATE_T.widen(
        StateT.create(s -> OPTIONAL.widen(Optional.<StateTuple<Integer, String>>empty()), MONAD));
  }

  private static final Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> OK = mkOk();
  private static final Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> OTHER =
      mkOther();
  private static final Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> EMPTY =
      mkEmpty();

  @Override
  protected Function<
          Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>,
          StateTOptionalAssert<Integer, OptionalKind.Witness, String>>
      entry() {
    return k -> StateTAssert.assertThatStateT(k, StateTAssertContractTest::unwrap);
  }

  @Override
  protected Stream<
          Row<
              Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>,
              StateTOptionalAssert<Integer, OptionalKind.Witness, String>>>
      rows() {
    return Stream.of(
        passOnly(
            "private constructor accessible via reflection",
            OK,
            a -> {
              try {
                var c = StateTAssert.class.getDeclaredConstructor();
                c.setAccessible(true);
                c.newInstance();
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            }),
        row("whenRunWith.isEmpty", EMPTY, OK, a -> a.whenRunWith(10).isEmpty()),
        row("whenRunWith.isPresent", OK, EMPTY, a -> a.whenRunWith(10).isPresent()),
        row("whenRunWith.hasValue match", OK, OTHER, a -> a.whenRunWith(10).hasValue("v10")),
        row(
            "whenRunWith.hasValue wrong outer state",
            OK,
            EMPTY,
            a -> a.whenRunWith(10).hasValue("v10")),
        row("whenRunWith.hasFinalState match", OK, OTHER, a -> a.whenRunWith(10).hasFinalState(11)),
        row(
            "whenRunWith.hasFinalState wrong outer state",
            OK,
            EMPTY,
            a -> a.whenRunWith(10).hasFinalState(11)),
        row("whenRunWith.hasResult match", OK, OTHER, a -> a.whenRunWith(10).hasResult("v10", 11)),
        row(
            "whenRunWith.hasResult wrong outer state",
            OK,
            EMPTY,
            a -> a.whenRunWith(10).hasResult("v10", 11)),
        passOnly(
            "whenRunWith.satisfiesValue passes",
            OK,
            a -> a.whenRunWith(10).satisfiesValue(v -> {})),
        failOnly(
            "whenRunWith.satisfiesValue inner fails",
            OK,
            a ->
                a.whenRunWith(10)
                    .satisfiesValue(
                        v -> {
                          throw new AssertionError("inner");
                        })),
        passOnly(
            "whenRunWith.satisfiesFinalState passes",
            OK,
            a -> a.whenRunWith(10).satisfiesFinalState(s -> {})),
        failOnly(
            "whenRunWith.satisfiesFinalState inner fails",
            OK,
            a ->
                a.whenRunWith(10)
                    .satisfiesFinalState(
                        s -> {
                          throw new AssertionError("inner");
                        })),
        row(
            "whenRunWith.valueMatches predicate",
            OK,
            OTHER,
            a -> a.whenRunWith(10).valueMatches("v10"::equals)),
        row(
            "whenRunWith.finalStateMatches predicate",
            OK,
            OTHER,
            a -> a.whenRunWith(10).finalStateMatches(s -> s == 11)),
        row("isEqualToStateT match", OK, OTHER, a -> a.isEqualToStateT(OK, 10)),
        failOnly("isEqualToStateT null other", OK, a -> a.isEqualToStateT(null, 10)));
  }

  @Test
  void hasResult_partial_mismatch_value_only() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                StateTAssert.assertThatStateT(OK, StateTAssertContractTest::unwrap)
                    .whenRunWith(10)
                    .hasResult("WRONG", 11));
  }

  @Test
  void hasResult_partial_mismatch_state_only() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                StateTAssert.assertThatStateT(OK, StateTAssertContractTest::unwrap)
                    .whenRunWith(10)
                    .hasResult("v10", 99));
  }
}
