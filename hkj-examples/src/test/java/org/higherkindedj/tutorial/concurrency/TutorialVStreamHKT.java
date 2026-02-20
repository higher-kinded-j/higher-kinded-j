// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

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
 * Tutorial: VStream HKT - Type Class Integration for VStream
 *
 * <p>This tutorial covers VStream's integration into Higher-Kinded-J's type-safe HKT simulation.
 * You will learn to widen/narrow VStreams, use type class instances (Functor, Applicative, Monad,
 * Foldable, Alternative), and write polymorphic functions.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>VStreamKind.Witness: phantom type marker for VStream in the Kind system
 *   <li>VStreamKindHelper.VSTREAM: enum singleton for widen/narrow operations
 *   <li>VStreamFunctor: lazy element-wise map via Kind interface
 *   <li>VStreamApplicative: of and Cartesian product ap
 *   <li>VStreamMonad: flatMap with stream substitution and flattening
 *   <li>VStreamTraverse: foldMap (Foldable) and traverse (Traverse)
 *   <li>VStreamAlternative: empty and orElse (concatenation semantics)
 * </ul>
 *
 * <p>Prerequisites: Complete the VStream Basics tutorial first.
 *
 * <p>Estimated time: 12-15 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: VStream HKT")
public class TutorialVStreamHKT {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Widen and Narrow
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Widen and Narrow")
  class WidenAndNarrow {

    /**
     * Exercise 1: Widen a VStream to Kind
     *
     * <p>Use VSTREAM.widen() to convert a concrete VStream into its higher-kinded representation.
     * This allows VStream to be used with polymorphic type class functions.
     *
     * <p>Task: Widen the VStream to a Kind
     */
    @Test
    @DisplayName("Exercise 1: Widen a VStream to Kind")
    void exercise1_widenVStreamToKind() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      // TODO: Replace answerRequired() with VSTREAM.widen(stream)
      Kind<VStreamKind.Witness, Integer> kind = answerRequired();

      assertThat(kind).isSameAs(stream);
    }

    /**
     * Exercise 2: Narrow a Kind back to VStream
     *
     * <p>Use VSTREAM.narrow() to convert from the Kind representation back to a concrete VStream.
     *
     * <p>Task: Narrow the Kind back to a VStream and collect elements
     */
    @Test
    @DisplayName("Exercise 2: Narrow a Kind back to VStream")
    void exercise2_narrowKindToVStream() {
      Kind<VStreamKind.Witness, String> kind = VSTREAM.widen(VStream.of("hello", "world"));

      // TODO: Replace answerRequired() with VSTREAM.narrow(kind)
      VStream<String> stream = answerRequired();

      assertThat(stream.toList().run()).containsExactly("hello", "world");
    }
  }

  // ===========================================================================
  // Part 2: Functor and Applicative
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Functor and Applicative")
  class FunctorAndApplicative {

    /**
     * Exercise 3: Use VStreamFunctor.map via the HKT interface
     *
     * <p>VStreamFunctor.INSTANCE provides a map operation that works through the Kind interface.
     * The operation remains lazy.
     *
     * <p>Task: Map a function over the stream using the functor instance
     */
    @Test
    @DisplayName("Exercise 3: Functor map via HKT")
    void exercise3_functorMapViaHKT() {
      VStreamFunctor functor = VStreamFunctor.INSTANCE;
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      // TODO: Replace answerRequired() with functor.map(i -> i * 2, stream)
      Kind<VStreamKind.Witness, Integer> doubled = answerRequired();

      assertThat(VSTREAM.narrow(doubled).toList().run()).containsExactly(2, 4, 6);
    }

    /**
     * Exercise 4: Use VStreamApplicative.of to create a singleton stream
     *
     * <p>Applicative.of lifts a pure value into the stream context.
     *
     * <p>Task: Create a single-element Kind using the applicative
     */
    @Test
    @DisplayName("Exercise 4: Applicative of creates singleton stream")
    void exercise4_applicativeOf() {
      VStreamApplicative applicative = VStreamApplicative.INSTANCE;

      // TODO: Replace answerRequired() with applicative.of(42)
      Kind<VStreamKind.Witness, Integer> result = answerRequired();

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(42);
    }

    /**
     * Exercise 5: Use VStreamApplicative.ap for Cartesian product
     *
     * <p>The ap operation applies each function in the function stream to each value in the value
     * stream, producing a Cartesian product.
     *
     * <p>Task: Apply the function stream to the value stream
     *
     * <p>Hint: applicative.ap(fns, values)
     */
    @Test
    @DisplayName("Exercise 5: Applicative ap (Cartesian product)")
    void exercise5_applicativeAp() {
      VStreamApplicative applicative = VStreamApplicative.INSTANCE;

      Kind<VStreamKind.Witness, Function<Integer, String>> fns =
          VSTREAM.widen(VStream.of(i -> "x" + i, i -> "y" + i));
      Kind<VStreamKind.Witness, Integer> values = VSTREAM.widen(VStream.of(1, 2));

      // TODO: Replace answerRequired() with applicative.ap(fns, values)
      Kind<VStreamKind.Witness, String> result = answerRequired();

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly("x1", "x2", "y1", "y2");
    }
  }

  // ===========================================================================
  // Part 3: Monad
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Monad")
  class MonadTests {

    /**
     * Exercise 6: Use VStreamMonad.flatMap for HKT-level composition
     *
     * <p>Monadic flatMap substitutes each element with a sub-stream and flattens the result.
     *
     * <p>Task: Use monad.flatMap to expand each number into a pair [n, n*10]
     *
     * <p>Hint: monad.flatMap(i -> VSTREAM.widen(VStream.of(i, i * 10)), stream)
     */
    @Test
    @DisplayName("Exercise 6: Monad flatMap via HKT")
    void exercise6_monadFlatMap() {
      VStreamMonad monad = VStreamMonad.INSTANCE;
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      // TODO: Replace answerRequired() with monad.flatMap(...)
      Kind<VStreamKind.Witness, Integer> result = answerRequired();

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  // ===========================================================================
  // Part 4: Foldable and Alternative
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Foldable and Alternative")
  class FoldableAndAlternative {

    /**
     * Exercise 7: Use VStreamTraverse.foldMap with a sum monoid
     *
     * <p>Foldable.foldMap maps each element to a Monoid and combines the results.
     *
     * <p>Task: Fold the stream to compute the sum of all elements
     *
     * <p>Hint: foldable.foldMap(sumMonoid, Function.identity(), stream)
     */
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

      // TODO: Replace answerRequired() with foldable.foldMap(sumMonoid, Function.identity(),
      // stream)
      Integer sum = answerRequired();

      assertThat(sum).isEqualTo(60);
    }

    /**
     * Exercise 8: Use VStreamAlternative.orElse for stream concatenation
     *
     * <p>Alternative.orElse concatenates two VStreams (concatenation semantics).
     *
     * <p>Task: Concatenate the two streams using orElse
     *
     * <p>Hint: alt.orElse(first, () -> second)
     */
    @Test
    @DisplayName("Exercise 8: Alternative orElse for concatenation")
    void exercise8_alternativeOrElse() {
      VStreamAlternative alt = VStreamAlternative.INSTANCE;

      Kind<VStreamKind.Witness, Integer> first = VSTREAM.widen(VStream.of(1, 2));
      Kind<VStreamKind.Witness, Integer> second = VSTREAM.widen(VStream.of(3, 4));

      // TODO: Replace answerRequired() with alt.orElse(first, () -> second)
      Kind<VStreamKind.Witness, Integer> combined = answerRequired();

      assertThat(VSTREAM.narrow(combined).toList().run()).containsExactly(1, 2, 3, 4);
    }

    /**
     * Exercise 9: Use Alternative.guard for conditional filtering
     *
     * <p>guard(true) produces a single Unit element; guard(false) produces an empty stream.
     *
     * <p>Task: Use guard to conditionally produce a value
     */
    @Test
    @DisplayName("Exercise 9: Alternative guard")
    void exercise9_alternativeGuard() {
      VStreamAlternative alt = VStreamAlternative.INSTANCE;

      // TODO: Replace answerRequired() with alt.guard(true)
      Kind<VStreamKind.Witness, Unit> trueResult = answerRequired();

      // TODO: Replace answerRequired() with alt.guard(false)
      Kind<VStreamKind.Witness, Unit> falseResult = answerRequired();

      assertThat(VSTREAM.narrow(trueResult).toList().run()).containsExactly(Unit.INSTANCE);
      assertThat(VSTREAM.narrow(falseResult).toList().run()).isEmpty();
    }

    /**
     * Exercise 10: Combine Monad and Foldable in a pipeline
     *
     * <p>Use flatMap to expand elements, then foldMap to aggregate the result.
     *
     * <p>Task: First expand each number into [n, n+1], then sum all the results
     *
     * <p>Hint: First use monad.flatMap, then use foldable.foldMap on the result
     */
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

      // TODO: First expand with flatMap: each i -> [i, i+1]
      // Then fold the result with foldMap using sumMonoid
      // Expected: [1, 2, 2, 3, 3, 4] -> sum = 15
      Integer sum = answerRequired();

      assertThat(sum).isEqualTo(15);
    }
  }

  /**
   * Congratulations! You've completed the VStream HKT Tutorial.
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to widen and narrow VStreams using VStreamKindHelper
   *   <li>Using VStreamFunctor.map via the HKT interface
   *   <li>Using VStreamApplicative.of and ap (Cartesian product)
   *   <li>Using VStreamMonad.flatMap for stream composition
   *   <li>Using VStreamTraverse.foldMap for monoid-based aggregation
   *   <li>Using VStreamAlternative for concatenation and guard
   *   <li>Combining type class operations in pipelines
   * </ul>
   *
   * <p>Next: VStreamPath and Effect Path API (Stage 3)
   */
}
