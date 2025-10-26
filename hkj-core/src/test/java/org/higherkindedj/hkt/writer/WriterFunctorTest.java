// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.writer.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for WriterFunctor using standardised patterns.
 *
 * <p>Tests Functor operations (map) for Writer with String logs.
 */
@DisplayName("WriterFunctor<W> Complete Test Suite")
class WriterFunctorTest extends WriterTestBase {

  private WriterFunctor<String> functor;

  @BeforeEach
  void setUpFunctor() {
    functor = new WriterFunctor<>();
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<WriterKind.Witness<String>>functor(WriterFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<WriterKind.Witness<String>>functor(WriterFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<WriterKind.Witness<String>>functor(WriterFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .selectTests()
          .onlyValidations()
          .test();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<WriterKind.Witness<String>>functor(WriterFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .selectTests()
          .onlyExceptions()
          .test();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<WriterKind.Witness<String>>functor(WriterFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }

  @Nested
  @DisplayName("Functor Operation Tests")
  class FunctorOperationTests {

    @Test
    @DisplayName("map() applies function and preserves log")
    void mapAppliesFunctionAndPreservesLog() {
      Kind<WriterKind.Witness<String>, Integer> writerKind = defaultKind();
      Function<Integer, String> mapper = i -> "Value:" + i;

      Kind<WriterKind.Witness<String>, String> result = functor.map(mapper, writerKind);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasLog(DEFAULT_LOG).hasValue("Value:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("map() works with empty log")
    void mapWorksWithEmptyLog() {
      Kind<WriterKind.Witness<String>, Integer> writerKind = WRITER.widen(valueWriter(42));
      Function<Integer, String> mapper = Object::toString;

      Kind<WriterKind.Witness<String>, String> result = functor.map(mapper, writerKind);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasEmptyLog().hasValue("42");
    }

    @Test
    @DisplayName("map() handles null value")
    void mapHandlesNullValue() {
      Kind<WriterKind.Witness<String>, Integer> writerKind =
          WRITER.widen(writerOf("NullValue;", null));
      Function<Integer, String> mapper = i -> "Got:" + i;

      Kind<WriterKind.Witness<String>, String> result = functor.map(mapper, writerKind);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasLog("NullValue;").hasValue("Got:null");
    }

    @Test
    @DisplayName("map() handles function returning null")
    void mapHandlesFunctionReturningNull() {
      Kind<WriterKind.Witness<String>, Integer> writerKind = defaultKind();
      Function<Integer, String> nullReturning = TestFunctions.nullReturningFunction();

      Kind<WriterKind.Witness<String>, String> result = functor.map(nullReturning, writerKind);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasLog(DEFAULT_LOG).hasNullValue();
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(writerOf("Start;", 5));

      Kind<WriterKind.Witness<String>, Integer> doubled = functor.map(i -> i * 2, start);
      Kind<WriterKind.Witness<String>, Integer> added = functor.map(i -> i + 10, doubled);
      Kind<WriterKind.Witness<String>, String> finalResult = functor.map(Object::toString, added);

      Writer<String, String> writer = narrowToWriter(finalResult);
      assertThatWriter(writer).hasLog("Start;").hasValue("20");
    }

    @Test
    @DisplayName("map() with complex transformations")
    void mapWithComplexTransformations() {
      Kind<WriterKind.Witness<String>, Integer> writerKind =
          WRITER.widen(writerOf("Complex;", 100));

      Function<Integer, Double> toDouble = Integer::doubleValue;
      Function<Double, String> format = d -> String.format("%.2f", d / 3);

      Kind<WriterKind.Witness<String>, Double> intermediate = functor.map(toDouble, writerKind);
      Kind<WriterKind.Witness<String>, String> result = functor.map(format, intermediate);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer)
          .hasLog("Complex;")
          .satisfiesValue(v -> assertThat(v).startsWith("33.33"));
    }
  }

  @Nested
  @DisplayName("Functor Law Tests")
  class FunctorLawTests {

    @Test
    @DisplayName("Identity Law: map(id, fa) == fa")
    void identityLaw() {
      Kind<WriterKind.Witness<String>, Integer> writerKind =
          WRITER.widen(writerOf("Identity;", 42));

      Kind<WriterKind.Witness<String>, Integer> result =
          functor.map(Function.identity(), writerKind);

      assertThat(narrowToWriter(result)).isEqualTo(narrowToWriter(writerKind));
    }

    @Test
    @DisplayName("Identity Law holds for empty log")
    void identityLawHoldsForEmptyLog() {
      Kind<WriterKind.Witness<String>, Integer> writerKind = WRITER.widen(valueWriter(42));

      Kind<WriterKind.Witness<String>, Integer> result =
          functor.map(Function.identity(), writerKind);

      assertThat(narrowToWriter(result)).isEqualTo(narrowToWriter(writerKind));
    }

    @Test
    @DisplayName("Composition Law: map(g ∘ f, fa) == map(g, map(f, fa))")
    void compositionLaw() {
      Kind<WriterKind.Witness<String>, Integer> writerKind = WRITER.widen(writerOf("Compose;", 10));

      Function<Integer, String> f = i -> "v" + i;
      Function<String, String> g = s -> s + "!";
      Function<Integer, String> gComposeF = g.compose(f);

      // Left side: map(g ∘ f, fa)
      Kind<WriterKind.Witness<String>, String> leftSide = functor.map(gComposeF, writerKind);

      // Right side: map(g, map(f, fa))
      Kind<WriterKind.Witness<String>, String> intermediate = functor.map(f, writerKind);
      Kind<WriterKind.Witness<String>, String> rightSide = functor.map(g, intermediate);

      assertThat(narrowToWriter(leftSide)).isEqualTo(narrowToWriter(rightSide));
    }

    @Test
    @DisplayName("Composition Law with complex types")
    void compositionLawWithComplexTypes() {
      Kind<WriterKind.Witness<String>, Integer> writerKind =
          WRITER.widen(writerOf("ComplexCompose;", 100));

      Function<Integer, Double> f = i -> i / 10.0;
      Function<Double, String> g = d -> String.format("Result:%.1f", d);
      Function<Integer, String> gComposeF = g.compose(f);

      Kind<WriterKind.Witness<String>, String> leftSide = functor.map(gComposeF, writerKind);

      Kind<WriterKind.Witness<String>, Double> intermediate = functor.map(f, writerKind);
      Kind<WriterKind.Witness<String>, String> rightSide = functor.map(g, intermediate);

      assertThat(narrowToWriter(leftSide)).isEqualTo(narrowToWriter(rightSide));
    }

    @Test
    @DisplayName("Functor preserves structure")
    void functorPreservesStructure() {
      Kind<WriterKind.Witness<String>, Integer> original = WRITER.widen(writerOf("Structure;", 42));

      // Apply multiple transformations
      Kind<WriterKind.Witness<String>, Integer> transformed = original;
      for (int i = 0; i < 5; i++) {
        transformed = functor.map(x -> x + 1, transformed);
      }

      // Log should be preserved
      Writer<String, Integer> writer = narrowToWriter(transformed);
      assertThatWriter(writer).hasLog("Structure;").hasValue(47);
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("map() propagates exceptions from mapper function")
    void mapPropagatesExceptionsFromMapperFunction() {
      RuntimeException testException = new RuntimeException("Test exception: functor map");
      Kind<WriterKind.Witness<String>, Integer> writerKind =
          WRITER.widen(writerOf("Exception;", 42));
      Function<Integer, String> throwingMapper = TestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> functor.map(throwingMapper, writerKind)).isSameAs(testException);
    }

    @Test
    @DisplayName("map() doesn't catch exceptions during transformation")
    void mapDoesNotCatchExceptionsDuringTransformation() {
      Kind<WriterKind.Witness<String>, Integer> writerKind =
          WRITER.widen(writerOf("Division;", 10));
      Function<Integer, Integer> divideByZero = i -> i / 0;

      assertThatThrownBy(() -> functor.map(divideByZero, writerKind))
          .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("Exception in chained maps is propagated")
    void exceptionInChainedMapsIsPropagated() {
      RuntimeException testException = new RuntimeException("Chain exception");
      Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(writerOf("Chain;", 5));

      Function<Integer, Integer> double1 = i -> i * 2;
      Function<Integer, Integer> throwing = TestFunctions.throwingFunction(testException);
      Function<Integer, String> toString = Object::toString;

      // Chain: double -> throw -> toString
      // Should fail at throwing step
      assertThatThrownBy(
              () -> {
                Kind<WriterKind.Witness<String>, Integer> step1 = functor.map(double1, start);
                Kind<WriterKind.Witness<String>, Integer> step2 = functor.map(throwing, step1);
                functor.map(toString, step2);
              })
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesTests {

    @Test
    @DisplayName("map() with very long log")
    void mapWithVeryLongLog() {
      String longLog = "x".repeat(10000);
      Kind<WriterKind.Witness<String>, Integer> writerKind = WRITER.widen(writerOf(longLog, 42));

      Kind<WriterKind.Witness<String>, String> result = functor.map(Object::toString, writerKind);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).satisfiesLog(log -> assertThat(log).hasSize(10000)).hasValue("42");
    }

    @Test
    @DisplayName("map() maintains referential transparency")
    void mapMaintainsReferentialTransparency() {
      Kind<WriterKind.Witness<String>, Integer> writerKind = WRITER.widen(writerOf("Ref;", 42));
      Function<Integer, String> mapper = Object::toString;

      Kind<WriterKind.Witness<String>, String> result1 = functor.map(mapper, writerKind);
      Kind<WriterKind.Witness<String>, String> result2 = functor.map(mapper, writerKind);

      assertThat(narrowToWriter(result1)).isEqualTo(narrowToWriter(result2));
      assertThatWriter(narrowToWriter(result1)).isPure();
    }

    @Test
    @DisplayName("map() with maximum integer value")
    void mapWithMaximumIntegerValue() {
      Kind<WriterKind.Witness<String>, Integer> writerKind =
          WRITER.widen(writerOf("Max;", Integer.MAX_VALUE));

      Kind<WriterKind.Witness<String>, Long> result =
          functor.map(i -> i.longValue() + 1, writerKind);
      Writer<String, Long> writer = narrowToWriter(result);

      assertThatWriter(writer).hasValue((long) Integer.MAX_VALUE + 1);
    }

    @Test
    @DisplayName("map() is stack-safe for deep chains")
    void mapIsStackSafeForDeepChains() {
      Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(writerOf("Deep;", 0));

      Kind<WriterKind.Witness<String>, Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = functor.map(x -> x + 1, result);
      }

      Writer<String, Integer> writer = narrowToWriter(result);
      assertThatWriter(writer).hasLog("Deep;").hasValue(10000);
    }
  }

  @Nested
  @DisplayName("Type Safety and Variance Tests")
  class TypeSafetyTests {

    @Test
    @DisplayName("map() maintains type safety across transformations")
    void mapMaintainsTypeSafetyAcrossTransformations() {
      Kind<WriterKind.Witness<String>, Number> numberKind =
          WRITER.widen(writerOf("Number;", (Number) 42));

      Kind<WriterKind.Witness<String>, Integer> intKind = functor.map(Number::intValue, numberKind);
      Kind<WriterKind.Witness<String>, String> stringKind = functor.map(Object::toString, intKind);

      Writer<String, String> writer = narrowToWriter(stringKind);
      assertThatWriter(writer).hasValue("42");
    }

    @Test
    @DisplayName("map() works with complex generic types")
    void mapWorksWithComplexGenericTypes() {
      Kind<WriterKind.Witness<String>, java.util.List<Integer>> listKind =
          WRITER.widen(writerOf("List;", java.util.List.of(1, 2, 3)));

      Kind<WriterKind.Witness<String>, Integer> sumKind =
          functor.map(list -> list.stream().mapToInt(Integer::intValue).sum(), listKind);

      Writer<String, Integer> writer = narrowToWriter(sumKind);
      assertThatWriter(writer).hasValue(6);
    }

    @Test
    @DisplayName("map() handles polymorphic functions")
    void mapHandlesPolymorphicFunctions() {
      Kind<WriterKind.Witness<String>, Integer> intKind = WRITER.widen(writerOf("Poly;", 42));

      // Polymorphic function that works for any Object
      Function<Object, String> polymorphic = obj -> "Value: " + obj.toString();

      Kind<WriterKind.Witness<String>, String> result = functor.map(polymorphic, intKind);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasValue("Value: 42");
    }
  }

  @Nested
  @DisplayName("Performance Characteristics")
  class PerformanceTests {

    @Test
    @DisplayName("map() has predictable performance")
    void mapHasPredictablePerformance() {
      Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(writerOf("Perf;", 1));

      long startTime = System.nanoTime();
      Kind<WriterKind.Witness<String>, Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = functor.map(x -> x + 1, result);
      }
      narrowToWriter(result).value();
      long duration = System.nanoTime() - startTime;

      // Should complete in reasonable time (less than 100ms for 10k ops)
      assertThat(duration).isLessThan(100_000_000L);
    }

    @Test
    @DisplayName("map() doesn't accumulate intermediate results")
    void mapDoesNotAccumulateIntermediateResults() {
      Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(writerOf("Start;", 1));

      // Multiple transformations
      Kind<WriterKind.Witness<String>, Integer> result = start;
      for (int i = 0; i < 100; i++) {
        result = functor.map(x -> x + 1, result);
      }

      Writer<String, Integer> writer = narrowToWriter(result);
      // Log should be unchanged
      assertThatWriter(writer).hasLog("Start;").hasValue(101);
    }
  }
}
