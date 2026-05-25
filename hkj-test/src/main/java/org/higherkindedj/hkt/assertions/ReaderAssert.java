// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.reader.ReaderKindHelper;

/**
 * Custom AssertJ assertions for {@link Reader} instances.
 *
 * @param <R> The type of the environment required by the Reader
 * @param <A> The type of the value produced by the Reader
 */
public class ReaderAssert<R, A> extends AbstractAssert<ReaderAssert<R, A>, Reader<R, A>> {

  /** Entry point accepting a {@code Kind<ReaderKind.Witness<R>, A>}. */
  public static <R, A> ReaderAssert<R, A> assertThatReader(Kind<ReaderKind.Witness<R>, A> actual) {
    return new ReaderAssert<>(ReaderKindHelper.READER.narrow(actual));
  }

  /** Entry point. */
  public static <R, A> ReaderAssert<R, A> assertThatReader(Reader<R, A> actual) {
    return new ReaderAssert<>(actual);
  }

  protected ReaderAssert(Reader<R, A> actual) {
    super(actual, ReaderAssert.class);
  }

  /** Begins a fluent assertion chain by specifying the environment to run the Reader with. */
  public ReaderResultAssert<R, A> whenRunWith(R environment) {
    isNotNull();
    return new ReaderResultAssert<>(actual, environment);
  }

  /**
   * Verifies that the Reader produces the same result when run multiple times with the same
   * environment (referential transparency).
   */
  public ReaderAssert<R, A> isPureWhenRunWith(R environment) {
    isNotNull();
    A firstResult = actual.run(environment);
    A secondResult = actual.run(environment);
    Assertions.assertThat(secondResult)
        .withFailMessage(
            "Expected Reader to be pure (produce same result on multiple runs) but got different"
                + " results: <%s> and <%s>",
            firstResult, secondResult)
        .isEqualTo(firstResult);
    return this;
  }

  /** Verifies that the Reader is constant (produces the same result regardless of environment). */
  public ReaderAssert<R, A> isConstantFor(R env1, R env2) {
    isNotNull();
    A result1 = actual.run(env1);
    A result2 = actual.run(env2);
    Assertions.assertThat(result2)
        .withFailMessage(
            "Expected Reader to be constant (produce same result for all environments) but got"
                + " <%s> for first environment and <%s> for second environment",
            result1, result2)
        .isEqualTo(result1);
    return this;
  }

  /**
   * Assertions on the result of running a Reader with a specific environment.
   *
   * <p>Extends {@link AbstractAssert} so AssertJ's standard fluent features ({@code as}, {@code
   * describedAs}, {@code overridingErrorMessage}) are available on the result.
   */
  public static class ReaderResultAssert<R, A> extends AbstractAssert<ReaderResultAssert<R, A>, A> {

    private final Reader<R, A> reader;
    private final R environment;

    ReaderResultAssert(Reader<R, A> reader, R environment) {
      super(reader.run(environment), ReaderResultAssert.class);
      this.reader = reader;
      this.environment = environment;
    }

    /** Verifies that the Reader produces the expected value. */
    public ReaderResultAssert<R, A> produces(A expected) {
      Assertions.assertThat(actual)
          .withFailMessage("Expected Reader to produce <%s> but produced <%s>", expected, actual)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the Reader produces a null value. */
    public ReaderResultAssert<R, A> producesNull() {
      Assertions.assertThat(actual).as("Reader result").isNull();
      return this;
    }

    /** Verifies that the Reader produces a non-null value. */
    public ReaderResultAssert<R, A> producesNonNull() {
      Assertions.assertThat(actual).as("Reader result").isNotNull();
      return this;
    }

    /** Verifies that the Reader's result satisfies the given requirements. */
    public ReaderResultAssert<R, A> satisfies(Consumer<? super A> requirements) {
      requirements.accept(actual);
      return this;
    }

    /** Verifies that the Reader's result matches the given predicate. */
    public ReaderResultAssert<R, A> matches(Predicate<? super A> predicate) {
      Assertions.assertThat(predicate.test(actual))
          .withFailMessage(
              "Expected Reader result to match predicate but <%s> did not match", actual)
          .isTrue();
      return this;
    }

    /** Verifies that the Reader's result matches the given predicate with a custom description. */
    public ReaderResultAssert<R, A> matches(Predicate<? super A> predicate, String description) {
      Assertions.assertThat(predicate.test(actual))
          .withFailMessage("%s but <%s> did not match", description, actual)
          .isTrue();
      return this;
    }

    /** Returns the actual result for further assertions. */
    public A getResult() {
      return actual;
    }
  }
}
