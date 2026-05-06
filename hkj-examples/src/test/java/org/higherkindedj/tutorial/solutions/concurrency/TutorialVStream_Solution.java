// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for TutorialVStream — teaching-solution format.
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
@DisplayName("Tutorial: VStream Basics (Solutions)")
public class TutorialVStream_Solution {

  // ===========================================================================
  // Part 1: Creating VStreams
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VStreams")
  class CreatingVStreams {

    /**
     * Why this is idiomatic: {@code VStream.fromList(source).toList().run()} treats the stream as a
     * lazy description and forces it once at the end. The list is the collection sink.
     *
     * <p>Alternative: a plain {@code Stream}. Same answer for in-memory data; reach for {@code
     * VStream} when downstream stages may be effectful or async.
     *
     * <p>Common wrong attempt: forget {@code .run()} on the {@code toList()} result. The VStream
     * stays a lazy description until {@code run} is invoked.
     */
    @Test
    @DisplayName("Exercise 1: Create from list and collect")
    void exercise1_fromListAndCollect() {
      List<String> source = List.of("alpha", "beta", "gamma");

      // SOLUTION: Use VStream.fromList() to create a lazy stream, then toList().run() to collect
      List<String> result = VStream.fromList(source).toList().run();

      assertThat(result).containsExactly("alpha", "beta", "gamma");
    }

    /**
     * Why this is idiomatic: {@code VStream.range(1, 6)} creates a half-open integer range as a
     * lazy stream. Standard semantics — start inclusive, end exclusive.
     *
     * <p>Alternative: {@code IntStream.range(1, 6).boxed()...} for plain streams. The VStream
     * version composes with effectful stages that {@code IntStream} cannot.
     *
     * <p>Common wrong attempt: assume the end is inclusive. The convention matches {@code
     * IntStream}; the upper bound is excluded.
     */
    @Test
    @DisplayName("Exercise 2: Create a range stream")
    void exercise2_rangeStream() {
      // SOLUTION: Use VStream.range(start, end) for integers [start, end)
      List<Integer> result = VStream.range(1, 6).toList().run();

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }
  }

  // ===========================================================================
  // Part 2: Transformations
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Transformations")
  class Transformations {

    /**
     * Why this is idiomatic: {@code map} transforms each element lazily. The lambda runs once per
     * element when the stream is finally consumed.
     *
     * <p>Alternative: {@code Stream.map}. Same shape; the VStream variant stays in the effect-aware
     * family for downstream composition.
     *
     * <p>Common wrong attempt: consume the stream twice expecting cached results. VStream does not
     * memoise — repeat consumers re-run the pipeline.
     */
    @Test
    @DisplayName("Exercise 3: Map elements")
    void exercise3_mapElements() {
      // SOLUTION: Use map() to transform each element lazily
      List<String> result = VStream.of(1, 2, 3).map(n -> "#" + n).toList().run();

      assertThat(result).containsExactly("#1", "#2", "#3");
    }

    /**
     * Why this is idiomatic: {@code filter} keeps elements matching the predicate. The retained
     * elements pass through unchanged; non-matches are dropped.
     *
     * <p>Alternative: a manual loop with an {@code if}. Same answer; the combinator fits into a
     * fluent pipeline cleanly.
     *
     * <p>Common wrong attempt: assume {@code filter} can mutate elements. It only decides
     * inclusion; transform with {@code map} when the value should change.
     */
    @Test
    @DisplayName("Exercise 4: Filter elements")
    void exercise4_filterElements() {
      // SOLUTION: Use filter() to keep only elements matching the predicate
      List<Integer> result = VStream.of(1, 2, 3, 4, 5, 6).filter(n -> n % 2 == 0).toList().run();

      assertThat(result).containsExactly(2, 4, 6);
    }

    /**
     * Why this is idiomatic: {@code iterate(seed, fn)} produces an infinite stream; {@code take(n)}
     * bounds it. The pair is the canonical "n powers of two" idiom.
     *
     * <p>Alternative: a {@code for} loop and a manual list. Works for one-off code; the combinator
     * pair composes with other stream stages.
     *
     * <p>Common wrong attempt: invoke {@code toList} on the unbounded iterate. The stream never
     * terminates; always pair {@code iterate} with a limiting combinator.
     */
    @Test
    @DisplayName("Exercise 5: Limit infinite stream with take")
    void exercise5_takeFromInfinite() {
      // SOLUTION: Use iterate() for an infinite stream and take() to limit it
      List<Integer> result = VStream.iterate(1, n -> n * 2).take(6).toList().run();

      assertThat(result).containsExactly(1, 2, 4, 8, 16, 32);
    }
  }

  // ===========================================================================
  // Part 3: Composition
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Composition")
  class Composition {

    /**
     * Why this is idiomatic: {@code flatMap} expands each element into a sub-stream and
     * concatenates the results. Use it when one element maps to many.
     *
     * <p>Alternative: a nested loop. Equivalent; the combinator stays in the lazy pipeline.
     *
     * <p>Common wrong attempt: expect {@code flatMap} to flatten arbitrarily nested structures. It
     * flattens one level — call again for deeper nesting.
     */
    @Test
    @DisplayName("Exercise 6: FlatMap expansion")
    void exercise6_flatMapExpansion() {
      // SOLUTION: Use flatMap() to expand each element into a sub-stream
      List<Integer> result = VStream.of(1, 2, 3).flatMap(n -> VStream.of(n, n * 10)).toList().run();

      assertThat(result).containsExactly(1, 10, 2, 20, 3, 30);
    }

    /**
     * Why this is idiomatic: chain filter, map, take in one lazy pipeline. Each stage runs only on
     * elements that survive the previous stage; the take limits total work even though range is
     * large.
     *
     * <p>Alternative: a hand-written loop with state variables. Same answer; the pipeline is
     * composable and the laziness limits work to what is needed.
     *
     * <p>Common wrong attempt: collect to a list between stages. Each materialisation costs memory;
     * keep the pipeline lazy until the end.
     */
    @Test
    @DisplayName("Exercise 7: Multi-step pipeline")
    void exercise7_pipeline() {
      // SOLUTION: Chain filter, map, and take in a lazy pipeline
      List<Integer> result =
          VStream.range(1, 100).filter(n -> n % 2 == 0).map(n -> n * 3).take(5).toList().run();

      assertThat(result).containsExactly(6, 12, 18, 24, 30);
    }
  }

  // ===========================================================================
  // Part 4: Terminal Operations and Effects
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Terminal Operations and Effects")
  class TerminalOperations {

    /**
     * Why this is idiomatic: {@code fold(seed, combiner)} reduces the stream into a single value.
     * {@code Integer::sum} is the canonical addition combiner.
     *
     * <p>Alternative: collect to a list and sum. Works; the fold avoids the intermediate list.
     *
     * <p>Common wrong attempt: forget the seed. {@code fold} needs an identity for empty streams;
     * supply zero for sum, one for product.
     */
    @Test
    @DisplayName("Exercise 8: Fold to sum")
    void exercise8_foldToSum() {
      // SOLUTION: Use fold() with seed 0 and Integer::sum to sum all elements
      Integer result = VStream.of(1, 2, 3, 4, 5).fold(0, Integer::sum).run();

      assertThat(result).isEqualTo(15);
    }

    /**
     * Why this is idiomatic: {@code unfold(seed, stateFn)} drives a stream from a stateful
     * generator. Each step returns either a {@code Seed(value, nextState)} or {@code empty} to
     * terminate.
     *
     * <p>Alternative: {@code iterate} with a sentinel value and {@code takeWhile}. Same answer for
     * monotonic generators; {@code unfold} handles non-monotonic termination directly.
     *
     * <p>Common wrong attempt: forget to terminate. Without a path to {@code empty} the stream runs
     * forever; bound it with {@code take} or terminate via state.
     */
    @Test
    @DisplayName("Exercise 9: Effectful unfold")
    void exercise9_unfold() {
      // SOLUTION: Use unfold() with a state function that produces Seed records
      List<Integer> result =
          VStream.unfold(
                  5,
                  state ->
                      VTask.succeed(
                          state > 0
                              ? Optional.of(new VStream.Seed<>(state, state - 1))
                              : Optional.empty()))
              .toList()
              .run();

      assertThat(result).containsExactly(5, 4, 3, 2, 1);
    }

    /**
     * Why this is idiomatic: {@code exists(predicate)} short-circuits at the first match. On an
     * infinite iterate stream, the search stops as soon as 42 appears.
     *
     * <p>Alternative: {@code filter(p).take(1).toList()} returns a list with one element. The named
     * {@code exists} returns a boolean directly.
     *
     * <p>Common wrong attempt: use {@code filter} on infinite streams expecting a boolean. The
     * result is a stream; reach for {@code exists}, {@code anyMatch}, or {@code findFirst} to
     * terminate.
     */
    @Test
    @DisplayName("Exercise 10: Short-circuiting exists")
    void exercise10_existsShortCircuit() {
      // SOLUTION: Use exists() which short-circuits on the first match
      Boolean result = VStream.iterate(0, n -> n + 1).exists(n -> n == 42).run();

      assertThat(result).isTrue();
    }
  }

  // ===========================================================================
  // Part 5: Error Handling
  // ===========================================================================

  @Nested
  @DisplayName("Part 5: Error Handling")
  class ErrorHandling {

    /**
     * Why this is idiomatic: {@code VStream.fail(throwable).recover(fn)} produces a single-element
     * stream containing the recovery value. The error message is available to the recovery
     * function.
     *
     * <p>Alternative: {@code recoverWith(e -> VStream.of(...))} when the recovery is itself a
     * stream of multiple elements. {@code recover} is the single-value form.
     *
     * <p>Common wrong attempt: catch the exception around {@code toList().run()}. The streaming
     * layer already provides recovery; staying inside the stream keeps later combinators
     * applicable.
     */
    @Test
    @DisplayName("Exercise 11: Recover from failure")
    void exercise11_recover() {
      // SOLUTION: Use fail() to create a failing stream, then recover() to replace the error
      List<String> result =
          VStream.<String>fail(new RuntimeException("oops"))
              .recover(e -> "recovered: " + e.getMessage())
              .toList()
              .run();

      assertThat(result).containsExactly("recovered: oops");
    }
  }
}
