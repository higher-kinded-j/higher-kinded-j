// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_FUNCTION_MSG;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedMonad<E> Tests")
class ValidatedMonadTest {

  record TestError(String code, String message) {}

  private final Semigroup<TestError> errorSemigroup =
      (e1, e2) -> new TestError(e1.code() + "+" + e2.code(), e1.message() + " & " + e2.message());

  private final ValidatedMonad<TestError> validatedMonad = ValidatedMonad.instance(errorSemigroup);

  private final TestError error1 = new TestError("E001", "First Error");
  private final TestError error2 = new TestError("E002", "Second Error");

  private <A> Validated<TestError, A> unwrap(Kind<ValidatedKind.Witness<TestError>, A> kind) {
    return VALIDATED.narrow(kind);
  }

  private <A> Kind<ValidatedKind.Witness<TestError>, A> validKind(A value) {
    return VALIDATED.widen(Validated.valid(value));
  }

  private <A> Kind<ValidatedKind.Witness<TestError>, A> invalidKind(TestError error) {
    return VALIDATED.widen(Validated.invalid(error));
  }

  @Test
  @DisplayName("instance() should return a new instance")
  void instanceShouldReturnNewInstance() {
    ValidatedMonad<TestError> anotherInstance = ValidatedMonad.instance(errorSemigroup);
    assertThat(validatedMonad).isNotSameAs(anotherInstance);
  }

  @Nested
  @DisplayName("Applicative 'of' (pure) Method")
  class OfMethod {
    @Test
    @DisplayName("of(value) should create a Valid Kind for non-null value")
    void ofValueShouldCreateValidKind() {
      Kind<ValidatedKind.Witness<TestError>, String> result = validatedMonad.of("success");
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isValid()).isTrue();
      assertThat(unwrapped.get()).isEqualTo("success");
    }

    @Test
    @DisplayName("of(value) should throw NullPointerException if argument value is null")
    void ofShouldThrowIfInputToOfIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.of(null))
          .withMessage("value for of cannot be null");
    }
  }

  @Nested
  @DisplayName("Functor 'map' Method")
  class MapMethod {
    private final Function<Integer, String> toStringFunc = Object::toString;
    private final Function<Integer, String> throwingFunc =
        i -> {
          throw new RuntimeException("Map Error");
        };

    @Test
    @DisplayName("map should apply function to Valid value")
    void mapShouldApplyFunctionToValidValue() {
      Kind<ValidatedKind.Witness<TestError>, Integer> valid = validKind(123);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.map(toStringFunc, valid);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isValid()).isTrue();
      assertThat(unwrapped.get()).isEqualTo("123");
    }

    @Test
    @DisplayName("map should propagate Invalid")
    void mapShouldPropagateInvalid() {
      Kind<ValidatedKind.Witness<TestError>, Integer> invalid = invalidKind(error1);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.map(toStringFunc, invalid);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isInvalid()).isTrue();
      assertThat(unwrapped.getError()).isEqualTo(error1);
    }

    @Test
    @DisplayName("map should throw NullPointerException if function returns null")
    void mapShouldThrowIfFunctionReturnsNull() {
      Kind<ValidatedKind.Witness<TestError>, Integer> valid = validKind(123);
      Function<Integer, String> nullReturningFunc = i -> null;
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.map(nullReturningFunc, valid))
          .withMessage("Mapping function returned null in Valid.map");
    }

    @Test
    @DisplayName("map should propagate exception from function")
    void mapShouldPropagateExceptionFromFunction() {
      Kind<ValidatedKind.Witness<TestError>, Integer> valid = validKind(123);
      assertThatExceptionOfType(RuntimeException.class)
          .isThrownBy(() -> validatedMonad.map(throwingFunc, valid))
          .withMessage("Map Error");
    }

    @Test
    @DisplayName("map should throw NullPointerException if function is null")
    void mapShouldThrowNullPointerExceptionIfFunctionIsNull() {
      Kind<ValidatedKind.Witness<TestError>, Integer> valid = validKind(123);
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.map(null, valid))
          .withMessage("function f for map cannot be null");
    }

    @Test
    @DisplayName("map should throw NullPointerException if kind is null")
    void mapShouldThrowNullPointerExceptionIfKindIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.map(toStringFunc, null))
          .withMessage("source Kind for map cannot be null");
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' Method")
  class ApMethod {
    private final Function<Integer, String> addPrefixFunc = i -> "Val-" + i;
    private final Kind<ValidatedKind.Witness<TestError>, Function<Integer, String>> validFnKind =
        validKind(addPrefixFunc);
    private final Kind<ValidatedKind.Witness<TestError>, Function<Integer, String>> invalidFnKind =
        invalidKind(error1);
    private final RuntimeException applyException = new RuntimeException("Apply exception in ap");
    private final Kind<ValidatedKind.Witness<TestError>, Function<Integer, String>> throwingFnKind =
        validKind(
            (Integer i) -> {
              throw applyException;
            });

    @Test
    @DisplayName("ap with Valid function and Valid value")
    void apWithValidFunctionAndValidValue() {
      Kind<ValidatedKind.Witness<TestError>, Integer> validValueKind = validKind(100);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.ap(validFnKind, validValueKind);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isValid()).isTrue();
      assertThat(unwrapped.get()).isEqualTo("Val-100");
    }

    @Test
    @DisplayName("ap with Invalid function and Valid value")
    void apWithInvalidFunctionAndValidValue() {
      Kind<ValidatedKind.Witness<TestError>, Integer> validValueKind = validKind(100);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.ap(invalidFnKind, validValueKind);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isInvalid()).isTrue();
      assertThat(unwrapped.getError()).isEqualTo(error1);
    }

    @Test
    @DisplayName("ap with Valid function and Invalid value")
    void apWithValidFunctionAndInvalidValue() {
      Kind<ValidatedKind.Witness<TestError>, Integer> invalidValueKind = invalidKind(error2);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.ap(validFnKind, invalidValueKind);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isInvalid()).isTrue();
      assertThat(unwrapped.getError()).isEqualTo(error2);
    }

    @Test
    @DisplayName("ap with Invalid function and Invalid value should combine errors")
    void apWithInvalidFunctionAndInvalidValue() {
      Kind<ValidatedKind.Witness<TestError>, Integer> invalidValueKind = invalidKind(error2);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.ap(invalidFnKind, invalidValueKind);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isInvalid()).isTrue();
      assertThat(unwrapped.getError().message()).isEqualTo("First Error & Second Error");
      assertThat(unwrapped.getError().code()).isEqualTo("E001+E002");
    }

    @Test
    @DisplayName("ap should throw when function application throws")
    void apShouldThrowWhenFunctionApplicationThrows() {
      Kind<ValidatedKind.Witness<TestError>, Integer> validValueKind = validKind(100);
      assertThatExceptionOfType(RuntimeException.class)
          .isThrownBy(() -> validatedMonad.ap(throwingFnKind, validValueKind))
          .isSameAs(applyException);
    }

    @Test
    @DisplayName("ap should throw NullPointerException if fnKind is null")
    void apShouldThrowIfFnKindIsNull() {
      Kind<ValidatedKind.Witness<TestError>, Integer> validValueKind = validKind(100);
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.ap(null, validValueKind))
          .withMessage("function Kind for ap cannot be null");
    }

    @Test
    @DisplayName("ap should throw NullPointerException if valueKind is null")
    void apShouldThrowIfValueKindIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.ap(validFnKind, null))
          .withMessage("argument Kind for ap cannot be null");
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' Method")
  class FlatMapMethod {
    private final Function<Integer, Kind<ValidatedKind.Witness<TestError>, String>> fValid =
        i -> validKind("Mapped-" + i);
    private final Function<Integer, Kind<ValidatedKind.Witness<TestError>, String>> fInvalid =
        i -> invalidKind(error2);
    private final Function<Integer, Kind<ValidatedKind.Witness<TestError>, String>> fThrows =
        i -> {
          throw new RuntimeException("FlatMap func error");
        };
    private final Function<Integer, Kind<ValidatedKind.Witness<TestError>, String>>
        fReturnsNullKind = i -> null;

    @Test
    @DisplayName("flatMap with Valid input and function returning Valid")
    void flatMapValidInputAndFuncReturnsValid() {
      Kind<ValidatedKind.Witness<TestError>, Integer> validInput = validKind(200);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.flatMap(fValid, validInput);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isValid()).isTrue();
      assertThat(unwrapped.get()).isEqualTo("Mapped-200");
    }

    @Test
    @DisplayName("flatMap with Valid input and function returning Invalid")
    void flatMapValidInputAndFuncReturnsInvalid() {
      Kind<ValidatedKind.Witness<TestError>, Integer> validInput = validKind(200);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.flatMap(fInvalid, validInput);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isInvalid()).isTrue();
      assertThat(unwrapped.getError()).isEqualTo(error2);
    }

    @Test
    @DisplayName("flatMap with Invalid input should propagate Invalid")
    void flatMapInvalidInputShouldPropagateInvalid() {
      Kind<ValidatedKind.Witness<TestError>, Integer> invalidInput = invalidKind(error1);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.flatMap(fValid, invalidInput);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isInvalid()).isTrue();
      assertThat(unwrapped.getError()).isEqualTo(error1);
    }

    @Test
    @DisplayName("flatMap should throw when function application throws")
    void flatMapShouldThrowWhenFunctionApplicationThrows() {
      Kind<ValidatedKind.Witness<TestError>, Integer> validInput = validKind(200);
      assertThatExceptionOfType(RuntimeException.class)
          .isThrownBy(() -> validatedMonad.flatMap(fThrows, validInput))
          .withMessage("FlatMap func error");
    }

    @Test
    @DisplayName("flatMap should throw NullPointerException if function returns null Kind")
    void flatMapShouldThrowIfFunctionReturnsNullKind() {
      Kind<ValidatedKind.Witness<TestError>, Integer> validInput = validKind(200);
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.flatMap(fReturnsNullKind, validInput))
          .withMessage("flatMap function returned Kind cannot be null");
    }

    @Test
    @DisplayName("flatMap should throw NullPointerException if function is null")
    void flatMapShouldThrowIfFunctionIsNull() {
      Kind<ValidatedKind.Witness<TestError>, Integer> validInput = validKind(200);
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.flatMap(null, validInput))
          .withMessage("function f for flatMap cannot be null");
    }

    @Test
    @DisplayName("flatMap should throw NullPointerException if valueKind is null")
    void flatMapShouldThrowIfValueKindIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.flatMap(fValid, null))
          .withMessage("source Kind for flatMap cannot be null");
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {
    private final String valA = "start value";
    private final Function<String, Kind<ValidatedKind.Witness<TestError>, Integer>> f =
        s -> validKind(s.length());
    private final Function<Integer, Kind<ValidatedKind.Witness<TestError>, String>> g =
        len -> validKind("Length: " + len);
    private final Function<String, Kind<ValidatedKind.Witness<TestError>, Integer>> fFails =
        s -> invalidKind(error1);

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<ValidatedKind.Witness<TestError>, String> ofA = validatedMonad.of(valA);
      Kind<ValidatedKind.Witness<TestError>, Integer> leftSide = validatedMonad.flatMap(f, ofA);
      Kind<ValidatedKind.Witness<TestError>, Integer> rightSide = f.apply(valA);
      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

      Kind<ValidatedKind.Witness<TestError>, Integer> leftSideFails =
          validatedMonad.flatMap(fFails, ofA);
      Kind<ValidatedKind.Witness<TestError>, Integer> rightSideFails = fFails.apply(valA);
      assertThat(unwrap(leftSideFails)).isEqualTo(unwrap(rightSideFails));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Kind<ValidatedKind.Witness<TestError>, String> mValid = validKind(valA);
      Kind<ValidatedKind.Witness<TestError>, String> resultValid =
          validatedMonad.flatMap(validatedMonad::of, mValid);
      assertThat(unwrap(resultValid)).isEqualTo(unwrap(mValid));

      Kind<ValidatedKind.Witness<TestError>, String> mInvalid = invalidKind(error1);
      Kind<ValidatedKind.Witness<TestError>, String> resultInvalid =
          validatedMonad.flatMap(validatedMonad::of, mInvalid);
      assertThat(unwrap(resultInvalid)).isEqualTo(unwrap(mInvalid));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m,f), g) == flatMap(m, x -> flatMap(f(x),g))")
    void associativity() {
      Kind<ValidatedKind.Witness<TestError>, String> mValid = validKind(valA);
      Kind<ValidatedKind.Witness<TestError>, Integer> fmValid = validatedMonad.flatMap(f, mValid);
      Kind<ValidatedKind.Witness<TestError>, String> leftSideValid =
          validatedMonad.flatMap(g, fmValid);

      Function<String, Kind<ValidatedKind.Witness<TestError>, String>> composedFuncValid =
          x -> validatedMonad.flatMap(g, f.apply(x));
      Kind<ValidatedKind.Witness<TestError>, String> rightSideValid =
          validatedMonad.flatMap(composedFuncValid, mValid);
      assertThat(unwrap(leftSideValid)).isEqualTo(unwrap(rightSideValid));

      Kind<ValidatedKind.Witness<TestError>, String> mInvalid = invalidKind(error2);
      Kind<ValidatedKind.Witness<TestError>, Integer> fmInvalid =
          validatedMonad.flatMap(f, mInvalid);
      Kind<ValidatedKind.Witness<TestError>, String> leftSideMInvalid =
          validatedMonad.flatMap(g, fmInvalid);

      Kind<ValidatedKind.Witness<TestError>, String> rightSideMInvalid =
          validatedMonad.flatMap(composedFuncValid, mInvalid);
      assertThat(unwrap(leftSideMInvalid)).isEqualTo(unwrap(rightSideMInvalid));
      assertThat(unwrap(leftSideMInvalid).getError()).isEqualTo(error2);

      Kind<ValidatedKind.Witness<TestError>, Integer> fmReturnsInvalid =
          validatedMonad.flatMap(fFails, mValid);
      Kind<ValidatedKind.Witness<TestError>, String> leftSideFReturnsInvalid =
          validatedMonad.flatMap(g, fmReturnsInvalid);

      Function<String, Kind<ValidatedKind.Witness<TestError>, String>> composedFuncFReturnsInvalid =
          x -> validatedMonad.flatMap(g, fFails.apply(x));
      Kind<ValidatedKind.Witness<TestError>, String> rightSideFReturnsInvalid =
          validatedMonad.flatMap(composedFuncFReturnsInvalid, mValid);

      assertThat(unwrap(leftSideFReturnsInvalid)).isEqualTo(unwrap(rightSideFReturnsInvalid));
      assertThat(unwrap(leftSideFReturnsInvalid).getError()).isEqualTo(error1);
    }
  }

  @Nested
  @DisplayName("MonadError Methods")
  class MonadErrorMethods {

    private final TestError testErrorForRaise = new TestError("E_RAISE", "Error from raiseError");
    private final TestError testErrorForHandler = new TestError("E_HANDLER", "Error from handler");

    // --- raiseError ---
    @Test
    @DisplayName("raiseError should create an Invalid Kind with the given error")
    void raiseError_shouldCreateInvalidKind() {
      Kind<ValidatedKind.Witness<TestError>, String> errorKind =
          validatedMonad.raiseError(testErrorForRaise);
      Validated<TestError, String> unwrapped = unwrap(errorKind);

      assertThat(unwrapped.isInvalid()).isTrue();
      assertThat(unwrapped.getError()).isSameAs(testErrorForRaise);
    }

    @Test
    @DisplayName("raiseError should throw NullPointerException if error is null")
    void raiseError_shouldThrowNpeIfErrorIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.raiseError(null))
          .withMessage("error for raiseError cannot be null");
    }

    // --- handleErrorWith ---
    @Test
    @DisplayName(
        "handleErrorWith with Valid input should return original Valid Kind and not call handler")
    void handleErrorWith_validInput_shouldReturnOriginalAndNotCallHandler() {
      final Kind<ValidatedKind.Witness<TestError>, String> validKind = validKind("Success");
      final java.util.concurrent.atomic.AtomicBoolean handlerCalled =
          new java.util.concurrent.atomic.AtomicBoolean(false);
      Function<TestError, Kind<ValidatedKind.Witness<TestError>, String>> handler =
          e -> {
            handlerCalled.set(true);
            return validKind("Recovered from " + e.message());
          };

      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.handleErrorWith(validKind, handler);

      assertThat(result).isSameAs(validKind);
      assertThat(handlerCalled.get()).isFalse();
    }

    @Test
    @DisplayName(
        "handleErrorWith with Invalid input should call handler and return its Valid result")
    void handleErrorWith_invalidInput_handlerReturnsValid() {
      Kind<ValidatedKind.Witness<TestError>, String> invalidKindInput = invalidKind(error1);
      Function<TestError, Kind<ValidatedKind.Witness<TestError>, String>> handler =
          e -> {
            assertThat(e).isSameAs(error1);
            return validKind("Recovered: " + e.message());
          };

      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.handleErrorWith(invalidKindInput, handler);
      Validated<TestError, String> unwrapped = unwrap(result);

      assertThat(unwrapped.isValid()).isTrue();
      assertThat(unwrapped.get()).isEqualTo("Recovered: " + error1.message());
    }

    @Test
    @DisplayName(
        "handleErrorWith with Invalid input should call handler and return its Invalid result")
    void handleErrorWith_invalidInput_handlerReturnsInvalid() {
      Kind<ValidatedKind.Witness<TestError>, String> invalidKindInput = invalidKind(error1);
      Function<TestError, Kind<ValidatedKind.Witness<TestError>, String>> handler =
          e -> {
            assertThat(e).isSameAs(error1);
            return invalidKind(testErrorForHandler);
          };

      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.handleErrorWith(invalidKindInput, handler);
      Validated<TestError, String> unwrapped = unwrap(result);

      assertThat(unwrapped.isInvalid()).isTrue();
      assertThat(unwrapped.getError()).isSameAs(testErrorForHandler);
    }

    @Test
    @DisplayName("handleErrorWith should throw NullPointerException if input Kind is null")
    void handleErrorWith_nullInputKind_shouldThrowNpe() {
      Function<TestError, Kind<ValidatedKind.Witness<TestError>, String>> handler =
          e -> validKind("Doesn't matter");
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.handleErrorWith(null, handler))
          .withMessage("Kind ma for handleErrorWith cannot be null");
    }

    @Test
    @DisplayName("handleErrorWith should throw NullPointerException if handler is null")
    void handleErrorWith_nullHandler_shouldThrowNpe() {
      Kind<ValidatedKind.Witness<TestError>, String> invalidKindInput = invalidKind(error1);
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.handleErrorWith(invalidKindInput, null))
          .withMessage(NULL_FUNCTION_MSG.formatted("handler function for handleErrorWith"));
    }

    @Test
    @DisplayName("handleErrorWith should throw NullPointerException if handler returns null")
    void handleErrorWith_handlerReturnsNull_shouldThrowNpe() {
      Kind<ValidatedKind.Witness<TestError>, String> invalidKindInput = invalidKind(error1);
      Function<TestError, Kind<ValidatedKind.Witness<TestError>, String>> handler = e -> null;

      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.handleErrorWith(invalidKindInput, handler))
          .withMessage("handler function returned Kind cannot be null");
    }

    // --- Default MonadError methods ---

    // handleError
    @Test
    @DisplayName("handleError with Valid input should return original Valid Kind")
    void handleError_validInput_shouldReturnOriginal() {
      Kind<ValidatedKind.Witness<TestError>, String> valid = validKind("Success");
      Function<TestError, String> handler = e -> "Recovered";
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.handleError(valid, handler);
      assertThat(result).isSameAs(valid);
    }

    @Test
    @DisplayName("handleError with Invalid input should recover using handler and 'of'")
    void handleError_invalidInput_shouldRecover() {
      Kind<ValidatedKind.Witness<TestError>, String> invalid = invalidKind(error1);
      Function<TestError, String> handler = e -> "Recovered from " + e.message();
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.handleError(invalid, handler);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isValid()).isTrue();
      assertThat(unwrapped.get()).isEqualTo("Recovered from " + error1.message());
    }

    // recover
    @Test
    @DisplayName("recover with Valid input should return original Valid Kind")
    void recover_validInput_shouldReturnOriginal() {
      Kind<ValidatedKind.Witness<TestError>, String> valid = validKind("Success");
      String fallbackValue = "Fallback";
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.recover(valid, fallbackValue);
      assertThat(result).isSameAs(valid);
    }

    @Test
    @DisplayName("recover with Invalid input should recover with fallback value using 'of'")
    void recover_invalidInput_shouldRecover() {
      Kind<ValidatedKind.Witness<TestError>, String> invalid = invalidKind(error1);
      String fallbackValue = "Fallback Value";
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.recover(invalid, fallbackValue);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isValid()).isTrue();
      assertThat(unwrapped.get()).isEqualTo(fallbackValue);
    }

    @Test
    @DisplayName(
        "recover with Invalid input and null fallback value should throw if 'of' disallows null")
    void recover_invalidInput_nullFallback_shouldThrowFromOf() {
      Kind<ValidatedKind.Witness<TestError>, String> invalid = invalidKind(error1);
      assertThatNullPointerException()
          .isThrownBy(() -> validatedMonad.recover(invalid, null))
          .withMessage("value for of cannot be null");
    }

    // recoverWith
    @Test
    @DisplayName("recoverWith with Valid input should return original Valid Kind")
    void recoverWith_validInput_shouldReturnOriginal() {
      Kind<ValidatedKind.Witness<TestError>, String> valid = validKind("Success");
      Kind<ValidatedKind.Witness<TestError>, String> fallbackKind = validKind("Fallback Kind");
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.recoverWith(valid, fallbackKind);
      assertThat(result).isSameAs(valid);
    }

    @Test
    @DisplayName("recoverWith with Invalid input should recover with fallback Kind (Valid)")
    void recoverWith_invalidInput_fallbackIsValid() {
      Kind<ValidatedKind.Witness<TestError>, String> invalid = invalidKind(error1);
      Kind<ValidatedKind.Witness<TestError>, String> fallbackKind = validKind("Fallback Valid");
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.recoverWith(invalid, fallbackKind);
      assertThat(result).isSameAs(fallbackKind);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isValid()).isTrue();
      assertThat(unwrapped.get()).isEqualTo("Fallback Valid");
    }

    @Test
    @DisplayName("recoverWith with Invalid input should recover with fallback Kind (Invalid)")
    void recoverWith_invalidInput_fallbackIsInvalid() {
      Kind<ValidatedKind.Witness<TestError>, String> invalid = invalidKind(error1);
      Kind<ValidatedKind.Witness<TestError>, String> fallbackKind =
          invalidKind(testErrorForHandler);
      Kind<ValidatedKind.Witness<TestError>, String> result =
          validatedMonad.recoverWith(invalid, fallbackKind);
      assertThat(result).isSameAs(fallbackKind);
      Validated<TestError, String> unwrapped = unwrap(result);
      assertThat(unwrapped.isInvalid()).isTrue();
      assertThat(unwrapped.getError()).isSameAs(testErrorForHandler);
    }
  }
}
