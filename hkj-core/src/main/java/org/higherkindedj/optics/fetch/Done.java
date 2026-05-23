// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

/**
 * A completed {@link Fetch} computation with no outstanding requests.
 *
 * <p>Package-private: not part of the public surface. The exposed API is {@link Fetch#fetch},
 * {@link Fetch#runCached}, {@link Fetch#runAsync}, {@link FetchApplicative}, and the runner result
 * types; the {@code Done}/{@link Blocked} ADT is an implementation detail.
 */
record Done<K, V, A>(A value) implements Fetch<K, V, A> {}
