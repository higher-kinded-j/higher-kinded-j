// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VStream Parallel Operations and Chunking
 *
 * <p>Learn to use VStreamPar for bounded-concurrency parallel processing and VStream chunking
 * operations for efficient batch processing. These operations leverage Java 25 virtual threads for
 * scalable concurrent element processing.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>parEvalMap: bounded-concurrency parallel processing that preserves order
 *   <li>parEvalMapUnordered: parallel processing in completion order for maximum throughput
 *   <li>chunk: grouping elements into fixed-size batches
 *   <li>mapChunked: batch transformation via chunk-map-flatten
 *   <li>merge: concurrent consumption of multiple streams
 * </ul>
 *
 * <p>Prerequisites: TutorialVStream, TutorialVTask
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: VStream Parallel Operations")
public class TutorialVStreamParallel {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Parallel Processing with parEvalMap
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Parallel Processing")
  class ParallelProcessing {

    /**
     * Exercise 1: Basic parallel map
     *
     * <p>VStreamPar.parEvalMap processes elements concurrently with bounded concurrency. The first
     * parameter is the stream, the second is the concurrency limit, and the third is a function
     * returning a VTask. Results are emitted in the same order as the input.
     *
     * <p>Task: Use VStreamPar.parEvalMap with concurrency=2 to double each number.
     *
     * <p>Hint: VStreamPar.parEvalMap(stream, 2, n -> VTask.succeed(n * 2))
     */
    @Test
    @DisplayName("Exercise 1: Basic parallel map")
    void exercise1_basicParallelMap() {
      // Given
      // VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

      // TODO: Use VStreamPar.parEvalMap to double each number with concurrency=2
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(2, 4, 6, 8, 10);
    }

    /**
     * Exercise 2: Unordered parallel map for throughput
     *
     * <p>VStreamPar.parEvalMapUnordered is like parEvalMap but emits results in completion order
     * rather than input order. This maximises throughput when order does not matter.
     *
     * <p>Task: Use VStreamPar.parEvalMapUnordered with concurrency=3 to triple each number.
     *
     * <p>Hint: VStreamPar.parEvalMapUnordered(stream, 3, n -> VTask.succeed(n * 3))
     */
    @Test
    @DisplayName("Exercise 2: Unordered parallel map")
    void exercise2_unorderedParallelMap() {
      // Given
      // VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

      // TODO: Use VStreamPar.parEvalMapUnordered to triple each number with concurrency=3
      List<Integer> result = answerRequired();

      // Note: containsExactlyInAnyOrder because order is not guaranteed
      assertThat(result).containsExactlyInAnyOrder(3, 6, 9, 12, 15);
    }

    /**
     * Exercise 3: Parallel processing with effectful tasks
     *
     * <p>The real power of parEvalMap is processing I/O-bound tasks concurrently. Each element is
     * mapped to a VTask that performs work on a virtual thread.
     *
     * <p>Task: Use parEvalMap with concurrency=4 to compute the string "item-N" for each number.
     *
     * <p>Hint: VStreamPar.parEvalMap(stream, 4, n -> VTask.of(() -> "item-" + n))
     */
    @Test
    @DisplayName("Exercise 3: Parallel with effectful tasks")
    void exercise3_parallelWithEffects() {
      // Given
      // VStream<Integer> numbers = VStream.of(1, 2, 3);

      // TODO: Process each number into "item-N" using parEvalMap
      List<String> result = answerRequired();

      assertThat(result).containsExactly("item-1", "item-2", "item-3");
    }
  }

  // ===========================================================================
  // Part 2: Chunking Operations
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Chunking Operations")
  class ChunkingOperations {

    /**
     * Exercise 4: Basic chunking
     *
     * <p>chunk(n) groups elements into lists of at most n elements. The last chunk may have fewer
     * elements.
     *
     * <p>Task: Chunk a stream of 7 elements into groups of 3.
     *
     * <p>Hint: stream.chunk(3).toList().run()
     */
    @Test
    @DisplayName("Exercise 4: Basic chunking")
    void exercise4_basicChunking() {
      // Given
      // VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5, 6, 7);

      // TODO: Chunk into groups of 3
      List<List<Integer>> result = answerRequired();

      assertThat(result).hasSize(3);
      assertThat(result.get(0)).containsExactly(1, 2, 3);
      assertThat(result.get(1)).containsExactly(4, 5, 6);
      assertThat(result.get(2)).containsExactly(7);
    }

    /**
     * Exercise 5: Batch transformation with mapChunked
     *
     * <p>mapChunked(n, f) chunks the stream into groups of n, applies a batch function to each
     * chunk, and flattens the results. This is ideal for bulk operations.
     *
     * <p>Task: Use mapChunked to sum each group of 3 elements.
     *
     * <p>Hint: stream.mapChunked(3, chunk ->
     * List.of(chunk.stream().mapToInt(Integer::intValue).sum()))
     */
    @Test
    @DisplayName("Exercise 5: Batch transformation")
    void exercise5_batchTransformation() {
      // Given
      // VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5, 6);

      // TODO: Use mapChunked to sum each group of 3
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(6, 15);
    }

    /**
     * Exercise 6: chunkWhile for grouping consecutive elements
     *
     * <p>chunkWhile(predicate) groups consecutive elements while the predicate holds between
     * adjacent pairs. A new chunk starts when the predicate fails.
     *
     * <p>Task: Group consecutive equal numbers.
     *
     * <p>Hint: stream.chunkWhile(Integer::equals).toList().run()
     */
    @Test
    @DisplayName("Exercise 6: Group consecutive elements")
    void exercise6_chunkWhile() {
      // Given
      // VStream<Integer> numbers = VStream.of(1, 1, 2, 2, 2, 3);

      // TODO: Group consecutive equal elements
      List<List<Integer>> result = answerRequired();

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

    /**
     * Exercise 7: Merge two streams
     *
     * <p>VStreamPar.merge() combines multiple streams concurrently. Elements are emitted as they
     * become available from any source.
     *
     * <p>Task: Merge two streams and verify all elements are present.
     *
     * <p>Hint: VStreamPar.merge(streamA, streamB).toList().run()
     */
    @Test
    @DisplayName("Exercise 7: Merge two streams")
    void exercise7_mergeTwoStreams() {
      // Given
      // VStream<String> letters = VStream.of("a", "b", "c");
      // VStream<String> numbers = VStream.of("1", "2", "3");

      // TODO: Merge the two streams
      List<String> result = answerRequired();

      assertThat(result).containsExactlyInAnyOrder("a", "b", "c", "1", "2", "3");
    }

    /**
     * Exercise 8: Parallel collect
     *
     * <p>VStreamPar.parCollect() is a terminal operation that collects all elements in parallel
     * batches. It returns a VTask containing the collected list.
     *
     * <p>Task: Use parCollect with batchSize=3 to collect all elements.
     *
     * <p>Hint: VStreamPar.parCollect(stream, 3).run()
     */
    @Test
    @DisplayName("Exercise 8: Parallel collect")
    void exercise8_parallelCollect() {
      // Given
      // VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

      // TODO: Use parCollect to collect all elements
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }
  }

  /**
   * Congratulations! You've completed the VStream Parallel Operations tutorial.
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to use parEvalMap for bounded-concurrency parallel processing
   *   <li>The difference between parEvalMap (ordered) and parEvalMapUnordered (completion order)
   *   <li>How to chunk streams for batch operations
   *   <li>How to use mapChunked for batch transformations
   *   <li>How to use chunkWhile for grouping consecutive elements
   *   <li>How to merge multiple streams concurrently
   *   <li>How to use parCollect for parallel terminal collection
   * </ul>
   *
   * <p>See the solution file TutorialVStreamParallel_Solution.java for answers.
   */
}
