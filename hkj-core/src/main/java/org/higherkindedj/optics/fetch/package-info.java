// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Request-batching {@link org.higherkindedj.hkt.Applicative} for optic traversals.
 *
 * <p>This package adds Haxl-style request batching at the van Laarhoven optic seam ({@code
 * Optic.modifyF(f, s, applicative)}), with no changes to the optic core. {@link
 * org.higherkindedj.optics.fetch.Fetch} is a free-applicative-style reification of deferred data
 * requests; {@link org.higherkindedj.optics.fetch.FetchApplicative} merges the pending request sets
 * of independent branches so a deep traversal collapses to a single batched backend call,
 * eliminating the N+1 access pattern, with deduplication and a per-run cache. The package is
 * transport- and datastore-neutral: the backend is any keyed lookup (a repository batch query, a
 * GraphQL mapped batch loader, an in-memory map), supplied via {@link
 * org.higherkindedj.optics.fetch.BatchLoader}.
 *
 * <p>{@link org.higherkindedj.optics.fetch.SafeFetch} adds a railway error channel (failures land
 * as {@code Either.left} values, including timeouts) and per-key partial-success helpers. {@link
 * org.higherkindedj.optics.fetch.SourceRouter} combines per-source loaders so one traversal fans
 * out to one concurrent dispatch per source. {@link
 * org.higherkindedj.optics.fetch.BatchLoaders#chunked} bounds a single dispatch's size. {@link
 * org.higherkindedj.optics.fetch.FetchOptics#fetchEach} builds a type-changing list traversal so
 * heterogeneous fetch ({@code Id -> Entity}) is ergonomic over an optic.
 *
 * <p>The Applicative/Monad batching boundary is explicit: {@link
 * org.higherkindedj.optics.fetch.Fetch#flatMap} cannot coalesce across a data dependency and is
 * resolved in multiple rounds; applicative composition is resolved in one.
 *
 * <h2>Concurrency model</h2>
 *
 * <p>{@code Fetch} values are immutable and {@code FetchApplicative} is a stateless shared
 * singleton; both are safe to share across threads. {@code Fetch.runCached} creates a fresh cache
 * per invocation and holds no shared mutable state, so independent calls run safely in parallel.
 * {@code Fetch.runAsync} resolves rounds sequentially (via {@code thenCompose}), so the {@code Map}
 * cache it is given is accessed single-threaded by that one invocation; that cache must not be
 * shared across concurrent {@code runAsync} calls. A {@link
 * org.higherkindedj.optics.fetch.BatchLoader} shared between concurrent programs must itself be
 * thread-safe.
 *
 * <h2>API surface</h2>
 *
 * <p>The package is exported. The public surface is the {@link
 * org.higherkindedj.optics.fetch.Fetch} sealed interface (with {@code fetch}, {@code done}, {@code
 * map}, {@code flatMap}, {@code ap}, {@code runCached}, {@code runAsync}, and the {@code RunResult}
 * record), {@link org.higherkindedj.optics.fetch.FetchApplicative}, {@link
 * org.higherkindedj.optics.fetch.BatchLoader}, {@link org.higherkindedj.optics.fetch.BatchLoaders},
 * {@link org.higherkindedj.optics.fetch.SafeFetch}, {@link
 * org.higherkindedj.optics.fetch.SourceRouter}, {@link org.higherkindedj.optics.fetch.FetchOptics},
 * the HKT plumbing ({@link org.higherkindedj.optics.fetch.FetchKind} / {@link
 * org.higherkindedj.optics.fetch.FetchKindHelper}), and {@link
 * org.higherkindedj.optics.fetch.MissingKeyException}. The {@code Done}/{@code Blocked} ADT and
 * {@link org.higherkindedj.optics.fetch.PendingKeys} are deliberately package-private: consumers
 * interact with {@code Fetch} through its static factories and runners, not by pattern-matching on
 * its constructors.
 *
 * <h2>Limits</h2>
 *
 * <ul>
 *   <li>Applicative-only batching: a data dependency via {@code flatMap} costs an extra round
 *       (Haxl's law, not a defect).
 *   <li>Per-run in-JVM cache only; no distributed cache.
 *   <li>Optics remain post-fetch; no predicate pushdown to the backend.
 * </ul>
 */
@NullMarked
package org.higherkindedj.optics.fetch;

import org.jspecify.annotations.NullMarked;
