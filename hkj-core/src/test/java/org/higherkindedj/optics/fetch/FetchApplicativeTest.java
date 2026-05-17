// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.FocusPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Validates that {@link FetchApplicative} added at the {@code Optic.modifyF} seam batches per-leaf
 * data requests across an optic traversal — eliminating the N+1 access pattern — without any change
 * to the optic core.
 */
@DisplayName("FetchApplicative batches optic-traversal requests at the modifyF seam")
class FetchApplicativeTest {

  /** A backend that can only be queried in batches; counts how many round-trips it serves. */
  static final class BatchingBackend {
    final AtomicInteger calls = new AtomicInteger();
    final java.util.ArrayList<Set<String>> seenBatches = new java.util.ArrayList<>();

    Map<String, String> resolve(Set<String> ids) {
      calls.incrementAndGet();
      seenBatches.add(Set.copyOf(ids));
      Map<String, String> out = new HashMap<>();
      for (String id : ids) {
        out.put(id, "User#" + id);
      }
      return out;
    }
  }

  private static Function<String, Kind<FetchKind.Witness<String, String>, String>> fetchName() {
    return id -> FETCH.widen(Fetch.fetch(id));
  }

  @Nested
  @DisplayName("flat list traversal")
  class FlatList {

    @Test
    @DisplayName("N elements collapse to ONE batched backend call")
    void nPlusOneEliminated() {
      var backend = new BatchingBackend();
      Traversal<List<String>, String> ids = FocusPaths.listElements();
      List<String> input = List.of("1", "2", "3", "4", "5");

      Kind<FetchKind.Witness<String, String>, List<String>> program =
          ids.modifyF(fetchName(), input, FetchApplicative.instance());

      Fetch.RunResult<String, List<String>> result =
          Fetch.run(FETCH.narrow(program), backend::resolve);

      assertThat(result.value())
          .containsExactly("User#1", "User#2", "User#3", "User#4", "User#5");
      assertThat(backend.calls.get()).isEqualTo(1);
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(result.batches()).hasSize(1);
      assertThat(result.batches().get(0)).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    @DisplayName("duplicate keys are deduplicated within the single batch")
    void deduplicatesWithinBatch() {
      var backend = new BatchingBackend();
      Traversal<List<String>, String> ids = FocusPaths.listElements();
      List<String> input = List.of("7", "7", "9", "7", "9");

      var program = ids.modifyF(fetchName(), input, FetchApplicative.instance());
      Fetch.RunResult<String, List<String>> result =
          Fetch.run(FETCH.narrow(program), backend::resolve);

      // Positional output is still correct for every (including repeated) element...
      assertThat(result.value())
          .containsExactly("User#7", "User#7", "User#9", "User#7", "User#9");
      // ...but the backend only ever saw the distinct keys, once.
      assertThat(backend.calls.get()).isEqualTo(1);
      assertThat(backend.seenBatches.get(0)).containsExactlyInAnyOrder("7", "9");
    }
  }

  @Nested
  @DisplayName("nested structure (composed traversal)")
  class Nested2D {

    @Test
    @DisplayName("a deep List<List<>> traversal still batches into ONE call")
    void deepTraversalSingleBatch() {
      var backend = new BatchingBackend();
      Traversal<List<List<String>>, String> deep =
          FocusPaths.<List<String>>listElements().andThen(FocusPaths.<String>listElements());
      List<List<String>> input = List.of(List.of("a", "b"), List.of("c"), List.of("d", "e", "f"));

      var program = deep.modifyF(fetchName(), input, FetchApplicative.instance());
      Fetch.RunResult<String, List<List<String>>> result =
          Fetch.run(FETCH.narrow(program), backend::resolve);

      assertThat(result.value())
          .containsExactly(
              List.of("User#a", "User#b"),
              List.of("User#c"),
              List.of("User#d", "User#e", "User#f"));
      // The entire nested selection set resolved in a single backend round-trip.
      assertThat(backend.calls.get()).isEqualTo(1);
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(result.batches().get(0))
          .containsExactly("a", "b", "c", "d", "e", "f");
    }
  }

  @Nested
  @DisplayName("the Applicative/Monad batching boundary")
  class MonadicBoundary {

    @Test
    @DisplayName("applicative composition of two fetches = ONE round")
    void applicativeStaysOneRound() {
      var backend = new BatchingBackend();
      Fetch<String, String, String> a = Fetch.fetch("x");
      Fetch<String, String, String> b = Fetch.fetch("y");

      Fetch<String, String, String> combined =
          Fetch.ap(a.map(av -> (Function<String, String>) bv -> av + "+" + bv), b);

      var result = Fetch.run(combined, backend::resolve);

      assertThat(result.value()).isEqualTo("User#x+User#y");
      assertThat(result.rounds()).isEqualTo(1);
    }

    @Test
    @DisplayName("monadic dependency between fetches forces a second round (documented limit)")
    void flatMapBreaksBatching() {
      var backend = new BatchingBackend();

      // Stage 2's key depends on stage 1's resolved value -> cannot be known up front.
      Fetch<String, String, String> dependent =
          Fetch.<String, String>fetch("root")
              .flatMap(rootName -> Fetch.fetch("child-of:" + rootName));

      var result = Fetch.run(dependent, backend::resolve);

      assertThat(result.value()).isEqualTo("User#child-of:User#root");
      assertThat(result.rounds()).isEqualTo(2);
      assertThat(backend.calls.get()).isEqualTo(2);
      assertThat(result.batches().get(0)).containsExactly("root");
      assertThat(result.batches().get(1)).containsExactly("child-of:User#root");
    }
  }
}
