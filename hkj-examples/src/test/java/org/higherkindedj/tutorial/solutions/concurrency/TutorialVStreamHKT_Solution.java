// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamAlternative;
import org.higherkindedj.hkt.vstream.VStreamApplicative;
import org.higherkindedj.hkt.vstream.VStreamFunctor;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamMonad;
import org.higherkindedj.hkt.vstream.VStreamTraverse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial: VStream HKT - Type Class Integration for VStream
 *
 * <p>Each exercise solution replaces the answerRequired() placeholder with the correct code.
 */
@DisplayName("Tutorial Solution: VStream HKT")
public class TutorialVStreamHKT_Solution {

  // ===========================================================================
  // Part 1: Widen and Narrow
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Widen and Narrow")
  class WidenAndNarrow {

    @Test
    @DisplayName("Exercise 1: Widen a VStream to Kind")
    void exercise1_widenVStreamToKind() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      // SOLUTION: Use VSTREAM.widen() to convert to Kind
      Kind<VStreamKind.Witness, Integer> kind = VSTREAM.widen(stream);

      assertThat(kind).isSameAs(stream);
    }

    @Test
    @DisplayName("Exercise 2: Narrow a Kind back to VStream")
    void exercise2_narrowKindToVStream() {
      Kind<VStreamKind.Witness, String> kind = VSTREAM.widen(VStream.of("hello", "world"));

      // SOLUTION: Use VSTREAM.narrow() to convert back to VStream
      VStream<String> stream = VSTREAM.narrow(kind);

      assertThat(stream.toList().run()).containsExactly("hello", "world");
    }
  }

  // ===========================================================================
  // Part 2: Functor and Applicative
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Functor and Applicative")
  class FunctorAndApplicative {

    @Test
    @DisplayName("Exercise 3: Functor map via HKT")
    void exercise3_functorMapViaHKT() {
      VStreamFunctor functor = VStreamFunctor.INSTANCE;
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      // SOLUTION: Use functor.map to double each element
      Kind<VStreamKind.Witness, Integer> doubled = functor.map(i -> i * 2, stream);

      assertThat(VSTREAM.narrow(doubled).toList().run()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("Exercise 4: Applicative of creates singleton stream")
    void exercise4_applicativeOf() {
      VStreamApplicative applicative = VStreamApplicative.INSTANCE;

      // SOLUTION: Use applicative.of to lift a value into VStream
      Kind<VStreamKind.Witness, Integer> result = applicative.of(42);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(42);
    }

    @Test
    @DisplayName("Exercise 5: Applicative ap (Cartesian product)")
    void exercise5_applicativeAp() {
      VStreamApplicative applicative = VStreamApplicative.INSTANCE;

      Kind<VStreamKind.Witness, Function<Integer, String>> fns =
          VSTREAM.widen(VStream.of(i -> "x" + i, i -> "y" + i));
      Kind<VStreamKind.Witness, Integer> values = VSTREAM.widen(VStream.of(1, 2));

      // SOLUTION: Use applicative.ap for Cartesian product
      Kind<VStreamKind.Witness, String> result = applicative.ap(fns, values);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly("x1", "x2", "y1", "y2");
    }
  }

  // ===========================================================================
  // Part 3: Monad
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Monad")
  class MonadTests {

    @Test
    @DisplayName("Exercise 6: Monad flatMap via HKT")
    void exercise6_monadFlatMap() {
      VStreamMonad monad = VStreamMonad.INSTANCE;
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      // SOLUTION: Use monad.flatMap to expand each element
      Kind<VStreamKind.Witness, Integer> result =
          monad.flatMap(i -> VSTREAM.widen(VStream.of(i, i * 10)), stream);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  // ===========================================================================
  // Part 4: Foldable and Alternative
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Foldable and Alternative")
  class FoldableAndAlternative {

    @Test
    @DisplayName("Exercise 7: Foldable foldMap with sum monoid")
    void exercise7_foldableFoldMap() {
      VStreamTraverse foldable = VStreamTraverse.INSTANCE;
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(10, 20, 30));

      Monoid<Integer> sumMonoid =
          new Monoid<>() {
            @Override
            public Integer empty() {
              return 0;
            }

            @Override
            public Integer combine(Integer a, Integer b) {
              return a + b;
            }
          };

      // SOLUTION: Use foldable.foldMap with sum monoid
      Integer sum = foldable.foldMap(sumMonoid, Function.identity(), stream);

      assertThat(sum).isEqualTo(60);
    }

    @Test
    @DisplayName("Exercise 8: Alternative orElse for concatenation")
    void exercise8_alternativeOrElse() {
      VStreamAlternative alt = VStreamAlternative.INSTANCE;

      Kind<VStreamKind.Witness, Integer> first = VSTREAM.widen(VStream.of(1, 2));
      Kind<VStreamKind.Witness, Integer> second = VSTREAM.widen(VStream.of(3, 4));

      // SOLUTION: Use alt.orElse for concatenation
      Kind<VStreamKind.Witness, Integer> combined = alt.orElse(first, () -> second);

      assertThat(VSTREAM.narrow(combined).toList().run()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Exercise 9: Alternative guard")
    void exercise9_alternativeGuard() {
      VStreamAlternative alt = VStreamAlternative.INSTANCE;

      // SOLUTION: Use alt.guard(true) and alt.guard(false)
      Kind<VStreamKind.Witness, Unit> trueResult = alt.guard(true);
      Kind<VStreamKind.Witness, Unit> falseResult = alt.guard(false);

      assertThat(VSTREAM.narrow(trueResult).toList().run()).containsExactly(Unit.INSTANCE);
      assertThat(VSTREAM.narrow(falseResult).toList().run()).isEmpty();
    }

    @Test
    @DisplayName("Exercise 10: Combine Monad and Foldable")
    void exercise10_combineMonadAndFoldable() {
      VStreamMonad monad = VStreamMonad.INSTANCE;
      VStreamTraverse foldable = VStreamTraverse.INSTANCE;
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      Monoid<Integer> sumMonoid =
          new Monoid<>() {
            @Override
            public Integer empty() {
              return 0;
            }

            @Override
            public Integer combine(Integer a, Integer b) {
              return a + b;
            }
          };

      // SOLUTION: First expand with flatMap, then fold with foldMap
      Kind<VStreamKind.Witness, Integer> expanded =
          monad.flatMap(i -> VSTREAM.widen(VStream.of(i, i + 1)), stream);
      Integer sum = foldable.foldMap(sumMonoid, Function.identity(), expanded);

      assertThat(sum).isEqualTo(15);
    }
  }
}
