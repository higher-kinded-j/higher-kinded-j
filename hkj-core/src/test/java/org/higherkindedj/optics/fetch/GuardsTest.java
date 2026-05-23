// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Guards: per-round budget enforcement and audit")
class GuardsTest {

  private static Function<Set<Integer>, Map<Integer, Integer>> doublerResolver(
      AtomicInteger calls) {
    return ids -> {
      calls.incrementAndGet();
      Map<Integer, Integer> out = new HashMap<>();
      ids.forEach(id -> out.put(id, id * 2));
      return out;
    };
  }

  private static Fetch<Integer, Integer, List<Integer>> threeFetches() {
    Fetch<Integer, Integer, Integer> a = Fetch.fetch(1);
    Fetch<Integer, Integer, Integer> b = Fetch.fetch(2);
    Fetch<Integer, Integer, Integer> c = Fetch.fetch(3);
    Fetch<Integer, Integer, List<Integer>> combined =
        Fetch.ap(a.map(va -> vb -> List.of(va, vb)), b);
    return Fetch.ap(
        combined.map(
            xs ->
                vc -> {
                  List<Integer> appended = new ArrayList<>(xs);
                  appended.add(vc);
                  return List.copyOf(appended);
                }),
        c);
  }

  @Nested
  @DisplayName("Factory builders")
  class Factories {

    @Test
    @DisplayName("none() passes any round")
    void noneAlwaysPasses() {
      Guards.<Integer>none().check(Set.of(1, 2, 3), 0, 0);
      Guards.<Integer>none().check(Set.of(), 99, 99);
    }

    @Test
    @DisplayName("maxKeysPerRound rejects negative budgets and oversize rounds")
    void maxKeysPerRoundRejects() {
      assertThatThrownBy(() -> Guards.<Integer>maxKeysPerRound(-1))
          .isInstanceOf(IllegalArgumentException.class);

      Guard<Integer> guard = Guards.maxKeysPerRound(2);
      guard.check(Set.of(1, 2), 0, 0); // at the budget
      assertThatThrownBy(() -> guard.check(Set.of(1, 2, 3), 0, 0))
          .isInstanceOf(GuardViolationException.class)
          .hasMessageContaining("keysPerRound=3")
          .hasMessageContaining("budget=2");
    }

    @Test
    @DisplayName("maxRounds rejects negative budgets and counts from zero")
    void maxRoundsRejects() {
      assertThatThrownBy(() -> Guards.<Integer>maxRounds(-1))
          .isInstanceOf(IllegalArgumentException.class);

      Guard<Integer> guard = Guards.maxRounds(2);
      guard.check(Set.of(1), 0, 0);
      guard.check(Set.of(2), 1, 0);
      assertThatThrownBy(() -> guard.check(Set.of(3), 2, 0))
          .isInstanceOf(GuardViolationException.class)
          .hasMessageContaining("rounds=3");
    }

    @Test
    @DisplayName("maxBackendCalls is stateless: the runner supplies the count, the guard reads it")
    void maxBackendCallsIsStateless() {
      assertThatThrownBy(() -> Guards.<Integer>maxBackendCalls(-1))
          .isInstanceOf(IllegalArgumentException.class);

      Guard<Integer> guard = Guards.maxBackendCalls(1);
      guard.check(Set.of(), 0, 0); // empty round: no dispatch, no budget consumed
      guard.check(Set.of(1), 1, 0); // would push count to 1: at budget, allowed
      assertThatThrownBy(() -> guard.check(Set.of(2), 2, 1))
          .isInstanceOf(GuardViolationException.class)
          .hasMessageContaining("backendCalls=2");

      // Reusing the same instance with a fresh count (e.g. another run) still works.
      guard.check(Set.of(7), 0, 0);
    }

    @Test
    @DisplayName("audit() records every round and never refuses")
    void auditSinkRecords() {
      List<Set<Integer>> seen = new ArrayList<>();
      List<Integer> rounds = new ArrayList<>();
      Guard<Integer> guard =
          Guards.audit(
              (keys, round) -> {
                seen.add(keys);
                rounds.add(round);
              });

      guard.check(Set.of(1, 2), 0, 0);
      guard.check(Set.of(3), 1, 1);

      assertThat(seen).containsExactly(Set.of(1, 2), Set.of(3));
      assertThat(rounds).containsExactly(0, 1);
    }

    @Test
    @DisplayName("audit() rejects a null sink")
    void auditRejectsNullSink() {
      assertThatThrownBy(() -> Guards.<Integer>audit(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("and() composes two guards; either may refuse")
    void andComposes() {
      Guard<Integer> guard = Guards.<Integer>maxKeysPerRound(5).and(Guards.maxRounds(2));

      guard.check(Set.of(1, 2), 0, 0);
      guard.check(Set.of(3, 4), 1, 1);

      assertThatThrownBy(() -> guard.check(Set.of(5), 2, 2))
          .isInstanceOf(GuardViolationException.class)
          .hasMessageContaining("rounds=3");
      assertThatThrownBy(() -> guard.check(Set.of(1, 2, 3, 4, 5, 6), 0, 0))
          .isInstanceOf(GuardViolationException.class)
          .hasMessageContaining("keysPerRound=6");
    }

    @Test
    @DisplayName("and() rejects a null other")
    void andRejectsNull() {
      assertThatThrownBy(() -> Guards.<Integer>none().and(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("runCached enforcement")
  class CachedRunner {

    @Test
    @DisplayName("a guard that passes lets the run complete normally")
    void passingGuardCompletes() {
      var calls = new AtomicInteger();
      Fetch.RunResult<Integer, List<Integer>> result =
          Guards.runCached(threeFetches(), doublerResolver(calls), Guards.maxKeysPerRound(5));

      assertThat(result.value()).containsExactly(2, 4, 6);
      assertThat(result.rounds()).isEqualTo(1);
      assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a guard that refuses aborts the run with GuardViolationException")
    void refusingGuardAborts() {
      var calls = new AtomicInteger();
      assertThatThrownBy(
              () ->
                  Guards.runCached(
                      threeFetches(), doublerResolver(calls), Guards.maxKeysPerRound(2)))
          .isInstanceOf(GuardViolationException.class)
          .hasMessageContaining("keysPerRound=3");
      assertThat(calls.get()).as("resolver must not be called for a refused round").isZero();
    }

    @Test
    @DisplayName("audit() records the keyset every round")
    void auditCapturesKeyset() {
      List<Set<Integer>> seen = new ArrayList<>();
      var calls = new AtomicInteger();
      Guards.runCached(
          threeFetches(),
          doublerResolver(calls),
          Guards.audit((keys, round) -> seen.add(Set.copyOf(keys))));

      assertThat(seen).hasSize(1);
      assertThat(seen.get(0)).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("runCached rejects null arguments")
    void runCachedRejectsNulls() {
      Fetch<Integer, Integer, Integer> p = Fetch.fetch(1);
      Function<Set<Integer>, Map<Integer, Integer>> resolver = doublerResolver(new AtomicInteger());
      Guard<Integer> guard = Guards.none();

      assertThatThrownBy(() -> Guards.runCached(null, resolver, guard))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Guards.runCached(p, null, guard))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Guards.runCached(p, resolver, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("runAsync enforcement")
  class AsyncRunner {

    private BatchLoader<Integer, Integer> asyncDoubler(AtomicInteger calls) {
      return ids ->
          CompletableFuture.supplyAsync(
              () -> {
                calls.incrementAndGet();
                Map<Integer, Integer> out = new HashMap<>();
                ids.forEach(id -> out.put(id, id * 2));
                return out;
              });
    }

    @Test
    @DisplayName("a passing guard completes the future normally")
    void passingGuardCompletes() throws Exception {
      var calls = new AtomicInteger();
      Fetch.RunResult<Integer, List<Integer>> result =
          Guards.runAsync(
                  threeFetches(),
                  asyncDoubler(calls),
                  new ConcurrentHashMap<>(),
                  Guards.maxKeysPerRound(5))
              .get();

      assertThat(result.value()).containsExactly(2, 4, 6);
      assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a refused round fails the future with GuardViolationException")
    void refusedFutureFails() {
      var calls = new AtomicInteger();
      CompletableFuture<Fetch.RunResult<Integer, List<Integer>>> future =
          Guards.runAsync(
              threeFetches(),
              asyncDoubler(calls),
              new ConcurrentHashMap<>(),
              Guards.maxKeysPerRound(2));

      assertThatThrownBy(future::get)
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(GuardViolationException.class);
      assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("runAsync rejects null arguments")
    void runAsyncRejectsNulls() {
      Fetch<Integer, Integer, Integer> p = Fetch.fetch(1);
      BatchLoader<Integer, Integer> loader = asyncDoubler(new AtomicInteger());
      Map<Integer, Integer> cache = new ConcurrentHashMap<>();
      Guard<Integer> guard = Guards.none();

      assertThatThrownBy(() -> Guards.runAsync(null, loader, cache, guard))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Guards.runAsync(p, null, cache, guard))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Guards.runAsync(p, loader, null, guard))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Guards.runAsync(p, loader, cache, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Multi-round caching paths")
  class MultiRound {

    @Test
    @DisplayName(
        "cached cached: a flatMap that re-asks for an already-fetched key counts a cache hit and dispatches nothing")
    void cachedSecondRoundIsHitOnly() {
      var calls = new AtomicInteger();
      Fetch<Integer, Integer, Integer> program =
          Fetch.<Integer, Integer>fetch(1).flatMap(v -> Fetch.fetch(1));
      Fetch.RunResult<Integer, Integer> result =
          Guards.runCached(program, doublerResolver(calls), Guards.none());

      assertThat(result.value()).isEqualTo(2);
      assertThat(result.cacheHits()).isEqualTo(1);
      assertThat(result.backendCalls()).isEqualTo(1);
      assertThat(result.rounds()).isEqualTo(2);
      assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "async: a flatMap that re-asks for an already-fetched key counts a cache hit and dispatches nothing")
    void asyncCachedSecondRoundIsHitOnly() throws Exception {
      var calls = new AtomicInteger();
      Fetch<Integer, Integer, Integer> program =
          Fetch.<Integer, Integer>fetch(1).flatMap(v -> Fetch.fetch(1));
      BatchLoader<Integer, Integer> loader =
          ids ->
              CompletableFuture.supplyAsync(
                  () -> {
                    calls.incrementAndGet();
                    Map<Integer, Integer> out = new HashMap<>();
                    ids.forEach(id -> out.put(id, id * 2));
                    return out;
                  });
      Fetch.RunResult<Integer, Integer> result =
          Guards.runAsync(program, loader, new ConcurrentHashMap<>(), Guards.none()).get();

      assertThat(result.value()).isEqualTo(2);
      assertThat(result.cacheHits()).isEqualTo(1);
      assertThat(result.backendCalls()).isEqualTo(1);
      assertThat(result.rounds()).isEqualTo(2);
      assertThat(calls.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Resolver contract violations propagate (cached)")
  class CachedContractViolations {

    @Test
    @DisplayName("a resolver that returns null fails fast")
    void cachedResolverNullMap() {
      Fetch<Integer, Integer, Integer> p = Fetch.fetch(1);
      assertThatThrownBy(() -> Guards.runCached(p, keys -> null, Guards.none()))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("batchResolver returned null");
    }

    @Test
    @DisplayName("a resolver that omits a requested key surfaces MissingKeyException")
    void cachedResolverMissingKey() {
      Fetch<Integer, Integer, Integer> p = Fetch.fetch(1);
      assertThatThrownBy(() -> Guards.runCached(p, keys -> Map.of(), Guards.none()))
          .isInstanceOf(MissingKeyException.class);
    }
  }

  @Nested
  @DisplayName("Resolver contract violations propagate (async)")
  class AsyncContractViolations {

    @Test
    @DisplayName("a loader that returns a null future fails fast")
    void asyncLoaderNullFuture() {
      Fetch<Integer, Integer, Integer> p = Fetch.fetch(1);
      BatchLoader<Integer, Integer> nullLoader = keys -> null;
      assertThatThrownBy(
              () -> Guards.runAsync(p, nullLoader, new ConcurrentHashMap<>(), Guards.none()))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("loader returned a null future");
    }

    @Test
    @DisplayName("a loader future resolving to null fails the run")
    void asyncLoaderNullMap() {
      Fetch<Integer, Integer, Integer> p = Fetch.fetch(1);
      BatchLoader<Integer, Integer> nullMap = keys -> CompletableFuture.completedFuture(null);
      CompletableFuture<Fetch.RunResult<Integer, Integer>> future =
          Guards.runAsync(p, nullMap, new ConcurrentHashMap<>(), Guards.none());
      assertThatThrownBy(future::get)
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("loader resolved to null");
    }

    @Test
    @DisplayName("a loader that omits a requested key surfaces MissingKeyException in the future")
    void asyncLoaderMissingKey() {
      Fetch<Integer, Integer, Integer> p = Fetch.fetch(1);
      BatchLoader<Integer, Integer> partial = keys -> CompletableFuture.completedFuture(Map.of());
      CompletableFuture<Fetch.RunResult<Integer, Integer>> future =
          Guards.runAsync(p, partial, new ConcurrentHashMap<>(), Guards.none());
      assertThatThrownBy(future::get)
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(MissingKeyException.class);
    }
  }

  @Nested
  @DisplayName("Stateless guards are safe to reuse across runs")
  class Reuse {

    @Test
    @DisplayName(
        "a single maxBackendCalls instance can serve two independent runs without leaking state")
    void maxBackendCallsReuses() {
      var calls = new AtomicInteger();
      Guard<Integer> guard = Guards.maxBackendCalls(1);

      Fetch<Integer, Integer, Integer> p1 = Fetch.fetch(1);
      Fetch<Integer, Integer, Integer> p2 = Fetch.fetch(2);

      // First run: one backend call, at the budget.
      Guards.runCached(p1, doublerResolver(calls), guard);
      // Second run shares the same Guard instance. A stateful counter would have rejected this.
      Guards.runCached(p2, doublerResolver(calls), guard);

      assertThat(calls.get()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("GuardViolationException carries the offending context")
  class ViolationContext {

    @Test
    @DisplayName("roundIndex and pendingKeys are exposed and immutable")
    void exceptionExposesContext() {
      try {
        Guards.runCached(
            threeFetches(), doublerResolver(new AtomicInteger()), Guards.maxKeysPerRound(0));
      } catch (GuardViolationException e) {
        assertThat(e.roundIndex()).isZero();
        assertThat(e.pendingKeys()).isEqualTo(Set.of(1, 2, 3));
        assertThatThrownBy(() -> e.pendingKeys().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        return;
      }
      throw new AssertionError("expected GuardViolationException");
    }
  }
}
