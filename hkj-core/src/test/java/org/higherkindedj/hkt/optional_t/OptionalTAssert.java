// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Custom AssertJ assertion for OptionalT transformer types.
 *
 * <p>Provides fluent assertions for OptionalT values wrapped in Kind, handling both the outer monad
 * layer and the inner Optional layer with British spellings in documentation.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Assert that result is Present with Some value
 * assertThatOptionalT(result).isPresentSome().hasSomeValue("expected");
 *
 * // Assert that result is Present with None
 * assertThatOptionalT(result).isPresentNone();
 *
 * // Assert that outer monad is empty
 * assertThatOptionalT(result).isEmpty();
 *
 * // Complex assertion with consumer
 * assertThatOptionalT(result)
 *     .isPresentSome()
 *     .satisfiesSome(value -> assertThat(value).startsWith("prefix"));
 * }</pre>
 *
 * @param <F> the witness type of the outer monad
 * @param <A> the value type
 */
public class OptionalTAssert {

  /**
   * Entry point for OptionalT assertions when the outer monad is Optional.
   *
   * <p>This is the most common case where F is OptionalKind.Witness.
   *
   * @param <F> the outer monad witness type
   * @param <A> the value type
   * @param actual the OptionalT Kind to assert on
   * @param outerUnwrapper function to unwrap outer monad to Optional
   * @return a new {@link OptionalTOptionalAssert} instance
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A>
      OptionalTOptionalAssert<F, A> assertThatOptionalT(
          Kind<OptionalTKind.Witness<F>, A> actual,
          Function<Kind<F, Optional<A>>, Optional<Optional<A>>> outerUnwrapper) {
    return new OptionalTOptionalAssert<>(actual, outerUnwrapper);
  }

  /**
   * Specialised assertion class for OptionalT with Optional as the outer monad.
   *
   * <p>Provides convenient methods for asserting on Optional&lt;Optional&lt;A&gt;&gt; structures.
   *
   * @param <F> the witness type of the outer monad
   * @param <A> the value type
   */
  public static class OptionalTOptionalAssert<F extends WitnessArity<TypeArity.Unary>, A>
      extends AbstractAssert<OptionalTOptionalAssert<F, A>, Kind<OptionalTKind.Witness<F>, A>> {

    private final Function<Kind<F, Optional<A>>, Optional<Optional<A>>> outerUnwrapper;

    protected OptionalTOptionalAssert(
        Kind<OptionalTKind.Witness<F>, A> actual,
        Function<Kind<F, Optional<A>>, Optional<Optional<A>>> outerUnwrapper) {
      super(actual, OptionalTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    private Optional<Optional<A>> unwrap() {
      if (actual == null) return Optional.empty();
      var optionalT = OptionalTKindHelper.OPTIONAL_T.narrow(actual);
      Kind<F, Optional<A>> outerKind = optionalT.value();
      return outerUnwrapper.apply(outerKind);
    }

    /**
     * Verifies that the outer monad is empty.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the outer monad is not empty
     */
    public OptionalTOptionalAssert<F, A> isEmpty() {
      isNotNull();
      Optional<Optional<A>> unwrapped = unwrap();
      if (unwrapped.isPresent()) {
        failWithMessage(
            "Expected outer monad to be empty but was present with: <%s>", unwrapped.get());
      }
      return this;
    }

    /**
     * Verifies that the outer monad is present (not empty).
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the outer monad is empty
     */
    public OptionalTOptionalAssert<F, A> isPresent() {
      isNotNull();
      Optional<Optional<A>> unwrapped = unwrap();
      if (unwrapped.isEmpty()) {
        failWithMessage("Expected outer monad to be present but was empty");
      }
      return this;
    }

    /**
     * Verifies that the outer monad is present and inner Optional contains a value (Some).
     *
     * @return this assertion object for chaining
     * @throws AssertionError if outer is empty or inner Optional is empty
     */
    public OptionalTOptionalAssert<F, A> isPresentSome() {
      isPresent();
      Optional<Optional<A>> unwrapped = unwrap();
      Optional<A> inner = unwrapped.get();
      if (inner.isEmpty()) {
        failWithMessage("Expected outer monad to contain Some but was None");
      }
      return this;
    }

    /**
     * Verifies that the outer monad is present and inner Optional is empty (None).
     *
     * @return this assertion object for chaining
     * @throws AssertionError if outer is empty or inner Optional contains a value
     */
    public OptionalTOptionalAssert<F, A> isPresentNone() {
      isPresent();
      Optional<Optional<A>> unwrapped = unwrap();
      Optional<A> inner = unwrapped.get();
      if (inner.isPresent()) {
        failWithMessage(
            "Expected outer monad to contain None but was Some with: <%s>", inner.get());
      }
      return this;
    }

    /**
     * Verifies that the OptionalT contains a Some value equal to the expected value.
     *
     * @param expected the expected Some value
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Some or value doesn't match
     */
    public OptionalTOptionalAssert<F, A> hasSomeValue(A expected) {
      isPresentSome();
      A actual = unwrap().get().get();
      if (!Objects.equals(actual, expected)) {
        failWithMessage("Expected Some value to be <%s> but was <%s>", expected, actual);
      }
      return this;
    }

    /**
     * Verifies that the OptionalT contains a Some value satisfying the given requirements.
     *
     * @param requirements the consumer to apply to the Some value
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Some or requirements not satisfied
     */
    public OptionalTOptionalAssert<F, A> satisfiesSome(Consumer<? super A> requirements) {
      isPresentSome();
      A value = unwrap().get().get();
      try {
        requirements.accept(value);
      } catch (AssertionError e) {
        failWithMessage("Some value did not satisfy requirements: %s", e.getMessage());
      }
      return this;
    }

    /**
     * Verifies that the Some value matches the given predicate.
     *
     * @param predicate the predicate to test
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Some or predicate fails
     */
    public OptionalTOptionalAssert<F, A> someMatches(Predicate<? super A> predicate) {
      isPresentSome();
      A value = unwrap().get().get();
      if (!predicate.test(value)) {
        failWithMessage("Some value <%s> did not match predicate", value);
      }
      return this;
    }

    /**
     * Verifies that the Some value is not null.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Some or value is null
     */
    public OptionalTOptionalAssert<F, A> hasNonNullSomeValue() {
      isPresentSome();
      A value = unwrap().get().get();
      if (value == null) {
        failWithMessage("Expected Some value to be non-null but was null");
      }
      return this;
    }

    /**
     * Verifies that the Some value is an instance of the specified type.
     *
     * @param type the expected type
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Some or value is not of expected type
     */
    public OptionalTOptionalAssert<F, A> hasSomeValueOfType(Class<?> type) {
      isPresentSome();
      A value = unwrap().get().get();
      if (value != null && !type.isInstance(value)) {
        failWithMessage(
            "Expected Some value to be of type <%s> but was <%s>",
            type.getName(), value.getClass().getName());
      }
      return this;
    }

    /**
     * Verifies that this OptionalT is equal to another OptionalT by comparing unwrapped values.
     *
     * @param other the other OptionalT to compare with
     * @return this assertion object for chaining
     * @throws AssertionError if the OptionalT values are not equal
     */
    public OptionalTOptionalAssert<F, A> isEqualToOptionalT(
        Kind<OptionalTKind.Witness<F>, A> other) {
      isNotNull();
      Optional<Optional<A>> thisUnwrapped = unwrap();

      if (other == null) {
        failWithMessage("Expected OptionalT to compare with but was null");
        return this;
      }

      var otherOptionalT = OptionalTKindHelper.OPTIONAL_T.narrow(other);
      Kind<F, Optional<A>> otherOuterKind = otherOptionalT.value();
      Optional<Optional<A>> otherUnwrapped = outerUnwrapper.apply(otherOuterKind);

      if (!thisUnwrapped.equals(otherUnwrapped)) {
        failWithMessage(
            "Expected OptionalT to be equal to <%s> but was <%s>", otherUnwrapped, thisUnwrapped);
      }
      return this;
    }
  }
}
