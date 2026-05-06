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
 * Solution for TutorialVStreamParallel — teaching-solution format.
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
@DisplayName("Solution: VStream Parallel Operations")
public class TutorialVStreamParallel_Solution {

  // ===========================================================================
  // Part 1: Parallel Processing with parEvalMap
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Parallel Processing")
  class ParallelProcessing {

    /**
     * Why this is idiomatic: {@code VStreamPar.parEvalMap(stream, parallelism, fn)} runs the
     * per-element function on a bounded pool of virtual threads while preserving order. Each task
     * may take time; the bound stops resource exhaustion.
     *
     * <p>Alternative: {@code stream.map(fn).toList()} runs sequentially. Same answer for fast
     * functions; the parallel variant earns its keep on slow ones.
     *
     * <p>Common wrong attempt: pick a parallelism of 1 and expect parallelism. The bound is the
     * maximum concurrent tasks; one means sequential.
     */
    @Test
    @DisplayName("Exercise 1: Basic parallel map")
    void exercise1_basicParallelMap() {
      VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

      List<Integer> result =
          VStreamPar.parEvalMap(numbers, 2, n -> VTask.succeed(n * 2)).toList().run();

      assertThat(result).containsExactly(2, 4, 6, 8, 10);
    }

    /**
     * Why this is idiomatic: {@code parEvalMapUnordered} skips the ordering wait — as each task
     * finishes its result is emitted. Faster when the consumer doesn't care about order.
     *
     * <p>Alternative: {@code parEvalMap} for ordered output. Choose by consumer requirements;
     * unordered is faster, ordered is more predictable.
     *
     * <p>Common wrong attempt: assume the result preserves input order. The "Unordered" suffix is
     * the contract — assert with {@code containsExactlyInAnyOrder}.
     */
    @Test
    @DisplayName("Exercise 2: Unordered parallel map")
    void exercise2_unorderedParallelMap() {
      VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

      List<Integer> result =
          VStreamPar.parEvalMapUnordered(numbers, 3, n -> VTask.succeed(n * 3)).toList().run();

      assertThat(result).containsExactlyInAnyOrder(3, 6, 9, 12, 15);
    }

    /**
     * Why this is idiomatic: each element gets a {@code VTask} that may perform I/O. The pool runs
     * them concurrently; the result stream collects the values.
     *
     * <p>Alternative: a sequential map. Same result, slower wall-clock when each task blocks.
     *
     * <p>Common wrong attempt: pick a parallelism larger than the input size. The extra slots are
     * idle; choose a sensible cap based on backend capacity.
     */
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

    /**
     * Why this is idiomatic: {@code chunk(n)} groups elements into fixed-size lists of {@code n}
     * (final chunk may be smaller). Useful for batching I/O calls.
     *
     * <p>Alternative: a manual sliding loop. Same answer; the combinator stays in the lazy
     * pipeline.
     *
     * <p>Common wrong attempt: assume every chunk is exactly the requested size. The last chunk
     * fills to whatever remains; accept the ragged edge.
     */
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

    /**
     * Why this is idiomatic: {@code mapChunked(n, fn)} processes each chunk of n elements with a
     * function that returns a list. The batch can be sent as a single backend call.
     *
     * <p>Alternative: chunk first, then map. Equivalent; the fused {@code mapChunked} keeps the
     * pipeline shorter.
     *
     * <p>Common wrong attempt: return a single value from the per-chunk function. It expects a
     * list; wrap with {@code List.of(...)} for one-output reductions.
     */
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

    /**
     * Why this is idiomatic: {@code chunkWhile(predicate)} groups consecutive elements that match a
     * 2-arg predicate. Adjacent equal elements coalesce; runs become chunks.
     *
     * <p>Alternative: a manual loop tracking the last element. Same answer; the combinator stays
     * composable.
     *
     * <p>Common wrong attempt: assume non-adjacent equal elements are grouped. The predicate
     * compares pairs in sequence; reorder first if the input is unsorted.
     */
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

    /**
     * Why this is idiomatic: {@code VStreamPar.merge(a, b)} interleaves two streams concurrently.
     * Order between streams is non-deterministic; both contribute as fast as they can.
     *
     * <p>Alternative: {@code Stream.concat} for sequential append. {@code merge} is the parallel
     * cousin.
     *
     * <p>Common wrong attempt: assert exact order. The merge interleaves whichever stream produces
     * faster; assert with {@code containsExactlyInAnyOrder}.
     */
    @Test
    @DisplayName("Exercise 7: Merge two streams")
    void exercise7_mergeTwoStreams() {
      VStream<String> letters = VStream.of("a", "b", "c");
      VStream<String> numbers = VStream.of("1", "2", "3");

      List<String> result = VStreamPar.merge(letters, numbers).toList().run();

      assertThat(result).containsExactlyInAnyOrder("a", "b", "c", "1", "2", "3");
    }

    /**
     * Why this is idiomatic: {@code parCollect(stream, parallelism)} drains the stream into a list
     * using a bounded pool. Returns a {@code VTask<List>} so the collection itself is composable.
     *
     * <p>Alternative: {@code stream.toList().run()} for sequential collection. The parallel form
     * earns its keep when each element is expensive to produce.
     *
     * <p>Common wrong attempt: assume the bounded pool changes the result type. The result is still
     * a {@code List}; only the production path is concurrent.
     */
    @Test
    @DisplayName("Exercise 8: Parallel collect")
    void exercise8_parallelCollect() {
      VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

      List<Integer> result = VStreamPar.parCollect(numbers, 3).run();

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }
  }
}
