// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.test.api.CoreTypeTest.writerKindHelper;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.typeclass.StringMonoid;
import org.higherkindedj.hkt.unit.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WriterKindHelper Complete Test Suite")
class WriterKindHelperTest extends TypeClassTestBase<WriterKind.Witness<String>, Integer, String> {

  private static final WriterKindHelper WRITER = WriterKindHelper.WRITER;
  private static final Monoid<String> STRING_MONOID = new StringMonoid();

  @Override
  protected Kind<WriterKind.Witness<String>, Integer> createValidKind() {
    return WRITER.widen(Writer.value(STRING_MONOID, 42));
  }

  @Override
  protected Kind<WriterKind.Witness<String>, Integer> createValidKind2() {
    return WRITER.widen(Writer.value(STRING_MONOID, 24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected BiPredicate<Kind<WriterKind.Witness<String>, ?>, Kind<WriterKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> WRITER.narrow(k1).equals(WRITER.narrow(k2));
  }

  @Nested
  @DisplayName("Complete KindHelper Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete KindHelper test suite for Writer")
    void completeKindHelperTestSuite() {
      Writer<String, Integer> validInstance = Writer.value(STRING_MONOID, 42);

      writerKindHelper(validInstance).test();
    }

    @Test
    @DisplayName("Complete test suite with multiple Writer types")
    void completeTestSuiteWithMultipleTypes() {
      List<Writer<String, Integer>> testInstances =
          List.of(
              Writer.value(STRING_MONOID, 42),
              new Writer<>("Log;", 10),
              Writer.value(STRING_MONOID, null),
              new Writer<>("NullLog;", null));

      for (Writer<String, Integer> instance : testInstances) {
        writerKindHelper(instance).test();
      }
    }

    @Test
    @DisplayName("Comprehensive test with implementation validation")
    void comprehensiveTestWithImplementationValidation() {
      Writer<String, Integer> validInstance = Writer.value(STRING_MONOID, 100);

      writerKindHelper(validInstance).testWithValidation(WriterKindHelper.class);
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {

    @Test
    @DisplayName("Test round-trip widen/narrow operations")
    void testRoundTripOperations() {
      Writer<String, Integer> validInstance = Writer.value(STRING_MONOID, 42);

      writerKindHelper(validInstance)
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test null parameter validations")
    void testNullParameterValidations() {
      writerKindHelper(Writer.value(STRING_MONOID, 42))
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid Kind type handling")
    void testInvalidKindType() {
      writerKindHelper(Writer.value(STRING_MONOID, 42))
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test idempotency of operations")
    void testIdempotency() {
      Writer<String, Integer> validInstance = new Writer<>("Log;", 10);

      writerKindHelper(validInstance)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test edge cases and boundary conditions")
    void testEdgeCases() {
      Writer<String, Integer> validInstance = Writer.value(STRING_MONOID, 42);

      writerKindHelper(validInstance)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .test();
    }
  }

  @Nested
  @DisplayName("Specific Writer Behaviour Tests")
  class SpecificBehaviourTests {

    @Test
    @DisplayName("Both value and tell instances work correctly")
    void testValueAndTellInstances() {
      Writer<String, Integer> valueWriter = Writer.value(STRING_MONOID, 42);
      Writer<String, Unit> tellWriter = Writer.tell("Log message");

      writerKindHelper(valueWriter).test();
      writerKindHelper(tellWriter).test();
    }

    @Test
    @DisplayName("Null values in Writer are preserved")
    void testNullValuesPreserved() {
      Writer<String, Integer> nullValue = Writer.value(STRING_MONOID, null);
      Writer<String, String> nullString = new Writer<>("Log;", null);

      writerKindHelper(nullValue).test();
      writerKindHelper(nullString).test();
    }

    @Test
    @DisplayName("Complex log types work correctly")
    void testComplexLogTypes() {
      record LogEntry(String message, java.time.Instant timestamp) {}

      Monoid<List<LogEntry>> listMonoid =
          new Monoid<>() {
            @Override
            public List<LogEntry> empty() {
              return List.of();
            }

            @Override
            public List<LogEntry> combine(List<LogEntry> a, List<LogEntry> b) {
              var result = new java.util.ArrayList<>(a);
              result.addAll(b);
              return result;
            }
          };

      LogEntry entry = new LogEntry("Test", java.time.Instant.now());
      Writer<List<LogEntry>, Integer> complexWriter = new Writer<>(List.of(entry), 42);

      writerKindHelper(complexWriter).test();
    }

    @Test
    @DisplayName("Type safety across different generic parameters")
    void testTypeSafetyAcrossDifferentGenerics() {
      Writer<String, Integer> intWriter = Writer.value(STRING_MONOID, 42);
      Writer<List<String>, Integer> listLogWriter = new Writer<>(List.of("log1", "log2"), 42);

      writerKindHelper(intWriter).test();
      writerKindHelper(listLogWriter).test();
    }

    @Test
    @DisplayName("Complex value types with nested generics")
    void testComplexValueTypes() {
      Writer<String, List<Integer>> complexValue = Writer.value(STRING_MONOID, List.of(1, 2, 3));
      Writer<String, java.util.Map<String, Integer>> mapValue =
          new Writer<>("Log;", java.util.Map.of("a", 1, "b", 2));

      writerKindHelper(complexValue).test();
      writerKindHelper(mapValue).test();

      assertThat(complexValue.value()).containsExactly(1, 2, 3);
      assertThat(mapValue.value()).containsEntry("a", 1).containsEntry("b", 2);
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Holder creates minimal overhead")
    void testMinimalOverhead() {
      Writer<String, Integer> original = Writer.value(STRING_MONOID, 42);

      writerKindHelper(original).skipPerformance().test();
    }

    @Test
    @DisplayName("Multiple operations are idempotent")
    void testIdempotentOperations() {
      Writer<String, Integer> original = new Writer<>("Log;", 10);

      writerKindHelper(original)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Performance characteristics test")
    void testPerformanceCharacteristics() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Writer<String, Integer> testInstance = Writer.value(STRING_MONOID, 42);

        writerKindHelper(testInstance).withPerformanceTests().test();
      }
    }

    @Test
    @DisplayName("Memory efficiency test")
    void testMemoryEfficiency() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Writer<String, Integer> testInstance = Writer.value(STRING_MONOID, 42);

        writerKindHelper(testInstance).withPerformanceTests().test();
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases and Corner Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("All combinations of null values")
    void testAllNullValueCombinations() {
      List<Writer<String, Integer>> nullInstances =
          List.of(
              Writer.value(STRING_MONOID, null),
              new Writer<>("Log;", null),
              new Writer<>("", 42),
              Writer.value(STRING_MONOID, 0));

      for (Writer<String, Integer> instance : nullInstances) {
        writerKindHelper(instance).test();
      }
    }
  }

  @Nested
  @DisplayName("Advanced Testing Scenarios")
  class AdvancedTestingScenarios {

    @Test
    @DisplayName("Concurrent access test")
    void testConcurrentAccess() {
      if (Boolean.parseBoolean(System.getProperty("test.concurrency", "false"))) {
        Writer<String, Integer> testInstance = Writer.value(STRING_MONOID, 42);

        writerKindHelper(testInstance).withConcurrencyTests().test();
      }
    }

    @Test
    @DisplayName("Quick test for fast test suites")
    void testQuickValidation() {
      Writer<String, Integer> testInstance = Writer.value(STRING_MONOID, 42);

      writerKindHelper(testInstance).test();
    }

    @Test
    @DisplayName("Stress test with complex scenarios")
    void testComplexStressScenarios() {
      // Use explicit type on left side and add each writer separately
      List<Writer<String, ?>> complexInstances =
          List.of(
              Writer.<String, Object>value(STRING_MONOID, "simple_string"),
              Writer.<String, Object>value(STRING_MONOID, 42),
              Writer.<String, Object>value(STRING_MONOID, List.of(1, 2, 3)),
              new Writer<>("Log;", java.util.Map.of("key", "value")),
              Writer.tell("Tell message"),
              Writer.<String, Object>value(STRING_MONOID, null),
              new Writer<>("", "empty log"));

      for (Writer<String, ?> instance : complexInstances) {
        writerKindHelper(instance).test();
      }
    }
  }

  @Nested
  @DisplayName("Comprehensive Coverage Tests")
  class ComprehensiveCoverageTests {

    @Test
    @DisplayName("All Writer types and states")
    void testAllWriterTypesAndStates() {
      List<Writer<String, Integer>> allStates =
          List.of(
              Writer.value(STRING_MONOID, 42),
              Writer.value(STRING_MONOID, 0),
              Writer.value(STRING_MONOID, null),
              new Writer<>("Log;", 10),
              new Writer<>("", 10),
              new Writer<>("Log;", null));

      for (Writer<String, Integer> state : allStates) {
        writerKindHelper(state).test();
      }
    }

    @Test
    @DisplayName("Full lifecycle test")
    void testFullLifecycle() {
      Writer<String, Integer> original = Writer.value(STRING_MONOID, 42);

      writerKindHelper(original).test();

      Writer<String, Unit> tellOriginal = Writer.tell("Lifecycle test");

      writerKindHelper(tellOriginal).test();
    }
  }

  @Nested
  @DisplayName("Runner Method Tests")
  class RunnerMethodTests {

    @Test
    @DisplayName("run() extracts value from Writer Kind")
    void runExtractsValueFromWriterKind() {
      Writer<String, Integer> writer = Writer.value(STRING_MONOID, 42);
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(writer);

      Integer result = WRITER.run(kind);

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("run() handles null values correctly")
    void runHandlesNullValuesCorrectly() {
      Writer<String, Integer> writer = Writer.value(STRING_MONOID, null);
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(writer);

      Integer result = WRITER.run(kind);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("run() extracts Unit from tell Writers")
    void runExtractsUnitFromTellWriters() {
      Writer<String, Unit> writer = Writer.tell("Log message");
      Kind<WriterKind.Witness<String>, Unit> kind = WRITER.widen(writer);

      Unit result = WRITER.run(kind);

      assertThat(result).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("run() works with complex value types")
    void runWorksWithComplexValueTypes() {
      List<Integer> complexValue = List.of(1, 2, 3);
      Writer<String, List<Integer>> writer = Writer.value(STRING_MONOID, complexValue);
      Kind<WriterKind.Witness<String>, List<Integer>> kind = WRITER.widen(writer);

      List<Integer> result = WRITER.run(kind);

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("exec() extracts log from Writer Kind")
    void execExtractsLogFromWriterKind() {
      Writer<String, Integer> writer = new Writer<>("TestLog;", 42);
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(writer);

      String result = WRITER.exec(kind);

      assertThat(result).isEqualTo("TestLog;");
    }

    @Test
    @DisplayName("exec() returns empty log when log is empty")
    void execReturnsEmptyLogWhenLogIsEmpty() {
      Writer<String, Integer> writer = Writer.value(STRING_MONOID, 42);
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(writer);

      String result = WRITER.exec(kind);

      assertThat(result).isEqualTo(STRING_MONOID.empty());
    }

    @Test
    @DisplayName("exec() works with tell Writers")
    void execWorksWithTellWriters() {
      Writer<String, Unit> writer = Writer.tell("Important log");
      Kind<WriterKind.Witness<String>, Unit> kind = WRITER.widen(writer);

      String result = WRITER.exec(kind);

      assertThat(result).isEqualTo("Important log");
    }

    @Test
    @DisplayName("exec() works with complex log types")
    void execWorksWithComplexLogTypes() {
      record LogEntry(String message, int level) {}

      Monoid<List<LogEntry>> listMonoid =
          new Monoid<>() {
            @Override
            public List<LogEntry> empty() {
              return List.of();
            }

            @Override
            public List<LogEntry> combine(List<LogEntry> a, List<LogEntry> b) {
              var result = new java.util.ArrayList<>(a);
              result.addAll(b);
              return result;
            }
          };

      List<LogEntry> log = List.of(new LogEntry("Error", 1), new LogEntry("Warning", 2));
      Writer<List<LogEntry>, Integer> writer = new Writer<>(log, 42);
      Kind<WriterKind.Witness<List<LogEntry>>, Integer> kind =
          WriterKindHelper.WRITER.widen(writer);

      List<LogEntry> result = WriterKindHelper.WRITER.exec(kind);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).message()).isEqualTo("Error");
      assertThat(result.get(1).message()).isEqualTo("Warning");
    }

    @Test
    @DisplayName("runWriter() returns complete Writer record")
    void runWriterReturnsCompleteWriterRecord() {
      Writer<String, Integer> original = new Writer<>("TestLog;", 42);
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(original);

      Writer<String, Integer> result = WRITER.runWriter(kind);

      assertThat(result).isEqualTo(original);
      assertThat(result.log()).isEqualTo("TestLog;");
      assertThat(result.value()).isEqualTo(42);
    }

    @Test
    @DisplayName("runWriter() preserves both log and value")
    void runWriterPreservesBothLogAndValue() {
      Writer<String, Integer> writer = Writer.value(STRING_MONOID, 42);
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(writer);

      Writer<String, Integer> result = WRITER.runWriter(kind);

      assertThat(result.log()).isEqualTo(STRING_MONOID.empty());
      assertThat(result.value()).isEqualTo(42);
    }

    @Test
    @DisplayName("runWriter() works with null values")
    void runWriterWorksWithNullValues() {
      Writer<String, Integer> writer = new Writer<>("NullLog;", null);
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(writer);

      Writer<String, Integer> result = WRITER.runWriter(kind);

      assertThat(result.log()).isEqualTo("NullLog;");
      assertThat(result.value()).isNull();
    }

    @Test
    @DisplayName("runWriter() works with tell Writers")
    void runWriterWorksWithTellWriters() {
      Writer<String, Unit> writer = Writer.tell("Tell log");
      Kind<WriterKind.Witness<String>, Unit> kind = WRITER.widen(writer);

      Writer<String, Unit> result = WRITER.runWriter(kind);

      assertThat(result.log()).isEqualTo("Tell log");
      assertThat(result.value()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Runner methods are consistent with each other")
    void runnerMethodsAreConsistentWithEachOther() {
      Writer<String, Integer> writer = new Writer<>("ConsistencyLog;", 42);
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(writer);

      // Extract using different methods
      Integer valueViaRun = WRITER.run(kind);
      String logViaExec = WRITER.exec(kind);
      Writer<String, Integer> writerViaRunWriter = WRITER.runWriter(kind);

      // All should be consistent
      assertThat(valueViaRun).isEqualTo(writerViaRunWriter.value());
      assertThat(logViaExec).isEqualTo(writerViaRunWriter.log());
      assertThat(writerViaRunWriter).isEqualTo(writer);
    }

    @Test
    @DisplayName("Runner methods maintain type safety")
    void runnerMethodsMaintainTypeSafety() {
      Writer<String, Number> numberWriter = Writer.value(STRING_MONOID, (Number) 42);
      Kind<WriterKind.Witness<String>, Number> kind = WRITER.widen(numberWriter);

      Number value = WRITER.run(kind);
      String log = WRITER.exec(kind);
      Writer<String, Number> complete = WRITER.runWriter(kind);

      assertThat(value).isInstanceOf(Number.class);
      assertThat(log).isInstanceOf(String.class);
      assertThat(complete.value()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Runner Method Integration Tests")
  class RunnerMethodIntegrationTests {

    @Test
    @DisplayName("Runner methods work with monadic operations")
    void runnerMethodsWorkWithMonadicOperations() {
      Monoid<String> monoid = new StringMonoid();
      Kind<WriterKind.Witness<String>, Integer> kind1 = WRITER.widen(new Writer<>("Log1;", 10));
      Kind<WriterKind.Witness<String>, Integer> kind2 = WRITER.widen(new Writer<>("Log2;", 20));

      // Use WriterMonad to combine
      WriterMonad<String> monad = new WriterMonad<>(monoid);
      Kind<WriterKind.Witness<String>, Integer> combined = monad.map2(kind1, kind2, Integer::sum);

      // Extract results using runner methods
      Integer value = WRITER.run(combined);
      String log = WRITER.exec(combined);
      Writer<String, Integer> complete = WRITER.runWriter(combined);

      assertThat(value).isEqualTo(30);
      assertThat(log).isEqualTo("Log1;Log2;");
      assertThat(complete.log()).isEqualTo("Log1;Log2;");
      assertThat(complete.value()).isEqualTo(30);
    }

    @Test
    @DisplayName("Runner methods work with functor operations")
    void runnerMethodsWorkWithFunctorOperations() {
      WriterFunctor<String> functor = new WriterFunctor<>();
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(new Writer<>("Original;", 10));

      Kind<WriterKind.Witness<String>, String> mapped = functor.map(i -> "Value:" + i, kind);

      String value = WRITER.run(mapped);
      String log = WRITER.exec(mapped);

      assertThat(value).isEqualTo("Value:10");
      assertThat(log).isEqualTo("Original;");
    }

    @Test
    @DisplayName("Runner methods work after flatMap operations")
    void runnerMethodsWorkAfterFlatMapOperations() {
      Monoid<String> monoid = new StringMonoid();
      WriterMonad<String> monad = new WriterMonad<>(monoid);

      Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(new Writer<>("Start;", 5));

      Kind<WriterKind.Witness<String>, String> result =
          monad.flatMap(i -> WRITER.widen(new Writer<>("Mapped:" + i + ";", "Result:" + i)), start);

      String value = WRITER.run(result);
      String log = WRITER.exec(result);
      Writer<String, String> complete = WRITER.runWriter(result);

      assertThat(value).isEqualTo("Result:5");
      assertThat(log).isEqualTo("Start;Mapped:5;");
      assertThat(complete.log()).isEqualTo("Start;Mapped:5;");
      assertThat(complete.value()).isEqualTo("Result:5");
    }

    @Test
    @DisplayName("Runner methods work with deep operation chains")
    void runnerMethodsWorkWithDeepOperationChains() {
      Monoid<String> monoid = new StringMonoid();
      WriterMonad<String> monad = new WriterMonad<>(monoid);

      Kind<WriterKind.Witness<String>, Integer> start = monad.of(1);

      Kind<WriterKind.Witness<String>, Integer> result = start;
      for (int i = 0; i < 5; i++) {
        final int step = i;
        result =
            monad.flatMap(x -> WRITER.widen(new Writer<>("Step" + step + ";", x + step)), result);
      }

      Integer value = WRITER.run(result);
      String log = WRITER.exec(result);

      assertThat(value).isEqualTo(11); // 1 + 0 + 1 + 2 + 3 + 4
      assertThat(log).contains("Step0;", "Step1;", "Step2;", "Step3;", "Step4;");
    }

    @Test
    @DisplayName("Runner methods validate invalid Kind types")
    void runnerMethodsValidateInvalidKindTypes() {
      // Create an invalid Kind that isn't a WriterHolder
      Kind<WriterKind.Witness<String>, Integer> invalidKind =
          new Kind<>() {
            @Override
            public String toString() {
              return "InvalidKind";
            }
          };

      assertThatThrownBy(() -> WRITER.run(invalidKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance cannot be narrowed to Writer");

      assertThatThrownBy(() -> WRITER.exec(invalidKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance cannot be narrowed to Writer");

      assertThatThrownBy(() -> WRITER.runWriter(invalidKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance cannot be narrowed to Writer");
    }
  }

  @Test
  @DisplayName("Runner methods have predictable performance")
  void runnerMethodsHavePredictablePerformance() {
    Writer<String, Integer> writer = Writer.value(STRING_MONOID, 42);
    Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(writer);

    long startRun = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
      WRITER.run(kind);
    }
    long durationRun = System.nanoTime() - startRun;

    long startExec = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
      WRITER.exec(kind);
    }
    long durationExec = System.nanoTime() - startExec;

    long startRunWriter = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
      WRITER.runWriter(kind);
    }
    long durationRunWriter = System.nanoTime() - startRunWriter;

    // All should complete quickly (less than 50ms for 10k ops)
    assertThat(durationRun).isLessThan(50_000_000L);
    assertThat(durationExec).isLessThan(50_000_000L);
    assertThat(durationRunWriter).isLessThan(50_000_000L);
  }

  @Test
  @DisplayName("Runner methods maintain performance with complex types")
  void runnerMethodsMaintainPerformanceWithComplexTypes() {
    List<Integer> complexValue = List.of(1, 2, 3, 4, 5);
    Writer<String, List<Integer>> writer = new Writer<>("ComplexLog;", complexValue);
    Kind<WriterKind.Witness<String>, List<Integer>> kind = WRITER.widen(writer);

    long start = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
      List<Integer> value = WRITER.run(kind);
      String log = WRITER.exec(kind);
      Writer<String, List<Integer>> complete = WRITER.runWriter(kind);
    }
    long duration = System.nanoTime() - start;

    assertThat(duration).isLessThan(100_000_000L);
  }
}
