// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Adapters that turn an ordinary keyed lookup into a {@link BatchLoader} for {@link
 * Fetch#runAsync}.
 *
 * <p>These are transport- and datastore-neutral. A {@code BatchLoader} is simply {@code Set<K> ->
 * CompletableFuture<Map<K, V>>}; anything that resolves a set of keys in one call can be a backend:
 *
 * <ul>
 *   <li>a repository batch lookup ({@code findAllById}, a single {@code IN}/{@code $in} query);
 *   <li>a GraphQL {@code MappedBatchLoader} (the shapes are identical; see {@link BatchLoader});
 *   <li>an in-memory map, a remote service, a cache tier; anything keyed.
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Navigate with optics + the batching applicative; the whole traversal becomes one Fetch:
 * var program = teamTraversal.modifyF(
 *     id -> FETCH.widen(Fetch.fetch(id)),
 *     team,
 *     FetchApplicative.<String, User>instance());
 *
 * // Drive it through any keyed backend:
 * CompletableFuture<Fetch.RunResult<String, ...>> result =
 *     Fetch.runAsync(FETCH.narrow(program),
 *                    BatchLoaders.fromMappedResolver(userRepo::findAllByIdAsMap),
 *                    new ConcurrentHashMap<>());
 * }</pre>
 *
 * <p>The boundary it plugs into (a GraphQL resolver, a REST controller, a batch job) is the
 * caller's choice and lives outside this class.
 */
public final class BatchLoaders {

  /**
   * Default executor for the blocking {@link #fromMappedResolver(Function)} adapter: one virtual
   * thread per task. Virtual threads suit blocking I/O and avoid starving a shared platform-thread
   * pool such as {@code ForkJoinPool.commonPool()}.
   */
  private static final Executor BLOCKING_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private BatchLoaders() {}

  /**
   * Adapts a synchronous keyed lookup (for example a blocking repository's {@code findAllById})
   * into an async {@link BatchLoader}, running it on a virtual-thread-per-task executor. Use {@link
   * #fromMappedResolver(Function, Executor)} to run on a specific executor instead.
   */
  public static <K, V> BatchLoader<K, V> fromMappedResolver(Function<Set<K>, Map<K, V>> resolver) {
    return fromMappedResolver(resolver, BLOCKING_EXECUTOR);
  }

  /**
   * Adapts a synchronous keyed lookup into an async {@link BatchLoader}, running it on the supplied
   * {@code executor}. Prefer this overload when the blocking work should run on an executor sized
   * and isolated for it rather than on a shared pool.
   */
  public static <K, V> BatchLoader<K, V> fromMappedResolver(
      Function<Set<K>, Map<K, V>> resolver, Executor executor) {
    return keys -> CompletableFuture.supplyAsync(() -> resolver.apply(keys), executor);
  }

  /**
   * Adapts an already-asynchronous keyed lookup into a {@link BatchLoader}. This is an
   * identity-level bridge; it exists to document that a function of the right shape (such as a
   * GraphQL {@code MappedBatchLoader}) needs no real adaptation.
   */
  public static <K, V> BatchLoader<K, V> fromAsyncResolver(
      Function<Set<K>, CompletableFuture<Map<K, V>>> asyncResolver) {
    return asyncResolver::apply;
  }

  /**
   * Decorates a {@link BatchLoader} so that no single dispatch carries more than {@code
   * maxBatchSize} keys. A larger key set is split into bounded chunks, each loaded concurrently,
   * and the results merged. Useful where a backend caps how many keys one query may carry (the
   * length of an {@code IN}/{@code $in} list, a URL, a request body).
   *
   * @param loader the loader to bound
   * @param maxBatchSize the largest key set a single dispatch may carry; must be at least 1
   * @throws IllegalArgumentException if {@code maxBatchSize} is below 1
   */
  public static <K, V> BatchLoader<K, V> chunked(BatchLoader<K, V> loader, int maxBatchSize) {
    requireNonNull(loader, "loader");
    if (maxBatchSize < 1) {
      throw new IllegalArgumentException("maxBatchSize must be at least 1, was " + maxBatchSize);
    }
    return keys -> {
      if (keys.size() <= maxBatchSize) {
        return loader.loadAll(keys);
      }
      List<CompletableFuture<Map<K, V>>> dispatches = new ArrayList<>();
      Set<K> chunk = LinkedHashSet.newLinkedHashSet(maxBatchSize);
      for (K key : keys) {
        chunk.add(key);
        if (chunk.size() == maxBatchSize) {
          dispatches.add(loader.loadAll(chunk));
          chunk = LinkedHashSet.newLinkedHashSet(maxBatchSize);
        }
      }
      if (!chunk.isEmpty()) {
        dispatches.add(loader.loadAll(chunk));
      }
      return CompletableFuture.allOf(dispatches.toArray(new CompletableFuture<?>[0]))
          .thenApply(
              ignored -> {
                Map<K, V> merged = new HashMap<>();
                for (CompletableFuture<Map<K, V>> dispatch : dispatches) {
                  merged.putAll(dispatch.join());
                }
                return merged;
              });
    };
  }
}
