// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Multi-source batching: combine per-source {@link BatchLoader}s into one.
 *
 * <p>The substrate's {@link Fetch} is single-family: one round hands one key set to one resolver.
 * Real programs draw from several backends at once (users <em>and</em> products <em>and</em>
 * prices). {@code SourceRouter} keeps the substrate single-family and combines several per-source
 * {@link BatchLoader}s into one: each round's key set is partitioned by a classifier, and every
 * source's loader is invoked exactly once with its own subset, the dispatches running concurrently.
 *
 * <p>So a traversal touching N sources still resolves in one substrate round, fanned out to one
 * dispatch per source. The combined loader is an ordinary {@link BatchLoader}, so it composes with
 * {@link Fetch#runAsync}, {@link SafeFetch}, the per-run cache and cross-round de-duplication
 * unchanged. The router itself is fully generic in the key, value and source-tag types; it adds no
 * unchecked casts.
 */
public final class SourceRouter {

  private SourceRouter() {}

  /**
   * Combines per-source loaders into one {@link BatchLoader}.
   *
   * @param classifier maps each key to its source tag
   * @param loadersBySource the {@link BatchLoader} for each source tag
   * @param <K> key type
   * @param <V> value type
   * @param <S> source-tag type
   * @return a loader that partitions each key set by source and dispatches one batch per source. If
   *     a key classifies to a tag with no registered loader, the returned future fails with an
   *     {@link IllegalStateException}.
   */
  public static <K, V, S> BatchLoader<K, V> routed(
      Function<? super K, ? extends S> classifier,
      Map<S, ? extends BatchLoader<K, V>> loadersBySource) {
    requireNonNull(classifier, "classifier");
    requireNonNull(loadersBySource, "loadersBySource");
    Map<S, BatchLoader<K, V>> loaders = Map.copyOf(loadersBySource);

    return keys -> {
      Map<S, Set<K>> bySource = new LinkedHashMap<>();
      for (K key : keys) {
        bySource.computeIfAbsent(classifier.apply(key), tag -> new LinkedHashSet<>()).add(key);
      }

      List<CompletableFuture<Map<K, V>>> dispatches = new ArrayList<>(bySource.size());
      for (Map.Entry<S, Set<K>> family : bySource.entrySet()) {
        BatchLoader<K, V> loader = loaders.get(family.getKey());
        if (loader == null) {
          return CompletableFuture.failedFuture(
              new IllegalStateException(
                  "no BatchLoader registered for source: " + family.getKey()));
        }
        dispatches.add(loader.loadAll(family.getValue()));
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
