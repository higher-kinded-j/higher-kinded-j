// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateFunctor;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateKindHelper;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for State core type tests.
 *
 * @param <S> The state type
 * @param <A> The value type
 * @param <B> The mapped type
 */
final class StateTestExecutor<S, A, B>
    extends BaseCoreTypeTestExecutor<A, B, StateValidationStage<S, A, B>> {

  private final State<S, A> stateInstance;
  private final S initialState;

  private final boolean includeFactoryMethods;
  private final boolean includeRun;
  private final boolean includeMap;
  private final boolean includeFlatMap;

  StateTestExecutor(
      Class<?> contextClass,
      State<S, A> stateInstance,
      S initialState,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeRun,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        stateInstance,
        initialState,
        mapper,
        includeFactoryMethods,
        includeRun,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        null);
  }

  StateTestExecutor(
      Class<?> contextClass,
      State<S, A> stateInstance,
      S initialState,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeRun,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases,
      StateValidationStage<S, A, B> validationStage) {

    super(contextClass, mapper, includeValidations, includeEdgeCases, validationStage);

    this.stateInstance = stateInstance;
    this.initialState = initialState;
    this.includeFactoryMethods = includeFactoryMethods;
    this.includeRun = includeRun;
    this.includeMap = includeMap;
    this.includeFlatMap = includeFlatMap;
  }

  @Override
  protected void executeOperationTests() {
    if (includeFactoryMethods) testFactoryMethods();
    if (includeRun) testRun();
    if (includeMap && hasMapper()) testMap();
    if (includeFlatMap && hasMapper()) testFlatMap();
  }

  @Override
  protected void executeValidationTests() {
    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Map validations - test through the Functor interface if custom context provided
    if (validationStage != null && validationStage.getMapContext() != null) {
      StateFunctor<S> functor = new StateFunctor<>();
      Kind<StateKind.Witness<S>, A> kind = StateKindHelper.STATE.widen(stateInstance);
      builder.assertMapperNull(() -> functor.map(null, kind), "f", getMapContext(), Operation.MAP);
    } else {
      builder.assertMapperNull(() -> stateInstance.map(null), "f", getMapContext(), Operation.MAP);
    }

    // FlatMap validations - test through the Monad interface if custom context provided
    if (validationStage != null && validationStage.getFlatMapContext() != null) {
      StateMonad<S> monad = new StateMonad<>();
      Kind<StateKind.Witness<S>, A> kind = StateKindHelper.STATE.widen(stateInstance);
      builder.assertFlatMapperNull(
          () -> monad.flatMap(null, kind), "f", getFlatMapContext(), Operation.FLAT_MAP);
    } else {
      builder.assertFlatMapperNull(
          () -> stateInstance.flatMap(null), "f", getFlatMapContext(), Operation.FLAT_MAP);
    }

    // Factory method validations
    builder.assertFunctionNull(() -> State.of(null), "runFunction", contextClass, Operation.OF);
    builder.assertValueNull(() -> State.set(null), "newState", contextClass, Operation.SET);
    builder.assertFunctionNull(() -> State.modify(null), "f", contextClass, Operation.MODIFY);
    builder.assertFunctionNull(() -> State.inspect(null), "f", contextClass, Operation.INSPECT);

    builder.execute();
  }

  @Override
  protected void executeEdgeCaseTests() {
    // Test state threading
    State<S, String> threadedState =
        State.<S>get()
            .flatMap(s -> State.pure("value"))
            .flatMap(v -> State.set(initialState))
            .flatMap(u -> State.get())
            .map(Object::toString);

    StateTuple<S, String> result = threadedState.run(initialState);
    assertThat(result.state()).isNotNull();
    assertThat(result.value()).isNotNull();

    // Test pure leaves state unchanged
    State<S, String> pureState = State.pure("test");
    StateTuple<S, String> pureResult = pureState.run(initialState);
    assertThat(pureResult.state()).isSameAs(initialState);

    // Test get returns current state
    State<S, S> getState = State.get();
    StateTuple<S, S> getResult = getState.run(initialState);
    assertThat(getResult.value()).isSameAs(initialState);
    assertThat(getResult.state()).isSameAs(initialState);

    // Test StateTuple factory method
    StateTuple<S, String> tuple = StateTuple.of(initialState, "value");
    assertThat(tuple.state()).isSameAs(initialState);
    assertThat(tuple.value()).isEqualTo("value");
  }

  private void testFactoryMethods() {
    // Test State.pure
    State<S, String> pureState = State.pure("test");
    StateTuple<S, String> pureResult = pureState.run(initialState);
    assertThat(pureResult.value()).isEqualTo("test");
    assertThat(pureResult.state()).isSameAs(initialState);

    // Test State.get
    State<S, S> getState = State.get();
    StateTuple<S, S> getResult = getState.run(initialState);
    assertThat(getResult.value()).isSameAs(initialState);
    assertThat(getResult.state()).isSameAs(initialState);

    // Test State.set
    State<S, Unit> setState = State.set(initialState);
    StateTuple<S, Unit> setResult = setState.run(initialState);
    assertThat(setResult.value()).isEqualTo(Unit.INSTANCE);
    assertThat(setResult.state()).isSameAs(initialState);

    // Test State.modify
    State<S, Unit> modifyState = State.modify(s -> s);
    StateTuple<S, Unit> modifyResult = modifyState.run(initialState);
    assertThat(modifyResult.value()).isEqualTo(Unit.INSTANCE);

    // Test State.inspect
    State<S, String> inspectState = State.inspect(Object::toString);
    StateTuple<S, String> inspectResult = inspectState.run(initialState);
    assertThat(inspectResult.value()).isNotNull();
    assertThat(inspectResult.state()).isSameAs(initialState);
  }

  private void testRun() {
    // Test run returns StateTuple
    StateTuple<S, A> result = stateInstance.run(initialState);
    assertThat(result).isNotNull();
    assertThat(result.value()).isNotNull();
    assertThat(result.state()).isNotNull();

    // Test run is consistent
    StateTuple<S, A> result2 = stateInstance.run(initialState);
    assertThat(result2.value()).isEqualTo(result.value());
  }

  private void testMap() {
    // Test map application
    State<S, B> mappedState = stateInstance.map(mapper);
    assertThat(mappedState).isNotNull();

    StateTuple<S, B> result = mappedState.run(initialState);
    assertThat(result.value()).isNotNull();

    // Test map composition
    State<S, String> composedState = stateInstance.map(mapper).map(Object::toString);
    assertThat(composedState).isNotNull();
    StateTuple<S, String> composedResult = composedState.run(initialState);
    assertThat(composedResult.value()).isNotNull();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: map test");
    Function<A, B> throwingMapper =
        a -> {
          throw testException;
        };
    State<S, B> throwingState = stateInstance.map(throwingMapper);
    assertThatThrownBy(() -> throwingState.run(initialState)).isSameAs(testException);
  }

  private void testFlatMap() {
    Function<A, State<S, B>> flatMapper = a -> State.pure(mapper.apply(a));

    // Test flatMap application
    State<S, B> flatMappedState = stateInstance.flatMap(flatMapper);
    assertThat(flatMappedState).isNotNull();

    StateTuple<S, B> result = flatMappedState.run(initialState);
    assertThat(result.value()).isNotNull();

    // Test flatMap chaining
    State<S, String> chainedState =
        stateInstance.flatMap(flatMapper).flatMap(b -> State.pure(b.toString()));
    assertThat(chainedState).isNotNull();
    StateTuple<S, String> chainedResult = chainedState.run(initialState);
    assertThat(chainedResult.value()).isNotNull();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: flatMap test");
    Function<A, State<S, B>> throwingFlatMapper =
        a -> {
          throw testException;
        };
    State<S, B> throwingState = stateInstance.flatMap(throwingFlatMapper);
    assertThatThrownBy(() -> throwingState.run(initialState)).isSameAs(testException);

    // Test null result validation
    Function<A, State<S, B>> nullReturningMapper = a -> null;
    State<S, B> nullState = stateInstance.flatMap(nullReturningMapper);
    assertThatThrownBy(() -> nullState.run(initialState))
        .isInstanceOf(KindUnwrapException.class)
        .hasMessageContaining("flatMap")
        .hasMessageContaining("returned null");
  }
}
