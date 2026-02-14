// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalAssert.assertThatOptional;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.function.Function5;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalMonad Complete Test Suite")
class OptionalMonadTest extends OptionalTestBase {

  private MonadError<OptionalKind.Witness, Unit> optionalMonad;

  @BeforeEach
  void setUpMonad() {
    optionalMonad = OptionalMonad.INSTANCE;
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<OptionalKind.Witness>monad(OptionalMonad.class)
          .<Integer>instance(optionalMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(OptionalMonadTest.class);

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
    @DisplayName("of() wraps value in present Optional")
    void ofWrapsValueInPresentOptional() {
      var result = optionalMonad.of(DEFAULT_PRESENT_VALUE);

      assertThatOptional(result).isPresent().contains(DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("of() creates empty Optional for null value")
    void ofCreatesEmptyOptionalForNull() {
      Kind<OptionalKind.Witness, String> result = optionalMonad.of(null);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("map() applies function when present")
    void mapAppliesFunctionWhenPresent() {
      var input = presentOf(5);
      var result = optionalMonad.map(Object::toString, input);

      assertThatOptional(result).isPresent().contains("5");
    }

    @Test
    @DisplayName("map() returns empty when empty")
    void mapReturnsEmptyWhenEmpty() {
      Kind<OptionalKind.Witness, Integer> input = emptyOptional();
      var result = optionalMonad.map(Object::toString, input);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("map() handles mapping to null as empty")
    void mapHandlesMappingToNullAsEmpty() {
      var input = presentOf(5);
      var result = optionalMonad.map(x -> null, input);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("ap() applies present function to present value")
    void apAppliesPresentFunctionToPresentValue() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind =
          optionalMonad.of(x -> "N" + x);
      var valueKind = optionalMonad.of(10);

      var result = optionalMonad.ap(funcKind, valueKind);

      assertThatOptional(result).isPresent().contains("N10");
    }

    @Test
    @DisplayName("ap() returns empty if function is empty")
    void apReturnsEmptyIfFunctionIsEmpty() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind = optionalMonad.of(null);
      var valueKind = optionalMonad.of(10);

      var result = optionalMonad.ap(funcKind, valueKind);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("ap() returns empty if value is empty")
    void apReturnsEmptyIfValueIsEmpty() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind =
          optionalMonad.of(x -> "N" + x);
      Kind<OptionalKind.Witness, Integer> valueKind = optionalMonad.of(null);

      var result = optionalMonad.ap(funcKind, valueKind);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("ap() returns empty if both are empty")
    void apReturnsEmptyIfBothAreEmpty() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind = optionalMonad.of(null);
      Kind<OptionalKind.Witness, Integer> valueKind = optionalMonad.of(null);

      var result = optionalMonad.ap(funcKind, valueKind);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap() applies function when present")
    void flatMapAppliesFunctionWhenPresent() {
      Function<Integer, Kind<OptionalKind.Witness, Double>> safeDivide =
          divisor -> (divisor == 0) ? emptyOptional() : presentOf(100.0 / divisor);

      var presentValue = optionalMonad.of(5);
      var result = optionalMonad.flatMap(safeDivide, presentValue);

      assertThatOptional(result).isPresent().contains(20.0);
    }

    @Test
    @DisplayName("flatMap() returns empty when input is empty")
    void flatMapReturnsEmptyWhenInputIsEmpty() {
      Function<Integer, Kind<OptionalKind.Witness, Double>> safeDivide =
          divisor -> (divisor == 0) ? emptyOptional() : presentOf(100.0 / divisor);

      Kind<OptionalKind.Witness, Integer> emptyValue = optionalMonad.of(null);
      var result = optionalMonad.flatMap(safeDivide, emptyValue);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap() returns empty when function result is empty")
    void flatMapReturnsEmptyWhenFunctionResultIsEmpty() {
      Function<Integer, Kind<OptionalKind.Witness, Double>> safeDivide =
          divisor -> (divisor == 0) ? emptyOptional() : presentOf(100.0 / divisor);

      var zeroValue = optionalMonad.of(0);
      var result = optionalMonad.flatMap(safeDivide, zeroValue);

      assertThatOptional(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<OptionalKind.Witness>monad(OptionalMonad.class)
          .<Integer>instance(optionalMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<OptionalKind.Witness>monad(OptionalMonad.class)
          .<Integer>instance(optionalMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<OptionalKind.Witness>monad(OptionalMonad.class)
          .<Integer>instance(optionalMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<OptionalKind.Witness>monad(OptionalMonad.class)
          .<Integer>instance(optionalMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("MonadError Tests")
  class MonadErrorTests {

    Kind<OptionalKind.Witness, Integer> presentVal;
    Kind<OptionalKind.Witness, Integer> emptyVal;
    Kind<OptionalKind.Witness, Integer> raisedErrorKind;

    @BeforeEach
    void setUp() {
      presentVal = optionalMonad.of(100);
      emptyVal = optionalMonad.of(null);
      raisedErrorKind = optionalMonad.raiseError(null);
    }

    @Test
    @DisplayName("raiseError() creates empty Optional")
    void raiseErrorCreatesEmpty() {
      assertThatOptional(raisedErrorKind).isEmpty();
    }

    @Test
    @DisplayName("handleErrorWith() handles empty Optional")
    void handleErrorWithHandlesEmpty() {
      Function<Unit, Kind<OptionalKind.Witness, Integer>> handler = err -> optionalMonad.of(0);
      var result = optionalMonad.handleErrorWith(emptyVal, handler);

      assertThatOptional(result).isPresent().contains(0);
    }

    @Test
    @DisplayName("handleErrorWith() ignores present Optional")
    void handleErrorWithIgnoresPresent() {
      Function<Unit, Kind<OptionalKind.Witness, Integer>> handler = err -> optionalMonad.of(-1);
      var result = optionalMonad.handleErrorWith(presentVal, handler);

      assertThat(result).isSameAs(presentVal);
      assertThatOptional(result).isPresent().contains(100);
    }
  }

  @Nested
  @DisplayName("flatMapN Tests")
  class FlatMapNTests {

    @Test
    @DisplayName("flatMap2() combines two present Optionals")
    void flatMap2CombinesTwoPresentOptionals() {
      var opt1 = presentOf(10);
      var opt2 = presentOf("x");

      var result = optionalMonad.flatMap2(opt1, opt2, (i, s) -> presentOf(i + s));

      assertThatOptional(result).isPresent().contains("10x");
    }

    @Test
    @DisplayName("flatMap2() returns empty if first Optional is empty")
    void flatMap2ReturnsEmptyIfFirstOptionalIsEmpty() {
      Kind<OptionalKind.Witness, Integer> opt1 = emptyOptional();
      var opt2 = presentOf("x");

      var result = optionalMonad.flatMap2(opt1, opt2, (i, s) -> presentOf(i + s));

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap2() returns empty if second Optional is empty")
    void flatMap2ReturnsEmptyIfSecondOptionalIsEmpty() {
      var opt1 = presentOf(10);
      Kind<OptionalKind.Witness, String> opt2 = emptyOptional();

      var result = optionalMonad.flatMap2(opt1, opt2, (i, s) -> presentOf(i + s));

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap2() returns empty if function returns empty")
    void flatMap2ReturnsEmptyIfFunctionReturnsEmpty() {
      var opt1 = presentOf(10);
      var opt2 = presentOf("x");

      var result = optionalMonad.flatMap2(opt1, opt2, (i, s) -> emptyOptional());

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap3() combines three present Optionals")
    void flatMap3CombinesThreePresentOptionals() {
      var opt1 = presentOf(1);
      var opt2 = presentOf("a");
      var opt3 = presentOf(2.5);
      Function3<Integer, String, Double, Kind<OptionalKind.Witness, String>> f =
          (i, s, d) -> presentOf(String.format("%d-%s-%.1f", i, s, d));

      var result = optionalMonad.flatMap3(opt1, opt2, opt3, f);

      assertThatOptional(result).isPresent().contains("1-a-2.5");
    }

    @Test
    @DisplayName("flatMap3() returns empty if middle Optional is empty")
    void flatMap3ReturnsEmptyIfMiddleOptionalIsEmpty() {
      var opt1 = presentOf(1);
      Kind<OptionalKind.Witness, String> opt2 = emptyOptional();
      var opt3 = presentOf(2.5);
      Function3<Integer, String, Double, Kind<OptionalKind.Witness, String>> f =
          (i, s, d) -> presentOf("Should not execute");

      var result = optionalMonad.flatMap3(opt1, opt2, opt3, f);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap4() combines four present Optionals")
    void flatMap4CombinesFourPresentOptionals() {
      var opt1 = presentOf(1);
      var opt2 = presentOf("a");
      var opt3 = presentOf(2.0);
      var opt4 = presentOf(true);
      Function4<Integer, String, Double, Boolean, Kind<OptionalKind.Witness, String>> f =
          (i, s, d, b) -> presentOf(String.format("%d-%s-%.0f-%b", i, s, d, b));

      var result = optionalMonad.flatMap4(opt1, opt2, opt3, opt4, f);

      assertThatOptional(result).isPresent().contains("1-a-2-true");
    }

    @Test
    @DisplayName("flatMap4() returns empty if any Optional is empty")
    void flatMap4ReturnsEmptyIfAnyOptionalIsEmpty() {
      var opt1 = presentOf(1);
      var opt2 = presentOf("a");
      Kind<OptionalKind.Witness, Double> opt3 = emptyOptional();
      var opt4 = presentOf(true);
      Function4<Integer, String, Double, Boolean, Kind<OptionalKind.Witness, String>> f =
          (i, s, d, b) -> presentOf("Should not execute");

      var result = optionalMonad.flatMap4(opt1, opt2, opt3, opt4, f);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap5() combines five present Optionals")
    void flatMap5CombinesFivePresentOptionals() {
      var opt1 = presentOf(1);
      var opt2 = presentOf("a");
      var opt3 = presentOf(2.0);
      var opt4 = presentOf(true);
      var opt5 = presentOf('X');
      Function5<Integer, String, Double, Boolean, Character, Kind<OptionalKind.Witness, String>> f =
          (i, s, d, b, c) -> presentOf(String.format("%d-%s-%.0f-%b-%c", i, s, d, b, c));

      var result = optionalMonad.flatMap5(opt1, opt2, opt3, opt4, opt5, f);

      assertThatOptional(result).isPresent().contains("1-a-2-true-X");
    }

    @Test
    @DisplayName("flatMap5() returns empty if any Optional is empty")
    void flatMap5ReturnsEmptyIfAnyOptionalIsEmpty() {
      var opt1 = presentOf(1);
      var opt2 = presentOf("a");
      var opt3 = presentOf(2.0);
      var opt4 = presentOf(true);
      Kind<OptionalKind.Witness, Character> opt5 = emptyOptional();
      Function5<Integer, String, Double, Boolean, Character, Kind<OptionalKind.Witness, String>> f =
          (i, s, d, b, c) -> presentOf("Should not execute");

      var result = optionalMonad.flatMap5(opt1, opt2, opt3, opt4, opt5, f);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap5() returns empty if function returns empty")
    void flatMap5ReturnsEmptyIfFunctionReturnsEmpty() {
      var opt1 = presentOf(1);
      var opt2 = presentOf("a");
      var opt3 = presentOf(2.0);
      var opt4 = presentOf(true);
      var opt5 = presentOf('X');
      Function5<Integer, String, Double, Boolean, Character, Kind<OptionalKind.Witness, String>> f =
          (i, s, d, b, c) -> emptyOptional();

      var result = optionalMonad.flatMap5(opt1, opt2, opt3, opt4, opt5, f);

      assertThatOptional(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("MonadZero Tests")
  class MonadZeroTests {

    @Test
    @DisplayName("zero() returns empty Optional")
    void zeroReturnsEmptyOptional() {
      Kind<OptionalKind.Witness, Object> zeroKind = OptionalMonad.INSTANCE.zero();

      assertThatOptional(zeroKind).isEmpty();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Chained operations maintain correctness")
    void chainedOperationsMaintainCorrectness() {
      var initial = presentOf(5);

      Function<Integer, Kind<OptionalKind.Witness, Integer>> step1 = x -> presentOf(x * 2);
      var step1Result = optionalMonad.flatMap(step1, initial);

      Function<Integer, Kind<OptionalKind.Witness, String>> step2 = y -> presentOf("N" + y);
      var finalResult = optionalMonad.flatMap(step2, step1Result);

      assertThatOptional(finalResult).isPresent().contains("N10");
    }

    @Test
    @DisplayName("Empty propagates through chain")
    void emptyPropagatesThroughChain() {
      Kind<OptionalKind.Witness, Integer> initial = emptyOptional();

      Function<Integer, Kind<OptionalKind.Witness, Integer>> step1 = x -> presentOf(x * 2);
      var step1Result = optionalMonad.flatMap(step1, initial);

      Function<Integer, Kind<OptionalKind.Witness, String>> step2 = y -> presentOf("N" + y);
      var finalResult = optionalMonad.flatMap(step2, step1Result);

      assertThatOptional(finalResult).isEmpty();
    }

    @Test
    @DisplayName("Empty in middle of chain propagates")
    void emptyInMiddleOfChainPropagates() {
      var initial = presentOf(5);

      Function<Integer, Kind<OptionalKind.Witness, Integer>> step1 = x -> emptyOptional();
      var step1Result = optionalMonad.flatMap(step1, initial);

      Function<Integer, Kind<OptionalKind.Witness, String>> step2 = y -> presentOf("N" + y);
      var finalResult = optionalMonad.flatMap(step2, step1Result);

      assertThatOptional(finalResult).isEmpty();
    }
  }
}
