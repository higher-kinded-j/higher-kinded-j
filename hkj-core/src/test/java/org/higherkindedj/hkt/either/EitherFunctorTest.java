// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherFunctor Complete Test Suite")
class EitherFunctorTest extends EitherTestBase {

  private EitherFunctor<String> functor;
  private Functor<EitherKind.Witness<String>> functorTyped;

  @BeforeEach
  void setUpFunctor() {
    functor = new EitherFunctor<>();
    functorTyped = functor;
  }

  @Nested
  @DisplayName("Complete Functor Test Suite")
  class CompleteFunctorTestSuite {
    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
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
      var result = functor.map(validMapper, validKind);

      assertThatEither(narrowToEither(result)).isRight().hasRight("42");
    }

    @Test
    @DisplayName("map() on Left passes through unchanged")
    void mapOnLeftPassesThrough() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.ERROR_1);

      var result = functor.map(validMapper, leftKind);

      assertThatEither(narrowToEither(result)).isLeft().hasLeft(TestErrorType.ERROR_1.message());
    }

    @Test
    @DisplayName("map() with null values in Right")
    void mapWithNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNull = rightKind((Integer) null);
      Function<Integer, String> nullSafeMapper = i -> String.valueOf(i);

      var result = functor.map(nullSafeMapper, rightNull);

      assertThatEither(narrowToEither(result)).isRight().hasRight("null");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {
    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {
    @Test
    @DisplayName("Test with different error types")
    void testWithDifferentErrorTypes() {
      EitherFunctor<ComplexTestError> complexFunctor = new EitherFunctor<>();
      Functor<EitherKind.Witness<ComplexTestError>> complexFunctorTyped = complexFunctor;
      var complexKind = EITHER.widen(Either.<ComplexTestError, Integer>right(100));

      TypeClassTest.<EitherKind.Witness<ComplexTestError>>functor(EitherFunctor.class)
          .<Integer>instance(complexFunctorTyped)
          .<String>withKind(complexKind)
          .withMapper(validMapper)
          .testOperations();

      TypeClassTest.<EitherKind.Witness<ComplexTestError>>functor(EitherFunctor.class)
          .<Integer>instance(complexFunctorTyped)
          .<String>withKind(complexKind)
          .withMapper(validMapper)
          .testValidations();
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

      var result = functor.map(complexMapper, validKind);

      assertThatEither(narrowToEither(result)).isRight().hasRight("positive:42");
    }
  }

}
