// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;

import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedMonadError Complete Test Suite")
class ValidatedMonadErrorTest extends ValidatedTestBase {

  private MonadError<ValidatedKind.Witness<String>, String> monadError;

  @BeforeEach
  void setUpMonadError() {
    Semigroup<String> stringSemigroup = Semigroups.first();
    monadError = ValidatedMonad.instance(stringSemigroup);
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete MonadError test pattern")
    void runCompleteMonadErrorTestPattern() {
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> validKind(0);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = validKind(-1);

      TypeClassTest.<ValidatedKind.Witness<String>, String>monadError(ValidatedMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(handler, fallback)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Error Handling Operations")
  class ErrorHandlingOperations {

    @Test
    @DisplayName("RaiseError creates Invalid")
    void raiseErrorCreatesInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> result = monadError.raiseError(DEFAULT_ERROR);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("HandleErrorWith recovers from Invalid")
    void handleErrorWithRecoversFromInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> validKind(99);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(invalid, handler);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isValid().hasValue(99);
    }

    @Test
    @DisplayName("HandleErrorWith passes through Valid")
    void handleErrorWithPassesThroughValid() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = validKind(DEFAULT_VALID_VALUE);
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> validKind(99);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(valid, handler);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isValid().hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("HandleError recovers with pure value")
    void handleErrorRecoversWithPureValue() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Function<String, Integer> handler = error -> 100;

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleError(invalid, handler);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isValid().hasValue(100);
    }

    @Test
    @DisplayName("RecoverWith uses fallback for Invalid")
    void recoverWithUsesFallbackForInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = validKind(50);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.recoverWith(invalid, fallback);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isValid().hasValue(50);
    }

    @Test
    @DisplayName("RecoverWith ignores fallback for Valid")
    void recoverWithIgnoresFallbackForValid() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = validKind(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = validKind(50);

      Kind<ValidatedKind.Witness<String>, Integer> result = monadError.recoverWith(valid, fallback);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isValid().hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Recover uses pure value for Invalid")
    void recoverUsesPureValueForInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

      Kind<ValidatedKind.Witness<String>, Integer> result = monadError.recover(invalid, 75);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isValid().hasValue(75);
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> validKind(0);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = validKind(-1);

      TypeClassTest.<ValidatedKind.Witness<String>, String>monadError(ValidatedMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(handler, fallback)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> validKind(0);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = validKind(-1);

      TypeClassTest.<ValidatedKind.Witness<String>, String>monadError(ValidatedMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(handler, fallback)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> validKind(0);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = validKind(-1);

      TypeClassTest.<ValidatedKind.Witness<String>, String>monadError(ValidatedMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(handler, fallback)
          .testExceptions();
    }
  }

  @Nested
  @DisplayName("Validation Configuration Tests")
  class ValidationConfigurationTests {

    @Test
    @DisplayName("Test with inheritance-based validation")
    void testWithInheritanceBasedValidation() {
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler = _ -> validKind(0);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = validKind(-1);

      TypeClassTest.<ValidatedKind.Witness<String>, String>monadError(ValidatedMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(handler, fallback)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ValidatedMonad.class)
          .withApFrom(ValidatedMonad.class)
          .withFlatMapFrom(ValidatedMonad.class)
          .withHandleErrorWithFrom(ValidatedMonad.class)
          .testValidations();
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
          error -> invalidKind("transformed-error");

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(invalid, handler);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isInvalid().hasError("transformed-error");
    }

    @Test
    @DisplayName("Multiple error recovery operations can be chained")
    void multipleErrorRecoveryOperationsCanBeChained() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind("error1");

      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler1 =
          error -> invalidKind("error2");
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler2 =
          error -> validKind(DEFAULT_VALID_VALUE);

      Kind<ValidatedKind.Witness<String>, Integer> step1 =
          monadError.handleErrorWith(invalid, handler1);
      Kind<ValidatedKind.Witness<String>, Integer> step2 =
          monadError.handleErrorWith(step1, handler2);

      Validated<String, Integer> result = narrowToValidated(step2);
      assertThatValidated(result).isValid().hasValue(DEFAULT_VALID_VALUE);
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
          error -> invalidKind("handled");

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(invalid, handler);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isInvalid().hasError("handled");
    }

    @Test
    @DisplayName("RecoverWith with Invalid fallback results in Invalid")
    void recoverWithWithInvalidFallbackResultsInInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> fallback = invalidKind("fallback-error");

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.recoverWith(invalid, fallback);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isInvalid().hasError("fallback-error");
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
