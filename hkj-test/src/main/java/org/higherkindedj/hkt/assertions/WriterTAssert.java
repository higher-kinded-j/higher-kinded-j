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
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.higherkindedj.hkt.writer_t.WriterTKindHelper;

/** Custom AssertJ assertion for WriterT transformer types. */
public final class WriterTAssert {

  private WriterTAssert() {}

  /** Entry point for WriterT assertions when the outer monad unwraps to Optional. */
  public static <F extends WitnessArity<TypeArity.Unary>, W, A>
      WriterTOptionalAssert<F, W, A> assertThatWriterT(
          Kind<WriterTKind.Witness<F, W>, A> actual,
          Function<Kind<F, Pair<A, W>>, Optional<Pair<A, W>>> outerUnwrapper) {
    return new WriterTOptionalAssert<>(actual, outerUnwrapper);
  }

  /** Specialised assertion for WriterT with Optional-shaped outer monad. */
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
      var writerT = WriterTKindHelper.WRITER_T.narrow(actual);
      return outerUnwrapper.apply(writerT.run());
    }

    /** Verifies that the outer monad is empty. */
    public WriterTOptionalAssert<F, W, A> isEmpty() {
      isNotNull();
      Optional<Pair<A, W>> u = unwrap();
      Assertions.assertThat(u)
          .withFailMessage(
              () ->
                  "Expected outer monad to be empty but was present with: <" + u.orElse(null) + ">")
          .isEmpty();
      return this;
    }

    /** Verifies that the outer monad is present (not empty). */
    public WriterTOptionalAssert<F, W, A> isPresent() {
      isNotNull();
      Assertions.assertThat(unwrap())
          .withFailMessage("Expected outer monad to be present but was empty")
          .isPresent();
      return this;
    }

    /** Verifies that the WriterT contains a value equal to the expected value. */
    public WriterTOptionalAssert<F, W, A> hasValue(A expected) {
      isPresent();
      A actualV = unwrap().get().first();
      Assertions.assertThat(actualV)
          .withFailMessage("Expected value to be <%s> but was <%s>", expected, actualV)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the WriterT contains output equal to the expected output. */
    public WriterTOptionalAssert<F, W, A> hasOutput(W expected) {
      isPresent();
      W actualW = unwrap().get().second();
      Assertions.assertThat(actualW)
          .withFailMessage("Expected output to be <%s> but was <%s>", expected, actualW)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the WriterT contains both the expected value and output. */
    public WriterTOptionalAssert<F, W, A> hasPair(A expectedValue, W expectedOutput) {
      isPresent();
      Pair<A, W> pair = unwrap().get();
      boolean matches =
          Objects.equals(pair.first(), expectedValue)
              && Objects.equals(pair.second(), expectedOutput);
      Assertions.assertThat(matches)
          .withFailMessage(
              "Expected Pair<%s, %s> but was Pair<%s, %s>",
              expectedValue, expectedOutput, pair.first(), pair.second())
          .isTrue();
      return this;
    }

    /** Verifies that the value satisfies the given requirements. */
    public WriterTOptionalAssert<F, W, A> satisfiesValue(Consumer<? super A> requirements) {
      isPresent();
      requirements.accept(unwrap().get().first());
      return this;
    }

    /** Verifies that the output satisfies the given requirements. */
    public WriterTOptionalAssert<F, W, A> satisfiesOutput(Consumer<? super W> requirements) {
      isPresent();
      requirements.accept(unwrap().get().second());
      return this;
    }

    /** Verifies that the output matches the given predicate. */
    public WriterTOptionalAssert<F, W, A> outputMatches(Predicate<? super W> predicate) {
      isPresent();
      W output = unwrap().get().second();
      Assertions.assertThat(predicate.test(output))
          .withFailMessage("Output <%s> did not match predicate", output)
          .isTrue();
      return this;
    }

    /** Verifies that this WriterT is equal to another WriterT by comparing unwrapped values. */
    public WriterTOptionalAssert<F, W, A> isEqualToWriterT(
        Kind<WriterTKind.Witness<F, W>, A> other) {
      isNotNull();
      Assertions.assertThat(other).as("other WriterT").isNotNull();
      var otherWriterT = WriterTKindHelper.WRITER_T.narrow(other);
      Optional<Pair<A, W>> otherUnwrapped = outerUnwrapper.apply(otherWriterT.run());
      Optional<Pair<A, W>> thisUnwrapped = unwrap();
      Assertions.assertThat(thisUnwrapped)
          .withFailMessage(
              "Expected WriterT to be equal to <%s> but was <%s>", otherUnwrapped, thisUnwrapped)
          .isEqualTo(otherUnwrapped);
      return this;
    }
  }
}
