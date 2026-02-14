// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.writer.WriterAssert.assertThatWriter;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.util.validation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Writer core functionality test using standardised patterns.
 *
 * <p>This test focuses on the core Writer functionality whilst using the standardised validation
 * framework for consistent error handling.
 */
@DisplayName("Writer<W, A> Core Functionality - Standardised Test Suite")
class WriterTest extends WriterTestBase {

  // Type class testing fixtures
  private WriterMonad<String> monad;
  private WriterFunctor<String> functor;

  @BeforeEach
  void setUpWriter() {
    monad = new WriterMonad<>(STRING_MONOID);
    functor = new WriterFunctor<>();
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<WriterKind.Witness<String>>monad(WriterMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(WriterFunctor.class)
          .withApFrom(WriterApplicative.class)
          .withFlatMapFrom(WriterMonad.class)
          .testAll();
    }

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
  @DisplayName("Core Type Testing with CoreTypeTest API")
  class CoreTypeTestingSuite {

    @Test
    @DisplayName("Test all Writer core operations")
    void testAllWriterCoreOperations() {
      CoreTypeTest.<String, Integer>writer(Writer.class)
          .withWriter(defaultWriter())
          .withMonoid(STRING_MONOID)
          .withMappers(TestFunctions.INT_TO_STRING)
          .testAll();
    }

    @Test
    @DisplayName("Test Writer with validation configuration")
    void testWriterWithValidationConfiguration() {
      CoreTypeTest.<String, Integer>writer(Writer.class)
          .withWriter(defaultWriter())
          .withMonoid(STRING_MONOID)
          .withMappers(TestFunctions.INT_TO_STRING)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(WriterFunctor.class)
          .withFlatMapFrom(WriterMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Test Writer selective operations")
    void testWriterSelectiveOperations() {
      CoreTypeTest.<String, Integer>writer(Writer.class)
          .withWriter(defaultWriter())
          .withMonoid(STRING_MONOID)
          .withMappers(TestFunctions.INT_TO_STRING)
          .onlyFactoryMethods()
          .testAll();
    }
  }

  @Nested
  @DisplayName("Factory Methods - Complete Coverage")
  class FactoryMethods {

    @Test
    @DisplayName("value() creates correct instances with empty log")
    void valueCreatesCorrectInstances() {
      // Non-null values
      Writer<String, Integer> w1 = valueWriter(42);
      assertThatWriter(w1).hasEmptyLog().hasValue(42);

      // Null values
      Writer<String, Integer> w2 = valueWriter(null);
      assertThatWriter(w2).hasEmptyLog().hasNullValue();

      // Complex types
      Writer<String, String> w3 = valueWriter("test");
      assertThatWriter(w3).hasEmptyLog().hasValue("test");
    }

    @Test
    @DisplayName("tell() creates correct instances with Unit value")
    void tellCreatesCorrectInstances() {
      // Non-empty log
      Writer<String, Unit> w1 = tellWriter("Message");
      assertThatWriter(w1).hasLog("Message").hasValue(Unit.INSTANCE);

      // Empty string log
      Writer<String, Unit> w2 = tellWriter("");
      assertThatWriter(w2).hasEmptyLog().hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Constructor creates correct instances")
    void constructorCreatesCorrectInstances() {
      // Standard case
      Writer<String, Integer> w1 = writerOf("Log", 10);
      assertThatWriter(w1).hasLog("Log").hasValue(10);

      // Null value
      Writer<String, Integer> w2 = writerOf("Log", null);
      assertThatWriter(w2).hasLog("Log").hasNullValue();
    }

    @Test
    @DisplayName("Factory methods type inference works correctly")
    void factoryMethodsTypeInference() {
      // Test that type inference works without explicit type parameters
      var valueWriterLocal = valueWriter(42);
      var tellWriterLocal = tellWriter("log");

      // Should be able to assign to properly typed variables
      Writer<String, Integer> valueAssignment = valueWriterLocal;
      Writer<String, Unit> tellAssignment = tellWriterLocal;

      assertThatWriter(valueAssignment).hasValue(42);
      assertThatWriter(tellAssignment).hasLog("log");
    }
  }

  @Nested
  @DisplayName("Accessor Methods - Comprehensive Coverage")
  class AccessorMethods {

    @Test
    @DisplayName("log() returns correct log for all Writer types")
    void logReturnsCorrectValue() {
      // Standard case
      assertThatWriter(defaultWriter()).hasLog(DEFAULT_LOG);

      // Empty log
      assertThatWriter(valueWriter(DEFAULT_VALUE)).hasEmptyLog();

      // Tell writer
      assertThatWriter(tellWriter("Tell;")).hasLog("Tell;");

      // Null value writer still has log
      assertThatWriter(writerOf("NullLog;", null)).hasLog("NullLog;");
    }

    @Test
    @DisplayName("value() returns correct value for all Writer types")
    void valueReturnsCorrectValue() {
      // Standard case
      assertThatWriter(defaultWriter()).hasValue(DEFAULT_VALUE);

      // Empty log writer
      assertThatWriter(valueWriter(42)).hasValue(42);

      // Tell writer returns Unit
      assertThatWriter(tellWriter("Tell;")).hasValue(Unit.INSTANCE);

      // Null value
      assertThatWriter(writerOf("NullLog;", null)).hasNullValue();
    }

    @Test
    @DisplayName("run() returns value for all Writer types")
    void runReturnsValue() {
      assertThat(defaultWriter().run()).isEqualTo(DEFAULT_VALUE);
      assertThat(valueWriter(42).run()).isEqualTo(42);
      assertThat(tellWriter("Tell;").run()).isEqualTo(Unit.INSTANCE);
      assertThat(writerOf("NullLog;", null).run()).isNull();
    }

    @Test
    @DisplayName("exec() returns log for all Writer types")
    void execReturnsLog() {
      assertThat(defaultWriter().exec()).isEqualTo(DEFAULT_LOG);
      assertThat(valueWriter(42).exec()).isEqualTo(STRING_MONOID.empty());
      assertThat(tellWriter("Tell;").exec()).isEqualTo("Tell;");
      assertThat(writerOf("NullLog;", null).exec()).isEqualTo("NullLog;");
    }
  }

  @Nested
  @DisplayName("map() Method - Comprehensive Testing")
  class MapMethodTests {

    @Test
    @DisplayName("map() applies function to value and preserves log")
    void mapAppliesFunctionToValue() {
      // Standard transformation
      Writer<String, String> result = defaultWriter().map(TestFunctions.INT_TO_STRING);
      assertThatWriter(result).hasLog(DEFAULT_LOG).hasValue(String.valueOf(DEFAULT_VALUE));

      // Complex transformation
      Writer<String, Integer> doubled = defaultWriter().map(i -> i * 2);
      assertThatWriter(doubled).hasLog(DEFAULT_LOG).hasValue(DEFAULT_VALUE * 2);

      // Transformation to different type
      Writer<String, Boolean> isPositive = defaultWriter().map(i -> i > 0);
      assertThatWriter(isPositive).hasLog(DEFAULT_LOG).hasValue(true);
    }

    @Test
    @DisplayName("map() handles null values correctly")
    void mapHandlesNullValues() {
      // Mapping null value
      Writer<String, String> result = writerOf("NullLog;", null).map(i -> String.valueOf(i));
      assertThatWriter(result).hasLog("NullLog;").hasValue("null");

      // Mapping to null
      Writer<String, Integer> toNull = defaultWriter().map(i -> null);
      assertThatWriter(toNull).hasLog(DEFAULT_LOG).hasNullValue();
    }

    @Test
    @DisplayName("map() validates null mapper using standardised validation")
    void mapValidatesNullMapper() {
      ValidationTestBuilder.create()
          .assertMapperNull(() -> defaultWriter().map(null), "f", Writer.class, Operation.MAP)
          .execute();
    }

    @Test
    @DisplayName("map() handles exception propagation and chaining")
    void mapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: map test");
      Function<Integer, String> throwingMapper = TestFunctions.throwingFunction(testException);

      // Exception should propagate
      assertThatThrownBy(() -> defaultWriter().map(throwingMapper)).isSameAs(testException);

      // Test chaining
      Writer<String, Integer> start = valueWriter(10);
      Writer<String, String> chainResult =
          start.map(i -> i * 2).map(i -> "Value: " + i).map(String::toUpperCase);

      assertThatWriter(chainResult).hasEmptyLog().hasValue("VALUE: 20");
    }

    @Test
    @DisplayName("map() preserves log through multiple transformations")
    void mapPreservesLogThroughChaining() {
      Writer<String, String> result =
          defaultWriter()
              .map(i -> i + 5) // 47
              .map(i -> i * 2) // 94
              .map(Object::toString); // "94"

      assertThatWriter(result)
          .hasLog(DEFAULT_LOG)
          .hasValue(String.valueOf((DEFAULT_VALUE + 5) * 2));
    }
  }

  @Nested
  @DisplayName("flatMap() Method - Comprehensive Monadic Testing")
  class FlatMapMethodTests {

    @Test
    @DisplayName("flatMap() applies function and combines logs")
    void flatMapAppliesFunctionAndCombinesLogs() {
      Function<Integer, Writer<String, String>> mapper =
          i -> writerOf("Mapped(" + i + ");", "Value: " + i);

      Writer<String, String> result = defaultWriter().flatMap(STRING_MONOID, mapper);

      assertThatWriter(result)
          .hasLog(DEFAULT_LOG + "Mapped(" + DEFAULT_VALUE + ");")
          .hasValue("Value: " + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("flatMap() works with tell for logging")
    void flatMapWorksWithTell() {
      Writer<String, Integer> start = valueWriter(10);

      Writer<String, String> result =
          start
              .flatMap(STRING_MONOID, i -> tellWriter("Logged " + i + ";"))
              .flatMap(STRING_MONOID, v -> writerOf("Final;", "Done"));

      assertThatWriter(result).hasLog("Logged 10;Final;").hasValue("Done");
    }

    @Test
    @DisplayName("flatMap() supports complex chaining patterns")
    void flatMapSupportsComplexChaining() {
      Writer<String, Integer> start = valueWriter(5);

      Writer<String, String> result =
          start
              .flatMap(STRING_MONOID, i -> writerOf("Double;", i * 2))
              .flatMap(STRING_MONOID, i -> writerOf("Add;", i + 10))
              .flatMap(STRING_MONOID, i -> writerOf("Result;", "Final: " + i));

      assertThatWriter(result).hasLog("Double;Add;Result;").hasValue("Final: 20");
    }

    @Test
    @DisplayName("flatMap() validates parameters using standardised validation")
    void flatMapValidatesParameters() {
      Function<Integer, Writer<String, String>> validMapper =
          i -> writerOf(STRING_MONOID.empty(), String.valueOf(i));

      ValidationTestBuilder.create()
          .assertMonoidNull(
              () -> defaultWriter().flatMap(null, validMapper),
              "monoidW",
              Writer.class,
              Operation.FLAT_MAP)
          .assertFlatMapperNull(
              () -> defaultWriter().flatMap(STRING_MONOID, null),
              "f",
              Writer.class,
              Operation.FLAT_MAP)
          .execute();
    }

    @Test
    @DisplayName("flatMap() validates non-null results")
    void flatMapValidatesNonNullResults() {
      Function<Integer, Writer<String, String>> nullReturningMapper = i -> null;

      assertThatThrownBy(() -> defaultWriter().flatMap(STRING_MONOID, nullReturningMapper))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Function f in Writer.flatMap returned null when Writer expected, which is not"
                  + " allowed");
    }

    @Test
    @DisplayName("flatMap() handles exception propagation")
    void flatMapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap test");
      Function<Integer, Writer<String, String>> throwingMapper =
          TestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> defaultWriter().flatMap(STRING_MONOID, throwingMapper))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("flatMap() handles null values correctly")
    void flatMapHandlesNullValues() {
      Function<Integer, Writer<String, String>> mapper =
          i -> writerOf("Null;", i == null ? "was null" : "was " + i);

      Writer<String, Integer> nullWriter = writerOf("NullLog;", null);
      Writer<String, String> result = nullWriter.flatMap(STRING_MONOID, mapper);

      assertThatWriter(result).hasLog("NullLog;Null;").hasValue("was null");
    }
  }

  @Nested
  @DisplayName("toString() and Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() provides meaningful representations")
    void toStringProvidesMeaningfulRepresentations() {
      assertThat(defaultWriter().toString())
          .contains("Writer")
          .contains(DEFAULT_LOG)
          .contains(String.valueOf(DEFAULT_VALUE));

      assertThat(tellWriter("Tell;").toString())
          .contains("Writer")
          .contains("Tell;")
          .contains("()");

      Writer<String, Integer> nullWriter = writerOf("NullLog;", null);
      assertThat(nullWriter.toString()).contains("Writer").contains("NullLog;").contains("null");
    }

    @Test
    @DisplayName("equals() and hashCode() work correctly")
    void equalsAndHashCodeWorkCorrectly() {
      Writer<String, Integer> writer1 = defaultWriter();

      // Same instances
      assertThat(writer1).isEqualTo(writer1);

      // Equal instances
      Writer<String, Integer> another = writerOf(DEFAULT_LOG, DEFAULT_VALUE);
      assertThat(writer1).isEqualTo(another);
      assertThat(writer1.hashCode()).isEqualTo(another.hashCode());

      // Different log
      Writer<String, Integer> differentLog = writerOf("Other;", DEFAULT_VALUE);
      assertThat(writer1).isNotEqualTo(differentLog);

      // Different value
      Writer<String, Integer> differentValue = writerOf(DEFAULT_LOG, 20);
      assertThat(writer1).isNotEqualTo(differentValue);

      // Null handling
      Writer<String, Integer> nullValue1 = writerOf("Log;", null);
      Writer<String, Integer> nullValue2 = writerOf("Log;", null);
      assertThat(nullValue1).isEqualTo(nullValue2);
      assertThat(nullValue1.hashCode()).isEqualTo(nullValue2.hashCode());
    }
  }

  @Nested
  @DisplayName("Advanced Usage Patterns")
  class AdvancedUsagePatterns {

    @Test
    @DisplayName("Writer as functor maintains structure")
    void writerAsFunctorMaintainsStructure() {
      Writer<String, Integer> start = writerOf("Init;", 5);

      Writer<String, Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

      assertThatWriter(result)
          .hasLog("Init;")
          .satisfiesValue(value -> assertThat(value).isCloseTo(Math.sqrt(10.5), within(0.001)));
    }

    @Test
    @DisplayName("Writer for computation logging")
    void writerForComputationLogging() {
      Writer<String, Integer> computation =
          valueWriter(10)
              .flatMap(STRING_MONOID, i -> writerOf("Start: " + i + "; ", i))
              .flatMap(STRING_MONOID, i -> writerOf("Double: " + (i * 2) + "; ", i * 2))
              .flatMap(STRING_MONOID, i -> writerOf("Add 5: " + (i + 5) + "; ", i + 5));

      assertThatWriter(computation).hasLog("Start: 10; Double: 20; Add 5: 25; ").hasValue(25);
    }

    @Test
    @DisplayName("Writer with accumulating logs")
    void writerWithAccumulatingLogs() {
      AtomicInteger counter = new AtomicInteger(0);

      Writer<String, Integer> tracked =
          valueWriter(1)
              .flatMap(
                  STRING_MONOID,
                  i -> {
                    int step = counter.incrementAndGet();
                    return writerOf("Step " + step + ": " + i + "; ", i * 2);
                  })
              .flatMap(
                  STRING_MONOID,
                  i -> {
                    int step = counter.incrementAndGet();
                    return writerOf("Step " + step + ": " + i + "; ", i + 10);
                  })
              .flatMap(
                  STRING_MONOID,
                  i -> {
                    int step = counter.incrementAndGet();
                    return writerOf("Step " + step + ": " + i + "; ", i * 3);
                  });

      assertThatWriter(tracked).hasLog("Step 1: 1; Step 2: 2; Step 3: 12; ").hasValue(36);
    }

    @Test
    @DisplayName("Writer pattern matching with records")
    void writerPatternMatchingWithRecords() {
      Function<Writer<String, Integer>, String> processWriter =
          writer ->
              switch (writer) {
                case Writer(String log, Integer value) when value != null && value > 0 ->
                    "Positive: " + value + " (logged: " + log + ")";
                case Writer(String log, Integer value) when value != null && value < 0 ->
                    "Negative: " + value + " (logged: " + log + ")";
                case Writer(String log, Integer value) when value != null ->
                    "Zero (logged: " + log + ")";
                case Writer(String log, _) -> "Null value (logged: " + log + ")";
              };

      assertThat(processWriter.apply(defaultWriter()))
          .isEqualTo("Positive: " + DEFAULT_VALUE + " (logged: " + DEFAULT_LOG + ")");
      assertThat(processWriter.apply(writerOf("Neg;", -5)))
          .isEqualTo("Negative: -5 (logged: Neg;)");
      assertThat(processWriter.apply(writerOf("NullLog;", null)))
          .isEqualTo("Null value (logged: NullLog;)");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("Writer operations complete in reasonable time")
    void writerOperationsCompleteInReasonableTime() {
      Writer<String, Integer> test = valueWriter(42);

      // Verify operations complete without hanging (generous timeout)
      // Use JMH benchmarks in hkj-benchmarks module for precise performance measurement
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () -> {
            for (int i = 0; i < 100_000; i++) {
              test.map(x -> x + 1).flatMap(STRING_MONOID, x -> valueWriter(x * 2)).value();
            }
          },
          "Writer operations should complete within reasonable time");
    }

    @Test
    @DisplayName("Log concatenation is efficient")
    void logConcatenationIsEfficient() {
      Writer<String, Integer> start = valueWriter(1);

      Writer<String, Integer> result = start;
      for (int i = 0; i < 100; i++) {
        final int iteration = i;
        result = result.flatMap(STRING_MONOID, x -> writerOf("Step " + iteration + ";", x + 1));
      }

      assertThatWriter(result)
          .hasValue(101)
          .satisfiesLog(
              log -> {
                assertThat(log).contains("Step 0;");
                assertThat(log).contains("Step 99;");
              });
    }
  }

  @Nested
  @DisplayName("Type Safety and Variance")
  class TypeSafetyAndVarianceTests {

    @Test
    @DisplayName("Writer maintains type safety across operations")
    void writerMaintainsTypeSafety() {
      Writer<String, Number> numberWriter = valueWriter(42);
      Writer<String, Integer> intWriter =
          numberWriter.flatMap(STRING_MONOID, n -> valueWriter(n.intValue()));

      assertThatWriter(intWriter).hasValue(42);
    }

    @Test
    @DisplayName("Writer works with complex generic types")
    void writerWorksWithComplexGenericTypes() {
      Writer<String, List<Integer>> listWriter = valueWriter(List.of(1, 2, 3));

      Writer<String, Integer> sumWriter =
          listWriter.map(list -> list.stream().mapToInt(Integer::intValue).sum());

      assertThatWriter(sumWriter).hasValue(6);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesAndBoundaryConditions {

    @Test
    @DisplayName("Writer handles extreme values correctly")
    void writerHandlesExtremeValuesCorrectly() {
      // Very large log
      String largeLog = "x".repeat(10000);
      Writer<String, Integer> largeLogWriter = writerOf(largeLog, 42);
      assertThatWriter(largeLogWriter).satisfiesLog(log -> assertThat(log).hasSize(10000));

      // Maximum integer
      Writer<String, Integer> maxIntWriter = valueWriter(Integer.MAX_VALUE);
      Writer<String, Long> promoted = maxIntWriter.map(i -> i.longValue() + 1);
      assertThatWriter(promoted).hasValue((long) Integer.MAX_VALUE + 1);
    }

    @Test
    @DisplayName("Writer operations are stack-safe for deep chains")
    void writerOperationsAreStackSafe() {
      Writer<String, Integer> start = valueWriter(0);

      Writer<String, Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = result.map(x -> x + 1);
      }

      assertThatWriter(result).hasValue(10000);
    }

    @Test
    @DisplayName("Writer maintains referential transparency")
    void writerMaintainsReferentialTransparency() {
      Writer<String, Integer> writer = valueWriter(42);
      Function<Integer, String> transform = i -> "value:" + i;

      Writer<String, String> result1 = writer.map(transform);
      Writer<String, String> result2 = writer.map(transform);

      assertThat(result1).isEqualTo(result2);
      assertThatWriter(result1).isEqualTo(result2);
      assertThatWriter(result1).isPure();
    }
  }
}
