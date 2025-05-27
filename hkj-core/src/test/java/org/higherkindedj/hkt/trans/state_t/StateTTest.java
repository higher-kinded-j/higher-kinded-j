// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trans.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for the StateT<S, F, A> implementation, specifically with F = OptionalKind<?> and S
 * = Integer.
 */
@DisplayName("StateT<Integer, OptionalKind.Witness, A> Direct Tests") // MODIFIED
class StateTTest {

  private Monad<OptionalKind.Witness> optMonad;
  private final Integer initialState = 10;

  @BeforeEach
  void setUp() {
    optMonad = new OptionalMonad();
  }

  // Helper to run and unwrap the outer Optional
  private <A> Optional<StateTuple<Integer, A>> runOptStateT(
      StateT<Integer, OptionalKind.Witness, A> stateT, Integer startState) {
    Kind<OptionalKind.Witness, StateTuple<Integer, A>> resultKind = stateT.runStateT(startState);
    return OptionalKindHelper.unwrap(resultKind);
  }

  // Helper to run and unwrap just the value from the outer Optional
  private <A> Optional<A> runOptEvalStateT(
      StateT<Integer, OptionalKind.Witness, A> stateT, Integer startState) {
    Kind<OptionalKind.Witness, A> resultKind = stateT.evalStateT(startState);
    return OptionalKindHelper.unwrap(resultKind);
  }

  // Helper to run and unwrap just the state from the outer Optional
  private Optional<Integer> runOptExecStateT(
      StateT<Integer, OptionalKind.Witness, ?> stateT, Integer startState) {
    Kind<OptionalKind.Witness, Integer> resultKind = stateT.execStateT(startState);
    return OptionalKindHelper.unwrap(resultKind);
  }

  @Nested
  @DisplayName("Factory Method: create()")
  class CreateTests {
    @Test
    void create_shouldStoreRunFunctionAndMonad() {
      Function<Integer, Kind<OptionalKind.Witness, StateTuple<Integer, String>>> runFn =
          s -> optMonad.of(StateTuple.of(s + 1, "Val:" + s));
      StateT<Integer, OptionalKind.Witness, String> stateT =
          StateT.<Integer, OptionalKind.Witness, String>create(runFn, optMonad);

      Optional<StateTuple<Integer, String>> expectedResult =
          OptionalKindHelper.unwrap(runFn.apply(initialState));
      Optional<StateTuple<Integer, String>> actualResult =
          OptionalKindHelper.unwrap(stateT.runStateT(initialState));

      assertThat(actualResult).isEqualTo(expectedResult);
      assertThat(runOptEvalStateT(stateT, initialState)).isPresent().contains("Val:10");
      assertThat(runOptExecStateT(stateT, initialState)).isPresent().contains(11);
    }

    @Test
    void create_shouldThrowNPEForNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> StateT.create(null, optMonad))
          .withMessageContaining("runStateTFn cannot be null");
    }

    @Test
    void create_shouldThrowNPEForNullMonad() {
      Function<Integer, Kind<OptionalKind.Witness, StateTuple<Integer, String>>> runFn =
          s -> optMonad.of(StateTuple.of(s, "val"));
      assertThatNullPointerException()
          .isThrownBy(() -> StateT.create(runFn, null))
          .withMessageContaining("monadF cannot be null");
    }
  }

  @Nested
  @DisplayName("Instance Methods: runStateT, evalStateT, execStateT")
  class RunEvalExecTests {

    private StateT<Integer, OptionalKind.Witness, String> stateT_Inc;
    private StateT<Integer, OptionalKind.Witness, String> stateT_Empty;

    @BeforeEach
    void setUpNested() {
      stateT_Inc =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> optMonad.of(StateTuple.of(s + 1, "V" + s)), optMonad);
      stateT_Empty =
          StateT.<Integer, OptionalKind.Witness, String>create(
              s -> OptionalKindHelper.wrap(Optional.empty()), optMonad);
    }

    @Test
    void runStateT_shouldExecuteFunctionAndReturnResultKind() {
      Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultKindInc =
          stateT_Inc.runStateT(initialState); // 10
      assertThat(OptionalKindHelper.unwrap(resultKindInc))
          .isPresent()
          .contains(StateTuple.of(11, "V10"));

      Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultKindEmpty =
          stateT_Empty.runStateT(initialState);
      assertThat(OptionalKindHelper.unwrap(resultKindEmpty)).isEmpty();
    }

    @Test
    void evalStateT_shouldExecuteAndExtractValue() {
      assertThat(runOptEvalStateT(stateT_Inc, initialState)).isPresent().contains("V10");
      assertThat(runOptEvalStateT(stateT_Empty, initialState)).isEmpty();
    }

    @Test
    void execStateT_shouldExecuteAndExtractState() {
      assertThat(runOptExecStateT(stateT_Inc, initialState)).isPresent().contains(11);
      assertThat(runOptExecStateT(stateT_Empty, initialState)).isEmpty();
    }
  }
}
