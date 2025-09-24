// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.patterns.FlexibleValidationConfig;
import org.higherkindedj.hkt.test.patterns.TypeClassTestPattern;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad Complete Test Suite")
class EitherMonadTest extends TypeClassTestBase<EitherKind.Witness<String>, Integer, String> {

  record TestError(String code) {}

  private EitherMonad<String> monad;

  @Override
  protected Kind<EitherKind.Witness<String>, Integer> createValidKind() {
    return EITHER.widen(Either.right(42));
  }

  @Override
  protected Kind<EitherKind.Witness<String>, Integer> createValidKind2() {
    return EITHER.widen(Either.right(24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<EitherKind.Witness<String>, String>> createValidFlatMapper() {
    return i -> EITHER.widen(Either.right("flat:" + i));
  }

  @Override
  protected Kind<EitherKind.Witness<String>, Function<Integer, String>> createValidFunctionKind() {
    return EITHER.widen(Either.right(TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return 42;
  }

  @Override
  protected Function<Integer, Kind<EitherKind.Witness<String>, String>> createTestFunction() {
    return i -> EITHER.widen(Either.right("test:" + i));
  }

  @Override
  protected Function<String, Kind<EitherKind.Witness<String>, String>> createChainFunction() {
    return s -> EITHER.widen(Either.right(s + "!"));
  }

  @Override
  protected BiPredicate<Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> EITHER.narrow(k1).equals(EITHER.narrow(k2));
  }

  @BeforeEach
  void setUpMonad() {
    monad = EitherMonad.instance();
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      // Create validation config with inheritance-aware validation
      FlexibleValidationConfig.MonadValidation<EitherKind.Witness<String>, Integer, String>
          validationConfig =
              new FlexibleValidationConfig.MonadValidation<>(
                      monad,
                      validKind,
                      validKind2,
                      validMapper,
                      validFunctionKind,
                      validCombiningFunction,
                      validFlatMapper)
                  .mapWithClassContext(EitherFunctor.class) // map is in EitherFunctor
                  .apWithClassContext(EitherMonad.class) // ap is in EitherMonad
                  .map2WithoutClassContext() // map2 has no class context
                  .flatMapWithClassContext(EitherMonad.class); // flatMap is in EitherMonad

      // Test operations
      TypeClassTestPattern.testMonadOperations(
          monad, validKind, validMapper, validFlatMapper, validFunctionKind);

      // Test validations using the configured validation expectations
      validationConfig.test();

      // Test exception propagation
      TypeClassTestPattern.testMonadExceptionPropagation(monad, validKind);

      // Test laws
      TypeClassTestPattern.testMonadLaws(
          monad, validKind, testValue, testFunction, chainFunction, equalityChecker);
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(EitherMonadTest.class);

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
    @DisplayName("flatMap() on Right applies function")
    void flatMapOnRightAppliesFunction() {
      Kind<EitherKind.Witness<String>, String> result = monad.flatMap(validFlatMapper, validKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("flat:42");
    }

    @Test
    @DisplayName("flatMap() on Right can return Left")
    void flatMapOnRightCanReturnLeft() {
      Function<Integer, Kind<EitherKind.Witness<String>, String>> errorMapper =
          i -> EITHER.widen(Either.left("ERROR"));

      Kind<EitherKind.Witness<String>, String> result = monad.flatMap(errorMapper, validKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("flatMap() on Left passes through unchanged")
    void flatMapOnLeftPassesThrough() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = EITHER.widen(Either.left("E1"));

      Kind<EitherKind.Witness<String>, String> result = monad.flatMap(validFlatMapper, leftKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("E1");
    }

    @Test
    @DisplayName("of() creates Right instances")
    void ofCreatesRightInstances() {
      Kind<EitherKind.Witness<String>, String> result = monad.of("success");

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("success");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTestPattern.testMonadOperations(
          monad, validKind, validMapper, validFlatMapper, validFunctionKind);
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      // Use FlexibleValidationConfig for EitherMonad
      new FlexibleValidationConfig.MonadValidation<>(
              monad,
              validKind,
              validKind2,
              validMapper,
              validFunctionKind,
              validCombiningFunction,
              validFlatMapper)
          .mapWithClassContext(EitherFunctor.class) // map is in EitherFunctor
          .apWithClassContext(EitherMonad.class) // ap is in EitherMonad
          .map2WithoutClassContext() // map2 has no class context
          .flatMapWithClassContext(EitherMonad.class) // flatMap is in EitherMonad
          .test();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTestPattern.testMonadExceptionPropagation(monad, validKind);
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTestPattern.testMonadLaws(
          monad, validKind, testValue, testFunction, chainFunction, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      Kind<EitherKind.Witness<String>, Integer> start = EITHER.widen(Either.right(1));

      Kind<EitherKind.Witness<String>, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      Either<String, Integer> either = EITHER.narrow(result);
      assertThat(either.getRight()).isEqualTo(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("flatMap with early Left short-circuits")
    void flatMapWithEarlyLeftShortCircuits() {
      Kind<EitherKind.Witness<String>, Integer> start = EITHER.widen(Either.right(1));

      Kind<EitherKind.Witness<String>, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int index = i;
        result =
            monad.flatMap(
                x -> {
                  if (index == 5) {
                    return EITHER.widen(Either.left("STOP"));
                  }
                  return monad.of(x + index);
                },
                result);
      }

      Either<String, Integer> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("STOP");
    }

    @Test
    @DisplayName("Test with different error types")
    void testWithDifferentErrorTypes() {
      record ComplexError(String code, int severity) {}

      EitherMonad<ComplexError> complexMonad = EitherMonad.instance();
      Kind<EitherKind.Witness<ComplexError>, Integer> complexKind = EITHER.widen(Either.right(100));

      Function<Integer, String> mapper = Object::toString;
      Function<Integer, Kind<EitherKind.Witness<ComplexError>, String>> flatMapper =
          i -> EITHER.widen(Either.right("flat:" + i));
      Kind<EitherKind.Witness<ComplexError>, Function<Integer, String>> functionKind =
          complexMonad.of(mapper);

      TypeClassTestPattern.testMonadOperations(
          complexMonad, complexKind, mapper, flatMapper, functionKind);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("flatMap efficient with many operations")
    void flatMapEfficientWithManyOperations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<EitherKind.Witness<String>, Integer> start = EITHER.widen(Either.right(1));

        Kind<EitherKind.Witness<String>, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int increment = i;
          result = monad.flatMap(x -> monad.of(x + increment), result);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        Either<String, Integer> either = EITHER.narrow(result);
        assertThat(either.getRight()).isEqualTo(expectedSum);
      }
    }

    @Test
    @DisplayName("Left values don't process operations")
    void leftValuesDontProcessOperations() {
      Kind<EitherKind.Witness<String>, String> leftStart = EITHER.widen(Either.left("ERROR"));
      Either<String, String> originalLeft = EITHER.narrow(leftStart);

      Kind<EitherKind.Witness<String>, String> leftResult = leftStart;
      for (int i = 0; i < 1000; i++) {
        final int index = i;
        leftResult = monad.flatMap(s -> monad.of(s + "_" + index), leftResult);
      }

      Either<String, String> finalLeft = EITHER.narrow(leftResult);
      assertThat(finalLeft).isSameAs(originalLeft);
    }
  }
}
