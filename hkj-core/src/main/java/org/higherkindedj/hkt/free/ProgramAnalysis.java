// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;

import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Static analysis results for a Free monad program.
 *
 * <p>Produced by {@link ProgramAnalyser#analyse(Free)} via structural traversal of the program
 * tree. The analysis covers the visible spine of the program — operations inside {@link
 * Free.FlatMapped} continuations are opaque and cannot be inspected without execution.
 *
 * <p>All counts are therefore <b>lower bounds</b>, not exact totals. The {@code hasOpaqueRegions}
 * flag indicates whether the program contains {@code FlatMapped} continuations that could not be
 * traversed.
 *
 * @param suspendCount the number of {@link Free.Suspend} nodes (individual instructions)
 * @param recoveryPoints the number of {@link Free.HandleError} nodes
 * @param parallelScopes the number of {@link Free.Ap} nodes (applicative sub-trees)
 * @param flatMapDepth the number of {@link Free.FlatMapped} nodes
 * @param hasOpaqueRegions whether the program contains FlatMapped continuations that could not be
 *     analysed
 */
@NullMarked
public record ProgramAnalysis(
    int suspendCount,
    int recoveryPoints,
    int parallelScopes,
    int flatMapDepth,
    boolean hasOpaqueRegions) {

  /** An empty analysis (identity for combining). */
  public static final ProgramAnalysis EMPTY = new ProgramAnalysis(0, 0, 0, 0, false);

  /**
   * Combines this analysis with another by summing counts and merging flags.
   *
   * @param other the other analysis to combine with
   * @return a combined ProgramAnalysis
   */
  public ProgramAnalysis combine(ProgramAnalysis other) {
    Validation.function().require(other, "other", CONSTRUCTION);
    return new ProgramAnalysis(
        suspendCount + other.suspendCount,
        recoveryPoints + other.recoveryPoints,
        parallelScopes + other.parallelScopes,
        flatMapDepth + other.flatMapDepth,
        hasOpaqueRegions || other.hasOpaqueRegions);
  }

  /**
   * Total number of analysable instructions (Suspend + Ap nodes).
   *
   * @return the total instruction count (lower bound)
   */
  public int totalInstructions() {
    return suspendCount + parallelScopes;
  }

  @Override
  public String toString() {
    return String.format(
        "ProgramAnalysis[%d suspend, %d recovery, %d parallel, %d flatMap%s]",
        suspendCount,
        recoveryPoints,
        parallelScopes,
        flatMapDepth,
        hasOpaqueRegions ? ", opaque regions present" : "");
  }
}
