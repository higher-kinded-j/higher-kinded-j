// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial05 ProgramInspection — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial05_ProgramInspection_Solution {

  private static final Functor<IdKind.Witness> ID_FUNCTOR =
      new Functor<>() {
        @Override
        public <A, B> Kind<IdKind.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<IdKind.Witness, A> fa) {
          return IdMonad.instance().map(f, fa);
        }
      };

  /**
   * Why this is idiomatic: {@code ProgramAnalyser.analyse} reports counts the program actually
   * contains. A single suspended instruction has {@code suspendCount = 1} and {@code
   * totalInstructions = 1}; the analysis is exact.
   *
   * <p>Alternative: hand-instrument the interpreter to bump counters as it visits each step. Same
   * answer; the analyser does it without an interpreter at all.
   *
   * <p>Common wrong attempt: count {@code Free.pure} as a suspended instruction. Pure values do not
   * suspend; only {@code Free.liftF} adds to the suspend count.
   */
  @Test
  void exercise1_countInstructions() {
    Free<IdKind.Witness, String> program = Free.liftF(new Id<>("hello"), ID_FUNCTOR);
    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.suspendCount()).isEqualTo(1);
    assertThat(analysis.totalInstructions()).isEqualTo(1);
  }

  /**
   * Why this is idiomatic: a {@code flatMap} introduces an opaque region — the analyser can see
   * "here be dragons" but not what is inside the lambda. The flag is honest about the limit of
   * static analysis.
   *
   * <p>Alternative: rewrite the program with applicative {@code map}/{@code map2} when the steps do
   * not depend on each other. The analyser then sees the structure clearly.
   *
   * <p>Common wrong attempt: assume opaque means "broken". Many real programs have {@code flatMap}
   * chains; the flag exists so callers know to add fallbacks for the unanalysable region rather
   * than rely on static guarantees.
   */
  @Test
  void exercise2_detectOpaqueRegions() {
    Free<IdKind.Witness, String> program =
        Free.liftF(new Id<>("a"), ID_FUNCTOR).flatMap(s -> Free.pure(s + "b"));

    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.hasOpaqueRegions()).isTrue();
    assertThat(analysis.flatMapDepth()).isGreaterThan(0);
  }

  /**
   * Why this is idiomatic: a {@code Free.Ap} wraps a {@code FreeAp} that records a parallel scope.
   * The analyser tallies them so a runtime planner can decide which scopes to spread across virtual
   * threads.
   *
   * <p>Alternative: dispatch parallelism manually with explicit task scheduling. Loses the
   * program-as-data property; the analyser can no longer see the boundary.
   *
   * <p>Common wrong attempt: assume {@code parallelScopes} measures actual parallelism. It counts
   * opportunities; the interpreter decides whether to exploit them.
   */
  @Test
  void exercise3_countParallelScopes() {
    Free<IdKind.Witness, String> program = new Free.Ap<>(FreeAp.pure("parallel"));

    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.parallelScopes()).isEqualTo(1);
  }
}
