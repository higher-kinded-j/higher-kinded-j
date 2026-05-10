// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.higherkindedj.hkt.state_t.StateTKindHelper;

/** Custom AssertJ assertion for StateT transformer types. */
public final class StateTAssert {

  private StateTAssert() {}

  /** Entry point for StateT assertions when the outer monad unwraps to Optional. */
  public static <S, F extends WitnessArity<TypeArity.Unary>, A>
      StateTOptionalAssert<S, F, A> assertThatStateT(
          Kind<StateTKind.Witness<S, F>, A> actual,
          Function<Kind<F, StateTuple<S, A>>, Optional<StateTuple<S, A>>> outerUnwrapper) {
    return new StateTOptionalAssert<>(actual, outerUnwrapper);
  }

  /** Specialised assertion for StateT with Optional-shaped outer monad. */
  public static class StateTOptionalAssert<S, F extends WitnessArity<TypeArity.Unary>, A>
      extends AbstractAssert<StateTOptionalAssert<S, F, A>, Kind<StateTKind.Witness<S, F>, A>> {

    private final Function<Kind<F, StateTuple<S, A>>, Optional<StateTuple<S, A>>> outerUnwrapper;

    protected StateTOptionalAssert(
        Kind<StateTKind.Witness<S, F>, A> actual,
        Function<Kind<F, StateTuple<S, A>>, Optional<StateTuple<S, A>>> outerUnwrapper) {
      super(actual, StateTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    /** Runs the StateT computation and returns a result assertion. */
    public StateTResultAssert<S, F, A> whenRunWith(S initialState) {
      isNotNull();
      var stateT = StateTKindHelper.STATE_T.narrow(actual);
      Optional<StateTuple<S, A>> unwrapped = outerUnwrapper.apply(stateT.runStateT(initialState));
      return new StateTResultAssert<>(unwrapped, initialState);
    }

    /** Verifies that this StateT is equal to another for the given initial state. */
    public StateTOptionalAssert<S, F, A> isEqualToStateT(
        Kind<StateTKind.Witness<S, F>, A> other, S initialState) {
      isNotNull();
      Assertions.assertThat(other).as("other StateT").isNotNull();
      var thisStateT = StateTKindHelper.STATE_T.narrow(actual);
      Optional<StateTuple<S, A>> thisResult =
          outerUnwrapper.apply(thisStateT.runStateT(initialState));
      var otherStateT = StateTKindHelper.STATE_T.narrow(other);
      Optional<StateTuple<S, A>> otherResult =
          outerUnwrapper.apply(otherStateT.runStateT(initialState));
      Assertions.assertThat(thisResult)
          .withFailMessage(
              "Expected StateT to be equal to <%s> but was <%s> for initial state <%s>",
              otherResult, thisResult, initialState)
          .isEqualTo(otherResult);
      return this;
    }
  }

  /**
   * Assertion class for the result of running a StateT computation with a specific initial state.
   *
   * <p>Extends {@link AbstractAssert} so AssertJ's standard fluent features ({@code as}, {@code
   * describedAs}, {@code overridingErrorMessage}) are available on the unwrapped result.
   */
  public static class StateTResultAssert<S, F extends WitnessArity<TypeArity.Unary>, A>
      extends AbstractAssert<StateTResultAssert<S, F, A>, Optional<StateTuple<S, A>>> {

    private final S initialState;

    protected StateTResultAssert(Optional<StateTuple<S, A>> result, S initialState) {
      super(result, StateTResultAssert.class);
      this.initialState = initialState;
    }

    /** Verifies that the result is empty (outer monad was empty). */
    public StateTResultAssert<S, F, A> isEmpty() {
      Assertions.assertThat(actual)
          .withFailMessage(
              () ->
                  "Expected result to be empty for initial state <"
                      + initialState
                      + "> but was present with: <"
                      + actual.orElse(null)
                      + ">")
          .isEmpty();
      return this;
    }

    /** Verifies that the result is present (not empty). */
    public StateTResultAssert<S, F, A> isPresent() {
      Assertions.assertThat(actual)
          .withFailMessage(
              "Expected result to be present for initial state <%s> but was empty", initialState)
          .isPresent();
      return this;
    }

    /** Verifies that the result contains a value equal to the expected value. */
    public StateTResultAssert<S, F, A> hasValue(A expected) {
      isPresent();
      A actualV = actual.get().value();
      Assertions.assertThat(actualV)
          .withFailMessage(
              "Expected value to be <%s> for initial state <%s> but was <%s>",
              expected, initialState, actualV)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the result contains a final state equal to the expected state. */
    public StateTResultAssert<S, F, A> hasFinalState(S expected) {
      isPresent();
      S actualS = actual.get().state();
      Assertions.assertThat(actualS)
          .withFailMessage(
              "Expected final state to be <%s> for initial state <%s> but was <%s>",
              expected, initialState, actualS)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the result contains both the expected value and final state. */
    public StateTResultAssert<S, F, A> hasResult(A expectedValue, S expectedState) {
      isPresent();
      StateTuple<S, A> tuple = actual.get();
      boolean matches =
          Objects.equals(tuple.value(), expectedValue)
              && Objects.equals(tuple.state(), expectedState);
      Assertions.assertThat(matches)
          .withFailMessage(
              "Expected result (%s, %s) for initial state <%s> but was (%s, %s)",
              expectedValue, expectedState, initialState, tuple.value(), tuple.state())
          .isTrue();
      return this;
    }

    /** Verifies that the value satisfies the given requirements. */
    public StateTResultAssert<S, F, A> satisfiesValue(Consumer<? super A> requirements) {
      isPresent();
      requirements.accept(actual.get().value());
      return this;
    }

    /** Verifies that the final state satisfies the given requirements. */
    public StateTResultAssert<S, F, A> satisfiesFinalState(Consumer<? super S> requirements) {
      isPresent();
      requirements.accept(actual.get().state());
      return this;
    }

    /** Verifies that the value matches the given predicate. */
    public StateTResultAssert<S, F, A> valueMatches(Predicate<? super A> predicate) {
      isPresent();
      A value = actual.get().value();
      Assertions.assertThat(predicate.test(value))
          .withFailMessage(
              "Value <%s> did not match predicate for initial state <%s>", value, initialState)
          .isTrue();
      return this;
    }

    /** Verifies that the final state matches the given predicate. */
    public StateTResultAssert<S, F, A> finalStateMatches(Predicate<? super S> predicate) {
      isPresent();
      S state = actual.get().state();
      Assertions.assertThat(predicate.test(state))
          .withFailMessage(
              "Final state <%s> did not match predicate for initial state <%s>",
              state, initialState)
          .isTrue();
      return this;
    }
  }
}
