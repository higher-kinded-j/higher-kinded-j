// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trymonad.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryApplicative Complete Test Suite")
class TryApplicativeTest extends TryTestBase {

  private TryApplicative applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = new TryApplicative();
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Applicative test pattern")
    void runCompleteApplicativePattern() {
      TypeClassTest.<TryKind.Witness>applicative(TryApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(TryFunctor.class)
          .withApFrom(TryApplicative.class)
          .testValidations();
    }

    @Test
    @DisplayName("Verify Applicative operations")
    void verifyApplicativeOperations() {
      TypeClassTest.<TryKind.Witness>applicative(TryApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Verify Applicative validations")
    void verifyApplicativeValidations() {
      TypeClassTest.<TryKind.Witness>applicative(TryApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(TryFunctor.class)
          .withApFrom(TryApplicative.class)
          .withMap2From(Applicative.class)
          .testValidations();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<TryKind.Witness>applicative(TryApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<TryKind.Witness>applicative(TryApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(TryFunctor.class)
          .withApFrom(TryApplicative.class)
          .withMap2From(Applicative.class)
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
      TypeClassTest.<TryKind.Witness>applicative(TryApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }

  @Nested
  @DisplayName("of() Operation Tests")
  class OfOperationTests {

    @Test
    @DisplayName("of() should wrap value in Success")
    void of_shouldWrapValueInSuccess() {
      Kind<TryKind.Witness, String> result = applicative.of("test");
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValue("test");
    }

    @Test
    @DisplayName("of() should allow null value")
    void of_shouldAllowNullValue() {
      Kind<TryKind.Witness, String> result = applicative.of(null);
      Try<String> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValueSatisfying(v -> assertThat(v).isNull());
    }
  }

  @Nested
  @DisplayName("ap() Operation Tests")
  class ApOperationTests {

    @Test
    @DisplayName("ap() with Success function and Success value should apply function")
    void ap_withSuccessAndSuccess_shouldApplyFunction() {
      Function<String, Integer> func = String::length;
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = TRY.widen(Try.success(func));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success("hello"));

      Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValue(5);
    }

    @Test
    @DisplayName("ap() with Failure function should return Failure")
    void ap_withFailureFunction_shouldReturnFailure() {
      RuntimeException exception = new RuntimeException("Function failure");
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = TRY.widen(Try.failure(exception));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success("hello"));

      Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("ap() with Success function and Failure value should return Failure")
    void ap_withSuccessFunctionAndFailureValue_shouldReturnFailure() {
      Function<String, Integer> func = String::length;
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = TRY.widen(Try.success(func));
      RuntimeException exception = new RuntimeException("Value failure");
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.failure(exception));

      Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("ap() with both Failure should return first Failure")
    void ap_withBothFailure_shouldReturnFirstFailure() {
      RuntimeException funcException = new RuntimeException("Function failure");
      Kind<TryKind.Witness, Function<String, Integer>> funcKind =
          TRY.widen(Try.failure(funcException));
      RuntimeException valueException = new RuntimeException("Value failure");
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.failure(valueException));

      Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(funcException);
    }

    @Test
    @DisplayName("ap() should capture exception thrown by function")
    void ap_shouldCaptureExceptionThrownByFunction() {
      RuntimeException funcException = new RuntimeException("Function threw");
      Function<String, Integer> throwingFunc =
          s -> {
            throw funcException;
          };
      Kind<TryKind.Witness, Function<String, Integer>> funcKind =
          TRY.widen(Try.success(throwingFunc));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success("test"));

      Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(funcException);
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("ap() should throw NPE if function Kind is null")
    void ap_shouldThrowNPEIfFunctionKindIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.ap(null, validKind))
          .withMessageContaining("Kind for TryApplicative.ap (function) cannot be null");
    }

    @Test
    @DisplayName("ap() should throw NPE if argument Kind is null")
    void ap_shouldThrowNPEIfArgumentKindIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.ap(validFunctionKind, null))
          .withMessageContaining("Kind for TryApplicative.ap (argument) cannot be null");
    }
  }

  @Nested
  @DisplayName("Exception Propagation Tests")
  class ExceptionPropagationTests {

    @Test
    @DisplayName("ap() should propagate RuntimeException from function application")
    void ap_shouldPropagateRuntimeExceptionFromFunctionApplication() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<String, Integer> throwingFunc =
          s -> {
            throw testException;
          };
      Kind<TryKind.Witness, Function<String, Integer>> funcKind =
          TRY.widen(Try.success(throwingFunc));

      Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, validKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(testException);
    }

    @Test
    @DisplayName("ap() should propagate Error from function application")
    void ap_shouldPropagateErrorFromFunctionApplication() {
      StackOverflowError testError = new StackOverflowError("Stack overflow");
      Function<String, Integer> throwingFunc =
          s -> {
            throw testError;
          };
      Kind<TryKind.Witness, Function<String, Integer>> funcKind =
          TRY.widen(Try.success(throwingFunc));

      Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, validKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isFailure().hasException(testError);
    }
  }

  @Nested
  @DisplayName("Applicative Law Tests")
  class ApplicativeLawTests {

    @Test
    @DisplayName("Identity law: ap(of(id), v) == v")
    void identityLaw() {
      Function<String, String> identity = s -> s;
      Kind<TryKind.Witness, Function<String, String>> idFunc = applicative.of(identity);
      Kind<TryKind.Witness, String> result = applicative.ap(idFunc, validKind);

      assertThat(equalityChecker.test(result, validKind))
          .as("Applicative Identity Law: ap(of(id), v) == v")
          .isTrue();
    }

    @Test
    @DisplayName("Homomorphism law: ap(of(f), of(x)) == of(f(x))")
    void homomorphismLaw() {
      String testVal = "test";
      Function<String, Integer> func = String::length;

      Kind<TryKind.Witness, Function<String, Integer>> funcKind = applicative.of(func);
      Kind<TryKind.Witness, String> valueKind = applicative.of(testVal);

      // Left side: ap(of(f), of(x))
      Kind<TryKind.Witness, Integer> leftSide = applicative.ap(funcKind, valueKind);

      // Right side: of(f(x))
      Kind<TryKind.Witness, Integer> rightSide = applicative.of(func.apply(testVal));

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Applicative Homomorphism Law: ap(of(f), of(x)) == of(f(x))")
          .isTrue();
    }

    @Test
    @DisplayName("Interchange law: ap(u, of(y)) == ap(of(f -> f(y)), u)")
    void interchangeLaw() {
      String testVal = "test";
      Function<String, Integer> func = String::length;
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = applicative.of(func);
      Kind<TryKind.Witness, String> valueKind = applicative.of(testVal);

      // Left side: ap(u, of(y))
      Kind<TryKind.Witness, Integer> leftSide = applicative.ap(funcKind, valueKind);

      // Right side: ap(of(f -> f(y)), u)
      Function<Function<String, Integer>, Integer> applyToValue = f -> f.apply(testVal);
      Kind<TryKind.Witness, Function<Function<String, Integer>, Integer>> applyFunc =
          applicative.of(applyToValue);
      Kind<TryKind.Witness, Integer> rightSide = applicative.ap(applyFunc, funcKind);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Applicative Interchange Law: ap(u, of(y)) == ap(of(f -> f(y)), u)")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("ap() should handle function returning null")
    void ap_shouldHandleFunctionReturningNull() {
      Function<String, Integer> nullFunc = s -> null;
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = TRY.widen(Try.success(nullFunc));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success("test"));

      Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValueSatisfying(v -> assertThat(v).isNull());
    }

    @Test
    @DisplayName("ap() should handle null value")
    void ap_shouldHandleNullValue() {
      Function<String, Integer> safeFunc = s -> s == null ? -1 : s.length();
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = TRY.widen(Try.success(safeFunc));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success(null));

      Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
      Try<Integer> tryResult = TRY.narrow(result);

      assertThatTry(tryResult).isSuccess().hasValue(-1);
    }
  }
}
