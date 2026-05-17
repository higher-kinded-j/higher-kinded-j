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
 * branches so that a deep traversal collapses to a single batched backend call (eliminating the
 * GraphQL/Spring-Data N+1 pattern), with intra-request deduplication.
 *
 * <p>It also makes the fundamental Applicative-vs-Monad batching boundary explicit: {@link
 * org.higherkindedj.optics.fetch.Fetch#flatMap} cannot coalesce across a data dependency and is
 * resolved in multiple rounds, whereas applicative composition is resolved in one.
 */
@NullMarked
package org.higherkindedj.optics.fetch;

import org.jspecify.annotations.NullMarked;
