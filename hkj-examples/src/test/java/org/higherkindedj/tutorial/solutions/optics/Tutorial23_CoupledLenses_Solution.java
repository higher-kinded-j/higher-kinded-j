// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.higherkindedj.hkt.tuple.Tuple3;
import org.higherkindedj.hkt.tuple.Tuple5;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.util.CoupledLenses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial 23: N-ary coupled lenses.
 *
 * <p>The exercise file uses {@code answerRequired()} placeholders; this solution shows the working
 * code with per-exercise teaching commentary.
 */
@DisplayName("Tutorial Solution 23: N-ary Coupled Lenses")
public class Tutorial23_CoupledLenses_Solution {

  record Triple(int lo, int mid, int hi) {
    Triple {
      if (!(lo <= mid && mid <= hi)) {
        throw new IllegalArgumentException(
            "Triple invariant violated: lo=" + lo + " mid=" + mid + " hi=" + hi);
      }
    }
  }

  static final Lens<Triple, Integer> tripleLo =
      Lens.of(Triple::lo, (t, lo) -> new Triple(lo, t.mid(), t.hi()));
  static final Lens<Triple, Integer> tripleMid =
      Lens.of(Triple::mid, (t, mid) -> new Triple(t.lo(), mid, t.hi()));
  static final Lens<Triple, Integer> tripleHi =
      Lens.of(Triple::hi, (t, hi) -> new Triple(t.lo(), t.mid(), hi));

  @Nested
  @DisplayName("Part 1: coupled3 for a 3-field invariant")
  class Coupled3 {

    /**
     * Why this is idiomatic: a record with a cross-field invariant (here, {@code lo <= mid <= hi})
     * cannot be reconfigured by chaining individual {@code .set(...)} calls; the first set produces
     * an intermediate value the constructor rejects. {@link CoupledLenses#coupled3} gathers all
     * three writes into one tuple and hands them to the constructor in a single call.
     *
     * <p>Alternative: write a {@code Triple::with(lo, mid, hi)} method by hand. Same effect, but
     * not composable with the optic graph: you cannot {@code .focus(...).set(...)} through it.
     *
     * <p>Common wrong attempt: assume the chained-set form will work because each lens is "correct
     * in isolation". The invariant is on the record, not on a lens; only an atomic reconstruction
     * satisfies it.
     */
    @Test
    @DisplayName("Exercise 1: shift all three fields at once")
    void exercise1_shiftAtomically() {
      Lens<Triple, Tuple3<Integer, Integer, Integer>> bounds =
          CoupledLenses.coupled3(tripleLo, tripleMid, tripleHi, Triple::new);

      Triple before = new Triple(1, 5, 10);
      Triple shifted =
          bounds.modify(t -> new Tuple3<>(t._1() + 10, t._2() + 10, t._3() + 10), before);

      assertThat(shifted).isEqualTo(new Triple(11, 15, 20));
    }

    /**
     * Why this is idiomatic: the sequential-set form is the temptation to flag explicitly. Showing
     * that it throws makes the value of {@code coupled3} concrete; the reader sees the failure mode
     * the combinator prevents.
     */
    @Test
    @DisplayName("Exercise 2: a chained-set attempt breaks the invariant; coupled3 doesn't")
    void exercise2_chainedSetBreaks() {
      Triple before = new Triple(1, 5, 10);

      // The naive chain: tripleLo.set(20, before) -> Triple(20, 5, 10) -> throws.
      assertThatThrownBy(() -> tripleLo.set(20, before))
          .isInstanceOf(IllegalArgumentException.class);

      // The same logical change through coupled3: one atomic reconstruction.
      Lens<Triple, Tuple3<Integer, Integer, Integer>> bounds =
          CoupledLenses.coupled3(tripleLo, tripleMid, tripleHi, Triple::new);
      Triple after = bounds.set(new Tuple3<>(20, 21, 22), before);

      assertThat(after).isEqualTo(new Triple(20, 21, 22));
    }
  }

  /**
   * A monotonic 5-tuple: {@code lo <= a <= b <= c <= hi}. The invariant generalises the {@code
   * Triple} idea to five fields, demonstrating that the same shape scales up the ladder.
   */
  record Ladder(int lo, int a, int b, int c, int hi) {
    Ladder {
      if (!(lo <= a && a <= b && b <= c && c <= hi)) {
        throw new IllegalArgumentException("Ladder must be monotonic: " + lo + "<=...<=" + hi);
      }
    }
  }

  @Nested
  @DisplayName("Part 2: the same shape at arity 5")
  class Coupled5 {

    /**
     * Why this is idiomatic: there is no special "5-field" technique; {@code coupled5} is just
     * {@code coupled3} with two more lenses and a {@code Function5} constructor reference.
     *
     * <p>Alternative: pair-of-pairs nesting via {@code coupled3 + paired} would technically work
     * but reads poorly and produces a nested {@code Tuple3<Tuple2, Tuple2, Integer>}; flat tuples
     * are easier to manipulate.
     *
     * <p>Common wrong attempt: reach for {@code paired} repeatedly. The arity ladder exists so that
     * {@code coupled5} is one call.
     */
    @Test
    @DisplayName("Exercise 3: double every value on a 5-field ladder, atomically")
    void exercise3_doubleAllFiveAtomically() {
      Lens<Ladder, Integer> lo =
          Lens.of(Ladder::lo, (l, x) -> new Ladder(x, l.a(), l.b(), l.c(), l.hi()));
      Lens<Ladder, Integer> a =
          Lens.of(Ladder::a, (l, x) -> new Ladder(l.lo(), x, l.b(), l.c(), l.hi()));
      Lens<Ladder, Integer> b =
          Lens.of(Ladder::b, (l, x) -> new Ladder(l.lo(), l.a(), x, l.c(), l.hi()));
      Lens<Ladder, Integer> c =
          Lens.of(Ladder::c, (l, x) -> new Ladder(l.lo(), l.a(), l.b(), x, l.hi()));
      Lens<Ladder, Integer> hi =
          Lens.of(Ladder::hi, (l, x) -> new Ladder(l.lo(), l.a(), l.b(), l.c(), x));

      Lens<Ladder, Tuple5<Integer, Integer, Integer, Integer, Integer>> all =
          CoupledLenses.coupled5(lo, a, b, c, hi, Ladder::new);

      Ladder before = new Ladder(1, 2, 3, 4, 5);
      Ladder doubled =
          all.modify(
              t -> new Tuple5<>(t._1() * 2, t._2() * 2, t._3() * 2, t._4() * 2, t._5() * 2),
              before);

      assertThat(doubled).isEqualTo(new Ladder(2, 4, 6, 8, 10));
    }
  }
}
