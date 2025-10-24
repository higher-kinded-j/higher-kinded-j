// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.reader.ReaderAssert.assertThatReader;

import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Reader core functionality test using standardised patterns.
 *
 * <p>This test focuses on the core Reader functionality whilst using the standardised validation
 * framework for consistent error handling.
 */
@DisplayName("Reader<R, A> Core Functionality - Standardised Test Suite")
class ReaderTest extends ReaderTestBase {

    private final Reader<TestConfig, String> getUrlReader = urlReader();
    private final Reader<TestConfig, Integer> getMaxConnectionsReader = maxConnectionsReader();
    private final Reader<TestConfig, String> urlNullReader = readerOf(cfg -> null);

    // Type class testing fixtures
    private ReaderMonad<TestConfig> monad;
    private ReaderFunctor<TestConfig> functor;

    @BeforeEach
    void setUpReader() {
        monad = ReaderMonad.instance();
        functor = new ReaderFunctor<>();
    }

    @Nested
    @DisplayName("Complete Type Class Test Suite")
    class CompleteTypeClassTestSuite {

        @Test
        @DisplayName("Run complete Monad test pattern")
        void runCompleteMonadTestPattern() {
            TypeClassTest.<ReaderKind.Witness<TestConfig>>monad(ReaderMonad.class)
                    .<Integer>instance(monad)
                    .<String>withKind(validKind)
                    .withMonadOperations(
                            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
                    .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
                    .configureValidation()
                    .useInheritanceValidation()
                    .withMapFrom(ReaderFunctor.class)
                    .withApFrom(ReaderApplicative.class)
                    .withFlatMapFrom(ReaderMonad.class)
                    .selectTests()
                    .skipExceptions() //  Reader has lazy evaluation
                    .test();
        }

        @Test
        @DisplayName("Run complete Functor test pattern")
        void runCompleteFunctorTestPattern() {
            TypeClassTest.<ReaderKind.Witness<TestConfig>>functor(ReaderFunctor.class)
                    .<Integer>instance(functor)
                    .<String>withKind(validKind)
                    .withMapper(validMapper)
                    .withSecondMapper(secondMapper)
                    .withEqualityChecker(equalityChecker)
                    .selectTests()
                    .skipExceptions() //  Reader has lazy evaluation
                    .test();
        }
    }

    @Nested
    @DisplayName("Core Type Testing with CoreTypeTest API")
    class CoreTypeTestingSuite {

        @Test
        @DisplayName("Test all Reader core operations")
        void testAllReaderCoreOperations() {
            CoreTypeTest.<TestConfig, String>reader(Reader.class)
                    .withReader(getUrlReader)
                    .withEnvironment(TEST_CONFIG)
                    .withMappers(String::toUpperCase)
                    .testAll();
        }

        @Test
        @DisplayName("Test Reader with validation configuration")
        void testReaderWithValidationConfiguration() {
            CoreTypeTest.<TestConfig, Integer>reader(Reader.class)
                    .withReader(getMaxConnectionsReader)
                    .withEnvironment(TEST_CONFIG)
                    .withMappers(i -> "MaxConnections: " + i)
                    .configureValidation()
                    .useInheritanceValidation()
                    .withMapFrom(ReaderFunctor.class)
                    .withFlatMapFrom(ReaderMonad.class)
                    .testAll();
        }

        @Test
        @DisplayName("Test Reader selective operations")
        void testReaderSelectiveOperations() {
            CoreTypeTest.<TestConfig, String>reader(Reader.class)
                    .withReader(getUrlReader)
                    .withEnvironment(TEST_CONFIG)
                    .withMappers(String::toLowerCase)
                    .onlyFactoryMethods()
                    .testAll();
        }

        @Test
        @DisplayName("Test Reader operations only")
        void testReaderOperationsOnly() {
            CoreTypeTest.<TestConfig, String>reader(Reader.class)
                    .withReader(getUrlReader)
                    .withEnvironment(TEST_CONFIG)
                    .withMappers(String::toUpperCase)
                    .skipValidations()
                    .skipEdgeCases()
                    .testAll();
        }

        @Test
        @DisplayName("Test Reader validations only")
        void testReaderValidationsOnly() {
            CoreTypeTest.<TestConfig, String>reader(Reader.class)
                    .withReader(getUrlReader)
                    .withEnvironment(TEST_CONFIG)
                    .withMappers(String::toUpperCase)
                    .onlyValidations()
                    .testAll();
        }
    }

    @Nested
    @DisplayName("Factory Methods - Complete Coverage")
    class FactoryMethods {

        @Test
        @DisplayName("of() creates correct Reader instances with all value types")
        void ofCreatesCorrectInstances() {
            // Non-null values
            Reader<TestConfig, String> urlReader = Reader.of(TestConfig::url);
            assertThatReader(urlReader)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_URL);

            // Null returning function
            Reader<TestConfig, String> nullReader = Reader.of(cfg -> null);
            assertThatReader(nullReader)
                    .whenRunWith(TEST_CONFIG)
                    .producesNull();

            // Complex types
            Reader<TestConfig, Integer> maxConnectionsReader = Reader.of(TestConfig::maxConnections);
            assertThatReader(maxConnectionsReader)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_MAX_CONNECTIONS);

            // Function composition
            Reader<TestConfig, String> composedReader =
                    Reader.of(cfg -> cfg.url() + ":" + cfg.maxConnections());
            assertThatReader(composedReader)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_URL + ":" + DEFAULT_MAX_CONNECTIONS);
        }

        @Test
        @DisplayName("constant() creates correct Reader instances with all value types")
        void constantCreatesCorrectInstances() {
            // Non-null values
            Reader<TestConfig, Integer> constantInt = Reader.constant(42);
            assertThatReader(constantInt)
                    .whenRunWith(TEST_CONFIG)
                    .produces(42);
            assertThatReader(constantInt)
                    .whenRunWith(ALTERNATIVE_CONFIG)
                    .produces(42);
            assertThatReader(constantInt)
                    .isConstantFor(TEST_CONFIG, ALTERNATIVE_CONFIG);

            // Null values
            Reader<TestConfig, String> constantNull = Reader.constant(null);
            assertThatReader(constantNull)
                    .whenRunWith(TEST_CONFIG)
                    .producesNull();

            // Complex types
            TestConfig constantConfig = new TestConfig("constant", 999);
            Reader<TestConfig, TestConfig> constantComplex = Reader.constant(constantConfig);
            assertThatReader(constantComplex)
                    .whenRunWith(TEST_CONFIG)
                    .satisfies(result -> assertThat(result).isSameAs(constantConfig));
            assertThatReader(constantComplex)
                    .whenRunWith(ALTERNATIVE_CONFIG)
                    .satisfies(result -> assertThat(result).isSameAs(constantConfig));
        }

        @Test
        @DisplayName("ask() creates correct Reader returning environment")
        void askCreatesCorrectInstances() {
            Reader<TestConfig, TestConfig> askReader = Reader.ask();

            assertThatReader(askReader)
                    .whenRunWith(TEST_CONFIG)
                    .satisfies(result -> assertThat(result).isSameAs(TEST_CONFIG));
            assertThatReader(askReader)
                    .whenRunWith(ALTERNATIVE_CONFIG)
                    .satisfies(result -> assertThat(result).isSameAs(ALTERNATIVE_CONFIG));

            // Can be used to extract parts of environment
            Reader<TestConfig, String> extractUrl = Reader.<TestConfig>ask().map(TestConfig::url);
            assertThatReader(extractUrl)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_URL);
        }

        @Test
        @DisplayName("Factory methods type inference works correctly")
        void factoryMethodsTypeInference() {
            // Test that type inference works with explicit type parameters where needed
            var urlReader = Reader.of((TestConfig cfg) -> cfg.url());
            var constantReader = Reader.<TestConfig, String>constant("fixed");
            var askReader = Reader.<TestConfig>ask();

            // Should be able to assign to properly typed variables
            Reader<TestConfig, String> urlAssignment = urlReader;
            Reader<TestConfig, String> constantAssignment = constantReader;
            Reader<TestConfig, TestConfig> askAssignment = askReader;

            assertThatReader(urlAssignment)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_URL);
            assertThatReader(constantAssignment)
                    .whenRunWith(TEST_CONFIG)
                    .produces("fixed");
            assertThatReader(askAssignment)
                    .whenRunWith(TEST_CONFIG)
                    .satisfies(result -> assertThat(result).isSameAs(TEST_CONFIG));
        }
    }

    @Nested
    @DisplayName("run() Method - Comprehensive Testing")
    class RunMethodTests {

        @Test
        @DisplayName("run() executes function with correct environment")
        void runExecutesFunctionCorrectly() {
            assertThatReader(getUrlReader)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_URL);
            assertThatReader(getUrlReader)
                    .whenRunWith(ALTERNATIVE_CONFIG)
                    .produces(ALTERNATIVE_URL);

            assertThatReader(getMaxConnectionsReader)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_MAX_CONNECTIONS);
            assertThatReader(getMaxConnectionsReader)
                    .whenRunWith(ALTERNATIVE_CONFIG)
                    .produces(ALTERNATIVE_MAX_CONNECTIONS);
        }

        @Test
        @DisplayName("run() is consistent for pure readers")
        void runIsConsistent() {
            Reader<TestConfig, String> reader = Reader.of(TestConfig::url);

            assertThatReader(reader)
                    .isPureWhenRunWith(TEST_CONFIG);
        }

        @Test
        @DisplayName("run() handles null results correctly")
        void runHandlesNullResults() {
            assertThatReader(urlNullReader)
                    .whenRunWith(TEST_CONFIG)
                    .producesNull();
            assertThatReader(urlNullReader)
                    .whenRunWith(ALTERNATIVE_CONFIG)
                    .producesNull();
        }

        @Test
        @DisplayName("run() passes different environments correctly")
        void runPassesDifferentEnvironments() {
            Reader<TestConfig, String> reader =
                    Reader.of(cfg -> cfg.url() + " with maxConnections " + cfg.maxConnections());

            assertThatReader(reader)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_URL + " with maxConnections " + DEFAULT_MAX_CONNECTIONS);
            assertThatReader(reader)
                    .whenRunWith(ALTERNATIVE_CONFIG)
                    .produces(ALTERNATIVE_URL + " with maxConnections " + ALTERNATIVE_MAX_CONNECTIONS);
        }
    }

    @Nested
    @DisplayName("Advanced Usage Patterns")
    class AdvancedUsagePatterns {

        @Test
        @DisplayName("Reader as functor maintains structure")
        void readerAsFunctorMaintainsStructure() {
            Reader<TestConfig, Integer> start = Reader.of(TestConfig::maxConnections);

            Reader<TestConfig, Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

            double expected = Math.sqrt(DEFAULT_MAX_CONNECTIONS * 2.0 + 0.5);
            assertThatReader(result)
                    .whenRunWith(TEST_CONFIG)
                    .satisfies(d -> assertThat(d).isCloseTo(expected, within(0.001)));
        }

        @Test
        @DisplayName("Reader for dependency injection pattern")
        void readerForDependencyInjection() {
            // Simulate services depending on configuration
            record DatabaseService(String connectionUrl) {
                String query(String sql) {
                    return "Executing on " + connectionUrl + ": " + sql;
                }
            }

            Reader<TestConfig, DatabaseService> getDbService =
                    Reader.of(cfg -> new DatabaseService(cfg.url()));

            Reader<TestConfig, String> executeQuery =
                    getDbService.flatMap(service -> Reader.constant(service.query("SELECT * FROM users")));

            assertThatReader(executeQuery)
                    .whenRunWith(TEST_CONFIG)
                    .satisfies(result -> {
                        assertThat(result).contains(DEFAULT_URL);
                        assertThat(result).contains("SELECT * FROM users");
                    });
        }

        @Test
        @DisplayName("Reader for configuration cascading")
        void readerForConfigurationCascading() {
            // Multiple config layers
            record AppConfig(String env, int maxThreads) {}
            record FullConfig(TestConfig dbConfig, AppConfig appConfig) {}

            Reader<FullConfig, String> getConnectionString =
                    Reader.<FullConfig>ask()
                            .map(
                                    fc ->
                                            fc.dbConfig().url()
                                                    + "?maxConn="
                                                    + fc.dbConfig().maxConnections()
                                                    + "&env="
                                                    + fc.appConfig().env());

            FullConfig fullConfig = new FullConfig(TEST_CONFIG, new AppConfig("production", 100));

            assertThatReader(getConnectionString)
                    .whenRunWith(fullConfig)
                    .produces(DEFAULT_URL + "?maxConn=" + DEFAULT_MAX_CONNECTIONS + "&env=production");
        }

        @Test
        @DisplayName("Reader with resource management patterns")
        void readerWithResourceManagement() {
            record Resource(String name, boolean acquired) {
                Resource acquire() {
                    return new Resource(name, true);
                }

                Resource release() {
                    return new Resource(name, false);
                }
            }

            Reader<TestConfig, Resource> acquireResource =
                    Reader.of(cfg -> new Resource(cfg.url(), false).acquire());

            Reader<TestConfig, String> useResource =
                    acquireResource.flatMap(
                            resource -> {
                                assertThat(resource.acquired()).isTrue();
                                return Reader.constant("Used: " + resource.name());
                            });

            assertThatReader(useResource)
                    .whenRunWith(TEST_CONFIG)
                    .produces("Used: " + DEFAULT_URL);
        }
    }

    @Nested
    @DisplayName("Performance and Memory Characteristics")
    class PerformanceAndMemoryTests {

        @Test
        @DisplayName("Reader operations have predictable performance")
        void readerOperationsHavePredictablePerformance() {
            Reader<TestConfig, String> test = Reader.of(TestConfig::url);

            long start = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                test.map(String::toUpperCase)
                        .flatMap(s -> Reader.constant(s.toLowerCase()))
                        .run(TEST_CONFIG);
            }
            long duration = System.nanoTime() - start;

            // Should complete in reasonable time (less than 100ms for 10k ops)
            assertThat(duration).isLessThan(100_000_000L);
        }

        @Test
        @DisplayName("Reader instances are lightweight")
        void readerInstancesAreLightweight() {
            // Creating many readers should not cause issues
            Reader<TestConfig, String>[] readers = new Reader[1000];
            for (int i = 0; i < readers.length; i++) {
                final int index = i;
                readers[i] = Reader.of(cfg -> cfg.url() + ":" + index);
            }

            // All should work correctly
            for (int i = 0; i < readers.length; i++) {
                final int index = i;
                assertThatReader(readers[i])
                        .whenRunWith(TEST_CONFIG)
                        .produces(DEFAULT_URL + ":" + index);
            }
        }

        @Test
        @DisplayName("Reader composition is stack-safe")
        void readerCompositionIsStackSafe() {
            Reader<TestConfig, Integer> start = Reader.constant(0);

            // Create a long chain
            Reader<TestConfig, Integer> result = start;
            for (int i = 0; i < 1000; i++) {
                result = result.map(x -> x + 1);
            }

            assertThatReader(result)
                    .whenRunWith(TEST_CONFIG)
                    .produces(1000);

            // flatMap chains
            Reader<TestConfig, Integer> flatMapResult = start;
            for (int i = 0; i < 500; i++) {
                flatMapResult = flatMapResult.flatMap(x -> Reader.constant(x + 1));
            }

            assertThatReader(flatMapResult)
                    .whenRunWith(TEST_CONFIG)
                    .produces(500);
        }
    }

    @Nested
    @DisplayName("Type Safety and Variance")
    class TypeSafetyAndVarianceTests {

        @Test
        @DisplayName("Reader maintains type safety across operations")
        void readerMaintainsTypeSafety() {
            Reader<TestConfig, Number> numberReader = Reader.of(cfg -> cfg.maxConnections());
            Reader<TestConfig, Integer> intReader = numberReader.map(Number::intValue);

            assertThatReader(intReader)
                    .whenRunWith(TEST_CONFIG)
                    .produces(DEFAULT_MAX_CONNECTIONS);
        }

        @Test
        @DisplayName("Reader works with complex generic types")
        void readerWorksWithComplexGenericTypes() {
            record ComplexConfig(java.util.List<String> urls, java.util.Map<String, Integer> settings) {}

            Reader<ComplexConfig, Integer> getUrlCount = Reader.of(cfg -> cfg.urls().size());

            Reader<ComplexConfig, java.util.Set<String>> getSettingKeys =
                    Reader.of(cfg -> cfg.settings().keySet());

            ComplexConfig complex =
                    new ComplexConfig(
                            java.util.List.of("url1", "url2", "url3"),
                            java.util.Map.of("timeout", 5000, "retries", 3));

            assertThatReader(getUrlCount)
                    .whenRunWith(complex)
                    .produces(3);
            assertThatReader(getSettingKeys)
                    .whenRunWith(complex)
                    .satisfies(keys -> assertThat(keys).containsExactlyInAnyOrder("timeout", "retries"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditions {

        @Test
        @DisplayName("Reader handles extreme values correctly")
        void readerHandlesExtremeValuesCorrectly() {
            // Very large strings
            String largeString = "x".repeat(10000);
            TestConfig largeConfig = new TestConfig(largeString, Integer.MAX_VALUE);
            Reader<TestConfig, Integer> lengthReader = Reader.of(cfg -> cfg.url().length());

            assertThatReader(lengthReader)
                    .whenRunWith(largeConfig)
                    .produces(10000);

            // Nested computations
            Reader<TestConfig, String> nested =
                    Reader.<TestConfig>ask()
                            .map(TestConfig::url)
                            .flatMap(url -> Reader.of(cfg -> url + ":" + cfg.maxConnections()))
                            .map(String::toUpperCase);

            assertThatReader(nested)
                    .whenRunWith(TEST_CONFIG)
                    .satisfies(result -> assertThat(result).contains(DEFAULT_URL.toUpperCase()));
        }

        @Test
        @DisplayName("Reader maintains referential transparency")
        void readerMaintainsReferentialTransparency() {
            Reader<TestConfig, String> reader = Reader.of(TestConfig::url);

            assertThatReader(reader)
                    .isPureWhenRunWith(TEST_CONFIG);
            assertThatReader(reader)
                    .isPureWhenRunWith(ALTERNATIVE_CONFIG);
        }

        @Test
        @DisplayName("Reader handles side effects predictably")
        void readerHandlesSideEffectsPredictably() {
            AtomicInteger counter = new AtomicInteger(0);

            Reader<TestConfig, Integer> sideEffectReader =
                    Reader.of(
                            cfg -> {
                                counter.incrementAndGet();
                                return cfg.maxConnections();
                            });

            // Each run executes the side effect
            sideEffectReader.run(TEST_CONFIG);
            assertThat(counter.get()).isEqualTo(1);

            sideEffectReader.run(TEST_CONFIG);
            assertThat(counter.get()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Exception Propagation - Reader Specific")
    class ExceptionPropagationTests {

        @Test
        @DisplayName("Reader.map propagates exceptions when run")
        void mapPropagatesExceptionsWhenRun() {
            RuntimeException testException = new RuntimeException("Test exception: map");
            java.util.function.Function<String, Integer> throwingMapper =
                    s -> {
                        throw testException;
                    };

            // Create a reader that will throw when executed
            Reader<TestConfig, String> sourceReader = Reader.of(TestConfig::url);
            Reader<TestConfig, Integer> mappedReader = sourceReader.map(throwingMapper);

            // Exception is only thrown when run() is called
            assertThatThrownBy(() -> mappedReader.run(TEST_CONFIG))
                    .as("map should propagate function exceptions when run")
                    .isSameAs(testException);
        }

        @Test
        @DisplayName("Reader.flatMap propagates exceptions when run")
        void flatMapPropagatesExceptionsWhenRun() {
            RuntimeException testException = new RuntimeException("Test exception: flatMap");
            java.util.function.Function<String, Reader<TestConfig, Integer>> throwingFlatMapper =
                    s -> {
                        throw testException;
                    };

            Reader<TestConfig, String> sourceReader = Reader.of(TestConfig::url);
            Reader<TestConfig, Integer> flatMappedReader = sourceReader.flatMap(throwingFlatMapper);

            assertThatThrownBy(() -> flatMappedReader.run(TEST_CONFIG))
                    .as("flatMap should propagate function exceptions when run")
                    .isSameAs(testException);
        }

        @Test
        @DisplayName("ReaderFunctor.map with throwing mapper executes lazily")
        void functorMapWithThrowingMapperIsLazy() {
            RuntimeException testException = new RuntimeException("Test exception: functor");
            java.util.function.Function<String, Integer> throwingMapper =
                    s -> {
                        throw testException;
                    };

            // Using the Functor interface - should not throw yet
            org.higherkindedj.hkt.Kind<ReaderKind.Witness<TestConfig>, Integer> mapped =
                    functor.map(throwingMapper, ReaderKindHelper.READER.widen(getUrlReader));

            // No exception yet - operation was lazy
            assertThat(mapped).isNotNull();

            // Exception only thrown when we run the reader
            Reader<TestConfig, Integer> reader = ReaderKindHelper.READER.narrow(mapped);
            assertThatThrownBy(() -> reader.run(TEST_CONFIG)).isSameAs(testException);
        }
    }

    @Nested
    @DisplayName("Equality and Hash Code")
    class EqualityTests {

        @Test
        @DisplayName("Reader equality is reference-based")
        void readerEqualityIsReferenceBased() {
            Reader<TestConfig, String> r1 = Reader.of(TestConfig::url);
            Reader<TestConfig, String> r2 = Reader.of(TestConfig::url);
            Reader<TestConfig, String> r3 = r1;

            assertThat(r1).isNotEqualTo(r2); // Different instances
            assertThat(r1).isEqualTo(r3); // Same instance
        }

        @Test
        @DisplayName("Reader behaviour is determined by function")
        void readerBehaviourIsDeterminedByFunction() {
            Reader<TestConfig, String> r1 = Reader.of(TestConfig::url);
            Reader<TestConfig, String> r2 = Reader.of(cfg -> cfg.url());

            // Different function instances but same behaviour
            assertThatReader(r1)
                    .whenRunWith(TEST_CONFIG)
                    .satisfies(result1 -> {
                        String result2 = r2.run(TEST_CONFIG);
                        assertThat(result1).isEqualTo(result2);
                    });
        }
    }
}