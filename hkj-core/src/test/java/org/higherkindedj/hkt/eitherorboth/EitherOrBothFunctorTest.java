// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("EitherOrBothFunctor")
class EitherOrBothFunctorTest extends EitherOrBothTestBase {

  private EitherOrBothFunctor<String> functor;

  @BeforeEach
  void setUp() {
    functor = EitherOrBothFunctor.instance();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#kinds")
    void identity(String label, Kind<EitherOrBothKind.Witness<String>, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#kinds")
    void composition(String label, Kind<EitherOrBothKind.Witness<String>, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  @Test
  @DisplayName("Functor contract — operations, validations & exceptions (laws verified above)")
  void functorContract() {
    TypeClassContract.<EitherOrBothKind.Witness<String>>functor(EitherOrBothFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operations")
  class Operations {

    @Test
    void mapOnRightAppliesFunction() {
      assertThat(EITHER_OR_BOTH.narrow(functor.map(validMapper, rightK(42))))
          .isEqualTo(EitherOrBoth.right("42"));
    }

    @Test
    void mapOnLeftPassesThrough() {
      assertThat(EITHER_OR_BOTH.narrow(functor.map(validMapper, leftK("err"))))
          .isEqualTo(EitherOrBoth.left("err"));
    }

    @Test
    void mapOnBothKeepsWarnings() {
      assertThat(EITHER_OR_BOTH.narrow(functor.map(validMapper, bothK("w", 42))))
          .isEqualTo(EitherOrBoth.both("w", "42"));
    }
  }
}
