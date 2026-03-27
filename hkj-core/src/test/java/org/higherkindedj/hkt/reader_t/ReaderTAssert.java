// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

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
 * Custom AssertJ assertion for ReaderT transformer types.
 *
 * <p>Provides fluent assertions for ReaderT values wrapped in Kind. Because ReaderT wraps a
 * function from an environment to a monadic result, assertions require running the reader with a
 * specific environment before inspecting the result.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Assert that running with an environment produces the expected value
 * assertThatReaderT(result, this::unwrapToOptional)
 *     .whenRunWith("test-env")
 *     .hasValue("expected");
 *
 * // Assert that the outer monad is empty after running
 * assertThatReaderT(result, this::unwrapToOptional)
 *     .whenRunWith("test-env")
 *     .isEmpty();
 * }</pre>
 *
 * @param <F> the witness type of the outer monad
 * @param <R_ENV> the environment type
 * @param <A> the value type
 */
public class ReaderTAssert {

  /**
   * Entry point for ReaderT assertions when the outer monad unwraps to Optional.
   *
   * @param <F> the witness type of the outer monad
   * @param <R_ENV> the environment type
   * @param <A> the value type
   * @param actual the ReaderT Kind to assert on
   * @param outerUnwrapper function to unwrap outer monad to Optional
   * @return a new {@link ReaderTOptionalAssert} instance
   */
  public static <F extends WitnessArity<TypeArity.Unary>, R_ENV, A>
      ReaderTOptionalAssert<F, R_ENV, A> assertThatReaderT(
          Kind<ReaderTKind.Witness<F, R_ENV>, A> actual,
          Function<Kind<F, A>, Optional<A>> outerUnwrapper) {
    return new ReaderTOptionalAssert<>(actual, outerUnwrapper);
  }

  /**
   * Specialised assertion class for ReaderT with Optional-based unwrapping.
   *
   * <p>Provides a two-phase assertion model: first run the reader with an environment via {@link
   * #whenRunWith(Object)}, then assert on the resulting value.
   *
   * @param <F> the witness type of the outer monad
   * @param <R_ENV> the environment type
   * @param <A> the value type
   */
  public static class ReaderTOptionalAssert<F extends WitnessArity<TypeArity.Unary>, R_ENV, A>
      extends AbstractAssert<
          ReaderTOptionalAssert<F, R_ENV, A>, Kind<ReaderTKind.Witness<F, R_ENV>, A>> {

    private final Function<Kind<F, A>, Optional<A>> outerUnwrapper;

    protected ReaderTOptionalAssert(
        Kind<ReaderTKind.Witness<F, R_ENV>, A> actual,
        Function<Kind<F, A>, Optional<A>> outerUnwrapper) {
      super(actual, ReaderTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    /**
     * Runs the ReaderT with the given environment and returns a result assertion for the computed
     * value.
     *
     * @param env the environment to run the reader with
     * @return a {@link ReaderTResultAssert} for asserting on the result
     */
    public ReaderTResultAssert<F, R_ENV, A> whenRunWith(R_ENV env) {
      isNotNull();
      var readerT = ReaderTKindHelper.READER_T.narrow(actual);
      Kind<F, A> result = readerT.run().apply(env);
      Optional<A> unwrapped = outerUnwrapper.apply(result);
      return new ReaderTResultAssert<>(unwrapped, env);
    }

    /**
     * Verifies that this ReaderT is equal to another ReaderT by comparing results for a given
     * environment.
     *
     * @param other the other ReaderT to compare with
     * @param env the environment to run both readers with
     * @return this assertion object for chaining
     * @throws AssertionError if the results are not equal
     */
    public ReaderTOptionalAssert<F, R_ENV, A> isEqualToReaderT(
        Kind<ReaderTKind.Witness<F, R_ENV>, A> other, R_ENV env) {
      isNotNull();
      var thisReaderT = ReaderTKindHelper.READER_T.narrow(actual);
      Optional<A> thisResult = outerUnwrapper.apply(thisReaderT.run().apply(env));

      if (other == null) {
        failWithMessage("Expected ReaderT to compare with but was null");
        return this;
      }

      var otherReaderT = ReaderTKindHelper.READER_T.narrow(other);
      Optional<A> otherResult = outerUnwrapper.apply(otherReaderT.run().apply(env));

      if (!thisResult.equals(otherResult)) {
        failWithMessage(
            "Expected ReaderT to be equal to <%s> but was <%s> for environment <%s>",
            otherResult, thisResult, env);
      }
      return this;
    }
  }

  /**
   * Assertion class for the result of running a ReaderT with a specific environment.
   *
   * @param <F> the witness type of the outer monad
   * @param <R_ENV> the environment type
   * @param <A> the value type
   */
  public static class ReaderTResultAssert<F extends WitnessArity<TypeArity.Unary>, R_ENV, A> {

    private final Optional<A> result;
    private final R_ENV environment;

    protected ReaderTResultAssert(Optional<A> result, R_ENV environment) {
      this.result = result;
      this.environment = environment;
    }

    /**
     * Verifies that the result is empty (outer monad was empty).
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the result is present
     */
    public ReaderTResultAssert<F, R_ENV, A> isEmpty() {
      if (result.isPresent()) {
        failWithMessage(
            "Expected result to be empty for environment <%s> but was present with: <%s>",
            environment, result.get());
      }
      return this;
    }

    /**
     * Verifies that the result is present (not empty).
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the result is empty
     */
    public ReaderTResultAssert<F, R_ENV, A> isPresent() {
      if (result.isEmpty()) {
        failWithMessage(
            "Expected result to be present for environment <%s> but was empty", environment);
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
    public ReaderTResultAssert<F, R_ENV, A> hasValue(A expected) {
      isPresent();
      A actual = result.get();
      if (!Objects.equals(actual, expected)) {
        failWithMessage(
            "Expected value to be <%s> for environment <%s> but was <%s>",
            expected, environment, actual);
      }
      return this;
    }

    /**
     * Verifies that the result value satisfies the given requirements.
     *
     * @param requirements the consumer to apply to the value
     * @return this assertion object for chaining
     * @throws AssertionError if not present or requirements not satisfied
     */
    public ReaderTResultAssert<F, R_ENV, A> satisfiesValue(Consumer<? super A> requirements) {
      isPresent();
      A value = result.get();
      try {
        requirements.accept(value);
      } catch (AssertionError e) {
        failWithMessage(
            "Value did not satisfy requirements for environment <%s>: %s",
            environment, e.getMessage());
      }
      return this;
    }

    /**
     * Verifies that the result value matches the given predicate.
     *
     * @param predicate the predicate to test
     * @return this assertion object for chaining
     * @throws AssertionError if not present or predicate fails
     */
    public ReaderTResultAssert<F, R_ENV, A> valueMatches(Predicate<? super A> predicate) {
      isPresent();
      A value = result.get();
      if (!predicate.test(value)) {
        failWithMessage(
            "Value <%s> did not match predicate for environment <%s>", value, environment);
      }
      return this;
    }

    private void failWithMessage(String message, Object... args) {
      throw new AssertionError(String.format(message, args));
    }
  }
}
