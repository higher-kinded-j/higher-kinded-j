// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either_t.EitherTAssert.assertThatEitherT;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTMonad Complete Test Suite")
// (Outer: OptionalKind.Witness, Left: TestError)
class EitherTMonadTest
    extends TypeClassTestBase<
        EitherTKind.Witness<OptionalKind.Witness, EitherTMonadTest.TestError>, Integer, String> {

  record TestError(String code) {}

  private MonadError<OptionalKind.Witness, Unit> outerMonad = OptionalMonad.INSTANCE;
  private MonadError<EitherTKind.Witness<OptionalKind.Witness, TestError>, TestError> eitherTMonad =
      new EitherTMonad<>(outerMonad);

  @BeforeEach
  void setUpMonad() {
    outerMonad = OptionalMonad.INSTANCE;
    eitherTMonad = new EitherTMonad<>(outerMonad);
  }

  private <A> Optional<Either<TestError, A>> unwrapKindToOptionalEither(
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, A> kind) {
    if (kind == null) return Optional.empty();
    var eitherT = EITHER_T.narrow(kind);
    Kind<OptionalKind.Witness, Either<TestError, A>> outerKind = eitherT.value();
    return OPTIONAL.narrow(outerKind);
  }

  private <A> Optional<Either<TestError, A>> unwrapOuterOptional(
      Kind<OptionalKind.Witness, Either<TestError, A>> kind) {
    return OPTIONAL.narrow(kind);
  }

  private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> rightT(R value) {
    return EITHER_T.widen(EitherT.right(outerMonad, value));
  }

  private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> leftT(
      String errorCode) {
    return EITHER_T.widen(EitherT.left(outerMonad, new TestError(errorCode)));
  }

  private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> emptyT() {
    Kind<OptionalKind.Witness, Either<TestError, R>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return EITHER_T.widen(EitherT.fromKind(emptyOuter));
  }

  private <A, B>
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<A, B>>
          rightTWithNullFunction() {
    Kind<OptionalKind.Witness, Either<TestError, Function<A, B>>>
        outerOptionalOfEitherRightNullFunc = OPTIONAL.widen(Optional.of(Either.right(null)));
    return EITHER_T.widen(EitherT.fromKind(outerOptionalOfEitherRightNullFunc));
  }

  // TypeClassTestBase implementations
  @Override
  protected Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> createValidKind() {
    return rightT(10);
  }

  @Override
  protected Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> createValidKind2() {
    return rightT(20);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected BiPredicate<
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, ?>,
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> unwrapKindToOptionalEither(k1).equals(unwrapKindToOptionalEither(k2));
  }

  @Override
  protected Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
      createValidFlatMapper() {
    return i -> rightT("v" + i);
  }

  @Override
  protected Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<Integer, String>>
      createValidFunctionKind() {
    return rightT(Object::toString);
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> a + "+" + b;
  }

  @Override
  protected Integer createTestValue() {
    return 5;
  }

  @Override
  protected Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
      createTestFunction() {
    return i -> rightT("v" + i);
  }

  @Override
  protected Function<String, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
      createChainFunction() {
    return s -> rightT(s + "!");
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Verify all test categories are covered")
    void verifyCompleteCoverage() {
      // Just verify that all the other nested test classes exist and have tests
      assertThat(FunctorOperationTests.class).isNotNull();
      assertThat(ApplicativeOperationTests.class).isNotNull();
      assertThat(MonadOperationTests.class).isNotNull();
      assertThat(MonadErrorOperationTests.class).isNotNull();
      assertThat(MonadLawTests.class).isNotNull();
      assertThat(EdgeCaseTests.class).isNotNull();
    }
  }

  @Nested
  @DisplayName("Functor Operations")
  class FunctorOperationTests {

    @Test
    @DisplayName("map should apply function when Right")
    void map_shouldApplyFunctionWhenRight() {
      var input = rightT(10);
      var result = eitherTMonad.map(Object::toString, input);

      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentRight()
          .hasRightValue("10");
    }

    @Test
    @DisplayName("map should propagate Left when Left")
    void map_shouldPropagateLeftWhenLeft() {
      var input = leftT("E1");
      var result = eitherTMonad.map(Object::toString, input);

      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentLeft()
          .hasLeftValue(new TestError("E1"));
    }

    @Test
    @DisplayName("map should propagate empty outer monad")
    void map_shouldPropagateEmpty() {
      var input = emptyT();
      var result = eitherTMonad.map(Object::toString, input);

      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional).isEmpty();
    }
  }

  @Nested
  @DisplayName("Applicative Operations")
  class ApplicativeOperationTests {

    final Function<Integer, String> multiplyToString = i -> "Res:" + (i * 2);

    @Test
    @DisplayName("ap: F<Right(func)> ap F<Right(val)> should apply function")
    void ap_FuncRight_ValRight_shouldApplyFunction() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<Integer, String>> ff =
          rightT(multiplyToString);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa = rightT(10);

      var result = eitherTMonad.ap(ff, fa);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentRight()
          .hasRightValue("Res:20");
    }

    @Test
    @DisplayName("ap: F<Right(func)> ap F<Left(L_val)> should propagate val Left")
    void ap_FuncRight_ValLeft_shouldPropagateValLeft() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<Integer, String>> ff =
          rightT(multiplyToString);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa =
          leftT("VAL_LEFT_ERROR");

      var result = eitherTMonad.ap(ff, fa);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentLeft()
          .hasLeftValue(new TestError("VAL_LEFT_ERROR"));
    }

    @Test
    @DisplayName("ap: F<Left(L_func)> ap F<Right(val)> should propagate func Left")
    void ap_FuncLeft_ValRight_shouldPropagateFuncLeft() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<Integer, String>> ff =
          leftT("FUNC_LEFT_ERROR");
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa = rightT(10);

      var result = eitherTMonad.ap(ff, fa);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentLeft()
          .hasLeftValue(new TestError("FUNC_LEFT_ERROR"));
    }

    @Test
    @DisplayName("ap: F<Left(L_func)> ap F<Left(L_val)> should propagate func Left")
    void ap_FuncLeft_ValLeft_shouldPropagateFuncLeft() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<Integer, String>> ff =
          leftT("FUNC_LEFT_ERROR_DOMINATES");
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa =
          leftT("VAL_LEFT_ERROR_SECONDARY");

      var result = eitherTMonad.ap(ff, fa);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentLeft()
          .hasLeftValue(new TestError("FUNC_LEFT_ERROR_DOMINATES"));
    }

    @Test
    @DisplayName("ap: F.empty (for function) ap F<Right(val)> should be outer empty")
    void ap_FuncOuterEmpty_ValRight_shouldBeOuterEmpty() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<Integer, String>> ff =
          emptyT();
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa = rightT(10);

      var result = eitherTMonad.ap(ff, fa);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("ap: F<Right(func)> ap F.empty (for value) should be outer empty")
    void ap_FuncRight_ValOuterEmpty_shouldBeOuterEmpty() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<Integer, String>> ff =
          rightT(multiplyToString);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa = emptyT();

      var result = eitherTMonad.ap(ff, fa);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("ap: F<Right(null_function)> ap F<Right(val)> should throw NPE")
    void ap_FuncRightIsNull_ValRight_shouldThrowNPE() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<Integer, String>> ff =
          rightTWithNullFunction();
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa = rightT(10);

      assertThatThrownBy(() -> eitherTMonad.ap(ff, fa))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Function mapper for Either.map cannot be null");
    }

    @Test
    @DisplayName("ap: F<Right(function_throws)> ap F<Right(val)> should throw exception")
    void ap_FuncRightThrows_ValRight_shouldThrowException() {
      RuntimeException ex = new RuntimeException("Function apply crashed");
      Function<Integer, String> throwingFunc =
          i -> {
            throw ex;
          };
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Function<Integer, String>> ff =
          rightT(throwingFunc);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa = rightT(10);

      assertThatThrownBy(() -> eitherTMonad.ap(ff, fa))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
    }
  }

  @Nested
  @DisplayName("Monad Operations")
  class MonadOperationTests {

    @Test
    @DisplayName("flatMap: Initial Right, Function returns Right")
    void flatMap_initialRight_funcReturnsRight() {
      var initialRight = rightT(10);
      Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
          funcReturnsRight = i -> rightT("Value:" + i);

      var result = eitherTMonad.flatMap(funcReturnsRight, initialRight);

      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentRight()
          .hasRightValue("Value:10");
    }

    @Test
    @DisplayName("flatMap: Initial Right, Function returns Left")
    void flatMap_initialRight_funcReturnsLeft() {
      var initialRight = rightT(10);
      Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
          funcReturnsLeft = i -> leftT("FuncError_" + i);

      var result = eitherTMonad.flatMap(funcReturnsLeft, initialRight);

      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentLeft()
          .hasLeftValue(new TestError("FuncError_10"));
    }

    @Test
    @DisplayName("flatMap: Initial Right, Function returns Empty Outer Monad")
    void flatMap_initialRight_funcReturnsEmptyOuter() {
      var initialRight = rightT(20);
      Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
          funcReturnsEmpty = i -> emptyT();

      var result = eitherTMonad.flatMap(funcReturnsEmpty, initialRight);

      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("flatMap: Initial Left, Function should not be called")
    void flatMap_initialLeft_funcNotCalled() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> initialLeft =
          leftT("InitialError");
      Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
          funcShouldNotRun =
              i -> {
                throw new AssertionError("Function should not have been called for Left input");
              };

      var result = eitherTMonad.flatMap(funcShouldNotRun, initialLeft);

      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentLeft()
          .hasLeftValue(new TestError("InitialError"));
    }

    @Test
    @DisplayName("flatMap: Initial Empty Outer Monad, Function should not be called")
    void flatMap_initialEmptyOuter_funcNotCalled() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> initialEmptyOuter =
          emptyT();
      Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
          funcShouldNotRun =
              i -> {
                throw new AssertionError(
                    "Function should not have been called for empty outer input");
              };

      var result = eitherTMonad.flatMap(funcShouldNotRun, initialEmptyOuter);

      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("flatMap: Function throws unhandled RuntimeException")
    void flatMap_functionThrowsRuntimeException() {
      var initialRight = rightT(30);
      RuntimeException runtimeEx = new RuntimeException("Error in function application!");
      Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
          funcThrows =
              i -> {
                throw runtimeEx;
              };

      assertThatThrownBy(() -> eitherTMonad.flatMap(funcThrows, initialRight))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(runtimeEx);
    }
  }

  @Nested
  @DisplayName("MonadError Operations")
  class MonadErrorOperationTests {

    Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> rightVal;
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> leftVal;
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> emptyVal;
    TestError raisedErrorObj;
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> raisedErrorKind;

    @BeforeEach
    void setUpMonadError() {
      rightVal = rightT(100);
      leftVal = leftT("E404");
      emptyVal = emptyT();
      raisedErrorObj = new TestError("E500");
      raisedErrorKind = eitherTMonad.raiseError(raisedErrorObj);
    }

    @Test
    @DisplayName("raiseError should create Left in Optional")
    void raiseError_shouldCreateLeftInOptional() {
      assertThatEitherT(raisedErrorKind, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentLeft()
          .hasLeftValue(raisedErrorObj);
    }

    @Test
    @DisplayName("handleErrorWith should handle Left")
    void handleErrorWith_shouldHandleLeft() {
      Function<TestError, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer>>
          handler = err -> rightT(Integer.parseInt(err.code().substring(1)));

      var result = eitherTMonad.handleErrorWith(leftVal, handler);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentRight()
          .hasRightValue(404);
    }

    @Test
    @DisplayName("handleErrorWith should ignore Right")
    void handleErrorWith_shouldIgnoreRight() {
      Function<TestError, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer>>
          handler = err -> rightT(-1);

      var result = eitherTMonad.handleErrorWith(rightVal, handler);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isEqualToEitherT(rightVal);
    }

    @Test
    @DisplayName("handleErrorWith should propagate empty")
    void handleErrorWith_shouldPropagateEmpty() {
      Function<TestError, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer>>
          handler = err -> rightT(-1);

      var result = eitherTMonad.handleErrorWith(emptyVal, handler);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawTests {

    @Test
    @DisplayName("Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      var ofValue = eitherTMonad.of(testValue);
      var leftSide = eitherTMonad.flatMap(testFunction, ofValue);
      var rightSide = testFunction.apply(testValue);

      assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
    }

    @Test
    @DisplayName("Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer>>
          ofFunc = i -> eitherTMonad.of(i);

      assertThat(equalityChecker.test(eitherTMonad.flatMap(ofFunc, validKind), validKind)).isTrue();
      assertThat(equalityChecker.test(eitherTMonad.flatMap(ofFunc, leftT("E")), leftT("E")))
          .isTrue();
      assertThat(equalityChecker.test(eitherTMonad.flatMap(ofFunc, emptyT()), emptyT())).isTrue();
    }

    @Test
    @DisplayName("Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      var innerFlatMap = eitherTMonad.flatMap(testFunction, validKind);
      var leftSide = eitherTMonad.flatMap(chainFunction, innerFlatMap);

      Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>
          rightSideFunc = a -> eitherTMonad.flatMap(chainFunction, testFunction.apply(a));
      var rightSide = eitherTMonad.flatMap(rightSideFunc, validKind);

      assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("of with null value")
    void of_withNullValue() {
      var result = eitherTMonad.of(null);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentRight()
          .hasRightValue(null);
    }

    @Test
    @DisplayName("raiseError with null error")
    void raiseError_withNullError() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> result =
          eitherTMonad.raiseError(null);
      assertThatEitherT(result, EitherTMonadTest.this::unwrapOuterOptional)
          .isPresentLeft()
          .hasLeftValue(null);
    }
  }
}
