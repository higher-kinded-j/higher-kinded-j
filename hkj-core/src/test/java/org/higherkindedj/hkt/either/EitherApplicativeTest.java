// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.patterns.FlexibleValidationConfig;
import org.higherkindedj.hkt.test.patterns.TypeClassTestPattern;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad Applicative Operations Complete Test Suite")
class EitherApplicativeTest extends TypeClassTestBase<EitherKind.Witness<String>, Integer, String> {

  record TestError(String code) {}

  private EitherMonad<String> applicative;

  @Override
  protected Kind<EitherKind.Witness<String>, Integer> createValidKind() {
    return EITHER.widen(Either.right(42));
  }

  @Override
  protected Kind<EitherKind.Witness<String>, Integer> createValidKind2() {
    return EITHER.widen(Either.right(24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Kind<EitherKind.Witness<String>, Function<Integer, String>> createValidFunctionKind() {
    return EITHER.widen(Either.right(TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return 42;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s; // String -> String, not Integer -> String
  }

  @Override
  protected BiPredicate<Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> EITHER.narrow(k1).equals(EITHER.narrow(k2));
  }

  @BeforeEach
  void setUpApplicative() {
    applicative = EitherMonad.instance();
    validateApplicativeFixtures();
  }

  @Nested
  @DisplayName("Complete Applicative Test Suite")
  class CompleteApplicativeTestSuite {

    @Test
    @DisplayName("Run complete Applicative test pattern")
    void runCompleteApplicativeTestPattern() {
      // Test operations
      TypeClassTestPattern.testApplicativeOperations(
          applicative,
          validKind,
          validKind2,
          validMapper,
          validFunctionKind,
          validCombiningFunction);

      // Test validations with flexible configuration
      new FlexibleValidationConfig.ApplicativeValidation<>(
              applicative,
              validKind,
              validKind2,
              validMapper,
              validFunctionKind,
              validCombiningFunction)
          .mapWithClassContext(EitherFunctor.class)
          .apWithClassContext(EitherMonad.class)
          .map2WithoutClassContext()
          .test();

      // Test exception propagation
      TypeClassTestPattern.testApplicativeExceptionPropagation(applicative, validKind);

      // Test laws
      TypeClassTestPattern.testApplicativeLaws(
          applicative, validKind, testValue, validMapper, equalityChecker);
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
      Kind<EitherKind.Witness<String>, Integer> valueKind = applicative.of(42);

      Kind<EitherKind.Witness<String>, String> result = applicative.ap(funcKind, valueKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.getRight()).isEqualTo("value:42");
    }

    @Test
    @DisplayName("ap() propagates Left from function")
    void apPropagatesLeftFromFunction() {
      Kind<EitherKind.Witness<String>, Function<Integer, String>> funcKind =
          EITHER.widen(Either.left("FUNC_ERR"));
      Kind<EitherKind.Witness<String>, Integer> valueKind = applicative.of(42);

      Kind<EitherKind.Witness<String>, String> result = applicative.ap(funcKind, valueKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.getLeft()).isEqualTo("FUNC_ERR");
    }

    @Test
    @DisplayName("map2() combines two Right values")
    void map2CombinesTwoRightValues() {
      Kind<EitherKind.Witness<String>, Integer> r1 = applicative.of(10);
      Kind<EitherKind.Witness<String>, String> r2 = applicative.of("test");

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<EitherKind.Witness<String>, String> result = applicative.map2(r1, r2, combiner);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.getRight()).isEqualTo("test:10");
    }

    @Test
    @DisplayName("map3() combines three Right values")
    void map3CombinesThreeRightValues() {
      Kind<EitherKind.Witness<String>, Integer> r1 = applicative.of(1);
      Kind<EitherKind.Witness<String>, String> r2 = applicative.of("test");
      Kind<EitherKind.Witness<String>, Double> r3 = applicative.of(3.14);

      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);

      Kind<EitherKind.Witness<String>, String> result = applicative.map3(r1, r2, r3, combiner);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.getRight()).isEqualTo("test:1:3.14");
    }

    @Test
    @DisplayName("map4() combines four Right values")
    void map4CombinesFourRightValues() {
      Kind<EitherKind.Witness<String>, Integer> r1 = applicative.of(1);
      Kind<EitherKind.Witness<String>, String> r2 = applicative.of("test");
      Kind<EitherKind.Witness<String>, Double> r3 = applicative.of(3.14);
      Kind<EitherKind.Witness<String>, Boolean> r4 = applicative.of(true);

      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);

      Kind<EitherKind.Witness<String>, String> result = applicative.map4(r1, r2, r3, r4, combiner);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.getRight()).isEqualTo("test:1:3.14:true");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTestPattern.testApplicativeOperations(
          applicative,
          validKind,
          validKind2,
          validMapper,
          validFunctionKind,
          validCombiningFunction);
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      new FlexibleValidationConfig.ApplicativeValidation<>(
              applicative,
              validKind,
              validKind2,
              validMapper,
              validFunctionKind,
              validCombiningFunction)
          .mapWithClassContext(EitherFunctor.class)
          .apWithClassContext(EitherMonad.class)
          .map2WithoutClassContext()
          .test();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTestPattern.testApplicativeExceptionPropagation(applicative, validKind);
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTestPattern.testApplicativeLaws(
          applicative,
          validKind, // Kind<EitherKind.Witness<String>, Integer>
          testValue, // Integer (42)
          validMapper, // Function<Integer, String>
          equalityChecker); // BiPredicate for Kind comparison
    }

    @Test
    @DisplayName("Test Functor composition law with both mappers")
    void testFunctorCompositionLaw() {
      // Composed: Integer -> String -> String
      Function<Integer, String> composed = validMapper.andThen(secondMapper);
      Kind<EitherKind.Witness<String>, String> leftSide = applicative.map(composed, validKind);

      // Separate: map(secondMapper, map(validMapper, validKind))
      Kind<EitherKind.Witness<String>, String> intermediate =
          applicative.map(validMapper, validKind);
      Kind<EitherKind.Witness<String>, String> rightSide =
          applicative.map(secondMapper, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide)).as("Functor Composition Law").isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("mapN operations with null values in Right")
    void mapNWithNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNull = applicative.of(null);
      Kind<EitherKind.Witness<String>, String> rightValue = applicative.of("test");

      BiFunction<Integer, String, String> nullSafeFunc =
          (i, s) -> (i == null ? "null" : i.toString()) + ":" + s;

      Kind<EitherKind.Witness<String>, String> result =
          applicative.map2(rightNull, rightValue, nullSafeFunc);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.getRight()).isEqualTo("null:test");
    }

    @Test
    @DisplayName("mapN operations short-circuit on first Left")
    void mapNShortCircuitsOnFirstLeft() {
      Kind<EitherKind.Witness<String>, Integer> l1 = EITHER.widen(Either.left("E1"));
      Kind<EitherKind.Witness<String>, String> l2 = EITHER.widen(Either.left("E2"));
      Kind<EitherKind.Witness<String>, Double> l3 = EITHER.widen(Either.left("E3"));

      Function3<Integer, String, Double, String> combiner = (i, s, d) -> "result";

      Kind<EitherKind.Witness<String>, String> result = applicative.map3(l1, l2, l3, combiner);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.getLeft()).isEqualTo("E1");
    }

    @Test
    @DisplayName("Complex nested applicative operations")
    void complexNestedApplicativeOperations() {
      Kind<EitherKind.Witness<String>, Function<Integer, Function<String, String>>> nestedFunc =
          applicative.of(i -> s -> s + ":" + i);
      Kind<EitherKind.Witness<String>, Integer> intKind = applicative.of(42);
      Kind<EitherKind.Witness<String>, String> stringKind = applicative.of("test");

      Kind<EitherKind.Witness<String>, Function<String, String>> partialFunc =
          applicative.ap(nestedFunc, intKind);
      Kind<EitherKind.Witness<String>, String> result = applicative.ap(partialFunc, stringKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.getRight()).isEqualTo("test:42");
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Efficient with many map2 operations")
    void efficientWithManyMap2Operations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<EitherKind.Witness<String>, Integer> start = applicative.of(1);

        Kind<EitherKind.Witness<String>, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int increment = i;
          Kind<EitherKind.Witness<String>, Integer> incrementKind = applicative.of(increment);
          result = applicative.map2(result, incrementKind, (a, b) -> a + b);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        Either<String, Integer> either = EITHER.narrow(result);
        assertThat(either.getRight()).isEqualTo(expectedSum);
      }
    }
  }
}
