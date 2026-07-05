// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.optics.Iso;

/**
 * Law-verification helpers for {@link Iso}: a lawful iso round-trips in both directions.
 *
 * <p>Flat {@code assert...} helpers in the same style as {@code org.higherkindedj.hkt.laws}; drive
 * coverage with {@code @ParameterizedTest} or property fixtures from the call site. Comparison is
 * by {@code equals} — right for records.
 */
public final class IsoLaws {

  private IsoLaws() {}

  /** Round-trip source: {@code reverseGet(get(s)) == s}. */
  public static <S, A> void assertGetReverseGet(Iso<S, A> iso, S s) {
    S roundTripped = iso.reverseGet(iso.get(s));
    assertThat(roundTripped)
        .as("Iso get-reverseGet: reverseGet(get(%s)) round-trips; got %s", s, roundTripped)
        .isEqualTo(s);
  }

  /** Round-trip focus: {@code get(reverseGet(a)) == a}. */
  public static <S, A> void assertReverseGetGet(Iso<S, A> iso, A a) {
    A roundTripped = iso.get(iso.reverseGet(a));
    assertThat(roundTripped)
        .as("Iso reverseGet-get: get(reverseGet(%s)) round-trips; got %s", a, roundTripped)
        .isEqualTo(a);
  }

  /** All iso laws for one fixture. */
  public static <S, A> void assertIsoLaws(Iso<S, A> iso, S s, A a) {
    assertGetReverseGet(iso, s);
    assertReverseGetGet(iso, a);
  }
}
