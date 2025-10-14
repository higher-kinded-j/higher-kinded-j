// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeMonad Complete Test Suite")
class MaybeMonadTest extends TypeClassTestBase<MaybeKind.Witness, Integer, String> {

  private MaybeMonad monad;

  @Override
  protected Kind<MaybeKind.Witness, Integer> createValidKind() {
    return MAYBE.widen(Maybe.just(42));
  }

  @Override
  protected Kind<MaybeKind.Witness, Integer> createValidKind2() {
    return MAYBE.widen(Maybe.just(24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<MaybeKind.Witness, String>> createValidFlatMapper() {
    return i -> MAYBE.widen(Maybe.just("flat:" + i));
  }

  @Override
  protected Kind<MaybeKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return MAYBE.widen(Maybe.just(TestFunctions.INT_TO_STRING));
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
  protected Function<Integer, Kind<MaybeKind.Witness, String>> createTestFunction() {
    return i -> MAYBE.widen(Maybe.just("test:" + i));
  }

  @Override
  protected Function<String, Kind<MaybeKind.Witness, String>> createChainFunction() {
    return s -> MAYBE.widen(Maybe.just(s + "!"));
  }

  @Override
  protected BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>>
      createEqualityChecker() {
    return (k1, k2) -> MAYBE.narrow(k1).equals(MAYBE.narrow(k2));
  }

  @BeforeEach
  void setUpMonad() {
    monad = MaybeMonad.INSTANCE;
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(MaybeMonadTest.class);

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
    @DisplayName("flatMap() on Just applies function")
    void flatMapOnJustAppliesFunction() {
      Kind<MaybeKind.Witness, String> result = monad.flatMap(validFlatMapper, validKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("flat:42");
    }

    @Test
    @DisplayName("flatMap() on Just can return Nothing")
    void flatMapOnJustCanReturnNothing() {
      Function<Integer, Kind<MaybeKind.Witness, String>> nothingMapper =
          i -> MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, String> result = monad.flatMap(nothingMapper, validKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("flatMap() on Nothing returns Nothing")
    void flatMapOnNothingReturnsNothing() {
      Kind<MaybeKind.Witness, Integer> nothingKind = MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, String> result = monad.flatMap(validFlatMapper, nothingKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("of() creates Just instances for non-null")
    void ofCreatesJustInstances() {
      Kind<MaybeKind.Witness, String> result = monad.of("success");

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("success");
    }

    @Test
    @DisplayName("of() creates Nothing for null")
    void ofCreatesNothingForNull() {
      Kind<MaybeKind.Witness, String> result = monad.of(null);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("ap() applies function in Just to value in Just")
    void apAppliesFunctionToValue() {
      Kind<MaybeKind.Witness, String> result = monad.ap(validFunctionKind, validKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("42");
    }

    @Test
    @DisplayName("ap() returns Nothing when function is Nothing")
    void apReturnsNothingWhenFunctionIsNothing() {
      Kind<MaybeKind.Witness, Function<Integer, String>> nothingFunc = MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, String> result = monad.ap(nothingFunc, validKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("ap() returns Nothing when argument is Nothing")
    void apReturnsNothingWhenArgumentIsNothing() {
      Kind<MaybeKind.Witness, Integer> nothingKind = MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, String> result = monad.ap(validFunctionKind, nothingKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map2() combines two Just values")
    void map2CombinesTwoJustValues() {
      Kind<MaybeKind.Witness, String> result =
          monad.map2(validKind, validKind2, validCombiningFunction);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("Result:42,24");
    }

    @Test
    @DisplayName("map2() returns Nothing if first argument is Nothing")
    void map2ReturnsNothingIfFirstIsNothing() {
      Kind<MaybeKind.Witness, Integer> nothingKind = MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, String> result =
          monad.map2(nothingKind, validKind2, validCombiningFunction);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map2() returns Nothing if second argument is Nothing")
    void map2ReturnsNothingIfSecondIsNothing() {
      Kind<MaybeKind.Witness, Integer> nothingKind = MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, String> result =
          monad.map2(validKind, nothingKind, validCombiningFunction);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      Kind<MaybeKind.Witness, Integer> start = MAYBE.widen(Maybe.just(1));

      Kind<MaybeKind.Witness, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("flatMap with early Nothing short-circuits")
    void flatMapWithEarlyNothingShortCircuits() {
      Kind<MaybeKind.Witness, Integer> start = MAYBE.widen(Maybe.just(1));

      Kind<MaybeKind.Witness, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int index = i;
        result =
            monad.flatMap(
                x -> {
                  if (index == 5) {
                    return MAYBE.widen(Maybe.nothing());
                  }
                  return monad.of(x + index);
                },
                result);
      }

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Chaining map and flatMap operations")
    void chainingMapAndFlatMap() {
      Kind<MaybeKind.Witness, Integer> start = MAYBE.widen(Maybe.just(10));

      Kind<MaybeKind.Witness, String> result =
          monad.flatMap(
              i ->
                  monad.map(
                      str -> str.toUpperCase(), monad.map(x -> "value:" + x, monad.of(i * 2))),
              start);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("VALUE:20");
    }

    @Test
    @DisplayName("Nested Maybe handling")
    void nestedMaybeHandling() {
      // Maybe<Maybe<Integer>> flattened to Maybe<Integer>
      Kind<MaybeKind.Witness, Kind<MaybeKind.Witness, Integer>> nested =
          MAYBE.widen(Maybe.just(MAYBE.widen(Maybe.just(42))));

      Kind<MaybeKind.Witness, Integer> flattened = monad.flatMap(inner -> inner, nested);

      Maybe<Integer> maybe = MAYBE.narrow(flattened);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("flatMap with null parameters validates correctly")
    void flatMapWithNullParametersValidatesCorrectly() {
      // Verify null validation for flatMap - covered by testValidationsOnly() but kept explicit
      assertThatThrownBy(() -> monad.flatMap(null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function f for MaybeMonad.flatMap cannot be null");

      assertThatThrownBy(() -> monad.flatMap(validFlatMapper, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind for MaybeMonad.flatMap cannot be null");
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("flatMap efficient with many operations")
    void flatMapEfficientWithManyOperations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<MaybeKind.Witness, Integer> start = MAYBE.widen(Maybe.just(1));

        Kind<MaybeKind.Witness, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int increment = i;
          result = monad.flatMap(x -> monad.of(x + increment), result);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        Maybe<Integer> maybe = MAYBE.narrow(result);
        assertThat(maybe.isJust()).isTrue();
        assertThat(maybe.get()).isEqualTo(expectedSum);
      }
    }

    @Test
    @DisplayName("Nothing values don't process operations")
    void nothingValuesDontProcessOperations() {
      Kind<MaybeKind.Witness, String> nothingStart = MAYBE.widen(Maybe.nothing());
      Maybe<String> originalNothing = MAYBE.narrow(nothingStart);

      Kind<MaybeKind.Witness, String> nothingResult = nothingStart;
      for (int i = 0; i < 1000; i++) {
        final int index = i;
        nothingResult = monad.flatMap(s -> monad.of(s + "_" + index), nothingResult);
      }

      Maybe<String> finalNothing = MAYBE.narrow(nothingResult);
      assertThat(finalNothing).isSameAs(originalNothing);
    }
  }
}
