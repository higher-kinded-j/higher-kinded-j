// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Custom AssertJ assertion for WriterT transformer types.
 *
 * <p>Provides fluent assertions for WriterT values wrapped in Kind, handling both the outer monad
 * layer and the inner Pair layer.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Assert that result is present with expected value and output
 * assertThatWriterT(result, this::unwrapToOptional)
 *     .isPresent()
 *     .hasValue("expected")
 *     .hasOutput("log entry");
 *
 * // Assert both components together
 * assertThatWriterT(result, this::unwrapToOptional)
 *     .hasPair("expected", "log entry");
 *
 * // Assert that outer monad is empty
 * assertThatWriterT(result, this::unwrapToOptional).isEmpty();
 * }</pre>
 */
public class WriterTAssert {

  /**
   * Entry point for WriterT assertions when the outer monad unwraps to Optional.
   *
   * @param <F> the witness type of the outer monad
   * @param <W> the output/log type
   * @param <A> the value type
   * @param actual the WriterT Kind to assert on
   * @param outerUnwrapper function to unwrap outer monad to Optional
   * @return a new {@link WriterTOptionalAssert} instance
   */
  public static <F extends WitnessArity<TypeArity.Unary>, W, A>
      WriterTOptionalAssert<F, W, A> assertThatWriterT(
          Kind<WriterTKind.Witness<F, W>, A> actual,
          Function<Kind<F, Pair<A, W>>, Optional<Pair<A, W>>> outerUnwrapper) {
    return new WriterTOptionalAssert<>(actual, outerUnwrapper);
  }

  /**
   * Specialised assertion class for WriterT with Optional-based unwrapping.
   *
   * <p>Provides convenient methods for asserting on Optional&lt;Pair&lt;A, W&gt;&gt; structures.
   *
   * @param <F> the witness type of the outer monad
   * @param <W> the output/log type
   * @param <A> the value type
   */
  public static class WriterTOptionalAssert<F extends WitnessArity<TypeArity.Unary>, W, A>
      extends AbstractAssert<WriterTOptionalAssert<F, W, A>, Kind<WriterTKind.Witness<F, W>, A>> {

    private final Function<Kind<F, Pair<A, W>>, Optional<Pair<A, W>>> outerUnwrapper;

    protected WriterTOptionalAssert(
        Kind<WriterTKind.Witness<F, W>, A> actual,
        Function<Kind<F, Pair<A, W>>, Optional<Pair<A, W>>> outerUnwrapper) {
      super(actual, WriterTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    private Optional<Pair<A, W>> unwrap() {
      if (actual == null) return Optional.empty();
      var writerT = WriterTKindHelper.WRITER_T.narrow(actual);
      Kind<F, Pair<A, W>> outerKind = writerT.run();
      return outerUnwrapper.apply(outerKind);
    }

    /**
     * Verifies that the outer monad is empty.
     *
     * @return this assertion object for chaining
     * @throws AssertionError if the outer monad is not empty
     */
    public WriterTOptionalAssert<F, W, A> isEmpty() {
      isNotNull();
      Optional<Pair<A, W>> unwrapped = unwrap();
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
    public WriterTOptionalAssert<F, W, A> isPresent() {
      isNotNull();
      Optional<Pair<A, W>> unwrapped = unwrap();
      if (unwrapped.isEmpty()) {
        failWithMessage("Expected outer monad to be present but was empty");
      }
      return this;
    }

    /**
     * Verifies that the WriterT contains a value equal to the expected value.
     *
     * @param expected the expected value (first component of the Pair)
     * @return this assertion object for chaining
     * @throws AssertionError if not present or value does not match
     */
    public WriterTOptionalAssert<F, W, A> hasValue(A expected) {
      isPresent();
      A actual = unwrap().get().first();
      if (!Objects.equals(actual, expected)) {
        failWithMessage("Expected value to be <%s> but was <%s>", expected, actual);
      }
      return this;
    }

    /**
     * Verifies that the WriterT contains output equal to the expected output.
     *
     * @param expected the expected output (second component of the Pair)
     * @return this assertion object for chaining
     * @throws AssertionError if not present or output does not match
     */
    public WriterTOptionalAssert<F, W, A> hasOutput(W expected) {
      isPresent();
      W actual = unwrap().get().second();
      if (!Objects.equals(actual, expected)) {
        failWithMessage("Expected output to be <%s> but was <%s>", expected, actual);
      }
      return this;
    }

    /**
     * Verifies that the WriterT contains both the expected value and output.
     *
     * @param expectedValue the expected value (first component)
     * @param expectedOutput the expected output (second component)
     * @return this assertion object for chaining
     * @throws AssertionError if not present or either component does not match
     */
    public WriterTOptionalAssert<F, W, A> hasPair(A expectedValue, W expectedOutput) {
      isPresent();
      Pair<A, W> pair = unwrap().get();
      if (!Objects.equals(pair.first(), expectedValue)
          || !Objects.equals(pair.second(), expectedOutput)) {
        failWithMessage(
            "Expected Pair<%s, %s> but was Pair<%s, %s>",
            expectedValue, expectedOutput, pair.first(), pair.second());
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
    public WriterTOptionalAssert<F, W, A> satisfiesValue(Consumer<? super A> requirements) {
      isPresent();
      A value = unwrap().get().first();
      try {
        requirements.accept(value);
      } catch (AssertionError e) {
        failWithMessage("Value did not satisfy requirements: %s", e.getMessage());
      }
      return this;
    }

    /**
     * Verifies that the output satisfies the given requirements.
     *
     * @param requirements the consumer to apply to the output
     * @return this assertion object for chaining
     * @throws AssertionError if not present or requirements not satisfied
     */
    public WriterTOptionalAssert<F, W, A> satisfiesOutput(Consumer<? super W> requirements) {
      isPresent();
      W output = unwrap().get().second();
      try {
        requirements.accept(output);
      } catch (AssertionError e) {
        failWithMessage("Output did not satisfy requirements: %s", e.getMessage());
      }
      return this;
    }

    /**
     * Verifies that the output matches the given predicate.
     *
     * @param predicate the predicate to test
     * @return this assertion object for chaining
     * @throws AssertionError if not present or predicate fails
     */
    public WriterTOptionalAssert<F, W, A> outputMatches(Predicate<? super W> predicate) {
      isPresent();
      W output = unwrap().get().second();
      if (!predicate.test(output)) {
        failWithMessage("Output <%s> did not match predicate", output);
      }
      return this;
    }

    /**
     * Verifies that this WriterT is equal to another WriterT by comparing unwrapped values.
     *
     * @param other the other WriterT to compare with
     * @return this assertion object for chaining
     * @throws AssertionError if the WriterT values are not equal
     */
    public WriterTOptionalAssert<F, W, A> isEqualToWriterT(
        Kind<WriterTKind.Witness<F, W>, A> other) {
      isNotNull();
      Optional<Pair<A, W>> thisUnwrapped = unwrap();

      if (other == null) {
        failWithMessage("Expected WriterT to compare with but was null");
        return this;
      }

      var otherWriterT = WriterTKindHelper.WRITER_T.narrow(other);
      Kind<F, Pair<A, W>> otherOuterKind = otherWriterT.run();
      Optional<Pair<A, W>> otherUnwrapped = outerUnwrapper.apply(otherOuterKind);

      if (!thisUnwrapped.equals(otherUnwrapped)) {
        failWithMessage(
            "Expected WriterT to be equal to <%s> but was <%s>", otherUnwrapped, thisUnwrapped);
      }
      return this;
    }
  }
}
