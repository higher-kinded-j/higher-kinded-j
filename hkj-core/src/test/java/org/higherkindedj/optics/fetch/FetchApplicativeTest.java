// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.FocusPaths;
import org.higherkindedj.optics.indexed.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Validates that {@link FetchApplicative} added at the {@code Optic.modifyF} seam batches per-leaf
 * data requests across an optic traversal (eliminating the N+1 access pattern) without any change
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
          Fetch.runCached(FETCH.narrow(program), backend::resolve);

      assertThat(result.value()).containsExactly("User#1", "User#2", "User#3", "User#4", "User#5");
      assertThat(backend.calls.get()).isEqualTo(1);
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(result.fetchedBatches()).hasSize(1);
      assertThat(result.fetchedBatches().get(0)).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    @DisplayName("duplicate keys are deduplicated within the single batch")
    void deduplicatesWithinBatch() {
      var backend = new BatchingBackend();
      Traversal<List<String>, String> ids = FocusPaths.listElements();
      List<String> input = List.of("7", "7", "9", "7", "9");

      var program = ids.modifyF(fetchName(), input, FetchApplicative.instance());
      Fetch.RunResult<String, List<String>> result =
          Fetch.runCached(FETCH.narrow(program), backend::resolve);

      // Positional output is still correct for every (including repeated) element...
      assertThat(result.value()).containsExactly("User#7", "User#7", "User#9", "User#7", "User#9");
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
          Fetch.runCached(FETCH.narrow(program), backend::resolve);

      assertThat(result.value())
          .containsExactly(
              List.of("User#a", "User#b"),
              List.of("User#c"),
              List.of("User#d", "User#e", "User#f"));
      // The entire nested selection set resolved in a single backend round-trip.
      assertThat(backend.calls.get()).isEqualTo(1);
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(result.fetchedBatches().get(0)).containsExactly("a", "b", "c", "d", "e", "f");
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

      var result = Fetch.runCached(combined, backend::resolve);

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

      var result = Fetch.runCached(dependent, backend::resolve);

      assertThat(result.value()).isEqualTo("User#child-of:User#root");
      assertThat(result.rounds()).isEqualTo(2);
      assertThat(backend.calls.get()).isEqualTo(2);
      assertThat(result.fetchedBatches().get(0)).containsExactly("root");
      assertThat(result.fetchedBatches().get(1)).containsExactly("child-of:User#root");
    }
  }

  @Nested
  @DisplayName("coupled-field optic (Lens.paired) over the batching seam")
  class CoupledFields {

    /** Two coupled bounds with a cross-field invariant enforced in the canonical constructor. */
    record Range(int lo, int hi) {
      Range {
        if (lo > hi) {
          throw new IllegalArgumentException(
              "invariant violated: lo > hi (" + lo + " > " + hi + ")");
        }
      }
    }

    private static final Lens<Range, Integer> LO =
        Lens.of(Range::lo, (r, lo) -> new Range(lo, r.hi()));
    private static final Lens<Range, Integer> HI =
        Lens.of(Range::hi, (r, hi) -> new Range(r.lo(), hi));

    // The coupled-field optic: lo and hi are ONE focus, rebuilt atomically.
    private static final Lens<Range, Pair<Integer, Integer>> BOUNDS =
        Lens.paired(LO, HI, Range::new);

    /** Resolves "lo"/"hi" from a backend that can only be queried in batches. */
    static final class BoundsBackend {
      final AtomicInteger calls = new AtomicInteger();
      Set<String> lastBatch = Set.of();
      private final int loVal;
      private final int hiVal;

      BoundsBackend(int loVal, int hiVal) {
        this.loVal = loVal;
        this.hiVal = hiVal;
      }

      Map<String, Integer> resolve(Set<String> keys) {
        calls.incrementAndGet();
        lastBatch = Set.copyOf(keys);
        Map<String, Integer> out = new HashMap<>();
        for (String k : keys) {
          out.put(k, k.equals("lo") ? loVal : hiVal);
        }
        return out;
      }
    }

    /** Builds a Fetch that loads BOTH bounds as siblings (one applicative combination). */
    private static Kind<FetchKind.Witness<String, Integer>, Pair<Integer, Integer>> loadBounds(
        Pair<Integer, Integer> ignoredCurrent) {
      Fetch<String, Integer, Pair<Integer, Integer>> both =
          Fetch.ap(
              Fetch.<String, Integer>fetch("lo")
                  .map(lo -> (Function<Integer, Pair<Integer, Integer>>) hi -> Pair.of(lo, hi)),
              Fetch.<String, Integer>fetch("hi"));
      return FETCH.widen(both);
    }

    @Test
    @DisplayName("both coupled fields resolve in ONE batch round and rebuild atomically")
    void coupledFieldsBatchAtomically() {
      var backend = new BoundsBackend(11, 12);
      Range start = new Range(1, 2);

      // Sequential lens updates here would pass through Range(11, 2) and THROW.
      // The paired optic hands both final values to ONE reconstruction.
      var program =
          BOUNDS.modifyF(
              CoupledFields::loadBounds, start, FetchApplicative.<String, Integer>instance());

      Fetch.RunResult<String, Range> result =
          Fetch.runCached(FETCH.narrow(program), backend::resolve);

      assertThat(result.value()).isEqualTo(new Range(11, 12));
      assertThat(backend.calls.get()).isEqualTo(1); // one round-trip for both fields
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(backend.lastBatch).containsExactlyInAnyOrder("lo", "hi");
    }

    @Test
    @DisplayName("invariant is enforced at the single atomic reconstruction, not field-by-field")
    void invariantEnforcedAtomically() {
      // Backend resolves to lo=20, hi=5 -> the COUPLED reconstruction must reject it.
      var backend = new BoundsBackend(20, 5);
      Range start = new Range(1, 2);

      var program =
          BOUNDS.modifyF(
              CoupledFields::loadBounds, start, FetchApplicative.<String, Integer>instance());

      // The failure surfaces exactly once, at the atomic rebuild after the single batch;
      // no illegal intermediate Range was ever constructed along the way.
      assertThatThrownBy(() -> Fetch.runCached(FETCH.narrow(program), backend::resolve))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("invariant violated");
      assertThat(backend.calls.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("cross-round cache + async BatchLoader bridge")
  class CachingAndBridge {

    @Test
    @DisplayName("a key reused across a flatMap round is served from cache, not re-fetched")
    void crossRoundCacheEliminatesRefetch() {
      var backend = new BatchingBackend();

      // Stage 1 fetches "u1"; stage 2 (data-dependent) needs "u1" AGAIN plus "u2".
      Fetch<String, String, String> program =
          Fetch.<String, String>fetch("u1")
              .flatMap(
                  first ->
                      Fetch.ap(
                          Fetch.<String, String>fetch("u1")
                              .map(a -> (Function<String, String>) b -> first + "|" + a + "|" + b),
                          Fetch.<String, String>fetch("u2")));

      Fetch.RunResult<String, String> r = Fetch.runCached(program, backend::resolve);

      assertThat(r.value()).isEqualTo("User#u1|User#u1|User#u2");
      assertThat(r.rounds()).isEqualTo(2); // monadic dependency => two rounds
      assertThat(r.backendCalls()).isEqualTo(2);
      // Round 1 fetched {u1}; round 2 only fetched {u2} ("u1" came from cache).
      assertThat(r.fetchedBatches().get(0)).containsExactly("u1");
      assertThat(r.fetchedBatches().get(1)).containsExactly("u2");
      assertThat(r.cacheHits()).isEqualTo(1);
    }

    @Test
    @DisplayName("an optic traversal drives ONE async batch dispatch")
    void opticTraversalThroughAsyncBatchLoader() throws Exception {
      var dispatches = new AtomicInteger();
      BatchLoader<String, String> loader =
          keys ->
              CompletableFuture.supplyAsync(
                  () -> {
                    dispatches.incrementAndGet();
                    Map<String, String> out = new HashMap<>();
                    for (String k : keys) {
                      out.put(k, "User#" + k);
                    }
                    return out;
                  });

      Traversal<List<String>, String> ids = FocusPaths.listElements();
      List<String> input = List.of("a", "b", "a", "c"); // note the duplicate

      var program = ids.modifyF(fetchName(), input, FetchApplicative.instance());

      Fetch.RunResult<String, List<String>> r =
          Fetch.runAsync(FETCH.narrow(program), loader, new ConcurrentHashMap<>()).get();

      assertThat(r.value()).containsExactly("User#a", "User#b", "User#a", "User#c");
      assertThat(dispatches.get()).isEqualTo(1); // whole traversal => one dispatch
      assertThat(r.backendCalls()).isEqualTo(1);
      assertThat(r.fetchedBatches().get(0)).containsExactly("a", "b", "c"); // deduped
    }

    @Test
    @DisplayName("BatchLoaders bridges a blocking mapped resolver unchanged")
    void bridgeAdaptsBlockingResolver() throws Exception {
      var calls = new AtomicInteger();
      BatchLoader<String, String> loader =
          BatchLoaders.fromMappedResolver(
              keys -> {
                calls.incrementAndGet();
                Map<String, String> out = new HashMap<>();
                keys.forEach(k -> out.put(k, "User#" + k));
                return out;
              });

      Traversal<List<String>, String> ids = FocusPaths.listElements();
      var program = ids.modifyF(fetchName(), List.of("1", "2", "3"), FetchApplicative.instance());

      var r = Fetch.runAsync(FETCH.narrow(program), loader, new ConcurrentHashMap<>()).get();

      assertThat(r.value()).containsExactly("User#1", "User#2", "User#3");
      assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("BatchLoaders adapts an already-async resolver unchanged")
    void bridgeAdaptsAsyncResolver() throws Exception {
      var calls = new AtomicInteger();
      BatchLoader<String, String> loader =
          BatchLoaders.fromAsyncResolver(
              keys ->
                  CompletableFuture.supplyAsync(
                      () -> {
                        calls.incrementAndGet();
                        Map<String, String> out = new HashMap<>();
                        keys.forEach(k -> out.put(k, "User#" + k));
                        return out;
                      }));

      Traversal<List<String>, String> ids = FocusPaths.listElements();
      var program = ids.modifyF(fetchName(), List.of("7", "8"), FetchApplicative.instance());

      var r = Fetch.runAsync(FETCH.narrow(program), loader, new ConcurrentHashMap<>()).get();

      assertThat(r.value()).containsExactly("User#7", "User#8");
      assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a round whose keys are all cached makes no backend call (runCached)")
    void allCachedRoundMakesNoBackendCall() {
      var backend = new BatchingBackend();
      // Stage 2 re-requests the SAME key stage 1 already fetched.
      Fetch<String, String, String> program =
          Fetch.<String, String>fetch("k").flatMap(v -> Fetch.<String, String>fetch("k"));

      Fetch.RunResult<String, String> r = Fetch.runCached(program, backend::resolve);

      assertThat(r.value()).isEqualTo("User#k");
      assertThat(r.rounds()).isEqualTo(2);
      assertThat(r.backendCalls()).isEqualTo(1); // round 2 was fully cached
      assertThat(r.cacheHits()).isEqualTo(1);
      assertThat(backend.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a round whose keys are all cached dispatches no loader call (runAsync)")
    void allCachedRoundMakesNoDispatch() throws Exception {
      var dispatches = new AtomicInteger();
      BatchLoader<String, String> loader =
          keys ->
              CompletableFuture.supplyAsync(
                  () -> {
                    dispatches.incrementAndGet();
                    Map<String, String> out = new HashMap<>();
                    keys.forEach(k -> out.put(k, "User#" + k));
                    return out;
                  });
      Fetch<String, String, String> program =
          Fetch.<String, String>fetch("k").flatMap(v -> Fetch.<String, String>fetch("k"));

      Fetch.RunResult<String, String> r =
          Fetch.runAsync(program, loader, new ConcurrentHashMap<>()).get();

      assertThat(r.value()).isEqualTo("User#k");
      assertThat(r.rounds()).isEqualTo(2);
      assertThat(r.backendCalls()).isEqualTo(1);
      assertThat(dispatches.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("BatchLoaders runs a blocking resolver on the supplied executor")
    void bridgeRunsOnSuppliedExecutor() throws Exception {
      var executorUses = new AtomicInteger();
      Executor counting =
          runnable -> {
            executorUses.incrementAndGet();
            runnable.run();
          };
      BatchLoader<String, String> loader =
          BatchLoaders.fromMappedResolver(
              keys -> {
                Map<String, String> out = new HashMap<>();
                keys.forEach(k -> out.put(k, "User#" + k));
                return out;
              },
              counting);

      Traversal<List<String>, String> ids = FocusPaths.listElements();
      var program = ids.modifyF(fetchName(), List.of("1", "2"), FetchApplicative.instance());

      var r = Fetch.runAsync(FETCH.narrow(program), loader, new ConcurrentHashMap<>()).get();

      assertThat(r.value()).containsExactly("User#1", "User#2");
      assertThat(executorUses.get()).isEqualTo(1); // the supplied executor ran the blocking work
    }
  }
}
