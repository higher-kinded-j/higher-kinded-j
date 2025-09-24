// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.patterns.TypeClassTestPattern;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherFunctor Complete Test Suite")
class EitherFunctorTest extends TypeClassTestBase<EitherKind.Witness<String>, Integer, String> {

  record TestError(String code) {}

  private EitherFunctor<String> functor;

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
  protected BiPredicate<Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> EITHER.narrow(k1).equals(EITHER.narrow(k2));
  }

  @BeforeEach
  void setUpFunctor() {
    functor = new EitherFunctor<>();
    validateRequiredFixtures();
  }

  @Nested
  @DisplayName("Complete Functor Test Suite")
  class CompleteFunctorTestSuite {

    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      // Use the complete test method directly to avoid type inference issues
      TypeClassTestPattern.testCompleteFunctor(
          functor, EitherFunctor.class, validKind, validMapper, equalityChecker);
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(EitherFunctorTest.class);

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
    @DisplayName("map() on Right applies function")
    void mapOnRightAppliesFunction() {
      Kind<EitherKind.Witness<String>, String> result = functor.map(validMapper, validKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("42");
    }

    @Test
    @DisplayName("map() on Left passes through unchanged")
    void mapOnLeftPassesThrough() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = EITHER.widen(Either.left("E1"));

      Kind<EitherKind.Witness<String>, String> result = functor.map(validMapper, leftKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("E1");
    }

    @Test
    @DisplayName("map() with null values in Right")
    void mapWithNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNull = EITHER.widen(Either.right(null));
      Function<Integer, String> nullSafeMapper = i -> String.valueOf(i);

      Kind<EitherKind.Witness<String>, String> result = functor.map(nullSafeMapper, rightNull);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("null");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTestPattern.testFunctorOperations(functor, validKind, validMapper);
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTestPattern.testFunctorValidations(
          functor, EitherFunctor.class, validKind, validMapper);
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTestPattern.testFunctorExceptionPropagation(functor, validKind);
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTestPattern.testFunctorLaws(
          functor, validKind, validMapper, secondMapper, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Test with different error types")
    void testWithDifferentErrorTypes() {
      record ComplexError(String code, int severity) {}

      EitherFunctor<ComplexError> complexFunctor = new EitherFunctor<>();
      Kind<EitherKind.Witness<ComplexError>, Integer> complexKind = EITHER.widen(Either.right(100));

      TypeClassTestPattern.testFunctorOperations(complexFunctor, complexKind, validMapper);
      TypeClassTestPattern.testFunctorValidations(
          complexFunctor, EitherFunctor.class, complexKind, validMapper);
    }

    @Test
    @DisplayName("Test with complex transformations")
    void testComplexTransformations() {
      Function<Integer, String> complexMapper =
          i -> {
            if (i < 0) return "negative";
            if (i == 0) return "zero";
            return "positive:" + i;
          };

      Kind<EitherKind.Witness<String>, String> result = functor.map(complexMapper, validKind);
      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.getRight()).isEqualTo("positive:42");
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Test performance characteristics")
    void testPerformanceCharacteristics() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        // Performance tests only run when explicitly enabled
        Kind<EitherKind.Witness<String>, Integer> start = validKind;

        long startTime = System.nanoTime();
        Kind<EitherKind.Witness<String>, Integer> result = start;
        for (int i = 0; i < 10000; i++) {
          result = functor.map(x -> x + 1, result);
        }
        long duration = System.nanoTime() - startTime;

        assertThat(duration).isLessThan(100_000_000L); // Less than 100ms
      }
    }
  }
}
