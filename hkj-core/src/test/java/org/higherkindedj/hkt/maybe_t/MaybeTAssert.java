// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Custom AssertJ assertion for MaybeT transformer types.
 *
 * <p>Provides fluent assertions for MaybeT values wrapped in Kind, handling both the outer monad
 * layer and the inner Maybe layer with British spellings in documentation.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Assert that result is Present with Just value
 * assertThatMaybeT(result).isPresentJust().hasJustValue("expected");
 *
 * // Assert that result is Present with Nothing
 * assertThatMaybeT(result).isPresentNothing();
 *
 * // Assert that outer monad is empty
 * assertThatMaybeT(result).isEmpty();
 *
 * // Complex assertion with consumer
 * assertThatMaybeT(result)
 *     .isPresentJust()
 *     .satisfiesJust(value -> assertThat(value).startsWith("prefix"));
 * }</pre>
 *
 * @param <F> the witness type of the outer monad
 * @param <A> the value type
 */
public class MaybeTAssert {

  /**
   * Entry point for MaybeT assertions when the outer monad is Optional.
   *
   * <p>This is the most common case where F is OptionalKind.Witness.
   *
   * @param <F> the outer monad witness type
   * @param <A> the value type
   * @param actual the MaybeT Kind to assert on
   * @param outerUnwrapper function to unwrap outer monad to Optional
   * @return a new {@link MaybeTOptionalAssert} instance
   */
  public static <F, A> MaybeTOptionalAssert<F, A> assertThatMaybeT(
      Kind<MaybeTKind.Witness<F>, A> actual,
      Function<Kind<F, Maybe<A>>, Optional<Maybe<A>>> outerUnwrapper) {
    return new MaybeTOptionalAssert<>(actual, outerUnwrapper);
  }

  /**
   * Specialised assertion class for MaybeT with Optional as the outer monad.
   *
   * <p>Provides convenient methods for asserting on Optional&lt;Maybe&lt;A&gt;&gt; structures.
   *
   * @param <F> the witness type of the outer monad
   * @param <A> the value type
   */
  public static class MaybeTOptionalAssert<F, A>
      extends AbstractAssert<MaybeTOptionalAssert<F, A>, Kind<MaybeTKind.Witness<F>, A>> {

    private final Function<Kind<F, Maybe<A>>, Optional<Maybe<A>>> outerUnwrapper;

    protected MaybeTOptionalAssert(
        Kind<MaybeTKind.Witness<F>, A> actual,
        Function<Kind<F, Maybe<A>>, Optional<Maybe<A>>> outerUnwrapper) {
      super(actual, MaybeTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    private Optional<Maybe<A>> unwrap() {
      if (actual == null) return Optional.empty();
      var maybeT = MaybeTKindHelper.MAYBE_T.narrow(actual);
      Kind<F, Maybe<A>> outerKind = maybeT.value();
      return outerUnwrapper.apply(outerKind);
    }

    /**
     * Verifies that the outer monad is empty.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the outer monad is not empty
     */
    public MaybeTOptionalAssert<F, A> isEmpty() {
      isNotNull();
      Optional<Maybe<A>> unwrapped = unwrap();
      if (unwrapped.isPresent()) {
        failWithMessage("Expected outer monad to be empty but was present with: <%s>", unwrapped.get());
      }
      return this;
    }

    /**
     * Verifies that the outer monad is present (not empty).
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the outer monad is empty
     */
    public MaybeTOptionalAssert<F, A> isPresent() {
      isNotNull();
      Optional<Maybe<A>> unwrapped = unwrap();
      if (unwrapped.isEmpty()) {
        failWithMessage("Expected outer monad to be present but was empty");
      }
      return this;
    }

    /**
     * Verifies that the outer monad is present and contains a Just value.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if outer is empty or contains Nothing
     */
    public MaybeTOptionalAssert<F, A> isPresentJust() {
      isPresent();
      Optional<Maybe<A>> unwrapped = unwrap();
      Maybe<A> maybe = unwrapped.get();
      if (maybe.isNothing()) {
        failWithMessage("Expected outer monad to contain Just but was Nothing");
      }
      return this;
    }

    /**
     * Verifies that the outer monad is present and contains Nothing.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if outer is empty or contains a Just value
     */
    public MaybeTOptionalAssert<F, A> isPresentNothing() {
      isPresent();
      Optional<Maybe<A>> unwrapped = unwrap();
      Maybe<A> maybe = unwrapped.get();
      if (maybe.isJust()) {
        failWithMessage(
            "Expected outer monad to contain Nothing but was Just with: <%s>", maybe.get());
      }
      return this;
    }

    /**
     * Verifies that the MaybeT contains a Just value equal to the expected value.
     *
     * @param expected the expected Just value
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Just or value doesn't match
     */
    public MaybeTOptionalAssert<F, A> hasJustValue(A expected) {
      isPresentJust();
      A actual = unwrap().get().get();
      if (!Objects.equals(actual, expected)) {
        failWithMessage("Expected Just value to be <%s> but was <%s>", expected, actual);
      }
      return this;
    }

    /**
     * Verifies that the MaybeT contains a Just value satisfying the given requirements.
     *
     * @param requirements the consumer to apply to the Just value
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Just or requirements not satisfied
     */
    public MaybeTOptionalAssert<F, A> satisfiesJust(Consumer<? super A> requirements) {
      isPresentJust();
      A value = unwrap().get().get();
      try {
        requirements.accept(value);
      } catch (AssertionError e) {
        failWithMessage("Just value did not satisfy requirements: %s", e.getMessage());
      }
      return this;
    }

    /**
     * Verifies that the Just value matches the given predicate.
     *
     * @param predicate the predicate to test
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Just or predicate fails
     */
    public MaybeTOptionalAssert<F, A> justMatches(Predicate<? super A> predicate) {
      isPresentJust();
      A value = unwrap().get().get();
      if (!predicate.test(value)) {
        failWithMessage("Just value <%s> did not match predicate", value);
      }
      return this;
    }

    /**
     * Verifies that the Just value is not null.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Just or value is null
     */
    public MaybeTOptionalAssert<F, A> hasNonNullJustValue() {
      isPresentJust();
      A value = unwrap().get().get();
      if (value == null) {
        failWithMessage("Expected Just value to be non-null but was null");
      }
      return this;
    }

    /**
     * Verifies that the Just value is an instance of the specified type.
     *
     * @param type the expected type
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Just or value is not of expected type
     */
    public MaybeTOptionalAssert<F, A> hasJustValueOfType(Class<?> type) {
      isPresentJust();
      A value = unwrap().get().get();
      if (value != null && !type.isInstance(value)) {
        failWithMessage(
            "Expected Just value to be of type <%s> but was <%s>",
            type.getName(),
            value.getClass().getName());
      }
      return this;
    }

    /**
     * Verifies that this MaybeT is equal to another MaybeT by comparing unwrapped values.
     *
     * @param other the other MaybeT to compare with
     * @return this assertion object for chaining
     * @throws AssertionError if the MaybeT values are not equal
     */
    public MaybeTOptionalAssert<F, A> isEqualToMaybeT(Kind<MaybeTKind.Witness<F>, A> other) {
      isNotNull();
      Optional<Maybe<A>> thisUnwrapped = unwrap();

      if (other == null) {
        failWithMessage("Expected MaybeT to compare with but was null");
        return this;
      }

      var otherMaybeT = MaybeTKindHelper.MAYBE_T.narrow(other);
      Kind<F, Maybe<A>> otherOuterKind = otherMaybeT.value();
      Optional<Maybe<A>> otherUnwrapped = outerUnwrapper.apply(otherOuterKind);

      if (!thisUnwrapped.equals(otherUnwrapped)) {
        failWithMessage(
            "Expected MaybeT to be equal to <%s> but was <%s>", otherUnwrapped, thisUnwrapped);
      }
      return this;
    }
  }
}
