// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateT Core Type Tests ")
// (Outer: OptionalKind.Witness)
class StateTTest {

  private Monad<OptionalKind.Witness> outerMonad;

  private final Integer initialValue = 42;
  private final String initialState = "initial";
  private final String updatedState = "updated";

  private Kind<OptionalKind.Witness, StateTuple<String, Integer>> wrappedResult;
  private Kind<OptionalKind.Witness, StateTuple<String, Integer>> wrappedEmpty;

  @BeforeEach
  void setUp() {
    outerMonad = Instances.monadError(optional());

    wrappedResult = OPTIONAL.widen(Optional.of(StateTuple.of(updatedState, initialValue)));
    wrappedEmpty = OPTIONAL.widen(Optional.empty());
  }

  private <A> Optional<StateTuple<String, A>> unwrapT(
      StateT<String, OptionalKind.Witness, A> stateT) {
    Kind<OptionalKind.Witness, StateTuple<String, A>> outerKind = stateT.runStateT(initialState);
    return OPTIONAL.narrow(outerKind);
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("create should wrap state transition function")
    void create_wrapsFunction() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(updatedState, initialValue));

      StateT<String, OptionalKind.Witness, Integer> stateT = StateT.create(runFn);

      assertThat(stateT).isNotNull();
      assertThat(stateT.runStateTFn()).isSameAs(runFn);
    }

    @Test
    @DisplayName("create should maintain function behaviour")
    void create_maintainsFunctionBehaviour() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s + "_modified", initialValue));

      StateT<String, OptionalKind.Witness, Integer> stateT = StateT.create(runFn);

      Optional<StateTuple<String, Integer>> result = unwrapT(stateT);
      assertThat(result).isPresent();
      assertThat(result.get().state()).isEqualTo("initial_modified");
      assertThat(result.get().value()).isEqualTo(initialValue);
    }
  }

  @Nested
  @DisplayName("Runner Methods")
  class RunnerMethodTests {

    private StateT<String, OptionalKind.Witness, Integer> stateT;

    @BeforeEach
    void setUp() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(updatedState, initialValue));
      stateT = StateT.create(runFn);
    }

    @Test
    @DisplayName("runStateT should execute state transition")
    void runStateT_executesTransition() {
      Kind<OptionalKind.Witness, StateTuple<String, Integer>> result =
          stateT.runStateT(initialState);

      Optional<StateTuple<String, Integer>> unwrapped = OPTIONAL.narrow(result);
      assertThat(unwrapped).isPresent();
      assertThat(unwrapped.get().state()).isEqualTo(updatedState);
      assertThat(unwrapped.get().value()).isEqualTo(initialValue);
    }

    @Test
    @DisplayName("evalStateT should extract value only")
    void evalStateT_extractsValue() {
      Kind<OptionalKind.Witness, Integer> result = stateT.evalStateT(initialState, outerMonad);

      Optional<Integer> unwrapped = OPTIONAL.narrow(result);
      assertThat(unwrapped).isPresent().contains(initialValue);
    }

    @Test
    @DisplayName("execStateT should extract state only")
    void execStateT_extractsState() {
      Kind<OptionalKind.Witness, String> result = stateT.execStateT(initialState, outerMonad);

      Optional<String> unwrapped = OPTIONAL.narrow(result);
      assertThat(unwrapped).isPresent().contains(updatedState);
    }

    @Test
    @DisplayName("runStateT should handle null initial state")
    void runStateT_handlesNullState() {
      // Create a StateT that accepts null state and returns a valid StateTuple
      // The state in the tuple can be null, but we need to handle it properly
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of("non-null-state", 42));
      StateT<String, OptionalKind.Witness, Integer> nullStateT = StateT.create(runFn);

      Kind<OptionalKind.Witness, StateTuple<String, Integer>> result =
          nullStateT.runStateT(null); // Pass null as initial state

      Optional<StateTuple<String, Integer>> unwrapped = OPTIONAL.narrow(result);
      assertThat(unwrapped).isPresent();
      assertThat(unwrapped.get().state()).isEqualTo("non-null-state");
      assertThat(unwrapped.get().value()).isEqualTo(42);
    }

    @Test
    @DisplayName("evalStateT should work with null initial state")
    void evalStateT_worksWithNullState() {
      // Create a StateT that accepts null state and returns a valid StateTuple
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of("non-null-state", 42));
      StateT<String, OptionalKind.Witness, Integer> nullStateT = StateT.create(runFn);

      Kind<OptionalKind.Witness, Integer> result = nullStateT.evalStateT(null, outerMonad);

      Optional<Integer> unwrapped = OPTIONAL.narrow(result);
      assertThat(unwrapped).isPresent().contains(42);
    }

    @Test
    @DisplayName("execStateT should work with null initial state")
    void execStateT_worksWithNullState() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(updatedState, 42)); // Use 42 instead of initialValue
      StateT<String, OptionalKind.Witness, Integer> nullStateT = StateT.create(runFn);

      Kind<OptionalKind.Witness, String> result = nullStateT.execStateT(null, outerMonad);

      Optional<String> unwrapped = OPTIONAL.narrow(result);
      assertThat(unwrapped).isPresent().contains(updatedState);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodTests {

    Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn1;
    Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn2;
    Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn3;

    StateT<String, OptionalKind.Witness, Integer> stateT1;
    StateT<String, OptionalKind.Witness, Integer> stateT2;
    StateT<String, OptionalKind.Witness, Integer> stateT3;

    @BeforeEach
    void setUpObjectTests() {
      runFn1 = s -> outerMonad.of(StateTuple.of("state1", 1));
      runFn2 = s -> outerMonad.of(StateTuple.of("state1", 1));
      runFn3 = s -> outerMonad.of(StateTuple.of("state2", 2));

      stateT1 = StateT.create(runFn1);
      stateT2 = StateT.create(runFn2);
      stateT3 = StateT.create(runFn3);
    }

    @Test
    @DisplayName("equals should compare based on function only")
    void equals_comparesFunction() {
      // Same function references
      StateT<String, OptionalKind.Witness, Integer> sameFn = StateT.create(runFn1);
      assertThat(stateT1).isEqualTo(sameFn);

      // Different function references
      assertThat(stateT1).isNotEqualTo(stateT3);
      assertThat(stateT1).isNotEqualTo(null);
      assertThat(stateT1).isNotEqualTo(runFn1);
    }

    @Test
    @DisplayName("equals should return true for self comparison")
    void equals_selfComparison() {
      assertThat(stateT1.equals(stateT1)).isTrue();
    }

    @Test
    @DisplayName("equals should return false for null comparison")
    void equals_nullComparison() {
      assertThat(stateT1.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals should return false for different type comparison")
    void equals_differentTypeComparison() {
      assertThat(stateT1.equals(new Object())).isFalse();
      assertThat(stateT1.equals(runFn1)).isFalse();
    }

    @Test
    @DisplayName("hashCode should be consistent with equals")
    void hashCode_consistentWithEquals() {
      StateT<String, OptionalKind.Witness, Integer> sameFn = StateT.create(runFn1);
      assertThat(stateT1.hashCode()).isEqualTo(sameFn.hashCode());
    }

    @Test
    @DisplayName("toString should represent the structure")
    void toString_representsStructure() {
      assertThat(stateT1.toString())
          .startsWith("StateT[")
          .contains("runStateTFn=")
          .doesNotContain("monadF=")
          .endsWith("]");
    }
  }

  @Nested
  @DisplayName("Complete Core Type Test Suite")
  class CompleteCoreTypeTests {

    @Test
    @DisplayName("Run complete StateT core type tests")
    void runCompleteStateTTests() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn1 =
          s -> outerMonad.of(StateTuple.of(s + "_1", initialValue));
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn2 =
          s -> outerMonad.of(StateTuple.of(s + "_2", initialValue * 2));

      StateT<String, OptionalKind.Witness, Integer> instance1 = StateT.create(runFn1);
      StateT<String, OptionalKind.Witness, Integer> instance2 = StateT.create(runFn2);

      CoreTypeTest.<String, OptionalKind.Witness, Integer>stateT(StateT.class, outerMonad)
          .withInstance(instance1)
          .withAnotherInstance(instance2)
          .withMappers(Object::toString)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Edge case: function that returns empty outer monad")
    void edgeCase_emptyOuterMonad() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> emptyFn =
          s -> OPTIONAL.widen(Optional.empty());
      StateT<String, OptionalKind.Witness, Integer> emptyStateT =
          StateT.create(emptyFn);

      Optional<StateTuple<String, Integer>> result = unwrapT(emptyStateT);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Edge case: function that uses state parameter")
    void edgeCase_usesStateParameter() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> statefulFn =
          s -> outerMonad.of(StateTuple.of(s.toUpperCase(), s.length()));
      StateT<String, OptionalKind.Witness, Integer> statefulStateT =
          StateT.create(statefulFn);

      Optional<StateTuple<String, Integer>> result = unwrapT(statefulStateT);
      assertThat(result).isPresent();
      assertThat(result.get().state()).isEqualTo("INITIAL");
      assertThat(result.get().value()).isEqualTo(7);
    }

    @Test
    @DisplayName("Edge case: chaining multiple state transitions")
    void edgeCase_chainingTransitions() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn1 =
          s -> outerMonad.of(StateTuple.of(s + "_1", 1));
      StateT<String, OptionalKind.Witness, Integer> stateT1 = StateT.create(runFn1);

      // Execute first transition
      Kind<OptionalKind.Witness, StateTuple<String, Integer>> result1 =
          stateT1.runStateT(initialState);
      Optional<StateTuple<String, Integer>> unwrapped1 = OPTIONAL.narrow(result1);

      assertThat(unwrapped1).isPresent();
      String intermediateState = unwrapped1.get().state();

      // Create second transition using intermediate state
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn2 =
          s -> outerMonad.of(StateTuple.of(s + "_2", 2));
      StateT<String, OptionalKind.Witness, Integer> stateT2 = StateT.create(runFn2);

      // Execute second transition
      Kind<OptionalKind.Witness, StateTuple<String, Integer>> result2 =
          stateT2.runStateT(intermediateState);
      Optional<StateTuple<String, Integer>> unwrapped2 = OPTIONAL.narrow(result2);

      assertThat(unwrapped2).isPresent();
      assertThat(unwrapped2.get().state()).isEqualTo("initial_1_2");
      assertThat(unwrapped2.get().value()).isEqualTo(2);
    }

    @Test
    @DisplayName("Edge case: state transition with null value")
    void edgeCase_nullValue() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> nullValueFn =
          s -> outerMonad.of(StateTuple.of(updatedState, null));
      StateT<String, OptionalKind.Witness, Integer> nullValueStateT =
          StateT.create(nullValueFn);

      Optional<StateTuple<String, Integer>> result = unwrapT(nullValueStateT);
      assertThat(result).isPresent();
      assertThat(result.get().state()).isEqualTo(updatedState);
      assertThat(result.get().value()).isNull();
    }
  }

  // ==========================================================================
  // Issue #445: monadF removed from StateT record components
  // ==========================================================================

  @Nested
  @DisplayName("Record Equality (issue #445)")
  class RecordEqualityTests {

    @Test
    @DisplayName("two StateT instances with the same function should be equal")
    void sameFunctionShouldBeEqual() {
      // monadF is no longer a record component, so equals/hashCode depend only on
      // the state-transition function.
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> fn =
          s -> OPTIONAL.widen(Optional.of(StateTuple.of(s, 42)));

      StateT<String, OptionalKind.Witness, Integer> st1 = new StateT<>(fn);
      StateT<String, OptionalKind.Witness, Integer> st2 = new StateT<>(fn);

      assertThat(st1).isEqualTo(st2);
      assertThat(st1.hashCode()).isEqualTo(st2.hashCode());
    }

    @Test
    @DisplayName("StateT.toString should not include monad instance details")
    void toStringShouldNotLeakMonadDetails() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> fn =
          s -> OPTIONAL.widen(Optional.of(StateTuple.of(s, 42)));
      StateT<String, OptionalKind.Witness, Integer> st = new StateT<>(fn);

      String str = st.toString();
      assertThat(str).isNotNull();
      assertThat(str).doesNotContain("monadF");
    }

    @Test
    @DisplayName("evalStateT should reject null monad")
    void evalStateT_rejectsNullMonad() {
      StateT<String, OptionalKind.Witness, Integer> st =
          StateT.create(s -> outerMonad.of(StateTuple.of(s, 42)));
      assertThatThrownBy(() -> st.evalStateT(initialState, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("execStateT should reject null monad")
    void execStateT_rejectsNullMonad() {
      StateT<String, OptionalKind.Witness, Integer> st =
          StateT.create(s -> outerMonad.of(StateTuple.of(s, 42)));
      assertThatThrownBy(() -> st.execStateT(initialState, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("mapT")
  class MapTTests {

    @Test
    @DisplayName("mapT with identity should return equivalent StateT")
    void mapT_identity() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(updatedState, initialValue));
      StateT<String, OptionalKind.Witness, Integer> st = StateT.create(runFn);

      StateT<String, OptionalKind.Witness, Integer> result =
          st.mapT(outerMonad, Function.identity());

      Optional<StateTuple<String, Integer>> unwrapped = unwrapT(result);
      assertThat(unwrapped).isPresent();
      assertThat(unwrapped.get().value()).isEqualTo(initialValue);
      assertThat(unwrapped.get().state()).isEqualTo(updatedState);
    }

    @Test
    @DisplayName("mapT should transform outer monad to a different type")
    void mapT_crossMonad() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s + "_done", 99));
      StateT<String, OptionalKind.Witness, Integer> st = StateT.create(runFn);

      Monad<IdKind.Witness> idMonad = Instances.monad(id());
      StateT<String, IdKind.Witness, Integer> result =
          st.mapT(
              idMonad,
              optKind -> {
                Optional<StateTuple<String, Integer>> opt = OPTIONAL.narrow(optKind);
                return IdKindHelper.ID.widen(Id.of(opt.orElse(StateTuple.of("empty", -1))));
              });

      // Run the transformed StateT with Id monad
      Kind<IdKind.Witness, StateTuple<String, Integer>> idResult = result.runStateT(initialState);
      Id<StateTuple<String, Integer>> id = IdKindHelper.ID.narrow(idResult);
      assertThat(id.value().value()).isEqualTo(99);
      assertThat(id.value().state()).isEqualTo("initial_done");
    }

    @Test
    @DisplayName("mapT should preserve state threading")
    void mapT_preservesStateThreading() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(s.toUpperCase(), s.length()));
      StateT<String, OptionalKind.Witness, Integer> st = StateT.create(runFn);

      StateT<String, OptionalKind.Witness, Integer> result =
          st.mapT(outerMonad, Function.identity());

      Optional<StateTuple<String, Integer>> unwrapped = unwrapT(result);
      assertThat(unwrapped).isPresent();
      assertThat(unwrapped.get().state()).isEqualTo("INITIAL");
      assertThat(unwrapped.get().value()).isEqualTo(7);
    }

    @Test
    @DisplayName("mapT should preserve empty outer monad")
    void mapT_preservesEmpty() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> emptyFn =
          s -> OPTIONAL.widen(Optional.empty());
      StateT<String, OptionalKind.Witness, Integer> st = StateT.create(emptyFn);

      StateT<String, OptionalKind.Witness, Integer> result =
          st.mapT(outerMonad, Function.identity());

      Optional<StateTuple<String, Integer>> unwrapped = unwrapT(result);
      assertThat(unwrapped).isEmpty();
    }

    @Test
    @DisplayName("mapT should reject null function")
    void mapT_rejectsNullFunction() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(updatedState, initialValue));
      StateT<String, OptionalKind.Witness, Integer> st = StateT.create(runFn);
      assertThatThrownBy(() -> st.mapT(outerMonad, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("mapT should reject null monad")
    void mapT_rejectsNullMonad() {
      Function<String, Kind<OptionalKind.Witness, StateTuple<String, Integer>>> runFn =
          s -> outerMonad.of(StateTuple.of(updatedState, initialValue));
      StateT<String, OptionalKind.Witness, Integer> st = StateT.create(runFn);
      assertThatThrownBy(() -> st.mapT(null, Function.identity()))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
