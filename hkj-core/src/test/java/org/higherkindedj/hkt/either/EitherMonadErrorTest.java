// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad Error Handling Complete Test Suite")
class EitherMonadErrorTest extends EitherTestBase {

  private EitherMonad<String> monadError;
  private Function<String, Kind<EitherKind.Witness<String>, Integer>> validHandler;
  private Kind<EitherKind.Witness<String>, Integer> validFallback;

  @BeforeEach
  void setUpMonadError() {
    monadError = EitherMonad.instance();
    validHandler = err -> monadError.of(-1);
    validFallback = monadError.of(-999);
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Test Suite Using New API")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete MonadError test pattern")
    void runCompleteMonadErrorTestPattern() {
      TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .withFlatMapFrom(EitherMonad.class)
          .withHandleErrorWithFrom(EitherMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Selective testing - operations and laws only")
    void selectiveTestingOperationsAndLaws() {
      TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .selectTests()
          .skipValidations()
          .skipExceptions()
          .test();
    }

    @Test
    @DisplayName("Quick smoke test - operations only")
    void quickSmokeTest() {
      TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .withFlatMapFrom(EitherMonad.class)
          .withHandleErrorWithFrom(EitherMonad.class)
          .testOperationsAndValidations();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(EitherMonadErrorTest.class);

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
    @DisplayName("handleErrorWith() recovers from Left")
    void handleErrorWithRecoversFromLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftValue = leftKind();

      Kind<EitherKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(leftValue, validHandler);

      assertThatEither(narrowToEither(result)).isRight().hasRight(-1);
    }

    @Test
    @DisplayName("handleErrorWith() passes through Right")
    void handleErrorWithPassesThroughRight() {
      Kind<EitherKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(validKind, validHandler);

      assertThatEither(narrowToEither(result)).isRight().hasRight(DEFAULT_RIGHT_VALUE);
    }

    @Test
    @DisplayName("raiseError() creates Left")
    void raiseErrorCreatesLeft() {
      Kind<EitherKind.Witness<String>, Integer> result =
          monadError.raiseError(TestErrorType.VALIDATION.message());

      assertThatEither(narrowToEither(result)).isLeft().hasLeft(TestErrorType.VALIDATION.message());
    }

    @Test
    @DisplayName("recoverWith() uses fallback on Left")
    void recoverWithUsesFallbackOnLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftValue = leftKind();

      Kind<EitherKind.Witness<String>, Integer> result =
          monadError.recoverWith(leftValue, validFallback);

      assertThatEither(narrowToEither(result)).isRight().hasRight(-999);
    }

    @Test
    @DisplayName("recover() uses value on Left")
    void recoverUsesValueOnLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftValue = leftKind();

      Kind<EitherKind.Witness<String>, Integer> result = monadError.recover(leftValue, 100);

      assertThatEither(narrowToEither(result)).isRight().hasRight(100);
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .withFlatMapFrom(EitherMonad.class)
          .withHandleErrorWithFrom(EitherMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Error handling with null error values")
    void errorHandlingWithNullErrors() {
      Kind<EitherKind.Witness<String>, Integer> nullError = monadError.raiseError(null);

      Function<String, Kind<EitherKind.Witness<String>, Integer>> handler =
          err -> monadError.of(err == null ? 0 : -1);

      Kind<EitherKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(nullError, handler);

      assertThatEither(narrowToEither(result)).isRight().hasRight(0);
    }

    @Test
    @DisplayName("Chained error recovery")
    void chainedErrorRecovery() {
      Kind<EitherKind.Witness<String>, Integer> start = leftKind(TestErrorType.RECOVERABLE);

      Kind<EitherKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(
              start,
              err -> {
                if (err.equals(TestErrorType.RECOVERABLE.message())) {
                  return monadError.of(999);
                }
                return leftKind(TestErrorType.UNRECOVERABLE);
              });

      assertThatEither(narrowToEither(result)).isRight().hasRight(999);
    }

    @Test
    @DisplayName("Exception propagation in handleErrorWith handler")
    void exceptionPropagationInHandler() {
      Kind<EitherKind.Witness<String>, Integer> leftValue = leftKind();
      RuntimeException testException = new RuntimeException("Handler exception");

      Function<String, Kind<EitherKind.Witness<String>, Integer>> throwingHandler =
          err -> {
            throw testException;
          };

      assertThatThrownBy(() -> monadError.handleErrorWith(leftValue, throwingHandler))
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Integration Examples")
  class IntegrationExamples {

    @Test
    @DisplayName("Real-world scenario: validation pipeline")
    void validationPipeline() {
      Function<Integer, Either<String, Integer>> validatePositive =
          i -> i > 0 ? Either.right(i) : Either.left(TestErrorType.VALIDATION.message());

      Function<Integer, Either<String, Integer>> validateRange =
          i -> i <= 100 ? Either.right(i) : Either.left("OUT_OF_RANGE");

      Function<Integer, Either<String, String>> format = i -> Either.right("Valid: " + i);

      // Success case
      Either<String, String> success =
          validatePositive.apply(50).flatMap(validateRange).flatMap(format);

      assertThatEither(success).isRight().hasRight("Valid: 50");

      // Failure case
      Either<String, String> failure =
          validatePositive.apply(-5).flatMap(validateRange).flatMap(format);

      assertThatEither(failure).isLeft().hasLeft(TestErrorType.VALIDATION.message());
    }

    @Test
    @DisplayName("Real-world scenario: error recovery with fallback")
    void errorRecoveryWithFallback() {
      Function<String, Either<String, String>> primarySource =
          id -> Either.left(TestErrorType.RESOURCE_UNAVAILABLE.message());

      Function<String, Either<String, String>> fallbackSource = id -> Either.right("fallback-data");

      String id = "user-123";
      Either<String, String> result =
          primarySource.apply(id).fold(err -> fallbackSource.apply(id), Either::right);

      assertThatEither(result).isRight().hasRight("fallback-data");
    }
  }
}
