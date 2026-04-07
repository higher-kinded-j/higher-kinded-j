// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/** Solutions for Tutorial 03: Error Recovery. */
public class Tutorial03_ErrorRecovery_Solution {

  private static final Natural<IdKind.Witness, IdKind.Witness> IDENTITY =
      new Natural<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return fa;
        }
      };

  @Test
  void exercise1_basicRecovery() {
    Free<IdKind.Witness, String> program = Free.pure("success");
    Free<IdKind.Witness, String> safe =
        program.handleError(Throwable.class, _ -> Free.pure("recovered"));

    Id<String> result = IdKindHelper.ID.narrow(safe.foldMap(IDENTITY, IdMonad.instance()));

    assertThat(result.value()).isEqualTo("success");
  }

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

  @Test
  void exercise3_recoveryWithMap() {
    Free<IdKind.Witness, String> program = Free.pure("hello");
    Free<IdKind.Witness, String> result =
        program.handleError(Throwable.class, _ -> Free.pure("recovered")).map(String::toUpperCase);

    Id<String> interpreted = IdKindHelper.ID.narrow(result.foldMap(IDENTITY, IdMonad.instance()));

    assertThat(interpreted.value()).isEqualTo("HELLO");
  }
}
