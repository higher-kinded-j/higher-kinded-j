// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateTMonadState Test Suite")
class StateTMonadStateTest {

  private Monad<IdKind.Witness> idMonad;
  private MonadState<StateTKind.Witness<Integer, IdKind.Witness>, Integer> monadState;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
    monadState = new StateTMonadState<>(idMonad);
  }

  private <A> StateTuple<Integer, A> runState(
      Kind<StateTKind.Witness<Integer, IdKind.Witness>, A> kind, int initialState) {
    StateT<Integer, IdKind.Witness, A> stateT = StateTKind.narrow(kind);
    Kind<IdKind.Witness, StateTuple<Integer, A>> result = stateT.runStateT(initialState);
    return IdKindHelper.ID.narrow(result).value();
  }

  @Nested
  @DisplayName("Get Tests")
  class GetTests {

    @Test
    @DisplayName("get should return current state as value")
    void get_shouldReturnCurrentState() {
      var result = monadState.get();
      var tuple = runState(result, 42);
      assertThat(tuple.value()).isEqualTo(42);
      assertThat(tuple.state()).isEqualTo(42);
    }

    @Test
    @DisplayName("get should preserve state unchanged")
    void get_shouldPreserveState() {
      var result = monadState.get();
      var tuple = runState(result, 100);
      assertThat(tuple.state()).isEqualTo(100);
    }

    @Test
    @DisplayName("get with different initial states")
    void get_withDifferentStates() {
      var result = monadState.get();
      assertThat(runState(result, 0).value()).isEqualTo(0);
      assertThat(runState(result, -1).value()).isEqualTo(-1);
      assertThat(runState(result, Integer.MAX_VALUE).value()).isEqualTo(Integer.MAX_VALUE);
    }
  }

  @Nested
  @DisplayName("Put Tests")
  class PutTests {

    @Test
    @DisplayName("put should replace state")
    void put_shouldReplaceState() {
      var result = monadState.put(99);
      var tuple = runState(result, 0);
      assertThat(tuple.state()).isEqualTo(99);
    }

    @Test
    @DisplayName("put should return Unit")
    void put_shouldReturnUnit() {
      var result = monadState.put(99);
      var tuple = runState(result, 0);
      assertThat(tuple.value()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("put should ignore initial state")
    void put_shouldIgnoreInitialState() {
      var result = monadState.put(42);
      assertThat(runState(result, 0).state()).isEqualTo(42);
      assertThat(runState(result, 100).state()).isEqualTo(42);
      assertThat(runState(result, -1).state()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Modify Tests")
  class ModifyTests {

    @Test
    @DisplayName("modify should transform state")
    void modify_shouldTransformState() {
      var result = monadState.modify(s -> s * 2);
      var tuple = runState(result, 5);
      assertThat(tuple.state()).isEqualTo(10);
      assertThat(tuple.value()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("modify with identity should not change state")
    void modify_withIdentity() {
      var result = monadState.modify(Function.identity());
      var tuple = runState(result, 42);
      assertThat(tuple.state()).isEqualTo(42);
    }

    @Test
    @DisplayName("modify should compose correctly")
    void modify_shouldCompose() {
      var composed =
          monadState.flatMap(_ -> monadState.modify(s -> s + 10), monadState.modify(s -> s * 2));
      var tuple = runState(composed, 5);
      assertThat(tuple.state()).isEqualTo(20); // (5 * 2) + 10
    }
  }

  @Nested
  @DisplayName("Gets Tests")
  class GetsTests {

    @Test
    @DisplayName("gets should extract value from state")
    void gets_shouldExtractFromState() {
      var result = monadState.gets(s -> "State is " + s);
      var tuple = runState(result, 42);
      assertThat(tuple.value()).isEqualTo("State is 42");
      assertThat(tuple.state()).isEqualTo(42); // state unchanged
    }
  }

  @Nested
  @DisplayName("Inspect Tests")
  class InspectTests {

    @Test
    @DisplayName("inspect should be alias for gets")
    void inspect_shouldBeAliasForGets() {
      Function<Integer, String> f = s -> "val:" + s;
      var getsResult = runState(monadState.gets(f), 10);
      var inspectResult = runState(monadState.inspect(f), 10);
      assertThat(inspectResult.value()).isEqualTo(getsResult.value());
      assertThat(inspectResult.state()).isEqualTo(getsResult.state());
    }
  }

  @Nested
  @DisplayName("MonadState Laws")
  class LawTests {

    @Test
    @DisplayName("Get-put: flatMap(s -> put(s), get()) == of(Unit.INSTANCE)")
    void getPut() {
      var leftSide = monadState.flatMap(s -> monadState.put(s), monadState.get());
      var rightSide = monadState.of(Unit.INSTANCE);

      var leftTuple = runState(leftSide, 42);
      var rightTuple = runState(rightSide, 42);
      assertThat(leftTuple.state()).isEqualTo(rightTuple.state());
      assertThat(leftTuple.value()).isEqualTo(rightTuple.value());
    }

    @Test
    @DisplayName("Put-get: flatMap(_ -> get(), put(s)) == flatMap(_ -> of(s), put(s))")
    void putGet() {
      int s = 99;
      var leftSide = monadState.flatMap(_ -> monadState.get(), monadState.put(s));
      var rightSide = monadState.flatMap(_ -> monadState.of(s), monadState.put(s));

      var leftTuple = runState(leftSide, 0);
      var rightTuple = runState(rightSide, 0);
      assertThat(leftTuple.value()).isEqualTo(rightTuple.value());
      assertThat(leftTuple.state()).isEqualTo(rightTuple.state());
    }

    @Test
    @DisplayName("Put-put: flatMap(_ -> put(s2), put(s1)) == put(s2)")
    void putPut() {
      var leftSide = monadState.flatMap(_ -> monadState.put(200), monadState.put(100));
      var rightSide = monadState.put(200);

      var leftTuple = runState(leftSide, 0);
      var rightTuple = runState(rightSide, 0);
      assertThat(leftTuple.state()).isEqualTo(rightTuple.state());
      assertThat(leftTuple.value()).isEqualTo(rightTuple.value());
    }

    @Test
    @DisplayName("Modify coherence: modify(f) == flatMap(s -> put(f(s)), get())")
    void modifyCoherence() {
      Function<Integer, Integer> f = s -> s * 3;
      var leftSide = monadState.modify(f);
      var rightSide = monadState.flatMap(s -> monadState.put(f.apply(s)), monadState.get());

      var leftTuple = runState(leftSide, 7);
      var rightTuple = runState(rightSide, 7);
      assertThat(leftTuple.state()).isEqualTo(rightTuple.state());
      assertThat(leftTuple.value()).isEqualTo(rightTuple.value());
    }
  }

  @Nested
  @DisplayName("Null Tests")
  class NullTests {

    @Test
    @DisplayName("constructor should reject null monadF")
    void constructor_shouldRejectNullMonad() {
      assertThatThrownBy(() -> new StateTMonadState<Integer, IdKind.Witness>(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("should compose get, modify, and put in sequence")
    void composedStatefulWorkflow() {
      // get the state, double it, add 1, then read the result
      var workflow =
          monadState.flatMap(
              _ ->
                  monadState.flatMap(
                      _ -> monadState.get(), // get final state
                      monadState.modify(s -> s + 1)),
              monadState.modify(s -> s * 2));

      var tuple = runState(workflow, 5);
      assertThat(tuple.value()).isEqualTo(11); // (5 * 2) + 1
      assertThat(tuple.state()).isEqualTo(11);
    }

    @Test
    @DisplayName("should work as a Monad via of and flatMap")
    void shouldWorkAsMonad() {
      // Use monadState as a regular Monad
      var result = monadState.flatMap(x -> monadState.of(x + 10), monadState.of(5));
      var tuple = runState(result, 0);
      assertThat(tuple.value()).isEqualTo(15);
      assertThat(tuple.state()).isEqualTo(0); // state unchanged by pure operations
    }
  }
}
