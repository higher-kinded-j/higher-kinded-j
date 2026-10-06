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
 * comparison is by {@code equals}, which suits records.
 *
 * <p>Affines differ in what {@code set} does when the focus is absent. One writes the value when
 * its last step can build it and every step before that is present, as {@code Affines.some()} and a
 * {@code Lens.andThen(Prism)} composition do; otherwise the target is left unchanged. {@link
 * #assertAffineLaws} checks the laws every lawful affine keeps, which allow either. For an affine
 * that must also leave an absent target alone, add {@link #assertSetNoOpWhenAbsent}.
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
   * Set-absence: on an absent target, {@code set} is a no-op and the target stays absent.
   *
   * <p>Opt in to this law for an affine that guards absence. An affine that writes an absent focus
   * on {@code set} by design, as {@code Affines.some()} and a {@code Lens.andThen(Prism)}
   * composition do, fails here, so {@link #assertAffineLaws} does not ask for it.
   */
  public static <S, A> void assertSetNoOpWhenAbsent(Affine<S, A> affine, S absentSource, A a) {
    assertThat(affine.getOptional(absentSource))
        .as(
            "Affine set-absence law needs an ABSENT target; getOptional(%s) was present",
            absentSource)
        .isEmpty();
    S result = affine.set(a, absentSource);
    assertThat(result)
        .as(
            "Affine set-absence: set(%s, %s) on an absent target is a no-op; got %s",
            a, absentSource, result)
        .isEqualTo(absentSource);
  }

  /**
   * Set on an absent target either changes nothing or writes a value that reads back: {@code set(a,
   * s) == s}, or {@code getOptional(set(a, s)) == Some(a)}. Every lawful affine keeps this, whether
   * it guards absence or writes through.
   */
  public static <S, A> void assertSetWhenAbsentLeavesOrWrites(
      Affine<S, A> affine, S absentSource, A a) {
    assertThat(affine.getOptional(absentSource))
        .as(
            "Affine set-when-absent law needs an ABSENT target; getOptional(%s) was present",
            absentSource)
        .isEmpty();
    S result = affine.set(a, absentSource);
    boolean leftAlone = result.equals(absentSource);
    boolean wroteIt = affine.getOptional(result).equals(Optional.of(a));
    assertThat(leftAlone || wroteIt)
        .as(
            "Affine set-when-absent: set(%s, %s) either changes nothing or writes a value that"
                + " reads back; got %s, which reads %s",
            a, absentSource, result, affine.getOptional(result))
        .isTrue();
  }

  /** Set-set on an absent target: the second set wins, whether or not the first one wrote. */
  public static <S, A> void assertSetSetWhenAbsent(
      Affine<S, A> affine, S absentSource, A a1, A a2) {
    assertThat(affine.getOptional(absentSource))
        .as(
            "Affine set-set-when-absent law needs an ABSENT target; getOptional(%s) was present",
            absentSource)
        .isEmpty();
    S twice = affine.set(a2, affine.set(a1, absentSource));
    S once = affine.set(a2, absentSource);
    assertThat(twice)
        .as(
            "Affine set-set on an absent target: set(%s, set(%s, %s)) == set once; got %s vs %s",
            a2, a1, absentSource, twice, once)
        .isEqualTo(once);
  }

  /**
   * The laws every lawful affine keeps, for one present and one absent fixture with distinct focus
   * values: get-set, set-get and set-set on the present target; and on the absent one, {@code
   * modify} changes nothing, set-set holds, and {@code set} either changes nothing or writes a
   * value that reads back. Add {@link #assertSetNoOpWhenAbsent} for an affine that must leave an
   * absent target alone.
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
    assertSetWhenAbsentLeavesOrWrites(affine, absentSource, a1);
    assertSetSetWhenAbsent(affine, absentSource, a1, a2);
  }
}
