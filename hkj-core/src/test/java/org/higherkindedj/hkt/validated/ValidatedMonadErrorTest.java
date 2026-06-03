// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedMonad — error handling")
class ValidatedMonadErrorTest extends ValidatedTestBase {

  private MonadError<ValidatedKind.Witness<String>, String> monadError;
  private Function<String, Kind<ValidatedKind.Witness<String>, Integer>> validHandler;
  private Kind<ValidatedKind.Witness<String>, Integer> validFallback;

  @BeforeEach
  void setUpMonadError() {
    monadError = Instances.validated(Semigroups.first());
    validHandler = _ -> validKind(0);
    validFallback = validKind(-1);
  }

  /**
   * Operations, null-argument validation and exception propagation on the MonadError instance, in a
   * single pass. The Monad/MonadError laws are verified parameterised in {@link
   * ValidatedMonadTest}, so this contract deliberately omits {@link Category#LAWS} rather than
   * re-running them.
   */
  @Test
  @DisplayName(
      "MonadError contract — operations, validations & exceptions (Monad laws in ValidatedMonadTest)")
  void monadErrorContract() {
    TypeClassContract.<ValidatedKind.Witness<String>, String>monadError(ValidatedMonad.class)
        .<Integer>instance(monadError)
        .<String>withKind(validKind)
        .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
        .withErrorHandling(validHandler, validFallback)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Error Handling Operations")
  class ErrorHandlingOperations {

    @Test
    @DisplayName("RaiseError creates Invalid")
    void raiseErrorCreatesInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> result = monadError.raiseError(DEFAULT_ERROR);
      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("HandleErrorWith recovers from Invalid")
    void handleErrorWithRecoversFromInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler = _ -> validKind(99);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(invalid, handler);
      assertThatValidated(result).isValid().hasValue(99);
    }

    @Test
    @DisplayName("HandleErrorWith passes through Valid")
    void handleErrorWithPassesThroughValid() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = validKind(DEFAULT_VALID_VALUE);
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler = _ -> validKind(99);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(valid, handler);
      assertThatValidated(result).isValid().hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("HandleError recovers with pure value")
    void handleErrorRecoversWithPureValue() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Function<String, Integer> handler = _ -> 100;

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleError(invalid, handler);
      assertThatValidated(result).isValid().hasValue(100);
    }

    @Test
    @DisplayName("RecoverWith uses fallback for Invalid")
    void recoverWithUsesFallbackForInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = validKind(50);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.recoverWith(invalid, fallback);
      assertThatValidated(result).isValid().hasValue(50);
    }

    @Test
    @DisplayName("RecoverWith ignores fallback for Valid")
    void recoverWithIgnoresFallbackForValid() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = validKind(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = validKind(50);

      Kind<ValidatedKind.Witness<String>, Integer> result = monadError.recoverWith(valid, fallback);
      assertThatValidated(result).isValid().hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Recover uses pure value for Invalid")
    void recoverUsesPureValueForInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

      Kind<ValidatedKind.Witness<String>, Integer> result = monadError.recover(invalid, 75);
      assertThatValidated(result).isValid().hasValue(75);
    }
  }

  @Nested
  @DisplayName("Error Recovery Scenarios")
  class ErrorRecoveryScenarios {

    @Test
    @DisplayName("Handler can transform error into different error")
    void handlerCanTransformErrorIntoDifferentError() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind("original-error");
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          _ -> invalidKind("transformed-error");

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(invalid, handler);
      assertThatValidated(result).isInvalid().hasError("transformed-error");
    }

    @Test
    @DisplayName("Multiple error recovery operations can be chained")
    void multipleErrorRecoveryOperationsCanBeChained() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind("error1");
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler1 =
          _ -> invalidKind("error2");
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler2 =
          _ -> validKind(DEFAULT_VALID_VALUE);

      Kind<ValidatedKind.Witness<String>, Integer> step1 =
          monadError.handleErrorWith(invalid, handler1);
      Kind<ValidatedKind.Witness<String>, Integer> step2 =
          monadError.handleErrorWith(step1, handler2);
      assertThatValidated(step2).isValid().hasValue(DEFAULT_VALID_VALUE);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("HandleErrorWith with handler returning Invalid preserves error")
    void handleErrorWithWithHandlerReturningInvalidPreservesError() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind("original");
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          _ -> invalidKind("handled");

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(invalid, handler);
      assertThatValidated(result).isInvalid().hasError("handled");
    }

    @Test
    @DisplayName("RecoverWith with Invalid fallback results in Invalid")
    void recoverWithWithInvalidFallbackResultsInInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = invalidKind("fallback-error");

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.recoverWith(invalid, fallback);
      assertThatValidated(result).isInvalid().hasError("fallback-error");
    }

    @Test
    @DisplayName("Recover with null value throws exception")
    void recoverWithNullValueThrowsException() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

      // Recover would use monad.of(null) internally, which doesn't allow null
      assertThatThrownBy(() -> monadError.recover(invalid, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("value cannot be null");
    }
  }
}
