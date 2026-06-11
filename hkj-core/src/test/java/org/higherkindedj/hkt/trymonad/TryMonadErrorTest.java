// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("TryMonad — error handling")
class TryMonadErrorTest extends TryTestBase {

  private MonadError<TryKind.Witness, Throwable> monadError;
  private Function<Throwable, Kind<TryKind.Witness, String>> validHandler;
  private Kind<TryKind.Witness, String> validFallback;

  @BeforeEach
  void setUpMonadError() {
    monadError = Instances.monadError(try_());
    validHandler = _ -> monadError.of("recovered");
    validFallback = monadError.of("fallback");
    validateMonadFixtures();
  }

  /**
   * Operation smoke and null-argument validation for {@code map}/{@code flatMap}/{@code ap}/{@code
   * handleErrorWith}/{@code recoverWith} on the MonadError instance. The Monad/MonadError laws are
   * verified parameterised in {@link TryMonadTest}, so this contract omits {@link Category#LAWS}.
   *
   * <p>{@link Category#EXCEPTIONS} is omitted because the generic contract asserts that {@code
   * map}/{@code flatMap} <em>propagate</em> a thrown function exception, whereas {@code Try}
   * captures it as a {@link Try.Failure} (exercised in the operation tests below and in {@link
   * TryMonadTest}).
   *
   * <p>{@link Category#VALIDATIONS} <em>is</em> run: {@code TryMonad} now overrides {@code
   * recoverWith} to reject a null fallback eagerly (regardless of {@code ma}'s state), matching
   * {@code Either}/{@code Maybe}. Per-method message assertions live in the operation tests below.
   */
  @Test
  @DisplayName(
      "MonadError contract — operations & validations (laws in TryMonadTest; exceptions omitted)")
  void monadErrorContract() {
    TypeClassContract.<TryKind.Witness, Throwable>monadError(TryMonad.class)
        .<String>instance(monadError)
        .<Integer>withKind(validKind)
        .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
        .withErrorHandling(validHandler, validFallback)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("handleErrorWith() passes through Success")
    void handleErrorWithPassesThroughSuccess() {
      var result = monadError.handleErrorWith(validKind, validHandler);
      assertThatTry(result).isSuccess().hasValue(DEFAULT_SUCCESS_VALUE);
    }

    @Test
    @DisplayName("handleErrorWith() recovers from Failure")
    void handleErrorWithRecoversFromFailure() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      var result = monadError.handleErrorWith(failure, validHandler);
      assertThatTry(result).isSuccess().hasValue("recovered");
    }

    @Test
    @DisplayName("handleErrorWith() captures an exception thrown by the handler")
    void handleErrorWithCapturesHandlerException() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      RuntimeException handlerException = new RuntimeException("Handler error");
      Function<Throwable, Kind<TryKind.Witness, String>> throwingHandler =
          _ -> {
            throw handlerException;
          };

      var result = monadError.handleErrorWith(failure, throwingHandler);
      assertThatTry(result).isFailure().hasException(handlerException);
    }

    @Test
    @DisplayName("handleErrorWith() rejects a null result from the handler")
    @SuppressWarnings("DataFlowIssue") // null-returning handler exercises handleErrorWith's guard
    void handleErrorWithRejectsNullHandlerResult() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      Function<Throwable, Kind<TryKind.Witness, String>> nullHandler = _ -> null;

      var result = monadError.handleErrorWith(failure, nullHandler);
      assertThatTry(result)
          .isFailure()
          .hasExceptionSatisfying(
              ex ->
                  assertThat(ex)
                      .hasMessageContaining("Function handler in handleErrorWith returned null"));
    }

    @Test
    @DisplayName("raiseError() creates a Failure for a RuntimeException")
    void raiseErrorCreatesFailureForRuntimeException() {
      RuntimeException exception = new RuntimeException("Test error");
      Kind<TryKind.Witness, String> result = monadError.raiseError(exception);
      assertThatTry(result).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("raiseError() creates a Failure for a checked exception")
    void raiseErrorCreatesFailureForCheckedException() {
      IOException exception = new IOException("IO error");
      Kind<TryKind.Witness, String> result = monadError.raiseError(exception);
      assertThatTry(result).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("raiseError() creates a Failure for an Error")
    void raiseErrorCreatesFailureForError() {
      StackOverflowError error = new StackOverflowError("Stack overflow");
      Kind<TryKind.Witness, String> result = monadError.raiseError(error);
      assertThatTry(result).isFailure().hasException(error);
    }

    @Test
    @DisplayName("raiseError() rejects a null Throwable")
    void raiseErrorRejectsNullThrowable() {
      assertThatNullPointerException()
          .isThrownBy(() -> monadError.raiseError(null))
          .withMessageContaining("TryMonad.raiseError error cannot be null");
    }

    @ParameterizedTest(name = "handleErrorWith rejects null {0} argument")
    @MethodSource("handleErrorWithNullArguments")
    @DisplayName("handleErrorWith() rejects null arguments")
    void handleErrorWithRejectsNullArguments(
        String expectedMessagePart,
        Kind<TryKind.Witness, String> ma,
        Function<Throwable, Kind<TryKind.Witness, String>> handler) {
      assertThatNullPointerException()
          .isThrownBy(() -> monadError.handleErrorWith(ma, handler))
          .withMessageContaining(expectedMessagePart);
    }

    static Stream<Arguments> handleErrorWithNullArguments() {
      Kind<TryKind.Witness, String> okKind = TRY.widen(Try.success("ok"));
      Function<Throwable, Kind<TryKind.Witness, String>> okHandler =
          _ -> TRY.widen(Try.success("recovered"));
      return Stream.of(
          Arguments.of("Kind", null, okHandler), Arguments.of("handler for", okKind, null));
    }

    @Test
    @DisplayName("recoverWith() passes through Success")
    void recoverWithPassesThroughSuccess() {
      var result = monadError.recoverWith(validKind, validFallback);
      assertThatTry(result).isSuccess().hasValue(DEFAULT_SUCCESS_VALUE);
    }

    @Test
    @DisplayName("recoverWith() uses fallback on Failure")
    void recoverWithUsesFallbackOnFailure() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      var result = monadError.recoverWith(failure, validFallback);
      assertThatTry(result).isSuccess().hasValue("fallback");
    }

    @Test
    @DisplayName("recover() passes through Success")
    void recoverPassesThroughSuccess() {
      var result = monadError.recover(validKind, "ignored");
      assertThatTry(result).isSuccess().hasValue(DEFAULT_SUCCESS_VALUE);
    }

    @Test
    @DisplayName("recover() uses value on Failure")
    void recoverUsesValueOnFailure() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      var result = monadError.recover(failure, "recovered");
      assertThatTry(result).isSuccess().hasValue("recovered");
    }

    @Test
    @DisplayName("recover() with a null value yields Success(null)")
    @SuppressWarnings("DataFlowIssue") // Success(null) is the intended outcome; assert it is null
    void recoverWithNullValueYieldsSuccessOfNull() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      var result = monadError.recover(failure, null);
      assertThatTry(result).isSuccess().hasValueSatisfying(v -> assertThat(v).isNull());
    }

    @ParameterizedTest(name = "recoverWith rejects null {0} argument")
    @MethodSource("recoverWithNullArguments")
    @DisplayName("recoverWith() rejects null arguments eagerly, regardless of source state")
    void recoverWithRejectsNullArguments(
        String expectedMessagePart,
        Kind<TryKind.Witness, String> ma,
        Kind<TryKind.Witness, String> fallback) {
      assertThatNullPointerException()
          .isThrownBy(() -> monadError.recoverWith(ma, fallback))
          .withMessageContaining(expectedMessagePart);
    }

    static Stream<Arguments> recoverWithNullArguments() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success("ok"));
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(new RuntimeException("boom")));
      Kind<TryKind.Witness, String> okFallback = TRY.widen(Try.success("fallback"));
      return Stream.of(
          Arguments.of("recoverWith (source)", null, okFallback),
          // A null fallback must be rejected for a Success too — not just on the error path.
          Arguments.of("recoverWith (fallback)", success, null),
          Arguments.of("recoverWith (fallback)", failure, null));
    }

    @Test
    @DisplayName("recover() rejects a null source, naming recover")
    @SuppressWarnings("DataFlowIssue") // null source exercises recover's guard
    void recoverRejectsNullSource() {
      assertThatNullPointerException()
          .isThrownBy(() -> monadError.recover(null, "value"))
          .withMessageContaining("recover (source)");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("handleErrorWith() recovers from different exception types")
    void handleErrorWithRecoversFromDifferentExceptionTypes() {
      Function<Throwable, Kind<TryKind.Witness, String>> handler =
          t -> monadError.of("Recovered from: " + t.getClass().getSimpleName());

      Kind<TryKind.Witness, String> runtimeFailure =
          failureKind(new RuntimeException("Runtime error"));
      Kind<TryKind.Witness, String> ioFailure = failureKind(new IOException("IO error"));
      Kind<TryKind.Witness, String> errorFailure =
          failureKind(new StackOverflowError("Stack overflow"));

      assertThatTry(monadError.handleErrorWith(runtimeFailure, handler))
          .isSuccess()
          .hasValue("Recovered from: RuntimeException");
      assertThatTry(monadError.handleErrorWith(ioFailure, handler))
          .isSuccess()
          .hasValue("Recovered from: IOException");
      assertThatTry(monadError.handleErrorWith(errorFailure, handler))
          .isSuccess()
          .hasValue("Recovered from: StackOverflowError");
    }

    @Test
    @DisplayName("handleErrorWith() can convert a Failure into another Failure")
    void handleErrorWithCanConvertFailureToAnotherFailure() {
      Kind<TryKind.Witness, String> failure = failureKind(new RuntimeException("Original"));
      IOException newException = new IOException("Converted error");
      Function<Throwable, Kind<TryKind.Witness, String>> handler =
          _ -> TRY.widen(Try.failure(newException));

      var result = monadError.handleErrorWith(failure, handler);
      assertThatTry(result).isFailure().hasException(newException);
    }

    @Test
    @DisplayName("Chained error recovery: the second handler succeeds")
    void chainedErrorRecovery() {
      Kind<TryKind.Witness, String> start = failureKind(DEFAULT_TEST_EXCEPTION);

      var result =
          monadError.handleErrorWith(
              start,
              _ -> {
                // First recovery attempt also fails...
                Kind<TryKind.Witness, String> firstAttempt =
                    monadError.raiseError(new RuntimeException("still failing"));
                // ...the second recovery succeeds.
                return monadError.handleErrorWith(firstAttempt, _ -> monadError.of("recovered"));
              });

      assertThatTry(result).isSuccess().hasValue("recovered");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

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
      Function<Throwable, Kind<TryKind.Witness, Integer>> errorHandler = _ -> monadError.of(-1);

      // Valid input flows through flatMap untouched by the handler.
      Kind<TryKind.Witness, Integer> handledValid =
          monadError.handleErrorWith(
              monadError.flatMap(riskyOperation, TRY.widen(Try.success("test"))), errorHandler);
      assertThatTry(handledValid).isSuccess().hasValue(4);

      // Empty input fails in flatMap and is recovered by the handler.
      Kind<TryKind.Witness, Integer> handledEmpty =
          monadError.handleErrorWith(
              monadError.flatMap(riskyOperation, TRY.widen(Try.success(""))), errorHandler);
      assertThatTry(handledEmpty).isSuccess().hasValue(-1);
    }

    @Test
    @DisplayName("MonadError operations preserve type-class behaviour")
    void monadErrorOperationsPreserveTypeClassBehaviour() {
      assertThatTry(monadError.map(validMapper, validKind)).isSuccess();
      assertThatTry(monadError.flatMap(validFlatMapper, validKind)).isSuccess();
      assertThatTry(monadError.ap(validFunctionKind, validKind)).isSuccess();

      Kind<TryKind.Witness, String> failure = monadError.raiseError(DEFAULT_TEST_EXCEPTION);
      assertThatTry(failure).isFailure();
      assertThatTry(monadError.handleErrorWith(failure, validHandler)).isSuccess();
    }
  }
}
