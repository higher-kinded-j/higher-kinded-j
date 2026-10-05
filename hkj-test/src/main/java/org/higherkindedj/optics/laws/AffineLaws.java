// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.higherkindedj.optics.Affine;

/**
 * Law-verification helpers for {@link Affine}: the conditional (zero-or-one) lens laws.
 *
 * <p>Flat {@code assert...} helpers in the same style as {@code org.higherkindedj.hkt.laws};
 * comparison is by {@code equals} — right for records.
 *
 * <p>Affines differ in what {@code set} does when the focus is absent. One whose last step can
 * build the value writes it, as {@code Affines.some()} and a {@code Lens.andThen(Prism)}
 * composition do; one that cannot leaves the target unchanged. {@link #assertAffineLaws} checks
 * only the laws every affine keeps. For an affine that must also leave an absent target alone, add
 * {@link #assertSetNoOpWhenAbsent}.
 */
public final class AffineLaws {

  private AffineLaws() {}

  /** Get-set on a present target: {@code getOptional(s) == Some(a) => set(a, s) == s}. */
  public static <S, A> void assertGetSetWhenPresent(Affine<S, A> affine, S presentSource) {
    Optional<A> got = affine.getOptional(presentSource);
    assertThat(got)
        .as("Affine get-set needs a PRESENT target; getOptional(%s) was empty", presentSource)
        .isPresent();
    S result = affine.set(got.orElseThrow(), presentSource);
    assertThat(result)
        .as(
            "Affine get-set: setting what you got changes nothing for %s; got %s",
            presentSource, result)
        .isEqualTo(presentSource);
  }

  /** Set-get on a present target: {@code getOptional(set(a, s)) == Some(a)}. */
  public static <S, A> void assertSetGetWhenPresent(Affine<S, A> affine, S presentSource, A a) {
    assertThat(affine.getOptional(presentSource))
        .as("Affine set-get needs a PRESENT target; getOptional(%s) was empty", presentSource)
        .isPresent();
    Optional<A> got = affine.getOptional(affine.set(a, presentSource));
    assertThat(got)
        .as(
            "Affine set-get: getOptional(set(%s, %s)) == Some(the value set); got %s",
            a, presentSource, got)
        .isEqualTo(Optional.of(a));
  }

  /** Set-set on a present target: the second set wins. */
  public static <S, A> void assertSetSetWhenPresent(
      Affine<S, A> affine, S presentSource, A a1, A a2) {
    assertThat(affine.getOptional(presentSource))
        .as("Affine set-set needs a PRESENT target; getOptional(%s) was empty", presentSource)
        .isPresent();
    S twice = affine.set(a2, affine.set(a1, presentSource));
    S once = affine.set(a2, presentSource);
    assertThat(twice)
        .as(
            "Affine set-set: set(%s, set(%s, %s)) == set once; got %s vs %s",
            a2, a1, presentSource, twice, once)
        .isEqualTo(once);
  }

  /**
   * Modify on an absent target is a no-op: {@code getOptional(s)} empty {@code => modify(f, s) ==
   * s}. Every affine keeps this law, whatever its {@code set} does on an absent target.
   */
  public static <S, A> void assertModifyNoOpWhenAbsent(Affine<S, A> affine, S absentSource, A a) {
    assertThat(affine.getOptional(absentSource))
        .as(
            "Affine modify-absence law needs an ABSENT target; getOptional(%s) was present",
            absentSource)
        .isEmpty();
    S result = affine.modify(ignored -> a, absentSource);
    assertThat(result)
        .as(
            "Affine modify-absence: modify on an absent target %s changes nothing; got %s",
            absentSource, result)
        .isEqualTo(absentSource);
  }

  /**
   * Absence is stable under {@code set}: on an absent target, {@code set} is a no-op and the target
   * stays absent.
   *
   * <p>Opt in to this law for an affine that guards absence. An affine whose last step can build
   * the value writes it on {@code set} by design, as {@code Affines.some()} and a {@code
   * Lens.andThen(Prism)} composition do, so it fails here and {@link #assertAffineLaws} does not
   * ask for it.
   */
  public static <S, A> void assertSetNoOpWhenAbsent(Affine<S, A> affine, S absentSource, A a) {
    assertThat(affine.getOptional(absentSource))
        .as("Affine absence law needs an ABSENT target; getOptional(%s) was present", absentSource)
        .isEmpty();
    S result = affine.set(a, absentSource);
    assertThat(result)
        .as(
            "Affine absence: set(%s, %s) on an absent target is a no-op; got %s",
            a, absentSource, result)
        .isEqualTo(absentSource);
  }

  /**
   * The laws every affine keeps, for one present and one absent fixture with distinct focus values:
   * get-set, set-get and set-set on the present target, and {@code modify} as a no-op on the absent
   * one. It does not check {@code set} on the absent target; add {@link #assertSetNoOpWhenAbsent}
   * for an affine that guards absence.
   */
  public static <S, A> void assertAffineLaws(
      Affine<S, A> affine, S presentSource, S absentSource, A a1, A a2) {
    Optional<A> current = affine.getOptional(presentSource);
    assertThat(current)
        .as("assertAffineLaws needs a PRESENT target; getOptional(%s) was empty", presentSource)
        .isPresent();
    assertThat(a1)
        .as(
            "assertAffineLaws needs focus values distinct from the current focus %s",
            current.orElseThrow())
        .isNotEqualTo(current.orElseThrow());
    assertThat(a2)
        .as(
            "assertAffineLaws needs focus values distinct from the current focus %s",
            current.orElseThrow())
        .isNotEqualTo(current.orElseThrow());
    assertThat(a1)
        .as("assertAffineLaws needs two DISTINCT focus values; both were %s", a1)
        .isNotEqualTo(a2);
    assertGetSetWhenPresent(affine, presentSource);
    assertSetGetWhenPresent(affine, presentSource, a1);
    assertSetGetWhenPresent(affine, presentSource, a2);
    assertSetSetWhenPresent(affine, presentSource, a1, a2);
    assertModifyNoOpWhenAbsent(affine, absentSource, a1);
  }
}
