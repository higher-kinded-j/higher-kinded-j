// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.boundary;

import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Analysis of a Free monad program's structure without executing it.
 *
 * <p>Produced by {@link TestBoundary#analyse(org.higherkindedj.hkt.free.Free)}. Walks the Free
 * structure counting effect invocations, error recovery nodes, and applicative blocks.
 *
 * @param effectsUsed the set of effect operation class names invoked by the program
 * @param totalInstructions total number of effect instructions (Suspend nodes)
 * @param recoveryPoints number of HandleError nodes (error recovery points)
 * @param applicativeBlocks number of Ap nodes (applicative parallel blocks)
 */
@NullMarked
public record ProgramAnalysis(
    Set<String> effectsUsed, int totalInstructions, int recoveryPoints, int applicativeBlocks) {

  /** Creates a ProgramAnalysis with defensive copies. */
  public ProgramAnalysis {
    effectsUsed = Set.copyOf(effectsUsed);
  }

  /** Creates an empty analysis. */
  public static ProgramAnalysis empty() {
    return new ProgramAnalysis(new HashSet<>(), 0, 0, 0);
  }
}
