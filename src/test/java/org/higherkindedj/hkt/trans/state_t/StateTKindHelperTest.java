// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trans.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.trans.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateTKindHelper Tests (F=Optional, S=Integer)")
class StateTKindHelperTest {

  record Config(String setting) {}

  final Integer initialState = 10;
  private Monad<OptionalKind.Witness> optMonad;
  private StateT<Integer, OptionalKind.Witness, String> baseStateT;

  @BeforeEach
  void setUp() {
    optMonad = new OptionalMonad();
    baseStateT =
        StateT.<Integer, OptionalKind.Witness, String>create(
            s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);
  }

  // Helper to run and unwrap the Optional result
  private <A> Optional<StateTuple<Integer, A>> runOptStateT(
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, A> kind, Integer startState) {
    Kind<OptionalKind.Witness, StateTuple<Integer, A>> resultKind =
        STATE_T.runStateT(kind, startState);
    return OPTIONAL.narrow(resultKind);
  }

  @Nested
  @DisplayName("widen()")
  class WidenTests {
    @Test
    void widen_shouldReturnCorrectKind() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind =
          STATE_T.widen(baseStateT);
      assertThat(kind).isSameAs(baseStateT);
      assertThat(kind).isInstanceOf(StateTKind.class);
      assertThat(kind).isInstanceOf(StateT.class);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> STATE_T.widen(null))
          .withMessageContaining(StateTKindHelper.INVALID_KIND_TYPE_NULL_MSG);
    }
  }

  @Nested
  @DisplayName("narrow()")
  class narrowTests {
    @Test
    void narrow_shouldReturnOriginalStateT() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind = baseStateT;
      assertThat(STATE_T.narrow(kind)).isSameAs(baseStateT);
    }

    record DummyKind<A>() implements Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, A> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> STATE_T.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(StateTKindHelper.INVALID_KIND_NULL_MSG);
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> unknownKind =
          new DummyKind<>();
      String expectedMessage =
          StateTKindHelper.INVALID_KIND_TYPE_MSG + unknownKind.getClass().getName();
      assertThatThrownBy(() -> STATE_T.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(expectedMessage);
    }
  }

  @Nested
  @DisplayName("Factory Helpers")
  class FactoryHelpersTests {

    @Test
    void stateT_shouldCreateAndWrapStateT() {
      Function<Integer, Kind<OptionalKind.Witness, StateTuple<Integer, String>>> runFn =
          s -> optMonad.of(StateTuple.of(s + 5, "State was " + s));
      StateT<Integer, OptionalKind.Witness, String> stateT = STATE_T.stateT(runFn, optMonad);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind = STATE_T.widen(stateT);
      assertThat(runOptStateT(kind, initialState))
          .isPresent()
          .contains(StateTuple.of(15, "State was 10"));
    }

    @Test
    void lift_shouldCreateStateTIgnoringState() {
      Kind<OptionalKind.Witness, String> outerValue = OPTIONAL.widen(Optional.of("Lifted"));
      StateT<Integer, OptionalKind.Witness, String> stateT = STATE_T.lift(optMonad, outerValue);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind = STATE_T.widen(stateT);
      assertThat(runOptStateT(kind, initialState))
          .isPresent()
          .contains(StateTuple.of(10, "Lifted"));
      assertThat(runOptStateT(kind, 50)).isPresent().contains(StateTuple.of(50, "Lifted"));
    }

    @Test
    void lift_shouldCreateStateTWithEmptyOuter() {
      Kind<OptionalKind.Witness, String> outerEmpty = OPTIONAL.widen(Optional.empty());
      StateT<Integer, OptionalKind.Witness, String> stateT = STATE_T.lift(optMonad, outerEmpty);
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind = STATE_T.widen(stateT);
      assertThat(runOptStateT(kind, initialState)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Runner Helpers")
  class RunnerHelpersTests {
    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> baseKind = baseStateT;

    @Test
    void runStateT_shouldExecuteAndReturnResultKind() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kindToTest =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);

      Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultKind =
          STATE_T.runStateT(kindToTest, initialState); // 10
      assertThat(OPTIONAL.narrow(resultKind)).isPresent().contains(StateTuple.of(11, "Val:10"));
    }

    @Test
    void evalStateT_shouldExecuteAndExtractValueKind() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kindToTest =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);

      Kind<OptionalKind.Witness, String> valueKind = STATE_T.evalStateT(kindToTest, initialState);
      assertThat(OPTIONAL.narrow(valueKind)).isPresent().contains("Val:10");
    }

    @Test
    void execStateT_shouldExecuteAndExtractStateKind() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kindToTest =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s)), optMonad);

      Kind<OptionalKind.Witness, Integer> stateKind = STATE_T.execStateT(kindToTest, initialState);
      assertThat(OPTIONAL.narrow(stateKind)).isPresent().contains(11);
    }

    @Test
    void runStateT_shouldHandleOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> emptyKind =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> OPTIONAL.widen(Optional.empty()), optMonad);

      Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultKind =
          STATE_T.runStateT(emptyKind, initialState);
      assertThat(OPTIONAL.narrow(resultKind)).isEmpty();
    }

    @Test
    void evalStateT_shouldHandleOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> emptyKind =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> OPTIONAL.widen(Optional.empty()), optMonad);

      Kind<OptionalKind.Witness, String> valueKind = STATE_T.evalStateT(emptyKind, initialState);
      assertThat(OPTIONAL.narrow(valueKind)).isEmpty();
    }

    @Test
    void execStateT_shouldHandleOuterEmpty() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> emptyKind =
          StateT.create(s -> OPTIONAL.widen(Optional.empty()), optMonad);

      Kind<OptionalKind.Witness, Integer> stateKind = STATE_T.execStateT(emptyKind, initialState);
      assertThat(OPTIONAL.narrow(stateKind)).isEmpty();
    }

    @Test
    void runStateT_shouldThrowClassCastFromBadNarrow() {
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> invalidKind =
          new narrowTests.DummyKind<>();
      String expectedMessage =
          StateTKindHelper.INVALID_KIND_TYPE_MSG + invalidKind.getClass().getName();
      assertThatThrownBy(() -> STATE_T.runStateT(invalidKind, initialState))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(expectedMessage);
    }
  }
}
