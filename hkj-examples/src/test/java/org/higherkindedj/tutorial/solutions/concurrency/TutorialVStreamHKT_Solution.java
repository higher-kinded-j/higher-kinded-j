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
 * Solution for TutorialVStreamHKT — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial Solution: VStream HKT")
public class TutorialVStreamHKT_Solution {

  // ===========================================================================
  // Part 1: Widen and Narrow
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Widen and Narrow")
  class WidenAndNarrow {

    /**
     * Why this is idiomatic: {@code VSTREAM.widen} is the canonical lift from a concrete {@code
     * VStream} to {@code Kind<VStreamKind.Witness, A>}. The witness lets generic type-class code
     * work with the stream uniformly.
     *
     * <p>Alternative: keep the {@code VStream} concrete. Same answer; widening is for generic
     * type-class APIs.
     *
     * <p>Common wrong attempt: cast directly to {@code Kind}. The widen helper is type-safe; raw
     * casts may silently misalign the witness.
     */
    @Test
    @DisplayName("Exercise 1: Widen a VStream to Kind")
    void exercise1_widenVStreamToKind() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      // SOLUTION: Use VSTREAM.widen() to convert to Kind
      Kind<VStreamKind.Witness, Integer> kind = VSTREAM.widen(stream);

      assertThat(kind).isSameAs(stream);
    }

    /**
     * Why this is idiomatic: {@code VSTREAM.narrow} brings a {@code Kind} back to the concrete
     * {@code VStream}. Use it at the boundary where the concrete type's methods (e.g. {@code
     * toList}) are needed.
     *
     * <p>Alternative: stay generic and rely on {@code Kind} operations. Reach for {@code narrow}
     * only when stream-specific methods are required.
     *
     * <p>Common wrong attempt: cast {@code Kind} to {@code VStream}. The narrow helper validates
     * the witness; a cast may produce a {@code ClassCastException}.
     */
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

    /**
     * Why this is idiomatic: {@code VStreamFunctor.INSTANCE.map(fn, kind)} is the type-class-driven
     * variant of {@code stream.map}. Generic code can call this without knowing about VStream
     * specifically.
     *
     * <p>Alternative: {@code stream.map(fn)} on the concrete type. Same answer; the type-class form
     * enables polymorphic code.
     *
     * <p>Common wrong attempt: skip widening and call functor methods directly. Type-class APIs
     * require {@code Kind}; widen first.
     */
    @Test
    @DisplayName("Exercise 3: Functor map via HKT")
    void exercise3_functorMapViaHKT() {
      VStreamFunctor functor = VStreamFunctor.INSTANCE;
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      // SOLUTION: Use functor.map to double each element
      Kind<VStreamKind.Witness, Integer> doubled = functor.map(i -> i * 2, stream);

      assertThat(VSTREAM.narrow(doubled).toList().run()).containsExactly(2, 4, 6);
    }

    /**
     * Why this is idiomatic: {@code Applicative.of(value)} lifts a value into the VStream as a
     * single-element stream. The "pure" element of the applicative.
     *
     * <p>Alternative: {@code VStream.of(42)}. Same answer; the type-class form is for generic code.
     *
     * <p>Common wrong attempt: assume {@code of} produces an empty stream. {@code of} is "pure" — a
     * singleton; use {@code VStream.empty()} for empty streams.
     */
    @Test
    @DisplayName("Exercise 4: Applicative of creates singleton stream")
    void exercise4_applicativeOf() {
      VStreamApplicative applicative = VStreamApplicative.INSTANCE;

      // SOLUTION: Use applicative.of to lift a value into VStream
      Kind<VStreamKind.Witness, Integer> result = applicative.of(42);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(42);
    }

    /**
     * Why this is idiomatic: {@code applicative.ap(fns, values)} produces the Cartesian product —
     * apply each function to each value. The result has {@code |fns| * |values|} elements.
     *
     * <p>Alternative: nested {@code flatMap} loops. Equivalent runtime; the type class makes the
     * intent explicit.
     *
     * <p>Common wrong attempt: assume {@code ap} pairs functions with values elementwise. The
     * applicative for VStream is Cartesian product, not zip.
     */
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

    /**
     * Why this is idiomatic: {@code VStreamMonad.INSTANCE.flatMap} is the type- class form of
     * {@code stream.flatMap}. Each element expands to a sub-stream; the results concatenate.
     *
     * <p>Alternative: {@code stream.flatMap(fn)} on the concrete type. The monad variant enables
     * generic code over any monad.
     *
     * <p>Common wrong attempt: forget to widen the inner stream before returning. The lambda must
     * yield a {@code Kind}; widen with {@code VSTREAM.widen}.
     */
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

    /**
     * Why this is idiomatic: {@code foldMap(monoid, fn, stream)} reduces the stream via a monoid.
     * {@code Function.identity()} maps each element to itself; the monoid combines.
     *
     * <p>Alternative: {@code stream.fold(0, Integer::sum)}. Same answer; the foldable is the
     * type-class abstraction over reduce.
     *
     * <p>Common wrong attempt: pick a non-associative combiner. Foldable assumes associativity;
     * non-associative reduces give non-deterministic results in parallel.
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

      // SOLUTION: Use foldable.foldMap with sum monoid
      Integer sum = foldable.foldMap(sumMonoid, Function.identity(), stream);

      assertThat(sum).isEqualTo(60);
    }

    /**
     * Why this is idiomatic: {@code Alternative.orElse(a, () -> b)} concatenates two streams; the
     * second is supplied lazily so it is only evaluated when needed.
     *
     * <p>Alternative: {@code Stream.concat(a, b)}. Equivalent for VStream; {@code orElse} is the
     * type-class abstraction.
     *
     * <p>Common wrong attempt: pass {@code b} eagerly. The thunk-style argument preserves laziness
     * when the first stream is enough.
     */
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

    /**
     * Why this is idiomatic: {@code guard(true)} yields a singleton; {@code guard(false)} yields an
     * empty stream. Useful for conditional branches inside monadic comprehensions.
     *
     * <p>Alternative: {@code if/else} returning {@code VStream.empty()} or {@code
     * VStream.of(Unit.INSTANCE)}. Same answer; {@code guard} is the named form.
     *
     * <p>Common wrong attempt: assume {@code guard(true)} produces a stream of booleans. It
     * produces {@code Unit} — the value is the presence, not the boolean.
     */
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

    /**
     * Why this is idiomatic: combine a monadic flatMap (expand) with a foldable foldMap (reduce).
     * The result is a single value summarising the expanded stream — classic map-reduce.
     *
     * <p>Alternative: chain stream operations directly. Same answer; the type- class form
     * generalises across monads.
     *
     * <p>Common wrong attempt: assume the expansion happens in parallel. The type-class API is
     * sequential by default; use {@code VStreamPar} for parallel expansion.
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

      // SOLUTION: First expand with flatMap, then fold with foldMap
      Kind<VStreamKind.Witness, Integer> expanded =
          monad.flatMap(i -> VSTREAM.widen(VStream.of(i, i + 1)), stream);
      Integer sum = foldable.foldMap(sumMonoid, Function.identity(), expanded);

      assertThat(sum).isEqualTo(15);
    }
  }
}
