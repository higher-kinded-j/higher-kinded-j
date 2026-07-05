// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.optics.Lens;

/**
 * Law-verification helpers for {@link Lens}: get-set, set-get and set-set.
 *
 * <p>Flat {@code assert...} helpers in the same style as {@code org.higherkindedj.hkt.laws};
 * comparison is by {@code equals} — right for records.
 */
public final class LensLaws {

  private LensLaws() {}

  /** Get-set: {@code set(get(s), s) == s} — setting what you got changes nothing. */
  public static <S, A> void assertGetSet(Lens<S, A> lens, S s) {
    S result = lens.set(lens.get(s), s);
    assertThat(result)
        .as("Lens get-set: set(get(s), s) == s for s=%s; got %s", s, result)
        .isEqualTo(s);
  }

  /** Set-get: {@code get(set(a, s)) == a} — you get back what you set. */
  public static <S, A> void assertSetGet(Lens<S, A> lens, S s, A a) {
    A result = lens.get(lens.set(a, s));
    assertThat(result)
        .as("Lens set-get: get(set(%s, %s)) == the value set; got %s", a, s, result)
        .isEqualTo(a);
  }

  /** Set-set: {@code set(a2, set(a1, s)) == set(a2, s)} — the second set wins. */
  public static <S, A> void assertSetSet(Lens<S, A> lens, S s, A a1, A a2) {
    S twice = lens.set(a2, lens.set(a1, s));
    S once = lens.set(a2, s);
    assertThat(twice)
        .as(
            "Lens set-set: set(%s, set(%s, %s)) == set(%s, s); got %s vs %s",
            a2, a1, s, a2, twice, once)
        .isEqualTo(once);
  }

  /** All lens laws for one fixture. Guards against vacuous set-set by requiring distinct values. */
  public static <S, A> void assertLensLaws(Lens<S, A> lens, S s, A a1, A a2) {
    A current = lens.get(s);
    assertThat(a1)
        .as("assertLensLaws needs focus values distinct from the current focus %s", current)
        .isNotEqualTo(current);
    assertThat(a2)
        .as("assertLensLaws needs focus values distinct from the current focus %s", current)
        .isNotEqualTo(current);
    assertThat(a1)
        .as("assertLensLaws needs two DISTINCT focus values; both were %s", a1)
        .isNotEqualTo(a2);
    assertGetSet(lens, s);
    assertSetGet(lens, s, a1);
    assertSetGet(lens, s, a2);
    assertSetSet(lens, s, a1, a2);
  }
}
