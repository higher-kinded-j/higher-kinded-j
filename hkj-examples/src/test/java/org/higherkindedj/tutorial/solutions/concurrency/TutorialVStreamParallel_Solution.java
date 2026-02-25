// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for VStream Parallel Operations Tutorial.
 *
 * <p>This file contains the working solutions for TutorialVStreamParallel. Compare your answers
 * against these solutions.
 */
@DisplayName("Solution: VStream Parallel Operations")
public class TutorialVStreamParallel_Solution {

  // ===========================================================================
  // Part 1: Parallel Processing with parEvalMap
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Parallel Processing")
  class ParallelProcessing {

    @Test
    @DisplayName("Exercise 1: Basic parallel map")
    void exercise1_basicParallelMap() {
      VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

      List<Integer> result =
          VStreamPar.parEvalMap(numbers, 2, n -> VTask.succeed(n * 2)).toList().run();

      assertThat(result).containsExactly(2, 4, 6, 8, 10);
    }

    @Test
    @DisplayName("Exercise 2: Unordered parallel map")
    void exercise2_unorderedParallelMap() {
      VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

      List<Integer> result =
          VStreamPar.parEvalMapUnordered(numbers, 3, n -> VTask.succeed(n * 3)).toList().run();

      assertThat(result).containsExactlyInAnyOrder(3, 6, 9, 12, 15);
    }

    @Test
    @DisplayName("Exercise 3: Parallel with effectful tasks")
    void exercise3_parallelWithEffects() {
      VStream<Integer> numbers = VStream.of(1, 2, 3);

      List<String> result =
          VStreamPar.parEvalMap(numbers, 4, n -> VTask.of(() -> "item-" + n)).toList().run();

      assertThat(result).containsExactly("item-1", "item-2", "item-3");
    }
  }

  // ===========================================================================
  // Part 2: Chunking Operations
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Chunking Operations")
  class ChunkingOperations {

    @Test
    @DisplayName("Exercise 4: Basic chunking")
    void exercise4_basicChunking() {
      VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5, 6, 7);

      List<List<Integer>> result = numbers.chunk(3).toList().run();

      assertThat(result).hasSize(3);
      assertThat(result.get(0)).containsExactly(1, 2, 3);
      assertThat(result.get(1)).containsExactly(4, 5, 6);
      assertThat(result.get(2)).containsExactly(7);
    }

    @Test
    @DisplayName("Exercise 5: Batch transformation")
    void exercise5_batchTransformation() {
      VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5, 6);

      List<Integer> result =
          numbers
              .mapChunked(3, chunk -> List.of(chunk.stream().mapToInt(Integer::intValue).sum()))
              .toList()
              .run();

      assertThat(result).containsExactly(6, 15);
    }

    @Test
    @DisplayName("Exercise 6: Group consecutive elements")
    void exercise6_chunkWhile() {
      VStream<Integer> numbers = VStream.of(1, 1, 2, 2, 2, 3);

      List<List<Integer>> result = numbers.chunkWhile(Integer::equals).toList().run();

      assertThat(result).hasSize(3);
      assertThat(result.get(0)).containsExactly(1, 1);
      assertThat(result.get(1)).containsExactly(2, 2, 2);
      assertThat(result.get(2)).containsExactly(3);
    }
  }

  // ===========================================================================
  // Part 3: Merging Streams
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Merging Streams")
  class MergingStreams {

    @Test
    @DisplayName("Exercise 7: Merge two streams")
    void exercise7_mergeTwoStreams() {
      VStream<String> letters = VStream.of("a", "b", "c");
      VStream<String> numbers = VStream.of("1", "2", "3");

      List<String> result = VStreamPar.merge(letters, numbers).toList().run();

      assertThat(result).containsExactlyInAnyOrder("a", "b", "c", "1", "2", "3");
    }

    @Test
    @DisplayName("Exercise 8: Parallel collect")
    void exercise8_parallelCollect() {
      VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

      List<Integer> result = VStreamPar.parCollect(numbers, 3).run();

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }
  }
}
