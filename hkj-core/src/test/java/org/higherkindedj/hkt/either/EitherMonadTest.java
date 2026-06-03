// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("EitherMonad")
class EitherMonadTest extends EitherTestBase {

  private MonadError<EitherKind.Witness<String>, String> monad;

  @BeforeEach
  void setUp() {
    monad = Instances.monadError(either());
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.either.EitherLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.either.EitherLawFixtures#kinds")
    void rightIdentity(String label, Kind<EitherKind.Witness<String>, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.either.EitherLawFixtures#kinds")
    void associativity(String label, Kind<EitherKind.Witness<String>, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  @Test
  @DisplayName("Monad contract — operations, validations & exceptions (laws verified above)")
  void monadContract() {
    TypeClassContract.<EitherKind.Witness<String>>monad(EitherMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("flatMap operations")
  class FlatMapOperations {

    @Test
    void flatMapOnRightAppliesFunction() {
      var result = monad.flatMap(validFlatMapper, validKind);
      assertThatEither(result).isRight().hasRight("flat:42");
    }

    @Test
    void flatMapOnRightCanReturnLeft() {
      var result =
          monad.flatMap(
              _ -> EITHER.widen(Either.<String, String>left(TestErrorType.VALIDATION.message())),
              validKind);
      assertThatEither(result).isLeft().hasLeft(TestErrorType.VALIDATION.message());
    }

    @Test
    void flatMapOnLeftPassesThrough() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.ERROR_1);
      var result = monad.flatMap(validFlatMapper, leftKind);
      assertThatEither(result).isLeft().hasLeft(TestErrorType.ERROR_1.message());
    }
  }

  @Nested
  @DisplayName("of and ap")
  class OfAndApOperations {

    @Test
    void ofCreatesRightInstance() {
      var result = monad.of("success");
      assertThatEither(result).isRight().hasRight("success");
    }

    @Test
    void apAppliesFunctionWhenBothRight() {
      var funcKind = monad.<Function<Integer, String>>of(i -> "value:" + i);
      var valueKind = monad.of(DEFAULT_RIGHT_VALUE);
      var result = monad.ap(funcKind, valueKind);
      assertThatEither(result).isRight().hasRight("value:42");
    }

    @Test
    void apPropagatesLeftFromFunction() {
      Kind<EitherKind.Witness<String>, Function<Integer, String>> funcKind =
          leftKind(TestErrorType.FUNCTION_ERROR);
      var valueKind = monad.of(DEFAULT_RIGHT_VALUE);
      var result = monad.ap(funcKind, valueKind);
      assertThatEither(result).isLeft().hasLeft(TestErrorType.FUNCTION_ERROR.message());
    }

    @Test
    void map2CombinesTwoRightValues() {
      var r1 = monad.of(10);
      var r2 = monad.of("test");
      var result = monad.map2(r1, r2, (i, s) -> s + ":" + i);
      assertThatEither(result).isRight().hasRight("test:10");
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    void deepFlatMapChaining() {
      Kind<EitherKind.Witness<String>, Integer> result = rightKind(1);
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }
      assertThatEither(result).isRight().hasRight(46);
    }

    @Test
    void flatMapWithEarlyLeftShortCircuits() {
      Kind<EitherKind.Witness<String>, Integer> result = rightKind(1);
      for (int i = 0; i < 10; i++) {
        final int index = i;
        result =
            monad.flatMap(
                x -> index == 5 ? leftKind(TestErrorType.VALIDATION) : monad.of(x + index), result);
      }
      assertThatEither(result).isLeft().hasLeft(TestErrorType.VALIDATION.message());
    }
  }
}
