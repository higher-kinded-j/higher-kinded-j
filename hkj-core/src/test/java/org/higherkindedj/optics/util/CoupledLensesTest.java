// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.higherkindedj.hkt.tuple.Tuple3;
import org.higherkindedj.hkt.tuple.Tuple5;
import org.higherkindedj.hkt.tuple.Tuple9;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CoupledLenses: N-ary atomic lens combinators above Lens.paired")
class CoupledLensesTest {

  /**
   * A 3-field record with a cross-field invariant: the lo/mid/hi triple must be ordered. The
   * canonical demonstration of why sequential lens updates can't reach a new valid triple.
   */
  record Triple(int lo, int mid, int hi) {
    Triple {
      if (!(lo <= mid && mid <= hi)) {
        throw new IllegalArgumentException(
            "Triple invariant violated: lo=" + lo + " mid=" + mid + " hi=" + hi);
      }
    }
  }

  private static final Lens<Triple, Integer> tripleLo =
      Lens.of(Triple::lo, (t, lo) -> new Triple(lo, t.mid(), t.hi()));
  private static final Lens<Triple, Integer> tripleMid =
      Lens.of(Triple::mid, (t, mid) -> new Triple(t.lo(), mid, t.hi()));
  private static final Lens<Triple, Integer> tripleHi =
      Lens.of(Triple::hi, (t, hi) -> new Triple(t.lo(), t.mid(), hi));

  @Nested
  @DisplayName("coupled3: the headline arity")
  class Coupled3 {

    private final Lens<Triple, Tuple3<Integer, Integer, Integer>> lens =
        CoupledLenses.coupled3(tripleLo, tripleMid, tripleHi, Triple::new);

    @Test
    @DisplayName("GetPut: setting back what was got is a no-op")
    void getPut() {
      Triple t = new Triple(1, 5, 10);
      assertThat(lens.set(lens.get(t), t)).isEqualTo(t);
    }

    @Test
    @DisplayName("PutGet: getting what was just set returns it unchanged")
    void putGet() {
      Triple t = new Triple(1, 5, 10);
      Tuple3<Integer, Integer, Integer> next = new Tuple3<>(2, 6, 11);
      assertThat(lens.get(lens.set(next, t))).isEqualTo(next);
    }

    @Test
    @DisplayName("PutPut: the second set wins")
    void putPut() {
      Triple t = new Triple(1, 5, 10);
      Tuple3<Integer, Integer, Integer> first = new Tuple3<>(2, 6, 11);
      Tuple3<Integer, Integer, Integer> second = new Tuple3<>(7, 8, 9);
      assertThat(lens.set(second, lens.set(first, t))).isEqualTo(lens.set(second, t));
    }

    @Test
    @DisplayName("Atomic reconstruction sidesteps intermediate invariant violations")
    void atomicReconstructionAvoidsInvariantBreak() {
      Triple before = new Triple(1, 5, 10);

      // The naive sequential update breaks: setting lo=20 first produces Triple(20, 5, 10).
      assertThatThrownBy(() -> tripleLo.set(20, before))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("lo=20 mid=5");

      // The coupled3 update shifts all three at once: no intermediate state ever exists.
      Triple shifted =
          lens.modify(t -> new Tuple3<>(t._1() + 19, t._2() + 16, t._3() + 12), before);
      assertThat(shifted).isEqualTo(new Triple(20, 21, 22));
    }

    @Test
    @DisplayName("Preserving overload keeps the source available to the reconstructor")
    void preservingOverloadReceivesSource() {
      Lens<Triple, Tuple3<Integer, Integer, Integer>> preserving =
          CoupledLenses.coupled3(
              tripleLo,
              tripleMid,
              tripleHi,
              (source, lo, mid, hi) -> {
                // The preserving form receives the original Triple before reconstruction.
                assertThat(source).isInstanceOf(Triple.class);
                return new Triple(lo, mid, hi);
              });
      assertThat(preserving.set(new Tuple3<>(3, 4, 5), new Triple(1, 5, 10)))
          .isEqualTo(new Triple(3, 4, 5));
    }
  }

  /**
   * A 5-field record demonstrating that the same atomic shape extends past the headline arity. The
   * invariant: {@code lo <= a <= b <= c <= hi}.
   */
  record Ladder(int lo, int a, int b, int c, int hi) {
    Ladder {
      if (!(lo <= a && a <= b && b <= c && c <= hi)) {
        throw new IllegalArgumentException(
            "Ladder must be monotonic: " + lo + "<=" + a + "<=" + b + "<=" + c + "<=" + hi);
      }
    }
  }

  @Nested
  @DisplayName("coupled5: middle of the ladder")
  class Coupled5 {

    private final Lens<Ladder, Tuple5<Integer, Integer, Integer, Integer, Integer>> lens =
        CoupledLenses.coupled5(
            Lens.of(Ladder::lo, (l, lo) -> new Ladder(lo, l.a(), l.b(), l.c(), l.hi())),
            Lens.of(Ladder::a, (l, a) -> new Ladder(l.lo(), a, l.b(), l.c(), l.hi())),
            Lens.of(Ladder::b, (l, b) -> new Ladder(l.lo(), l.a(), b, l.c(), l.hi())),
            Lens.of(Ladder::c, (l, c) -> new Ladder(l.lo(), l.a(), l.b(), c, l.hi())),
            Lens.of(Ladder::hi, (l, hi) -> new Ladder(l.lo(), l.a(), l.b(), l.c(), hi)),
            Ladder::new);

    @Test
    @DisplayName("modify reshuffles all 5 fields atomically")
    void modifyReshufflesAtomically() {
      Ladder before = new Ladder(1, 2, 3, 4, 5);
      Ladder doubled =
          lens.modify(
              t -> new Tuple5<>(t._1() * 2, t._2() * 2, t._3() * 2, t._4() * 2, t._5() * 2),
              before);
      assertThat(doubled).isEqualTo(new Ladder(2, 4, 6, 8, 10));
    }

    @Test
    @DisplayName("get reads all 5 fields into a Tuple5")
    void getReadsAllFields() {
      assertThat(lens.get(new Ladder(1, 2, 3, 4, 5))).isEqualTo(new Tuple5<>(1, 2, 3, 4, 5));
    }
  }

  @Nested
  @DisplayName("coupled9: top of the ladder, smoke test")
  class Coupled9 {

    record Nine(int a, int b, int c, int d, int e, int f, int g, int h, int i) {}

    @Test
    @DisplayName("a 9-field record round-trips through coupled9")
    void roundTrip() {
      Lens<Nine, Integer> la =
          Lens.of(
              Nine::a,
              (n, x) -> new Nine(x, n.b(), n.c(), n.d(), n.e(), n.f(), n.g(), n.h(), n.i()));
      Lens<Nine, Integer> lb =
          Lens.of(
              Nine::b,
              (n, x) -> new Nine(n.a(), x, n.c(), n.d(), n.e(), n.f(), n.g(), n.h(), n.i()));
      Lens<Nine, Integer> lc =
          Lens.of(
              Nine::c,
              (n, x) -> new Nine(n.a(), n.b(), x, n.d(), n.e(), n.f(), n.g(), n.h(), n.i()));
      Lens<Nine, Integer> ld =
          Lens.of(
              Nine::d,
              (n, x) -> new Nine(n.a(), n.b(), n.c(), x, n.e(), n.f(), n.g(), n.h(), n.i()));
      Lens<Nine, Integer> le =
          Lens.of(
              Nine::e,
              (n, x) -> new Nine(n.a(), n.b(), n.c(), n.d(), x, n.f(), n.g(), n.h(), n.i()));
      Lens<Nine, Integer> lf =
          Lens.of(
              Nine::f,
              (n, x) -> new Nine(n.a(), n.b(), n.c(), n.d(), n.e(), x, n.g(), n.h(), n.i()));
      Lens<Nine, Integer> lg =
          Lens.of(
              Nine::g,
              (n, x) -> new Nine(n.a(), n.b(), n.c(), n.d(), n.e(), n.f(), x, n.h(), n.i()));
      Lens<Nine, Integer> lh =
          Lens.of(
              Nine::h,
              (n, x) -> new Nine(n.a(), n.b(), n.c(), n.d(), n.e(), n.f(), n.g(), x, n.i()));
      Lens<Nine, Integer> li =
          Lens.of(
              Nine::i,
              (n, x) -> new Nine(n.a(), n.b(), n.c(), n.d(), n.e(), n.f(), n.g(), n.h(), x));

      Lens<
              Nine,
              Tuple9<
                  Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer>>
          lens = CoupledLenses.coupled9(la, lb, lc, ld, le, lf, lg, lh, li, Nine::new);

      Nine before = new Nine(1, 2, 3, 4, 5, 6, 7, 8, 9);
      Tuple9<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> got =
          lens.get(before);
      assertThat(got).isEqualTo(new Tuple9<>(1, 2, 3, 4, 5, 6, 7, 8, 9));

      Nine after = lens.set(new Tuple9<>(9, 8, 7, 6, 5, 4, 3, 2, 1), before);
      assertThat(after).isEqualTo(new Nine(9, 8, 7, 6, 5, 4, 3, 2, 1));
    }
  }
}
