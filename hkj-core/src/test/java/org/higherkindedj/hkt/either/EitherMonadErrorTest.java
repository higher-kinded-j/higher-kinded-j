// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * EitherMonad Error Handling Test Suite using new TypeClassTest API.
 *
 * <p>Demonstrates migration to the new fluent API whilst maintaining full test coverage.
 */
@DisplayName("EitherMonad Error Handling Complete Test Suite")
class EitherMonadErrorTest extends TypeClassTestBase<EitherKind.Witness<String>, Integer, String> {

  private EitherMonad<String> monadError;
  private Function<String, Kind<EitherKind.Witness<String>, Integer>> validHandler;
  private Kind<EitherKind.Witness<String>, Integer> validFallback;

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
  protected Function<Integer, Kind<EitherKind.Witness<String>, String>> createValidFlatMapper() {
    return i -> EITHER.widen(Either.right("flat:" + i));
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
  protected Function<Integer, Kind<EitherKind.Witness<String>, String>> createTestFunction() {
    return i -> EITHER.widen(Either.right("test:" + i));
  }

  @Override
  protected Function<String, Kind<EitherKind.Witness<String>, String>> createChainFunction() {
    return s -> EITHER.widen(Either.right(s + "!"));
  }

  @Override
  protected BiPredicate<Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> EITHER.narrow(k1).equals(EITHER.narrow(k2));
  }

  @BeforeEach
  void setUpMonadError() {
    monadError = EitherMonad.instance();
    validHandler = err -> monadError.of(-1);
    validFallback = monadError.of(-999);
    validateMonadFixtures();
  }

  // Helper method to create a Left value for error testing
  private Kind<EitherKind.Witness<String>, Integer> leftKind() {
    return EITHER.widen(Either.left("TEST_ERROR"));
  }

  record TestError(String code) {}

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

      Either<String, Integer> either = EITHER.narrow(result);
      assert either.getRight() == -1;
    }

    @Test
    @DisplayName("handleErrorWith() passes through Right")
    void handleErrorWithPassesThroughRight() {
      Kind<EitherKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(validKind, validHandler);

      Either<String, Integer> either = EITHER.narrow(result);
      assert either.getRight() == 42;
    }

    @Test
    @DisplayName("raiseError() creates Left")
    void raiseErrorCreatesLeft() {
      Kind<EitherKind.Witness<String>, Integer> result = monadError.raiseError("ERROR");

      Either<String, Integer> either = EITHER.narrow(result);
      assert either.isLeft();
      assert either.getLeft().equals("ERROR");
    }

    @Test
    @DisplayName("recoverWith() uses fallback on Left")
    void recoverWithUsesFallbackOnLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftValue = leftKind();

      Kind<EitherKind.Witness<String>, Integer> result =
          monadError.recoverWith(leftValue, validFallback);

      Either<String, Integer> either = EITHER.narrow(result);
      assert either.getRight() == -999;
    }

    @Test
    @DisplayName("recover() uses value on Left")
    void recoverUsesValueOnLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftValue = leftKind();

      Kind<EitherKind.Witness<String>, Integer> result = monadError.recover(leftValue, 100);

      Either<String, Integer> either = EITHER.narrow(result);
      assert either.getRight() == 100;
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

      Either<String, Integer> either = EITHER.narrow(result);
      assert either.getRight() == 0;
    }

    @Test
    @DisplayName("Chained error recovery")
    void chainedErrorRecovery() {
      Kind<EitherKind.Witness<String>, Integer> start = EITHER.widen(Either.left("RECOVERABLE"));

      Kind<EitherKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(
              start,
              err -> {
                if (err.equals("RECOVERABLE")) {
                  return monadError.of(999);
                }
                return EITHER.widen(Either.left("UNRECOVERABLE"));
              });

      Either<String, Integer> either = EITHER.narrow(result);
      assert either.getRight() == 999;
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

      try {
        monadError.handleErrorWith(leftValue, throwingHandler);
        throw new AssertionError("Should have thrown exception");
      } catch (RuntimeException e) {
        assert e == testException : "Should propagate the same exception";
      }
    }
  }

  @Nested
  @DisplayName("Integration Examples")
  class IntegrationExamples {

    @Test
    @DisplayName("Real-world scenario: validation pipeline")
    void validationPipeline() {
      Function<Integer, Either<String, Integer>> validatePositive =
          i -> i > 0 ? Either.right(i) : Either.left("NOT_POSITIVE");

      Function<Integer, Either<String, Integer>> validateRange =
          i -> i <= 100 ? Either.right(i) : Either.left("OUT_OF_RANGE");

      Function<Integer, Either<String, String>> format = i -> Either.right("Valid: " + i);

      // Success case
      Either<String, String> success =
          validatePositive.apply(50).flatMap(validateRange).flatMap(format);

      assert success.isRight();
      assert success.getRight().equals("Valid: 50");

      // Failure case
      Either<String, String> failure =
          validatePositive.apply(-5).flatMap(validateRange).flatMap(format);

      assert failure.isLeft();
      assert failure.getLeft().equals("NOT_POSITIVE");
    }

    @Test
    @DisplayName("Real-world scenario: error recovery with fallback")
    void errorRecoveryWithFallback() {
      Function<String, Either<String, String>> primarySource =
          id -> Either.left("PRIMARY_UNAVAILABLE");

      Function<String, Either<String, String>> fallbackSource = id -> Either.right("fallback-data");

      String id = "user-123";
      Either<String, String> result =
          primarySource.apply(id).fold(err -> fallbackSource.apply(id), Either::right);

      assert result.isRight();
      assert result.getRight().equals("fallback-data");
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Error recovery efficient with many operations")
    void errorRecoveryEfficientWithManyOperations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<EitherKind.Witness<String>, Integer> start = validKind;

        Kind<EitherKind.Witness<String>, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          result = monadError.handleErrorWith(result, validHandler);
        }

        Either<String, Integer> either = EITHER.narrow(result);
        assert either.getRight() == 42; // Should still be original value
      }
    }
  }
}
