// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
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

@DisplayName("EitherFunctor")
class EitherFunctorTest extends EitherTestBase {

  private EitherFunctor<String> functor;

  @BeforeEach
  void setUp() {
    functor = EitherFunctor.instance();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.either.EitherLawFixtures#kinds")
    void identity(String label, Kind<EitherKind.Witness<String>, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.either.EitherLawFixtures#kinds")
    void composition(String label, Kind<EitherKind.Witness<String>, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  @Test
  @DisplayName("Functor contract — operations, validations & exceptions (laws verified above)")
  void functorContract() {
    TypeClassContract.<EitherKind.Witness<String>>functor(EitherFunctor.class)
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
      var result = functor.map(validMapper, validKind);
      assertThatEither(result).isRight().hasRight("42");
    }

    @Test
    void mapOnLeftPassesThrough() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.ERROR_1);
      var result = functor.map(validMapper, leftKind);
      assertThatEither(result).isLeft().hasLeft(TestErrorType.ERROR_1.message());
    }

    @Test
    void mapWithNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNull = rightKind(null);
      var result = functor.map(String::valueOf, rightNull);
      assertThatEither(result).isRight().hasRight("null");
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    void mapWithComplexBranchingTransformation() {
      Function<Integer, String> complex =
          i -> {
            if (i < 0) return "negative";
            if (i == 0) return "zero";
            return "positive:" + i;
          };
      var result = functor.map(complex, validKind);
      assertThatEither(result).isRight().hasRight("positive:42");
    }

    @Test
    void worksWithDifferentErrorTypes() {
      EitherFunctor<ComplexTestError> complexFunctor = EitherFunctor.instance();
      var complexKind = EITHER.widen(Either.<ComplexTestError, Integer>right(100));
      var result = complexFunctor.map(validMapper, complexKind);
      assertThatEither(result).isRight().hasRight("100");
    }
  }
}
