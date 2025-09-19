// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.test.HKTTestAssertions.*;
import static org.higherkindedj.hkt.test.HKTTestHelpers.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad Tests")
class EitherMonadTest {

  // Define a simple error type for testing
  record TestError(String code) {}

  private EitherMonad<TestError> eitherMonad;

  // Helper Functions
  private <R> Either<TestError, R> narrow(Kind<EitherKind.Witness<TestError>, R> kind) {
    return EITHER.narrow(kind);
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> right(R value) {
    return EITHER.widen(Either.right(value));
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> left(String errorCode) {
    return EITHER.widen(Either.left(new TestError(errorCode)));
  }

  @BeforeEach
  void setUp() {
    eitherMonad = EitherMonad.instance();
  }

  @Nested
  @DisplayName("Complete Test Suite Using Framework")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete monad test suite")
    void completeMonadTestSuite() {
      // Test data
      Kind<EitherKind.Witness<TestError>, Integer> validKind = right(10);
      Integer testValue = 42;
      Function<Integer, String> validMapper = i -> "mapped:" + i;
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> validFlatMapper =
          i -> right("flat:" + i);
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> validFunctionKind =
          eitherMonad.of(i -> "func:" + i);
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> testFunction =
          i -> right("test:" + i);
      Function<String, Kind<EitherKind.Witness<TestError>, String>> chainFunction =
          s -> right(s + "!");

      // Equality checker for Either
      java.util.function.BiPredicate<
              Kind<EitherKind.Witness<TestError>, ?>, Kind<EitherKind.Witness<TestError>, ?>>
          equalityChecker = (k1, k2) -> narrow(k1).equals(narrow(k2));

      // Run the complete test suite
      runCompleteMonadTestSuite(
          eitherMonad,
          "EitherMonad",
          validKind,
          testValue,
          validMapper,
          validFlatMapper,
          validFunctionKind,
          testFunction,
          chainFunction,
          equalityChecker,
          "Either");
    }
  }

  @Nested
  @DisplayName("Instance Method Coverage")
  class InstanceMethodTests {

    @Test
    @DisplayName("Test EitherMonad.instance() method")
    void testInstanceMethod() {
      // Test that instance() returns a non-null instance
      EitherMonad<TestError> instance1 = EitherMonad.instance();
      EitherMonad<TestError> instance2 = EitherMonad.instance();

      assertThat(instance1).isNotNull();
      assertThat(instance2).isNotNull();

      // Test that it returns the same instance (singleton behavior)
      assertThat(instance1).isSameAs(instance2);

      // Test with different error types to ensure generic flexibility
      EitherMonad<String> stringErrorInstance = EitherMonad.instance();
      EitherMonad<RuntimeException> exceptionInstance = EitherMonad.instance();

      assertThat(stringErrorInstance).isNotNull();
      assertThat(exceptionInstance).isNotNull();
    }
  }

  @Nested
  @DisplayName("Basic Monad Operations - Comprehensive Coverage")
  class BasicOperationsComprehensiveTests {

    @Test
    @DisplayName("of() method comprehensive testing")
    void ofMethodComprehensive() {
      // Test with various values including null
      Kind<EitherKind.Witness<TestError>, String> result1 = eitherMonad.of("success");
      Kind<EitherKind.Witness<TestError>, String> result2 = eitherMonad.of(null);
      Kind<EitherKind.Witness<TestError>, Integer> result3 = eitherMonad.of(42);
      Kind<EitherKind.Witness<TestError>, List<String>> result4 =
          eitherMonad.of(Arrays.asList("a", "b"));

      // All should create Right instances
      assertThat(narrow(result1).isRight()).isTrue();
      assertThat(narrow(result1).getRight()).isEqualTo("success");

      assertThat(narrow(result2).isRight()).isTrue();
      assertThat(narrow(result2).getRight()).isNull();

      assertThat(narrow(result3).isRight()).isTrue();
      assertThat(narrow(result3).getRight()).isEqualTo(42);

      assertThat(narrow(result4).isRight()).isTrue();
      assertThat(narrow(result4).getRight()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("map() comprehensive testing with Right and Left")
    void mapComprehensive() {
      Kind<EitherKind.Witness<TestError>, Integer> rightInput = right(10);
      Kind<EitherKind.Witness<TestError>, Integer> leftInput = left("E1");

      // Test mapping on Right
      Kind<EitherKind.Witness<TestError>, String> rightResult =
          eitherMonad.map(i -> "v" + i, rightInput);
      assertThat(narrow(rightResult).isRight()).isTrue();
      assertThat(narrow(rightResult).getRight()).isEqualTo("v10");

      // Test mapping on Left (should pass through unchanged)
      Kind<EitherKind.Witness<TestError>, String> leftResult =
          eitherMonad.map(i -> "v" + i, leftInput);
      assertThat(narrow(leftResult).isLeft()).isTrue();
      assertThat(narrow(leftResult).getLeft()).isEqualTo(new TestError("E1"));

      // Test with null mapper result
      Kind<EitherKind.Witness<TestError>, String> nullResult =
          eitherMonad.map(i -> null, rightInput);
      assertThat(narrow(nullResult).isRight()).isTrue();
      assertThat(narrow(nullResult).getRight()).isNull();

      // Test standard validations
      testAllMonadNullValidations(
          eitherMonad,
          rightInput,
          Object::toString,
          i -> right("flat" + i),
          eitherMonad.of(Object::toString));
    }

    @Test
    @DisplayName("flatMap() comprehensive testing")
    void flatMapComprehensive() {
      Kind<EitherKind.Witness<TestError>, Integer> rightInput = right(5);
      Kind<EitherKind.Witness<TestError>, Integer> leftInput = left("E1");

      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> successMapper =
          i -> right("success" + i);
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> failMapper =
          i -> left("FAIL" + i);

      // Test flatMap on Right -> Right
      Kind<EitherKind.Witness<TestError>, String> rightToRight =
          eitherMonad.flatMap(successMapper, rightInput);
      assertThat(narrow(rightToRight).isRight()).isTrue();
      assertThat(narrow(rightToRight).getRight()).isEqualTo("success5");

      // Test flatMap on Right -> Left
      Kind<EitherKind.Witness<TestError>, String> rightToLeft =
          eitherMonad.flatMap(failMapper, rightInput);
      assertThat(narrow(rightToLeft).isLeft()).isTrue();
      assertThat(narrow(rightToLeft).getLeft()).isEqualTo(new TestError("FAIL5"));

      // Test flatMap on Left (should pass through)
      Kind<EitherKind.Witness<TestError>, String> leftResult =
          eitherMonad.flatMap(successMapper, leftInput);
      assertThat(narrow(leftResult).isLeft()).isTrue();
      assertThat(narrow(leftResult).getLeft()).isEqualTo(new TestError("E1"));

      // Test chaining multiple flatMaps
      Kind<EitherKind.Witness<TestError>, String> chainResult =
          eitherMonad.flatMap(
              i -> eitherMonad.flatMap(s -> right(s + "!"), right("step" + i)), rightInput);
      assertThat(narrow(chainResult).getRight()).isEqualTo("step5!");
    }

    @Test
    @DisplayName("ap() comprehensive testing - all combinations")
    void apComprehensive() {
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> rightFunc =
          right(i -> "func:" + i);
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> leftFunc = left("FUNC_ERR");
      Kind<EitherKind.Witness<TestError>, Integer> rightVal = right(100);
      Kind<EitherKind.Witness<TestError>, Integer> leftVal = left("VAL_ERR");

      // Right function, Right value
      Kind<EitherKind.Witness<TestError>, String> rightRight = eitherMonad.ap(rightFunc, rightVal);
      assertThat(narrow(rightRight).isRight()).isTrue();
      assertThat(narrow(rightRight).getRight()).isEqualTo("func:100");

      // Right function, Left value
      Kind<EitherKind.Witness<TestError>, String> rightLeft = eitherMonad.ap(rightFunc, leftVal);
      assertThat(narrow(rightLeft).isLeft()).isTrue();
      assertThat(narrow(rightLeft).getLeft()).isEqualTo(new TestError("VAL_ERR"));

      // Left function, Right value
      Kind<EitherKind.Witness<TestError>, String> leftRight = eitherMonad.ap(leftFunc, rightVal);
      assertThat(narrow(leftRight).isLeft()).isTrue();
      assertThat(narrow(leftRight).getLeft()).isEqualTo(new TestError("FUNC_ERR"));

      // Left function, Left value (function error should take precedence)
      Kind<EitherKind.Witness<TestError>, String> leftLeft = eitherMonad.ap(leftFunc, leftVal);
      assertThat(narrow(leftLeft).isLeft()).isTrue();
      assertThat(narrow(leftLeft).getLeft()).isEqualTo(new TestError("FUNC_ERR"));
    }
  }

  @Nested
  @DisplayName("MapN Methods - Complete Coverage")
  class MapNMethodsTests {

    @Test
    @DisplayName("map3() comprehensive testing")
    void map3Comprehensive() {
      // Test all success case
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(1);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      Kind<EitherKind.Witness<TestError>, Double> r3 = right(3.14);

      Function3<Integer, String, Double, String> combineFunction =
          (i, s, d) -> String.format("Result: %d, %s, %.2f", i, s, d);

      Kind<EitherKind.Witness<TestError>, String> allSuccess =
          eitherMonad.map3(r1, r2, r3, combineFunction);
      assertThat(narrow(allSuccess).isRight()).isTrue();
      assertThat(narrow(allSuccess).getRight()).isEqualTo("Result: 1, test, 3.14");

      // Test with first argument Left
      Kind<EitherKind.Witness<TestError>, Integer> l1 = left("E1");
      Kind<EitherKind.Witness<TestError>, String> firstLeft =
          eitherMonad.map3(l1, r2, r3, combineFunction);
      assertThat(narrow(firstLeft).isLeft()).isTrue();
      assertThat(narrow(firstLeft).getLeft()).isEqualTo(new TestError("E1"));

      // Test with second argument Left
      Kind<EitherKind.Witness<TestError>, String> l2 = left("E2");
      Kind<EitherKind.Witness<TestError>, String> secondLeft =
          eitherMonad.map3(r1, l2, r3, combineFunction);
      assertThat(narrow(secondLeft).isLeft()).isTrue();
      assertThat(narrow(secondLeft).getLeft()).isEqualTo(new TestError("E2"));

      // Test with third argument Left
      Kind<EitherKind.Witness<TestError>, Double> l3 = left("E3");
      Kind<EitherKind.Witness<TestError>, String> thirdLeft =
          eitherMonad.map3(r1, r2, l3, combineFunction);
      assertThat(narrow(thirdLeft).isLeft()).isTrue();
      assertThat(narrow(thirdLeft).getLeft()).isEqualTo(new TestError("E3"));

      // Test with multiple Left arguments (first encountered should be returned)
      Kind<EitherKind.Witness<TestError>, String> multipleLeft =
          eitherMonad.map3(l1, l2, l3, combineFunction);
      assertThat(narrow(multipleLeft).isLeft()).isTrue();
      assertThat(narrow(multipleLeft).getLeft()).isEqualTo(new TestError("E1"));

      // Test null validations for map3
      ValidationTestBuilder.create()
          .assertNullKind(
              () -> eitherMonad.map3(null, r2, r3, combineFunction), "Kind faKind for map3")
          .assertNullKind(
              () -> eitherMonad.map3(r1, null, r3, combineFunction), "Kind fbKind for map3")
          .assertNullKind(
              () -> eitherMonad.map3(r1, r2, null, combineFunction), "Kind fcKind for map3")
          .assertNullFunction(() -> eitherMonad.map3(r1, r2, r3, null), "function f for map3")
          .execute();
    }

    @Test
    @DisplayName("map4() comprehensive testing")
    void map4Comprehensive() {
      // Test all success case
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(1);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      Kind<EitherKind.Witness<TestError>, Double> r3 = right(3.14);
      Kind<EitherKind.Witness<TestError>, Boolean> r4 = right(true);

      Function4<Integer, String, Double, Boolean, String> combineFunction =
          (i, s, d, b) -> String.format("Result: %d, %s, %.2f, %b", i, s, d, b);

      Kind<EitherKind.Witness<TestError>, String> allSuccess =
          eitherMonad.map4(r1, r2, r3, r4, combineFunction);
      assertThat(narrow(allSuccess).isRight()).isTrue();
      assertThat(narrow(allSuccess).getRight()).isEqualTo("Result: 1, test, 3.14, true");

      // Test each position with Left
      Kind<EitherKind.Witness<TestError>, Integer> l1 = left("E1");
      Kind<EitherKind.Witness<TestError>, String> l2 = left("E2");
      Kind<EitherKind.Witness<TestError>, Double> l3 = left("E3");
      Kind<EitherKind.Witness<TestError>, Boolean> l4 = left("E4");

      // First Left
      Kind<EitherKind.Witness<TestError>, String> firstLeft =
          eitherMonad.map4(l1, r2, r3, r4, combineFunction);
      assertThat(narrow(firstLeft).getLeft()).isEqualTo(new TestError("E1"));

      // Second Left
      Kind<EitherKind.Witness<TestError>, String> secondLeft =
          eitherMonad.map4(r1, l2, r3, r4, combineFunction);
      assertThat(narrow(secondLeft).getLeft()).isEqualTo(new TestError("E2"));

      // Third Left
      Kind<EitherKind.Witness<TestError>, String> thirdLeft =
          eitherMonad.map4(r1, r2, l3, r4, combineFunction);
      assertThat(narrow(thirdLeft).getLeft()).isEqualTo(new TestError("E3"));

      // Fourth Left
      Kind<EitherKind.Witness<TestError>, String> fourthLeft =
          eitherMonad.map4(r1, r2, r3, l4, combineFunction);
      assertThat(narrow(fourthLeft).getLeft()).isEqualTo(new TestError("E4"));

      // Test null validations for map4
      ValidationTestBuilder.create()
          .assertNullKind(
              () -> eitherMonad.map4(null, r2, r3, r4, combineFunction), "Kind faKind for map4")
          .assertNullKind(
              () -> eitherMonad.map4(r1, null, r3, r4, combineFunction), "Kind fbKind for map4")
          .assertNullKind(
              () -> eitherMonad.map4(r1, r2, null, r4, combineFunction), "Kind fcKind for map4")
          .assertNullKind(
              () -> eitherMonad.map4(r1, r2, r3, null, combineFunction), "Kind fdKind for map4")
          .assertNullFunction(() -> eitherMonad.map4(r1, r2, r3, r4, null), "function f for map4")
          .execute();
    }
  }

  @Nested
  @DisplayName("MonadError Methods - Complete Coverage")
  class MonadErrorMethodsTests {

    @Test
    @DisplayName("raiseError() comprehensive testing")
    void raiseErrorComprehensive() {
      TestError error1 = new TestError("E500");
      TestError error2 = null; // Test null error

      // Test raising non-null error
      Kind<EitherKind.Witness<TestError>, Integer> errorKind1 = eitherMonad.raiseError(error1);
      Either<TestError, Integer> either1 = narrow(errorKind1);
      assertThat(either1.isLeft()).isTrue();
      assertThat(either1.getLeft()).isEqualTo(error1);

      // Test raising null error (should be allowed)
      Kind<EitherKind.Witness<TestError>, Integer> errorKind2 = eitherMonad.raiseError(error2);
      Either<TestError, Integer> either2 = narrow(errorKind2);
      assertThat(either2.isLeft()).isTrue();
      assertThat(either2.getLeft()).isNull();

      // Test with different result types
      Kind<EitherKind.Witness<TestError>, String> errorKindString = eitherMonad.raiseError(error1);
      Kind<EitherKind.Witness<TestError>, List<Integer>> errorKindList =
          eitherMonad.raiseError(error1);

      assertThat(narrow(errorKindString).isLeft()).isTrue();
      assertThat(narrow(errorKindList).isLeft()).isTrue();
    }

    @Test
    @DisplayName("handleErrorWith() comprehensive testing")
    void handleErrorWithComprehensive() {
      Kind<EitherKind.Witness<TestError>, Integer> rightValue = right(100);
      Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("E404");

      // Handler that converts error to Right
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> recoverHandler =
          err -> {
            if (err.code().equals("E404")) {
              return right(-1);
            }
            return right(-999);
          };

      // Handler that converts error to another Left
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> failHandler =
          err -> left("HANDLED_" + err.code());

      // Test handling Right value (should pass through unchanged)
      Kind<EitherKind.Witness<TestError>, Integer> rightResult =
          eitherMonad.handleErrorWith(rightValue, recoverHandler);
      assertThat(rightResult).isSameAs(rightValue);
      assertThat(narrow(rightResult).getRight()).isEqualTo(100);

      // Test handling Left value with recovery to Right
      Kind<EitherKind.Witness<TestError>, Integer> recoveredResult =
          eitherMonad.handleErrorWith(leftValue, recoverHandler);
      assertThat(narrow(recoveredResult).isRight()).isTrue();
      assertThat(narrow(recoveredResult).getRight()).isEqualTo(-1);

      // Test handling Left value with conversion to another Left
      Kind<EitherKind.Witness<TestError>, Integer> rehandledResult =
          eitherMonad.handleErrorWith(leftValue, failHandler);
      assertThat(narrow(rehandledResult).isLeft()).isTrue();
      assertThat(narrow(rehandledResult).getLeft()).isEqualTo(new TestError("HANDLED_E404"));

      // Test handling with null error
      Kind<EitherKind.Witness<TestError>, Integer> nullErrorValue = eitherMonad.raiseError(null);
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> nullHandler =
          err -> right(err == null ? 0 : -1);

      Kind<EitherKind.Witness<TestError>, Integer> nullHandledResult =
          eitherMonad.handleErrorWith(nullErrorValue, nullHandler);
      assertThat(narrow(nullHandledResult).getRight()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("Exception and Error Handling Coverage")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("Test exception propagation in all operations")
    void testExceptionPropagationComplete() {
      Kind<EitherKind.Witness<TestError>, Integer> validInput = right(20);
      RuntimeException testException = createTestException("EitherMonad test");

      // Test map exception propagation
      Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };
      assertThatThrownBy(() -> eitherMonad.map(throwingMapper, validInput)).isSameAs(testException);

      // Test flatMap exception propagation
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };
      assertThatThrownBy(() -> eitherMonad.flatMap(throwingFlatMapper, validInput))
          .isSameAs(testException);

      // Test ap function application exception
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> throwingFunction =
          right(
              i -> {
                throw testException;
              });
      assertThatThrownBy(() -> eitherMonad.ap(throwingFunction, validInput))
          .isSameAs(testException);

      // Test handleErrorWith handler exception
      Kind<EitherKind.Witness<TestError>, Integer> leftInput = left("E1");
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> throwingHandler =
          err -> {
            throw testException;
          };
      assertThatThrownBy(() -> eitherMonad.handleErrorWith(leftInput, throwingHandler))
          .isSameAs(testException);

      // Test map3 function exception
      Function3<Integer, String, Double, String> throwingMap3Function =
          (i, s, d) -> {
            throw testException;
          };
      assertThatThrownBy(
              () -> eitherMonad.map3(right(1), right("test"), right(1.0), throwingMap3Function))
          .isSameAs(testException);

      // Test map4 function exception
      Function4<Integer, String, Double, Boolean, String> throwingMap4Function =
          (i, s, d, b) -> {
            throw testException;
          };
      assertThatThrownBy(
              () ->
                  eitherMonad.map4(
                      right(1), right("test"), right(1.0), right(true), throwingMap4Function))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Test null Kind validation in flatMap and handleErrorWith")
    void testNullKindValidation() {
      Kind<EitherKind.Witness<TestError>, Integer> validInput = right(10);
      Kind<EitherKind.Witness<TestError>, Integer> leftInput = left("E1");

      // Test flatMap with null-returning function
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> nullReturningMapper =
          i -> null;
      assertNullKindNarrowThrows(
          () -> eitherMonad.flatMap(nullReturningMapper, validInput), "Either");

      // Test handleErrorWith with null-returning handler
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> nullReturningHandler =
          err -> null;
      Kind<EitherKind.Witness<TestError>, Integer> result =
          eitherMonad.handleErrorWith(leftInput, nullReturningHandler);
      assertThat(result).isNull();
      assertNullKindNarrowThrows(() -> EITHER.narrow(result), "Either");
    }
  }

  @Nested
  @DisplayName("Comprehensive Null Parameter Validation")
  class NullParameterValidationTests {

    @Test
    @DisplayName("Test all null parameter validations comprehensively")
    void testAllNullParameterValidations() {
      Kind<EitherKind.Witness<TestError>, Integer> validKind = right(10);
      Function<Integer, String> validMapper = Object::toString;
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> validFlatMapper =
          i -> right("v" + i);
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> validFunctionKind =
          eitherMonad.of(validMapper);
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> validHandler =
          err -> right(-1);

      ValidationTestBuilder.create()
          // Basic operations
          .assertNullFunction(() -> eitherMonad.map(null, validKind), "function f for map")
          .assertNullKind(() -> eitherMonad.map(validMapper, null), "source Kind for map")
          .assertNullFunction(() -> eitherMonad.flatMap(null, validKind), "function f for flatMap")
          .assertNullKind(
              () -> eitherMonad.flatMap(validFlatMapper, null), "source Kind for flatMap")
          .assertNullKind(() -> eitherMonad.ap(null, validKind), "function Kind for ap")
          .assertNullKind(() -> eitherMonad.ap(validFunctionKind, null), "argument Kind for ap")

          // MonadError operations
          .assertNullKind(
              () -> eitherMonad.handleErrorWith(null, validHandler),
              "source Kind for handleErrorWith")
          .assertNullFunction(
              () -> eitherMonad.handleErrorWith(validKind, null),
              "handler function for handleErrorWith")

          // map3 operations
          .assertNullKind(
              () -> eitherMonad.map3(null, validKind, validKind, (a, b, c) -> "test"),
              "Kind faKind for map3")
          .assertNullKind(
              () -> eitherMonad.map3(validKind, null, validKind, (a, b, c) -> "test"),
              "Kind fbKind for map3")
          .assertNullKind(
              () -> eitherMonad.map3(validKind, validKind, null, (a, b, c) -> "test"),
              "Kind fcKind for map3")
          .assertNullFunction(
              () -> eitherMonad.map3(validKind, validKind, validKind, null), "function f for map3")

          // map4 operations
          .assertNullKind(
              () -> eitherMonad.map4(null, validKind, validKind, validKind, (a, b, c, d) -> "test"),
              "Kind faKind for map4")
          .assertNullKind(
              () -> eitherMonad.map4(validKind, null, validKind, validKind, (a, b, c, d) -> "test"),
              "Kind fbKind for map4")
          .assertNullKind(
              () -> eitherMonad.map4(validKind, validKind, null, validKind, (a, b, c, d) -> "test"),
              "Kind fcKind for map4")
          .assertNullKind(
              () -> eitherMonad.map4(validKind, validKind, validKind, null, (a, b, c, d) -> "test"),
              "Kind fdKind for map4")
          .assertNullFunction(
              () -> eitherMonad.map4(validKind, validKind, validKind, validKind, null),
              "function f for map4")
          .execute();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Corner Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Test operations with null values in Either")
    void testOperationsWithNullValues() {
      // Test map with null value in Right
      Kind<EitherKind.Witness<TestError>, String> rightNull = right(null);
      Kind<EitherKind.Witness<TestError>, String> mappedNull =
          eitherMonad.map(s -> s == null ? "was null" : s, rightNull);
      assertThat(narrow(mappedNull).getRight()).isEqualTo("was null");

      // Test flatMap with null value in Right
      Kind<EitherKind.Witness<TestError>, String> flatMappedNull =
          eitherMonad.flatMap(s -> right(s == null ? "flat was null" : s), rightNull);
      assertThat(narrow(flatMappedNull).getRight()).isEqualTo("flat was null");

      // Test ap with null value
      Kind<EitherKind.Witness<TestError>, Function<String, String>> funcKind =
          right(s -> s == null ? "func null" : s);
      Kind<EitherKind.Witness<TestError>, String> apNull = eitherMonad.ap(funcKind, rightNull);
      assertThat(narrow(apNull).getRight()).isEqualTo("func null");

      // Test Left with null error
      Kind<EitherKind.Witness<TestError>, String> leftNull = eitherMonad.raiseError(null);
      Kind<EitherKind.Witness<TestError>, String> handledNull =
          eitherMonad.handleErrorWith(
              leftNull,
              err -> right(err == null ? "handled null error" : "handled: " + err.code()));
      assertThat(narrow(handledNull).getRight()).isEqualTo("handled null error");
    }

    @Test
    @DisplayName("Test complex nested operations")
    void testComplexNestedOperations() {
      // Complex chaining of operations
      Kind<EitherKind.Witness<TestError>, Integer> start = right(5);

      Kind<EitherKind.Witness<TestError>, String> complexResult =
          eitherMonad.flatMap(
              i ->
                  eitherMonad.flatMap(
                      s ->
                          eitherMonad.map(
                              x -> x + "!", eitherMonad.map(y -> y + " processed", right(s))),
                      right("step" + i)),
              start);

      assertThat(narrow(complexResult).getRight()).isEqualTo("step5 processed!");

      // Complex operation with early Left
      Kind<EitherKind.Witness<TestError>, Integer> failStart = left("FAIL");

      Kind<EitherKind.Witness<TestError>, String> failResult =
          eitherMonad.flatMap(i -> eitherMonad.map(s -> s + "!", right("step" + i)), failStart);

      assertThat(narrow(failResult).isLeft()).isTrue();
      assertThat(narrow(failResult).getLeft()).isEqualTo(new TestError("FAIL"));
    }

    @Test
    @DisplayName("Test error recovery patterns")
    void testErrorRecoveryPatterns() {
      Kind<EitherKind.Witness<TestError>, Integer> errorValue = left("RECOVERABLE");

      // Recovery with transformation
      Kind<EitherKind.Witness<TestError>, Integer> recovered =
          eitherMonad.handleErrorWith(
              errorValue,
              err -> {
                if (err.code().equals("RECOVERABLE")) {
                  return right(999);
                }
                return left("UNRECOVERABLE");
              });

      assertThat(narrow(recovered).getRight()).isEqualTo(999);

      // Recovery with another error
      Kind<EitherKind.Witness<TestError>, Integer> reErrored =
          eitherMonad.handleErrorWith(errorValue, err -> left("NEW_ERROR"));

      assertThat(narrow(reErrored).getLeft()).isEqualTo(new TestError("NEW_ERROR"));

      // Chained error handling
      Kind<EitherKind.Witness<TestError>, Integer> chainedHandling =
          eitherMonad.handleErrorWith(
              eitherMonad.handleErrorWith(errorValue, err -> left("FIRST_HANDLER")),
              err -> right(-1));

      assertThat(narrow(chainedHandling).getRight()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Test map3 and map4 with mixed success and failure")
    void testMapNWithMixedResults() {
      // Test map3 with various combinations
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(1);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      Kind<EitherKind.Witness<TestError>, Double> l3 = left("E3");

      Function3<Integer, String, Double, String> func3 =
          (i, s, d) -> String.format("%d-%s-%.1f", i, s, d);

      // Should fail at third argument
      Kind<EitherKind.Witness<TestError>, String> map3Result = eitherMonad.map3(r1, r2, l3, func3);
      assertThat(narrow(map3Result).getLeft()).isEqualTo(new TestError("E3"));

      // Test map4 with early failure
      Kind<EitherKind.Witness<TestError>, Boolean> l4 = left("E4");
      Function4<Integer, String, Double, Boolean, String> func4 = (i, s, d, b) -> "combined";

      Kind<EitherKind.Witness<TestError>, String> map4Result =
          eitherMonad.map4(r1, r2, l3, l4, func4);
      assertThat(narrow(map4Result).getLeft())
          .isEqualTo(new TestError("E3")); // First error should be returned

      // Test map3 with all failures (first should be returned)
      Kind<EitherKind.Witness<TestError>, Integer> l1 = left("E1");
      Kind<EitherKind.Witness<TestError>, String> l2 = left("E2");

      Kind<EitherKind.Witness<TestError>, String> allFailMap3 = eitherMonad.map3(l1, l2, l3, func3);
      assertThat(narrow(allFailMap3).getLeft()).isEqualTo(new TestError("E1"));
    }

    @Test
    @DisplayName("Test type inference and generic behavior")
    void testTypeInferenceAndGenerics() {
      // Test with different generic types
      Kind<EitherKind.Witness<TestError>, List<String>> listRight = right(Arrays.asList("a", "b"));
      Kind<EitherKind.Witness<TestError>, java.util.Map<String, Integer>> mapResult =
          eitherMonad.map(
              list -> {
                java.util.Map<String, Integer> map = new java.util.HashMap<>();
                for (int i = 0; i < list.size(); i++) {
                  map.put(list.get(i), i);
                }
                return map;
              },
              listRight);

      assertThat(narrow(mapResult).isRight()).isTrue();
      assertThat(narrow(mapResult).getRight()).containsEntry("a", 0).containsEntry("b", 1);

      // Test with nested Either types
      Kind<EitherKind.Witness<TestError>, Either<String, Integer>> nestedRight =
          right(Either.right(42));
      Kind<EitherKind.Witness<TestError>, String> unnested =
          eitherMonad.map(
              either -> either.fold(err -> "nested error: " + err, val -> "nested value: " + val),
              nestedRight);

      assertThat(narrow(unnested).getRight()).isEqualTo("nested value: 42");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Test operations with large number of chained calls")
    void testLargeChainedOperations() {
      Kind<EitherKind.Witness<TestError>, Integer> start = right(0);

      // Chain many map operations
      Kind<EitherKind.Witness<TestError>, Integer> chainedMaps = start;
      for (int i = 0; i < 100; i++) {
        final int increment = i;
        chainedMaps = eitherMonad.map(x -> x + increment, chainedMaps);
      }

      // Sum should be 0 + 0 + 1 + 2 + ... + 99 = 4950
      int expectedSum = (99 * 100) / 2;
      assertThat(narrow(chainedMaps).getRight()).isEqualTo(expectedSum);

      // Chain many flatMap operations
      Kind<EitherKind.Witness<TestError>, Integer> chainedFlatMaps = right(1);
      for (int i = 0; i < 10; i++) {
        chainedFlatMaps = eitherMonad.flatMap(x -> right(x * 2), chainedFlatMaps);
      }

      // Result should be 1 * 2^10 = 1024
      assertThat(narrow(chainedFlatMaps).getRight()).isEqualTo(1024);
    }

    @Test
    @DisplayName("Test memory usage with large data structures")
    void testLargeDataStructures() {
      // Create a large list
      List<String> largeList = new java.util.ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        largeList.add("item" + i);
      }

      Kind<EitherKind.Witness<TestError>, List<String>> largeListKind = right(largeList);

      // Transform the large list
      Kind<EitherKind.Witness<TestError>, List<String>> transformed =
          eitherMonad.map(
              list ->
                  list.stream()
                      .map(s -> s.toUpperCase())
                      .collect(java.util.stream.Collectors.toList()),
              largeListKind);

      assertThat(narrow(transformed).getRight()).hasSize(1000);
      assertThat(narrow(transformed).getRight().get(0)).isEqualTo("ITEM0");
      assertThat(narrow(transformed).getRight().get(999)).isEqualTo("ITEM999");
    }
  }

  @Nested
  @DisplayName("Integration with Common Test Functions")
  class IntegrationTests {

    @Test
    @DisplayName("Test using common test functions and utilities")
    void testWithCommonFunctions() {
      Kind<EitherKind.Witness<TestError>, Integer> validInput = right(100);

      // Test with common functions from HKTTestHelpers
      Kind<EitherKind.Witness<TestError>, String> result1 =
          eitherMonad.map(CommonTestFunctions.INT_TO_STRING, validInput);
      assertThat(narrow(result1).getRight()).isEqualTo("100");

      Kind<EitherKind.Witness<TestError>, String> result2 =
          eitherMonad.map(CommonTestFunctions.APPEND_SUFFIX, result1);
      assertThat(narrow(result2).getRight()).isEqualTo("100_test");

      Kind<EitherKind.Witness<TestError>, Integer> result3 =
          eitherMonad.map(CommonTestFunctions.MULTIPLY_BY_2, validInput);
      assertThat(narrow(result3).getRight()).isEqualTo(200);

      // Test exception propagation with common test exception
      RuntimeException testException = createTestException("integration test");
      Function<Integer, String> throwingFunction =
          CommonTestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> eitherMonad.map(throwingFunction, validInput))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Test combined with other testing utilities")
    void testCombinedWithOtherUtilities() {
      // Use both individual and complete testing approaches
      Kind<EitherKind.Witness<TestError>, Integer> validKind = right(42);
      Function<Integer, String> validMapper = i -> "value:" + i;
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> validFlatMapper =
          i -> right("flat:" + i);
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> validFunctionKind =
          eitherMonad.of(validMapper);

      // First test individual components
      testBasicMonadOperations(
          eitherMonad, validKind, validMapper, validFlatMapper, validFunctionKind);

      // Then test null validations
      testAllMonadNullValidations(
          eitherMonad, validKind, validMapper, validFlatMapper, validFunctionKind);

      // Test exception propagation
      RuntimeException testException = createTestException("combined test");
      testMonadExceptionPropagation(eitherMonad, validKind, testException);

      // Test flatMap null Kind validation
      testFlatMapNullKindValidation(eitherMonad, validKind, "Either");

      // Finally, test specific EitherMonad behavior
      assertThat(narrow(eitherMonad.map(validMapper, validKind)).getRight()).isEqualTo("value:42");
    }
  }

  @Nested
  @DisplayName("Comprehensive Monad Laws with Error Handling")
  class ComprehensiveMonadLawsTests {

    @Test
    @DisplayName("Monad laws with comprehensive error validation")
    void monadLawsWithErrorValidation() {
      Integer testValue = 30;
      Kind<EitherKind.Witness<TestError>, Integer> testKind = right(15);
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> testFunction =
          i -> right("law:" + i);
      Function<String, Kind<EitherKind.Witness<TestError>, String>> chainFunction =
          s -> right(s + "!");

      java.util.function.BiPredicate<
              Kind<EitherKind.Witness<TestError>, ?>, Kind<EitherKind.Witness<TestError>, ?>>
          equalityChecker = (k1, k2) -> narrow(k1).equals(narrow(k2));

      // Test all three monad laws with error validation
      testLeftIdentityLaw(
          eitherMonad, testValue, testFunction, (k1, k2) -> equalityChecker.test(k1, k2));
      testRightIdentityLaw(eitherMonad, testKind, (k1, k2) -> equalityChecker.test(k1, k2));
      testAssociativityLaw(
          eitherMonad,
          testKind,
          testFunction,
          chainFunction,
          (k1, k2) -> equalityChecker.test(k1, k2));
    }

    @Test
    @DisplayName("Test monad laws with Left values")
    void monadLawsWithLeftValues() {
      Kind<EitherKind.Witness<TestError>, Integer> leftKind = left("L1");
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> testFunction =
          i -> right("f:" + i);
      Function<Integer, Kind<EitherKind.Witness<TestError>, Integer>> ofFunction = eitherMonad::of;

      // Right identity law with Left value: flatMap(m, of) == m
      Kind<EitherKind.Witness<TestError>, Integer> rightIdentityResult =
          eitherMonad.flatMap(ofFunction, leftKind);
      assertThat(narrow(rightIdentityResult)).isEqualTo(narrow(leftKind));

      // Left values should pass through flatMap unchanged
      Kind<EitherKind.Witness<TestError>, String> flatMapLeftResult =
          eitherMonad.flatMap(testFunction, leftKind);
      assertThat(narrow(flatMapLeftResult).isLeft()).isTrue();
      assertThat(narrow(flatMapLeftResult).getLeft()).isEqualTo(new TestError("L1"));
    }
  }
}
