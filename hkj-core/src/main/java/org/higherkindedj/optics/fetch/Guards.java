// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Standard {@link Guard} factories and guarded runners.
 *
 * <p>A guard is inspected once per round, before the round's keyset would dispatch. The factories
 * here ({@link #maxKeysPerRound}, {@link #maxRounds}, {@link #maxBackendCalls}, {@link #audit})
 * cover the common audit and rate-limit cases; compose them with {@link Guard#and(Guard)}.
 *
 * <p>The runners ({@link #runCached}, {@link #runAsync}) wrap the substrate runners and interpose
 * the guard at the round boundary. A guard that throws aborts the run with {@link
 * GuardViolationException}; in the async runner the returned future completes exceptionally with
 * the same exception. For railway-style capture see {@link SafeFetch#runCachedWithGuard} and {@link
 * SafeFetch#runAsyncWithGuard}.
 */
public final class Guards {

  private Guards() {}

  /** A guard that always passes. Useful as the identity for {@link Guard#and(Guard)} chains. */
  public static <K> Guard<K> none() {
    return (keys, round, calls) -> {};
  }

  /** Refuse a round whose pending keyset has more than {@code max} entries. */
  public static <K> Guard<K> maxKeysPerRound(int max) {
    if (max < 0) {
      throw new IllegalArgumentException("maxKeysPerRound must be >= 0, was " + max);
    }
    return (keys, round, calls) -> {
      if (keys.size() > max) {
        throw new GuardViolationException(
            "keysPerRound=" + keys.size() + " exceeds budget=" + max, round, keys);
      }
    };
  }

  /** Refuse to start a round numbered {@code max} or higher (i.e. cap rounds to {@code max}). */
  public static <K> Guard<K> maxRounds(int max) {
    if (max < 0) {
      throw new IllegalArgumentException("maxRounds must be >= 0, was " + max);
    }
    return (keys, round, calls) -> {
      if (round >= max) {
        throw new GuardViolationException(
            "rounds=" + (round + 1) + " exceeds budget=" + max, round, keys);
      }
    };
  }

  /**
   * Refuse a round whose keyset would push the total backend-call count above {@code max}. A round
   * with an empty pending keyset (everything already cached) does not consume a backend-call slot.
   *
   * <p>Stateless and reusable across runs: the count is supplied by the runner on each {@code
   * check} call, so the same guard instance is safe to share between concurrent invocations.
   */
  public static <K> Guard<K> maxBackendCalls(int max) {
    if (max < 0) {
      throw new IllegalArgumentException("maxBackendCalls must be >= 0, was " + max);
    }
    return (keys, round, callsSoFar) -> {
      if (!keys.isEmpty() && callsSoFar >= max) {
        int wouldBe = callsSoFar + 1;
        throw new GuardViolationException(
            "backendCalls=" + wouldBe + " exceeds budget=" + max, round, keys);
      }
    };
  }

  /**
   * A pass-through guard that records every round's keyset for audit. Never throws. Useful in an
   * {@link Guard#and(Guard)} chain to log what an enforcement guard let through.
   */
  public static <K> Guard<K> audit(BiConsumer<Set<K>, Integer> sink) {
    requireNonNull(sink, "sink");
    return (keys, round, calls) -> sink.accept(keys, round);
  }

  /**
   * The cached runner from {@link Fetch#runCached(Fetch, Function)} with the guard interposed at
   * the round boundary. The guard sees the per-round uncached keyset just before it would dispatch.
   *
   * @throws GuardViolationException if any round's guard refuses
   */
  public static <K, V, A> Fetch.RunResult<K, A> runCached(
      Fetch<K, V, A> fetch, Function<Set<K>, Map<K, V>> batchResolver, Guard<K> guard) {
    requireNonNull(fetch, "fetch");
    requireNonNull(batchResolver, "batchResolver");
    requireNonNull(guard, "guard");
    Map<K, V> cache = new HashMap<>();
    int rounds = 0;
    int backendCalls = 0;
    int cacheHits = 0;
    ArrayList<Set<K>> fetchedBatches = new ArrayList<>();
    Fetch<K, V, A> current = fetch;
    while (current instanceof Blocked<K, V, A> blocked) {
      Set<K> pending = blocked.pending().flatten();
      Set<K> uncached = LinkedHashSet.newLinkedHashSet(pending.size());
      for (K k : pending) {
        if (cache.containsKey(k)) {
          cacheHits++;
        } else {
          uncached.add(k);
        }
      }
      Set<K> roundKeys = Collections.unmodifiableSet(uncached);
      guard.check(roundKeys, rounds, backendCalls);
      rounds++;
      if (!roundKeys.isEmpty()) {
        backendCalls++;
        fetchedBatches.add(roundKeys);
        Map<K, V> returned = batchResolver.apply(roundKeys);
        requireNonNull(returned, "batchResolver returned null");
        requireAllResolved(roundKeys, returned);
        cache.putAll(returned);
      }
      Map<K, V> view = Collections.unmodifiableMap(cache);
      current = blocked.resume().apply(view).run();
    }
    @SuppressWarnings("unchecked")
    Done<K, V, A> done = (Done<K, V, A>) current;
    return new Fetch.RunResult<>(
        done.value(), rounds, backendCalls, List.copyOf(fetchedBatches), cacheHits);
  }

  /**
   * The async runner from {@link Fetch#runAsync(Fetch, BatchLoader, Map)} with the guard
   * interposed. The future completes exceptionally with {@link GuardViolationException} on the
   * first round the guard refuses.
   */
  public static <K, V, A> CompletableFuture<Fetch.RunResult<K, A>> runAsync(
      Fetch<K, V, A> fetch, BatchLoader<K, V> loader, Map<K, V> cache, Guard<K> guard) {
    requireNonNull(fetch, "fetch");
    requireNonNull(loader, "loader");
    requireNonNull(cache, "cache");
    requireNonNull(guard, "guard");
    return runAsyncLoop(fetch, loader, cache, guard, 0, 0, 0, new ArrayList<>());
  }

  private static <K, V, A> CompletableFuture<Fetch.RunResult<K, A>> runAsyncLoop(
      Fetch<K, V, A> current,
      BatchLoader<K, V> loader,
      Map<K, V> cache,
      Guard<K> guard,
      int rounds,
      int backendCalls,
      int cacheHits,
      List<Set<K>> fetchedBatches) {
    if (current instanceof Blocked<K, V, A> blocked) {
      Set<K> pending = blocked.pending().flatten();
      Set<K> uncached = LinkedHashSet.newLinkedHashSet(pending.size());
      int hits = cacheHits;
      for (K k : pending) {
        if (cache.containsKey(k)) {
          hits++;
        } else {
          uncached.add(k);
        }
      }
      Set<K> roundKeys = Collections.unmodifiableSet(uncached);
      try {
        guard.check(roundKeys, rounds, backendCalls);
      } catch (RuntimeException violation) {
        return CompletableFuture.failedFuture(violation);
      }
      int nextRounds = rounds + 1;
      int finalHits = hits;
      CompletableFuture<Map<K, V>> dispatch =
          roundKeys.isEmpty()
              ? CompletableFuture.completedFuture(Map.of())
              : loader.loadAll(roundKeys);
      requireNonNull(dispatch, "loader returned a null future");
      if (!roundKeys.isEmpty()) {
        fetchedBatches.add(roundKeys);
      }
      int nextBackendCalls = roundKeys.isEmpty() ? backendCalls : backendCalls + 1;
      return dispatch.thenCompose(
          loaded -> {
            requireNonNull(loaded, "loader resolved to null");
            requireAllResolved(roundKeys, loaded);
            cache.putAll(loaded);
            Map<K, V> view = Collections.unmodifiableMap(cache);
            return runAsyncLoop(
                blocked.resume().apply(view).run(),
                loader,
                cache,
                guard,
                nextRounds,
                nextBackendCalls,
                finalHits,
                fetchedBatches);
          });
    }
    @SuppressWarnings("unchecked")
    Done<K, V, A> done = (Done<K, V, A>) current;
    return CompletableFuture.completedFuture(
        new Fetch.RunResult<>(
            done.value(), rounds, backendCalls, List.copyOf(fetchedBatches), cacheHits));
  }

  private static <K, V> void requireAllResolved(Set<K> requested, Map<K, V> returned) {
    LinkedHashSet<K> missing = new LinkedHashSet<>();
    for (K k : requested) {
      if (!returned.containsKey(k)) {
        missing.add(k);
      }
    }
    if (!missing.isEmpty()) {
      throw new MissingKeyException(missing);
    }
  }
}
