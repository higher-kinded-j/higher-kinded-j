// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
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
class IdMonadTest extends TypeClassTestBase<Id.Witness, Integer, String> {

  private IdMonad monad;

  @Override
  protected Kind<Id.Witness, Integer> createValidKind() {
    return ID.widen(Id.of(42));
  }

  @Override
  protected Kind<Id.Witness, Integer> createValidKind2() {
    return ID.widen(Id.of(24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected BiPredicate<Kind<Id.Witness, ?>, Kind<Id.Witness, ?>> createEqualityChecker() {
    return (k1, k2) -> {
      Id<?> id1 = ID.narrow(k1);
      Id<?> id2 = ID.narrow(k2);
      Object v1 = id1.value();
      Object v2 = id2.value();
      return v1 == null ? v2 == null : v1.equals(v2);
    };
  }

  @Override
  protected Function<Integer, Kind<Id.Witness, String>> createValidFlatMapper() {
    return i -> ID.widen(Id.of(i.toString()));
  }

  @Override
  protected Kind<Id.Witness, Function<Integer, String>> createValidFunctionKind() {
    return ID.widen(Id.of(Object::toString));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return 100;
  }

  @Override
  protected Function<Integer, Kind<Id.Witness, String>> createTestFunction() {
    return i -> ID.widen(Id.of("value:" + i));
  }

  @Override
  protected Function<String, Kind<Id.Witness, String>> createChainFunction() {
    return s -> ID.widen(Id.of(s.toUpperCase()));
  }

  @BeforeEach
  void setUp() {
    monad = IdMonad.instance();
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind) // Explicitly specify B = String
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Monad test pattern with validation contexts")
    void runCompleteMonadTestPatternWithValidationContexts() {
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind) // Explicitly specify B = String
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
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("Test Functor operations (map)")
    void testFunctorOperations() {
      TypeClassTest.<Id.Witness>functor(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withEqualityChecker(equalityChecker)
          .testOperations();
    }

    @Test
    @DisplayName("Test Applicative operations (of, ap, map2)")
    void testApplicativeOperations() {
      TypeClassTest.<Id.Witness>applicative(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test Monad operations (flatMap)")
    void testMonadOperations() {
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      // Test default validation (no class context specified)
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      // Configure only map to use IdMonad context
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      // Configure only flatMap to use IdMonad context
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      // Configure all operations to use IdMonad context
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      TypeClassTest.<Id.Witness>functor(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withEqualityChecker(equalityChecker)
          .testLaws();
    }

    @Test
    @DisplayName("Test Applicative laws (identity, homomorphism, interchange)")
    void testApplicativeLaws() {
      TypeClassTest.<Id.Witness>applicative(IdMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .testLaws();
    }

    @Test
    @DisplayName("Test Monad laws (left identity, right identity, associativity)")
    void testMonadLaws() {
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      Kind<Id.Witness, Integer> nullKind = ID.widen(Id.of(null));

      // Test that Id can hold null and basic operations work
      Integer value = ID.narrow(nullKind).value();
      assertThat(value).isNull();

      // Test that of() works with null
      Kind<Id.Witness, Integer> created = monad.of(null);
      assertThat(ID.narrow(created).value()).isNull();

      // Test with null-safe operations
      Function<Integer, String> nullSafeMapper = i -> i == null ? "null" : i.toString();
      Kind<Id.Witness, String> mapped = monad.map(nullSafeMapper, nullKind);
      assertThat(ID.narrow(mapped).value()).isEqualTo("null");

      Function<Integer, Kind<Id.Witness, String>> nullSafeFlatMapper =
          i -> monad.of(i == null ? "null" : i.toString());
      Kind<Id.Witness, String> flatMapped = monad.flatMap(nullSafeFlatMapper, nullKind);
      assertThat(ID.narrow(flatMapped).value()).isEqualTo("null");
    }

    @Test
    @DisplayName("Test map with identity function")
    void testMapWithIdentityFunction() {
      Function<Integer, Integer> identity = i -> i;
      Kind<Id.Witness, Integer> mapped = monad.map(identity, validKind);

      Integer originalValue = ID.narrow(validKind).value();
      Integer mappedValue = ID.narrow(mapped).value();

      assertThat(mappedValue).isEqualTo(originalValue);
    }

    @Test
    @DisplayName("Test flatMap with of")
    void testFlatMapWithOf() {
      Function<Integer, Kind<Id.Witness, Integer>> ofFunc = monad::of;
      Kind<Id.Witness, Integer> flatMapped = monad.flatMap(ofFunc, validKind);

      assertThat(equalityChecker.test(flatMapped, validKind))
          .as("flatMap with of should preserve identity")
          .isTrue();
    }

    @Test
    @DisplayName("Test multiple sequential operations")
    void testMultipleSequentialOperations() {
      Kind<Id.Witness, Integer> result =
          monad.flatMap(
              i -> monad.map(s -> s.length(), monad.flatMap(testFunction, monad.of(i))), validKind);

      assertThat(result).isNotNull();
      assertThat(ID.narrow(result).value()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Exception Propagation")
  class ExceptionPropagation {

    @Test
    @DisplayName("Test map propagates exceptions")
    void testMapPropagatesExceptions() {
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      Function<Integer, Kind<Id.Witness, String>> throwingFlatMapper =
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
      Kind<Id.Witness, Function<Integer, String>> throwingFunctionKind =
          ID.widen(Id.of(throwingFunction));

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
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
      TypeClassTest.<Id.Witness>monad(IdMonad.class)
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
