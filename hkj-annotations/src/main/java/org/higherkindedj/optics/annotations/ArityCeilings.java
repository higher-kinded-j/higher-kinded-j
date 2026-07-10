// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

/**
 * The single source of truth for the code-generation arity ceilings, referenced by both the
 * {@code @Generate*} annotation usages and the annotation processors so the two can never drift.
 *
 * <p>The two ceilings are deliberately independent, which is what lets the accumulating-assembly
 * ladder go wider than the For-comprehension builder:
 *
 * <ul>
 *   <li>{@link #FOR_COMPREHENSION} bounds {@code @GenerateForComprehensions}: the For-comprehension
 *       step families and the shared {@code TupleN} records are generated for arities {@code
 *       2..FOR_COMPREHENSION}.
 *   <li>{@link #ASSEMBLY} bounds {@code @GenerateAccumulators} (and, built on it,
 *       {@code @GenerateMapping} / {@code @GenerateMerge}): the staged {@code accumulate()}/{@code
 *       fields()} ladder is generated for arities {@code 1..ASSEMBLY}. Because the ladder
 *       accumulates into a {@code TupleN} and applies a {@code FunctionN}, the accumulator
 *       processor fills the {@code TupleN} gap from {@code FOR_COMPREHENSION + 1} to {@code
 *       ASSEMBLY} itself (the hand-written {@code FunctionN} family already reaches {@code
 *       ASSEMBLY}).
 * </ul>
 *
 * <p>Invariant: {@code ASSEMBLY >= FOR_COMPREHENSION}. Raising {@link #FOR_COMPREHENSION} past
 * {@link #ASSEMBLY}, or lowering {@link #ASSEMBLY} below {@link #FOR_COMPREHENSION}, would make the
 * two processors emit overlapping or missing {@code TupleN} classes; both fail loudly at build
 * time. The ceiling ultimately stops at 26 (the {@code A..Z} type-parameter-letter limit).
 *
 * <p>A non-instantiable holder for these constants.
 */
public final class ArityCeilings {

  private ArityCeilings() {}

  /**
   * The arity up to which {@code @GenerateForComprehensions} emits its step families and the shared
   * {@code TupleN} records.
   */
  public static final int FOR_COMPREHENSION = 12;

  /**
   * The arity up to which the {@code accumulate()}/{@code fields()} assembly ladder (and the
   * mappers built on it) is generated. Must be {@code >= }{@link #FOR_COMPREHENSION}.
   */
  public static final int ASSEMBLY = 16;
}
