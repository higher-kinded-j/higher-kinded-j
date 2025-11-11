// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalAssert.assertThatOptional;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalFunctor Complete Test Suite")
class OptionalFunctorTest extends OptionalTestBase {
  private OptionalFunctor functor;
  private Functor<OptionalKind.Witness> functorTyped;

  @BeforeEach
  void setUpFunctor() {
    functor = new OptionalFunctor();
    functorTyped = functor;
  }

  @Nested
  @DisplayName("Complete Functor Test Suite")
  class CompleteFunctorTestSuite {
    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<OptionalKind.Witness>functor(OptionalFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(OptionalFunctorTest.class);

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
    @DisplayName("map() on present Optional applies function")
    void mapOnPresentAppliesFunction() {
      Kind<OptionalKind.Witness, String> result = functor.map(validMapper, validKind);

      assertThatOptional(result).isPresent().contains(String.valueOf(DEFAULT_PRESENT_VALUE));
    }

    @Test
    @DisplayName("map() on empty Optional returns empty")
    void mapOnEmptyReturnsEmpty() {
      Kind<OptionalKind.Witness, Integer> emptyKind = emptyOptional();

      Kind<OptionalKind.Witness, String> result = functor.map(validMapper, emptyKind);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("map() with null-returning mapper returns empty")
    void mapWithNullReturningMapper() {
      Function<Integer, String> nullMapper = i -> null;

      Kind<OptionalKind.Witness, String> result = functor.map(nullMapper, validKind);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("map() chains multiple transformations")
    void mapChainsMultipleTransformations() {
      Kind<OptionalKind.Witness, String> result =
          functor.map(validMapper.andThen(String::toUpperCase), validKind);

      assertThatOptional(result).isPresent().contains(String.valueOf(DEFAULT_PRESENT_VALUE));
    }

    @Test
    @DisplayName("map() transforms to different type")
    void mapTransformsToDifferentType() {
      Function<Integer, Double> toDouble = i -> i * 1.5;

      Kind<OptionalKind.Witness, Double> result = functor.map(toDouble, validKind);

      assertThatOptional(result).isPresent().contains(DEFAULT_PRESENT_VALUE * 1.5);
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {
    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<OptionalKind.Witness>functor(OptionalFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<OptionalKind.Witness>functor(OptionalFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<OptionalKind.Witness>functor(OptionalFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<OptionalKind.Witness>functor(OptionalFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {
    @Test
    @DisplayName("map() preserves empty through chains")
    void mapPreservesEmptyThroughChains() {
      Kind<OptionalKind.Witness, Integer> empty = emptyOptional();

      Function<Integer, Integer> doubleFunc = i -> i * 2;
      Function<Integer, String> stringFunc = i -> "Value: " + i;

      Kind<OptionalKind.Witness, Integer> intermediate = functor.map(doubleFunc, empty);
      Kind<OptionalKind.Witness, String> result = functor.map(stringFunc, intermediate);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("map() with complex transformations")
    void mapWithComplexTransformations() {
      Function<Integer, String> complexMapper =
          i -> {
            if (i < 0) return "negative";
            if (i == 0) return "zero";
            return "positive:" + i;
          };

      Kind<OptionalKind.Witness, String> result = functor.map(complexMapper, validKind);
      assertThatOptional(result).isPresent().contains("positive:" + DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("map() with identity is idempotent")
    void mapWithIdentityIsIdempotent() {
      Function<Integer, Integer> identity = i -> i;

      Kind<OptionalKind.Witness, Integer> result = functor.map(identity, validKind);

      assertThat(narrowToOptional(result)).isEqualTo(narrowToOptional(validKind));
    }

    @Test
    @DisplayName("map() handles null value in Optional correctly")
    void mapHandlesNullValueCorrectly() {
      // Java's Optional.of() doesn't allow null, so this tests that our
      // implementation correctly handles the nullable type parameter
      Kind<OptionalKind.Witness, String> presentString = presentOf("test");
      Function<String, Integer> lengthFunc = String::length;

      Kind<OptionalKind.Witness, Integer> result = functor.map(lengthFunc, presentString);

      assertThatOptional(result).isPresent().contains(4);
    }

    @Test
    @DisplayName("map() with mapper that returns empty Optional equivalent")
    void mapWithMapperReturningEmptyEquivalent() {
      // When mapper returns null, it should create an empty Optional
      Function<Integer, String> conditionalMapper = i -> i > 100 ? "large" : null;

      Kind<OptionalKind.Witness, String> result = functor.map(conditionalMapper, validKind);

      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("map() preserves referential transparency")
    void mapPreservesReferentialTransparency() {
      Function<Integer, String> mapper = i -> "Value: " + i;

      Kind<OptionalKind.Witness, String> result1 = functor.map(mapper, validKind);
      Kind<OptionalKind.Witness, String> result2 = functor.map(mapper, validKind);

      assertThat(narrowToOptional(result1)).isEqualTo(narrowToOptional(result2));
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {
    @Test
    @DisplayName("Test performance characteristics")
    void testPerformanceCharacteristics() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<OptionalKind.Witness, Integer> start = validKind;

        long startTime = System.nanoTime();
        Kind<OptionalKind.Witness, Integer> result = start;
        for (int i = 0; i < 10000; i++) {
          result = functor.map(x -> x + 1, result);
        }
        long duration = System.nanoTime() - startTime;

        assertThat(duration).isLessThan(100_000_000L); // Less than 100ms
      }
    }

    @Test
    @DisplayName("Empty optimisation - map not called")
    void emptyOptimisationMapNotCalled() {
      Kind<OptionalKind.Witness, Integer> empty = emptyOptional();
      AtomicBoolean called = new AtomicBoolean(false);

      Function<Integer, String> tracker =
          i -> {
            called.set(true);
            return i.toString();
          };

      functor.map(tracker, empty);

      assertThat(called).as("Mapper should not be called for empty Optional").isFalse();
    }

    @Test
    @DisplayName("Map composition performance")
    void mapCompositionPerformance() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Function<Integer, Integer> increment = i -> i + 1;
        Function<Integer, Integer> composed = increment;
        for (int i = 0; i < 100; i++) {
          composed = composed.andThen(increment);
        }

        long startTime = System.nanoTime();
        Kind<OptionalKind.Witness, Integer> result = functor.map(composed, validKind);
        long duration = System.nanoTime() - startTime;

        assertThatOptional(result).isPresent();
        assertThat(duration).isLessThan(10_000_000L); // Less than 10ms
      }
    }
  }

  @Nested
  @DisplayName("Functor Laws Verification")
  class FunctorLawsVerification {
    @Test
    @DisplayName("Identity law: map(id) == id")
    void identityLaw() {
      Function<Integer, Integer> identity = x -> x;

      Kind<OptionalKind.Witness, Integer> mapped = functor.map(identity, validKind);

      assertThat(narrowToOptional(mapped)).isEqualTo(narrowToOptional(validKind));
    }

    @Test
    @DisplayName("Identity law holds for empty Optional")
    void identityLawHoldsForEmpty() {
      Function<Integer, Integer> identity = x -> x;
      Kind<OptionalKind.Witness, Integer> empty = emptyOptional();

      Kind<OptionalKind.Witness, Integer> mapped = functor.map(identity, empty);

      assertThat(narrowToOptional(mapped)).isEqualTo(narrowToOptional(empty));
    }

    @Test
    @DisplayName("Composition law: map(f . g) == map(f) . map(g)")
    void compositionLaw() {
      Function<Integer, String> f = i -> "num:" + i;
      Function<String, Integer> g = String::length;
      Function<Integer, Integer> composed = f.andThen(g);

      Kind<OptionalKind.Witness, Integer> mappedComposed = functor.map(composed, validKind);
      Kind<OptionalKind.Witness, Integer> mappedSequential =
          functor.map(g, functor.map(f, validKind));

      assertThat(narrowToOptional(mappedComposed)).isEqualTo(narrowToOptional(mappedSequential));
    }

    @Test
    @DisplayName("Composition law holds for empty Optional")
    void compositionLawHoldsForEmpty() {
      Function<Integer, String> f = i -> "num:" + i;
      Function<String, Integer> g = String::length;
      Function<Integer, Integer> composed = f.andThen(g);
      Kind<OptionalKind.Witness, Integer> empty = emptyOptional();

      Kind<OptionalKind.Witness, Integer> mappedComposed = functor.map(composed, empty);
      Kind<OptionalKind.Witness, Integer> mappedSequential = functor.map(g, functor.map(f, empty));

      assertThat(narrowToOptional(mappedComposed)).isEqualTo(narrowToOptional(mappedSequential));
    }
  }

  @Nested
  @DisplayName("Type Safety Tests")
  class TypeSafetyTests {
    @Test
    @DisplayName("map() maintains type safety with generics")
    void mapMaintainsTypeSafety() {
      Kind<OptionalKind.Witness, Integer> intOptional = presentOf(42);
      Function<Integer, String> toString = Object::toString;

      Kind<OptionalKind.Witness, String> stringOptional = functor.map(toString, intOptional);

      // Compile-time type safety ensures this works
      assertThatOptional(stringOptional).isPresent().contains("42");
    }

    @Test
    @DisplayName("map() works with complex nested types")
    void mapWorksWithNestedTypes() {
      Kind<OptionalKind.Witness, java.util.List<Integer>> listOptional =
          presentOf(java.util.List.of(1, 2, 3));
      Function<java.util.List<Integer>, Integer> sizeFunc = java.util.List::size;

      Kind<OptionalKind.Witness, Integer> result = functor.map(sizeFunc, listOptional);

      assertThatOptional(result).isPresent().contains(3);
    }
  }
}
