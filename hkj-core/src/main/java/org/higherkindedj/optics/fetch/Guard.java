// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import java.util.Objects;
import java.util.Set;

/**
 * A per-round interceptor for {@link Fetch} runs.
 *
 * <p>A {@code Guard} is called once per round, before the round's keyset is handed to the resolver
 * or loader. It either passes (returns normally) or refuses by throwing a {@link
 * GuardViolationException}. Throwing aborts the run; the guarded {@link Fetch} runners surface the
 * exception, and the {@link SafeFetch} variants translate it to {@code Either.left}.
 *
 * <p>Use {@link Guards} for the standard guards ({@link Guards#maxKeysPerRound}, {@link
 * Guards#maxRounds}, {@link Guards#maxBackendCalls}, {@link Guards#audit}) and the guarded runners.
 * Compose guards with {@link #and(Guard)}.
 *
 * <p>A guard is invoked with the uncached keyset for the round (the keys that would actually
 * dispatch), so a {@link Guards#maxKeysPerRound} budget reflects the work the guard could prevent.
 * The round index starts at zero and increments per resolved {@link Blocked} round.
 *
 * @param <K> the request key type
 */
@FunctionalInterface
public interface Guard<K> {

  /**
   * Inspect a round's pending keyset. Return normally to allow the round; throw {@link
   * GuardViolationException} to refuse it.
   *
   * @param pendingKeys the uncached keys this round would dispatch (never null, possibly empty)
   * @param roundIndex zero-based round counter
   * @param backendCallsSoFar number of non-empty rounds that have already dispatched (i.e.
   *     completed backend calls before this round). Lets guards enforce cross-round budgets
   *     statelessly; the runner is the single source of truth for the count
   * @throws GuardViolationException to refuse the round
   */
  void check(Set<K> pendingKeys, int roundIndex, int backendCallsSoFar);

  /**
   * Compose two guards. The result calls {@code this} then {@code other}; the first to throw wins.
   */
  default Guard<K> and(Guard<K> other) {
    Objects.requireNonNull(other, "other");
    return (keys, round, calls) -> {
      check(keys, round, calls);
      other.check(keys, round, calls);
    };
  }
}
