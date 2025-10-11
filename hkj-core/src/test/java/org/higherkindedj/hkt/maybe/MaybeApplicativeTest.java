// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeMonad Applicative Operations Complete Test Suite")
class MaybeApplicativeTest extends TypeClassTestBase<MaybeKind.Witness, Integer, String> {

  private MaybeMonad applicative;
  private Applicative<MaybeKind.Witness> applicativeTyped;

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
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>>
      createEqualityChecker() {
    return (k1, k2) -> MAYBE.narrow(k1).equals(MAYBE.narrow(k2));
  }

  @BeforeEach
  void setUpApplicative() {
    applicative = MaybeMonad.INSTANCE;
    applicativeTyped = applicative;
    validateApplicativeFixtures();
  }

  @Nested
  @DisplayName("Complete Applicative Test Suite")
  class CompleteApplicativeTestSuite {

    @Test
    @DisplayName("Run complete Applicative test pattern")
    void runCompleteApplicativeTestPattern() {
      TypeClassTest.<MaybeKind.Witness>applicative(MaybeMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(MaybeApplicativeTest.class);

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
    @DisplayName("ap() applies function to value - both Just")
    void apAppliesFunctionToValue() {
      Kind<MaybeKind.Witness, Function<Integer, String>> funcKind =
          applicative.of(i -> "value:" + i);
      Kind<MaybeKind.Witness, Integer> valueKind = applicative.of(42);

      Kind<MaybeKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("value:42");
    }

    @Test
    @DisplayName("ap() returns Nothing if function is Nothing")
    void apReturnsNothingIfFunctionIsNothing() {
      Kind<MaybeKind.Witness, Function<Integer, String>> funcKind = MAYBE.widen(Maybe.nothing());
      Kind<MaybeKind.Witness, Integer> valueKind = applicative.of(42);

      Kind<MaybeKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("ap() returns Nothing if value is Nothing")
    void apReturnsNothingIfValueIsNothing() {
      Kind<MaybeKind.Witness, Function<Integer, String>> funcKind =
          applicative.of(i -> "value:" + i);
      Kind<MaybeKind.Witness, Integer> valueKind = MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map2() combines two Just values")
    void map2CombinesTwoJustValues() {
      Kind<MaybeKind.Witness, Integer> r1 = applicative.of(10);
      Kind<MaybeKind.Witness, String> r2 = applicative.of("test");

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<MaybeKind.Witness, String> result = applicative.map2(r1, r2, combiner);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test:10");
    }

    @Test
    @DisplayName("map2() returns Nothing if either value is Nothing")
    void map2ReturnsNothingIfEitherIsNothing() {
      Kind<MaybeKind.Witness, Integer> just = applicative.of(10);
      Kind<MaybeKind.Witness, Integer> nothing = MAYBE.widen(Maybe.nothing());

      BiFunction<Integer, Integer, String> combiner = (a, b) -> a + ":" + b;

      Maybe<String> result1 = MAYBE.narrow(applicative.map2(nothing, just, combiner));
      assertThat(result1.isNothing()).isTrue();

      Maybe<String> result2 = MAYBE.narrow(applicative.map2(just, nothing, combiner));
      assertThat(result2.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map3() combines three Just values")
    void map3CombinesThreeJustValues() {
      Kind<MaybeKind.Witness, Integer> r1 = applicative.of(1);
      Kind<MaybeKind.Witness, String> r2 = applicative.of("test");
      Kind<MaybeKind.Witness, Double> r3 = applicative.of(3.14);

      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);

      Kind<MaybeKind.Witness, String> result = applicative.map3(r1, r2, r3, combiner);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test:1:3.14");
    }

    @Test
    @DisplayName("map4() combines four Just values")
    void map4CombinesFourJustValues() {
      Kind<MaybeKind.Witness, Integer> r1 = applicative.of(1);
      Kind<MaybeKind.Witness, String> r2 = applicative.of("test");
      Kind<MaybeKind.Witness, Double> r3 = applicative.of(3.14);
      Kind<MaybeKind.Witness, Boolean> r4 = applicative.of(true);

      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);

      Kind<MaybeKind.Witness, String> result = applicative.map4(r1, r2, r3, r4, combiner);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test:1:3.14:true");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<MaybeKind.Witness>applicative(MaybeMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<MaybeKind.Witness>applicative(MaybeMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<MaybeKind.Witness>applicative(MaybeMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<MaybeKind.Witness>applicative(MaybeMonad.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .testLaws();
    }

    @Test
    @DisplayName("Test Functor composition law with both mappers")
    void testFunctorCompositionLaw() {
      Function<Integer, String> composed = validMapper.andThen(secondMapper);
      Kind<MaybeKind.Witness, String> leftSide = applicative.map(composed, validKind);

      Kind<MaybeKind.Witness, String> intermediate = applicative.map(validMapper, validKind);
      Kind<MaybeKind.Witness, String> rightSide = applicative.map(secondMapper, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide)).as("Functor Composition Law").isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("mapN operations short-circuit on first Nothing")
    void mapNShortCircuitsOnFirstNothing() {
      Kind<MaybeKind.Witness, Integer> n1 = MAYBE.widen(Maybe.nothing());
      Kind<MaybeKind.Witness, String> n2 = MAYBE.widen(Maybe.nothing());
      Kind<MaybeKind.Witness, Double> n3 = MAYBE.widen(Maybe.nothing());

      Function3<Integer, String, Double, String> combiner = (i, s, d) -> "result";

      Kind<MaybeKind.Witness, String> result = applicative.map3(n1, n2, n3, combiner);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Complex nested applicative operations")
    void complexNestedApplicativeOperations() {
      Kind<MaybeKind.Witness, Function<Integer, Function<String, String>>> nestedFunc =
          applicative.of(i -> s -> s + ":" + i);
      Kind<MaybeKind.Witness, Integer> intKind = applicative.of(42);
      Kind<MaybeKind.Witness, String> stringKind = applicative.of("test");

      Kind<MaybeKind.Witness, Function<String, String>> partialFunc =
          applicative.ap(nestedFunc, intKind);
      Kind<MaybeKind.Witness, String> result = applicative.ap(partialFunc, stringKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test:42");
    }

    @Test
    @DisplayName("of() with null creates Nothing")
    void ofWithNullCreatesNothing() {
      Kind<MaybeKind.Witness, String> result = applicative.of(null);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Efficient with many map2 operations")
    void efficientWithManyMap2Operations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<MaybeKind.Witness, Integer> start = applicative.of(1);

        Kind<MaybeKind.Witness, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          Kind<MaybeKind.Witness, Integer> incrementKind = applicative.of(i);
          result = applicative.map2(result, incrementKind, (a, b) -> a + b);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        Maybe<Integer> maybe = MAYBE.narrow(result);
        assertThat(maybe.isJust()).isTrue();
        assertThat(maybe.get()).isEqualTo(expectedSum);
      }
    }

    @Test
    @DisplayName("Nothing short-circuits efficiently")
    void nothingShortCircuitsEfficiently() {
      Kind<MaybeKind.Witness, Integer> nothing = MAYBE.widen(Maybe.nothing());

      AtomicInteger callCount = new AtomicInteger(0);
      BiFunction<Integer, Integer, Integer> trackingCombiner =
          (a, b) -> {
            callCount.incrementAndGet();
            return a + b;
          };

      for (int i = 0; i < 1000; i++) {
        applicative.map2(nothing, applicative.of(i), trackingCombiner);
      }

      assertThat(callCount).as("Combiner should not be called for Nothing").hasValue(0);
    }
  }
}
