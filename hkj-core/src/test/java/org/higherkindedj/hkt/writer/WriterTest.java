// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.test.api.CoreTypeTest.writer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.typeclass.StringMonoid;
import org.higherkindedj.hkt.unit.Unit;
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
class WriterTest extends TypeClassTestBase<WriterKind.Witness<String>, Integer, String> {

  private final Monoid<String> stringMonoid = new StringMonoid();
  private final Writer<String, Integer> valueWriter = new Writer<>("Log;", 10);
  private final Writer<String, Unit> tellWriter = Writer.tell("Tell;");
  private final Writer<String, Integer> emptyLogWriter = Writer.value(stringMonoid, 42);
  private final Writer<String, Integer> nullValueWriter = new Writer<>("NullLog;", null);

  // Type class testing fixtures
  private WriterMonad<String> monad;
  private WriterFunctor<String> functor;

  @Override
  protected Kind<WriterKind.Witness<String>, Integer> createValidKind() {
    return WriterKindHelper.WRITER.widen(valueWriter);
  }

  @Override
  protected Kind<WriterKind.Witness<String>, Integer> createValidKind2() {
    return WriterKindHelper.WRITER.widen(Writer.value(stringMonoid, 24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected java.util.function.BiPredicate<
          Kind<WriterKind.Witness<String>, ?>, Kind<WriterKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) ->
        WriterKindHelper.WRITER.narrow(k1).equals(WriterKindHelper.WRITER.narrow(k2));
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> s; // String -> String for law testing
  }

  @Override
  protected Function<Integer, Kind<WriterKind.Witness<String>, String>> createValidFlatMapper() {
    return i ->
        WriterKindHelper.WRITER.widen(new Writer<>(stringMonoid.empty(), String.valueOf(i)));
  }

  @Override
  protected Kind<WriterKind.Witness<String>, Function<Integer, String>> createValidFunctionKind() {
    return WriterKindHelper.WRITER.widen(new Writer<>(stringMonoid.empty(), validMapper));
  }

  @Override
  protected java.util.function.BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (i1, i2) -> String.valueOf(i1 + i2);
  }

  @Override
  protected Integer createTestValue() {
    return 10;
  }

  @Override
  protected Function<Integer, Kind<WriterKind.Witness<String>, String>> createTestFunction() {
    return i -> WriterKindHelper.WRITER.widen(new Writer<>(stringMonoid.empty(), i.toString()));
  }

  @Override
  protected Function<String, Kind<WriterKind.Witness<String>, String>> createChainFunction() {
    return s -> WriterKindHelper.WRITER.widen(new Writer<>(stringMonoid.empty(), s + "!"));
  }

  @BeforeEach
  void setUpWriter() {
    monad = new WriterMonad<>(stringMonoid);
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
  @DisplayName("Core Type Testing with TypeClassTest API")
  class CoreTypeTestingSuite {

    @Test
    @DisplayName("Test all Writer core operations")
    void testAllWriterCoreOperations() {
      CoreTypeTest.<String, Integer>writer(Writer.class)
          .withWriter(valueWriter)
          .withMonoid(stringMonoid)
          .withMappers(TestFunctions.INT_TO_STRING)
          .testAll();
    }

    @Test
    @DisplayName("Test Writer with validation configuration")
    void testWriterWithValidationConfiguration() {
      CoreTypeTest.<String, Integer>writer(Writer.class)
          .withWriter(valueWriter)
          .withMonoid(stringMonoid)
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
          .withWriter(valueWriter)
          .withMonoid(stringMonoid)
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
      Writer<String, Integer> w1 = Writer.value(stringMonoid, 42);
      assertThat(w1.log()).isEqualTo(stringMonoid.empty());
      assertThat(w1.value()).isEqualTo(42);

      // Null values
      Writer<String, Integer> w2 = Writer.value(stringMonoid, null);
      assertThat(w2.log()).isEqualTo(stringMonoid.empty());
      assertThat(w2.value()).isNull();

      // Complex types
      Writer<String, String> w3 = Writer.value(stringMonoid, "test");
      assertThat(w3.log()).isEqualTo(stringMonoid.empty());
      assertThat(w3.value()).isEqualTo("test");
    }

    @Test
    @DisplayName("tell() creates correct instances with Unit value")
    void tellCreatesCorrectInstances() {
      // Non-empty log
      Writer<String, Unit> w1 = Writer.tell("Message");
      assertThat(w1.log()).isEqualTo("Message");
      assertThat(w1.value()).isEqualTo(Unit.INSTANCE);

      // Empty string log
      Writer<String, Unit> w2 = Writer.tell("");
      assertThat(w2.log()).isEmpty();
      assertThat(w2.value()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Constructor creates correct instances")
    void constructorCreatesCorrectInstances() {
      // Standard case
      Writer<String, Integer> w1 = new Writer<>("Log", 10);
      assertThat(w1.log()).isEqualTo("Log");
      assertThat(w1.value()).isEqualTo(10);

      // Null value
      Writer<String, Integer> w2 = new Writer<>("Log", null);
      assertThat(w2.log()).isEqualTo("Log");
      assertThat(w2.value()).isNull();
    }

    @Test
    @DisplayName("Factory methods type inference works correctly")
    void factoryMethodsTypeInference() {
      // Test that type inference works without explicit type parameters
      var valueWriter = Writer.value(stringMonoid, 42);
      var tellWriter = Writer.tell("log");

      // Should be able to assign to properly typed variables
      Writer<String, Integer> valueAssignment = valueWriter;
      Writer<String, Unit> tellAssignment = tellWriter;

      assertThat(valueAssignment.value()).isEqualTo(42);
      assertThat(tellAssignment.log()).isEqualTo("log");
    }
  }

  @Nested
  @DisplayName("Accessor Methods - Comprehensive Coverage")
  class AccessorMethods {

    @Test
    @DisplayName("log() returns correct log for all Writer types")
    void logReturnsCorrectValue() {
      // Standard case
      assertThat(valueWriter.log()).isEqualTo("Log;");

      // Empty log
      assertThat(emptyLogWriter.log()).isEqualTo(stringMonoid.empty());

      // Tell writer
      assertThat(tellWriter.log()).isEqualTo("Tell;");

      // Null value writer still has log
      assertThat(nullValueWriter.log()).isEqualTo("NullLog;");
    }

    @Test
    @DisplayName("value() returns correct value for all Writer types")
    void valueReturnsCorrectValue() {
      // Standard case
      assertThat(valueWriter.value()).isEqualTo(10);

      // Empty log writer
      assertThat(emptyLogWriter.value()).isEqualTo(42);

      // Tell writer returns Unit
      assertThat(tellWriter.value()).isEqualTo(Unit.INSTANCE);

      // Null value
      assertThat(nullValueWriter.value()).isNull();
    }

    @Test
    @DisplayName("run() returns value for all Writer types")
    void runReturnsValue() {
      assertThat(valueWriter.run()).isEqualTo(10);
      assertThat(emptyLogWriter.run()).isEqualTo(42);
      assertThat(tellWriter.run()).isEqualTo(Unit.INSTANCE);
      assertThat(nullValueWriter.run()).isNull();
    }

    @Test
    @DisplayName("exec() returns log for all Writer types")
    void execReturnsLog() {
      assertThat(valueWriter.exec()).isEqualTo("Log;");
      assertThat(emptyLogWriter.exec()).isEqualTo(stringMonoid.empty());
      assertThat(tellWriter.exec()).isEqualTo("Tell;");
      assertThat(nullValueWriter.exec()).isEqualTo("NullLog;");
    }
  }

  @Nested
  @DisplayName("map() Method - Comprehensive Testing")
  class MapMethodTests {

    @Test
    @DisplayName("map() applies function to value and preserves log")
    void mapAppliesFunctionToValue() {
      // Standard transformation
      Writer<String, String> result = valueWriter.map(TestFunctions.INT_TO_STRING);
      assertThat(result.log()).isEqualTo("Log;");
      assertThat(result.value()).isEqualTo("10");

      // Complex transformation
      Writer<String, Integer> doubled = valueWriter.map(i -> i * 2);
      assertThat(doubled.log()).isEqualTo("Log;");
      assertThat(doubled.value()).isEqualTo(20);

      // Transformation to different type
      Writer<String, Boolean> isPositive = valueWriter.map(i -> i > 0);
      assertThat(isPositive.log()).isEqualTo("Log;");
      assertThat(isPositive.value()).isTrue();
    }

    @Test
    @DisplayName("map() handles null values correctly")
    void mapHandlesNullValues() {
      // Mapping null value
      Writer<String, String> result = nullValueWriter.map(i -> String.valueOf(i));
      assertThat(result.log()).isEqualTo("NullLog;");
      assertThat(result.value()).isEqualTo("null");

      // Mapping to null
      Writer<String, Integer> toNull = valueWriter.map(i -> null);
      assertThat(toNull.log()).isEqualTo("Log;");
      assertThat(toNull.value()).isNull();
    }

    @Test
    @DisplayName("map() validates null mapper using standardised validation")
    void mapValidatesNullMapper() {
      ValidationTestBuilder.create()
          .assertMapperNull(() -> valueWriter.map(null), "f", Writer.class, Operation.MAP)
          .execute();
    }

    @Test
    @DisplayName("map() handles exception propagation and chaining")
    void mapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: map test");
      Function<Integer, String> throwingMapper = TestFunctions.throwingFunction(testException);

      // Exception should propagate
      assertThatThrownBy(() -> valueWriter.map(throwingMapper)).isSameAs(testException);

      // Test chaining
      Writer<String, Integer> start = Writer.value(stringMonoid, 10);
      Writer<String, String> chainResult =
          start.map(i -> i * 2).map(i -> "Value: " + i).map(String::toUpperCase);

      assertThat(chainResult.log()).isEqualTo(stringMonoid.empty());
      assertThat(chainResult.value()).isEqualTo("VALUE: 20");
    }

    @Test
    @DisplayName("map() preserves log through multiple transformations")
    void mapPreservesLogThroughChaining() {
      Writer<String, String> result =
          valueWriter
              .map(i -> i + 5) // 15
              .map(i -> i * 2) // 30
              .map(Object::toString); // "30"

      assertThat(result.log()).isEqualTo("Log;");
      assertThat(result.value()).isEqualTo("30");
    }
  }

  @Nested
  @DisplayName("flatMap() Method - Comprehensive Monadic Testing")
  class FlatMapMethodTests {

    @Test
    @DisplayName("flatMap() applies function and combines logs")
    void flatMapAppliesFunctionAndCombinesLogs() {
      Function<Integer, Writer<String, String>> mapper =
          i -> new Writer<>("Mapped(" + i + ");", "Value: " + i);

      Writer<String, String> result = valueWriter.flatMap(stringMonoid, mapper);

      assertThat(result.log()).isEqualTo("Log;Mapped(10);");
      assertThat(result.value()).isEqualTo("Value: 10");
    }

    @Test
    @DisplayName("flatMap() works with tell for logging")
    void flatMapWorksWithTell() {
      Writer<String, Integer> start = Writer.value(stringMonoid, 10);

      Writer<String, String> result =
          start
              .flatMap(stringMonoid, i -> Writer.tell("Logged " + i + ";"))
              .flatMap(stringMonoid, v -> new Writer<>("Final;", "Done"));

      assertThat(result.log()).isEqualTo("Logged 10;Final;");
      assertThat(result.value()).isEqualTo("Done");
    }

    @Test
    @DisplayName("flatMap() supports complex chaining patterns")
    void flatMapSupportsComplexChaining() {
      Writer<String, Integer> start = Writer.value(stringMonoid, 5);

      Writer<String, String> result =
          start
              .flatMap(stringMonoid, i -> new Writer<>("Double;", i * 2))
              .flatMap(stringMonoid, i -> new Writer<>("Add;", i + 10))
              .flatMap(stringMonoid, i -> new Writer<>("Result;", "Final: " + i));

      assertThat(result.log()).isEqualTo("Double;Add;Result;");
      assertThat(result.value()).isEqualTo("Final: 20");
    }

    @Test
    @DisplayName("flatMap() validates parameters using standardised validation")
    void flatMapValidatesParameters() {
      Function<Integer, Writer<String, String>> validMapper =
          i -> new Writer<>(stringMonoid.empty(), String.valueOf(i));

      ValidationTestBuilder.create()
          .assertMonoidNull(
              () -> valueWriter.flatMap(null, validMapper),
              "monoidW",
              Writer.class,
              Operation.FLAT_MAP)
          .assertFlatMapperNull(
              () -> valueWriter.flatMap(stringMonoid, null), "f", Writer.class, Operation.FLAT_MAP)
          .execute();
    }

    @Test
    @DisplayName("flatMap() validates non-null results")
    void flatMapValidatesNonNullResults() {
      Function<Integer, Writer<String, String>> nullReturningMapper = i -> null;

      assertThatThrownBy(() -> valueWriter.flatMap(stringMonoid, nullReturningMapper))
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

      assertThatThrownBy(() -> valueWriter.flatMap(stringMonoid, throwingMapper))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("flatMap() handles null values correctly")
    void flatMapHandlesNullValues() {
      Function<Integer, Writer<String, String>> mapper =
          i -> new Writer<>("Null;", i == null ? "was null" : "was " + i);

      Writer<String, String> result = nullValueWriter.flatMap(stringMonoid, mapper);

      assertThat(result.log()).isEqualTo("NullLog;Null;");
      assertThat(result.value()).isEqualTo("was null");
    }
  }

  @Nested
  @DisplayName("toString() and Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() provides meaningful representations")
    void toStringProvidesMeaningfulRepresentations() {
      assertThat(valueWriter.toString()).contains("Writer").contains("Log;").contains("10");

      assertThat(tellWriter.toString()).contains("Writer").contains("Tell;").contains("()");

      assertThat(nullValueWriter.toString())
          .contains("Writer")
          .contains("NullLog;")
          .contains("null");
    }

    @Test
    @DisplayName("equals() and hashCode() work correctly")
    void equalsAndHashCodeWorkCorrectly() {
      // Same instances
      assertThat(valueWriter).isEqualTo(valueWriter);

      // Equal instances
      Writer<String, Integer> another = new Writer<>("Log;", 10);
      assertThat(valueWriter).isEqualTo(another);
      assertThat(valueWriter.hashCode()).isEqualTo(another.hashCode());

      // Different log
      Writer<String, Integer> differentLog = new Writer<>("Other;", 10);
      assertThat(valueWriter).isNotEqualTo(differentLog);

      // Different value
      Writer<String, Integer> differentValue = new Writer<>("Log;", 20);
      assertThat(valueWriter).isNotEqualTo(differentValue);

      // Null handling
      Writer<String, Integer> nullValue1 = new Writer<>("Log;", null);
      Writer<String, Integer> nullValue2 = new Writer<>("Log;", null);
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
      Writer<String, Integer> start = new Writer<>("Init;", 5);

      Writer<String, Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

      assertThat(result.log()).isEqualTo("Init;");
      assertThat(result.value()).isCloseTo(Math.sqrt(10.5), within(0.001));
    }

    @Test
    @DisplayName("Writer for computation logging")
    void writerForComputationLogging() {
      Writer<String, Integer> computation =
          Writer.value(stringMonoid, 10)
              .flatMap(stringMonoid, i -> new Writer<>("Start: " + i + "; ", i))
              .flatMap(stringMonoid, i -> new Writer<>("Double: " + (i * 2) + "; ", i * 2))
              .flatMap(stringMonoid, i -> new Writer<>("Add 5: " + (i + 5) + "; ", i + 5));

      assertThat(computation.log()).isEqualTo("Start: 10; Double: 20; Add 5: 25; ");
      assertThat(computation.value()).isEqualTo(25);
    }

    @Test
    @DisplayName("Writer with accumulating logs")
    void writerWithAccumulatingLogs() {
      AtomicInteger counter = new AtomicInteger(0);

      Writer<String, Integer> tracked =
          Writer.value(stringMonoid, 1)
              .flatMap(
                  stringMonoid,
                  i -> {
                    int step = counter.incrementAndGet();
                    return new Writer<>("Step " + step + ": " + i + "; ", i * 2);
                  })
              .flatMap(
                  stringMonoid,
                  i -> {
                    int step = counter.incrementAndGet();
                    return new Writer<>("Step " + step + ": " + i + "; ", i + 10);
                  })
              .flatMap(
                  stringMonoid,
                  i -> {
                    int step = counter.incrementAndGet();
                    return new Writer<>("Step " + step + ": " + i + "; ", i * 3);
                  });

      assertThat(tracked.log()).isEqualTo("Step 1: 1; Step 2: 2; Step 3: 12; ");
      assertThat(tracked.value()).isEqualTo(36);
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

      assertThat(processWriter.apply(valueWriter)).isEqualTo("Positive: 10 (logged: Log;)");
      assertThat(processWriter.apply(new Writer<>("Neg;", -5)))
          .isEqualTo("Negative: -5 (logged: Neg;)");
      assertThat(processWriter.apply(nullValueWriter)).isEqualTo("Null value (logged: NullLog;)");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("Writer operations have predictable performance")
    void writerOperationsHavePredictablePerformance() {
      Writer<String, Integer> test = Writer.value(stringMonoid, 42);

      long start = System.nanoTime();
      for (int i = 0; i < 10000; i++) {
        test.map(x -> x + 1).flatMap(stringMonoid, x -> Writer.value(stringMonoid, x * 2)).value();
      }
      long duration = System.nanoTime() - start;

      // Should complete in reasonable time (less than 100ms for 10k ops)
      assertThat(duration).isLessThan(100_000_000L);
    }

    @Test
    @DisplayName("Log concatenation is efficient")
    void logConcatenationIsEfficient() {
      Writer<String, Integer> start = Writer.value(stringMonoid, 1);

      Writer<String, Integer> result = start;
      for (int i = 0; i < 100; i++) {
        final int iteration = i;
        result = result.flatMap(stringMonoid, x -> new Writer<>("Step " + iteration + ";", x + 1));
      }

      assertThat(result.value()).isEqualTo(101);
      assertThat(result.log()).contains("Step 0;").contains("Step 99;");
    }
  }

  @Nested
  @DisplayName("Type Safety and Variance")
  class TypeSafetyAndVarianceTests {

    @Test
    @DisplayName("Writer maintains type safety across operations")
    void writerMaintainsTypeSafety() {
      Writer<String, Number> numberWriter = Writer.value(stringMonoid, 42);
      Writer<String, Integer> intWriter =
          numberWriter.flatMap(stringMonoid, n -> Writer.value(stringMonoid, n.intValue()));

      assertThat(intWriter.value()).isEqualTo(42);
    }

    @Test
    @DisplayName("Writer works with complex generic types")
    void writerWorksWithComplexGenericTypes() {
      Writer<String, java.util.List<Integer>> listWriter =
          Writer.value(stringMonoid, java.util.List.of(1, 2, 3));

      Writer<String, Integer> sumWriter =
          listWriter.map(list -> list.stream().mapToInt(Integer::intValue).sum());

      assertThat(sumWriter.value()).isEqualTo(6);
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
      Writer<String, Integer> largeLogWriter = new Writer<>(largeLog, 42);
      assertThat(largeLogWriter.log()).hasSize(10000);

      // Maximum integer
      Writer<String, Integer> maxIntWriter = Writer.value(stringMonoid, Integer.MAX_VALUE);
      Writer<String, Long> promoted = maxIntWriter.map(i -> i.longValue() + 1);
      assertThat(promoted.value()).isEqualTo((long) Integer.MAX_VALUE + 1);
    }

    @Test
    @DisplayName("Writer operations are stack-safe for deep chains")
    void writerOperationsAreStackSafe() {
      Writer<String, Integer> start = Writer.value(stringMonoid, 0);

      Writer<String, Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = result.map(x -> x + 1);
      }

      assertThat(result.value()).isEqualTo(10000);
    }

    @Test
    @DisplayName("Writer maintains referential transparency")
    void writerMaintainsReferentialTransparency() {
      Writer<String, Integer> writer = Writer.value(stringMonoid, 42);
      Function<Integer, String> transform = i -> "value:" + i;

      Writer<String, String> result1 = writer.map(transform);
      Writer<String, String> result2 = writer.map(transform);

      assertThat(result1).isEqualTo(result2);
      assertThat(result1.value()).isEqualTo(result2.value());
      assertThat(result1.log()).isEqualTo(result2.log());
    }
  }
}
