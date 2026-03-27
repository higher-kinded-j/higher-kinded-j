// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Custom AssertJ assertion for StateT transformer types.
 *
 * <p>Provides fluent assertions for StateT values wrapped in Kind. Because StateT wraps a function
 * from an initial state to a monadic result containing both a value and a final state, assertions
 * require running the computation with a specific initial state before inspecting the result.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Assert that running with an initial state produces the expected value and final state
 * assertThatStateT(result, this::unwrapToOptional)
 *     .whenRunWith(0)
 *     .hasValue("expected")
 *     .hasFinalState(42);
 *
 * // Assert both value and state together
 * assertThatStateT(result, this::unwrapToOptional)
 *     .whenRunWith(0)
 *     .hasResult("expected", 42);
 *
 * // Assert that the outer monad is empty after running
 * assertThatStateT(result, this::unwrapToOptional)
 *     .whenRunWith(0)
 *     .isEmpty();
 * }</pre>
 */
public class StateTAssert {

  /**
   * Entry point for StateT assertions when the outer monad unwraps to Optional.
   *
   * @param <S> the state type
   * @param <F> the witness type of the outer monad
   * @param <A> the value type
   * @param actual the StateT Kind to assert on
   * @param outerUnwrapper function to unwrap outer monad to Optional
   * @return a new {@link StateTOptionalAssert} instance
   */
  public static <S, F extends WitnessArity<TypeArity.Unary>, A>
      StateTOptionalAssert<S, F, A> assertThatStateT(
          Kind<StateTKind.Witness<S, F>, A> actual,
          Function<Kind<F, StateTuple<S, A>>, Optional<StateTuple<S, A>>> outerUnwrapper) {
    return new StateTOptionalAssert<>(actual, outerUnwrapper);
  }

  /**
   * Specialised assertion class for StateT with Optional-based unwrapping.
   *
   * <p>Provides a two-phase assertion model: first run the state computation with an initial state
   * via {@link #whenRunWith(Object)}, then assert on the resulting value and final state.
   *
   * @param <S> the state type
   * @param <F> the witness type of the outer monad
   * @param <A> the value type
   */
  public static class StateTOptionalAssert<S, F extends WitnessArity<TypeArity.Unary>, A>
      extends AbstractAssert<StateTOptionalAssert<S, F, A>, Kind<StateTKind.Witness<S, F>, A>> {

    private final Function<Kind<F, StateTuple<S, A>>, Optional<StateTuple<S, A>>> outerUnwrapper;

    protected StateTOptionalAssert(
        Kind<StateTKind.Witness<S, F>, A> actual,
        Function<Kind<F, StateTuple<S, A>>, Optional<StateTuple<S, A>>> outerUnwrapper) {
      super(actual, StateTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    /**
     * Runs the StateT computation with the given initial state and returns a result assertion for
     * the computed value and final state.
     *
     * @param initialState the initial state to run the computation with
     * @return a {@link StateTResultAssert} for asserting on the result
     */
    public StateTResultAssert<S, F, A> whenRunWith(S initialState) {
      isNotNull();
      var stateT = StateTKindHelper.STATE_T.narrow(actual);
      Kind<F, StateTuple<S, A>> result = stateT.runStateT(initialState);
      Optional<StateTuple<S, A>> unwrapped = outerUnwrapper.apply(result);
      return new StateTResultAssert<>(unwrapped, initialState);
    }

    /**
     * Verifies that this StateT is equal to another StateT by comparing results for a given initial
     * state.
     *
     * @param other the other StateT to compare with
     * @param initialState the initial state to run both computations with
     * @return this assertion object for chaining
     * @throws AssertionError if the results are not equal
     */
    public StateTOptionalAssert<S, F, A> isEqualToStateT(
        Kind<StateTKind.Witness<S, F>, A> other, S initialState) {
      isNotNull();
      var thisStateT = StateTKindHelper.STATE_T.narrow(actual);
      Optional<StateTuple<S, A>> thisResult =
          outerUnwrapper.apply(thisStateT.runStateT(initialState));

      if (other == null) {
        failWithMessage("Expected StateT to compare with but was null");
        return this;
      }

      var otherStateT = StateTKindHelper.STATE_T.narrow(other);
      Optional<StateTuple<S, A>> otherResult =
          outerUnwrapper.apply(otherStateT.runStateT(initialState));

      if (!thisResult.equals(otherResult)) {
        failWithMessage(
            "Expected StateT to be equal to <%s> but was <%s> for initial state <%s>",
            otherResult, thisResult, initialState);
      }
      return this;
    }
  }

  /**
   * Assertion class for the result of running a StateT computation with a specific initial state.
   *
   * @param <S> the state type
   * @param <F> the witness type of the outer monad
   * @param <A> the value type
   */
  public static class StateTResultAssert<S, F extends WitnessArity<TypeArity.Unary>, A> {

    private final Optional<StateTuple<S, A>> result;
    private final S initialState;

    protected StateTResultAssert(Optional<StateTuple<S, A>> result, S initialState) {
      this.result = result;
      this.initialState = initialState;
    }

    /**
     * Verifies that the result is empty (outer monad was empty).
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the result is present
     */
    public StateTResultAssert<S, F, A> isEmpty() {
      if (result.isPresent()) {
        failWithMessage(
            "Expected result to be empty for initial state <%s> but was present with: <%s>",
            initialState, result.get());
      }
      return this;
    }

    /**
     * Verifies that the result is present (not empty).
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the result is empty
     */
    public StateTResultAssert<S, F, A> isPresent() {
      if (result.isEmpty()) {
        failWithMessage(
            "Expected result to be present for initial state <%s> but was empty", initialState);
      }
      return this;
    }

    /**
     * Verifies that the result contains a value equal to the expected value.
     *
     * @param expected the expected value
     * @return this assertion object for chaining
     * @throws AssertionError if not present or value does not match
     */
    public StateTResultAssert<S, F, A> hasValue(A expected) {
      isPresent();
      A actual = result.get().value();
      if (!Objects.equals(actual, expected)) {
        failWithMessage(
            "Expected value to be <%s> for initial state <%s> but was <%s>",
            expected, initialState, actual);
      }
      return this;
    }

    /**
     * Verifies that the result contains a final state equal to the expected state.
     *
     * @param expected the expected final state
     * @return this assertion object for chaining
     * @throws AssertionError if not present or final state does not match
     */
    public StateTResultAssert<S, F, A> hasFinalState(S expected) {
      isPresent();
      S actual = result.get().state();
      if (!Objects.equals(actual, expected)) {
        failWithMessage(
            "Expected final state to be <%s> for initial state <%s> but was <%s>",
            expected, initialState, actual);
      }
      return this;
    }

    /**
     * Verifies that the result contains both the expected value and final state.
     *
     * @param expectedValue the expected value
     * @param expectedState the expected final state
     * @return this assertion object for chaining
     * @throws AssertionError if not present or either component does not match
     */
    public StateTResultAssert<S, F, A> hasResult(A expectedValue, S expectedState) {
      isPresent();
      StateTuple<S, A> tuple = result.get();
      if (!Objects.equals(tuple.value(), expectedValue)
          || !Objects.equals(tuple.state(), expectedState)) {
        failWithMessage(
            "Expected result (%s, %s) for initial state <%s> but was (%s, %s)",
            expectedValue, expectedState, initialState, tuple.value(), tuple.state());
      }
      return this;
    }

    /**
     * Verifies that the value satisfies the given requirements.
     *
     * @param requirements the consumer to apply to the value
     * @return this assertion object for chaining
     * @throws AssertionError if not present or requirements not satisfied
     */
    public StateTResultAssert<S, F, A> satisfiesValue(Consumer<? super A> requirements) {
      isPresent();
      A value = result.get().value();
      try {
        requirements.accept(value);
      } catch (AssertionError e) {
        failWithMessage(
            "Value did not satisfy requirements for initial state <%s>: %s",
            initialState, e.getMessage());
      }
      return this;
    }

    /**
     * Verifies that the final state satisfies the given requirements.
     *
     * @param requirements the consumer to apply to the final state
     * @return this assertion object for chaining
     * @throws AssertionError if not present or requirements not satisfied
     */
    public StateTResultAssert<S, F, A> satisfiesFinalState(Consumer<? super S> requirements) {
      isPresent();
      S state = result.get().state();
      try {
        requirements.accept(state);
      } catch (AssertionError e) {
        failWithMessage(
            "Final state did not satisfy requirements for initial state <%s>: %s",
            initialState, e.getMessage());
      }
      return this;
    }

    /**
     * Verifies that the value matches the given predicate.
     *
     * @param predicate the predicate to test
     * @return this assertion object for chaining
     * @throws AssertionError if not present or predicate fails
     */
    public StateTResultAssert<S, F, A> valueMatches(Predicate<? super A> predicate) {
      isPresent();
      A value = result.get().value();
      if (!predicate.test(value)) {
        failWithMessage(
            "Value <%s> did not match predicate for initial state <%s>", value, initialState);
      }
      return this;
    }

    /**
     * Verifies that the final state matches the given predicate.
     *
     * @param predicate the predicate to test
     * @return this assertion object for chaining
     * @throws AssertionError if not present or predicate fails
     */
    public StateTResultAssert<S, F, A> finalStateMatches(Predicate<? super S> predicate) {
      isPresent();
      S state = result.get().state();
      if (!predicate.test(state)) {
        failWithMessage(
            "Final state <%s> did not match predicate for initial state <%s>", state, initialState);
      }
      return this;
    }

    private void failWithMessage(String message, Object... args) {
      throw new AssertionError(String.format(message, args));
    }
  }
}
