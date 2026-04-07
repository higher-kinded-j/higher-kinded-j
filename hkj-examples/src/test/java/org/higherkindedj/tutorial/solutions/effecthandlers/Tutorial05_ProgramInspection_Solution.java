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

/** Solutions for Tutorial 05: Program Inspection. */
public class Tutorial05_ProgramInspection_Solution {

  private static final Functor<IdKind.Witness> ID_FUNCTOR =
      new Functor<>() {
        @Override
        public <A, B> Kind<IdKind.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<IdKind.Witness, A> fa) {
          return IdMonad.instance().map(f, fa);
        }
      };

  @Test
  void exercise1_countInstructions() {
    Free<IdKind.Witness, String> program = Free.liftF(new Id<>("hello"), ID_FUNCTOR);
    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.suspendCount()).isEqualTo(1);
    assertThat(analysis.totalInstructions()).isEqualTo(1);
  }

  @Test
  void exercise2_detectOpaqueRegions() {
    Free<IdKind.Witness, String> program =
        Free.liftF(new Id<>("a"), ID_FUNCTOR).flatMap(s -> Free.pure(s + "b"));

    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.hasOpaqueRegions()).isTrue();
    assertThat(analysis.flatMapDepth()).isGreaterThan(0);
  }

  @Test
  void exercise3_countParallelScopes() {
    Free<IdKind.Witness, String> program = new Free.Ap<>(FreeAp.pure("parallel"));

    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.parallelScopes()).isEqualTo(1);
  }
}
