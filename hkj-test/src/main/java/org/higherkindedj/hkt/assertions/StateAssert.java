// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Custom AssertJ assertions for {@link StateTuple} instances.
 *
 * @param <S> The type of the state
 * @param <A> The type of the value held by the StateTuple
 */
public class StateAssert<S, A> extends AbstractAssert<StateAssert<S, A>, StateTuple<S, A>> {

  /** Entry point. */
  public static <S, A> StateAssert<S, A> assertThatStateTuple(StateTuple<S, A> actual) {
    return new StateAssert<>(actual);
  }

  protected StateAssert(StateTuple<S, A> actual) {
    super(actual, StateAssert.class);
  }

  /** Verifies that the StateTuple has the expected value. */
  public StateAssert<S, A> hasValue(A expected) {
    isNotNull();
    A actualValue = actual.value();
    Assertions.assertThat(actualValue)
        .withFailMessage(
            "Expected StateTuple to have value <%s> but had <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the StateTuple has the expected state. */
  public StateAssert<S, A> hasState(S expected) {
    isNotNull();
    S actualState = actual.state();
    Assertions.assertThat(actualState)
        .withFailMessage(
            "Expected StateTuple to have state <%s> but had <%s>", expected, actualState)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the StateTuple has a value satisfying the given requirements. */
  public StateAssert<S, A> hasValueSatisfying(Consumer<? super A> requirements) {
    isNotNull();
    requirements.accept(actual.value());
    return this;
  }

  /** Verifies that the StateTuple has a state satisfying the given requirements. */
  public StateAssert<S, A> hasStateSatisfying(Consumer<? super S> requirements) {
    isNotNull();
    requirements.accept(actual.state());
    return this;
  }

  /** Verifies that the StateTuple has a non-null value. */
  public StateAssert<S, A> hasValueNonNull() {
    isNotNull();
    Assertions.assertThat(actual.value()).as("StateTuple value").isNotNull();
    return this;
  }

  /** Verifies that the StateTuple has a non-null state. */
  public StateAssert<S, A> hasStateNonNull() {
    isNotNull();
    Assertions.assertThat(actual.state()).as("StateTuple state").isNotNull();
    return this;
  }

  /** Verifies that the StateTuple has a null value. */
  public StateAssert<S, A> hasNullValue() {
    isNotNull();
    Assertions.assertThat(actual.value()).as("StateTuple value").isNull();
    return this;
  }
}
