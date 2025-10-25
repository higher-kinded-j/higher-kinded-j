// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeAssert.assertThatMaybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeMonad Complete Test Suite")
class MaybeMonadTest extends MaybeTestBase {

  private MaybeMonad monad;

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

      assertThatMaybe(narrowToMaybe(result)).isJust().hasValue("flat:" + DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("flatMap() on Just can return Nothing")
    void flatMapOnJustCanReturnNothing() {
      Function<Integer, Kind<MaybeKind.Witness, String>> nothingMapper = i -> nothingKind();

      Kind<MaybeKind.Witness, String> result = monad.flatMap(nothingMapper, validKind);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("flatMap() on Nothing returns Nothing")
    void flatMapOnNothingReturnsNothing() {
      Kind<MaybeKind.Witness, Integer> nothing = nothingKind();

      Kind<MaybeKind.Witness, String> result = monad.flatMap(validFlatMapper, nothing);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("of() creates Just instances for non-null")
    void ofCreatesJustInstances() {
      Kind<MaybeKind.Witness, String> result = monad.of("success");

      assertThatMaybe(narrowToMaybe(result)).isJust().hasValue("success");
    }

    @Test
    @DisplayName("of() creates Nothing for null")
    void ofCreatesNothingForNull() {
      Kind<MaybeKind.Witness, String> result = monad.of(null);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("ap() applies function in Just to value in Just")
    void apAppliesFunctionToValue() {
      Kind<MaybeKind.Witness, String> result = monad.ap(validFunctionKind, validKind);

      assertThatMaybe(narrowToMaybe(result)).isJust().hasValue(String.valueOf(DEFAULT_JUST_VALUE));
    }

    @Test
    @DisplayName("ap() returns Nothing when function is Nothing")
    void apReturnsNothingWhenFunctionIsNothing() {
      Kind<MaybeKind.Witness, Function<Integer, String>> nothingFunc = nothingKind();

      Kind<MaybeKind.Witness, String> result = monad.ap(nothingFunc, validKind);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("ap() returns Nothing when argument is Nothing")
    void apReturnsNothingWhenArgumentIsNothing() {
      Kind<MaybeKind.Witness, Integer> nothing = nothingKind();

      Kind<MaybeKind.Witness, String> result = monad.ap(validFunctionKind, nothing);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("map2() combines two Just values")
    void map2CombinesTwoJustValues() {
      Kind<MaybeKind.Witness, String> result =
          monad.map2(validKind, validKind2, validCombiningFunction);

      assertThatMaybe(narrowToMaybe(result))
          .isJust()
          .hasValue("Result:" + DEFAULT_JUST_VALUE + "," + ALTERNATIVE_JUST_VALUE);
    }

    @Test
    @DisplayName("map2() returns Nothing if first argument is Nothing")
    void map2ReturnsNothingIfFirstIsNothing() {
      Kind<MaybeKind.Witness, Integer> nothing = nothingKind();

      Kind<MaybeKind.Witness, String> result =
          monad.map2(nothing, validKind2, validCombiningFunction);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("map2() returns Nothing if second argument is Nothing")
    void map2ReturnsNothingIfSecondIsNothing() {
      Kind<MaybeKind.Witness, Integer> nothing = nothingKind();

      Kind<MaybeKind.Witness, String> result =
          monad.map2(validKind, nothing, validCombiningFunction);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
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
      Kind<MaybeKind.Witness, Integer> start = justKind(1);

      Kind<MaybeKind.Witness, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      assertThatMaybe(narrowToMaybe(result)).isJust().hasValue(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("flatMap with early Nothing short-circuits")
    void flatMapWithEarlyNothingShortCircuits() {
      Kind<MaybeKind.Witness, Integer> start = justKind(1);

      Kind<MaybeKind.Witness, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int index = i;
        result =
            monad.flatMap(
                x -> {
                  if (index == 5) {
                    return nothingKind();
                  }
                  return monad.of(x + index);
                },
                result);
      }

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("Chaining map and flatMap operations")
    void chainingMapAndFlatMap() {
      Kind<MaybeKind.Witness, Integer> start = justKind(10);

      Kind<MaybeKind.Witness, String> result =
          monad.flatMap(
              i ->
                  monad.map(
                      str -> str.toUpperCase(), monad.map(x -> "value:" + x, monad.of(i * 2))),
              start);

      assertThatMaybe(narrowToMaybe(result)).isJust().hasValue("VALUE:20");
    }

    @Test
    @DisplayName("Nested Maybe handling")
    void nestedMaybeHandling() {
      // Maybe<Maybe<Integer>> flattened to Maybe<Integer>
      Kind<MaybeKind.Witness, Kind<MaybeKind.Witness, Integer>> nested =
          justKind(justKind(DEFAULT_JUST_VALUE));

      Kind<MaybeKind.Witness, Integer> flattened = monad.flatMap(inner -> inner, nested);

      assertThatMaybe(narrowToMaybe(flattened)).isJust().hasValue(DEFAULT_JUST_VALUE);
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
        Kind<MaybeKind.Witness, Integer> start = justKind(1);

        Kind<MaybeKind.Witness, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int increment = i;
          result = monad.flatMap(x -> monad.of(x + increment), result);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        assertThatMaybe(narrowToMaybe(result)).isJust().hasValue(expectedSum);
      }
    }

    @Test
    @DisplayName("Nothing values don't process operations")
    void nothingValuesDontProcessOperations() {
      Kind<MaybeKind.Witness, String> nothingStart = nothingKind();
      Maybe<String> originalNothing = narrowToMaybe(nothingStart);

      Kind<MaybeKind.Witness, String> nothingResult = nothingStart;
      for (int i = 0; i < 1000; i++) {
        final int index = i;
        nothingResult = monad.flatMap(s -> monad.of(s + "_" + index), nothingResult);
      }

      Maybe<String> finalNothing = narrowToMaybe(nothingResult);
      assertThat(finalNothing).isSameAs(originalNothing);
    }
  }
}
