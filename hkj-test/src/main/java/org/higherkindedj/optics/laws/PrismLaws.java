// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.higherkindedj.optics.Prism;

/**
 * Law-verification helpers for {@link Prism}: build-match and the partial match-build round trip.
 *
 * <p>Flat {@code assert...} helpers in the same style as {@code org.higherkindedj.hkt.laws};
 * comparison is by {@code equals} — right for records.
 */
public final class PrismLaws {

  private PrismLaws() {}

  /** Build-match: {@code getOptional(build(a)) == Optional.of(a)}. */
  public static <S, A> void assertBuildMatch(Prism<S, A> prism, A a) {
    Optional<A> matched = prism.getOptional(prism.build(a));
    assertThat(matched)
        .as("Prism build-match: getOptional(build(%s)) == Optional.of(a); got %s", a, matched)
        .isEqualTo(Optional.of(a));
  }

  /** Match-build: when {@code getOptional(s)} matches, {@code build} reconstructs {@code s}. */
  public static <S, A> void assertMatchBuild(Prism<S, A> prism, S matchingSource) {
    Optional<A> matched = prism.getOptional(matchingSource);
    assertThat(matched)
        .as("Prism match-build needs a MATCHING source; getOptional(%s) was empty", matchingSource)
        .isPresent();
    S rebuilt = prism.build(matched.orElseThrow());
    assertThat(rebuilt)
        .as(
            "Prism match-build: build(getOptional(%s).get()) reconstructs it; got %s",
            matchingSource, rebuilt)
        .isEqualTo(matchingSource);
  }

  /** Sanity: a non-matching source yields {@code Optional.empty()}. */
  public static <S, A> void assertNoMatch(Prism<S, A> prism, S nonMatchingSource) {
    Optional<A> matched = prism.getOptional(nonMatchingSource);
    assertThat(matched)
        .as("Prism no-match: getOptional(%s) should be empty; got %s", nonMatchingSource, matched)
        .isEmpty();
  }

  /** All prism laws for one fixture (a matching source plus a non-matching source). */
  public static <S, A> void assertPrismLaws(
      Prism<S, A> prism, S matchingSource, S nonMatchingSource) {
    assertThat(matchingSource)
        .as(
            "assertPrismLaws needs DISTINCT matching and non-matching sources; both were %s",
            matchingSource)
        .isNotEqualTo(nonMatchingSource);
    Optional<A> matched = prism.getOptional(matchingSource);
    assertThat(matched)
        .as("assertPrismLaws needs a MATCHING source; getOptional(%s) was empty", matchingSource)
        .isPresent();
    assertBuildMatch(prism, matched.orElseThrow());
    assertMatchBuild(prism, matchingSource);
    assertNoMatch(prism, nonMatchingSource);
  }
}
