// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedMonadError Complete Test Suite")
class ValidatedMonadErrorTest
    extends TypeClassTestBase<ValidatedKind.Witness<String>, Integer, String> {

    private MonadError<ValidatedKind.Witness<String>, String> monadError;

    @Override
  protected Kind<ValidatedKind.Witness<String>, Integer> createValidKind() {
    return VALIDATED.widen(Validated.valid(42));
  }

  @Override
  protected Kind<ValidatedKind.Witness<String>, Integer> createValidKind2() {
    return VALIDATED.widen(Validated.valid(24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected Function<Integer, Kind<ValidatedKind.Witness<String>, String>>
  createValidFlatMapper() {
    return n -> VALIDATED.widen(Validated.valid("Mapped: " + n));
  }

  @Override
  protected Kind<ValidatedKind.Witness<String>, Function<Integer, String>>
  createValidFunctionKind() {
    Function<Integer, String> fn = n -> "Function: " + n;
    return VALIDATED.widen(Validated.valid(fn));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Combined: " + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return 100;
  }

  @Override
  protected Function<Integer, Kind<ValidatedKind.Witness<String>, String>> createTestFunction() {
    return n -> VALIDATED.widen(Validated.valid("Test: " + n));
  }

  @Override
  protected Function<String, Kind<ValidatedKind.Witness<String>, String>> createChainFunction() {
    return s -> VALIDATED.widen(Validated.valid("[" + s + "]"));
  }

  @Override
  protected BiPredicate <Kind<ValidatedKind.Witness<String>, ?>, Kind<ValidatedKind.Witness<String>, ?>>
  createEqualityChecker() {
    return (k1, k2) -> {
      Validated<String, ?> v1 = VALIDATED.narrow(k1);
      Validated<String, ?> v2 = VALIDATED.narrow(k2);
      return v1.equals(v2);
    };
  }

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
                  error -> VALIDATED.widen(Validated.valid(0));
          Kind<ValidatedKind.Witness<String>, Integer> fallback = VALIDATED.widen(Validated.valid(-1));

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
      Kind<ValidatedKind.Witness<String>, Integer> result = monadError.raiseError("test-error");

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("test-error");
    }

    @Test
    @DisplayName("HandleErrorWith recovers from Invalid")
    void handleErrorWithRecoversFromInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("error"));
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> VALIDATED.widen(Validated.valid(99));

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(invalid, handler);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(99);
    }

    @Test
    @DisplayName("HandleErrorWith passes through Valid")
    void handleErrorWithPassesThroughValid() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = VALIDATED.widen(Validated.valid(42));
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> VALIDATED.widen(Validated.valid(99));

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(valid, handler);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("HandleError recovers with pure value")
    void handleErrorRecoversWithPureValue() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("error"));
      Function<String, Integer> handler = error -> 100;

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleError(invalid, handler);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(100);
    }

    @Test
    @DisplayName("RecoverWith uses fallback for Invalid")
    void recoverWithUsesFallbackForInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("error"));
      Kind<ValidatedKind.Witness<String>, Integer> fallback = VALIDATED.widen(Validated.valid(50));

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.recoverWith(invalid, fallback);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(50);
    }

    @Test
    @DisplayName("RecoverWith ignores fallback for Valid")
    void recoverWithIgnoresFallbackForValid() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = VALIDATED.widen(Validated.valid(42));
      Kind<ValidatedKind.Witness<String>, Integer> fallback = VALIDATED.widen(Validated.valid(50));

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.recoverWith(valid, fallback);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Recover uses pure value for Invalid")
    void recoverUsesPureValueForInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("error"));

      Kind<ValidatedKind.Witness<String>, Integer> result = monadError.recover(invalid, 75);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(75);
    }
  }

    @Nested
    @DisplayName("Individual Component Tests")
    class IndividualComponentTests {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
                    error -> VALIDATED.widen(Validated.valid(0));
            Kind<ValidatedKind.Witness<String>, Integer> fallback = VALIDATED.widen(Validated.valid(-1));

            TypeClassTest.<ValidatedKind.Witness<String>, String>monadError(ValidatedMonad.class)
                    .<Integer>instance(monadError)
                    .<String>withKind(validKind)
                    .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)  // Fixed
                    .withErrorHandling(handler, fallback)
                    .testOperations();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
                    error -> VALIDATED.widen(Validated.valid(0));
            Kind<ValidatedKind.Witness<String>, Integer> fallback = VALIDATED.widen(Validated.valid(-1));

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
                    error -> VALIDATED.widen(Validated.valid(0));
            Kind<ValidatedKind.Witness<String>, Integer> fallback = VALIDATED.widen(Validated.valid(-1));

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
          Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
                  _ -> VALIDATED.widen(Validated.valid(0));
          Kind<ValidatedKind.Witness<String>, Integer> fallback = VALIDATED.widen(Validated.valid(-1));

          TypeClassTest.<ValidatedKind.Witness<String>, String>monadError(ValidatedMonad.class)
                  .<Integer>instance(monadError)
                  .<String>withKind(validKind)
                  .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)  // Fixed
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
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("original-error"));
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> VALIDATED.widen(Validated.invalid("transformed-error"));

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(invalid, handler);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("transformed-error");
    }

    @Test
    @DisplayName("Multiple error recovery operations can be chained")
    void multipleErrorRecoveryOperationsCanBeChained() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("error1"));

      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler1 =
          error -> VALIDATED.widen(Validated.invalid("error2"));
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler2 =
          error -> VALIDATED.widen(Validated.valid(42));

      Kind<ValidatedKind.Witness<String>, Integer> step1 =
          monadError.handleErrorWith(invalid, handler1);
      Kind<ValidatedKind.Witness<String>, Integer> step2 =
          monadError.handleErrorWith(step1, handler2);

      Validated<String, Integer> result = VALIDATED.narrow(step2);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("HandleErrorWith with handler returning Invalid preserves error")
    void handleErrorWithWithHandlerReturningInvalidPreservesError() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("original"));
      Function<String, Kind<ValidatedKind.Witness<String>, Integer>> handler =
          error -> VALIDATED.widen(Validated.invalid("handled"));

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.handleErrorWith(invalid, handler);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("handled");
    }

    @Test
    @DisplayName("RecoverWith with Invalid fallback results in Invalid")
    void recoverWithWithInvalidFallbackResultsInInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("error"));
      Kind<ValidatedKind.Witness<String>, Integer> fallback =
          VALIDATED.widen(Validated.invalid("fallback-error"));

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monadError.recoverWith(invalid, fallback);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("fallback-error");
    }

      @Test
      @DisplayName("Recover with null value throws exception")
      void recoverWithNullValueThrowsException() {
          Kind<ValidatedKind.Witness<String>, Integer> invalid =
                  VALIDATED.widen(Validated.invalid("error"));

          // Recover would use monad.of(null) internally, which doesn't allow null
          assertThatThrownBy(() -> monadError.recover(invalid, null))
                  .isInstanceOf(NullPointerException.class)
                  .hasMessageContaining("value cannot be null");
      }
  }
}