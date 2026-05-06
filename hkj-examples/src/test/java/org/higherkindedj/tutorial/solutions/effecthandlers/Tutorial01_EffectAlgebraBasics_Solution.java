// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial01 EffectAlgebraBasics — teaching-solution format.
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
public class Tutorial01_EffectAlgebraBasics_Solution {

  private static final Natural<IdKind.Witness, IdKind.Witness> IDENTITY_INTERP =
      new Natural<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return fa;
        }
      };

  /**
   * Why this is idiomatic: {@code Free.pure(value)} is the no-op program — a value lifted into the
   * Free monad without any operation. {@code foldMap} with the identity interpreter unwraps it to
   * the value.
   *
   * <p>Alternative: skip Free for pure values. Pointless once the value is meant to be combined
   * with effectful steps; the {@code pure} constructor is the start of every Free pipeline.
   *
   * <p>Common wrong attempt: try to {@code foldMap} without supplying both the natural
   * transformation and the target monad. Both are mandatory — the transformation maps algebra ops,
   * the monad combines results.
   */
  @Test
  void exercise1_pureProgram() {
    Free<IdKind.Witness, String> program = Free.pure("hello");

    Id<String> result =
        IdKindHelper.ID.narrow(program.foldMap(IDENTITY_INTERP, IdMonad.instance()));

    assertThat(result.value()).isEqualTo("hello");
  }

  /**
   * Why this is idiomatic: {@code flatMap} on a {@code Free} threads the previous result into the
   * next step. The interpreter sees one chained program; the value flows through the lambda the
   * same way it would in a {@code Stream} pipeline.
   *
   * <p>Alternative: compute the value eagerly outside the Free. Loses the program-as-data shape;
   * the audit and dry-run interpreters cannot inspect a closed-over computation.
   *
   * <p>Common wrong attempt: nest {@code Free.pure(...)} calls without {@code flatMap}. The result
   * is two independent programs; chain them so the dependency is explicit.
   */
  @Test
  void exercise2_flatMapChaining() {
    Free<IdKind.Witness, Integer> first = Free.pure(10);
    Free<IdKind.Witness, Integer> chained = first.flatMap(n -> Free.pure(n + 5));

    Id<Integer> result =
        IdKindHelper.ID.narrow(chained.foldMap(IDENTITY_INTERP, IdMonad.instance()));

    assertThat(result.value()).isEqualTo(15);
  }

  /**
   * Why this is idiomatic: {@code map} composes a pure transformation into the program without
   * interpreting it. The function is recorded in the AST and applied during the fold.
   *
   * <p>Alternative: pre-compute the upper-cased value and {@code Free.pure(...)} that. Equivalent;
   * pointless work that stays symbolic with {@code map}.
   *
   * <p>Common wrong attempt: side-effect inside the {@code map} function. {@code Free} is meant to
   * be inspectable; effects belong in the interpreter, not the ASTs leaves.
   */
  @Test
  void exercise3_mappingValues() {
    Free<IdKind.Witness, String> greeting = Free.pure("hello");
    Free<IdKind.Witness, String> upperGreeting = greeting.map(String::toUpperCase);

    Id<String> result =
        IdKindHelper.ID.narrow(upperGreeting.foldMap(IDENTITY_INTERP, IdMonad.instance()));

    assertThat(result.value()).isEqualTo("HELLO");
  }

  /**
   * Why this is idiomatic: {@code ProgramAnalyser.analyse(program)} walks the AST and reports
   * counts — suspends, recoveries, opaque regions. A pure program has zero of each; an effectful
   * one has positive counts.
   *
   * <p>Alternative: instrument an interpreter to count steps as it runs. Same answer; the static
   * analyser does it without any execution.
   *
   * <p>Common wrong attempt: assume the analysis can short-circuit interpretation. It runs over the
   * program tree only; the values it returns are descriptive, not actionable in place of {@code
   * foldMap}.
   */
  @Test
  void exercise4_programAnalysis() {
    Free<IdKind.Witness, String> program = Free.pure("hello");
    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.suspendCount()).isEqualTo(0);
    assertThat(analysis.recoveryPoints()).isEqualTo(0);
    assertThat(analysis.hasOpaqueRegions()).isFalse();
  }

  /**
   * Why this is idiomatic: {@code program.handleError(class, recovery)} attaches a recovery point
   * to the AST. The analyser counts these so a code review can confirm "yes, every dangerous step
   * has a fallback".
   *
   * <p>Alternative: wrap the whole program in {@code try/catch} at the boundary. Works locally;
   * loses the per-step granularity the analyser provides.
   *
   * <p>Common wrong attempt: rely on Java's checked exceptions for error tracking. The Free
   * analyser tracks recovery within the algebra — a different layer, but the one the program
   * actually traverses.
   */
  @Test
  void exercise5_errorRecoveryAnalysis() {
    Free<IdKind.Witness, String> risky = Free.pure("data");
    Free<IdKind.Witness, String> safe =
        risky.handleError(Throwable.class, _ -> Free.pure("recovered"));

    ProgramAnalysis analysis = ProgramAnalyser.analyse(safe);

    assertThat(analysis.recoveryPoints()).isEqualTo(1);
  }
}
