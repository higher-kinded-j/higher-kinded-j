// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.test.api.CoreTypeTest.writerKindHelper;

import java.util.List;
import java.util.function.BiPredicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
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
  protected java.util.function.Function<Integer, String> createValidMapper() {
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
}
