// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.IdAssert.assertThatId;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("IdMonad")
class IdMonadTest extends IdTestBase {

  private Monad<IdKind.Witness> monad;

  @BeforeEach
  void setUpMonad() {
    monad = Instances.monad(id());
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("fixtures")
    void rightIdentity(String label, Kind<IdKind.Witness, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("fixtures")
    void associativity(String label, Kind<IdKind.Witness, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of("Id(0)", ID.widen(Id.of(0))),
          Arguments.of("Id(42)", ID.widen(Id.of(42))),
          Arguments.of("Id(-1)", ID.widen(Id.of(-1))));
    }

    static Stream<Arguments> values() {
      return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("of() wraps value in Id")
    void ofWrapsValueInId() {
      var result = monad.of(DEFAULT_VALUE);

      assertThatId(result).hasValue(DEFAULT_VALUE);
    }

    @Test
    @DisplayName("of() handles null value")
    void ofHandlesNullValue() {
      Kind<IdKind.Witness, Integer> result = monad.of(null);

      assertThatId(result).hasValue(null);
    }

    @Test
    @DisplayName("map() applies function to value")
    void mapAppliesFunctionToValue() {
      var input = idOf(5);
      var result = monad.map(Object::toString, input);

      assertThatId(result).hasValue("5");
    }

    @Test
    @DisplayName("map() handles null value")
    void mapHandlesNullValue() {
      Kind<IdKind.Witness, Integer> input = idOf(null);
      var result = monad.map(i -> i == null ? "null" : i.toString(), input);

      assertThatId(result).hasValue("null");
    }

    @Test
    @DisplayName("ap() applies function to value")
    void apAppliesFunctionToValue() {
      Kind<IdKind.Witness, Function<Integer, String>> funcKind = monad.of(x -> "N" + x);
      var valueKind = monad.of(10);

      var result = monad.ap(funcKind, valueKind);

      assertThatId(result).hasValue("N10");
    }

    @Test
    @DisplayName("flatMap() applies function and unwraps")
    void flatMapAppliesFunctionAndUnwraps() {
      var input = idOf(5);
      Function<Integer, Kind<IdKind.Witness, String>> func = i -> idOf("value:" + i);

      var result = monad.flatMap(func, input);

      assertThatId(result).hasValue("value:5");
    }

    @Test
    @DisplayName("Test Functor operations (map)")
    void testFunctorOperations() {
      TypeClassContract.<IdKind.Witness>functor(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withEqualityChecker(equalityChecker)
          .verifyOnly(Category.OPERATIONS);
    }

    @Test
    @DisplayName("Test Applicative operations (of, ap, map2)")
    void testApplicativeOperations() {
      TypeClassContract.<IdKind.Witness>applicative(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.OPERATIONS);
    }

    @Test
    @DisplayName("Test Monad operations (flatMap)")
    void testMonadOperations() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.OPERATIONS);
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.OPERATIONS);
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.VALIDATIONS);
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.EXCEPTIONS);
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .verifyOnly(Category.LAWS);
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Test null parameter validations")
    void testAllNullParameterValidations() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.VALIDATIONS);
    }

    @Test
    @DisplayName("Test validation with Functor context for map")
    void testValidationWithFunctorContext() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.VALIDATIONS);
    }

    @Test
    @DisplayName("Test validation with Monad context for flatMap")
    void testValidationWithMonadContext() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.VALIDATIONS);
    }

    @Test
    @DisplayName("Test validation with full inheritance hierarchy")
    void testValidationWithFullInheritanceHierarchy() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.VALIDATIONS);
    }
  }

  @Nested
  @DisplayName("Law Tests")
  class LawTests {

    @Test
    @DisplayName("Test Functor laws (identity and composition)")
    void testFunctorLaws() {
      TypeClassContract.<IdKind.Witness>functor(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withEqualityChecker(equalityChecker)
          .verifyOnly(Category.LAWS);
    }

    @Test
    @DisplayName("Test Applicative laws (identity, homomorphism, interchange)")
    void testApplicativeLaws() {
      TypeClassContract.<IdKind.Witness>applicative(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .verifyOnly(Category.LAWS);
    }

    @Test
    @DisplayName("Test Monad laws (left identity, right identity, associativity)")
    void testMonadLaws() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .verifyOnly(Category.LAWS);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Test with null value")
    void testWithNullValue() {
      Kind<IdKind.Witness, Integer> nullKind = idOf(null);

      // Test that Id can hold null and basic operations work
      assertThatId(nullKind).hasValue(null);

      // Test that of() works with null
      Kind<IdKind.Witness, Integer> created = monad.of(null);
      assertThatId(created).hasValue(null);

      // Test with null-safe operations
      Function<Integer, String> nullSafeMapper = i -> i == null ? "null" : i.toString();
      var mapped = monad.map(nullSafeMapper, nullKind);
      assertThatId(mapped).hasValue("null");

      Function<Integer, Kind<IdKind.Witness, String>> nullSafeFlatMapper =
          i -> monad.of(i == null ? "null" : i.toString());
      var flatMapped = monad.flatMap(nullSafeFlatMapper, nullKind);
      assertThatId(flatMapped).hasValue("null");
    }

    @Test
    @DisplayName("Test map with identity function")
    void testMapWithIdentityFunction() {
      Function<Integer, Integer> identity = i -> i;
      var mapped = monad.map(identity, validKind);

      Integer originalValue = extractValue(validKind);
      Integer mappedValue = extractValue(mapped);

      assertThat(mappedValue).isEqualTo(originalValue);
    }

    @Test
    @DisplayName("Test flatMap with of")
    void testFlatMapWithOf() {
      Function<Integer, Kind<IdKind.Witness, Integer>> ofFunc = monad::of;
      var flatMapped = monad.flatMap(ofFunc, validKind);

      assertThat(equalityChecker.test(flatMapped, validKind))
          .as("flatMap with of should preserve identity")
          .isTrue();
    }

    @Test
    @DisplayName("Test multiple sequential operations")
    void testMultipleSequentialOperations() {
      var result =
          monad.flatMap(
              i -> monad.map(s -> s.length(), monad.flatMap(testFunction, monad.of(i))), validKind);

      assertThat(result).isNotNull();
      assertThat(extractValue(result)).isNotNull();
    }

    @Test
    @DisplayName("Chained operations maintain correctness")
    void chainedOperationsMaintainCorrectness() {
      var initial = idOf(5);

      Function<Integer, Kind<IdKind.Witness, Integer>> step1 = x -> idOf(x * 2);
      var step1Result = monad.flatMap(step1, initial);

      Function<Integer, Kind<IdKind.Witness, String>> step2 = y -> idOf("N" + y);
      var finalResult = monad.flatMap(step2, step1Result);

      assertThatId(finalResult).hasValue("N10");
    }
  }

  @Nested
  @DisplayName("Exception Propagation")
  class ExceptionPropagation {

    @Test
    @DisplayName("Test map propagates exceptions")
    void testMapPropagatesExceptions() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.EXCEPTIONS);
    }

    @Test
    @DisplayName("Test flatMap propagates exceptions")
    void testFlatMapPropagatesExceptions() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<Integer, Kind<IdKind.Witness, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };

      Assertions.assertThatThrownBy(() -> monad.flatMap(throwingFlatMapper, validKind))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Test ap propagates exceptions from function")
    void testApPropagatesExceptionsFromFunction() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<Integer, String> throwingFunction =
          i -> {
            throw testException;
          };
      Kind<IdKind.Witness, Function<Integer, String>> throwingFunctionKind = idOf(throwingFunction);

      Assertions.assertThatThrownBy(() -> monad.ap(throwingFunctionKind, validKind))
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Selective Testing")
  class SelectiveTesting {

    @Test
    @DisplayName("Skip operations")
    void skipOperations() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .verifyOnly(Category.VALIDATIONS, Category.EXCEPTIONS, Category.LAWS);
    }

    @Test
    @DisplayName("Skip validations")
    void skipValidations() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .verifyOnly(Category.OPERATIONS, Category.EXCEPTIONS, Category.LAWS);
    }

    @Test
    @DisplayName("Skip exceptions")
    void skipExceptions() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.LAWS);
    }

    @Test
    @DisplayName("Skip laws")
    void skipLaws() {
      TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
    }
  }
}
