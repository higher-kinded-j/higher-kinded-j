// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A keyed batch-loading function: resolve a whole set of keys in one call. This is the only
 * contract {@link Fetch#runAsync} needs from a backend, and it is transport- and datastore-neutral.
 *
 * <p>Its shape ({@code Set<K> -> CompletableFuture<Map<K, V>>}) matches several existing contracts,
 * so adapting them needs little or no glue: a GraphQL {@code MappedBatchLoader}, a repository batch
 * lookup such as {@code findAllById}, or a single {@code IN}/{@code $in} query. See {@link
 * BatchLoaders} for the adapters.
 *
 * <p>The {@code Fetch} runner guarantees this is invoked once per round with the deduplicated,
 * not-yet-cached key set.
 *
 * @param <K> request key type
 * @param <V> resolved value type
 */
@FunctionalInterface
public interface BatchLoader<K, V> {

  /** Resolve a whole set of keys in one call. */
  CompletableFuture<Map<K, V>> loadAll(Set<K> keys);
}
