// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.test.api.CoreTypeTest.readerKindHelper;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderKindHelper Complete Test Suite")
class ReaderKindHelperTest
    extends TypeClassTestBase<ReaderKind.Witness<ReaderKindHelperTest.TestEnv>, ReaderKindHelperTest.TestEnv, String> {

  public record TestEnv(String value) {}

  // Helper constant for cleaner type references
  private static final ReaderKindHelper READER = ReaderKindHelper.READER;
  private static final TestEnv TEST_ENV = new TestEnv("test-environment");

  @Override
  protected Kind<ReaderKind.Witness<TestEnv>, TestEnv> createValidKind() {
    return READER.widen(Reader.ask());
  }

  @Override
  protected Kind<ReaderKind.Witness<TestEnv>, TestEnv> createValidKind2() {
    return READER.widen(Reader.constant(TEST_ENV));
  }

  @Override
  protected Function<TestEnv, String> createValidMapper() {
    return TestEnv::value;
  }

  @Override
  protected BiPredicate<
            Kind<ReaderKind.Witness<TestEnv>, ?>, Kind<ReaderKind.Witness<TestEnv>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      Reader<TestEnv, ?> r1 = READER.narrow(k1);
      Reader<TestEnv, ?> r2 = READER.narrow(k2);
      return r1.run(TEST_ENV).equals(r2.run(TEST_ENV));
    };
  }

  @Nested
  @DisplayName("Complete KindHelper Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete KindHelper test suite for Reader")
    void completeKindHelperTestSuite() {
      Reader<TestEnv, String> validInstance = Reader.of(TestEnv::value);

      readerKindHelper(validInstance).test();
    }

    @Test
    @DisplayName("Complete test suite with multiple Reader types")
    void completeTestSuiteWithMultipleTypes() {
      List<Reader<TestEnv, String>> testInstances =
          List.of(
              Reader.of(TestEnv::value),
              Reader.constant("constant-value"),
              Reader.<TestEnv>ask().map(TestEnv::value), // Add explicit type parameter
              Reader.of(env -> null));

      for (Reader<TestEnv, String> instance : testInstances) {
        readerKindHelper(instance).test();
      }
    }

    @Test
    @DisplayName("Comprehensive test with implementation validation")
    void comprehensiveTestWithImplementationValidation() {
      Reader<TestEnv, String> validInstance = Reader.of(TestEnv::value);

      readerKindHelper(validInstance).testWithValidation(ReaderKindHelper.class);
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {

    @Test
    @DisplayName("Test round-trip widen/narrow operations")
    void testRoundTripOperations() {
      Reader<TestEnv, String> validInstance = Reader.of(TestEnv::value);

      readerKindHelper(validInstance)
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test null parameter validations")
    void testNullParameterValidations() {
      readerKindHelper(Reader.<TestEnv>ask())
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid Kind type handling")
    void testInvalidKindType() {
      readerKindHelper(Reader.<TestEnv, String>constant("test"))
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test idempotency of operations")
    void testIdempotency() {
      Reader<TestEnv, String> validInstance = Reader.of(env -> env.value() + "-idempotent");

      readerKindHelper(validInstance)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test edge cases and boundary conditions")
    void testEdgeCases() {
      Reader<TestEnv, String> validInstance = Reader.constant("edge-case");

      readerKindHelper(validInstance)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .test();
    }
  }

  @Nested
  @DisplayName("Specific Reader Behaviour Tests")
  class SpecificBehaviourTests {

    @Test
    @DisplayName("All Reader factory methods work correctly")
    void testAllFactoryMethods() {
      Reader<TestEnv, String> ofReader = Reader.of(TestEnv::value);
      Reader<TestEnv, String> constantReader = Reader.constant("constant");
      Reader<TestEnv, TestEnv> askReader = Reader.ask();

      readerKindHelper(ofReader).test();
      readerKindHelper(constantReader).test();
      readerKindHelper(askReader).test();
    }

    @Test
    @DisplayName("Null values in Reader are preserved")
    void testNullValuesPreserved() {
      Reader<TestEnv, String> nullReader = Reader.of(env -> null);
      Reader<TestEnv, String> constantNull = Reader.constant(null);

      readerKindHelper(nullReader).test();
      readerKindHelper(constantNull).test();
    }

    @Test
    @DisplayName("Complex environment types work correctly")
    void testComplexEnvironmentTypes() {
      record ComplexEnv(
          String name, int value, java.util.List<String> items, java.time.Instant timestamp) {}

      ComplexEnv complexEnv =
          new ComplexEnv("complex", 42, java.util.List.of("a", "b", "c"), java.time.Instant.now());

      Reader<ComplexEnv, String> complexReader = Reader.of(ComplexEnv::name);

      CoreTypeTest.readerKindHelper(complexReader).test();

      Kind<ReaderKind.Witness<ComplexEnv>, String> widened =
          ReaderKindHelper.READER.widen(complexReader);
      Reader<ComplexEnv, String> narrowed = ReaderKindHelper.READER.narrow(widened);

      assertThat(narrowed.run(complexEnv)).isEqualTo("complex");
    }

    @Test
    @DisplayName("Type safety across different generic parameters")
    void testTypeSafetyAcrossDifferentGenerics() {
      Reader<TestEnv, Integer> intReader = Reader.constant(42);
      Reader<TestEnv, String> stringReader = Reader.of(TestEnv::value);
      Reader<String, String> differentEnvReader = Reader.ask();

      readerKindHelper(intReader).test();
      readerKindHelper(stringReader).test();
      CoreTypeTest.readerKindHelper(differentEnvReader).test();
    }

    @Test
    @DisplayName("Complex result values with nested generics")
    void testComplexResultValues() {
      Reader<TestEnv, java.util.List<String>> listReader =
          Reader.constant(java.util.List.of("a", "b", "c"));
      Reader<TestEnv, java.util.Map<String, Integer>> mapReader =
          Reader.constant(java.util.Map.of("key", 42));

      readerKindHelper(listReader).test();
      readerKindHelper(mapReader).test();

      Kind<ReaderKind.Witness<TestEnv>, java.util.List<String>> widened = READER.widen(listReader);
      Reader<TestEnv, java.util.List<String>> narrowed = READER.narrow(widened);

      assertThat(narrowed.run(TEST_ENV)).containsExactly("a", "b", "c");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Holder creates minimal overhead")
    void testMinimalOverhead() {
      Reader<TestEnv, String> original = Reader.of(TestEnv::value);

      readerKindHelper(original).skipPerformance().test();
    }

    @Test
    @DisplayName("Multiple operations are idempotent")
    void testIdempotentOperations() {
      Reader<TestEnv, String> original = Reader.constant("idempotent");

      readerKindHelper(original)
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
        Reader<TestEnv, String> testInstance = Reader.of(env -> env.value() + "-performance");

        readerKindHelper(testInstance).withPerformanceTests().test();
      }
    }

    @Test
    @DisplayName("Memory efficiency test")
    void testMemoryEfficiency() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Reader<TestEnv, String> testInstance = Reader.constant("memory-test");

        readerKindHelper(testInstance).withPerformanceTests().test();
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases and Corner Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("All combinations of null values")
    void testAllNullValueCombinations() {
      Reader<TestEnv, String> nullFromFunction = Reader.of(env -> null);
      Reader<TestEnv, String> nullFromConstant = Reader.constant(null);
      Reader<TestEnv, String> nullFromMap = Reader.<TestEnv>ask().map(env -> (String) null);

      List<Reader<TestEnv, String>> nullInstances =
          List.of(nullFromFunction, nullFromConstant, nullFromMap);

      for (Reader<TestEnv, String> instance : nullInstances) {
        readerKindHelper(instance).test();
      }
    }

    @Test
    @DisplayName("Empty and minimal environments")
    void testEmptyAndMinimalEnvironments() {
      record EmptyEnv() {}

      Reader<EmptyEnv, String> emptyReader = Reader.constant("no-env-needed");

      CoreTypeTest.readerKindHelper(emptyReader).test();

      Kind<ReaderKind.Witness<EmptyEnv>, String> widened =
          ReaderKindHelper.READER.widen(emptyReader);
      Reader<EmptyEnv, String> narrowed = ReaderKindHelper.READER.narrow(widened);

      assertThat(narrowed.run(new EmptyEnv())).isEqualTo("no-env-needed");
    }
  }

  @Nested
  @DisplayName("Advanced Testing Scenarios")
  class AdvancedTestingScenarios {

    @Test
    @DisplayName("Concurrent access test")
    void testConcurrentAccess() {
      if (Boolean.parseBoolean(System.getProperty("test.concurrency", "false"))) {
        Reader<TestEnv, String> testInstance = Reader.of(TestEnv::value);

        readerKindHelper(testInstance).withConcurrencyTests().test();
      }
    }

    @Test
    @DisplayName("Implementation standards validation")
    void testImplementationStandards() {
      org.higherkindedj.hkt.test.patterns.KindHelperTestPattern.validateImplementationStandards(
          Reader.class, ReaderKindHelper.class);
    }

    @Test
    @DisplayName("Quick test for fast test suites")
    void testQuickValidation() {
      Reader<TestEnv, String> testInstance = Reader.<TestEnv>ask().map(TestEnv::value);

      readerKindHelper(testInstance).test();
    }

    @Test
    @DisplayName("Stress test with complex scenarios")
    void testComplexStressScenarios() {
      // Create readers separately with explicit types to avoid type inference issues
      Reader<TestEnv, Object> simpleString = Reader.constant("simple_string");
      Reader<TestEnv, Object> simpleInt = Reader.constant(42);
      Reader<TestEnv, Object> simpleList = Reader.constant(java.util.List.of(1, 2, 3));
      Reader<TestEnv, Object> simpleMap = Reader.constant(java.util.Map.of("key", "value"));
      Reader<TestEnv, Object> fromValue = Reader.of(env -> (Object) env.value());
      Reader<TestEnv, Object> fromAsk = Reader.<TestEnv>ask().map(env -> (Object) env);
      Reader<TestEnv, Object> constantNull = Reader.constant(null);
      Reader<TestEnv, Object> functionNull = Reader.of(env -> null);

      List<Reader<TestEnv, Object>> complexInstances =
          List.of(
              simpleString,
              simpleInt,
              simpleList,
              simpleMap,
              fromValue,
              fromAsk,
              constantNull,
              functionNull);

      for (Reader<TestEnv, Object> instance : complexInstances) {
        readerKindHelper(instance).test();
      }
    }
  }

  @Nested
  @DisplayName("Comprehensive Coverage Tests")
  class ComprehensiveCoverageTests {

    @Test
    @DisplayName("All Reader types and states")
    void testAllReaderTypesAndStates() {
      List<Reader<TestEnv, String>> allStates =
          List.of(
              Reader.of(TestEnv::value),
              Reader.constant("constant"),
              Reader.<TestEnv>ask().map(TestEnv::value), // Add explicit type parameter
              Reader.of(env -> ""),
              Reader.constant(""),
              Reader.of(env -> null),
              Reader.constant(null));

      for (Reader<TestEnv, String> state : allStates) {
        readerKindHelper(state).test();
      }
    }

    @Test
    @DisplayName("Full lifecycle test")
    void testFullLifecycle() {
      Reader<TestEnv, String> original = Reader.of(env -> env.value() + "-lifecycle");

      readerKindHelper(original).test();

      Reader<TestEnv, String> constantOriginal = Reader.constant("lifecycle-constant");

      readerKindHelper(constantOriginal).test();
    }

    @Test
    @DisplayName("Composition and transformation preservation")
    void testCompositionPreservation() {
      Reader<TestEnv, String> base = Reader.of(TestEnv::value);
      Reader<TestEnv, String> composed =
          base.map(String::toUpperCase).flatMap(s -> Reader.constant(s + "!"));

      Kind<ReaderKind.Witness<TestEnv>, String> widened = READER.widen(composed);
      Reader<TestEnv, String> narrowed = READER.narrow(widened);

      assertThat(narrowed.run(TEST_ENV)).isEqualTo("TEST-ENVIRONMENT!");
    }
  }

  @Nested
  @DisplayName("Helper Method Tests")
  class HelperMethodTests {

    @Test
    @DisplayName("reader() factory method works correctly")
    void testReaderFactoryMethod() {
      java.util.function.Function<TestEnv, String> func = TestEnv::value;
      Kind<ReaderKind.Witness<TestEnv>, String> kind = READER.reader(func);

      assertThat(kind).isNotNull();
      Reader<TestEnv, String> narrowed = READER.narrow(kind);
      assertThat(narrowed.run(TEST_ENV)).isEqualTo("test-environment");
    }

    @Test
    @DisplayName("constant() factory method works correctly")
    void testConstantFactoryMethod() {
      Kind<ReaderKind.Witness<TestEnv>, String> kind = READER.constant("fixed");

      assertThat(kind).isNotNull();
      Reader<TestEnv, String> narrowed = READER.narrow(kind);
      assertThat(narrowed.run(TEST_ENV)).isEqualTo("fixed");
      assertThat(narrowed.run(new TestEnv("other"))).isEqualTo("fixed");
    }

    @Test
    @DisplayName("ask() factory method works correctly")
    void testAskFactoryMethod() {
      Kind<ReaderKind.Witness<TestEnv>, TestEnv> kind = READER.ask();

      assertThat(kind).isNotNull();
      Reader<TestEnv, TestEnv> narrowed = READER.narrow(kind);
      assertThat(narrowed.run(TEST_ENV)).isSameAs(TEST_ENV);
    }

    @Test
    @DisplayName("runReader() executes correctly")
    void testRunReaderMethod() {
      Reader<TestEnv, String> reader = Reader.of(TestEnv::value);
      Kind<ReaderKind.Witness<TestEnv>, String> kind = READER.widen(reader);

      String result = READER.runReader(kind, TEST_ENV);
      assertThat(result).isEqualTo("test-environment");
    }

    @Test
    @DisplayName("runReader() validates null Kind")
    void testRunReaderValidatesNullKind() {
      assertThatThrownBy(() -> READER.runReader(null, TEST_ENV))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("widen() validates null input")
    void testWidenValidatesNull() {
      assertThatThrownBy(() -> READER.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Input Reader cannot be null");
    }

    @Test
    @DisplayName("narrow() validates null input")
    void testNarrowValidatesNull() {
      assertThatThrownBy(() -> READER.narrow(null)).hasMessageContaining("null");
    }

    @Test
    @DisplayName("narrow() validates invalid Kind type")
    void testNarrowValidatesInvalidType() {
      Kind<ReaderKind.Witness<TestEnv>, String> invalidKind =
          new Kind<ReaderKind.Witness<TestEnv>, String>() {};

      assertThatThrownBy(() -> READER.narrow(invalidKind)).hasMessageContaining("Reader");
    }

    @Test
    @DisplayName("reader() factory validates null function")
    void testReaderFactoryValidatesNull() {
      assertThatThrownBy(() -> READER.reader(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("runFunction");
    }
  }
}
