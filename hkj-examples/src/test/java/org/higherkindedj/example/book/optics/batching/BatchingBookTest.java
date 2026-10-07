// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.batching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.higherkindedj.optics.fetch.Fetch;
import org.higherkindedj.optics.fetch.SafeFetch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Optic-Driven Batching page makes about its rounds and its partition. */
@DisplayName("the Optic-Driven Batching page: one traversal, one round, one batched call")
class BatchingBookTest {

  @Test
  @DisplayName("five foci go to the backend in one round and one call")
  void fiveFociOneRoundOneCall() {
    BatchingBook.Backend backend = new BatchingBook.Backend();

    Fetch.RunResult<Integer, List<Integer>> result = BatchingBook.pipeline(backend);

    assertThat(result.rounds()).isEqualTo(1);
    assertThat(result.backendCalls()).isEqualTo(1);
    assertThat(backend.calls()).isEqualTo(1);
    assertThat(result.fetchedBatches()).containsExactly(Set.of(1, 2, 3, 4, 5));
    assertThat(result.value()).containsExactly(10, 20, 30, 40, 50);
  }

  @Test
  @DisplayName("the routed loader sends each key to its own backend, still in one round")
  void routedLoaderKeepsOneRound() throws Exception {
    Fetch.RunResult<String, List<String>> result = BatchingBook.routed();

    assertThat(result.value()).containsExactly("user 1", "product 7", "user 2");
    assertThat(result.rounds()).isEqualTo(1);
  }

  @Test
  @DisplayName("partition splits the per-key answers into users and reasons")
  void partitionSplitsSuccessesFromFailures() {
    SafeFetch.Partitioned<String, User> split = BatchingBook.partition();

    assertThat(split.successes())
        .extracting(User::name)
        .containsExactly("Ada Lovelace", "Grace Hopper");
    assertThat(split.failures()).containsExactly("no user nobody");
  }
}
