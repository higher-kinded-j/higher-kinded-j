// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("EitherOrBothApplicative")
class EitherOrBothApplicativeTest extends EitherOrBothTestBase {

  private Applicative<EitherOrBothKind.Witness<String>> applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = Instances.eitherOrBoth(Semigroups.string());
    validateApplicativeFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#kinds")
    void identity(String label, Kind<EitherOrBothKind.Witness<String>, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#kinds")
    void composition(String label, Kind<EitherOrBothKind.Witness<String>, Integer> w) {
      Kind<EitherOrBothKind.Witness<String>, Function<String, String>> u =
          EITHER_OR_BOTH.widen(EitherOrBoth.right(s -> "u(" + s + ")"));
      Kind<EitherOrBothKind.Witness<String>, Function<Integer, String>> v =
          EITHER_OR_BOTH.widen(EitherOrBoth.right(i -> "v" + i));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }
  }
}
