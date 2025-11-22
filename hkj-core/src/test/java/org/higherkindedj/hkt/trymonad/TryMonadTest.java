// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trymonad.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("TryMonad Complete Test Suite")
class TryMonadTest extends TryTestBase {

  private TryMonad monad;

  @BeforeEach
  void setUpMonad() {
    monad = TryMonad.INSTANCE;
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadPattern() {
      TypeClassTest.<TryKind.Witness>monad(TryMonad.class)
          .<String>instance(monad)
          .<Integer>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(TryFunctor.class)
          .withApFrom(TryApplicative.class)
          .withFlatMapFrom(TryMonad.class)
          .selectTests()
          .skipExceptions() // Try captures exceptions, not propagates
          .test();
    }

    @Test
    @DisplayName("Verify Monad operations")
    void verifyMonadOperations() {
      TypeClassTest.<TryKind.Witness>monad(TryMonad.class)
          .<String>instance(monad)
          .<Integer>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Verify Monad validations")
    void verifyMonadValidations() {
      TypeClassTest.<TryKind.Witness>monad(TryMonad.class)
          .<String>instance(monad)
          .<Integer>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(TryFunctor.class)
          .withApFrom(TryApplicative.class)
          .withFlatMapFrom(TryMonad.class)
          .testValidations();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<TryKind.Witness>monad(TryMonad.class)
          .<String>instance(monad)
          .<Integer>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<TryKind.Witness>monad(TryMonad.class)
          .<String>instance(monad)
          .<Integer>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(TryFunctor.class)
          .withApFrom(TryApplicative.class)
          .withFlatMapFrom(TryMonad.class)
          .selectTests()
          .onlyValidations()
          .test();
    }

    @Test
    @DisplayName("Test exception propagation only - N/A for Try (captures exceptions by design)")
    void testExceptionPropagationOnly() {
      // Try captures exceptions rather than propagating them
      // This is the core feature of Try, so standard exception propagation tests don't apply
      // See ExceptionPropagationTests nested class for Try-specific exception handling tests
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<TryKind.Witness>monad(TryMonad.class)
          .<String>instance(monad)
          .<Integer>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }

  @Nested
  @DisplayName("flatMap() Operation Tests")
  class FlatMapOperationTests {

    @Test
    @DisplayName("flatMap() with Success and returning Success should succeed")
    void flatMap_withSuccessReturningSuccess_shouldSucceed() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success("hello"));
      Function<String, Kind<TryKind.Witness, Integer>> func =
          s -> TRY.widen(Try.success(s.length()));

      Kind<TryKind.Witness, Integer> result = monad.flatMap(func, success);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValue(5);
    }

    @Test
    @DisplayName("flatMap() with Success and returning Failure should return Failure")
    void flatMap_withSuccessReturningFailure_shouldReturnFailure() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success("hello"));
      RuntimeException exception = new RuntimeException("Inner failure");
      Function<String, Kind<TryKind.Witness, Integer>> func =
          s -> TRY.widen(Try.failure(exception));

      Kind<TryKind.Witness, Integer> result = monad.flatMap(func, success);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("flatMap() with Failure should return Failure")
    void flatMap_withFailure_shouldReturnFailure() {
      RuntimeException exception = new RuntimeException("Original failure");
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(exception));
      Function<String, Kind<TryKind.Witness, Integer>> func =
          s -> TRY.widen(Try.success(s.length()));

      Kind<TryKind.Witness, Integer> result = monad.flatMap(func, failure);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("flatMap() should capture exception thrown by function")
    void flatMap_shouldCaptureExceptionThrownByFunction() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success("test"));
      RuntimeException funcException = new RuntimeException("Function threw");
      Function<String, Kind<TryKind.Witness, Integer>> throwingFunc =
          s -> {
            throw funcException;
          };

      Kind<TryKind.Witness, Integer> result = monad.flatMap(throwingFunc, success);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(funcException);
    }

    @Test
    @DisplayName("flatMap() should reject null result from function")
    void flatMap_shouldRejectNullResultFromFunction() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success("test"));
      Function<String, Kind<TryKind.Witness, Integer>> nullFunc = s -> null;

      Kind<TryKind.Witness, Integer> result = monad.flatMap(nullFunc, success);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult)
          .isFailure()
          .hasExceptionSatisfying(
              ex ->
                  assertThat(ex)
                      .hasMessageContaining(
                          "Function f in TryMonad.flatMap returned null when Kind expected"));
    }
  }

  @Nested
  @DisplayName("raiseError() Operation Tests")
  class RaiseErrorOperationTests {

    @Test
    @DisplayName("raiseError() should create Failure with RuntimeException")
    void raiseError_shouldCreateFailureWithRuntimeException() {
      RuntimeException exception = new RuntimeException("Test error");
      Kind<TryKind.Witness, String> result = monad.raiseError(exception);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("raiseError() should create Failure with checked exception")
    void raiseError_shouldCreateFailureWithCheckedException() {
      IOException exception = new IOException("IO error");
      Kind<TryKind.Witness, String> result = monad.raiseError(exception);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("raiseError() should create Failure with Error")
    void raiseError_shouldCreateFailureWithError() {
      StackOverflowError error = new StackOverflowError("Stack overflow");
      Kind<TryKind.Witness, String> result = monad.raiseError(error);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(error);
    }

    @Test
    @DisplayName("raiseError() should throw NPE for null Throwable")
    void raiseError_shouldThrowNPEForNullThrowable() {
      assertThatNullPointerException()
          .isThrownBy(() -> monad.raiseError(null))
          .withMessageContaining("TryMonad.raiseError error cannot be null");
    }
  }

  @Nested
  @DisplayName("handleErrorWith() Operation Tests")
  class HandleErrorWithOperationTests {

    @Test
    @DisplayName("handleErrorWith() on Success should return original Success")
    void handleErrorWith_onSuccess_shouldReturnOriginalSuccess() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success("test"));
      Function<Throwable, Kind<TryKind.Witness, String>> handler =
          t -> TRY.widen(Try.success("recovered"));

      Kind<TryKind.Witness, String> result = monad.handleErrorWith(success, handler);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValue("test");
    }

    @Test
    @DisplayName("handleErrorWith() on Failure should apply handler")
    void handleErrorWith_onFailure_shouldApplyHandler() {
      RuntimeException exception = new RuntimeException("Original error");
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(exception));
      Function<Throwable, Kind<TryKind.Witness, String>> handler =
          t -> TRY.widen(Try.success("recovered"));

      Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, handler);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValue("recovered");
    }

    @Test
    @DisplayName("handleErrorWith() should capture exception from handler")
    void handleErrorWith_shouldCaptureExceptionFromHandler() {
      RuntimeException originalException = new RuntimeException("Original error");
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(originalException));
      RuntimeException handlerException = new RuntimeException("Handler error");
      Function<Throwable, Kind<TryKind.Witness, String>> throwingHandler =
          t -> {
            throw handlerException;
          };

      Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, throwingHandler);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(handlerException);
    }

    @Test
    @DisplayName("handleErrorWith() should reject null result from handler")
    void handleErrorWith_shouldRejectNullResultFromHandler() {
      RuntimeException exception = new RuntimeException("Original error");
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(exception));
      Function<Throwable, Kind<TryKind.Witness, String>> nullHandler = t -> null;

      Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, nullHandler);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult)
          .isFailure()
          .hasExceptionSatisfying(
              ex ->
                  assertThat(ex)
                      .hasMessageContaining(
                          "Function handler in TryMonad.handleErrorWith returned null when Kind"
                              + " expected"));
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    // Parameterized test data for flatMap null validation
    private static Stream<Arguments> flatMapNullParameters() {
      Kind<TryKind.Witness, String> testValidKind = TRY.widen(Try.success("test value"));
      Function<String, Kind<TryKind.Witness, Integer>> testValidFlatMapper =
          s -> TRY.widen(Try.success(s.length()));
      return Stream.of(
          Arguments.of("Function f", testValidKind, null),
          Arguments.of("Kind", null, testValidFlatMapper));
    }

    @ParameterizedTest(name = "flatMap validates {0} parameter is non-null")
    @MethodSource("flatMapNullParameters")
    @DisplayName("flatMap() validates null parameters")
    void flatMap_shouldValidateNullParameters(
        String expectedMessagePart,
        Kind<TryKind.Witness, String> kind,
        Function<String, Kind<TryKind.Witness, Integer>> function) {
      assertThatNullPointerException()
          .isThrownBy(() -> monad.flatMap(function, kind))
          .withMessageContaining(expectedMessagePart)
          .withMessageContaining("TryMonad.flatMap");
    }

    // Parameterized test data for handleErrorWith null validation
    private static Stream<Arguments> handleErrorWithNullParameters() {
      Kind<TryKind.Witness, String> testValidKind = TRY.widen(Try.success("test value"));
      Function<Throwable, Kind<TryKind.Witness, String>> validHandler =
          t -> TRY.widen(Try.success("recovered"));
      return Stream.of(
          Arguments.of("Kind", null, validHandler),
          Arguments.of("Function handler", testValidKind, null));
    }

    @ParameterizedTest(name = "handleErrorWith validates {0} parameter is non-null")
    @MethodSource("handleErrorWithNullParameters")
    @DisplayName("handleErrorWith() validates null parameters")
    void handleErrorWith_shouldValidateNullParameters(
        String expectedMessagePart,
        Kind<TryKind.Witness, String> kind,
        Function<Throwable, Kind<TryKind.Witness, String>> handler) {
      assertThatNullPointerException()
          .isThrownBy(() -> monad.handleErrorWith(kind, handler))
          .withMessageContaining(expectedMessagePart)
          .withMessageContaining("TryMonad.handleErrorWith");
    }
  }

  @Nested
  @DisplayName("Exception Propagation Tests")
  class ExceptionPropagationTests {

    @Test
    @DisplayName("flatMap() should propagate RuntimeException from function")
    void flatMap_shouldPropagateRuntimeExceptionFromFunction() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<String, Kind<TryKind.Witness, Integer>> throwingFunc =
          s -> {
            throw testException;
          };

      Kind<TryKind.Witness, Integer> result = monad.flatMap(throwingFunc, validKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(testException);
    }

    @Test
    @DisplayName("flatMap() should propagate Error from function")
    void flatMap_shouldPropagateErrorFromFunction() {
      StackOverflowError testError = new StackOverflowError("Stack overflow");
      Function<String, Kind<TryKind.Witness, Integer>> throwingFunc =
          s -> {
            throw testError;
          };

      Kind<TryKind.Witness, Integer> result = monad.flatMap(throwingFunc, validKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(testError);
    }

    @Test
    @DisplayName("handleErrorWith() should propagate exception from handler")
    void handleErrorWith_shouldPropagateExceptionFromHandler() {
      RuntimeException originalException = new RuntimeException("Original");
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(originalException));
      RuntimeException handlerException = new RuntimeException("Handler failed");
      Function<Throwable, Kind<TryKind.Witness, String>> throwingHandler =
          t -> {
            throw handlerException;
          };

      Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, throwingHandler);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(handlerException);
    }
  }

  @Nested
  @DisplayName("Monad Law Tests")
  class MonadLawTests {

    @Test
    @DisplayName("Left identity: flatMap(of(a), f) == f(a)")
    void leftIdentityLaw() {
      String testVal = "test";
      Function<String, Kind<TryKind.Witness, Integer>> func =
          s -> TRY.widen(Try.success(s.length()));

      Kind<TryKind.Witness, String> ofValue = monad.of(testVal);
      Kind<TryKind.Witness, Integer> leftSide = monad.flatMap(func, ofValue);
      Kind<TryKind.Witness, Integer> rightSide = func.apply(testVal);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Monad Left Identity Law: flatMap(of(a), f) == f(a)")
          .isTrue();
    }

    @Test
    @DisplayName("Right identity: flatMap(m, of) == m")
    void rightIdentityLaw() {
      Function<String, Kind<TryKind.Witness, String>> ofFunc = monad::of;
      Kind<TryKind.Witness, String> leftSide = monad.flatMap(ofFunc, validKind);

      assertThat(equalityChecker.test(leftSide, validKind))
          .as("Monad Right Identity Law: flatMap(m, of) == m")
          .isTrue();
    }

    @Test
    @DisplayName("Associativity: flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))")
    void associativityLaw() {
      Function<String, Kind<TryKind.Witness, Integer>> f = s -> TRY.widen(Try.success(s.length()));
      Function<Integer, Kind<TryKind.Witness, Integer>> g = i -> TRY.widen(Try.success(i * 2));

      // Left side: flatMap(flatMap(m, f), g)
      Kind<TryKind.Witness, Integer> innerFlatMap = monad.flatMap(f, validKind);
      Kind<TryKind.Witness, Integer> leftSide = monad.flatMap(g, innerFlatMap);

      // Right side: flatMap(m, a -> flatMap(f(a), g))
      Function<String, Kind<TryKind.Witness, Integer>> rightSideFunc =
          a -> monad.flatMap(g, f.apply(a));
      Kind<TryKind.Witness, Integer> rightSide = monad.flatMap(rightSideFunc, validKind);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as(
              "Monad Associativity Law: flatMap(flatMap(m, f), g) == flatMap(m, x ->"
                  + " flatMap(f(x), g))")
          .isTrue();
    }

    @Test
    @DisplayName("Laws hold for Failure instances")
    void lawsHoldForFailure() {
      RuntimeException exception = new RuntimeException("Test failure");
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(exception));

      // Right identity with Failure
      Function<String, Kind<TryKind.Witness, String>> ofFunc = monad::of;
      Kind<TryKind.Witness, String> result = monad.flatMap(ofFunc, failure);

      assertThat(equalityChecker.test(result, failure))
          .as("Right identity holds for Failure")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("flatMap() should handle null value in Success")
    void flatMap_shouldHandleNullValueInSuccess() {
      Kind<TryKind.Witness, String> successNull = TRY.widen(Try.success(null));
      Function<String, Kind<TryKind.Witness, Integer>> safeFunc =
          s -> TRY.widen(Try.success(s == null ? -1 : s.length()));

      Kind<TryKind.Witness, Integer> result = monad.flatMap(safeFunc, successNull);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValue(-1);
    }

    @Test
    @DisplayName("handleErrorWith() should handle different exception types")
    void handleErrorWith_shouldHandleDifferentExceptionTypes() {
      // Test with RuntimeException
      RuntimeException runtimeEx = new RuntimeException("Runtime error");
      Kind<TryKind.Witness, String> runtimeFailure = TRY.widen(Try.failure(runtimeEx));
      Function<Throwable, Kind<TryKind.Witness, String>> handler =
          t -> TRY.widen(Try.success("Recovered from: " + t.getClass().getSimpleName()));

      Kind<TryKind.Witness, String> result1 = monad.handleErrorWith(runtimeFailure, handler);
      Try<String> tryResult1 = TRY.narrow(result1);
      assertThatTry(tryResult1).isSuccess().hasValue("Recovered from: RuntimeException");

      // Test with checked exception
      IOException ioEx = new IOException("IO error");
      Kind<TryKind.Witness, String> ioFailure = TRY.widen(Try.failure(ioEx));

      Kind<TryKind.Witness, String> result2 = monad.handleErrorWith(ioFailure, handler);
      Try<String> tryResult2 = TRY.narrow(result2);
      assertThatTry(tryResult2).isSuccess().hasValue("Recovered from: IOException");

      // Test with Error
      StackOverflowError error = new StackOverflowError("Stack overflow");
      Kind<TryKind.Witness, String> errorFailure = TRY.widen(Try.failure(error));

      Kind<TryKind.Witness, String> result3 = monad.handleErrorWith(errorFailure, handler);
      Try<String> tryResult3 = TRY.narrow(result3);
      assertThatTry(tryResult3).isSuccess().hasValue("Recovered from: StackOverflowError");
    }

    @Test
    @DisplayName("Chained operations should preserve error information")
    void chainedOperations_shouldPreserveErrorInformation() {
      RuntimeException exception = new RuntimeException("Original error");
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(exception));

      Function<String, Kind<TryKind.Witness, Integer>> func1 =
          s -> TRY.widen(Try.success(s.length()));
      Function<Integer, Kind<TryKind.Witness, String>> func2 =
          i -> TRY.widen(Try.success(i.toString()));

      Kind<TryKind.Witness, Integer> result1 = monad.flatMap(func1, failure);
      Kind<TryKind.Witness, String> result2 = monad.flatMap(func2, result1);

      Try<String> tryResult = TRY.narrow(result2);
      assertThatTry(tryResult).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("of() should wrap null value correctly")
    void of_shouldWrapNullValueCorrectly() {
      Kind<TryKind.Witness, String> result = monad.of(null);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValueSatisfying(v -> assertThat(v).isNull());
    }

    @Test
    @DisplayName("handleErrorWith() can convert Failure to another Failure")
    void handleErrorWith_canConvertFailureToAnotherFailure() {
      RuntimeException originalException = new RuntimeException("Original");
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(originalException));
      IOException newException = new IOException("Converted error");
      Function<Throwable, Kind<TryKind.Witness, String>> handler =
          t -> TRY.widen(Try.failure(newException));

      Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, handler);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(newException);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Complex chain of flatMap operations")
    void complexChainOfFlatMapOperations() {
      Function<String, Kind<TryKind.Witness, Integer>> step1 =
          s -> TRY.widen(Try.success(s.length()));
      Function<Integer, Kind<TryKind.Witness, Integer>> step2 = i -> TRY.widen(Try.success(i * 2));
      Function<Integer, Kind<TryKind.Witness, String>> step3 =
          i -> TRY.widen(Try.success("Result: " + i));

      Kind<TryKind.Witness, Integer> result1 = monad.flatMap(step1, validKind);
      Kind<TryKind.Witness, Integer> result2 = monad.flatMap(step2, result1);
      Kind<TryKind.Witness, String> result3 = monad.flatMap(step3, result2);

      Try<String> tryResult = TRY.narrow(result3);
      assertThatTry(tryResult)
          .isSuccess()
          .hasValueSatisfying(v -> assertThat(v).startsWith("Result:"));
    }

    @Test
    @DisplayName("Combining flatMap and handleErrorWith")
    void combiningFlatMapAndHandleErrorWith() {
      Function<String, Kind<TryKind.Witness, Integer>> riskyOperation =
          s -> {
            if (s.isEmpty()) {
              return TRY.widen(Try.failure(new IllegalArgumentException("Empty string")));
            }
            return TRY.widen(Try.success(s.length()));
          };

      Function<Throwable, Kind<TryKind.Witness, Integer>> errorHandler =
          t -> TRY.widen(Try.success(-1));

      // Test with valid input
      Kind<TryKind.Witness, String> validInput = TRY.widen(Try.success("test"));
      Kind<TryKind.Witness, Integer> result1 = monad.flatMap(riskyOperation, validInput);
      Kind<TryKind.Witness, Integer> handled1 = monad.handleErrorWith(result1, errorHandler);

      Try<Integer> tryResult1 = TRY.narrow(handled1);
      assertThatTry(tryResult1).isSuccess().hasValue(4);

      // Test with empty input
      Kind<TryKind.Witness, String> emptyInput = TRY.widen(Try.success(""));
      Kind<TryKind.Witness, Integer> result2 = monad.flatMap(riskyOperation, emptyInput);
      Kind<TryKind.Witness, Integer> handled2 = monad.handleErrorWith(result2, errorHandler);

      Try<Integer> tryResult2 = TRY.narrow(handled2);
      assertThatTry(tryResult2).isSuccess().hasValue(-1);
    }

    @Test
    @DisplayName("MonadError operations preserve type class behaviour")
    void monadErrorOperations_preserveTypeClassBehaviour() {
      // Verify that monad operations work correctly
      Kind<TryKind.Witness, Integer> mapped = monad.map(validMapper, validKind);
      assertThatTry(TRY.narrow(mapped)).isSuccess();

      Kind<TryKind.Witness, Integer> flatMapped = monad.flatMap(validFlatMapper, validKind);
      assertThatTry(TRY.narrow(flatMapped)).isSuccess();

      Kind<TryKind.Witness, Integer> apped = monad.ap(validFunctionKind, validKind);
      assertThatTry(TRY.narrow(apped)).isSuccess();

      // Verify error handling
      RuntimeException exception = new RuntimeException("Error");
      Kind<TryKind.Witness, String> failure = monad.raiseError(exception);
      assertThatTry(TRY.narrow(failure)).isFailure();

      Function<Throwable, Kind<TryKind.Witness, String>> handler = t -> monad.of("recovered");
      Kind<TryKind.Witness, String> recovered = monad.handleErrorWith(failure, handler);
      assertThatTry(TRY.narrow(recovered)).isSuccess();
    }
  }
}
