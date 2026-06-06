// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.function.Function5;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("OptionalMonad")
class OptionalMonadTest extends OptionalTestBase {

  private MonadError<OptionalKind.Witness, Unit> optionalMonad;

  @BeforeEach
  void setUpMonad() {
    optionalMonad = Instances.monadError(optional());
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(optionalMonad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#kinds")
    void rightIdentity(String label, Kind<OptionalKind.Witness, Integer> ma) {
      MonadLaws.assertRightIdentity(optionalMonad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#kinds")
    void associativity(String label, Kind<OptionalKind.Witness, Integer> ma) {
      MonadLaws.assertAssociativity(
          optionalMonad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  @Test
  @DisplayName("Monad contract — operations, validations & exceptions (laws verified above)")
  void monadContract() {
    TypeClassContract.<OptionalKind.Witness>monad(OptionalMonad.class)
        .<Integer>instance(optionalMonad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("of() wraps value in present Optional")
    void ofWrapsValueInPresentOptional() {
      var result = optionalMonad.of(DEFAULT_PRESENT_VALUE);
      assertThatOptionalKind(result).isPresent().contains(DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("of() creates empty Optional for null value")
    void ofCreatesEmptyOptionalForNull() {
      Kind<OptionalKind.Witness, String> result = optionalMonad.of(null);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("map() applies function when present")
    void mapAppliesFunctionWhenPresent() {
      var result = optionalMonad.map(Object::toString, presentOf(5));
      assertThatOptionalKind(result).isPresent().contains("5");
    }

    @Test
    @DisplayName("map() returns empty when empty")
    void mapReturnsEmptyWhenEmpty() {
      Kind<OptionalKind.Witness, Integer> input = emptyOptional();
      var result = optionalMonad.map(Object::toString, input);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("map() handles mapping to null as empty")
    void mapHandlesMappingToNullAsEmpty() {
      var result = optionalMonad.map(_ -> null, presentOf(5));
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("ap() applies present function to present value")
    void apAppliesPresentFunctionToPresentValue() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind =
          optionalMonad.of(x -> "N" + x);
      var result = optionalMonad.ap(funcKind, optionalMonad.of(10));
      assertThatOptionalKind(result).isPresent().contains("N10");
    }

    @Test
    @DisplayName("ap() returns empty if function is empty")
    void apReturnsEmptyIfFunctionIsEmpty() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind = emptyOptional();
      var result = optionalMonad.ap(funcKind, optionalMonad.of(10));
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("ap() returns empty if value is empty")
    void apReturnsEmptyIfValueIsEmpty() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind =
          optionalMonad.of(x -> "N" + x);
      Kind<OptionalKind.Witness, Integer> valueKind = emptyOptional();
      var result = optionalMonad.ap(funcKind, valueKind);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap() applies function when present")
    void flatMapAppliesFunctionWhenPresent() {
      Function<Integer, Kind<OptionalKind.Witness, Double>> safeDivide =
          divisor -> (divisor == 0) ? emptyOptional() : presentOf(100.0 / divisor);
      var result = optionalMonad.flatMap(safeDivide, optionalMonad.of(5));
      assertThatOptionalKind(result).isPresent().contains(20.0);
    }

    @Test
    @DisplayName("flatMap() returns empty when input is empty")
    void flatMapReturnsEmptyWhenInputIsEmpty() {
      Function<Integer, Kind<OptionalKind.Witness, Double>> safeDivide =
          divisor -> (divisor == 0) ? emptyOptional() : presentOf(100.0 / divisor);
      Kind<OptionalKind.Witness, Integer> emptyValue = emptyOptional();
      var result = optionalMonad.flatMap(safeDivide, emptyValue);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap() returns empty when function result is empty")
    void flatMapReturnsEmptyWhenFunctionResultIsEmpty() {
      Function<Integer, Kind<OptionalKind.Witness, Double>> safeDivide =
          divisor -> (divisor == 0) ? emptyOptional() : presentOf(100.0 / divisor);
      var result = optionalMonad.flatMap(safeDivide, optionalMonad.of(0));
      assertThatOptionalKind(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("flatMapN Tests")
  class FlatMapNTests {

    @Test
    @DisplayName("flatMap2() combines two present Optionals")
    void flatMap2CombinesTwoPresentOptionals() {
      var result =
          optionalMonad.flatMap2(presentOf(10), presentOf("x"), (i, s) -> presentOf(i + s));
      assertThatOptionalKind(result).isPresent().contains("10x");
    }

    @Test
    @DisplayName("flatMap2() returns empty if first Optional is empty")
    void flatMap2ReturnsEmptyIfFirstOptionalIsEmpty() {
      Kind<OptionalKind.Witness, Integer> opt1 = emptyOptional();
      var result = optionalMonad.flatMap2(opt1, presentOf("x"), (i, s) -> presentOf(i + s));
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap2() returns empty if second Optional is empty")
    void flatMap2ReturnsEmptyIfSecondOptionalIsEmpty() {
      Kind<OptionalKind.Witness, String> opt2 = emptyOptional();
      var result = optionalMonad.flatMap2(presentOf(10), opt2, (i, s) -> presentOf(i + s));
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap2() returns empty if function returns empty")
    void flatMap2ReturnsEmptyIfFunctionReturnsEmpty() {
      var result = optionalMonad.flatMap2(presentOf(10), presentOf("x"), (_, _) -> emptyOptional());
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap3() combines three present Optionals")
    void flatMap3CombinesThreePresentOptionals() {
      Function3<Integer, String, Double, Kind<OptionalKind.Witness, String>> f =
          (i, s, d) -> presentOf(String.format("%d-%s-%.1f", i, s, d));
      var result = optionalMonad.flatMap3(presentOf(1), presentOf("a"), presentOf(2.5), f);
      assertThatOptionalKind(result).isPresent().contains("1-a-2.5");
    }

    @Test
    @DisplayName("flatMap3() returns empty if middle Optional is empty")
    void flatMap3ReturnsEmptyIfMiddleOptionalIsEmpty() {
      Kind<OptionalKind.Witness, String> opt2 = emptyOptional();
      Function3<Integer, String, Double, Kind<OptionalKind.Witness, String>> f =
          (_, _, _) -> presentOf("Should not execute");
      var result = optionalMonad.flatMap3(presentOf(1), opt2, presentOf(2.5), f);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap4() combines four present Optionals")
    void flatMap4CombinesFourPresentOptionals() {
      Function4<Integer, String, Double, Boolean, Kind<OptionalKind.Witness, String>> f =
          (i, s, d, b) -> presentOf(String.format("%d-%s-%.0f-%b", i, s, d, b));
      var result =
          optionalMonad.flatMap4(presentOf(1), presentOf("a"), presentOf(2.0), presentOf(true), f);
      assertThatOptionalKind(result).isPresent().contains("1-a-2-true");
    }

    @Test
    @DisplayName("flatMap4() returns empty if any Optional is empty")
    void flatMap4ReturnsEmptyIfAnyOptionalIsEmpty() {
      Kind<OptionalKind.Witness, Double> opt3 = emptyOptional();
      Function4<Integer, String, Double, Boolean, Kind<OptionalKind.Witness, String>> f =
          (_, _, _, _) -> presentOf("Should not execute");
      var result = optionalMonad.flatMap4(presentOf(1), presentOf("a"), opt3, presentOf(true), f);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap5() combines five present Optionals")
    void flatMap5CombinesFivePresentOptionals() {
      Function5<Integer, String, Double, Boolean, Character, Kind<OptionalKind.Witness, String>> f =
          (i, s, d, b, c) -> presentOf(String.format("%d-%s-%.0f-%b-%c", i, s, d, b, c));
      var result =
          optionalMonad.flatMap5(
              presentOf(1), presentOf("a"), presentOf(2.0), presentOf(true), presentOf('X'), f);
      assertThatOptionalKind(result).isPresent().contains("1-a-2-true-X");
    }

    @Test
    @DisplayName("flatMap5() returns empty if any Optional is empty")
    void flatMap5ReturnsEmptyIfAnyOptionalIsEmpty() {
      Kind<OptionalKind.Witness, Character> opt5 = emptyOptional();
      Function5<Integer, String, Double, Boolean, Character, Kind<OptionalKind.Witness, String>> f =
          (_, _, _, _, _) -> presentOf("Should not execute");
      var result =
          optionalMonad.flatMap5(
              presentOf(1), presentOf("a"), presentOf(2.0), presentOf(true), opt5, f);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap5() returns empty if function returns empty")
    void flatMap5ReturnsEmptyIfFunctionReturnsEmpty() {
      Function5<Integer, String, Double, Boolean, Character, Kind<OptionalKind.Witness, String>> f =
          (_, _, _, _, _) -> emptyOptional();
      var result =
          optionalMonad.flatMap5(
              presentOf(1), presentOf("a"), presentOf(2.0), presentOf(true), presentOf('X'), f);
      assertThatOptionalKind(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Chained operations maintain correctness")
    void chainedOperationsMaintainCorrectness() {
      Function<Integer, Kind<OptionalKind.Witness, Integer>> step1 = x -> presentOf(x * 2);
      Function<Integer, Kind<OptionalKind.Witness, String>> step2 = y -> presentOf("N" + y);
      var result = optionalMonad.flatMap(step2, optionalMonad.flatMap(step1, presentOf(5)));
      assertThatOptionalKind(result).isPresent().contains("N10");
    }

    @Test
    @DisplayName("Empty propagates through a chain")
    void emptyPropagatesThroughChain() {
      Kind<OptionalKind.Witness, Integer> initial = emptyOptional();
      Function<Integer, Kind<OptionalKind.Witness, Integer>> step1 = x -> presentOf(x * 2);
      Function<Integer, Kind<OptionalKind.Witness, String>> step2 = y -> presentOf("N" + y);
      var result = optionalMonad.flatMap(step2, optionalMonad.flatMap(step1, initial));
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("Empty in the middle of a chain propagates")
    void emptyInMiddleOfChainPropagates() {
      Function<Integer, Kind<OptionalKind.Witness, Integer>> step1 = _ -> emptyOptional();
      Function<Integer, Kind<OptionalKind.Witness, String>> step2 = y -> presentOf("N" + y);
      var result = optionalMonad.flatMap(step2, optionalMonad.flatMap(step1, presentOf(5)));
      assertThatOptionalKind(result).isEmpty();
    }
  }
}
