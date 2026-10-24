// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.higherkindedj.hkt.instances.Witnesses.trampoline;
import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("TrampolineApplicative")
class TrampolineApplicativeTest extends TrampolineTestBase {

  private final Applicative<TrampolineKind.Witness> applicative = Instances.monad(trampoline());

  private final Function<Integer, String> mapper = i -> "v" + i;

  private final Kind<TrampolineKind.Witness, Function<Integer, String>> validFunctionKind =
      TRAMPOLINE.widen(Trampoline.done(mapper));

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#kinds")
    void identity(String label, Kind<TrampolineKind.Witness, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, TrampolineLawFixtures.EQ);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, mapper, TrampolineLawFixtures.EQ);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(
          applicative, validFunctionKind, value, TrampolineLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#kinds")
    void composition(String label, Kind<TrampolineKind.Witness, Integer> w) {
      Kind<TrampolineKind.Witness, Function<String, String>> u =
          TRAMPOLINE.widen(Trampoline.done(s -> "u(" + s + ")"));

      Kind<TrampolineKind.Witness, Function<Integer, String>> v =
          TRAMPOLINE.widen(Trampoline.done(mapper));

      ApplicativeLaws.assertComposition(applicative, u, v, w, TrampolineLawFixtures.EQ);
    }
  }
}
