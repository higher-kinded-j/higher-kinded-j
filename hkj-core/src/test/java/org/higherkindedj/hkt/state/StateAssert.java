// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;

/**
 * Custom AssertJ assertions for {@link StateTuple} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code StateTuple}
 * instances, making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.state.StateAssert.assertThatStateTuple;
 *
 * StateTuple<Integer, String> tuple = new StateTuple<>("result", 42);
 * assertThatStateTuple(tuple)
 *     .hasValue("result")
 *     .hasState(42);
 *
 * // Chaining with custom assertions
 * assertThatStateTuple(tuple)
 *     .hasValueSatisfying(value -> {
 *         assertThat(value).startsWith("res");
 *     })
 *     .hasStateSatisfying(state -> {
 *         assertThat(state).isGreaterThan(40);
 *     });
 * }</pre>
 *
 * @param <S> The type of the state
 * @param <A> The type of the value held by the StateTuple
 */
public class StateAssert<S, A> extends AbstractAssert<StateAssert<S, A>, StateTuple<S, A>> {

  /**
   * Creates a new {@code StateAssert} instance.
   *
   * <p>This is the entry point for all StateTuple assertions. Import statically for best
   * readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.state.StateAssert.assertThatStateTuple;
   * }</pre>
   *
   * @param <S> The type of the state
   * @param <A> The type of the value held by the StateTuple
   * @param actual The StateTuple instance to make assertions on
   * @return A new StateAssert instance
   */
  public static <S, A> StateAssert<S, A> assertThatStateTuple(StateTuple<S, A> actual) {
    return new StateAssert<>(actual);
  }

  protected StateAssert(StateTuple<S, A> actual) {
    super(actual, StateAssert.class);
  }

  /**
   * Verifies that the actual {@code StateTuple} has the expected value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * StateTuple<Integer, String> tuple = new StateTuple<>("result", 42);
   * assertThatStateTuple(tuple).hasValue("result");
   * }</pre>
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the value doesn't match
   */
  public StateAssert<S, A> hasValue(A expected) {
    isNotNull();
    A actualValue = actual.value();
    if (!Objects.equals(actualValue, expected)) {
      failWithMessage("Expected StateTuple to have value <%s> but had <%s>", expected, actualValue);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code StateTuple} has the expected state.
   *
   * <p>Example:
   *
   * <pre>{@code
   * StateTuple<Integer, String> tuple = new StateTuple<>("result", 42);
   * assertThatStateTuple(tuple).hasState(42);
   * }</pre>
   *
   * @param expected The expected state
   * @return This assertion object for method chaining
   * @throws AssertionError if the state doesn't match
   */
  public StateAssert<S, A> hasState(S expected) {
    isNotNull();
    S actualState = actual.state();
    if (!Objects.equals(actualState, expected)) {
      failWithMessage("Expected StateTuple to have state <%s> but had <%s>", expected, actualState);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code StateTuple} has a value satisfying the given requirements.
   *
   * <p>This is useful for complex assertions on the value without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * StateTuple<Integer, String> tuple = new StateTuple<>("result", 42);
   * assertThatStateTuple(tuple).hasValueSatisfying(value -> {
   *     assertThat(value).isNotEmpty();
   *     assertThat(value).startsWith("res");
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the requirements are not satisfied
   */
  public StateAssert<S, A> hasValueSatisfying(Consumer<? super A> requirements) {
    isNotNull();
    requirements.accept(actual.value());
    return this;
  }

  /**
   * Verifies that the actual {@code StateTuple} has a state satisfying the given requirements.
   *
   * <p>This is useful for complex assertions on the state without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * StateTuple<Integer, String> tuple = new StateTuple<>("result", 42);
   * assertThatStateTuple(tuple).hasStateSatisfying(state -> {
   *     assertThat(state).isGreaterThan(40);
   *     assertThat(state).isLessThan(50);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the state
   * @return This assertion object for method chaining
   * @throws AssertionError if the requirements are not satisfied
   */
  public StateAssert<S, A> hasStateSatisfying(Consumer<? super S> requirements) {
    isNotNull();
    requirements.accept(actual.state());
    return this;
  }

  /**
   * Verifies that the actual {@code StateTuple} has a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * StateTuple<Integer, String> tuple = new StateTuple<>("result", 42);
   * assertThatStateTuple(tuple).hasValueNonNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the value is null
   */
  public StateAssert<S, A> hasValueNonNull() {
    isNotNull();
    if (actual.value() == null) {
      failWithMessage("Expected StateTuple to have non-null value but was null");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code StateTuple} has a non-null state.
   *
   * <p>Note: StateTuple's constructor already enforces non-null state, so this assertion should
   * always pass for valid StateTuple instances.
   *
   * <p>Example:
   *
   * <pre>{@code
   * StateTuple<Integer, String> tuple = new StateTuple<>("result", 42);
   * assertThatStateTuple(tuple).hasStateNonNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the state is null (should never happen for valid StateTuple)
   */
  public StateAssert<S, A> hasStateNonNull() {
    isNotNull();
    if (actual.state() == null) {
      failWithMessage("Expected StateTuple to have non-null state but was null");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code StateTuple} has a null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * StateTuple<Integer, String> tuple = new StateTuple<>(null, 42);
   * assertThatStateTuple(tuple).hasNullValue();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the value is not null
   */
  public StateAssert<S, A> hasNullValue() {
    isNotNull();
    if (actual.value() != null) {
      failWithMessage("Expected StateTuple to have null value but had <%s>", actual.value());
    }
    return this;
  }
}
