// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.*;

import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for MutableContext.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Factory methods: io(), pure(), get(), put(), modify()
 *   <li>Chainable operations: map(), via(), flatMap(), then()
 *   <li>Execution methods: runWith(), evalWith(), execWith()
 *   <li>Escape hatch: toStateT()
 * </ul>
 */
@DisplayName("MutableContext")
class MutableContextTest {

  record Counter(int count) {}

  private static final Counter INITIAL_STATE = new Counter(0);
  private static final Integer TEST_VALUE = 42;

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("io() creates state transformation")
    void ioCreatesStateTransformation() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx =
          MutableContext.io(s -> StateTuple.of(new Counter(s.count() + 1), s.count()));

      StateTuple<Counter, Integer> result = ctx.runWith(INITIAL_STATE).unsafeRun();
      assertThat(result.value()).isEqualTo(0);
      assertThat(result.state().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("io() validates non-null computation")
    void ioValidatesComputation() {
      assertThatNullPointerException()
          .isThrownBy(() -> MutableContext.io(null))
          .withMessageContaining("computation must not be null");
    }

    @Test
    @DisplayName("pure() returns value without changing state")
    void pureReturnsValueWithoutChangingState() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(TEST_VALUE);

      StateTuple<Counter, Integer> result = ctx.runWith(INITIAL_STATE).unsafeRun();
      assertThat(result.value()).isEqualTo(TEST_VALUE);
      assertThat(result.state()).isEqualTo(INITIAL_STATE);
    }

    @Test
    @DisplayName("get() returns current state as value")
    void getReturnsCurrentState() {
      MutableContext<IOKind.Witness, Counter, Counter> ctx = MutableContext.get();

      Counter result = ctx.evalWith(INITIAL_STATE).unsafeRun();
      assertThat(result).isEqualTo(INITIAL_STATE);
    }

    @Test
    @DisplayName("put() sets new state")
    void putSetsNewState() {
      Counter newState = new Counter(100);
      MutableContext<IOKind.Witness, Counter, Unit> ctx = MutableContext.put(newState);

      Counter result = ctx.execWith(INITIAL_STATE).unsafeRun();
      assertThat(result).isEqualTo(newState);
    }

    @Test
    @DisplayName("modify() transforms state")
    void modifyTransformsState() {
      MutableContext<IOKind.Witness, Counter, Unit> ctx =
          MutableContext.modify(s -> new Counter(s.count() + 10));

      Counter result = ctx.execWith(INITIAL_STATE).unsafeRun();
      assertThat(result.count()).isEqualTo(10);
    }

    @Test
    @DisplayName("modify() validates non-null modifier")
    void modifyValidatesModifier() {
      assertThatNullPointerException()
          .isThrownBy(() -> MutableContext.modify(null))
          .withMessageContaining("modifier must not be null");
    }
  }

  @Nested
  @DisplayName("Chainable Operations")
  class ChainableOperationsTests {

    @Test
    @DisplayName("map() transforms value")
    void mapTransformsValue() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx =
          MutableContext.<Counter, Integer>pure(21).map(x -> x * 2);

      Integer result = ctx.evalWith(INITIAL_STATE).unsafeRun();
      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("map() validates non-null mapper")
    void mapValidatesMapper() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() chains stateful operations")
    void viaChainsStatefulOperations() {
      MutableContext<IOKind.Witness, Counter, Integer> result =
          MutableContext.<Counter>get()
              .map(Counter::count)
              .via(
                  count ->
                      MutableContext.<Counter, Integer>io(
                          s -> StateTuple.of(new Counter(s.count() + 1), count)));

      StateTuple<Counter, Integer> tuple = result.runWith(INITIAL_STATE).unsafeRun();
      assertThat(tuple.value()).isEqualTo(0);
      assertThat(tuple.state().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("via() validates non-null function")
    void viaValidatesFunction() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.via(null))
          .withMessageContaining("fn must not be null");
    }

    @Test
    @DisplayName("via() throws when function returns wrong context type")
    void viaThrowsOnWrongContextType() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  ctx.via(x -> ErrorContext.success(x.toString()))
                      .runWith(INITIAL_STATE)
                      .unsafeRun())
          .withMessageContaining("via function must return a MutableContext");
    }

    @Test
    @DisplayName("flatMap() chains contexts maintaining state")
    void flatMapChainsContexts() {
      MutableContext<IOKind.Witness, Counter, Integer> result =
          MutableContext.<Counter, Integer>pure(10)
              .flatMap(
                  x -> MutableContext.io(s -> StateTuple.of(new Counter(s.count() + x), x * 2)));

      StateTuple<Counter, Integer> tuple = result.runWith(INITIAL_STATE).unsafeRun();
      assertThat(tuple.value()).isEqualTo(20);
      assertThat(tuple.state().count()).isEqualTo(10);
    }

    @Test
    @DisplayName("then() sequences state operations")
    void thenSequencesOperations() {
      MutableContext<IOKind.Witness, Counter, String> result =
          MutableContext.<Counter>modify(s -> new Counter(s.count() + 1))
              .then(() -> MutableContext.modify(s -> new Counter(s.count() + 1)))
              .then(() -> MutableContext.pure("done"));

      StateTuple<Counter, String> tuple = result.runWith(INITIAL_STATE).unsafeRun();
      assertThat(tuple.value()).isEqualTo("done");
      assertThat(tuple.state().count()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("State Threading")
  class StateThreadingTests {

    @Test
    @DisplayName("state is threaded through chained operations")
    void stateIsThreaded() {
      MutableContext<IOKind.Witness, Counter, Integer> workflow =
          MutableContext.<Counter>modify(s -> new Counter(s.count() + 1))
              .then(() -> MutableContext.modify(s -> new Counter(s.count() + 2)))
              .then(() -> MutableContext.modify(s -> new Counter(s.count() + 3)))
              .then(() -> MutableContext.<Counter>get().map(Counter::count));

      Integer result = workflow.evalWith(new Counter(10)).unsafeRun();
      assertThat(result).isEqualTo(16); // 10 + 1 + 2 + 3
    }

    @Test
    @DisplayName("get-modify-get pattern works correctly")
    void getModifyGetPattern() {
      MutableContext<IOKind.Witness, Counter, String> workflow =
          MutableContext.<Counter>get()
              .map(s -> "before:" + s.count())
              .flatMap(
                  before ->
                      MutableContext.<Counter>modify(s -> new Counter(s.count() + 5))
                          .then(
                              () ->
                                  MutableContext.<Counter>get()
                                      .map(s -> before + ",after:" + s.count())));

      String result = workflow.evalWith(new Counter(10)).unsafeRun();
      assertThat(result).isEqualTo("before:10,after:15");
    }
  }

  @Nested
  @DisplayName("Execution Methods")
  class ExecutionMethodsTests {

    @Test
    @DisplayName("runWith() returns both value and state")
    void runWithReturnsBoth() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx =
          MutableContext.io(s -> StateTuple.of(new Counter(s.count() + 1), s.count()));

      StateTuple<Counter, Integer> result = ctx.runWith(INITIAL_STATE).unsafeRun();
      assertThat(result.value()).isEqualTo(0);
      assertThat(result.state().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("evalWith() returns only value")
    void evalWithReturnsOnlyValue() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(TEST_VALUE);

      Integer result = ctx.evalWith(INITIAL_STATE).unsafeRun();
      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("execWith() returns only state")
    void execWithReturnsOnlyState() {
      MutableContext<IOKind.Witness, Counter, Unit> ctx =
          MutableContext.modify(s -> new Counter(s.count() + 5));

      Counter result = ctx.execWith(INITIAL_STATE).unsafeRun();
      assertThat(result.count()).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("Escape Hatch")
  class EscapeHatchTests {

    @Test
    @DisplayName("toStateT() returns underlying transformer")
    void toStateTReturnsTransformer() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(TEST_VALUE);

      var stateT = ctx.toStateT();

      assertThat(stateT).isNotNull();
    }

    @Test
    @DisplayName("underlying() returns Kind")
    void underlyingReturnsKind() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(TEST_VALUE);

      var underlying = ctx.underlying();

      assertThat(underlying).isNotNull();
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() contains MutableContext")
    void toStringContainsClassName() {
      MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(TEST_VALUE);

      assertThat(ctx.toString()).contains("MutableContext");
    }
  }
}
