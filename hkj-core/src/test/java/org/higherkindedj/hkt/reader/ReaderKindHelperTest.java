// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.reader.ReaderAssert.assertThatReader;
import static org.higherkindedj.hkt.test.api.CoreTypeTest.readerKindHelper;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderKindHelper Complete Test Suite")
class ReaderKindHelperTest extends ReaderTestBase {

    // Helper constant for cleaner type references
    private static final ReaderKindHelper READER = ReaderKindHelper.READER;

    @Nested
    @DisplayName("Complete KindHelper Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete KindHelper test suite for Reader")
        void completeKindHelperTestSuite() {
            Reader<TestConfig, String> validInstance = Reader.of(TestConfig::url);

            readerKindHelper(validInstance).test();
        }

        @Test
        @DisplayName("Complete test suite with multiple Reader types")
        void completeTestSuiteWithMultipleTypes() {
            List<Reader<TestConfig, String>> testInstances =
                    List.of(
                            Reader.of(TestConfig::url),
                            Reader.constant("constant-value"),
                            Reader.<TestConfig>ask().map(TestConfig::url),
                            Reader.of(env -> null));

            for (Reader<TestConfig, String> instance : testInstances) {
                readerKindHelper(instance).test();
            }
        }

        @Test
        @DisplayName("Comprehensive test with implementation validation")
        void comprehensiveTestWithImplementationValidation() {
            Reader<TestConfig, String> validInstance = Reader.of(TestConfig::url);

            readerKindHelper(validInstance).testWithValidation(ReaderKindHelper.class);
        }
    }

    @Nested
    @DisplayName("Individual Component Tests")
    class IndividualComponentTests {

        @Test
        @DisplayName("Test round-trip widen/narrow operations")
        void testRoundTripOperations() {
            Reader<TestConfig, String> validInstance = Reader.of(TestConfig::url);

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
            readerKindHelper(Reader.<TestConfig>ask())
                    .skipRoundTrip()
                    .skipInvalidType()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test invalid Kind type handling")
        void testInvalidKindType() {
            readerKindHelper(Reader.<TestConfig, String>constant("test"))
                    .skipRoundTrip()
                    .skipValidations()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test idempotency of operations")
        void testIdempotency() {
            Reader<TestConfig, String> validInstance = Reader.of(env -> env.url() + "-idempotent");

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
            Reader<TestConfig, String> validInstance = Reader.constant("edge-case");

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
            Reader<TestConfig, String> ofReader = Reader.of(TestConfig::url);
            Reader<TestConfig, String> constantReader = Reader.constant("constant");
            Reader<TestConfig, TestConfig> askReader = Reader.ask();

            readerKindHelper(ofReader).test();
            readerKindHelper(constantReader).test();
            readerKindHelper(askReader).test();
        }

        @Test
        @DisplayName("Null values in Reader are preserved")
        void testNullValuesPreserved() {
            Reader<TestConfig, String> nullReader = Reader.of(env -> null);
            Reader<TestConfig, String> constantNull = Reader.constant(null);

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

            assertThatReader(narrowed)
                    .whenRunWith(complexEnv)
                    .produces("complex");
        }

        @Test
        @DisplayName("Type safety across different generic parameters")
        void testTypeSafetyAcrossDifferentGenerics() {
            Reader<TestConfig, Integer> intReader = Reader.constant(42);
            Reader<TestConfig, String> stringReader = Reader.of(TestConfig::url);
            Reader<String, String> differentEnvReader = Reader.ask();

            readerKindHelper(intReader).test();
            readerKindHelper(stringReader).test();
            CoreTypeTest.readerKindHelper(differentEnvReader).test();
        }

        @Test
        @DisplayName("Complex result values with nested generics")
        void testComplexResultValues() {
            Reader<TestConfig, java.util.List<String>> listReader =
                    Reader.constant(java.util.List.of("a", "b", "c"));
            Reader<TestConfig, java.util.Map<String, Integer>> mapReader =
                    Reader.constant(java.util.Map.of("key", 42));

            readerKindHelper(listReader).test();
            readerKindHelper(mapReader).test();

            Kind<ReaderKind.Witness<TestConfig>, java.util.List<String>> widened = READER.widen(listReader);
            Reader<TestConfig, java.util.List<String>> narrowed = READER.narrow(widened);

            assertThatReader(narrowed)
                    .whenRunWith(TEST_CONFIG)
                    .satisfies(result -> assertThat(result).containsExactly("a", "b", "c"));
        }
    }

    @Nested
    @DisplayName("Performance and Memory Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Holder creates minimal overhead")
        void testMinimalOverhead() {
            Reader<TestConfig, String> original = Reader.of(TestConfig::url);

            readerKindHelper(original).skipPerformance().test();
        }

        @Test
        @DisplayName("Multiple operations are idempotent")
        void testIdempotentOperations() {
            Reader<TestConfig, String> original = Reader.constant("idempotent");

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
                Reader<TestConfig, String> testInstance = Reader.of(env -> env.url() + "-performance");

                readerKindHelper(testInstance).withPerformanceTests().test();
            }
        }

        @Test
        @DisplayName("Memory efficiency test")
        void testMemoryEfficiency() {
            if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
                Reader<TestConfig, String> testInstance = Reader.constant("memory-test");

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
            Reader<TestConfig, String> nullFromFunction = Reader.of(env -> null);
            Reader<TestConfig, String> nullFromConstant = Reader.constant(null);
            Reader<TestConfig, String> nullFromMap = Reader.<TestConfig>ask().map(env -> (String) null);

            List<Reader<TestConfig, String>> nullInstances =
                    List.of(nullFromFunction, nullFromConstant, nullFromMap);

            for (Reader<TestConfig, String> instance : nullInstances) {
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

            assertThatReader(narrowed)
                    .whenRunWith(new EmptyEnv())
                    .produces("no-env-needed");
        }
    }

    @Nested
    @DisplayName("Advanced Testing Scenarios")
    class AdvancedTestingScenarios {

        @Test
        @DisplayName("Concurrent access test")
        void testConcurrentAccess() {
            if (Boolean.parseBoolean(System.getProperty("test.concurrency", "false"))) {
                Reader<TestConfig, String> testInstance = Reader.of(TestConfig::url);

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
            Reader<TestConfig, String> testInstance = Reader.<TestConfig>ask().map(TestConfig::url);

            readerKindHelper(testInstance).test();
        }

        @Test
        @DisplayName("Stress test with complex scenarios")
        void testComplexStressScenarios() {
            // Create readers separately with explicit types to avoid type inference issues
            Reader<TestConfig, Object> simpleString = Reader.constant("simple_string");
            Reader<TestConfig, Object> simpleInt = Reader.constant(42);
            Reader<TestConfig, Object> simpleList = Reader.constant(java.util.List.of(1, 2, 3));
            Reader<TestConfig, Object> simpleMap = Reader.constant(java.util.Map.of("key", "value"));
            Reader<TestConfig, Object> fromValue = Reader.of(env -> (Object) env.url());
            Reader<TestConfig, Object> fromAsk = Reader.<TestConfig>ask().map(env -> (Object) env);
            Reader<TestConfig, Object> constantNull = Reader.constant(null);
            Reader<TestConfig, Object> functionNull = Reader.of(env -> null);

            List<Reader<TestConfig, Object>> complexInstances =
                    List.of(
                            simpleString,
                            simpleInt,
                            simpleList,
                            simpleMap,
                            fromValue,
                            fromAsk,
                            constantNull,
                            functionNull);

            for (Reader<TestConfig, Object> instance : complexInstances) {
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
            List<Reader<TestConfig, String>> allStates =
                    List.of(
                            Reader.of(TestConfig::url),
                            Reader.constant("constant"),
                            Reader.<TestConfig>ask().map(TestConfig::url),
                            Reader.of(env -> ""),
                            Reader.constant(""),
                            Reader.of(env -> null),
                            Reader.constant(null));

            for (Reader<TestConfig, String> state : allStates) {
                readerKindHelper(state).test();
            }
        }

        @Test
        @DisplayName("Full lifecycle test")
        void testFullLifecycle() {
            Reader<TestConfig, String> original = Reader.of(env -> env.url() + "-lifecycle");

            readerKindHelper(original).test();

            Reader<TestConfig, String> constantOriginal = Reader.constant("lifecycle-constant");

            readerKindHelper(constantOriginal).test();
        }

        @Test
        @DisplayName("Composition and transformation preservation")
        void testCompositionPreservation() {
            Reader<TestConfig, String> base = Reader.of(TestConfig::url);
            Reader<TestConfig, String> composed =
                    base.map(String::toUpperCase).flatMap(s -> Reader.constant(s + "!"));

            Kind<ReaderKind.Witness<TestConfig>, String> widened = READER.widen(composed);
            Reader<TestConfig, String> narrowed = READER.narrow(widened);

            assertThatReader(narrowed)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_URL.toUpperCase() + "!");
        }
    }

    @Nested
    @DisplayName("Helper Method Tests")
    class HelperMethodTests {

        @Test
        @DisplayName("reader() factory method works correctly")
        void testReaderFactoryMethod() {
            java.util.function.Function<TestConfig, String> func = TestConfig::url;
            Kind<ReaderKind.Witness<TestConfig>, String> kind = READER.reader(func);

            assertThat(kind).isNotNull();
            Reader<TestConfig, String> narrowed = READER.narrow(kind);
            assertThatReader(narrowed)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_URL);
        }

        @Test
        @DisplayName("constant() factory method works correctly")
        void testConstantFactoryMethod() {
            Kind<ReaderKind.Witness<TestConfig>, String> kind = READER.constant("fixed");

            assertThat(kind).isNotNull();
            Reader<TestConfig, String> narrowed = READER.narrow(kind);
            assertThatReader(narrowed)
                    .whenRunWith(TEST_CONFIG)
                    .produces("fixed");
            assertThatReader(narrowed)
                    .whenRunWith(ALTERNATIVE_CONFIG)
                    .produces("fixed");
            assertThatReader(narrowed)
                    .isConstantFor(TEST_CONFIG, ALTERNATIVE_CONFIG);
        }

        @Test
        @DisplayName("ask() factory method works correctly")
        void testAskFactoryMethod() {
            Kind<ReaderKind.Witness<TestConfig>, TestConfig> kind = READER.ask();

            assertThat(kind).isNotNull();
            Reader<TestConfig, TestConfig> narrowed = READER.narrow(kind);
            assertThatReader(narrowed)
                    .whenRunWith(TEST_CONFIG)
                    .satisfies(result -> assertThat(result).isSameAs(TEST_CONFIG));
        }

        @Test
        @DisplayName("runReader() executes correctly")
        void testRunReaderMethod() {
            Reader<TestConfig, String> reader = Reader.of(TestConfig::url);
            Kind<ReaderKind.Witness<TestConfig>, String> kind = READER.widen(reader);

            String result = READER.runReader(kind, TEST_CONFIG);
            assertThat(result).isEqualTo(DEFAULT_URL);
        }

        @Test
        @DisplayName("runReader() validates null Kind")
        void testRunReaderValidatesNullKind() {
            assertThatThrownBy(() -> READER.runReader(null, TEST_CONFIG))
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
            Kind<ReaderKind.Witness<TestConfig>, String> invalidKind =
                    new Kind<ReaderKind.Witness<TestConfig>, String>() {};

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