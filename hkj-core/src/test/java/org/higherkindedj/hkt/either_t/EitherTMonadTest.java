// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_KIND_TEMPLATE;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.unit.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTMonad Tests (Outer: OptionalKind.Witness, Left: TestErrorEitherTMonad)")
class EitherTMonadTest {
  record TestErrorEitherTMonad(String code) {}

  private MonadError<OptionalKind.Witness, Unit> outerMonad;
  private MonadError<
          EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, TestErrorEitherTMonad>
      eitherTMonad;

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;
    eitherTMonad = new EitherTMonad<>(outerMonad);
  }

  private <A> Optional<Either<TestErrorEitherTMonad, A>> unwrapKindToOptionalEither(
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, A> kind) {
    if (kind == null) return Optional.empty();
    var eitherT = EITHER_T.narrow(kind);
    Kind<OptionalKind.Witness, Either<TestErrorEitherTMonad, A>> outerKind = eitherT.value();
    return OPTIONAL.narrow(outerKind);
  }

  private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, R> rightT(
      R value) {
    return EITHER_T.widen(EitherT.right(outerMonad, value));
  }

  private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, R> leftT(
      String errorCode) {
    return EITHER_T.widen(EitherT.left(outerMonad, new TestErrorEitherTMonad(errorCode)));
  }

  private <R>
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, R> emptyOuterT() {
    Kind<OptionalKind.Witness, Either<TestErrorEitherTMonad, R>> emptyOuter =
        OPTIONAL.widen(Optional.empty());
    return EITHER_T.widen(EitherT.fromKind(emptyOuter));
  }

  private <A, B>
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Function<A, B>>
          rightTWithNullFunction() {
    Kind<OptionalKind.Witness, Either<TestErrorEitherTMonad, Function<A, B>>>
        outerOptionalOfEitherRightNullFunc = OPTIONAL.widen(Optional.of(Either.right(null)));
    return EITHER_T.widen(EitherT.fromKind(outerOptionalOfEitherRightNullFunc));
  }

  private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, R> emptyT() {
    Kind<OptionalKind.Witness, Either<TestErrorEitherTMonad, R>> emptyOuter =
        OPTIONAL.widen(Optional.empty());
    EitherT<OptionalKind.Witness, TestErrorEitherTMonad, R> concreteEitherT =
        EitherT.fromKind(emptyOuter);
    return EITHER_T.widen(concreteEitherT);
  }

  // Functions for laws and tests
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  private final Function<
          Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
      fT_right = i -> rightT("v" + i);
  private final Function<
          String, Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
      gT_right = s -> rightT(s + "!");

  private final Function<
          Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
      fT_left = i -> leftT("ErrorFromF_v" + i);
  private final Function<
          Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
      fT_empty = i -> emptyT();

  @Nested
  @DisplayName("Applicative 'ap' specific tests")
  class ApSpecificTests {

    final Function<Integer, String> multiplyToString = i -> "Res:" + (i * 2);
    final TestErrorEitherTMonad errorFunc = new TestErrorEitherTMonad("ERR_FUNC");
    final TestErrorEitherTMonad errorVal = new TestErrorEitherTMonad("ERR_VAL");

    // Case 1: F<Right(func)> ap F<Right(val)> -> F<Right(func(val))>
    @Test
    void ap_FuncRight_ValRight_shouldApplyFunction() {
      Kind<
              EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>,
              Function<Integer, String>>
          ff = rightT(multiplyToString);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> fa =
          rightT(10);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.ap(ff, fa);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.right("Res:20"));
    }

    // Case 2: F<Right(func)> ap F<Left(L_val)> -> F<Left(L_val)>
    @Test
    void ap_FuncRight_ValLeft_shouldPropagateValLeft() {
      Kind<
              EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>,
              Function<Integer, String>>
          ff = rightT(multiplyToString);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> fa =
          leftT("VAL_LEFT_ERROR");
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.ap(ff, fa);
      assertThat(unwrapKindToOptionalEither(result))
          .isPresent()
          .contains(Either.left(new TestErrorEitherTMonad("VAL_LEFT_ERROR")));
    }

    // Case 3: F<Left(L_func)> ap F<Right(val)> -> F<Left(L_func)>
    @Test
    void ap_FuncLeft_ValRight_shouldPropagateFuncLeft() {
      Kind<
              EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>,
              Function<Integer, String>>
          ff = leftT("FUNC_LEFT_ERROR");
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> fa =
          rightT(10);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.ap(ff, fa);
      assertThat(unwrapKindToOptionalEither(result))
          .isPresent()
          .contains(Either.left(new TestErrorEitherTMonad("FUNC_LEFT_ERROR")));
    }

    // Case 4: F<Left(L_func)> ap F<Left(L_val)> -> F<Left(L_func)>
    @Test
    void ap_FuncLeft_ValLeft_shouldPropagateFuncLeft() {
      Kind<
              EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>,
              Function<Integer, String>>
          ff = leftT("FUNC_LEFT_ERROR_DOMINATES");
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> fa =
          leftT("VAL_LEFT_ERROR_SECONDARY");
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.ap(ff, fa);
      assertThat(unwrapKindToOptionalEither(result))
          .isPresent()
          .contains(Either.left(new TestErrorEitherTMonad("FUNC_LEFT_ERROR_DOMINATES")));
    }

    // Case 5: F.empty (for function) ap F<Right(val)> -> F.empty
    @Test
    void ap_FuncOuterEmpty_ValRight_shouldBeOuterEmpty() {
      Kind<
              EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>,
              Function<Integer, String>>
          ff = emptyOuterT();
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> fa =
          rightT(10);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.ap(ff, fa);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }

    // Case 6: F<Right(func)> ap F.empty (for value) -> F.empty
    @Test
    void ap_FuncRight_ValOuterEmpty_shouldBeOuterEmpty() {
      Kind<
              EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>,
              Function<Integer, String>>
          ff = rightT(multiplyToString);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> fa =
          emptyOuterT();
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.ap(ff, fa);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }

    // Test 1 from user's failing stack traces - Corrected Assertion
    // Test method name in user stack trace was ap_FuncRightIsNull_ValRight_shouldThrowNPEWhenRun
    // The line number pointed to the eitherTMonad.ap call (line 197 in your file)
    @Test
    @DisplayName("ap: F<Right(null_function)> ap F<Right(val)> -> NPE during ap execution")
    void ap_FuncRightIsNull_ValRight_shouldThrowNPE() { // Renamed for clarity
      Kind<
              EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>,
              Function<Integer, String>>
          ff = rightTWithNullFunction(); // This creates F<Right(null_function_itself)>
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> fa =
          rightT(10);

      // The NPE with "mapper function cannot be null" occurs when EitherTMonad.ap tries to
      // execute the logic `eitherF.flatMap(f -> eitherA.map(f))`, and `f` (the unwrapped
      // function from `eitherF`) is null, causing `eitherA.map(null)` to fail.
      // This happens inside the `eitherTMonad.ap` call.
      assertThatThrownBy(() -> eitherTMonad.ap(ff, fa))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("function f for map cannot be null");
    }

    // Test 2 from user's failing stack traces - Corrected Assertion
    // Test method name in user stack trace was
    // ap_FuncRightThrows_ValRight_shouldThrowExceptionWhenRun
    // The line number pointed to the `rightT(throwingFunc)` setup line, but the actual exception
    // comes from `ap`.
    @Test
    @DisplayName("ap: F<Right(function_throws)> ap F<Right(val)> -> Exception during ap execution")
    void ap_FuncRightThrows_ValRight_shouldThrowException() { // Renamed for clarity
      RuntimeException ex = new RuntimeException("Function apply crashed");
      Function<Integer, String> throwingFunc =
          i -> {
            throw ex;
          }; // Defined at EitherTMonadTest.java:210
      Kind<
              EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>,
              Function<Integer, String>>
          ff = rightT(throwingFunc);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> fa =
          rightT(10);

      // The RuntimeException 'ex' is thrown when `throwingFunc.apply()` is executed
      // within the logic of `eitherTMonad.ap`. This exception propagates out of the `ap` call.
      assertThatThrownBy(() -> eitherTMonad.ap(ff, fa))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenRight() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> input =
          rightT(10);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.map(Object::toString, input);
      Optional<Either<TestErrorEitherTMonad, String>> either = unwrapKindToOptionalEither(result);
      assertThat(either).isPresent().contains(Either.right("10"));
    }

    @Test
    void map_shouldPropagateLeftWhenLeft() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> input =
          leftT("E1");
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.map(Object::toString, input);
      Optional<Either<TestErrorEitherTMonad, String>> either = unwrapKindToOptionalEither(result);
      assertThat(either).isPresent().contains(Either.left(new TestErrorEitherTMonad("E1")));
    }

    @Test
    void map_shouldPropagateEmpty() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> input =
          emptyT();
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.map(Object::toString, input);
      Optional<Either<TestErrorEitherTMonad, String>> either = unwrapKindToOptionalEither(result);
      assertThat(either).isEmpty();
    }
  }

  private <A> void assertEitherTEquals(
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, A> k1,
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, A> k2) {
    assertThat(unwrapKindToOptionalEither(k1)).isEqualTo(unwrapKindToOptionalEither(k2));
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value;
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> mValue;
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> mLeft;
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> mEmpty;
    Function<
            Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
        fTLocal;
    Function<String, Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
        gTLocal;

    @BeforeEach
    void setUpLaws() {
      value = 5;
      mValue = rightT(value);
      mLeft = leftT("M_ERR");
      mEmpty = emptyT();
      fTLocal = i -> rightT("v" + i);
      gTLocal = s -> rightT(s + "!");
    }

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> ofValue =
          eitherTMonad.of(value);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> leftSide =
          eitherTMonad.flatMap(fTLocal, ofValue);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> rightSide =
          fTLocal.apply(value);
      assertEitherTEquals(leftSide, rightSide);
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<
              Integer,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer>>
          ofFunc = i -> eitherTMonad.of(i);

      assertEitherTEquals(eitherTMonad.flatMap(ofFunc, mValue), mValue);
      assertEitherTEquals(eitherTMonad.flatMap(ofFunc, mLeft), mLeft);
      assertEitherTEquals(eitherTMonad.flatMap(ofFunc, mEmpty), mEmpty);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> innerFlatMap =
          eitherTMonad.flatMap(fTLocal, mValue);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> leftSide =
          eitherTMonad.flatMap(gTLocal, innerFlatMap);

      Function<
              Integer,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
          rightSideFunc = a -> eitherTMonad.flatMap(gTLocal, fTLocal.apply(a));
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> rightSide =
          eitherTMonad.flatMap(rightSideFunc, mValue);
      assertEitherTEquals(leftSide, rightSide);

      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>
          innerFlatMapLeft = eitherTMonad.flatMap(fTLocal, mLeft);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> leftSideLeft =
          eitherTMonad.flatMap(gTLocal, innerFlatMapLeft);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> rightSideLeft =
          eitherTMonad.flatMap(rightSideFunc, mLeft);
      assertEitherTEquals(leftSideLeft, rightSideLeft);

      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>
          innerFlatMapEmpty = eitherTMonad.flatMap(fTLocal, mEmpty);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> leftSideEmpty =
          eitherTMonad.flatMap(gTLocal, innerFlatMapEmpty);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>
          rightSideEmpty = eitherTMonad.flatMap(rightSideFunc, mEmpty);
      assertEitherTEquals(leftSideEmpty, rightSideEmpty);
    }
  }

  @Nested
  @DisplayName("MonadError Methods")
  class MonadErrorMethods {
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> rightVal;
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> leftVal;
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> emptyVal;
    TestErrorEitherTMonad raisedErrorObj;
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> raisedErrorKind;

    @BeforeEach
    void setUpMonadError() {
      rightVal = rightT(100);
      leftVal = leftT("E404");
      emptyVal = emptyT();
      raisedErrorObj = new TestErrorEitherTMonad("E500");
      raisedErrorKind = eitherTMonad.raiseError(raisedErrorObj);
    }

    @Test
    void raiseError_shouldCreateLeftInOptional() {
      Optional<Either<TestErrorEitherTMonad, Integer>> result =
          unwrapKindToOptionalEither(raisedErrorKind);
      assertThat(result).isPresent().contains(Either.left(raisedErrorObj));
    }

    @Test
    void handleErrorWith_shouldHandleLeft() {
      Function<
              TestErrorEitherTMonad,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer>>
          handler = err -> rightT(Integer.parseInt(err.code().substring(1)));

      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> result =
          eitherTMonad.handleErrorWith(leftVal, handler);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.right(404));
    }

    @Test
    void handleErrorWith_shouldIgnoreRight() {
      Function<
              TestErrorEitherTMonad,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer>>
          handler = err -> rightT(-1);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> result =
          eitherTMonad.handleErrorWith(rightVal, handler);
      assertEitherTEquals(result, rightVal);
    }

    @Test
    void handleErrorWith_shouldPropagateEmpty() {
      Function<
              TestErrorEitherTMonad,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer>>
          handler = err -> rightT(-1);
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> result =
          eitherTMonad.handleErrorWith(emptyVal, handler);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("flatMap Specific Scenarios")
  class FlatMapSpecificTests {

    @Test
    @DisplayName("flatMap: Initial Right, Function returns Left")
    void flatMap_initialRight_funcReturnsLeft() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> initialRight =
          rightT(10);
      Function<
              Integer,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
          funcReturnsLeft = i -> leftT("FuncError_" + i);

      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.flatMap(funcReturnsLeft, initialRight);

      Optional<Either<TestErrorEitherTMonad, String>> eitherResult =
          unwrapKindToOptionalEither(result);
      assertThat(eitherResult)
          .isPresent()
          .contains(Either.left(new TestErrorEitherTMonad("FuncError_10")));
    }

    @Test
    @DisplayName("flatMap: Initial Right, Function returns Empty Outer Monad")
    void flatMap_initialRight_funcReturnsEmptyOuter() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> initialRight =
          rightT(20);
      Function<
              Integer,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
          funcReturnsEmpty = i -> emptyT();

      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.flatMap(funcReturnsEmpty, initialRight);

      Optional<Either<TestErrorEitherTMonad, String>> eitherResult =
          unwrapKindToOptionalEither(result);
      assertThat(eitherResult).isEmpty();
    }

    @Test
    @DisplayName("flatMap: Initial Left, Function should not be called")
    void flatMap_initialLeft_funcNotCalled() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> initialLeft =
          leftT("InitialError");
      Function<
              Integer,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
          funcShouldNotRun =
              i -> {
                throw new AssertionError("Function should not have been called for Left input");
              };

      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.flatMap(funcShouldNotRun, initialLeft);

      Optional<Either<TestErrorEitherTMonad, String>> eitherResult =
          unwrapKindToOptionalEither(result);
      assertThat(eitherResult)
          .isPresent()
          .contains(Either.left(new TestErrorEitherTMonad("InitialError")));
    }

    @Test
    @DisplayName("flatMap: Initial Empty Outer Monad, Function should not be called")
    void flatMap_initialEmptyOuter_funcNotCalled() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer>
          initialEmptyOuter = emptyT();
      Function<
              Integer,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
          funcShouldNotRun =
              i -> {
                throw new AssertionError(
                    "Function should not have been called for empty outer input");
              };

      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String> result =
          eitherTMonad.flatMap(funcShouldNotRun, initialEmptyOuter);

      Optional<Either<TestErrorEitherTMonad, String>> eitherResult =
          unwrapKindToOptionalEither(result);
      assertThat(eitherResult).isEmpty();
    }

    @Test
    @DisplayName("flatMap: Function throws unhandled RuntimeException")
    void flatMap_functionThrowsRuntimeException() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> initialRight =
          rightT(30);
      RuntimeException runtimeEx = new RuntimeException("Error in function application!");
      Function<
              Integer,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
          funcThrows =
              i -> {
                throw runtimeEx;
              };

      assertThatThrownBy(() -> eitherTMonad.flatMap(funcThrows, initialRight))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(runtimeEx);
    }

    @Test
    @DisplayName("flatMap: Function returns null Kind (should throw KindUnwrapException)")
    void flatMap_functionReturnsNullKind() {
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, Integer> initialRight =
          rightT(40);
      Function<
              Integer,
              Kind<EitherTKind.Witness<OptionalKind.Witness, TestErrorEitherTMonad>, String>>
          funcReturnsNullKind = i -> null;

      assertThatThrownBy(() -> eitherTMonad.flatMap(funcReturnsNullKind, initialRight))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(NULL_KIND_TEMPLATE.formatted("EitherT"));
    }
  }
}
