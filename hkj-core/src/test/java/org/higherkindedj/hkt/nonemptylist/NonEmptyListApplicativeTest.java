// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("NonEmptyListApplicative")
class NonEmptyListApplicativeTest extends NonEmptyListTestBase {

  private Applicative<NonEmptyListKind.Witness> applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = Instances.monad(nonEmptyList());
    validateApplicativeFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.nonemptylist.NonEmptyListLawFixtures#kinds")
    void identity(String label, Kind<NonEmptyListKind.Witness, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.nonemptylist.NonEmptyListLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.nonemptylist.NonEmptyListLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.nonemptylist.NonEmptyListLawFixtures#kinds")
    void composition(String label, Kind<NonEmptyListKind.Witness, Integer> w) {
      Kind<NonEmptyListKind.Witness, Function<String, String>> u =
          NON_EMPTY_LIST.widen(NonEmptyList.of(s -> "u(" + s + ")", s -> s + "!"));
      Kind<NonEmptyListKind.Witness, Function<Integer, String>> v =
          NON_EMPTY_LIST.widen(NonEmptyList.of(i -> "v" + i, i -> "w" + i));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }
  }
}
