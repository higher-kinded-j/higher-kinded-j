// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.optics.indexed.Pair;

/**
 * Property-based tests for Lens laws applied to paired lenses.
 *
 * <p>These tests verify that {@link Lens#paired} produces lenses that satisfy the three fundamental
 * lens laws:
 *
 * <ul>
 *   <li><b>GetPut</b>: {@code set(get(s), s) == s}
 *   <li><b>PutGet</b>: {@code get(set(a, s)) == a}
 *   <li><b>PutPut</b>: {@code set(a2, set(a1, s)) == set(a2, s)}
 * </ul>
 *
 * <p>The tests use a {@code Range} record with an invariant ({@code lo <= hi}) to demonstrate that
 * paired lenses correctly handle coupled fields while still satisfying lens laws.
 */
class LensPairedLawsPropertyTest {

  /**
   * A range with an invariant: lo must be less than or equal to hi.
   *
   * <p>This is the canonical example of coupled fields that require atomic updates.
   */
  record Range(int lo, int hi) {
    Range {
      if (lo > hi) {
        throw new IllegalArgumentException(
            "Range invariant violated: lo (" + lo + ") must be <= hi (" + hi + ")");
      }
    }
  }

  /** Individual lens for the 'lo' field. */
  private static final Lens<Range, Integer> loLens =
      Lens.of(Range::lo, (r, lo) -> new Range(lo, r.hi()));

  /** Individual lens for the 'hi' field. */
  private static final Lens<Range, Integer> hiLens =
      Lens.of(Range::hi, (r, hi) -> new Range(r.lo(), hi));

  /** Paired lens that focuses on both bounds as a pair. */
  private static final Lens<Range, Pair<Integer, Integer>> boundsLens =
      Lens.paired(loLens, hiLens, Range::new);

  // ===== Arbitrary Providers =====

  /** Provides arbitrary valid Range instances. */
  @Provide
  Arbitrary<Range> validRanges() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .flatMap(
            lo ->
                Arbitraries.integers()
                    .between(lo, lo + 500) // Ensure hi >= lo
                    .map(hi -> new Range(lo, hi)));
  }

  /** Provides arbitrary valid bounds pairs (where first <= second). */
  @Provide
  Arbitrary<Pair<Integer, Integer>> validBounds() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .flatMap(
            lo ->
                Arbitraries.integers()
                    .between(lo, lo + 500) // Ensure second >= first
                    .map(hi -> Pair.of(lo, hi)));
  }

  // ===== Lens Law Properties =====

  /**
   * GetPut Law: Setting what you just got gives back the original structure.
   *
   * <p>{@code set(get(s), s) == s}
   *
   * <p>This law ensures that getting a value and immediately setting it back produces no change.
   */
  @Property
  @Label("GetPut Law: set(get(s), s) == s")
  void getPutLaw(@ForAll("validRanges") Range range) {
    Pair<Integer, Integer> gotten = boundsLens.get(range);
    Range roundTripped = boundsLens.set(gotten, range);

    assertThat(roundTripped).isEqualTo(range);
  }

  /**
   * PutGet Law: Getting what you just set gives back what you set.
   *
   * <p>{@code get(set(a, s)) == a}
   *
   * <p>This law ensures that setting a value and then getting it returns the value that was set.
   */
  @Property
  @Label("PutGet Law: get(set(a, s)) == a")
  void putGetLaw(
      @ForAll("validRanges") Range range, @ForAll("validBounds") Pair<Integer, Integer> bounds) {
    Range updated = boundsLens.set(bounds, range);
    Pair<Integer, Integer> gotten = boundsLens.get(updated);

    assertThat(gotten).isEqualTo(bounds);
  }

  /**
   * PutPut Law: Setting twice is the same as setting once with the second value.
   *
   * <p>{@code set(a2, set(a1, s)) == set(a2, s)}
   *
   * <p>This law ensures that the most recent set operation "wins" and intermediate sets have no
   * residual effect.
   */
  @Property
  @Label("PutPut Law: set(a2, set(a1, s)) == set(a2, s)")
  void putPutLaw(
      @ForAll("validRanges") Range range,
      @ForAll("validBounds") Pair<Integer, Integer> bounds1,
      @ForAll("validBounds") Pair<Integer, Integer> bounds2) {
    Range setTwice = boundsLens.set(bounds2, boundsLens.set(bounds1, range));
    Range setOnce = boundsLens.set(bounds2, range);

    assertThat(setTwice).isEqualTo(setOnce);
  }

  // ===== Additional Properties for Paired Lenses =====

  /**
   * Verify that paired lens get returns the correct tuple components.
   *
   * <p>The first component should match the first lens's get, and the second component should match
   * the second lens's get.
   */
  @Property
  @Label("Paired get extracts correct components: get(s) == (first.get(s), second.get(s))")
  void pairedGetExtractsCorrectComponents(@ForAll("validRanges") Range range) {
    Pair<Integer, Integer> bounds = boundsLens.get(range);

    assertThat(bounds.first()).isEqualTo(loLens.get(range));
    assertThat(bounds.second()).isEqualTo(hiLens.get(range));
  }

  /**
   * Verify that modify is consistent with get and set.
   *
   * <p>{@code modify(f, s) == set(f(get(s)), s)}
   */
  @Property
  @Label("Modify consistency: modify(f, s) == set(f(get(s)), s)")
  void modifyConsistency(@ForAll("validRanges") Range range) {
    // Use a transformation that preserves the invariant
    Function<Pair<Integer, Integer>, Pair<Integer, Integer>> shift =
        t -> Pair.of(t.first() + 10, t.second() + 10);

    Range viaModify = boundsLens.modify(shift, range);
    Range viaGetSet = boundsLens.set(shift.apply(boundsLens.get(range)), range);

    assertThat(viaModify).isEqualTo(viaGetSet);
  }

  /**
   * Verify that identity modification leaves the structure unchanged.
   *
   * <p>{@code modify(identity, s) == s}
   */
  @Property
  @Label("Identity modification: modify(identity, s) == s")
  void identityModification(@ForAll("validRanges") Range range) {
    Range modified = boundsLens.modify(t -> t, range);

    assertThat(modified).isEqualTo(range);
  }

  /**
   * Verify that composition of modifications works correctly.
   *
   * <p>{@code modify(g, modify(f, s)) == modify(g.compose(f), s)}
   */
  @Property
  @Label("Modification composition: modify(g, modify(f, s)) == modify(g ∘ f, s)")
  void modificationComposition(
      @ForAll("validRanges") Range range,
      @ForAll @IntRange(min = -100, max = 100) int shift1,
      @ForAll @IntRange(min = -100, max = 100) int shift2) {

    // Ensure shifts don't violate invariant
    int totalShift = shift1 + shift2;
    Assume.that(range.lo() + totalShift <= range.hi() + totalShift);

    Function<Pair<Integer, Integer>, Pair<Integer, Integer>> f =
        t -> Pair.of(t.first() + shift1, t.second() + shift1);
    Function<Pair<Integer, Integer>, Pair<Integer, Integer>> g =
        t -> Pair.of(t.first() + shift2, t.second() + shift2);

    Range sequential = boundsLens.modify(g, boundsLens.modify(f, range));
    Range composed = boundsLens.modify(t -> g.apply(f.apply(t)), range);

    assertThat(sequential).isEqualTo(composed);
  }

  // ===== Edge Case Properties =====

  /** Verify behaviour with zero-width range (lo == hi). */
  @Property
  @Label("Zero-width range: paired lens works when lo == hi")
  void zeroWidthRange(@ForAll @IntRange(min = -500, max = 500) int value) {
    Range point = new Range(value, value);

    Pair<Integer, Integer> bounds = boundsLens.get(point);
    assertThat(bounds.first()).isEqualTo(value);
    assertThat(bounds.second()).isEqualTo(value);

    // Shift the point
    Range shifted = boundsLens.modify(t -> Pair.of(t.first() + 1, t.second() + 1), point);
    assertThat(shifted.lo()).isEqualTo(value + 1);
    assertThat(shifted.hi()).isEqualTo(value + 1);
  }

  /** Verify that operations on maximum range values work correctly. */
  @Property
  @Label("Large range values: lens laws hold for large positive/negative values")
  void largeRangeValues(
      @ForAll @IntRange(min = -1_000_000, max = 0) int lo,
      @ForAll @IntRange(min = 0, max = 1_000_000) int hi) {
    Assume.that(lo <= hi);

    Range range = new Range(lo, hi);

    // GetPut
    assertThat(boundsLens.set(boundsLens.get(range), range)).isEqualTo(range);

    // PutGet with new bounds
    Pair<Integer, Integer> newBounds = Pair.of(lo + 1, hi + 1);
    assertThat(boundsLens.get(boundsLens.set(newBounds, range))).isEqualTo(newBounds);
  }
}
