// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial04 CombiningEffects — teaching-solution format.
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
public class Tutorial04_CombiningEffects_Solution {

  private static final Natural<IdKind.Witness, IdKind.Witness> ID_INTERP =
      new Natural<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return fa;
        }
      };

  /**
   * Why this is idiomatic: {@code Interpreters.combine(a, b)} merges two interpreters into one.
   * Programs that use either algebra interpret through the combined handler — no routing code in
   * the middle.
   *
   * <p>Alternative: pattern-match the program against its algebra and dispatch by hand. Tedious;
   * the combinator is the named version.
   *
   * <p>Common wrong attempt: pass interpreters that target the same algebra. The combinator is for
   * distinct algebras; use a single composite for one algebra.
   */
  @Test
  void exercise1_combineTwo() {
    var combined = Interpreters.combine(ID_INTERP, ID_INTERP);

    assertThat(combined).isNotNull();
  }

  /**
   * Why this is idiomatic: the three-arg overload extends combine to a third algebra. The shape
   * generalises to as many algebras as the program needs.
   *
   * <p>Alternative: nest two-arg {@code combine} calls. Equivalent; the named overload is tidier
   * when the count is known up-front.
   *
   * <p>Common wrong attempt: rely on a single mega-interpreter that handles every algebra. Each
   * algebra deserves its own handler so the test mocks stay focused.
   */
  @Test
  void exercise2_combineThree() {
    var combined = Interpreters.combine(ID_INTERP, ID_INTERP, ID_INTERP);

    assertThat(combined).isNotNull();
  }

  /**
   * Why this is idiomatic: a program over the combined algebra interprets through the combined
   * handler — the result drops back to the target monad. The Free encoding keeps the algebra
   * coproduct visible until the fold.
   *
   * <p>Alternative: handle each algebra in its own pass, threading the partial results through.
   * Same answer; the combined interpreter does it in one fold.
   *
   * <p>Common wrong attempt: try to interpret with only one of the original interpreters. The
   * program references both algebras; missing handlers produce a compile error.
   */
  @Test
  void exercise3_interpretComposed() {
    var combined = Interpreters.combine(ID_INTERP, ID_INTERP);
    Free<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>, String> program =
        Free.pure("combined");

    Id<String> result = IdKindHelper.ID.narrow(program.foldMap(combined, IdMonad.instance()));

    assertThat(result.value()).isEqualTo("combined");
  }
}
