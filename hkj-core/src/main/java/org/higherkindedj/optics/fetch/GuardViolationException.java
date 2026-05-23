// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import java.util.Collections;
import java.util.Set;

/**
 * Thrown by a {@link Guard} to refuse a round before it dispatches.
 *
 * <p>The exception carries the zero-based {@code roundIndex} and the {@code pendingKeys} the round
 * was about to send so callers can log the rejection and the offending workload. The {@code reason}
 * string is short and human-readable (e.g. {@code "keysPerRound=550 exceeds budget=500"}).
 */
public final class GuardViolationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int roundIndex;
  private final Set<?> pendingKeys;

  /**
   * @param reason human-readable summary of the violation
   * @param roundIndex the round that triggered the guard
   * @param pendingKeys the keys the round was about to dispatch
   */
  public GuardViolationException(String reason, int roundIndex, Set<?> pendingKeys) {
    super(reason + " at round " + roundIndex);
    this.roundIndex = roundIndex;
    this.pendingKeys = Collections.unmodifiableSet(pendingKeys);
  }

  public int roundIndex() {
    return roundIndex;
  }

  public Set<?> pendingKeys() {
    return pendingKeys;
  }
}
