// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
 * Tutorial 23: N-ary coupled lenses.
 *
 * <p>Pain to Promise. A record with a cross-field invariant (a {@code Range} where {@code lo <=
 * hi}, a {@code Trade} with consistent {@code currency}/{@code precision}/{@code amount}, the
 * classic monotonic triple) cannot be reconfigured one field at a time without producing an
 * intermediate value the constructor rejects. {@link Lens#paired} solves this for two fields;
 * {@link CoupledLenses} extends the same shape to {@code coupled3} ... {@code coupled9}.
 *
 * <pre>
 *   Lens&lt;Triple, Tuple3&lt;Integer, Integer, Integer&gt;&gt; bounds =
 *       CoupledLenses.coupled3(loLens, midLens, hiLens, Triple::new);
 *
 *   Triple shifted = bounds.modify(
 *       t -&gt; new Tuple3&lt;&gt;(t._1() + 10, t._2() + 10, t._3() + 10),
 *       new Triple(1, 5, 10));
 *   // Triple(11, 15, 20) - constructor only ever sees the new, valid value.
 * </pre>
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@link CoupledLenses#coupled3} ... {@link CoupledLenses#coupled9 coupled9} are the arity
 *       ladder above {@link Lens#paired};
 *   <li>each method has two overloads: a preserving form taking {@code (S, A, B, C, ...) -> S} and
 *       a simple form taking the constructor {@code (A, B, C, ...) -> S};
 *   <li>the returned {@code Lens<S, TupleN<...>>} reads all focused fields together and rebuilds
 *       the source from a new tuple in one constructor call, so invariants are checked once.
 * </ul>
 *
 * <p>Limits, stated up front: the ladder caps at 9 (cross-field invariants past 5 are rare and past
 * 9 vanishing); the underlying combinator is value-level glue over {@link Lens#of}, so it does not
 * interact with batching, traversals, or the effect path API.
 *
 * <p>Prerequisites: Complete Tutorial 01 (Lens Basics) and read the Coupled Fields chapter.
 *
 * <p>Estimated time: ~10 minutes.
 *
 * <p>Replace each {@code answerRequired()} placeholder with the correct code to make the tests
 * pass.
 */
@DisplayName("Tutorial 23: N-ary Coupled Lenses")
public class Tutorial23_CoupledLenses {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

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
     * Exercise 1: atomic shift.
     *
     * <p>Task: build a coupled lens over {@code tripleLo}, {@code tripleMid}, {@code tripleHi} so
     * that adding 10 to every field happens in a single reconstruction.
     *
     * <pre>
     *   // Hint 1: {@link CoupledLenses#coupled3} takes three lenses and a {@code Triple::new}
     *   //         constructor reference.
     *   // Hint 2: the result is a {@code Lens<Triple, Tuple3<Integer, Integer, Integer>>}.
     *   // Hint 3: use {@code .modify(t -> new Tuple3<>(t._1() + 10, ...), before)}.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: shift all three fields at once")
    void exercise1_shiftAtomically() {
      // TODO: build the coupled3 lens.
      Lens<Triple, Tuple3<Integer, Integer, Integer>> bounds = answerRequired();

      Triple before = new Triple(1, 5, 10);
      Triple shifted =
          bounds.modify(t -> new Tuple3<>(t._1() + 10, t._2() + 10, t._3() + 10), before);

      assertThat(shifted).isEqualTo(new Triple(11, 15, 20));
    }

    /**
     * Exercise 2: the failure mode you avoid.
     *
     * <p>Task: confirm that setting one field at a time throws (the intermediate value violates the
     * invariant), then make the same logical change atomically through {@code coupled3}.
     *
     * <pre>
     *   // Hint 1: {@code tripleLo.set(20, before)} produces an intermediate Triple(20, 5, 10).
     *   // Hint 2: catch the IllegalArgumentException to assert the failure.
     *   // Hint 3: then build the coupled3 lens and use {@code .set(new Tuple3<>(20, 21, 22), before)}.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: a chained-set attempt breaks the invariant; coupled3 doesn't")
    void exercise2_chainedSetBreaks() {
      Triple before = new Triple(1, 5, 10);

      // TODO: assert that the naive chained-set form throws.
      assertThatThrownBy(answerRequired()).isInstanceOf(IllegalArgumentException.class);

      // TODO: build the coupled3 lens and apply the same change atomically.
      Triple after = answerRequired();

      assertThat(after).isEqualTo(new Triple(20, 21, 22));
    }
  }

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
     * Exercise 3: scale-up.
     *
     * <p>Task: double every value of a 5-field monotonic ladder, atomically. The lens declarations
     * for the five fields are written out; use them with {@link CoupledLenses#coupled5}.
     *
     * <pre>
     *   // Hint 1: signature is {@code CoupledLenses.coupled5(l1, l2, l3, l4, l5, Ladder::new)}.
     *   // Hint 2: doubled value is {@code new Tuple5<>(t._1() * 2, ..., t._5() * 2)}.
     *   // Hint 3: no special technique - the arity ladder is just more parameters.
     * </pre>
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

      // TODO: build the coupled5 lens.
      Lens<Ladder, Tuple5<Integer, Integer, Integer, Integer, Integer>> all = answerRequired();

      Ladder before = new Ladder(1, 2, 3, 4, 5);
      Ladder doubled =
          all.modify(
              t -> new Tuple5<>(t._1() * 2, t._2() * 2, t._3() * 2, t._4() * 2, t._5() * 2),
              before);

      assertThat(doubled).isEqualTo(new Ladder(2, 4, 6, 8, 10));
    }
  }
}
