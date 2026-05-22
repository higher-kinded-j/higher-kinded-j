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
import java.util.function.Function;
import org.higherkindedj.hkt.trampoline.Trampoline;

/**
 * A free-applicative-style reification of deferred, batchable data requests.
 *
 * <p>A {@code Fetch<K, V, A>} describes a computation producing an {@code A} that may require
 * resolving requests of key type {@code K} into values of type {@code V}. Building a {@code Fetch}
 * performs no I/O; only {@link #runCached(Fetch, Function)} and {@link #runAsync(Fetch,
 * BatchLoader, Map)} execute, in <em>rounds</em>, handing every key discovered in a round to a
 * single batch resolver.
 *
 * <p>The applicative combinator {@link #ap(Fetch, Fetch)} unions the pending requests of its two
 * independent arguments, so a traversal over N elements collapses to one backend call.
 *
 * <p><b>Stack safety.</b> Resolving a round is driven by the HKJ {@link Trampoline}: each {@link
 * Blocked#resume} step returns a {@code Trampoline<Fetch>} that only <em>builds</em> deferred
 * nodes, and {@link Trampoline#run()} collapses an arbitrarily deep structure in constant stack
 * space. Pending keys are held as a {@link PendingKeys} deferred-union tree so that {@code ap} is
 * O(1).
 *
 * @param <K> request key type
 * @param <V> resolved value type
 * @param <A> result type
 */
public sealed interface Fetch<K, V, A> permits Fetch.Done, Fetch.Blocked {

  /** A completed computation with no outstanding requests. */
  record Done<K, V, A>(A value) implements Fetch<K, V, A> {}

  /**
   * A computation blocked on a tree of pending requests, with a continuation that resumes (stack-
   * safely, via a {@link Trampoline}) once those requests have been resolved.
   */
  record Blocked<K, V, A>(
      PendingKeys<K> pending, Function<Map<K, V>, Trampoline<Fetch<K, V, A>>> resume)
      implements Fetch<K, V, A> {}

  /** Lifts a pure value (no requests). */
  static <K, V, A> Fetch<K, V, A> done(A value) {
    return new Done<>(value);
  }

  /** A single request for {@code key}; resolves to the value the resolver returns for it. */
  static <K, V> Fetch<K, V, V> fetch(K key) {
    requireNonNull(key, "key");
    return new Blocked<>(
        PendingKeys.one(key), resolved -> Trampoline.done(done(resolved.get(key))));
  }

  /** Functorial map: structure (and pending requests) preserved, result transformed. */
  default <B> Fetch<K, V, B> map(Function<? super A, ? extends B> f) {
    requireNonNull(f, "f");
    return switch (this) {
      case Done<K, V, A> d -> done(f.apply(d.value()));
      case Blocked<K, V, A> b ->
          new Blocked<>(
              b.pending(),
              m -> Trampoline.defer(() -> b.resume().apply(m)).map(inner -> inner.map(f)));
    };
  }

  /**
   * Applicative apply. The two arguments are independent, so their pending request trees are merged
   * (O(1)) into one round; this is what turns N per-element fetches into one batch.
   */
  @SuppressWarnings("unchecked")
  static <K, V, A, B> Fetch<K, V, B> ap(
      Fetch<K, V, ? extends Function<A, B>> ff, Fetch<K, V, A> fa) {
    requireNonNull(ff, "ff");
    requireNonNull(fa, "fa");
    Fetch<K, V, Function<A, B>> fnf = (Fetch<K, V, Function<A, B>>) ff;

    if (fnf instanceof Done<K, V, Function<A, B>> df && fa instanceof Done<K, V, A> da) {
      return done(df.value().apply(da.value()));
    }
    if (fnf instanceof Done<K, V, Function<A, B>> df) {
      Blocked<K, V, A> ba = (Blocked<K, V, A>) fa;
      return new Blocked<>(
          ba.pending(), m -> Trampoline.defer(() -> ba.resume().apply(m)).map(faR -> ap(df, faR)));
    }
    if (fa instanceof Done<K, V, A> da) {
      Blocked<K, V, Function<A, B>> bf = (Blocked<K, V, Function<A, B>>) fnf;
      return new Blocked<>(
          bf.pending(), m -> Trampoline.defer(() -> bf.resume().apply(m)).map(ffR -> ap(ffR, da)));
    }
    Blocked<K, V, Function<A, B>> bf = (Blocked<K, V, Function<A, B>>) fnf;
    Blocked<K, V, A> ba = (Blocked<K, V, A>) fa;
    return new Blocked<>(
        PendingKeys.merge(bf.pending(), ba.pending()),
        m ->
            Trampoline.defer(() -> bf.resume().apply(m))
                .flatMap(
                    ffR -> Trampoline.defer(() -> ba.resume().apply(m)).map(faR -> ap(ffR, faR))));
  }

  /**
   * Monadic bind. The continuation's requests are unknowable until {@code this} is resolved, so a
   * dependency chain of N binds costs N rounds: the Applicative/Monad batching boundary.
   */
  default <B> Fetch<K, V, B> flatMap(Function<? super A, ? extends Fetch<K, V, B>> f) {
    requireNonNull(f, "f");
    return switch (this) {
      case Done<K, V, A> d -> f.apply(d.value());
      case Blocked<K, V, A> b ->
          new Blocked<>(
              b.pending(),
              m -> Trampoline.defer(() -> b.resume().apply(m)).map(inner -> inner.flatMap(f)));
    };
  }

  /**
   * The outcome of a run. {@code rounds} counts {@link Blocked} nodes resolved; {@code
   * backendCalls} counts how many of those rounds actually hit the resolver (a round whose keys are
   * all already cached costs zero backend calls); {@code fetchedBatches} records the key set sent
   * to the resolver on each backend call; {@code cacheHits} counts individual keys served from the
   * per-run cache instead of being fetched.
   */
  record RunResult<K, A>(
      A value, int rounds, int backendCalls, List<Set<K>> fetchedBatches, int cacheHits) {}

  /**
   * Runs the computation with a per-run request cache. Each round flattens the current pending-key
   * tree, hands the uncached subset to {@code batchResolver} once, and resumes stack-safely.
   * Deduplication spans <em>rounds</em>: a key requested again in a later round (e.g. across a
   * {@link #flatMap} dependency) is served from cache and never re-fetched. Pure applicative
   * programs terminate in a single round; monadic dependency chains take one round per dependency.
   *
   * @param batchResolver resolves a whole set of keys in one call (e.g. {@code findAllById}, a
   *     single {@code $in} query, or a GraphQL batch-loader dispatch); it must return a value for
   *     every requested key (see {@link MissingKeyException})
   */
  static <K, V, A> RunResult<K, A> runCached(
      Fetch<K, V, A> fetch, Function<Set<K>, Map<K, V>> batchResolver) {
    requireNonNull(fetch, "fetch");
    requireNonNull(batchResolver, "batchResolver");
    Map<K, V> cache = new HashMap<>();
    int rounds = 0;
    int backendCalls = 0;
    int cacheHits = 0;
    ArrayList<Set<K>> fetchedBatches = new ArrayList<>();
    Fetch<K, V, A> current = fetch;
    while (current instanceof Blocked<K, V, A> blocked) {
      rounds++;
      Set<K> pending = blocked.pending().flatten();
      Set<K> uncached = LinkedHashSet.newLinkedHashSet(pending.size());
      for (K k : pending) {
        if (cache.containsKey(k)) {
          cacheHits++;
        } else {
          uncached.add(k);
        }
      }
      if (!uncached.isEmpty()) {
        backendCalls++;
        Set<K> roundKeys = Collections.unmodifiableSet(uncached);
        fetchedBatches.add(roundKeys);
        Map<K, V> returned = batchResolver.apply(roundKeys);
        requireNonNull(returned, "batchResolver returned null");
        requireAllResolved(roundKeys, returned);
        cache.putAll(returned);
      }
      Map<K, V> view = Collections.unmodifiableMap(cache);
      current = blocked.resume().apply(view).run();
    }
    // The loop exits only when `current` is no longer Blocked; Fetch is sealed, so it is Done.
    @SuppressWarnings("unchecked")
    Done<K, V, A> done = (Done<K, V, A>) current;
    return new RunResult<>(
        done.value(), rounds, backendCalls, List.copyOf(fetchedBatches), cacheHits);
  }

  /**
   * Asynchronous cached run against a {@link BatchLoader} ({@code Set<K> ->
   * CompletableFuture<Map<K, V>>}). Each {@link Blocked} round becomes one loader dispatch; the
   * supplied {@code cache} carries across rounds. See {@link BatchLoaders} for adapters from
   * repository lookups, GraphQL mapped batch loaders, or any other keyed source.
   *
   * <p>The {@code cache} is owned by this single invocation; rounds run sequentially, so it is
   * accessed single-threaded, and must not be shared across concurrent {@code runAsync} calls.
   */
  static <K, V, A> CompletableFuture<RunResult<K, A>> runAsync(
      Fetch<K, V, A> fetch, BatchLoader<K, V> loader, Map<K, V> cache) {
    requireNonNull(fetch, "fetch");
    requireNonNull(loader, "loader");
    requireNonNull(cache, "cache");
    return runAsyncLoop(fetch, loader, cache, 0, 0, 0, new ArrayList<>());
  }

  private static <K, V, A> CompletableFuture<RunResult<K, A>> runAsyncLoop(
      Fetch<K, V, A> current,
      BatchLoader<K, V> loader,
      Map<K, V> cache,
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
                nextRounds,
                nextBackendCalls,
                finalHits,
                fetchedBatches);
          });
    }
    // Fetch is sealed: not Blocked means Done.
    @SuppressWarnings("unchecked")
    Done<K, V, A> done = (Done<K, V, A>) current;
    return CompletableFuture.completedFuture(
        new RunResult<>(
            done.value(), rounds, backendCalls, List.copyOf(fetchedBatches), cacheHits));
  }

  /**
   * Verifies that a resolver returned an entry for every requested key. A round requires a value
   * for each key it asked for; a missing entry would otherwise become a silent {@code null} in the
   * result, so it is reported as a {@link MissingKeyException} instead.
   */
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
