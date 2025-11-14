// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad Applicative Operations Complete Test Suite")
class EitherApplicativeTest extends EitherTestBase {

  private EitherMonad<String> applicative;
  private Applicative<EitherKind.Witness<String>> applicativeTyped;

  @BeforeEach
  void setUpApplicative() {
    applicative = EitherMonad.instance();
    applicativeTyped = applicative;
    validateApplicativeFixtures();
  }

  @Nested
  @DisplayName("Complete Applicative Test Suite")
  class CompleteApplicativeTestSuite {

    @Test
    @DisplayName("Run complete Applicative test pattern")
    void runCompleteApplicativeTestPattern() {
      TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(EitherApplicativeTest.class);

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
    @DisplayName("ap() applies function to value - both Right")
    void apAppliesFunctionToValue() {
      Kind<EitherKind.Witness<String>, Function<Integer, String>> funcKind =
          applicative.of(i -> "value:" + i);
      Kind<EitherKind.Witness<String>, Integer> valueKind = applicative.of(DEFAULT_RIGHT_VALUE);

      var result = applicative.ap(funcKind, valueKind);

      assertThatEither(narrowToEither(result)).isRight().hasRight("value:42");
    }

    @Test
    @DisplayName("ap() propagates Left from function")
    void apPropagatesLeftFromFunction() {
      Kind<EitherKind.Witness<String>, Function<Integer, String>> funcKind =
          leftKind(TestErrorType.FUNCTION_ERROR);
      Kind<EitherKind.Witness<String>, Integer> valueKind = applicative.of(DEFAULT_RIGHT_VALUE);

      var result = applicative.ap(funcKind, valueKind);

      assertThatEither(narrowToEither(result))
          .isLeft()
          .hasLeft(TestErrorType.FUNCTION_ERROR.message());
    }

    @Test
    @DisplayName("map2() combines two Right values")
    void map2CombinesTwoRightValues() {
      var r1 = applicative.of(10);
      var r2 = applicative.of("test");

      var result = applicative.map2(r1, r2, (i, s) -> s + ":" + i);

      assertThatEither(narrowToEither(result)).isRight().hasRight("test:10");
    }

    @Test
    @DisplayName("map3() combines three Right values")
    void map3CombinesThreeRightValues() {
      var r1 = applicative.of(1);
      var r2 = applicative.of("test");
      var r3 = applicative.of(3.14);

      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);

      var result = applicative.map3(r1, r2, r3, combiner);

      assertThatEither(narrowToEither(result)).isRight().hasRight("test:1:3.14");
    }

    @Test
    @DisplayName("map4() combines four Right values")
    void map4CombinesFourRightValues() {
      var r1 = applicative.of(1);
      var r2 = applicative.of("test");
      var r3 = applicative.of(3.14);
      var r4 = applicative.of(true);

      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);

      var result = applicative.map4(r1, r2, r3, r4, combiner);

      assertThatEither(narrowToEither(result)).isRight().hasRight("test:1:3.14:true");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .testLaws();
    }

    @Test
    @DisplayName("Test Functor composition law with both mappers")
    void testFunctorCompositionLaw() {
      var composed = validMapper.andThen(secondMapper);
      var leftSide = applicative.map(composed, validKind);

      var intermediate = applicative.map(validMapper, validKind);
      var rightSide = applicative.map(secondMapper, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide)).as("Functor Composition Law").isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("mapN operations with null values in Right")
    void mapNWithNullValuesInRight() {
      var rightNull = applicative.of(null);
      var rightValue = applicative.of("test");

      var result =
          applicative.map2(
              rightNull, rightValue, (i, s) -> (i == null ? "null" : i.toString()) + ":" + s);

      assertThatEither(narrowToEither(result)).isRight().hasRight("null:test");
    }

    @Test
    @DisplayName("mapN operations short-circuit on first Left")
    void mapNShortCircuitsOnFirstLeft() {
      Kind<EitherKind.Witness<String>, Integer> l1 = leftKind(TestErrorType.ERROR_1);
      Kind<EitherKind.Witness<String>, String> l2 = leftKind(TestErrorType.ERROR_2);
      Kind<EitherKind.Witness<String>, Double> l3 = leftKind(TestErrorType.ERROR_3);

      Function3<Integer, String, Double, String> combiner = (i, s, d) -> "result";

      Kind<EitherKind.Witness<String>, String> result = applicative.map3(l1, l2, l3, combiner);
      assertThatEither(narrowToEither(result)).isLeft().hasLeft(TestErrorType.ERROR_1.message());
    }

    @Test
    @DisplayName("Complex nested applicative operations")
    void complexNestedApplicativeOperations() {
      Kind<EitherKind.Witness<String>, Function<Integer, Function<String, String>>> nestedFunc =
          applicative.of(i -> s -> s + ":" + i);
      Kind<EitherKind.Witness<String>, Integer> intKind = applicative.of(DEFAULT_RIGHT_VALUE);
      Kind<EitherKind.Witness<String>, String> stringKind = applicative.of("test");

      var partialFunc = applicative.ap(nestedFunc, intKind);
      var result = applicative.ap(partialFunc, stringKind);

      assertThatEither(narrowToEither(result)).isRight().hasRight("test:42");
    }
  }
}
