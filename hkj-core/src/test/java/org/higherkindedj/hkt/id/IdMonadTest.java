// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.id.IdAssert.assertThatId;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for IdMonad implementation.
 *
 * <p>Tests the Monad type class implementation for Id, including all inherited operations from
 * Functor and Applicative, as well as Monad-specific operations and laws.
 */
@DisplayName("IdMonad Complete Test Suite")
class IdMonadTest extends IdTestBase {

  private IdMonad monad;

  @BeforeEach
  void setUpMonad() {
    monad = IdMonad.instance();
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Monad test pattern with validation contexts")
    void runCompleteMonadTestPatternWithValidationContexts() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IdMonad.class)
          .withApFrom(IdMonad.class)
          .withFlatMapFrom(IdMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(IdMonadTest.class);

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
      TypeClassTest.<IdKind.Witness>functor(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withEqualityChecker(equalityChecker)
          .testOperations();
    }

    @Test
    @DisplayName("Test Applicative operations (of, ap, map2)")
    void testApplicativeOperations() {
      TypeClassTest.<IdKind.Witness>applicative(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test Monad operations (flatMap)")
    void testMonadOperations() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Test null parameter validations")
    void testAllNullParameterValidations() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyValidations()
          .test();
    }

    @Test
    @DisplayName("Test validation with Functor context for map")
    void testValidationWithFunctorContext() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IdMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test validation with Monad context for flatMap")
    void testValidationWithMonadContext() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withFlatMapFrom(IdMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test validation with full inheritance hierarchy")
    void testValidationWithFullInheritanceHierarchy() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IdMonad.class)
          .withApFrom(IdMonad.class)
          .withFlatMapFrom(IdMonad.class)
          .testValidations();
    }
  }

  @Nested
  @DisplayName("Law Tests")
  class LawTests {

    @Test
    @DisplayName("Test Functor laws (identity and composition)")
    void testFunctorLaws() {
      TypeClassTest.<IdKind.Witness>functor(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withEqualityChecker(equalityChecker)
          .testLaws();
    }

    @Test
    @DisplayName("Test Applicative laws (identity, homomorphism, interchange)")
    void testApplicativeLaws() {
      TypeClassTest.<IdKind.Witness>applicative(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .testLaws();
    }

    @Test
    @DisplayName("Test Monad laws (left identity, right identity, associativity)")
    void testMonadLaws() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
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
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyExceptions()
          .test();
    }

    @Test
    @DisplayName("Test flatMap propagates exceptions")
    void testFlatMapPropagatesExceptions() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<Integer, Kind<IdKind.Witness, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> monad.flatMap(throwingFlatMapper, validKind))
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

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> monad.ap(throwingFunctionKind, validKind))
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Selective Testing")
  class SelectiveTesting {

    @Test
    @DisplayName("Skip operations")
    void skipOperations() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .selectTests()
          .skipOperations()
          .test();
    }

    @Test
    @DisplayName("Skip validations")
    void skipValidations() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .selectTests()
          .skipValidations()
          .test();
    }

    @Test
    @DisplayName("Skip exceptions")
    void skipExceptions() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .selectTests()
          .skipExceptions()
          .test();
    }

    @Test
    @DisplayName("Skip laws")
    void skipLaws() {
      TypeClassTest.<IdKind.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .skipLaws()
          .test();
    }
  }
}
