// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

/**
 * Base class for State type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * State type class tests, eliminating duplication across Functor, Applicative, and Monad tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all State tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_INITIAL_STATE} - The primary initial state value (10)
 *   <li>{@link #ALTERNATIVE_INITIAL_STATE} - A secondary initial state value (100)
 * </ul>
 *
 * <h2>State Type Configuration</h2>
 *
 * <p>Subclasses can override {@link #getInitialState()} and {@link #getAlternativeState()} if they
 * need different state values, but the default implementations work for most tests.
 *
 * <h2>Helper Methods</h2>
 *
 * <p>Provides utility methods for common State operations:
 *
 * <ul>
 *   <li>{@link #runState(Kind, Object)} - Executes a State computation
 *   <li>{@link #evalState(Kind, Object)} - Evaluates and returns only the value
 *   <li>{@link #execState(Kind, Object)} - Executes and returns only the final state
 *   <li>{@link #narrowToState(Kind)} - Converts Kind to State
 * </ul>
 */
abstract class StateTestBase<S> extends TypeClassTestBase<StateKind.Witness<S>, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default initial state value for tests. */
  protected static final Integer DEFAULT_INITIAL_STATE = 10;

  /** Alternative initial state value when testing with multiple states. */
  protected static final Integer ALTERNATIVE_INITIAL_STATE = 100;

  // ============================================================================
  // State Type Configuration - Concrete Implementations
  // ============================================================================

  /**
   * Returns the initial state value to use in tests.
   *
   * <p>Default implementation returns {@link #DEFAULT_INITIAL_STATE} (10). Override if your tests
   * need a different value.
   *
   * @return The initial state value
   */
  @SuppressWarnings("unchecked")
  protected S getInitialState() {
    return (S) DEFAULT_INITIAL_STATE;
  }

  /**
   * Returns an alternative initial state value for tests requiring multiple states.
   *
   * <p>Default implementation returns {@link #ALTERNATIVE_INITIAL_STATE} (100). Override if your
   * tests need a different value.
   *
   * @return The alternative initial state value
   */
  @SuppressWarnings("unchecked")
  protected S getAlternativeState() {
    return (S) ALTERNATIVE_INITIAL_STATE;
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
   * Runs a State computation with the given initial state.
   *
   * @param <A> The type of the computed value
   * @param kind The State computation wrapped in a Kind
   * @param initialState The initial state
   * @return The resulting StateTuple containing the value and final state
   */
  protected <A> StateTuple<S, A> runState(Kind<StateKind.Witness<S>, A> kind, S initialState) {
    return STATE.runState(kind, initialState);
  }

  /**
   * Evaluates a State computation and returns only the computed value.
   *
   * @param <A> The type of the computed value
   * @param kind The State computation wrapped in a Kind
   * @param initialState The initial state
   * @return The computed value
   */
  protected <A> A evalState(Kind<StateKind.Witness<S>, A> kind, S initialState) {
    return STATE.evalState(kind, initialState);
  }

  /**
   * Executes a State computation and returns only the final state.
   *
   * @param <A> The type of the computed value
   * @param kind The State computation wrapped in a Kind
   * @param initialState The initial state
   * @return The final state
   */
  protected <A> S execState(Kind<StateKind.Witness<S>, A> kind, S initialState) {
    return STATE.execState(kind, initialState);
  }

  /**
   * Converts a Kind to a State instance.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * STATE.narrow() calls.
   *
   * @param <A> The type of the value
   * @param kind The Kind to convert
   * @return The underlying State instance
   */
  protected <A> State<S, A> narrowToState(Kind<StateKind.Witness<S>, A> kind) {
    return STATE.narrow(kind);
  }

  // ============================================================================
  // Integer-based State Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<StateKind.Witness<S>, Integer> createValidKind() {
    State<S, Integer> state = State.of(s -> new StateTuple<>(1, s));
    return STATE.widen(state);
  }

  @Override
  protected Kind<StateKind.Witness<S>, Integer> createValidKind2() {
    State<S, Integer> state = State.of(s -> new StateTuple<>(2, s));
    return STATE.widen(state);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<StateKind.Witness<S>, String>> createValidFlatMapper() {
    return i -> STATE.widen(State.pure("flat:" + i));
  }

  @Override
  protected Kind<StateKind.Witness<S>, Function<Integer, String>> createValidFunctionKind() {
    return STATE.widen(State.pure(TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return 42;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return String::toUpperCase;
  }

  @Override
  protected Function<Integer, Kind<StateKind.Witness<S>, String>> createTestFunction() {
    return i -> STATE.widen(State.pure("test:" + i));
  }

  @Override
  protected Function<String, Kind<StateKind.Witness<S>, String>> createChainFunction() {
    return s -> STATE.widen(State.pure(s + "!"));
  }

  @Override
  protected BiPredicate<Kind<StateKind.Witness<S>, ?>, Kind<StateKind.Witness<S>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      StateTuple<S, ?> result1 = STATE.runState(k1, getInitialState());
      StateTuple<S, ?> result2 = STATE.runState(k2, getInitialState());
      return result1.equals(result2);
    };
  }
}
