// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.state.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for WithStatePath.
 *
 * <p>Tests cover factory methods, state threading, Composable/Combinable/Chainable operations, and
 * conversions.
 */
@DisplayName("WithStatePath<S, A> Complete Test Suite")
class WithStatePathTest {

  private static final String TEST_VALUE = "test";

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.statePure() creates path with unchanged state")
    void statePureCreatesPathWithUnchangedState() {
      WithStatePath<Integer, String> path = Path.statePure(TEST_VALUE);

      var result = path.run(0);
      assertThat(result.value()).isEqualTo(TEST_VALUE);
      assertThat(result.state()).isEqualTo(0);
    }

    @Test
    @DisplayName("Path.getState() returns current state")
    void getStateReturnsCurrentState() {
      WithStatePath<Integer, Integer> path = Path.getState();

      var result = path.run(42);
      assertThat(result.value()).isEqualTo(42);
      assertThat(result.state()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.setState() replaces state")
    void setStateReplacesState() {
      WithStatePath<Integer, Unit> path = Path.setState(100);

      var result = path.run(0);
      assertThat(result.value()).isEqualTo(Unit.INSTANCE);
      assertThat(result.state()).isEqualTo(100);
    }

    @Test
    @DisplayName("Path.modifyState() transforms state")
    void modifyStateTransformsState() {
      WithStatePath<Integer, Unit> path = Path.modifyState(n -> n * 2);

      var result = path.run(21);
      assertThat(result.value()).isEqualTo(Unit.INSTANCE);
      assertThat(result.state()).isEqualTo(42);
    }

    @Test
    @DisplayName("WithStatePath.pure() creates constant value path")
    void staticPureCreatesConstantPath() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      var result = path.run(99);
      assertThat(result.value()).isEqualTo(TEST_VALUE);
      assertThat(result.state()).isEqualTo(99);
    }

    @Test
    @DisplayName("WithStatePath.get() returns state as value")
    void staticGetReturnsState() {
      WithStatePath<String, String> path = WithStatePath.get();

      var result = path.run("hello");
      assertThat(result.value()).isEqualTo("hello");
      assertThat(result.state()).isEqualTo("hello");
    }

    @Test
    @DisplayName("WithStatePath.set() sets new state")
    void staticSetSetsNewState() {
      WithStatePath<String, Unit> path = WithStatePath.set("new");

      var result = path.run("old");
      assertThat(result.state()).isEqualTo("new");
    }

    @Test
    @DisplayName("WithStatePath.modify() modifies state")
    void staticModifyModifiesState() {
      WithStatePath<Integer, Unit> path = WithStatePath.modify(n -> n + 1);

      var result = path.run(5);
      assertThat(result.state()).isEqualTo(6);
    }

    @Test
    @DisplayName("WithStatePath.inspect() extracts from state")
    void staticInspectExtractsFromState() {
      record AppState(String name, int count) {}
      WithStatePath<AppState, String> path = WithStatePath.inspect(AppState::name);

      var result = path.run(new AppState("test", 42));
      assertThat(result.value()).isEqualTo("test");
    }
  }

  @Nested
  @DisplayName("Run and Terminal Methods")
  class RunAndTerminalMethodsTests {

    @Test
    @DisplayName("run() returns both value and final state")
    void runReturnsBothValueAndState() {
      WithStatePath<Integer, String> path =
          WithStatePath.<Integer>modify(n -> n + 1).then(() -> WithStatePath.pure("done"));

      var result = path.run(0);
      assertThat(result.value()).isEqualTo("done");
      assertThat(result.state()).isEqualTo(1);
    }

    @Test
    @DisplayName("run() validates non-null initial state")
    void runValidatesNonNullInitialState() {
      WithStatePath<String, String> path = WithStatePath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.run(null))
          .withMessageContaining("initialState must not be null");
    }

    @Test
    @DisplayName("evalState() returns only value")
    void evalStateReturnsOnlyValue() {
      WithStatePath<Integer, String> path = WithStatePath.pure("result");

      assertThat(path.evalState(0)).isEqualTo("result");
    }

    @Test
    @DisplayName("execState() returns only final state")
    void execStateReturnsOnlyState() {
      WithStatePath<Integer, Unit> path = WithStatePath.modify(n -> n + 10);

      assertThat(path.execState(5)).isEqualTo(15);
    }

    @Test
    @DisplayName("toState() returns underlying State")
    void toStateReturnsUnderlyingState() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      assertThat(path.toState()).isNotNull();
      assertThat(path.toState().run(0).value()).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value preserving state")
    void mapTransformsValuePreservingState() {
      WithStatePath<Integer, Integer> path = WithStatePath.<Integer>get().map(n -> n * 2);

      var result = path.run(21);
      assertThat(result.value()).isEqualTo(42);
      assertThat(result.state()).isEqualTo(21);
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      WithStatePath<Integer, String> path =
          WithStatePath.<Integer, String>pure("hello").map(String::toUpperCase).map(s -> s + "!");

      assertThat(path.evalState(0)).isEqualTo("HELLO!");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      WithStatePath<Integer, String> result = path.peek(v -> called.set(true));

      assertThat(called).isFalse(); // Not called until run
      assertThat(result.evalState(0)).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains stateful computations")
    void viaChainsStatefulComputations() {
      WithStatePath<Integer, Integer> path =
          WithStatePath.<Integer>modify(n -> n + 1)
              .then(WithStatePath::<Integer>get)
              .via(
                  n -> WithStatePath.<Integer>modify(s -> s + n).then(WithStatePath::<Integer>get));

      var result = path.run(0);
      assertThat(result.value()).isEqualTo(2); // 0+1=1, 1+1=2
      assertThat(result.state()).isEqualTo(2);
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates non-null result")
    void viaValidatesNonNullResult() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(s -> null).run(0))
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() validates result is WithStatePath")
    void viaValidatesResultType() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.just(s)).run(0))
          .withMessageContaining("via mapper must return WithStatePath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      WithStatePath<Integer, String> path = WithStatePath.pure("hello");

      WithStatePath<Integer, Integer> viaResult = path.via(s -> WithStatePath.pure(s.length()));
      @SuppressWarnings("unchecked")
      WithStatePath<Integer, Integer> flatMapResult =
          (WithStatePath<Integer, Integer>) path.flatMap(s -> WithStatePath.pure(s.length()));

      assertThat(flatMapResult.evalState(0)).isEqualTo(viaResult.evalState(0));
    }

    @Test
    @DisplayName("then() sequences computations threading state")
    void thenSequencesWithThreadedState() {
      WithStatePath<Integer, String> path =
          WithStatePath.<Integer>modify(n -> n + 1)
              .then(() -> WithStatePath.<Integer>modify(n -> n * 2))
              .then(() -> WithStatePath.pure("done"));

      var result = path.run(5);
      assertThat(result.value()).isEqualTo("done");
      assertThat(result.state()).isEqualTo(12); // (5+1)*2 = 12
    }

    @Test
    @DisplayName("then() throws for incompatible path type")
    void thenThrowsForIncompatibleType() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.then(() -> Path.id(42)).run(0))
          .withMessageContaining("then supplier must return WithStatePath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines values threading state")
    void zipWithCombinesValuesThreadingState() {
      WithStatePath<Integer, Integer> first =
          WithStatePath.<Integer>modify(n -> n + 1).then(WithStatePath::<Integer>get);
      WithStatePath<Integer, Integer> second =
          WithStatePath.<Integer>modify(n -> n + 1).then(WithStatePath::<Integer>get);

      WithStatePath<Integer, Integer> result = first.zipWith(second, Integer::sum);

      var output = result.run(0);
      assertThat(output.value()).isEqualTo(3); // 1 + 2
      assertThat(output.state()).isEqualTo(2);
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(WithStatePath.pure("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-WithStatePath")
    void zipWithThrowsWhenGivenNonWithStatePath() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);
      IdPath<Integer> idPath = Path.id(42);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(idPath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-WithStatePath");
    }

    @Test
    @DisplayName("zipWith3() combines three values")
    void zipWith3CombinesThreeValues() {
      WithStatePath<Integer, String> first = WithStatePath.pure("a");
      WithStatePath<Integer, String> second = WithStatePath.pure("b");
      WithStatePath<Integer, String> third = WithStatePath.pure("c");

      WithStatePath<Integer, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.evalState(0)).isEqualTo("abc");
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toIOPath() converts to IOPath with initial state")
    void toIOPathConvertsCorrectly() {
      WithStatePath<Integer, String> path = WithStatePath.<Integer>get().map(n -> "state=" + n);

      IOPath<String> result = path.toIOPath(42);

      assertThat(result.unsafeRun()).isEqualTo("state=42");
    }

    @Test
    @DisplayName("toIdPath() converts to IdPath with initial state")
    void toIdPathConvertsCorrectly() {
      WithStatePath<Integer, Integer> path = WithStatePath.get();

      IdPath<Integer> result = path.toIdPath(42);

      assertThat(result.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("toMaybePath() converts to MaybePath with Just")
    void toMaybePathConvertsToJust() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      MaybePath<String> result = path.toMaybePath(0);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts to Nothing for null")
    void toMaybePathConvertsToNothing() {
      WithStatePath<Integer, String> path = WithStatePath.pure(null);

      MaybePath<String> result = path.toMaybePath(0);

      assertThat(result.run().isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      WithStatePath<Integer, String> path = WithStatePath.pure("test");

      assertThat(path).isEqualTo(path);
    }

    @Test
    @DisplayName("equals() returns false for non-WithStatePath")
    void equalsReturnsFalseForNonWithStatePath() {
      WithStatePath<Integer, String> path = WithStatePath.pure("test");

      assertThat(path.equals("not a WithStatePath")).isFalse();
      assertThat(path.equals(null)).isFalse();
      assertThat(path.equals(Path.id(42))).isFalse();
    }

    @Test
    @DisplayName("equals() compares underlying state")
    void equalsComparesUnderlyingState() {
      // Two different WithStatePath instances wrapping the same State
      State<Integer, String> state = State.pure("test");
      WithStatePath<Integer, String> path1 = Path.state(state);
      WithStatePath<Integer, String> path2 = Path.state(state);

      assertThat(path1).isEqualTo(path2);
    }

    @Test
    @DisplayName("hashCode() returns consistent value")
    void hashCodeReturnsConsistentValue() {
      WithStatePath<Integer, String> path = WithStatePath.pure("test");

      int hash1 = path.hashCode();
      int hash2 = path.hashCode();

      assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("hashCode() based on underlying state")
    void hashCodeBasedOnUnderlyingState() {
      State<Integer, String> state = State.pure("test");
      WithStatePath<Integer, String> path1 = Path.state(state);
      WithStatePath<Integer, String> path2 = Path.state(state);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      WithStatePath<Integer, String> path = WithStatePath.pure(TEST_VALUE);

      assertThat(path.toString()).contains("WithStatePath");
    }
  }

  @Nested
  @DisplayName("Practical Usage Patterns")
  class PracticalUsagePatternsTests {

    @Test
    @DisplayName("Can generate sequential IDs")
    void canGenerateSequentialIds() {
      WithStatePath<Integer, Integer> nextId =
          WithStatePath.<Integer>get()
              .via(id -> WithStatePath.<Integer>set(id + 1).then(() -> WithStatePath.pure(id)));

      // Generate 3 IDs starting from 1
      WithStatePath<Integer, List<Integer>> threeIds =
          nextId.via(id1 -> nextId.via(id2 -> nextId.map(id3 -> List.of(id1, id2, id3))));

      var result = threeIds.run(1);
      assertThat(result.value()).containsExactly(1, 2, 3);
      assertThat(result.state()).isEqualTo(4);
    }

    @Test
    @DisplayName("Can build up structure through state")
    void canBuildUpStructureThroughState() {
      Function<String, WithStatePath<List<String>, Unit>> addLog =
          msg ->
              WithStatePath.modify(
                  log -> {
                    var newLog = new ArrayList<>(log);
                    newLog.add(msg);
                    return newLog;
                  });

      WithStatePath<List<String>, String> computation =
          addLog
              .apply("Starting")
              .then(() -> addLog.apply("Processing"))
              .then(() -> addLog.apply("Done"))
              .then(() -> WithStatePath.pure("result"));

      var result = computation.run(List.of());
      assertThat(result.value()).isEqualTo("result");
      assertThat(result.state()).containsExactly("Starting", "Processing", "Done");
    }
  }
}
