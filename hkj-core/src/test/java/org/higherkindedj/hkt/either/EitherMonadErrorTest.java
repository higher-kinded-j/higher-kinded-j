// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad — error handling")
class EitherMonadErrorTest extends EitherTestBase {

  private MonadError<EitherKind.Witness<String>, String> monadError;
  private Function<String, Kind<EitherKind.Witness<String>, Integer>> validHandler;
  private Kind<EitherKind.Witness<String>, Integer> validFallback;

  @BeforeEach
  void setUpMonadError() {
    monadError = Instances.monadError(either());
    validHandler = _ -> monadError.of(-1);
    validFallback = monadError.of(-999);
    validateMonadFixtures();
  }

  /**
   * Operations, null-argument validation and exception propagation on the MonadError instance, in a
   * single pass. The Monad/MonadError laws are verified parameterised in {@link EitherMonadTest},
   * so this contract deliberately omits {@link Category#LAWS} rather than re-running them.
   */
  @Test
  @DisplayName(
      "MonadError contract — operations, validations & exceptions (Monad laws in EitherMonadTest)")
  void monadErrorContract() {
    TypeClassContract.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
        .<Integer>instance(monadError)
        .<String>withKind(validKind)
        .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
        .withErrorHandling(validHandler, validFallback)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("handleErrorWith() recovers from Left")
    void handleErrorWithRecoversFromLeft() {
      var result = monadError.handleErrorWith(leftKind(), validHandler);
      assertThatEither(result).isRight().hasRight(-1);
    }

    @Test
    @DisplayName("handleErrorWith() passes through Right")
    void handleErrorWithPassesThroughRight() {
      var result = monadError.handleErrorWith(validKind, validHandler);
      assertThatEither(result).isRight().hasRight(DEFAULT_RIGHT_VALUE);
    }

    @Test
    @DisplayName("raiseError() creates Left")
    void raiseErrorCreatesLeft() {
      var result = monadError.raiseError(TestErrorType.VALIDATION.message());
      assertThatEither(result).isLeft().hasLeft(TestErrorType.VALIDATION.message());
    }

    @Test
    @DisplayName("recoverWith() uses fallback on Left")
    void recoverWithUsesFallbackOnLeft() {
      var result = monadError.recoverWith(leftKind(), validFallback);
      assertThatEither(result).isRight().hasRight(-999);
    }

    @Test
    @DisplayName("recover() uses value on Left")
    void recoverUsesValueOnLeft() {
      var result = monadError.recover(leftKind(), 100);
      assertThatEither(result).isRight().hasRight(100);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Error handling with null error values")
    void errorHandlingWithNullErrors() {
      Kind<EitherKind.Witness<String>, String> nullError = monadError.raiseError(null);

      // The null error flows through to the handler, which stringifies it as "null".
      Function<String, Kind<EitherKind.Witness<String>, String>> handler =
          err -> monadError.of("recovered:" + err);

      var result = monadError.handleErrorWith(nullError, handler);

      assertThatEither(result).isRight().hasRight("recovered:null");
    }

    @Test
    @DisplayName("Chained error recovery")
    void chainedErrorRecovery() {
      Kind<EitherKind.Witness<String>, Integer> start = leftKind(TestErrorType.RECOVERABLE);

      var result =
          monadError.handleErrorWith(
              start,
              err -> {
                if (err.equals(TestErrorType.RECOVERABLE.message())) {
                  return monadError.of(999);
                }
                return leftKind(TestErrorType.UNRECOVERABLE);
              });

      assertThatEither(result).isRight().hasRight(999);
    }

    @Test
    @DisplayName("Exception propagation in handleErrorWith handler")
    void exceptionPropagationInHandler() {
      Kind<EitherKind.Witness<String>, Integer> leftValue = leftKind();
      RuntimeException testException = new RuntimeException("Handler exception");

      Function<String, Kind<EitherKind.Witness<String>, Integer>> throwingHandler =
          _ -> {
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
          _ -> Either.left(TestErrorType.RESOURCE_UNAVAILABLE.message());

      Function<String, Either<String, String>> fallbackSource = _ -> Either.right("fallback-data");

      String id = "user-123";
      Either<String, String> result =
          primarySource.apply(id).fold(_ -> fallbackSource.apply(id), Either::right);

      assertThatEither(result).isRight().hasRight("fallback-data");
    }
  }
}
