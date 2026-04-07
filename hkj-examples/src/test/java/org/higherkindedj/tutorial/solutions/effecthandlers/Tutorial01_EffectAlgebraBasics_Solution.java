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

/** Solutions for Tutorial 01: Effect Algebra Basics. */
public class Tutorial01_EffectAlgebraBasics_Solution {

  private static final Natural<IdKind.Witness, IdKind.Witness> IDENTITY_INTERP =
      new Natural<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return fa;
        }
      };

  @Test
  void exercise1_pureProgram() {
    Free<IdKind.Witness, String> program = Free.pure("hello");

    Id<String> result =
        IdKindHelper.ID.narrow(program.foldMap(IDENTITY_INTERP, IdMonad.instance()));

    assertThat(result.value()).isEqualTo("hello");
  }

  @Test
  void exercise2_flatMapChaining() {
    Free<IdKind.Witness, Integer> first = Free.pure(10);
    Free<IdKind.Witness, Integer> chained = first.flatMap(n -> Free.pure(n + 5));

    Id<Integer> result =
        IdKindHelper.ID.narrow(chained.foldMap(IDENTITY_INTERP, IdMonad.instance()));

    assertThat(result.value()).isEqualTo(15);
  }

  @Test
  void exercise3_mappingValues() {
    Free<IdKind.Witness, String> greeting = Free.pure("hello");
    Free<IdKind.Witness, String> upperGreeting = greeting.map(String::toUpperCase);

    Id<String> result =
        IdKindHelper.ID.narrow(upperGreeting.foldMap(IDENTITY_INTERP, IdMonad.instance()));

    assertThat(result.value()).isEqualTo("HELLO");
  }

  @Test
  void exercise4_programAnalysis() {
    Free<IdKind.Witness, String> program = Free.pure("hello");
    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.suspendCount()).isEqualTo(0);
    assertThat(analysis.recoveryPoints()).isEqualTo(0);
    assertThat(analysis.hasOpaqueRegions()).isFalse();
  }

  @Test
  void exercise5_errorRecoveryAnalysis() {
    Free<IdKind.Witness, String> risky = Free.pure("data");
    Free<IdKind.Witness, String> safe =
        risky.handleError(Throwable.class, _ -> Free.pure("recovered"));

    ProgramAnalysis analysis = ProgramAnalyser.analyse(safe);

    assertThat(analysis.recoveryPoints()).isEqualTo(1);
  }
}
