// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import java.util.List;
import java.util.Set;

/**
 * The structural shape of a {@link Fetch} program, as observed by {@link Plans#preflight} without
 * any backend I/O.
 *
 * <p>Each entry of {@link #fetchedBatches} is one round's keyset, in order. For a pure applicative
 * program every round is fully observable and {@link #truncated} is {@code false}. A {@code
 * flatMap} data dependency makes later rounds value-dependent: the preflight walks as far as it can
 * on stub values and sets {@code truncated = true} when it hits a round whose continuation inspects
 * a value to decide the next dispatch.
 *
 * @param fetchedBatches one keyset per round, in dispatch order
 * @param totalKeyCount sum of {@code fetchedBatches[i].size()} across all observed rounds
 * @param truncated {@code true} if a {@code flatMap} dependency stopped further inspection; later
 *     rounds may exist but cannot be observed without running
 * @param <K> the request key type
 */
public record Plan<K>(List<Set<K>> fetchedBatches, int totalKeyCount, boolean truncated) {

  public Plan {
    fetchedBatches = List.copyOf(fetchedBatches);
  }

  /** Number of rounds observed; equals {@code fetchedBatches().size()}. */
  public int rounds() {
    return fetchedBatches.size();
  }

  /** An empty plan: a program that resolves with no rounds (a {@code Done}). */
  public static <K> Plan<K> empty() {
    return new Plan<>(List.of(), 0, false);
  }
}
