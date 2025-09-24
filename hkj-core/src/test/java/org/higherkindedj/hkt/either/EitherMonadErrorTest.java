// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.test.assertions.MonadAssertions.*;
import static org.higherkindedj.hkt.test.data.TestExceptions.*;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad Error Handling Tests")
class EitherMonadErrorTest {

  record TestError(String code) {}

  private EitherMonad<TestError> monadError;

  @BeforeEach
  void setUp() {
    monadError = EitherMonad.instance();
  }

  // Helper methods
  private <R> Either<TestError, R> narrow(Kind<EitherKind.Witness<TestError>, R> kind) {
    return EITHER.narrow(kind);
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> right(R value) {
    return EITHER.widen(Either.right(value));
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> left(String errorCode) {
    return EITHER.widen(Either.left(new TestError(errorCode)));
  }

  @Nested
  @DisplayName("RaiseError Operation Tests")
  class RaiseErrorOperationTests {

    @Test
    @DisplayName("raiseError() creates Left instances")
    void raiseErrorCreatesLeftInstances() {
      TestError error = new TestError("E500");
      Kind<EitherKind.Witness<TestError>, Integer> errorKind = monadError.raiseError(error);

      Either<TestError, Integer> either = narrow(errorKind);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(error);
    }

    @Test
    @DisplayName("raiseError() accepts null error values")
    void raiseErrorAcceptsNullErrors() {
      Kind<EitherKind.Witness<TestError>, Integer> errorKind = monadError.raiseError(null);

      Either<TestError, Integer> either = narrow(errorKind);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isNull();
    }

    @Test
    @DisplayName("raiseError() with different result types")
    void raiseErrorWithDifferentTypes() {
      TestError error = new TestError("E404");

      Kind<EitherKind.Witness<TestError>, String> stringError = monadError.raiseError(error);
      Kind<EitherKind.Witness<TestError>, Integer> intError = monadError.raiseError(error);
      Kind<EitherKind.Witness<TestError>, Boolean> boolError = monadError.raiseError(error);

      assertThat(narrow(stringError).getLeft()).isEqualTo(error);
      assertThat(narrow(intError).getLeft()).isEqualTo(error);
      assertThat(narrow(boolError).getLeft()).isEqualTo(error);
    }
  }

  @Nested
  @DisplayName("HandleErrorWith Operation Tests")
  class HandleErrorWithOperationTests {

    @Test
    @DisplayName("handleErrorWith() recovers from Left to Right")
    void handleErrorWithRecoversFromLeftToRight() {
      Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("E404");

      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> handler =
          err -> err.code().equals("E404") ? right(-1) : right(-999);

      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.handleErrorWith(leftValue, handler);

      assertThat(narrow(result).getRight()).isEqualTo(-1);
    }

    @Test
    @DisplayName("handleErrorWith() passes through Right unchanged")
    void handleErrorWithPassesThroughRight() {
      Kind<EitherKind.Witness<TestError>, Integer> rightValue = right(100);

      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> handler = err -> right(-1);

      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.handleErrorWith(rightValue, handler);

      assertThat(result).isSameAs(rightValue);
      assertThat(narrow(result).getRight()).isEqualTo(100);
    }

    @Test
    @DisplayName("handleErrorWith() converts Left to another Left")
    void handleErrorWithConvertsLeftToLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("E404");

      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> handler =
          err -> left("HANDLED_" + err.code());

      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.handleErrorWith(leftValue, handler);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("HANDLED_E404"));
    }

    @Test
    @DisplayName("handleErrorWith() handles null error values")
    void handleErrorWithHandlesNullErrors() {
      Kind<EitherKind.Witness<TestError>, Integer> nullError = monadError.raiseError(null);

      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> handler =
          err -> right(err == null ? 0 : -1);

      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.handleErrorWith(nullError, handler);

      assertThat(narrow(result).getRight()).isEqualTo(0);
    }

    @Test
    @DisplayName("handleErrorWith() null validations")
    void handleErrorWithNullValidations() {
      Kind<EitherKind.Witness<TestError>, Integer> validKind = right(42);
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> validHandler =
          err -> right(-1);

      assertHandleErrorWithKindNull(() -> monadError.handleErrorWith(null, validHandler));
      assertHandleErrorWithHandlerNull(() -> monadError.handleErrorWith(validKind, null));
    }
  }

  @Nested
  @DisplayName("HandleError Operation Tests")
  class HandleErrorOperationTests {

    @Test
    @DisplayName("handleError() recovers with pure value")
    void handleErrorRecoversWithPureValue() {
      Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("E404");

      Function<TestError, Integer> handler = err -> err.code().equals("E404") ? -1 : -999;

      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.handleError(leftValue, handler);

      assertThat(narrow(result).getRight()).isEqualTo(-1);
    }

    @Test
    @DisplayName("handleError() passes through Right unchanged")
    void handleErrorPassesThroughRight() {
      Kind<EitherKind.Witness<TestError>, Integer> rightValue = right(100);

      Function<TestError, Integer> handler = err -> -1;

      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.handleError(rightValue, handler);

      assertThat(narrow(result).getRight()).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("RecoverWith Operation Tests")
  class RecoverWithOperationTests {

    @Test
    @DisplayName("recoverWith() uses fallback for Left")
    void recoverWithUsesFallbackForLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("E404");
      Kind<EitherKind.Witness<TestError>, Integer> fallback = right(-1);

      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.recoverWith(leftValue, fallback);

      assertThat(narrow(result).getRight()).isEqualTo(-1);
    }

    @Test
    @DisplayName("recoverWith() returns original for Right")
    void recoverWithReturnsOriginalForRight() {
      Kind<EitherKind.Witness<TestError>, Integer> rightValue = right(100);
      Kind<EitherKind.Witness<TestError>, Integer> fallback = right(-1);

      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.recoverWith(rightValue, fallback);

      assertThat(result).isSameAs(rightValue);
      assertThat(narrow(result).getRight()).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("Recover Operation Tests")
  class RecoverOperationTests {

    @Test
    @DisplayName("recover() uses default value for Left")
    void recoverUsesDefaultValueForLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("E404");

      Kind<EitherKind.Witness<TestError>, Integer> result = monadError.recover(leftValue, -1);

      assertThat(narrow(result).getRight()).isEqualTo(-1);
    }

    @Test
    @DisplayName("recover() returns original for Right")
    void recoverReturnsOriginalForRight() {
      Kind<EitherKind.Witness<TestError>, Integer> rightValue = right(100);

      Kind<EitherKind.Witness<TestError>, Integer> result = monadError.recover(rightValue, -1);

      assertThat(result).isSameAs(rightValue);
      assertThat(narrow(result).getRight()).isEqualTo(100);
    }

    @Test
    @DisplayName("recover() accepts null default value")
    void recoverAcceptsNullDefaultValue() {
      Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("E404");

      Kind<EitherKind.Witness<TestError>, Integer> result = monadError.recover(leftValue, null);

      assertThat(narrow(result).getRight()).isNull();
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("handleErrorWith() propagates handler exceptions")
    void handleErrorWithPropagatesHandlerExceptions() {
      RuntimeException testException = runtime("handler test");
      Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("E404");

      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> throwingHandler =
          err -> {
            throw testException;
          };

      assertThatThrownBy(() -> monadError.handleErrorWith(leftValue, throwingHandler))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("handleError() propagates handler exceptions")
    void handleErrorPropagatesHandlerExceptions() {
      RuntimeException testException = runtime("handler test");
      Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("E404");

      Function<TestError, Integer> throwingHandler =
          err -> {
            throw testException;
          };

      assertThatThrownBy(() -> monadError.handleError(leftValue, throwingHandler))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Right values don't trigger handler exceptions")
    void rightValuesDontTriggerHandlerExceptions() {
      RuntimeException testException = runtime("should not throw");
      Kind<EitherKind.Witness<TestError>, Integer> rightValue = right(100);

      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> throwingHandler =
          err -> {
            throw testException;
          };

      // Should not throw because handler is not called for Right
      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.handleErrorWith(rightValue, throwingHandler);

      assertThat(result).isSameAs(rightValue);
    }
  }

  @Nested
  @DisplayName("Error Recovery Patterns Tests")
  class ErrorRecoveryPatternsTests {

    @Test
    @DisplayName("Chained error handling")
    void chainedErrorHandling() {
      Kind<EitherKind.Witness<TestError>, Integer> start = left("RECOVERABLE");

      Kind<EitherKind.Witness<TestError>, Integer> result =
          monadError.handleErrorWith(
              start,
              err -> {
                if (err.code().equals("RECOVERABLE")) {
                  return right(999);
                }
                return left("UNRECOVERABLE");
              });

      assertThat(narrow(result).getRight()).isEqualTo(999);
    }

    @Test
    @DisplayName("Multi-level error handling")
    void multiLevelErrorHandling() {
      Kind<EitherKind.Witness<TestError>, Integer> start = left("FIRST_ERROR");

      // First handler converts to another error
      Kind<EitherKind.Witness<TestError>, Integer> step1 =
          monadError.handleErrorWith(start, err -> left("SECOND_ERROR"));

      // Second handler recovers
      Kind<EitherKind.Witness<TestError>, Integer> step2 =
          monadError.handleErrorWith(step1, err -> right(-1));

      assertThat(narrow(step2).getRight()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Conditional error recovery")
    void conditionalErrorRecovery() {
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> conditionalHandler =
          err -> {
            return switch (err.code()) {
              case "E404" -> right(-1);
              case "E500" -> right(-2);
              default -> left("UNHANDLED");
            };
          };

      Kind<EitherKind.Witness<TestError>, Integer> e404 = left("E404");
      Kind<EitherKind.Witness<TestError>, Integer> e500 = left("E500");
      Kind<EitherKind.Witness<TestError>, Integer> eOther = left("E999");

      assertThat(narrow(monadError.handleErrorWith(e404, conditionalHandler)).getRight())
          .isEqualTo(-1);
      assertThat(narrow(monadError.handleErrorWith(e500, conditionalHandler)).getRight())
          .isEqualTo(-2);
      assertThat(narrow(monadError.handleErrorWith(eOther, conditionalHandler)).getLeft())
          .isEqualTo(new TestError("UNHANDLED"));
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Error handling with complex error types")
    void errorHandlingWithComplexErrorTypes() {
      record ComplexError(String code, String message, int severity) {}

      EitherMonad<ComplexError> complexMonad = EitherMonad.instance();
      ComplexError error = new ComplexError("E500", "Server Error", 5);

      Kind<EitherKind.Witness<ComplexError>, Integer> errorKind = complexMonad.raiseError(error);

      Kind<EitherKind.Witness<ComplexError>, Integer> recovered =
          complexMonad.handleErrorWith(
              errorKind,
              err -> {
                if (err.severity() > 3) {
                  return complexMonad.of(-999);
                }
                return complexMonad.of(0);
              });

      Either<ComplexError, Integer> result = EITHER.narrow(recovered);
      assertThat(result.getRight()).isEqualTo(-999);
    }

    @Test
    @DisplayName("Error handling preserves Right type")
    void errorHandlingPreservesRightType() {
      Kind<EitherKind.Witness<TestError>, List<String>> rightList =
          monadError.of(List.of("a", "b", "c"));

      Kind<EitherKind.Witness<TestError>, List<String>> handled =
          monadError.handleErrorWith(rightList, err -> monadError.of(List.of()));

      Either<TestError, List<String>> result = EITHER.narrow(handled);
      assertThat(result.getRight()).containsExactly("a", "b", "c");
    }
  }
}
