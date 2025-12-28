// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;

/**
 * Custom AssertJ assertion for EitherT transformer types.
 *
 * <p>Provides fluent assertions for EitherT values wrapped in Kind, handling both the outer monad
 * layer and the inner Either layer with British spellings in documentation.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Assert that result is Present with Right value
 * assertThatEitherT(result, this::unwrapToOptional)
 *     .isPresentRight()
 *     .hasRightValue("expected");
 *
 * // Assert that result is Present with Left error
 * assertThatEitherT(result, this::unwrapToOptional)
 *     .isPresentLeft()
 *     .hasLeftValue(new TestError("E1"));
 *
 * // Assert that outer monad is empty
 * assertThatEitherT(result, this::unwrapToOptional).isEmpty();
 *
 * // Complex assertion with consumer
 * assertThatEitherT(result, this::unwrapToOptional)
 *     .isPresentRight()
 *     .satisfiesRight(value -> assertThat(value).startsWith("prefix"));
 * }</pre>
 */
public class EitherTAssert {

  /**
   * Entry point for EitherT assertions when the outer monad is Optional.
   *
   * <p>This is the most common case where F is OptionalKind.Witness.
   *
   * @param <F> the witness type of the outer monad
   * @param <E> the error type
   * @param <A> the success type
   * @param actual the EitherT Kind to assert on
   * @param outerUnwrapper function to unwrap outer monad to Optional
   * @return a new {@link EitherTOptionalAssert} instance
   */
  public static <F extends WitnessArity<TypeArity.Unary>, E, A>
      EitherTOptionalAssert<F, E, A> assertThatEitherT(
          Kind<EitherTKind.Witness<F, E>, A> actual,
          Function<Kind<F, Either<E, A>>, Optional<Either<E, A>>> outerUnwrapper) {
    return new EitherTOptionalAssert<>(actual, outerUnwrapper);
  }

  /**
   * Specialised assertion class for EitherT with Optional as the outer monad.
   *
   * <p>Provides convenient methods for asserting on Optional&lt;Either&lt;E, A&gt;&gt; structures.
   *
   * @param <F> the witness type of the outer monad
   * @param <E> the error type
   * @param <A> the success type
   */
  public static class EitherTOptionalAssert<F extends WitnessArity<TypeArity.Unary>, E, A>
      extends AbstractAssert<EitherTOptionalAssert<F, E, A>, Kind<EitherTKind.Witness<F, E>, A>> {

    private final Function<Kind<F, Either<E, A>>, Optional<Either<E, A>>> outerUnwrapper;

    protected EitherTOptionalAssert(
        Kind<EitherTKind.Witness<F, E>, A> actual,
        Function<Kind<F, Either<E, A>>, Optional<Either<E, A>>> outerUnwrapper) {
      super(actual, EitherTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    private Optional<Either<E, A>> unwrap() {
      if (actual == null) return Optional.empty();
      var eitherT = EitherTKindHelper.EITHER_T.narrow(actual);
      Kind<F, Either<E, A>> outerKind = eitherT.value();
      return outerUnwrapper.apply(outerKind);
    }

    /**
     * Verifies that the outer monad is empty.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the outer monad is not empty
     */
    public EitherTOptionalAssert<F, E, A> isEmpty() {
      isNotNull();
      Optional<Either<E, A>> unwrapped = unwrap();
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
    public EitherTOptionalAssert<F, E, A> isPresent() {
      isNotNull();
      Optional<Either<E, A>> unwrapped = unwrap();
      if (unwrapped.isEmpty()) {
        failWithMessage("Expected outer monad to be present but was empty");
      }
      return this;
    }

    /**
     * Verifies that the outer monad is present and contains a Right value.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if outer is empty or contains a Left
     */
    public EitherTOptionalAssert<F, E, A> isPresentRight() {
      isPresent();
      Optional<Either<E, A>> unwrapped = unwrap();
      Either<E, A> either = unwrapped.get();
      if (either.isLeft()) {
        failWithMessage(
            "Expected outer monad to contain Right but was Left with: <%s>", either.getLeft());
      }
      return this;
    }

    /**
     * Verifies that the outer monad is present and contains a Left value.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if outer is empty or contains a Right
     */
    public EitherTOptionalAssert<F, E, A> isPresentLeft() {
      isPresent();
      Optional<Either<E, A>> unwrapped = unwrap();
      Either<E, A> either = unwrapped.get();
      if (either.isRight()) {
        failWithMessage(
            "Expected outer monad to contain Left but was Right with: <%s>", either.getRight());
      }
      return this;
    }

    /**
     * Verifies that the EitherT contains a Right value equal to the expected value.
     *
     * @param expected the expected Right value
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Right or value doesn't match
     */
    public EitherTOptionalAssert<F, E, A> hasRightValue(A expected) {
      isPresentRight();
      A actual = unwrap().get().getRight();
      if (!Objects.equals(actual, expected)) {
        failWithMessage("Expected Right value to be <%s> but was <%s>", expected, actual);
      }
      return this;
    }

    /**
     * Verifies that the EitherT contains a Left value equal to the expected error.
     *
     * @param expected the expected Left value
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Left or error doesn't match
     */
    public EitherTOptionalAssert<F, E, A> hasLeftValue(E expected) {
      isPresentLeft();
      E actual = unwrap().get().getLeft();
      if (!Objects.equals(actual, expected)) {
        failWithMessage("Expected Left value to be <%s> but was <%s>", expected, actual);
      }
      return this;
    }

    /**
     * Verifies that the EitherT contains a Right value satisfying the given requirements.
     *
     * @param requirements the consumer to apply to the Right value
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Right or requirements not satisfied
     */
    public EitherTOptionalAssert<F, E, A> satisfiesRight(Consumer<? super A> requirements) {
      isPresentRight();
      A value = unwrap().get().getRight();
      try {
        requirements.accept(value);
      } catch (AssertionError e) {
        failWithMessage("Right value did not satisfy requirements: %s", e.getMessage());
      }
      return this;
    }

    /**
     * Verifies that the EitherT contains a Left value satisfying the given requirements.
     *
     * @param requirements the consumer to apply to the Left value
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Left or requirements not satisfied
     */
    public EitherTOptionalAssert<F, E, A> satisfiesLeft(Consumer<? super E> requirements) {
      isPresentLeft();
      E error = unwrap().get().getLeft();
      try {
        requirements.accept(error);
      } catch (AssertionError e) {
        failWithMessage("Left value did not satisfy requirements: %s", e.getMessage());
      }
      return this;
    }

    /**
     * Verifies that the Right value matches the given predicate.
     *
     * @param predicate the predicate to test
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Right or predicate fails
     */
    public EitherTOptionalAssert<F, E, A> rightMatches(Predicate<? super A> predicate) {
      isPresentRight();
      A value = unwrap().get().getRight();
      if (!predicate.test(value)) {
        failWithMessage("Right value <%s> did not match predicate", value);
      }
      return this;
    }

    /**
     * Verifies that the Left value matches the given predicate.
     *
     * @param predicate the predicate to test
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Left or predicate fails
     */
    public EitherTOptionalAssert<F, E, A> leftMatches(Predicate<? super E> predicate) {
      isPresentLeft();
      E error = unwrap().get().getLeft();
      if (!predicate.test(error)) {
        failWithMessage("Left value <%s> did not match predicate", error);
      }
      return this;
    }

    /**
     * Verifies that the Right value is not null.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Right or value is null
     */
    public EitherTOptionalAssert<F, E, A> hasNonNullRightValue() {
      isPresentRight();
      A value = unwrap().get().getRight();
      if (value == null) {
        failWithMessage("Expected Right value to be non-null but was null");
      }
      return this;
    }

    /**
     * Verifies that the Left value is not null.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Left or error is null
     */
    public EitherTOptionalAssert<F, E, A> hasNonNullLeftValue() {
      isPresentLeft();
      E error = unwrap().get().getLeft();
      if (error == null) {
        failWithMessage("Expected Left value to be non-null but was null");
      }
      return this;
    }

    /**
     * Verifies that the Right value is an instance of the specified type.
     *
     * @param type the expected type
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Right or value is not of expected type
     */
    public EitherTOptionalAssert<F, E, A> hasRightValueOfType(Class<?> type) {
      isPresentRight();
      A value = unwrap().get().getRight();
      if (value != null && !type.isInstance(value)) {
        failWithMessage(
            "Expected Right value to be of type <%s> but was <%s>",
            type.getName(), value.getClass().getName());
      }
      return this;
    }

    /**
     * Verifies that the Left value is an instance of the specified type.
     *
     * @param type the expected type
     * @return this assertion object for chaining
     * @throws AssertionError if not Present Left or error is not of expected type
     */
    public EitherTOptionalAssert<F, E, A> hasLeftValueOfType(Class<?> type) {
      isPresentLeft();
      E error = unwrap().get().getLeft();
      if (error != null && !type.isInstance(error)) {
        failWithMessage(
            "Expected Left value to be of type <%s> but was <%s>",
            type.getName(), error.getClass().getName());
      }
      return this;
    }

    /**
     * Verifies that this EitherT is equal to another EitherT by comparing unwrapped values.
     *
     * @param other the other EitherT to compare with
     * @return this assertion object for chaining
     * @throws AssertionError if the EitherT values are not equal
     */
    public EitherTOptionalAssert<F, E, A> isEqualToEitherT(
        Kind<EitherTKind.Witness<F, E>, A> other) {
      isNotNull();
      Optional<Either<E, A>> thisUnwrapped = unwrap();

      if (other == null) {
        failWithMessage("Expected EitherT to compare with but was null");
        return this;
      }

      var otherEitherT = EitherTKindHelper.EITHER_T.narrow(other);
      Kind<F, Either<E, A>> otherOuterKind = otherEitherT.value();
      Optional<Either<E, A>> otherUnwrapped = outerUnwrapper.apply(otherOuterKind);

      if (!thisUnwrapped.equals(otherUnwrapped)) {
        failWithMessage(
            "Expected EitherT to be equal to <%s> but was <%s>", otherUnwrapped, thisUnwrapped);
      }
      return this;
    }
  }
}
