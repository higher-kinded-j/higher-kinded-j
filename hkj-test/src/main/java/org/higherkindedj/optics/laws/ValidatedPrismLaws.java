// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * Law-verification helpers for {@link ValidatedPrism}: both round-trip laws of a lawful validated
 * boundary.
 *
 * <p>Flat {@code assert...} helpers in the same style as the other optic-law classes; comparison is
 * by {@code equals} — right for records.
 */
public final class ValidatedPrismLaws {

  private ValidatedPrismLaws() {}

  /** Parse-build: {@code parse(build(a)) == Valid(a)}. */
  public static <S, A> void assertParseBuild(ValidatedPrism<S, A> prism, A a) {
    Validated<NonEmptyList<FieldError>, A> parsed = prism.parse(prism.build(a));
    assertThat(parsed)
        .as("ValidatedPrism parse-build: parse(build(%s)) == Valid(a)", a)
        .isEqualTo(Validated.validNel(a));
  }

  /**
   * Build-parse (the section law): {@code parse(s) == Valid(a)} implies {@code build(a) == s} —
   * forbids a lossy parse-normalise that breaks the round trip.
   */
  public static <S, A> void assertBuildParse(ValidatedPrism<S, A> prism, S parsingSource) {
    Validated<NonEmptyList<FieldError>, A> parsed = prism.parse(parsingSource);
    assertThat(parsed.isValid())
        .as(
            "ValidatedPrism build-parse needs a PARSING source; parse(%s) was %s",
            parsingSource, parsed)
        .isTrue();
    S rebuilt = prism.build(parsed.get());
    assertThat(rebuilt)
        .as(
            "ValidatedPrism build-parse: build(parse(%s).get()) reconstructs it; got %s",
            parsingSource, rebuilt)
        .isEqualTo(parsingSource);
  }

  /** Sanity: a non-parsing source yields {@code Invalid}. */
  public static <S, A> void assertNoParse(ValidatedPrism<S, A> prism, S nonParsingSource) {
    Validated<NonEmptyList<FieldError>, A> parsed = prism.parse(nonParsingSource);
    assertThat(parsed.isInvalid())
        .as(
            "ValidatedPrism no-parse: parse(%s) should be Invalid; got %s",
            nonParsingSource, parsed)
        .isTrue();
  }

  /** All laws for one fixture (a parsing source plus a non-parsing source). */
  public static <S, A> void assertValidatedPrismLaws(
      ValidatedPrism<S, A> prism, S parsingSource, S nonParsingSource) {
    assertThat(parsingSource)
        .as(
            "assertValidatedPrismLaws needs DISTINCT parsing and non-parsing sources; both were %s",
            parsingSource)
        .isNotEqualTo(nonParsingSource);
    Validated<NonEmptyList<FieldError>, A> parsed = prism.parse(parsingSource);
    assertThat(parsed.isValid())
        .as(
            "assertValidatedPrismLaws needs a PARSING source; parse(%s) was %s",
            parsingSource, parsed)
        .isTrue();
    assertParseBuild(prism, parsed.get());
    assertBuildParse(prism, parsingSource);
    assertNoParse(prism, nonParsingSource);
  }
}
