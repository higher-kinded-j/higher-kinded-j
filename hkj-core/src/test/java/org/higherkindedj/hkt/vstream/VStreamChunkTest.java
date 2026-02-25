// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.vstream.VStreamAssert.assertThatVStream;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VStream Chunking Operations Test Suite")
class VStreamChunkTest {

  @Nested
  @DisplayName("chunk() Tests")
  class ChunkTests {

    @Test
    @DisplayName("chunk() groups elements correctly with exact divisor")
    void chunkExactDivisor() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5, 6);

      List<List<Integer>> chunks = stream.chunk(3).toList().run();

      assertThat(chunks).hasSize(2);
      assertThat(chunks.get(0)).containsExactly(1, 2, 3);
      assertThat(chunks.get(1)).containsExactly(4, 5, 6);
    }

    @Test
    @DisplayName("chunk() last chunk smaller when not exact divisor")
    void chunkLastChunkSmaller() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5);

      List<List<Integer>> chunks = stream.chunk(3).toList().run();

      assertThat(chunks).hasSize(2);
      assertThat(chunks.get(0)).containsExactly(1, 2, 3);
      assertThat(chunks.get(1)).containsExactly(4, 5);
    }

    @Test
    @DisplayName("chunk(1) equivalent to wrapping each in singleton list")
    void chunkOfOneEquivalentToMapListOf() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      List<List<Integer>> chunks = stream.chunk(1).toList().run();

      assertThat(chunks).hasSize(3);
      assertThat(chunks.get(0)).containsExactly(1);
      assertThat(chunks.get(1)).containsExactly(2);
      assertThat(chunks.get(2)).containsExactly(3);
    }

    @Test
    @DisplayName("chunk(n) where n >= stream length returns single chunk")
    void chunkLargerThanStreamReturnsSingleChunk() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      List<List<Integer>> chunks = stream.chunk(10).toList().run();

      assertThat(chunks).hasSize(1);
      assertThat(chunks.getFirst()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("chunk() on empty stream returns empty stream of chunks")
    void chunkEmptyStreamReturnsEmpty() {
      VStream<Integer> stream = VStream.empty();

      List<List<Integer>> chunks = stream.chunk(3).toList().run();

      assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("chunk() preserves element order within and across chunks")
    void chunkPreservesOrder() {
      VStream<String> stream = VStream.of("a", "b", "c", "d", "e");

      List<List<String>> chunks = stream.chunk(2).toList().run();

      assertThat(chunks).hasSize(3);
      assertThat(chunks.get(0)).containsExactly("a", "b");
      assertThat(chunks.get(1)).containsExactly("c", "d");
      assertThat(chunks.get(2)).containsExactly("e");
    }

    @Test
    @DisplayName("chunk() laziness: chunks produced on demand")
    void chunkLaziness() {
      AtomicInteger pullCount = new AtomicInteger(0);

      VStream<Integer> stream =
          VStream.iterate(1, n -> n + 1).peek(n -> pullCount.incrementAndGet());

      // Take 2 chunks of size 3, so we should pull exactly 6 elements
      VStream<List<Integer>> chunked = stream.chunk(3).take(2);

      // Nothing pulled yet (lazy)
      assertThat(pullCount.get()).isZero();

      List<List<Integer>> result = chunked.toList().run();

      assertThat(result).hasSize(2);
      assertThat(result.get(0)).containsExactly(1, 2, 3);
      assertThat(result.get(1)).containsExactly(4, 5, 6);
    }

    @Test
    @DisplayName("chunk() with zero size throws IllegalArgumentException")
    void chunkWithZeroSizeThrows() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      assertThatThrownBy(() -> stream.chunk(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("chunk() with negative size throws IllegalArgumentException")
    void chunkWithNegativeSizeThrows() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      assertThatThrownBy(() -> stream.chunk(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("chunk() handles Skip steps from filtered stream")
    void chunkHandlesSkipSteps() {
      // filter() produces Skip steps for non-matching elements
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5, 6).filter(n -> n % 2 != 0);

      List<List<Integer>> chunks = stream.chunk(2).toList().run();

      assertThat(chunks).hasSize(2);
      assertThat(chunks.get(0)).containsExactly(1, 3);
      assertThat(chunks.get(1)).containsExactly(5);
    }
  }

  @Nested
  @DisplayName("chunkWhile() Tests")
  class ChunkWhileTests {

    @Test
    @DisplayName("chunkWhile() groups consecutive equal elements")
    void chunkWhileGroupsConsecutiveEquals() {
      VStream<Integer> stream = VStream.of(1, 1, 2, 2, 2, 3);

      List<List<Integer>> chunks = stream.chunkWhile(Integer::equals).toList().run();

      assertThat(chunks).hasSize(3);
      assertThat(chunks.get(0)).containsExactly(1, 1);
      assertThat(chunks.get(1)).containsExactly(2, 2, 2);
      assertThat(chunks.get(2)).containsExactly(3);
    }

    @Test
    @DisplayName("chunkWhile() single-element chunks when no adjacent matches")
    void chunkWhileSingleElementChunks() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4);

      List<List<Integer>> chunks = stream.chunkWhile(Integer::equals).toList().run();

      assertThat(chunks).hasSize(4);
      for (int i = 0; i < 4; i++) {
        assertThat(chunks.get(i)).containsExactly(i + 1);
      }
    }

    @Test
    @DisplayName("chunkWhile() all-same elements produce single chunk")
    void chunkWhileAllSameProducesSingleChunk() {
      VStream<String> stream = VStream.of("x", "x", "x", "x");

      List<List<String>> chunks = stream.chunkWhile(String::equals).toList().run();

      assertThat(chunks).hasSize(1);
      assertThat(chunks.getFirst()).containsExactly("x", "x", "x", "x");
    }

    @Test
    @DisplayName("chunkWhile() empty stream returns empty")
    void chunkWhileEmptyStreamReturnsEmpty() {
      VStream<Integer> stream = VStream.empty();

      List<List<Integer>> chunks = stream.chunkWhile(Integer::equals).toList().run();

      assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("chunkWhile() groups by custom predicate")
    void chunkWhileCustomPredicate() {
      // Group numbers that differ by at most 1
      VStream<Integer> stream = VStream.of(1, 2, 3, 10, 11, 20);

      List<List<Integer>> chunks = stream.chunkWhile((a, b) -> Math.abs(a - b) <= 1).toList().run();

      assertThat(chunks).hasSize(3);
      assertThat(chunks.get(0)).containsExactly(1, 2, 3);
      assertThat(chunks.get(1)).containsExactly(10, 11);
      assertThat(chunks.get(2)).containsExactly(20);
    }

    @Test
    @DisplayName("chunkWhile() single element stream produces single chunk")
    void chunkWhileSingleElement() {
      VStream<Integer> stream = VStream.of(42);

      List<List<Integer>> chunks = stream.chunkWhile(Integer::equals).toList().run();

      assertThat(chunks).hasSize(1);
      assertThat(chunks.getFirst()).containsExactly(42);
    }

    @Test
    @DisplayName("chunkWhile() handles Skip steps from filtered stream")
    void chunkWhileHandlesSkipSteps() {
      // filter() produces Skip steps for non-matching elements.
      // Starting with 2 (even) ensures the first pull produces a Skip step,
      // covering the outer chunkWhile loop's Skip branch (L693).
      VStream<Integer> stream = VStream.of(2, 1, 4, 3, 6, 5, 8, 7).filter(n -> n % 2 != 0);

      // Group consecutive odd numbers that differ by exactly 2
      List<List<Integer>> chunks = stream.chunkWhile((a, b) -> b - a == 2).toList().run();

      // Odd numbers after filter: 1, 3, 5, 7
      // 1->3 differ by 2: same chunk
      // 3->5 differ by 2: same chunk
      // 5->7 differ by 2: same chunk
      assertThat(chunks).hasSize(1);
      assertThat(chunks.getFirst()).containsExactly(1, 3, 5, 7);
    }

    @Test
    @DisplayName("chunkWhile() handles Skip steps with multiple chunks")
    void chunkWhileHandlesSkipStepsMultipleChunks() {
      // filter() produces Skip steps; multiple chunks test buildChunkWhile Skip branch
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).filter(n -> n % 2 != 0);

      // Group by equality (each element is unique, so each gets its own chunk)
      List<List<Integer>> chunks = stream.chunkWhile(Integer::equals).toList().run();

      // Odd numbers: 1, 3, 5, 7, 9 — each in its own chunk
      assertThat(chunks).hasSize(5);
      assertThat(chunks.get(0)).containsExactly(1);
      assertThat(chunks.get(1)).containsExactly(3);
      assertThat(chunks.get(2)).containsExactly(5);
      assertThat(chunks.get(3)).containsExactly(7);
      assertThat(chunks.get(4)).containsExactly(9);
    }

    @Test
    @DisplayName("chunkWhile() with null predicate throws NullPointerException")
    void chunkWhileWithNullPredicateThrows() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      assertThatThrownBy(() -> stream.chunkWhile(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("mapChunked() Tests")
  class MapChunkedTests {

    @Test
    @DisplayName("mapChunked() applies batch function to each chunk")
    void mapChunkedAppliesBatchFunction() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5, 6);

      VStream<Integer> result =
          stream.mapChunked(3, chunk -> chunk.stream().map(n -> n * 10).toList());

      assertThatVStream(result).producesElements(10, 20, 30, 40, 50, 60);
    }

    @Test
    @DisplayName("mapChunked() results flattened correctly")
    void mapChunkedFlattenedCorrectly() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4);

      VStream<String> result =
          stream.mapChunked(2, chunk -> chunk.stream().map(n -> "item-" + n).toList());

      assertThatVStream(result).producesElements("item-1", "item-2", "item-3", "item-4");
    }

    @Test
    @DisplayName("mapChunked() batch function can change element count (expand)")
    void mapChunkedCanExpand() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      // Expand: each element becomes two elements
      VStream<Integer> result =
          stream.mapChunked(
              2,
              chunk -> {
                List<Integer> expanded = new java.util.ArrayList<>();
                for (int n : chunk) {
                  expanded.add(n);
                  expanded.add(n * 100);
                }
                return expanded;
              });

      assertThatVStream(result).producesElements(1, 100, 2, 200, 3, 300);
    }

    @Test
    @DisplayName("mapChunked() batch function can change element count (contract)")
    void mapChunkedCanContract() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5, 6);

      // Contract: return only the sum of each chunk
      VStream<Integer> result =
          stream.mapChunked(3, chunk -> List.of(chunk.stream().mapToInt(Integer::intValue).sum()));

      assertThatVStream(result).producesElements(6, 15);
    }

    @Test
    @DisplayName("mapChunked() empty stream returns empty")
    void mapChunkedEmptyStreamReturnsEmpty() {
      VStream<Integer> stream = VStream.empty();

      VStream<Integer> result = stream.mapChunked(3, chunk -> chunk);

      assertThatVStream(result).isEmpty();
    }

    @Test
    @DisplayName("mapChunked() with null function throws NullPointerException")
    void mapChunkedWithNullFunctionThrows() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      assertThatThrownBy(() -> stream.mapChunked(2, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("mapChunked() with zero size throws IllegalArgumentException")
    void mapChunkedWithZeroSizeThrows() {
      VStream<Integer> stream = VStream.of(1, 2, 3);

      assertThatThrownBy(() -> stream.mapChunked(0, chunk -> chunk))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
