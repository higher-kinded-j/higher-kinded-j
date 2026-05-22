// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Prototype: a request-batching {@link org.higherkindedj.hkt.Applicative} for optic traversals.
 *
 * <p>This package demonstrates that the van Laarhoven encoding of HKJ optics ({@code
 * Optic.modifyF(f, s, applicative)}) is a sufficient seam to add Haxl-style request batching
 * <em>without modifying the optic core</em>. The {@link org.higherkindedj.optics.fetch.Fetch} type
 * is a free-applicative-style reification of deferred data requests; {@link
 * org.higherkindedj.optics.fetch.FetchApplicative} merges the pending request sets of independent
 * branches so that a deep traversal collapses to a single batched backend call (eliminating the N+1
 * access pattern), with deduplication. The package is transport- and datastore-neutral: the backend
 * is any keyed lookup (a repository batch query, a GraphQL mapped batch loader, an in-memory map),
 * supplied via {@link org.higherkindedj.optics.fetch.BatchLoader}.
 *
 * <p>It also makes the fundamental Applicative-vs-Monad batching boundary explicit: {@link
 * org.higherkindedj.optics.fetch.Fetch#flatMap} cannot coalesce across a data dependency and is
 * resolved in multiple rounds, whereas applicative composition is resolved in one.
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
 * <p>This is a prototype, and the package is intentionally <em>not exported</em> by the module: its
 * types (including the {@code Fetch.Done}/{@code Fetch.Blocked} ADT and {@code PendingKeys}) are
 * not public API. The intended surface is {@code Fetch.fetch}, {@code Fetch.runCached}/{@code
 * runAsync}, {@code FetchApplicative}, and {@code BatchLoader}/{@code BatchLoaders}. If the
 * prototype graduates, the ADT constructors move to package-private and the surface is exported
 * deliberately.
 */
@NullMarked
package org.higherkindedj.optics.fetch;

import org.jspecify.annotations.NullMarked;
