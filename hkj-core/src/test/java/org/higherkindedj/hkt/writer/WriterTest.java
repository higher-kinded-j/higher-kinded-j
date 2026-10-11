// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.WriterAssert.assertThatWriter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.assertions.ValidationTestBuilder;
import org.higherkindedj.hkt.test.fixtures.TestFunctions;
import org.higherkindedj.hkt.util.validation.Operation;
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

  // The Functor/Monad type-class contracts and laws live in WriterFunctorTest / WriterMonadTest;
  // this suite focuses on the Writer record's own factory, accessor, map and flatMap methods.

  @Nested
  @DisplayName("Factory Methods - Complete Coverage")
  class FactoryMethods {

    @Test
    @DisplayName("value() creates correct instances with empty log")
    void valueCreatesCorrectInstances() {
      // Non-null values
      Writer<String, Integer> w1 = valueWriter(42);
      assertThatWriter(w1).hasEmptyLog().hasValue(42);

      // Complex types
      Writer<String, String> w3 = valueWriter("test");
      assertThatWriter(w3).hasEmptyLog().hasValue("test");
    }

    @Test
    @DisplayName("value() refuses a null value at the call")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void valueRefusesNullValue() {
      assertThatNullPointerException()
          .isThrownBy(() -> Writer.value(STRING_MONOID, null))
          .withMessage("Writer.value value cannot be null");
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
    @DisplayName("of() creates correct instances with a custom log")
    void ofCreatesCorrectInstances() {
      // Non-null value with a custom log
      Writer<String, Integer> w1 = Writer.of("Log", 10);
      assertThatWriter(w1).hasLog("Log").hasValue(10);

      // Complex value type
      Writer<String, String> w3 = Writer.of("Init;", "test");
      assertThatWriter(w3).hasLog("Init;").hasValue("test");
    }

    @Test
    @DisplayName("of() and the constructor refuse a null value, and name a null log")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void ofAndConstructorRefuseNulls() {
      assertThatNullPointerException()
          .isThrownBy(() -> Writer.of("Log", null))
          .withMessage("Writer.of value cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> new Writer<>("Log", null))
          .withMessage("Writer.construction value cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> new Writer<>(null, 10))
          .withMessage("Writer.construction log cannot be null");
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
    }

    @Test
    @DisplayName("run() returns value for all Writer types")
    void runReturnsValue() {
      assertThat(defaultWriter().run()).isEqualTo(DEFAULT_VALUE);
      assertThat(valueWriter(42).run()).isEqualTo(42);
      assertThat(tellWriter("Tell;").run()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("exec() returns log for all Writer types")
    void execReturnsLog() {
      assertThat(defaultWriter().exec()).isEqualTo(DEFAULT_LOG);
      assertThat(valueWriter(42).exec()).isEqualTo(STRING_MONOID.empty());
      assertThat(tellWriter("Tell;").exec()).isEqualTo("Tell;");
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
    @DisplayName("map() refuses a null result from its function")
    @SuppressWarnings("DataFlowIssue") // null is returned deliberately to verify rejection
    void mapRefusesNullResult() {
      assertThatNullPointerException()
          .isThrownBy(() -> defaultWriter().map(_ -> null))
          .withMessage("f must not return null");
    }

    @Test
    @DisplayName("bimap() refuses a null result from its value mapper")
    @SuppressWarnings("DataFlowIssue") // null is returned deliberately to verify rejection
    void bimapRefusesNullValueResult() {
      assertThatNullPointerException()
          .isThrownBy(() -> defaultWriter().bimap(log -> log, _ -> null))
          .withMessage("valueMapper must not return null");
    }

    @Test
    @DisplayName("map() validates null mapper using standardised validation")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mapValidatesNullMapper() {
      ValidationTestBuilder.create()
          .assertMapperNull(() -> defaultWriter().map(null), "f", Operation.MAP)
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
              .flatMap(STRING_MONOID, _ -> writerOf("Final;", "Done"));

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
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void flatMapValidatesParameters() {
      Function<Integer, Writer<String, String>> validMapper =
          i -> writerOf(STRING_MONOID.empty(), String.valueOf(i));

      ValidationTestBuilder.create()
          .assertMonoidNull(
              () -> defaultWriter().flatMap(null, validMapper), "monoidW", Operation.FLAT_MAP)
          .assertFlatMapperNull(
              () -> defaultWriter().flatMap(STRING_MONOID, null), "f", Operation.FLAT_MAP)
          .execute();
    }

    @Test
    @DisplayName("flatMap() validates non-null results")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void flatMapValidatesNonNullResults() {
      Function<Integer, Writer<String, String>> nullReturningMapper = _ -> null;

      assertThatThrownBy(() -> defaultWriter().flatMap(STRING_MONOID, nullReturningMapper))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function f in flatMap returned null, which is not allowed");
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
    }

    @Test
    @DisplayName("equals() and hashCode() work correctly")
    void equalsAndHashCodeWorkCorrectly() {
      Writer<String, Integer> writer1 = defaultWriter();

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
                case Writer(String log, Integer value) when value > 0 ->
                    "Positive: " + value + " (logged: " + log + ")";
                case Writer(String log, Integer value) when value < 0 ->
                    "Negative: " + value + " (logged: " + log + ")";
                case Writer(String log, _) -> "Zero (logged: " + log + ")";
              };

      assertThat(processWriter.apply(defaultWriter()))
          .isEqualTo("Positive: " + DEFAULT_VALUE + " (logged: " + DEFAULT_LOG + ")");
      assertThat(processWriter.apply(writerOf("Neg;", -5)))
          .isEqualTo("Negative: -5 (logged: Neg;)");
      assertThat(processWriter.apply(writerOf("Zero;", 0))).isEqualTo("Zero (logged: Zero;)");
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
      Writer<String, Integer> result = valueWriter(0);
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
