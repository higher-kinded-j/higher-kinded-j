// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeMonad — error handling")
class MaybeMonadErrorTest extends MaybeTestBase {

  private MonadError<MaybeKind.Witness, Unit> monadError;
  private Function<Unit, Kind<MaybeKind.Witness, Integer>> validHandler;
  private Kind<MaybeKind.Witness, Integer> validFallback;

  @BeforeEach
  void setUpMonadError() {
    monadError = Instances.monadError(maybe());
    validHandler = _ -> monadError.of(-1);
    validFallback = monadError.of(-999);
    validateMonadFixtures();
  }

  /**
   * Operations, null-argument validation and exception propagation on the MonadError instance, in a
   * single pass. The Monad/MonadError laws are verified parameterised in {@link MaybeMonadTest}, so
   * this contract deliberately omits {@link Category#LAWS} rather than re-running them.
   */
  @Test
  @DisplayName(
      "MonadError contract — operations, validations & exceptions (Monad laws in MaybeMonadTest)")
  void monadErrorContract() {
    TypeClassContract.<MaybeKind.Witness, Unit>monadError(MaybeMonad.class)
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
    @DisplayName("handleErrorWith() recovers from Nothing")
    void handleErrorWithRecoversFromNothing() {
      var result = monadError.handleErrorWith(nothingKind(), validHandler);
      assertThatMaybe(result).isJust().hasValue(-1);
    }

    @Test
    @DisplayName("handleErrorWith() passes through Just")
    void handleErrorWithPassesThroughJust() {
      var result = monadError.handleErrorWith(validKind, validHandler);
      assertThat(result).isSameAs(validKind);
      assertThatMaybe(result).isJust().hasValue(DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("raiseError() creates Nothing")
    void raiseErrorCreatesNothing() {
      var result = monadError.raiseError(Unit.INSTANCE);
      assertThatMaybe(result).isNothing();
    }

    @Test
    @DisplayName("raiseError() with null Unit still creates Nothing")
    void raiseErrorWithNullCreatesNothing() {
      var result = monadError.raiseError(null);
      assertThatMaybe(result).isNothing();
    }

    @Test
    @DisplayName("recoverWith() uses fallback on Nothing")
    void recoverWithUsesFallbackOnNothing() {
      var result = monadError.recoverWith(nothingKind(), validFallback);
      assertThat(result).isSameAs(validFallback);
      assertThatMaybe(result).isJust().hasValue(-999);
    }

    @Test
    @DisplayName("recoverWith() passes through Just")
    void recoverWithPassesThroughJust() {
      var result = monadError.recoverWith(validKind, validFallback);
      assertThat(result).isSameAs(validKind);
    }

    @Test
    @DisplayName("recover() uses value on Nothing")
    void recoverUsesValueOnNothing() {
      var result = monadError.recover(nothingKind(), 100);
      assertThatMaybe(result).isJust().hasValue(100);
    }

    @Test
    @DisplayName("recover() passes through Just")
    void recoverPassesThroughJust() {
      // recover uses handleError, which creates a new Maybe rather than reusing the input
      var result = monadError.recover(validKind, 100);
      assertThatMaybe(result).isJust().hasValue(DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("recover() with null value creates Nothing (fromNullable semantics)")
    void recoverWithNullValueCreatesNothing() {
      var result = monadError.recover(nothingKind(), null);
      assertThatMaybe(result).isNothing();
    }

    @Test
    @DisplayName("handleError() transforms error to value")
    void handleErrorTransformsErrorToValue() {
      Function<Unit, Integer> errorHandler = _ -> 999;
      var result = monadError.handleError(nothingKind(), errorHandler);
      assertThatMaybe(result).isJust().hasValue(999);
    }

    @Test
    @DisplayName("zero() returns Nothing")
    void zeroReturnsNothing() {
      var zero = Instances.monadZero(maybe()).zero();
      assertThatMaybe(zero).isNothing();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Error handling with Unit.INSTANCE")
    void errorHandlingWithUnitInstance() {
      Kind<MaybeKind.Witness, Integer> nothing = monadError.raiseError(Unit.INSTANCE);
      Function<Unit, Kind<MaybeKind.Witness, Integer>> handler = _ -> monadError.of(0);

      Kind<MaybeKind.Witness, Integer> result = monadError.handleErrorWith(nothing, handler);
      assertThatMaybe(result).isJust().hasValue(0);
    }

    @Test
    @DisplayName("Chained error recovery")
    void chainedErrorRecovery() {
      Kind<MaybeKind.Witness, Integer> result =
          monadError.handleErrorWith(
              nothingKind(),
              _ -> {
                // First recovery attempt - also fails
                Kind<MaybeKind.Witness, Integer> firstAttempt =
                    monadError.raiseError(Unit.INSTANCE);
                // Second recovery - succeeds
                return monadError.handleErrorWith(firstAttempt, _ -> monadError.of(999));
              });
      assertThatMaybe(result).isJust().hasValue(999);
    }

    @Test
    @DisplayName("Exception propagation in handleErrorWith handler")
    void exceptionPropagationInHandler() {
      RuntimeException testException = new RuntimeException("Handler exception");
      Function<Unit, Kind<MaybeKind.Witness, Integer>> throwingHandler =
          _ -> {
            throw testException;
          };

      assertThatThrownBy(() -> monadError.handleErrorWith(nothingKind(), throwingHandler))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Mixing map, flatMap, and error recovery")
    void mixingMapFlatMapAndErrorRecovery() {
      var start = justKind(10);

      Kind<MaybeKind.Witness, String> result =
          monadError.flatMap(
              i -> {
                if (i < 5) {
                  return monadError.raiseError(Unit.INSTANCE);
                }
                return monadError.map(x -> "Value:" + x, monadError.of(i * 2));
              },
              start);
      result = monadError.handleErrorWith(result, _ -> monadError.of("Recovered"));

      assertThatMaybe(result).isJust().hasValue("Value:20");
    }

    @Test
    @DisplayName("Deep error recovery chain")
    void deepErrorRecoveryChain() {
      Kind<MaybeKind.Witness, Integer> result = nothingKind();
      for (int i = 0; i < 10; i++) {
        final int index = i;
        result =
            monadError.handleErrorWith(
                result,
                _ -> {
                  if (index < 9) {
                    return monadError.raiseError(Unit.INSTANCE); // Keep failing
                  }
                  return monadError.of(100); // Finally succeed
                });
      }
      assertThatMaybe(result).isJust().hasValue(100);
    }

    @Test
    @DisplayName("recoverWith with Nothing fallback")
    void recoverWithNothingFallback() {
      Kind<MaybeKind.Witness, Integer> nothingFallback = nothingKind();

      Kind<MaybeKind.Witness, Integer> result =
          monadError.recoverWith(nothingKind(), nothingFallback);
      assertThat(result).isSameAs(nothingFallback);
      assertThatMaybe(result).isNothing();
    }

    @Test
    @DisplayName("Multiple error recovery strategies")
    void multipleErrorRecoveryStrategies() {
      Kind<MaybeKind.Witness, Integer> strategy1 =
          monadError.handleErrorWith(nothingKind(), _ -> monadError.of(1));
      Kind<MaybeKind.Witness, Integer> strategy2 =
          monadError.recoverWith(nothingKind(), monadError.of(2));
      Kind<MaybeKind.Witness, Integer> strategy3 = monadError.recover(nothingKind(), 3);

      assertThatMaybe(strategy1).isJust().hasValue(1);
      assertThatMaybe(strategy2).isJust().hasValue(2);
      assertThatMaybe(strategy3).isJust().hasValue(3);
    }
  }

  @Nested
  @DisplayName("Integration Examples")
  class IntegrationExamples {

    @Test
    @DisplayName("Real-world scenario: validation pipeline")
    void validationPipeline() {
      Function<Integer, Maybe<Integer>> validatePositive =
          i -> i > 0 ? Maybe.just(i) : Maybe.nothing();
      Function<Integer, Maybe<Integer>> validateRange =
          i -> i <= 100 ? Maybe.just(i) : Maybe.nothing();
      Function<Integer, Maybe<String>> format = i -> Maybe.just("Valid: " + i);

      Maybe<String> success = validatePositive.apply(50).flatMap(validateRange).flatMap(format);
      assertThatMaybe(success).isJust().hasValue("Valid: 50");

      Maybe<String> failure = validatePositive.apply(-5).flatMap(validateRange).flatMap(format);
      assertThatMaybe(failure).isNothing();
    }

    @Test
    @DisplayName("Real-world scenario: error recovery with fallback")
    void errorRecoveryWithFallback() {
      Function<String, Maybe<String>> primarySource = _ -> Maybe.nothing();
      Function<String, Maybe<String>> fallbackSource = _ -> Maybe.just("fallback-data");

      String id = "user-123";
      Maybe<String> result =
          primarySource
              .apply(id)
              .map(Maybe::just) // If primary succeeds
              .orElseGet(() -> fallbackSource.apply(id)); // If primary fails, try fallback

      assertThatMaybe(result).isJust().hasValue("fallback-data");
    }
  }

  @Nested
  @DisplayName("MonadZero Tests")
  class MonadZeroTests {

    @Test
    @DisplayName("zero() is consistent with raiseError()")
    void zeroIsConsistentWithRaiseError() {
      var zero = Instances.monadZero(maybe()).zero();
      var raised = monadError.raiseError(Unit.INSTANCE);

      assertThatMaybe(zero).isNothing();
      assertThatMaybe(raised).isNothing();
      assertThat(narrowToMaybe(zero)).isEqualTo(narrowToMaybe(raised));
    }

    @Test
    @DisplayName("zero() can be recovered")
    void zeroCanBeRecovered() {
      Kind<MaybeKind.Witness, Integer> zero = Instances.monadZero(maybe()).zero();

      var recovered = monadError.handleErrorWith(zero, _ -> monadError.of(0));
      assertThatMaybe(recovered).isJust().hasValue(0);
    }
  }
}
