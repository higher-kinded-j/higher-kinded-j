// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.OR_ELSE_GET;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.util.validation.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for Maybe using standardised patterns.
 *
 * <p>This test combines:
 *
 * <ul>
 *   <li>Type class behaviour testing using the new hierarchical API
 *   <li>Maybe-specific functionality testing
 *   <li>Edge cases and performance characteristics
 * </ul>
 *
 * <p>Coverage includes:
 *
 * <ul>
 *   <li>Factory methods (just, nothing, fromNullable)
 *   <li>Functor operations (map)
 *   <li>Monad operations (flatMap)
 *   <li>Utility methods (get, orElse, orElseGet)
 *   <li>Object methods (toString, equals, hashCode)
 *   <li>Algebraic laws (identity, composition, associativity)
 *   <li>Performance and memory characteristics
 * </ul>
 */
@DisplayName("Maybe<T> Complete Test Suite")
class MaybeTest extends TypeClassTestBase<MaybeKind.Witness, String, Integer> {

  // Maybe-specific test fixtures
  private final String justValue = "Present Value";
  private final Maybe<String> justInstance = Maybe.just(justValue);
  private final Maybe<String> nothingInstance = Maybe.nothing();
  private final Maybe<String> fromNullableJust = Maybe.fromNullable(justValue);
  private final Maybe<String> fromNullableNothing = Maybe.fromNullable(null);

  // Type class testing fixtures
  private final MaybeMonad MONAD = MaybeMonad.INSTANCE;

  @Override
  protected Kind<MaybeKind.Witness, String> createValidKind() {
    return MaybeKindHelper.MAYBE.widen(justInstance);
  }

  @Override
  protected Kind<MaybeKind.Witness, String> createValidKind2() {
    return MaybeKindHelper.MAYBE.widen(Maybe.just("Another"));
  }

  @Override
  protected Function<String, Integer> createValidMapper() {
    return String::length;
  }

  @Override
  protected BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>>
      createEqualityChecker() {
    return (k1, k2) -> MaybeKindHelper.MAYBE.narrow(k1).equals(MaybeKindHelper.MAYBE.narrow(k2));
  }

  @Override
  protected Function<String, Kind<MaybeKind.Witness, Integer>> createValidFlatMapper() {
    return s -> MaybeKindHelper.MAYBE.widen(Maybe.just(s.length()));
  }

  @Override
  protected Kind<MaybeKind.Witness, Function<String, Integer>> createValidFunctionKind() {
    return MaybeKindHelper.MAYBE.widen(Maybe.just(validMapper));
  }

  @Override
  protected BiFunction<String, String, Integer> createValidCombiningFunction() {
    return (s1, s2) -> s1.length() + s2.length();
  }

  @Override
  protected String createTestValue() {
    return justValue;
  }

  @Override
  protected Function<String, Kind<MaybeKind.Witness, Integer>> createTestFunction() {
    return s -> MaybeKindHelper.MAYBE.widen(Maybe.just(s.length()));
  }

  @Override
  protected Function<Integer, Kind<MaybeKind.Witness, Integer>> createChainFunction() {
    return i -> MaybeKindHelper.MAYBE.widen(Maybe.just(i * 2));
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      // Test complete Monad behaviour using the new hierarchical API
      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<String>instance(MONAD)
          .<Integer>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(justValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<String>instance(MONAD)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Maybe core type tests")
    void runCompleteMaybeCoreTypeTests() {
      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(validMapper)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Individual Type Class Components")
  class IndividualTypeClassComponents {

    @Test
    @DisplayName("Test Functor operations only")
    void testFunctorOperationsOnly() {
      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<String>instance(MONAD)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test Functor validations only")
    void testFunctorValidationsOnly() {
      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(validMapper)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test Functor exception propagation only")
    void testFunctorExceptionPropagationOnly() {
      RuntimeException testException = new RuntimeException("Test exception: functor test");
      Function<String, Integer> throwingMapper = TestFunctions.throwingFunction(testException);

      // Just instances should propagate exceptions
      assertThatThrownBy(() -> MONAD.map(throwingMapper, validKind)).isSameAs(testException);

      // Nothing instances should not call mapper
      Kind<MaybeKind.Witness, String> nothingKind = MaybeKindHelper.MAYBE.widen(nothingInstance);
      assertThatCode(() -> MONAD.map(throwingMapper, nothingKind)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test Functor laws only")
    void testFunctorLawsOnly() {
      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<String>instance(MONAD)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }

    @Test
    @DisplayName("Test Monad operations only")
    void testMonadOperationsOnly() {
      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<String>instance(MONAD)
          .<Integer>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test Monad validations only with full hierarchy")
    void testMonadValidationsOnly() {
      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(validMapper)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test Monad exception propagation only")
    void testMonadExceptionPropagationOnly() {
      RuntimeException testException = new RuntimeException("Test exception: monad test");
      Function<String, Kind<MaybeKind.Witness, Integer>> throwingFlatMapper =
          TestFunctions.throwingFunction(testException);

      // Just instances should propagate exceptions
      assertThatThrownBy(() -> MONAD.flatMap(throwingFlatMapper, validKind))
          .isSameAs(testException);

      // Nothing instances should not call flatMapper
      Kind<MaybeKind.Witness, String> nothingKind = MaybeKindHelper.MAYBE.widen(nothingInstance);
      assertThatCode(() -> MONAD.flatMap(throwingFlatMapper, nothingKind))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test Monad laws only")
    void testMonadLawsOnly() {
      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<String>instance(MONAD)
          .<Integer>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(justValue, testFunction, chainFunction, equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }

  @Nested
  @DisplayName("Factory Methods - Complete Coverage")
  class FactoryMethods {

    @Test
    @DisplayName("just() creates correct Just instances with all value types")
    void justCreatesCorrectInstances() {
      // Non-null values
      assertThat(justInstance).isInstanceOf(Just.class);
      assertThat(justInstance.isJust()).isTrue();
      assertThat(justInstance.isNothing()).isFalse();
      assertThat(justInstance.get()).isEqualTo(justValue);

      // Complex types
      List<Integer> list = List.of(1, 2, 3);
      Maybe<List<Integer>> listJust = Maybe.just(list);
      assertThat(listJust.get()).isSameAs(list);

      // Primitives and wrappers
      Maybe<Boolean> boolJust = Maybe.just(true);
      assertThat(boolJust.get()).isTrue();
    }

    @Test
    @DisplayName("just() validates non-null requirement")
    void justValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Maybe.just(null))
          .withMessageContaining("Maybe.just value cannot be null");
    }

    @Test
    @DisplayName("nothing() returns singleton Nothing instance")
    void nothingReturnsSingleton() {
      assertThat(nothingInstance).isInstanceOf(Nothing.class);
      assertThat(nothingInstance.isNothing()).isTrue();
      assertThat(nothingInstance.isJust()).isFalse();

      // Verify singleton behaviour
      Maybe<Integer> nothingInt = Maybe.nothing();
      assertThat(nothingInstance).isSameAs(nothingInt);
    }

    @Test
    @DisplayName("fromNullable() creates Just for non-null values")
    void fromNullableCreatesJustForNonNull() {
      assertThat(fromNullableJust).isInstanceOf(Just.class);
      assertThat(fromNullableJust.get()).isEqualTo(justValue);

      // Empty string should be Just
      Maybe<String> emptyJust = Maybe.fromNullable("");
      assertThat(emptyJust.isJust()).isTrue();
      assertThat(emptyJust.get()).isEmpty();
    }

    @Test
    @DisplayName("fromNullable() creates Nothing for null values")
    void fromNullableCreatesNothingForNull() {
      assertThat(fromNullableNothing).isInstanceOf(Nothing.class);
      assertThat(fromNullableNothing.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Getter Methods - Comprehensive Edge Cases")
  class GetterMethodsTests {

    @Test
    @DisplayName("get() works correctly on all Just variations")
    void getWorksCorrectly() {
      // Standard case
      assertThat(justInstance.get()).isEqualTo(justValue);

      // Complex types
      List<String> list = List.of("a", "b", "c");
      Maybe<List<String>> listJust = Maybe.just(list);
      assertThat(listJust.get()).isSameAs(list);

      // Nested Maybe (Maybe as value)
      Maybe<Integer> nested = Maybe.just(99);
      Maybe<Maybe<Integer>> nestedJust = Maybe.just(nested);
      assertThat(nestedJust.get()).isSameAs(nested);
    }

    @Test
    @DisplayName("get() throws correct exceptions on Nothing instances")
    void getThrowsOnNothing() {
      // Standard Nothing
      assertThatThrownBy(nothingInstance::get)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot call get() on Nothing");

      // Nothing from different sources
      assertThatThrownBy(() -> Maybe.nothing().get())
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot call get() on Nothing");

      assertThatThrownBy(() -> Maybe.fromNullable(null).get())
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot call get() on Nothing");
    }

    @Test
    @DisplayName("isJust() and isNothing() work correctly")
    void isJustAndIsNothingWorkCorrectly() {
      // Just instance
      assertThat(justInstance.isJust()).isTrue();
      assertThat(justInstance.isNothing()).isFalse();

      // Nothing instance
      assertThat(nothingInstance.isJust()).isFalse();
      assertThat(nothingInstance.isNothing()).isTrue();

      // fromNullable instances
      assertThat(fromNullableJust.isJust()).isTrue();
      assertThat(fromNullableJust.isNothing()).isFalse();
      assertThat(fromNullableNothing.isJust()).isFalse();
      assertThat(fromNullableNothing.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("orElse / orElseGet - Complete Validation")
  class OrElseTests {

    private final String defaultValue = "Default";

    @Test
    @DisplayName("orElse() returns value for Just instances")
    void orElseReturnsValueForJust() {
      assertThat(justInstance.orElse(defaultValue)).isEqualTo(justValue);

      // Complex types
      List<String> list = List.of("a", "b");
      List<String> defaultList = List.of("x", "y");
      Maybe<List<String>> listJust = Maybe.just(list);
      assertThat(listJust.orElse(defaultList)).isSameAs(list);
    }

    @Test
    @DisplayName("orElse() returns default for Nothing instances")
    void orElseReturnsDefaultForNothing() {
      assertThat(nothingInstance.orElse(defaultValue)).isEqualTo(defaultValue);

      // Test with null default (should work)
      assertThat(nothingInstance.orElse(null)).isNull();
    }

    @Test
    @DisplayName("orElseGet() returns value for Just without calling supplier")
    void orElseGetReturnsValueForJustWithoutCallingSupplier() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return defaultValue;
          };

      assertThat(justInstance.orElseGet(trackingSupplier)).isEqualTo(justValue);
      assertThat(supplierCalled).isFalse();
    }

    @Test
    @DisplayName("orElseGet() calls supplier and returns result for Nothing")
    void orElseGetCallsSupplierForNothing() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return defaultValue;
          };

      assertThat(nothingInstance.orElseGet(trackingSupplier)).isEqualTo(defaultValue);
      assertThat(supplierCalled).isTrue();
    }

    @Test
    @DisplayName("orElseGet() validates null supplier using ValidationTestBuilder")
    void orElseGetValidatesNullSupplier() {
      ValidationTestBuilder.create()
          .assertFunctionNull(
              () -> nothingInstance.orElseGet(null), "otherSupplier", Maybe.class, OR_ELSE_GET)
          .execute();
    }

    @Test
    @DisplayName("orElseGet() doesn't validate null supplier for Just")
    void orElseGetDoesNotValidateNullSupplierForJust() {
      // Supplier isn't called for Just, so null supplier is acceptable
      assertThatCode(() -> justInstance.orElseGet(null)).doesNotThrowAnyException();
      assertThat(justInstance.orElseGet(null)).isEqualTo(justValue);
    }
  }

  @Nested
  @DisplayName("Map Method - Comprehensive Testing")
  class MapMethodTests {

    @Test
    @DisplayName("map() applies function to Just values")
    void mapAppliesFunctionToJust() {
      // Standard transformation
      Maybe<Integer> result = justInstance.map(String::length);
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo(justValue.length());

      // Complex transformation
      Maybe<List<Character>> listResult =
          justInstance.map(s -> s.chars().mapToObj(c -> (char) c).toList());
      assertThat(listResult.get()).hasSize(justValue.length());

      // Transformation returning null creates Nothing
      Maybe<String> nullResult = justInstance.map(s -> null);
      assertThat(nullResult.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map() preserves Nothing instances unchanged")
    void mapPreservesNothingInstances() {
      // Standard Nothing
      Maybe<Integer> result = nothingInstance.map(String::length);
      assertThat(result).isSameAs(nothingInstance);
      assertThat(result.isNothing()).isTrue();

      // Complex transformation on Nothing
      Maybe<List<String>> complexResult = nothingInstance.map(s -> List.of(s, s.toUpperCase()));
      assertThat(complexResult).isSameAs(nothingInstance);
      assertThat(complexResult.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map() validates null mapper using ValidationTestBuilder")
    void mapValidatesNullMapper() {
      ValidationTestBuilder.create()
          .assertMapperNull(() -> justInstance.map(null), "mapper", Just.class, Operation.MAP)
          .assertMapperNull(() -> nothingInstance.map(null), "mapper", Maybe.class, Operation.MAP)
          .execute();
    }

    @Test
    @DisplayName("map() handles exception propagation and chaining")
    void mapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: map test");
      Function<String, Integer> throwingMapper = TestFunctions.throwingFunction(testException);

      // Just instances should propagate exceptions
      assertThatThrownBy(() -> justInstance.map(throwingMapper)).isSameAs(testException);

      // Nothing instances should not call mapper
      Maybe<Integer> nothingResult = nothingInstance.map(throwingMapper);
      assertThat(nothingResult).isSameAs(nothingInstance);

      // Test chaining
      Maybe<String> start = Maybe.just("hello");
      Maybe<String> chainResult =
          start.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));
      assertThat(chainResult.get()).isEqualTo("HELLO!HELLO!");

      // Test chaining with Nothing short-circuit
      Maybe<String> nothingStart = Maybe.nothing();
      Maybe<String> nothingChainResult =
          nothingStart.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));
      assertThat(nothingChainResult.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map() handles null-returning functions")
    void mapHandlesNullReturningFunctions() {
      Function<String, Integer> nullReturningMapper = TestFunctions.nullReturningFunction();

      Maybe<Integer> result = justInstance.map(nullReturningMapper);
      assertThat(result.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("FlatMap Method - Comprehensive Monadic Testing")
  class FlatMapMethodTests {

    @Test
    @DisplayName("flatMap() applies function to Just values")
    void flatMapAppliesFunctionToJust() {
      Function<String, Maybe<Integer>> mapper = s -> Maybe.just(s.length());

      Maybe<Integer> result = justInstance.flatMap(mapper);
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo(justValue.length());

      // Test returning Nothing from flatMap
      Function<String, Maybe<Integer>> nothingMapper = s -> Maybe.nothing();
      Maybe<Integer> nothingResult = justInstance.flatMap(nothingMapper);
      assertThat(nothingResult.isNothing()).isTrue();
    }

    @Test
    @DisplayName("flatMap() preserves Nothing instances unchanged")
    void flatMapPreservesNothingInstances() {
      Function<String, Maybe<Integer>> mapper = s -> Maybe.just(s.length());

      Maybe<Integer> result = nothingInstance.flatMap(mapper);
      assertThat(result).isSameAs(nothingInstance);
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("flatMap() validates parameters using ValidationTestBuilder")
    void flatMapValidatesParameters() {
      ValidationTestBuilder.create()
          .assertFlatMapperNull(() -> justInstance.flatMap(null), "mapper", Just.class, FLAT_MAP)
          .assertFlatMapperNull(() -> nothingInstance.flatMap(null), "mapper", Maybe.class, FLAT_MAP)
          .execute();
    }

    @Test
    @DisplayName("flatMap() validates non-null results")
    void flatMapValidatesNonNullResults() {
      Function<String, Maybe<Integer>> nullReturningMapper = s -> null;

      assertThatThrownBy(() -> justInstance.flatMap(nullReturningMapper))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("flatMap returned null");
    }

    @Test
    @DisplayName("flatMap() supports complex chaining patterns")
    void flatMapSupportsComplexChaining() {
      // Success chain
      Maybe<Integer> start = Maybe.just(10);
      Maybe<String> result =
          start
              .flatMap(i -> Maybe.just(i * 2))
              .flatMap(i -> Maybe.just("Value: " + i))
              .flatMap(s -> Maybe.just(s.toUpperCase()));
      assertThat(result.get()).isEqualTo("VALUE: 20");

      // Failure in middle of chain
      Maybe<String> failureResult =
          start
              .flatMap(i -> Maybe.just(i * 2))
              .flatMap(i -> Maybe.nothing())
              .flatMap(i -> Maybe.just("Should not reach"));
      assertThat(failureResult.isNothing()).isTrue();

      // Mixed operations
      Maybe<Integer> mixedResult =
          start
              .map(i -> i + 5) // 15
              .flatMap(i -> Maybe.just(i * 2)) // 30
              .map(i -> i - 10); // 20
      assertThat(mixedResult.get()).isEqualTo(20);
    }

    @Test
    @DisplayName("flatMap() handles exception propagation")
    void flatMapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap test");
      Function<String, Maybe<Integer>> throwingMapper =
          TestFunctions.throwingFunction(testException);

      // Just instances should propagate exceptions
      assertThatThrownBy(() -> justInstance.flatMap(throwingMapper)).isSameAs(testException);

      // Nothing instances should not call mapper
      Maybe<Integer> nothingResult = nothingInstance.flatMap(throwingMapper);
      assertThat(nothingResult).isSameAs(nothingInstance);
    }
  }

  @Nested
  @DisplayName("ToString and Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() provides meaningful representations")
    void toStringProvidesMeaningfulRepresentations() {
      // Just toString
      assertThat(justInstance.toString()).isEqualTo("Just(" + justValue + ")");

      // Nothing toString
      assertThat(nothingInstance.toString()).isEqualTo("Nothing");

      // Complex types
      Maybe<List<String>> complexJust = Maybe.just(List.of("a", "b"));
      assertThat(complexJust.toString()).isEqualTo("Just([a, b])");
    }

    @Test
    @DisplayName("equals() and hashCode() work correctly")
    void equalsAndHashCodeWorkCorrectly() {
      // Same instances
      assertThat(justInstance).isEqualTo(justInstance);
      assertThat(nothingInstance).isEqualTo(nothingInstance);

      // Equal instances
      Maybe<String> anotherJust = Maybe.just(justValue);
      assertThat(justInstance).isEqualTo(anotherJust);
      assertThat(justInstance.hashCode()).isEqualTo(anotherJust.hashCode());

      // Different instances
      assertThat(justInstance).isNotEqualTo(nothingInstance);
      assertThat(justInstance).isNotEqualTo(Maybe.just("different"));

      // Nothing equality
      Maybe<String> anotherNothing = Maybe.nothing();
      assertThat(nothingInstance).isEqualTo(anotherNothing);
      assertThat(nothingInstance).isSameAs(anotherNothing); // Singleton
      assertThat(nothingInstance.hashCode()).isEqualTo(anotherNothing.hashCode());
    }
  }

  @Nested
  @DisplayName("Advanced Usage Patterns")
  class AdvancedUsagePatterns {

    @Test
    @DisplayName("Maybe as functor maintains structure")
    void maybeAsFunctorMaintainsStructure() {
      // Multiple transformations should maintain Maybe structure
      Maybe<Integer> start = Maybe.just(5);

      Maybe<Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isCloseTo(Math.sqrt(10.5), within(0.001));
    }

    @Test
    @DisplayName("Maybe for railway-oriented programming")
    void maybeForRailwayOrientedProgramming() {
      // Simulate a pipeline where each step can fail
      Function<String, Maybe<Integer>> parseInteger =
          s -> {
            try {
              return Maybe.just(Integer.parseInt(s));
            } catch (NumberFormatException e) {
              return Maybe.nothing();
            }
          };

      Function<Integer, Maybe<Double>> squareRoot =
          i -> {
            if (i < 0) {
              return Maybe.nothing();
            }
            return Maybe.just(Math.sqrt(i));
          };

      Function<Double, Maybe<String>> formatResult =
          d -> {
            if (d > 100) {
              return Maybe.nothing();
            }
            return Maybe.just(String.format("%.2f", d));
          };

      // Success path
      Maybe<String> success =
          Maybe.just("16").flatMap(parseInteger).flatMap(squareRoot).flatMap(formatResult);
      assertThat(success.isJust()).isTrue();
      assertThat(success.get()).isEqualTo("4.00");

      // Failure paths
      Maybe<String> parseFailure =
          Maybe.just("not-a-number")
              .flatMap(parseInteger)
              .flatMap(squareRoot)
              .flatMap(formatResult);
      assertThat(parseFailure.isNothing()).isTrue();

      Maybe<String> negativeFailure =
          Maybe.just("-4").flatMap(parseInteger).flatMap(squareRoot).flatMap(formatResult);
      assertThat(negativeFailure.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Maybe pattern matching with expressions")
    void maybePatternMatchingWithExpressions() {
      // Test exhaustive handling
      Function<Maybe<Integer>, String> processMaybe =
          maybe ->
              switch (maybe) {
                case Just<Integer>(var value) -> "Just: " + value;
                case Nothing<Integer> n -> "Nothing";
              };

      assertThat(processMaybe.apply(Maybe.just(42))).isEqualTo("Just: 42");
      assertThat(processMaybe.apply(Maybe.nothing())).isEqualTo("Nothing");

      // Test with nested Maybe
      Maybe<Maybe<Integer>> nested = Maybe.just(Maybe.just(42));
      String nestedResult =
          switch (nested) {
            case Just<Maybe<Integer>>(var inner) ->
                switch (inner) {
                  case Just<Integer>(var value) -> "Nested value: " + value;
                  case Nothing<Integer> n -> "Inner nothing";
                };
            case Nothing<Maybe<Integer>> n -> "Outer nothing";
          };
      assertThat(nestedResult).isEqualTo("Nested value: 42");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("Maybe operations have predictable performance")
    void maybeOperationsHavePredictablePerformance() {
      // Test that basic operations are fast
      Maybe<Integer> test = Maybe.just(42);

      // Simple operations should be very fast
      long start = System.nanoTime();
      for (int i = 0; i < 10000; i++) {
        test.map(x -> x + 1).flatMap(x -> Maybe.just(x * 2)).isJust();
      }
      long duration = System.nanoTime() - start;

      // Should complete in reasonable time (less than 100ms for 10k ops)
      assertThat(duration).isLessThan(100_000_000L);
    }

    @Test
    @DisplayName("Nothing instances are reused efficiently")
    void nothingInstancesAreReusedEfficiently() {
      Maybe<String> nothing = Maybe.nothing();

      // map should return same instance for Nothing
      Maybe<Integer> mapped = nothing.map(String::length);
      assertThat(mapped).isSameAs(nothing);

      // Multiple map operations should all return same instance
      Maybe<Boolean> multiMapped = nothing.map(String::length).map(len -> len > 0).map(b -> !b);
      assertThat(multiMapped).isSameAs(nothing);

      // flatMap should also return same instance for Nothing
      Maybe<Integer> flatMapped = nothing.flatMap(s -> Maybe.just(s.length()));
      assertThat(flatMapped).isSameAs(nothing);
    }

    @Test
    @DisplayName("Memory usage is reasonable for large chains")
    void memoryUsageIsReasonableForLargeChains() {
      // Test that long chains don't create excessive rubbish
      Maybe<Integer> start = Maybe.just(1);

      Maybe<Integer> result = start;
      for (int i = 0; i < 1000; i++) {
        final int increment = i;
        result = result.map(x -> x + increment);
      }

      // Should complete without memory issues
      assertThat(result.get()).isEqualTo(1 + (999 * 1000) / 2);

      // Nothing chains should be even more efficient
      Maybe<Integer> nothingStart = Maybe.nothing();
      Maybe<Integer> nothingResult = nothingStart;
      for (int i = 0; i < 1000; i++) {
        int finalI = i;
        nothingResult = nothingResult.map(x -> x + finalI);
      }

      // Should be same instance throughout
      assertThat(nothingResult).isSameAs(nothingStart);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesTests {

    @Test
    @DisplayName("Maybe handles extreme values correctly")
    void maybeHandlesExtremeValuesCorrectly() {
      // Very large strings
      String largeString = "x".repeat(10000);
      Maybe<String> largeJust = Maybe.just(largeString);
      assertThat(largeJust.map(String::length).get()).isEqualTo(10000);

      // Maximum/minimum integer values
      Maybe<Integer> maxInt = Maybe.just(Integer.MAX_VALUE);
      Maybe<Long> promoted = maxInt.map(i -> i.longValue() + 1);
      assertThat(promoted.get()).isEqualTo((long) Integer.MAX_VALUE + 1);

      // Very nested structures
      Maybe<Maybe<Maybe<Integer>>> tripleNested = Maybe.just(Maybe.just(Maybe.just(42)));

      Maybe<Integer> flattened =
          tripleNested.flatMap(inner -> inner.flatMap(innerInner -> innerInner));
      assertThat(flattened.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Maybe operations are stack-safe for deep recursion")
    void maybeOperationsAreStackSafe() {
      // Test that deep map chains don't cause stack overflow
      Maybe<Integer> start = Maybe.just(0);

      Maybe<Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = result.map(x -> x + 1);
      }

      assertThat(result.get()).isEqualTo(10000);

      // Test with flatMap chains
      Maybe<Integer> flatMapResult = start;
      for (int i = 0; i < 1000; i++) {
        flatMapResult = flatMapResult.flatMap(x -> Maybe.just(x + 1));
      }

      assertThat(flatMapResult.get()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Maybe maintains referential transparency")
    void maybeMaintainsReferentialTransparency() {
      // Same operations should always produce same results
      Maybe<Integer> maybe = Maybe.just(42);
      Function<Integer, String> transform = i -> "value:" + i;

      Maybe<String> result1 = maybe.map(transform);
      Maybe<String> result2 = maybe.map(transform);

      assertThat(result1).isEqualTo(result2);
      assertThat(result1.get()).isEqualTo(result2.get());

      // This should be true for all Maybe operations
      Maybe<String> flatMapResult1 = maybe.flatMap(i -> Maybe.just("flat:" + i));
      Maybe<String> flatMapResult2 = maybe.flatMap(i -> Maybe.just("flat:" + i));

      assertThat(flatMapResult1).isEqualTo(flatMapResult2);
    }
  }
}
