// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.higherkindedj.hkt.reader_t.ReaderTKindHelper;

/** Custom AssertJ assertion for ReaderT transformer types. */
public final class ReaderTAssert {

  private ReaderTAssert() {}

  /** Entry point for ReaderT assertions when the outer monad unwraps to Optional. */
  public static <F extends WitnessArity<TypeArity.Unary>, R_ENV, A>
      ReaderTOptionalAssert<F, R_ENV, A> assertThatReaderT(
          Kind<ReaderTKind.Witness<F, R_ENV>, A> actual,
          Function<Kind<F, A>, Optional<A>> outerUnwrapper) {
    return new ReaderTOptionalAssert<>(actual, outerUnwrapper);
  }

  /** Specialised assertion for ReaderT with Optional-shaped outer monad. */
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

    /** Runs the ReaderT and returns a result assertion for the computed value. */
    public ReaderTResultAssert<F, R_ENV, A> whenRunWith(R_ENV env) {
      isNotNull();
      var readerT = ReaderTKindHelper.READER_T.narrow(actual);
      Kind<F, A> result = readerT.run().apply(env);
      Optional<A> unwrapped = outerUnwrapper.apply(result);
      return new ReaderTResultAssert<>(unwrapped, env);
    }

    /** Verifies that this ReaderT is equal to another ReaderT for the given environment. */
    public ReaderTOptionalAssert<F, R_ENV, A> isEqualToReaderT(
        Kind<ReaderTKind.Witness<F, R_ENV>, A> other, R_ENV env) {
      isNotNull();
      Assertions.assertThat(other).as("other ReaderT").isNotNull();
      var thisReaderT = ReaderTKindHelper.READER_T.narrow(actual);
      Optional<A> thisResult = outerUnwrapper.apply(thisReaderT.run().apply(env));
      var otherReaderT = ReaderTKindHelper.READER_T.narrow(other);
      Optional<A> otherResult = outerUnwrapper.apply(otherReaderT.run().apply(env));
      Assertions.assertThat(thisResult)
          .withFailMessage(
              "Expected ReaderT to be equal to <%s> but was <%s> for environment <%s>",
              otherResult, thisResult, env)
          .isEqualTo(otherResult);
      return this;
    }
  }

  /**
   * Assertion class for the result of running a ReaderT with a specific environment.
   *
   * <p>Extends {@link AbstractAssert} so AssertJ's standard fluent features ({@code as}, {@code
   * describedAs}, {@code overridingErrorMessage}) are available on the unwrapped result.
   */
  public static class ReaderTResultAssert<F extends WitnessArity<TypeArity.Unary>, R_ENV, A>
      extends AbstractAssert<ReaderTResultAssert<F, R_ENV, A>, Optional<A>> {

    private final R_ENV environment;

    protected ReaderTResultAssert(Optional<A> result, R_ENV environment) {
      super(result, ReaderTResultAssert.class);
      this.environment = environment;
    }

    /** Verifies that the result is empty (outer monad was empty). */
    public ReaderTResultAssert<F, R_ENV, A> isEmpty() {
      Assertions.assertThat(actual)
          .withFailMessage(
              () ->
                  "Expected result to be empty for environment <"
                      + environment
                      + "> but was present with: <"
                      + actual.orElse(null)
                      + ">")
          .isEmpty();
      return this;
    }

    /** Verifies that the result is present (not empty). */
    public ReaderTResultAssert<F, R_ENV, A> isPresent() {
      Assertions.assertThat(actual)
          .withFailMessage(
              "Expected result to be present for environment <%s> but was empty", environment)
          .isPresent();
      return this;
    }

    /** Verifies that the result contains a value equal to the expected value. */
    public ReaderTResultAssert<F, R_ENV, A> hasValue(A expected) {
      isPresent();
      A actualV = actual.get();
      Assertions.assertThat(actualV)
          .withFailMessage(
              "Expected value to be <%s> for environment <%s> but was <%s>",
              expected, environment, actualV)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the result value satisfies the given requirements. */
    public ReaderTResultAssert<F, R_ENV, A> satisfiesValue(Consumer<? super A> requirements) {
      isPresent();
      requirements.accept(actual.get());
      return this;
    }

    /** Verifies that the result value matches the given predicate. */
    public ReaderTResultAssert<F, R_ENV, A> valueMatches(Predicate<? super A> predicate) {
      isPresent();
      A value = actual.get();
      Assertions.assertThat(predicate.test(value))
          .withFailMessage(
              "Value <%s> did not match predicate for environment <%s>", value, environment)
          .isTrue();
      return this;
    }
  }
}
