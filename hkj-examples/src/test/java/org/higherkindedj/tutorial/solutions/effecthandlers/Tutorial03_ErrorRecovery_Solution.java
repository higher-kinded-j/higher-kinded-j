// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial03 ErrorRecovery — teaching-solution format.
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
public class Tutorial03_ErrorRecovery_Solution {

  private static final Natural<IdKind.Witness, IdKind.Witness> IDENTITY =
      new Natural<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return fa;
        }
      };

  /**
   * Why this is idiomatic: {@code program.handleError(class, recoveryFn)} attaches a recovery
   * branch. The success path runs first; the recovery only fires if the algebra raises a matching
   * error.
   *
   * <p>Alternative: wrap the interpreter in a {@code try/catch}. Loses the per-step granularity —
   * every error in the program gets the same handler.
   *
   * <p>Common wrong attempt: ignore the error class and catch {@code Throwable} for everything.
   * Specific exception classes mean recovery scopes can stay narrow; broaden only when needed.
   */
  @Test
  void exercise1_basicRecovery() {
    Free<IdKind.Witness, String> program = Free.pure("success");
    Free<IdKind.Witness, String> safe =
        program.handleError(Throwable.class, _ -> Free.pure("recovered"));

    Id<String> result = IdKindHelper.ID.narrow(safe.foldMap(IDENTITY, Instances.monad(id())));

    assertThat(result.value()).isEqualTo("success");
  }

  /**
   * Why this is idiomatic: chain two {@code handleError} calls and the inner recovery fires first;
   * the outer is the safety net. The analyser counts both — useful evidence for review.
   *
   * <p>Alternative: a single recovery with a richer fallback. Equivalent for two-level cases;
   * nested handlers stay readable when the recoveries genuinely differ.
   *
   * <p>Common wrong attempt: assume nested recoveries collapse into one. Each handler is its own
   * recovery point in the analysis; treat them as composed layers.
   */
  @Test
  void exercise2_nestedRecovery() {
    Free<IdKind.Witness, String> program = Free.pure("data");
    Free<IdKind.Witness, String> doubleRecovery =
        program
            .handleError(Throwable.class, _ -> Free.pure("inner"))
            .handleError(Throwable.class, _ -> Free.pure("outer"));

    var analysis = ProgramAnalyser.analyse(doubleRecovery);
    assertThat(analysis.recoveryPoints()).isEqualTo(2);
  }

  /**
   * Why this is idiomatic: recovery returns a regular {@code Free}, so it composes with {@code map}
   * like any other step. The map runs whether the original succeeded or the recovery fired.
   *
   * <p>Alternative: chain the recovery after the map. Different semantics — the map runs first, may
   * itself fail, and the recovery sees that failure.
   *
   * <p>Common wrong attempt: assume {@code map} skips when recovery fired. The recovery produced a
   * value; the map applies to it just like to a regular success.
   */
  @Test
  void exercise3_recoveryWithMap() {
    Free<IdKind.Witness, String> program = Free.pure("hello");
    Free<IdKind.Witness, String> result =
        program.handleError(Throwable.class, _ -> Free.pure("recovered")).map(String::toUpperCase);

    Id<String> interpreted =
        IdKindHelper.ID.narrow(result.foldMap(IDENTITY, Instances.monad(id())));

    assertThat(interpreted.value()).isEqualTo("HELLO");
  }
}
