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
 * Tutorial: VStream Basics - SOLUTIONS
 *
 * <p>This file contains the complete solutions for all exercises in TutorialVStream.java.
 */
@DisplayName("Tutorial: VStream Basics (Solutions)")
public class TutorialVStream_Solution {

  // ===========================================================================
  // Part 1: Creating VStreams
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VStreams")
  class CreatingVStreams {

    @Test
    @DisplayName("Exercise 1: Create from list and collect")
    void exercise1_fromListAndCollect() {
      List<String> source = List.of("alpha", "beta", "gamma");

      // SOLUTION: Use VStream.fromList() to create a lazy stream, then toList().run() to collect
      List<String> result = VStream.fromList(source).toList().run();

      assertThat(result).containsExactly("alpha", "beta", "gamma");
    }

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

    @Test
    @DisplayName("Exercise 3: Map elements")
    void exercise3_mapElements() {
      // SOLUTION: Use map() to transform each element lazily
      List<String> result = VStream.of(1, 2, 3).map(n -> "#" + n).toList().run();

      assertThat(result).containsExactly("#1", "#2", "#3");
    }

    @Test
    @DisplayName("Exercise 4: Filter elements")
    void exercise4_filterElements() {
      // SOLUTION: Use filter() to keep only elements matching the predicate
      List<Integer> result = VStream.of(1, 2, 3, 4, 5, 6).filter(n -> n % 2 == 0).toList().run();

      assertThat(result).containsExactly(2, 4, 6);
    }

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

    @Test
    @DisplayName("Exercise 6: FlatMap expansion")
    void exercise6_flatMapExpansion() {
      // SOLUTION: Use flatMap() to expand each element into a sub-stream
      List<Integer> result = VStream.of(1, 2, 3).flatMap(n -> VStream.of(n, n * 10)).toList().run();

      assertThat(result).containsExactly(1, 10, 2, 20, 3, 30);
    }

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

    @Test
    @DisplayName("Exercise 8: Fold to sum")
    void exercise8_foldToSum() {
      // SOLUTION: Use fold() with seed 0 and Integer::sum to sum all elements
      Integer result = VStream.of(1, 2, 3, 4, 5).fold(0, Integer::sum).run();

      assertThat(result).isEqualTo(15);
    }

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
