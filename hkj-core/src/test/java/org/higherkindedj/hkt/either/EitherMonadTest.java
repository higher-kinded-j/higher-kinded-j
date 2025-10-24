// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad Complete Test Suite")
class EitherMonadTest extends EitherTestBase {

  private EitherMonad<String> monad;

  @BeforeEach
  void setUpMonad() {
    monad = EitherMonad.instance();
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .withFlatMapFrom(EitherMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(EitherMonadTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("flatMap() on Right applies function")
    void flatMapOnRightAppliesFunction() {
      var result = monad.flatMap(validFlatMapper, validKind);

      assertThatEither(narrowToEither(result)).isRight().hasRight("flat:42");
    }

    @Test
    @DisplayName("flatMap() on Right can return Left")
    void flatMapOnRightCanReturnLeft() {
      Function<Integer, Either<String, String>> errorMapper =
          i -> Either.left(TestErrorType.VALIDATION.message());

      var result = monad.flatMap(i -> EITHER.widen(errorMapper.apply(i)), validKind);

      assertThatEither(narrowToEither(result)).isLeft().hasLeft(TestErrorType.VALIDATION.message());
    }

    @Test
    @DisplayName("flatMap() on Left passes through unchanged")
    void flatMapOnLeftPassesThrough() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.ERROR_1);

      var result = monad.flatMap(validFlatMapper, leftKind);

      assertThatEither(narrowToEither(result)).isLeft().hasLeft(TestErrorType.ERROR_1.message());
    }

    @Test
    @DisplayName("of() creates Right instances")
    void ofCreatesRightInstances() {
      var result = monad.of("success");

      assertThatEither(narrowToEither(result)).isRight().hasRight("success");
    }

    @Test
    @DisplayName("ap() applies function to value - both Right")
    void apAppliesFunctionToValue() {
      Kind<EitherKind.Witness<String>, Function<Integer, String>> funcKind =
          monad.of(i -> "value:" + i);
      Kind<EitherKind.Witness<String>, Integer> valueKind = monad.of(DEFAULT_RIGHT_VALUE);

      var result = monad.ap(funcKind, valueKind);

      assertThatEither(narrowToEither(result)).isRight().hasRight("value:42");
    }

    @Test
    @DisplayName("ap() propagates Left from function")
    void apPropagatesLeftFromFunction() {
      Kind<EitherKind.Witness<String>, Function<Integer, String>> funcKind =
          leftKind(TestErrorType.FUNCTION_ERROR);
      Kind<EitherKind.Witness<String>, Integer> valueKind = monad.of(DEFAULT_RIGHT_VALUE);

      Kind<EitherKind.Witness<String>, String> result = monad.ap(funcKind, valueKind);

      assertThatEither(narrowToEither(result))
          .isLeft()
          .hasLeft(TestErrorType.FUNCTION_ERROR.message());
    }

    @Test
    @DisplayName("map2() combines two Right values")
    void map2CombinesTwoRightValues() {
      var r1 = monad.of(10);
      var r2 = monad.of("test");

      var result = monad.map2(r1, r2, (i, s) -> s + ":" + i);

      assertThatEither(narrowToEither(result)).isRight().hasRight("test:10");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .withFlatMapFrom(EitherMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      var start = rightKind(1);

      var result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      assertThatEither(narrowToEither(result))
          .isRight()
          .hasRight(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("flatMap with early Left short-circuits")
    void flatMapWithEarlyLeftShortCircuits() {
      var start = rightKind(1);

      var result = start;
      for (int i = 0; i < 10; i++) {
        final int index = i;
        result =
            monad.flatMap(
                x -> {
                  if (index == 5) {
                    return leftKind(TestErrorType.VALIDATION);
                  }
                  return monad.of(x + index);
                },
                result);
      }

      assertThatEither(narrowToEither(result)).isLeft().hasLeft(TestErrorType.VALIDATION.message());
    }

    @Test
    @DisplayName("Test with different error types")
    void testWithDifferentErrorTypes() {
      EitherMonad<ComplexTestError> complexMonad = EitherMonad.instance();
      var complexKind = EITHER.widen(Either.<ComplexTestError, Integer>right(100));

      Function<Integer, String> mapper = Object::toString;
      Function<Integer, Kind<EitherKind.Witness<ComplexTestError>, String>> flatMapper =
          i -> EITHER.widen(Either.right("flat:" + i));
      Kind<EitherKind.Witness<ComplexTestError>, Function<Integer, String>> functionKind =
          complexMonad.of(mapper);
      BiFunction<Integer, Integer, String> combiningFunction = (a, b) -> a + "," + b;

      TypeClassTest.<EitherKind.Witness<ComplexTestError>>monad(EitherMonad.class)
          .<Integer>instance(complexMonad)
          .<String>withKind(complexKind)
          .withMonadOperations(complexKind, mapper, flatMapper, functionKind, combiningFunction)
          .testOperations();
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("flatMap efficient with many operations")
    void flatMapEfficientWithManyOperations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        var start = rightKind(1);

        var result = start;
        for (int i = 0; i < 100; i++) {
          final int increment = i;
          result = monad.flatMap(x -> monad.of(x + increment), result);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        assertThatEither(narrowToEither(result)).isRight().hasRight(expectedSum);
      }
    }

    @Test
    @DisplayName("Left values don't process operations")
    void leftValuesDontProcessOperations() {
      Kind<EitherKind.Witness<String>, Integer> leftStart = leftKind(TestErrorType.DEFAULT);
      Either<String, Integer> originalLeft = narrowToEither(leftStart);

      var leftResult = leftStart;
      for (int i = 0; i < 1000; i++) {
        final int index = i;
        leftResult = monad.flatMap(s -> monad.of(s + index), leftResult);
      }

      Either<String, Integer> finalLeft = narrowToEither(leftResult);
      assertThatEither(finalLeft).isSameAs(originalLeft);
    }
  }
}
