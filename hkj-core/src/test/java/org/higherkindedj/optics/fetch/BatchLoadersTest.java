// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.FocusPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Capability A, A2 specification: {@link BatchLoaders#chunked} bounds the size of a single
 * dispatch, splitting an oversized key set into concurrent sub-batches.
 */
@DisplayName("BatchLoaders.chunked: batch-size limiting")
class BatchLoadersTest {

  private static Kind<FetchKind.Witness<String, String>, List<String>> traverse(List<String> keys) {
    Traversal<List<String>, String> ids = FocusPaths.listElements();
    return ids.modifyF(id -> FETCH.widen(Fetch.fetch(id)), keys, FetchApplicative.instance());
  }

  @Test
  @DisplayName("a key set larger than the limit is split into bounded chunks")
  void oversizedKeySetIsChunked() throws Exception {
    List<Set<String>> dispatchedChunks = new ArrayList<>();
    BatchLoader<String, String> underlying =
        keys -> {
          dispatchedChunks.add(Set.copyOf(keys));
          Map<String, String> out = new HashMap<>();
          keys.forEach(k -> out.put(k, "V:" + k));
          return CompletableFuture.completedFuture(out);
        };
    BatchLoader<String, String> chunked = BatchLoaders.chunked(underlying, 3);

    var program = traverse(List.of("1", "2", "3", "4", "5", "6", "7"));
    Fetch.RunResult<String, List<String>> result =
        Fetch.runAsync(FETCH.narrow(program), chunked, new ConcurrentHashMap<>()).get();

    assertThat(result.value()).containsExactly("V:1", "V:2", "V:3", "V:4", "V:5", "V:6", "V:7");
    assertThat(dispatchedChunks).hasSize(3); // 7 keys, limit 3 -> 3 + 3 + 1
    assertThat(dispatchedChunks).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(3));
    assertThat(dispatchedChunks.stream().flatMap(Set::stream))
        .containsExactlyInAnyOrder("1", "2", "3", "4", "5", "6", "7");
  }

  @Test
  @DisplayName("a key set within the limit is a single dispatch")
  void smallKeySetIsNotChunked() throws Exception {
    List<Set<String>> dispatchedChunks = new ArrayList<>();
    BatchLoader<String, String> underlying =
        keys -> {
          dispatchedChunks.add(Set.copyOf(keys));
          Map<String, String> out = new HashMap<>();
          keys.forEach(k -> out.put(k, "V:" + k));
          return CompletableFuture.completedFuture(out);
        };
    BatchLoader<String, String> chunked = BatchLoaders.chunked(underlying, 10);

    var program = traverse(List.of("1", "2", "3"));
    Fetch.runAsync(FETCH.narrow(program), chunked, new ConcurrentHashMap<>()).get();

    assertThat(dispatchedChunks).hasSize(1);
  }

  @Test
  @DisplayName("a key count that is an exact multiple of the limit chunks evenly")
  void exactMultipleChunksEvenly() throws Exception {
    List<Set<String>> dispatchedChunks = new ArrayList<>();
    BatchLoader<String, String> underlying =
        keys -> {
          dispatchedChunks.add(Set.copyOf(keys));
          Map<String, String> out = new HashMap<>();
          keys.forEach(k -> out.put(k, "V:" + k));
          return CompletableFuture.completedFuture(out);
        };
    BatchLoader<String, String> chunked = BatchLoaders.chunked(underlying, 3);

    var program = traverse(List.of("1", "2", "3", "4", "5", "6"));
    Fetch.RunResult<String, List<String>> result =
        Fetch.runAsync(FETCH.narrow(program), chunked, new ConcurrentHashMap<>()).get();

    assertThat(result.value()).containsExactly("V:1", "V:2", "V:3", "V:4", "V:5", "V:6");
    assertThat(dispatchedChunks).hasSize(2); // 6 keys, limit 3 -> 3 + 3, no trailing chunk
    assertThat(dispatchedChunks).allSatisfy(chunk -> assertThat(chunk).hasSize(3));
  }

  @Test
  @DisplayName("a maxBatchSize below 1 is rejected")
  void rejectsNonPositiveBatchSize() {
    BatchLoader<String, String> underlying = keys -> CompletableFuture.completedFuture(Map.of());
    assertThatThrownBy(() -> BatchLoaders.chunked(underlying, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxBatchSize");
  }
}
