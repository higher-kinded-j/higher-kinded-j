// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import java.util.Map;
import java.util.function.Function;
import org.higherkindedj.hkt.trampoline.Trampoline;

/**
 * A {@link Fetch} computation blocked on a tree of pending requests, with a continuation that
 * resumes stack-safely (via {@link Trampoline}) once those requests have been resolved.
 *
 * <p>Package-private: not part of the public surface. The exposed API is {@link Fetch#fetch},
 * {@link Fetch#runCached}, {@link Fetch#runAsync}, {@link FetchApplicative}, and the runner result
 * types; the {@link Done}/{@code Blocked} ADT is an implementation detail.
 */
record Blocked<K, V, A>(
    PendingKeys<K> pending, Function<Map<K, V>, Trampoline<Fetch<K, V, A>>> resume)
    implements Fetch<K, V, A> {}
