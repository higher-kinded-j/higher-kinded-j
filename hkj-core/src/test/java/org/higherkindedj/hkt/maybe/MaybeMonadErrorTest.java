// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MaybeMonad Error Handling Test Suite using new TypeClassTest API.
 *
 * <p>Demonstrates migration to the new fluent API whilst maintaining full test coverage.
 */
@DisplayName("MaybeMonad Error Handling Complete Test Suite")
class MaybeMonadErrorTest extends MaybeTestBase {

  private MaybeMonad monadError;
  private Function<Unit, Kind<MaybeKind.Witness, Integer>> validHandler;
  private Kind<MaybeKind.Witness, Integer> validFallback;

  @BeforeEach
  void setUpMonadError() {
    monadError = MaybeMonad.INSTANCE;
    validHandler = unit -> monadError.of(-1);
    validFallback = monadError.of(-999);
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Test Suite Using New API")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete MonadError test pattern")
    void runCompleteMonadErrorTestPattern() {
      TypeClassTest.<MaybeKind.Witness, Unit>monadError(MaybeMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .withFlatMapFrom(MaybeMonad.class)
          .withHandleErrorWithFrom(MaybeMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Selective testing - operations and laws only")
    void selectiveTestingOperationsAndLaws() {
      TypeClassTest.<MaybeKind.Witness, Unit>monadError(MaybeMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .selectTests()
          .skipValidations()
          .skipExceptions()
          .test();
    }

    @Test
    @DisplayName("Quick smoke test - operations only")
    void quickSmokeTest() {
      TypeClassTest.<MaybeKind.Witness, Unit>monadError(MaybeMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .withFlatMapFrom(MaybeMonad.class)
          .withHandleErrorWithFrom(MaybeMonad.class)
          .testOperationsAndValidations();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(MaybeMonadErrorTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("handleErrorWith() recovers from Nothing")
    void handleErrorWithRecoversFromNothing() {
      Kind<MaybeKind.Witness, Integer> nothingValue = nothingKind();

      Kind<MaybeKind.Witness, Integer> result =
          monadError.handleErrorWith(nothingValue, validHandler);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(-1);
    }

    @Test
    @DisplayName("handleErrorWith() passes through Just")
    void handleErrorWithPassesThroughJust() {
      Kind<MaybeKind.Witness, Integer> result = monadError.handleErrorWith(validKind, validHandler);

      assertThat(result).isSameAs(validKind);
      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("raiseError() creates Nothing")
    void raiseErrorCreatesNothing() {
      Kind<MaybeKind.Witness, Integer> result = monadError.raiseError(Unit.INSTANCE);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("raiseError() with null Unit still creates Nothing")
    void raiseErrorWithNullCreatesNothing() {
      Kind<MaybeKind.Witness, Integer> result = monadError.raiseError(null);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("recoverWith() uses fallback on Nothing")
    void recoverWithUsesFallbackOnNothing() {
      Kind<MaybeKind.Witness, Integer> nothingValue = nothingKind();

      Kind<MaybeKind.Witness, Integer> result = monadError.recoverWith(nothingValue, validFallback);

      assertThat(result).isSameAs(validFallback);
      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(-999);
    }

    @Test
    @DisplayName("recoverWith() passes through Just")
    void recoverWithPassesThroughJust() {
      Kind<MaybeKind.Witness, Integer> result = monadError.recoverWith(validKind, validFallback);

      assertThat(result).isSameAs(validKind);
    }

    @Test
    @DisplayName("recover() uses value on Nothing")
    void recoverUsesValueOnNothing() {
      Kind<MaybeKind.Witness, Integer> nothingValue = nothingKind();

      Kind<MaybeKind.Witness, Integer> result = monadError.recover(nothingValue, 100);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(100);
    }

    @Test
    @DisplayName("recover() passes through Just")
    void recoverPassesThroughJust() {
      Kind<MaybeKind.Witness, Integer> result = monadError.recover(validKind, 100);

      // recover uses handleError which creates a new Maybe
      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("recover() with null value creates Just(null)")
    void recoverWithNullValueCreatesJustNull() {
      Kind<MaybeKind.Witness, Integer> nothingValue = nothingKind();

      Kind<MaybeKind.Witness, Integer> result = monadError.recover(nothingValue, null);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue(); // fromNullable(null) creates Nothing
    }

    @Test
    @DisplayName("handleError() transforms error to value")
    void handleErrorTransformsErrorToValue() {
      Kind<MaybeKind.Witness, Integer> nothingValue = nothingKind();

      Function<Unit, Integer> errorHandler = unit -> 999;
      Kind<MaybeKind.Witness, Integer> result = monadError.handleError(nothingValue, errorHandler);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(999);
    }

    @Test
    @DisplayName("zero() returns Nothing")
    void zeroReturnsNothing() {
      Kind<MaybeKind.Witness, Integer> zero = monadError.zero();

      Maybe<Integer> maybe = narrowToMaybe(zero);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<MaybeKind.Witness, Unit>monadError(MaybeMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<MaybeKind.Witness, Unit>monadError(MaybeMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .withFlatMapFrom(MaybeMonad.class)
          .withHandleErrorWithFrom(MaybeMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<MaybeKind.Witness, Unit>monadError(MaybeMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<MaybeKind.Witness, Unit>monadError(MaybeMonad.class)
          .<Integer>instance(monadError)
          .<String>withKind(validKind)
          .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
          .withErrorHandling(validHandler, validFallback)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Error handling with Unit.INSTANCE")
    void errorHandlingWithUnitInstance() {
      Kind<MaybeKind.Witness, Integer> nothing = monadError.raiseError(Unit.INSTANCE);

      Function<Unit, Kind<MaybeKind.Witness, Integer>> handler = unit -> monadError.of(0);

      Kind<MaybeKind.Witness, Integer> result = monadError.handleErrorWith(nothing, handler);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Chained error recovery")
    void chainedErrorRecovery() {
      Kind<MaybeKind.Witness, Integer> start = nothingKind();

      Kind<MaybeKind.Witness, Integer> result =
          monadError.handleErrorWith(
              start,
              unit -> {
                // First recovery attempt - also fails
                Kind<MaybeKind.Witness, Integer> firstAttempt =
                    monadError.raiseError(Unit.INSTANCE);
                // Second recovery - succeeds
                return monadError.handleErrorWith(firstAttempt, u -> monadError.of(999));
              });

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(999);
    }

    @Test
    @DisplayName("Exception propagation in handleErrorWith handler")
    void exceptionPropagationInHandler() {
      Kind<MaybeKind.Witness, Integer> nothingValue = nothingKind();
      RuntimeException testException = new RuntimeException("Handler exception");

      Function<Unit, Kind<MaybeKind.Witness, Integer>> throwingHandler =
          unit -> {
            throw testException;
          };

      assertThatThrownBy(() -> monadError.handleErrorWith(nothingValue, throwingHandler))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Mixing map, flatMap, and error recovery")
    void mixingMapFlatMapAndErrorRecovery() {
      Kind<MaybeKind.Witness, Integer> start = justKind(10);

      Kind<MaybeKind.Witness, String> result =
          monadError.flatMap(
              i -> {
                if (i < 5) {
                  return monadError.raiseError(Unit.INSTANCE);
                }
                return monadError.map(x -> "Value:" + x, monadError.of(i * 2));
              },
              start);

      result = monadError.handleErrorWith(result, unit -> monadError.of("Recovered"));

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("Value:20");
    }

    @Test
    @DisplayName("Deep error recovery chain")
    void deepErrorRecoveryChain() {
      Kind<MaybeKind.Witness, Integer> start = nothingKind();

      Kind<MaybeKind.Witness, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int index = i;
        result =
            monadError.handleErrorWith(
                result,
                unit -> {
                  if (index < 9) {
                    return monadError.raiseError(Unit.INSTANCE); // Keep failing
                  }
                  return monadError.of(100); // Finally succeed
                });
      }

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(100);
    }

    @Test
    @DisplayName("recoverWith with Nothing fallback")
    void recoverWithNothingFallback() {
      Kind<MaybeKind.Witness, Integer> nothingValue = nothingKind();
      Kind<MaybeKind.Witness, Integer> nothingFallback = nothingKind();

      Kind<MaybeKind.Witness, Integer> result =
          monadError.recoverWith(nothingValue, nothingFallback);

      assertThat(result).isSameAs(nothingFallback);
      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Multiple error recovery strategies")
    void multipleErrorRecoveryStrategies() {
      Kind<MaybeKind.Witness, Integer> nothingValue = nothingKind();

      // Strategy 1: handleErrorWith
      Kind<MaybeKind.Witness, Integer> strategy1 =
          monadError.handleErrorWith(nothingValue, unit -> monadError.of(1));

      // Strategy 2: recoverWith
      Kind<MaybeKind.Witness, Integer> strategy2 =
          monadError.recoverWith(nothingValue, monadError.of(2));

      // Strategy 3: recover
      Kind<MaybeKind.Witness, Integer> strategy3 = monadError.recover(nothingValue, 3);

      assertThat(narrowToMaybe(strategy1).get()).isEqualTo(1);
      assertThat(narrowToMaybe(strategy2).get()).isEqualTo(2);
      assertThat(narrowToMaybe(strategy3).get()).isEqualTo(3);
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

      // Success case
      Maybe<String> success = validatePositive.apply(50).flatMap(validateRange).flatMap(format);

      assertThat(success.isJust()).isTrue();
      assertThat(success.get()).isEqualTo("Valid: 50");

      // Failure case
      Maybe<String> failure = validatePositive.apply(-5).flatMap(validateRange).flatMap(format);

      assertThat(failure.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Real-world scenario: error recovery with fallback")
    void errorRecoveryWithFallback() {
      Function<String, Maybe<String>> primarySource = id -> Maybe.nothing();

      Function<String, Maybe<String>> fallbackSource = id -> Maybe.just("fallback-data");

      String id = "user-123";
      Maybe<String> result =
          primarySource
              .apply(id)
              .map(Maybe::just) // If primary succeeds
              .orElseGet(() -> fallbackSource.apply(id)); // If primary fails, try fallback

      assertThat(result.get()).isEqualTo("fallback-data");
    }

    @Test
    @DisplayName("Real-world scenario: optional chaining")
    void optionalChaining() {
      record User(String name, Maybe<String> email) {}
      record Email(String address) {}

      User userWithEmail = new User("Alice", Maybe.just("alice@example.com"));
      User userWithoutEmail = new User("Bob", Maybe.nothing());

      Function<User, Maybe<Email>> getEmail = user -> user.email().map(Email::new);

      Maybe<Email> aliceEmail = getEmail.apply(userWithEmail);
      Maybe<Email> bobEmail = getEmail.apply(userWithoutEmail);

      assertThat(aliceEmail.isJust()).isTrue();
      assertThat(aliceEmail.get().address()).isEqualTo("alice@example.com");
      assertThat(bobEmail.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Real-world scenario: safe division")
    void safeDivision() {
      Function<Integer, Function<Integer, Maybe<Integer>>> safeDivide =
          divisor ->
              dividend -> {
                if (divisor == 0) {
                  return Maybe.nothing();
                }
                return Maybe.just(dividend / divisor);
              };

      // Success case
      Maybe<Integer> result1 = safeDivide.apply(2).apply(10);
      assertThat(result1.isJust()).isTrue();
      assertThat(result1.get()).isEqualTo(5);

      // Failure case - division by zero
      Maybe<Integer> result2 = safeDivide.apply(0).apply(10);
      assertThat(result2.isNothing()).isTrue();

      // Recovery
      Integer finalResult = result2.orElseGet(() -> safeDivide.apply(1).apply(10).orElse(-1));
      assertThat(finalResult).isEqualTo(10);
    }
  }

  @Nested
  @DisplayName("MonadZero Tests")
  class MonadZeroTests {

    @Test
    @DisplayName("zero() is consistent with raiseError()")
    void zeroIsConsistentWithRaiseError() {
      Kind<MaybeKind.Witness, Integer> zero = monadError.zero();
      Kind<MaybeKind.Witness, Integer> raised = monadError.raiseError(Unit.INSTANCE);

      Maybe<Integer> zeroMaybe = narrowToMaybe(zero);
      Maybe<Integer> raisedMaybe = narrowToMaybe(raised);

      assertThat(zeroMaybe.isNothing()).isTrue();
      assertThat(raisedMaybe.isNothing()).isTrue();
      assertThat(zeroMaybe).isEqualTo(raisedMaybe);
    }

    @Test
    @DisplayName("zero() can be recovered")
    void zeroCanBeRecovered() {
      Kind<MaybeKind.Witness, Integer> zero = monadError.zero();

      Kind<MaybeKind.Witness, Integer> recovered =
          monadError.handleErrorWith(zero, unit -> monadError.of(0));

      Maybe<Integer> maybe = narrowToMaybe(recovered);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(0);
    }
  }
}
