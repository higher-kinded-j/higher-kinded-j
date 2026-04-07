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

/** Solutions for Tutorial 04: Combining Effects. */
public class Tutorial04_CombiningEffects_Solution {

  private static final Natural<IdKind.Witness, IdKind.Witness> ID_INTERP =
      new Natural<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return fa;
        }
      };

  @Test
  void exercise1_combineTwo() {
    var combined = Interpreters.combine(ID_INTERP, ID_INTERP);

    assertThat(combined).isNotNull();
  }

  @Test
  void exercise2_combineThree() {
    var combined = Interpreters.combine(ID_INTERP, ID_INTERP, ID_INTERP);

    assertThat(combined).isNotNull();
  }

  @Test
  void exercise3_interpretComposed() {
    var combined = Interpreters.combine(ID_INTERP, ID_INTERP);
    Free<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>, String> program =
        Free.pure("combined");

    Id<String> result = IdKindHelper.ID.narrow(program.foldMap(combined, IdMonad.instance()));

    assertThat(result.value()).isEqualTo("combined");
  }
}
