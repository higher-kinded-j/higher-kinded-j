// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state_t;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for StateT core type tests.
 *
 * <p>This class coordinates test execution by delegating to appropriate test methods.
 *
 * @param <S> The state type
 * @param <F> The outer monad witness type
 * @param <A> The value type
 * @param <B> The mapped type
 */
final class StateTTestExecutor<S, F extends WitnessArity<TypeArity.Unary>, A, B> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final StateT<S, F, A> firstInstance;
  private final StateT<S, F, A> secondInstance;
  private final Function<A, B> mapper;

  private final boolean includeFactoryMethods;
  private final boolean includeRunnerMethods;
  private final boolean includeValidations;
  private final boolean includeEdgeCases;

  private final StateTValidationStage<S, F, A, B> validationStage;

  StateTTestExecutor(
      Class<?> contextClass,
      Monad<F> outerMonad,
      StateT<S, F, A> firstInstance,
      StateT<S, F, A> secondInstance,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeRunnerMethods,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        outerMonad,
        firstInstance,
        secondInstance,
        mapper,
        includeFactoryMethods,
        includeRunnerMethods,
        includeValidations,
        includeEdgeCases,
        null);
  }

  StateTTestExecutor(
      Class<?> contextClass,
      Monad<F> outerMonad,
      StateT<S, F, A> firstInstance,
      StateT<S, F, A> secondInstance,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeRunnerMethods,
      boolean includeValidations,
      boolean includeEdgeCases,
      StateTValidationStage<S, F, A, B> validationStage) {

    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.firstInstance = firstInstance;
    this.secondInstance = secondInstance;
    this.mapper = mapper;
    this.includeFactoryMethods = includeFactoryMethods;
    this.includeRunnerMethods = includeRunnerMethods;
    this.includeValidations = includeValidations;
    this.includeEdgeCases = includeEdgeCases;
    this.validationStage = validationStage;
  }

  void executeAll() {
    if (includeFactoryMethods) testFactoryMethods();
    if (includeRunnerMethods) testRunnerMethods();
    if (includeValidations) testValidations();
    if (includeEdgeCases) testEdgeCases();
  }

  private void testFactoryMethods() {
    // Test create() factory method
    assertThat(firstInstance).isNotNull();
    assertThat(firstInstance.runStateTFn()).isNotNull();
    assertThat(firstInstance.monadF()).isNotNull();

    // Test that created instance is valid
    assertThat(secondInstance).isNotNull();
    assertThat(secondInstance.runStateTFn()).isNotNull();
    assertThat(secondInstance.monadF()).isNotNull();
  }

  private void testRunnerMethods() {
    // We need a test state value - use null as a simple case
    // In real usage, the test would provide appropriate state values
    S testState = null;

    // Test runStateT() - returns Kind<F, StateTuple<S, A>>
    Kind<F, StateTuple<S, A>> result = firstInstance.runStateT(testState);
    assertThat(result).as("runStateT should return non-null result").isNotNull();

    // Test evalStateT() - returns Kind<F, A> (extracts value)
    Kind<F, A> valueResult = firstInstance.evalStateT(testState);
    assertThat(valueResult).as("evalStateT should return non-null result").isNotNull();

    // Test execStateT() - returns Kind<F, S> (extracts state)
    Kind<F, S> stateResult = firstInstance.execStateT(testState);
    assertThat(stateResult).as("execStateT should return non-null result").isNotNull();
  }

  void testValidations() {
    // Determine which class context to use
    Class<?> validationContext =
        (validationStage != null && validationStage.getValidationContext() != null)
            ? validationStage.getValidationContext()
            : contextClass;

    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Test create() null runStateTFn validation - uses FunctionValidator
    builder.assertFunctionNull(
        () -> StateT.create(null, outerMonad),
        "runStateTFn",
        validationContext,
        Operation.CONSTRUCTION);

    // Test create() null monad validation - uses DomainValidator.requireOuterMonad
    builder.assertTransformerOuterMonadNull(
        () -> StateT.create(s -> outerMonad.of(StateTuple.of(s, null)), null),
        validationContext,
        Operation.CONSTRUCTION);

    builder.execute();
  }

  private void testEdgeCases() {
    // Test with null state values
    S nullState = null;

    Kind<F, StateTuple<S, A>> nullStateResult = firstInstance.runStateT(nullState);
    assertThat(nullStateResult).isNotNull();

    Kind<F, A> nullStateValue = firstInstance.evalStateT(nullState);
    assertThat(nullStateValue).isNotNull();

    Kind<F, S> nullStateExtracted = firstInstance.execStateT(nullState);
    assertThat(nullStateExtracted).isNotNull();

    // Test toString
    assertThat(firstInstance.toString()).isNotNull();
    assertThat(secondInstance.toString()).isNotNull();

    // Test equals and hashCode
    StateT<S, F, A> anotherInstance = StateT.create(firstInstance.runStateTFn(), outerMonad);
    assertThat(firstInstance).isEqualTo(anotherInstance);
    assertThat(firstInstance.hashCode()).isEqualTo(anotherInstance.hashCode());
  }
}
