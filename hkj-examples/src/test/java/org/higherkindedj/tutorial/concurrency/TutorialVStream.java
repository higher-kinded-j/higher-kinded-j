// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VStream Basics - Lazy Pull-Based Streaming on Virtual Threads
 *
 * <p>Learn to work with VStream, a lazy, pull-based streaming abstraction that executes element
 * production on virtual threads via VTask. VStream fills the gap between VTask (single-value
 * effect) and Java's Stream (single-use, no virtual thread integration).
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>VStream is lazy: no elements are produced until a terminal operation runs
 *   <li>Each pull returns a VTask, leveraging virtual threads for scalable concurrency
 *   <li>Use map(), filter(), flatMap() to build pipelines (all lazy)
 *   <li>Use toList(), fold(), exists() to trigger evaluation (terminal operations)
 *   <li>Use take()/takeWhile() to limit infinite streams
 * </ul>
 *
 * <p>Prerequisites: Familiarity with VTask basics
 *
 * <p>Estimated time: 12-15 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: VStream Basics")
public class TutorialVStream {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Creating VStreams
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VStreams")
  class CreatingVStreams {

    /**
     * Exercise 1: Create a VStream from a list and collect to list
     *
     * <p>VStream.fromList() creates a lazy stream from a list. toList() is a terminal operation
     * that collects all elements. Since toList() returns a VTask, you must call .run() to execute.
     *
     * <p>Task: Create a VStream from the given list and collect its elements.
     */
    @Test
    @DisplayName("Exercise 1: Create from list and collect")
    void exercise1_fromListAndCollect() {
      List<String> source = List.of("alpha", "beta", "gamma");

      // TODO: Replace answerRequired() with VStream.fromList(source).toList().run()
      List<String> result = answerRequired();

      assertThat(result).containsExactly("alpha", "beta", "gamma");
    }

    /**
     * Exercise 2: Create streams from various sources
     *
     * <p>VStream provides several factory methods: of(), range(), empty().
     *
     * <p>Task: Create a VStream using VStream.range() for integers [1, 6).
     */
    @Test
    @DisplayName("Exercise 2: Create a range stream")
    void exercise2_rangeStream() {
      // TODO: Replace answerRequired() with VStream.range(1, 6).toList().run()
      List<Integer> result = answerRequired();

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
     * Exercise 3: Map over stream elements
     *
     * <p>map() transforms each element lazily. Nothing happens until a terminal operation runs.
     *
     * <p>Task: Map each number to its string representation prefixed with "#".
     */
    @Test
    @DisplayName("Exercise 3: Map elements")
    void exercise3_mapElements() {
      // TODO: Replace answerRequired() with
      //   VStream.of(1, 2, 3).map(n -> "#" + n).toList().run()
      List<String> result = answerRequired();

      assertThat(result).containsExactly("#1", "#2", "#3");
    }

    /**
     * Exercise 4: Filter elements from a stream
     *
     * <p>filter() keeps only elements matching the predicate. Non-matching elements are skipped
     * efficiently without allocation (using the Skip step type internally).
     *
     * <p>Task: Filter to keep only even numbers.
     */
    @Test
    @DisplayName("Exercise 4: Filter elements")
    void exercise4_filterElements() {
      // TODO: Replace answerRequired() with
      //   VStream.of(1, 2, 3, 4, 5, 6).filter(n -> n % 2 == 0).toList().run()
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(2, 4, 6);
    }

    /**
     * Exercise 5: Use take to limit an infinite stream
     *
     * <p>VStream.iterate() creates an infinite stream. take(n) limits it to the first n elements.
     * This is safe because VStream is lazy: only the taken elements are ever produced.
     *
     * <p>Task: Create an infinite stream of powers of 2 starting from 1, take the first 6.
     */
    @Test
    @DisplayName("Exercise 5: Limit infinite stream with take")
    void exercise5_takeFromInfinite() {
      // TODO: Replace answerRequired() with
      //   VStream.iterate(1, n -> n * 2).take(6).toList().run()
      List<Integer> result = answerRequired();

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
     * Exercise 6: FlatMap to expand elements into sub-streams
     *
     * <p>flatMap() (or its alias via()) substitutes each element with a sub-stream and flattens the
     * results. This is the monadic bind operation for VStream.
     *
     * <p>Task: Expand each number n into a pair [n, n*10] using flatMap.
     */
    @Test
    @DisplayName("Exercise 6: FlatMap expansion")
    void exercise6_flatMapExpansion() {
      // TODO: Replace answerRequired() with
      //   VStream.of(1, 2, 3).flatMap(n -> VStream.of(n, n * 10)).toList().run()
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(1, 10, 2, 20, 3, 30);
    }

    /**
     * Exercise 7: Compose filter, map, and take in a pipeline
     *
     * <p>VStream operations compose lazily. You can chain multiple transformations before
     * triggering evaluation with a terminal operation.
     *
     * <p>Task: From range [1, 100), filter evens, multiply by 3, take first 5.
     */
    @Test
    @DisplayName("Exercise 7: Multi-step pipeline")
    void exercise7_pipeline() {
      // TODO: Replace answerRequired() with
      //   VStream.range(1, 100)
      //       .filter(n -> n % 2 == 0)
      //       .map(n -> n * 3)
      //       .take(5)
      //       .toList()
      //       .run()
      List<Integer> result = answerRequired();

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
     * Exercise 8: Fold a stream to a single value
     *
     * <p>fold() is a terminal operation that reduces the stream to a single value using a seed and
     * an operator. Like toList(), it returns a VTask that must be run().
     *
     * <p>Task: Sum the numbers 1 through 5 using fold.
     */
    @Test
    @DisplayName("Exercise 8: Fold to sum")
    void exercise8_foldToSum() {
      // TODO: Replace answerRequired() with
      //   VStream.of(1, 2, 3, 4, 5).fold(0, Integer::sum).run()
      Integer result = answerRequired();

      assertThat(result).isEqualTo(15);
    }

    /**
     * Exercise 9: Use unfold to create an effectful stream
     *
     * <p>VStream.unfold() creates a stream by repeatedly applying an effectful function to a state.
     * It produces elements until Optional.empty() is returned. The Seed record carries both the
     * emitted value and the next state.
     *
     * <p>Task: Unfold to produce countdown values [5, 4, 3, 2, 1] from initial state 5.
     */
    @Test
    @DisplayName("Exercise 9: Effectful unfold")
    void exercise9_unfold() {
      // TODO: Replace answerRequired() with
      //   VStream.unfold(5, state ->
      //       VTask.succeed(state > 0
      //           ? Optional.of(new VStream.Seed<>(state, state - 1))
      //           : Optional.empty()))
      //       .toList().run()
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(5, 4, 3, 2, 1);
    }

    /**
     * Exercise 10: Check existence with short-circuiting
     *
     * <p>exists() is a terminal operation that returns true as soon as it finds a matching element.
     * It short-circuits: it stops pulling elements once a match is found.
     *
     * <p>Task: Check whether the infinite stream of natural numbers contains 42.
     */
    @Test
    @DisplayName("Exercise 10: Short-circuiting exists")
    void exercise10_existsShortCircuit() {
      // TODO: Replace answerRequired() with
      //   VStream.iterate(0, n -> n + 1).exists(n -> n == 42).run()
      Boolean result = answerRequired();

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
     * Exercise 11: Handle errors in a stream with recover
     *
     * <p>recover() replaces a failed pull with a recovery value. The stream continues from the
     * recovery point.
     *
     * <p>Task: Recover a failing stream with a default value.
     */
    @Test
    @DisplayName("Exercise 11: Recover from failure")
    void exercise11_recover() {
      // TODO: Replace answerRequired() with
      //   VStream.<String>fail(new RuntimeException("oops"))
      //       .recover(e -> "recovered: " + e.getMessage())
      //       .toList().run()
      List<String> result = answerRequired();

      assertThat(result).containsExactly("recovered: oops");
    }
  }

  // ===========================================================================
  // Congratulations! 🎉
  // You've completed the VStream Basics tutorial.
  // Next: explore VStream's HKT integration and Path API in later tutorials.
  // ===========================================================================
}
