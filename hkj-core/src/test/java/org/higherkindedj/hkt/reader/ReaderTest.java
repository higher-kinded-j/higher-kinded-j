// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.reader.ReaderAssert.assertThatReader;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
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
      assertThatReader(urlReader).whenRunWith(TEST_CONFIG).produces(DEFAULT_URL);

      // Null returning function
      Reader<TestConfig, String> nullReader = Reader.of(cfg -> null);
      assertThatReader(nullReader).whenRunWith(TEST_CONFIG).producesNull();

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
      assertThatReader(constantInt).whenRunWith(TEST_CONFIG).produces(42);
      assertThatReader(constantInt).whenRunWith(ALTERNATIVE_CONFIG).produces(42);
      assertThatReader(constantInt).isConstantFor(TEST_CONFIG, ALTERNATIVE_CONFIG);

      // Null values
      Reader<TestConfig, String> constantNull = Reader.constant(null);
      assertThatReader(constantNull).whenRunWith(TEST_CONFIG).producesNull();

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
      assertThatReader(extractUrl).whenRunWith(TEST_CONFIG).produces(DEFAULT_URL);
    }

    @Test
    @DisplayName("unit() creates Reader that returns Unit.INSTANCE")
    void unitCreatesReaderReturningUnitInstance() {
      Reader<TestConfig, Unit> unitReader = Reader.unit();

      assertThatReader(unitReader).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);

      assertThatReader(unitReader).whenRunWith(ALTERNATIVE_CONFIG).produces(Unit.INSTANCE);
    }

    @Test
    @DisplayName("unit() ignores the environment completely")
    void unitIgnoresEnvironment() {
      Reader<TestConfig, Unit> unitReader = Reader.unit();

      Unit result1 = unitReader.run(TEST_CONFIG);
      Unit result2 = unitReader.run(ALTERNATIVE_CONFIG);
      Unit result3 = unitReader.run(new TestConfig("different", 999));

      assertThat(result1).isSameAs(Unit.INSTANCE);
      assertThat(result2).isSameAs(Unit.INSTANCE);
      assertThat(result3).isSameAs(Unit.INSTANCE);
    }

    @Test
    @DisplayName("unit() is referentially transparent")
    void unitIsReferentiallyTransparent() {
      Reader<TestConfig, Unit> unitReader = Reader.unit();

      assertThatReader(unitReader).isPureWhenRunWith(TEST_CONFIG);
      assertThatReader(unitReader).isPureWhenRunWith(ALTERNATIVE_CONFIG);
    }

    @Test
    @DisplayName("unit() can be composed with other operations")
    void unitCanBeComposedWithOtherOperations() {
      Reader<TestConfig, Unit> unitReader = Reader.unit();

      // Map over Unit (though result is still Unit)
      Reader<TestConfig, Unit> mapped = unitReader.map(u -> Unit.INSTANCE);
      assertThatReader(mapped).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);

      // FlatMap with Unit
      Reader<TestConfig, String> flatMapped = unitReader.flatMap(u -> Reader.of(cfg -> cfg.url()));
      assertThatReader(flatMapped).whenRunWith(TEST_CONFIG).produces(DEFAULT_URL);
    }

    @Test
    @DisplayName("unit() toString works correctly")
    void unitToStringWorksCorrectly() {
      Reader<TestConfig, Unit> unitReader = Reader.unit();
      Unit unit = unitReader.run(TEST_CONFIG);

      assertThat(unit.toString()).isEqualTo("()");
    }

    @Test
    @DisplayName("fromConsumer() creates Reader that executes consumer and returns Unit")
    void fromConsumerCreatesReaderExecutingConsumer() {
      AtomicInteger counter = new AtomicInteger(0);

      Reader<TestConfig, Unit> consumerReader =
          Reader.fromConsumer(cfg -> counter.incrementAndGet());

      assertThat(counter.get()).isEqualTo(0); // Not executed yet (lazy)

      Unit result = consumerReader.run(TEST_CONFIG);

      assertThat(result).isEqualTo(Unit.INSTANCE);
      assertThat(counter.get()).isEqualTo(1); // Now executed
    }

    @Test
    @DisplayName("fromConsumer() provides environment to consumer")
    void fromConsumerProvidesEnvironmentToConsumer() {
      AtomicReference<String> capturedUrl = new AtomicReference<>();
      AtomicInteger capturedMaxConnections = new AtomicInteger(-1);

      Reader<TestConfig, Unit> consumerReader =
          Reader.fromConsumer(
              cfg -> {
                capturedUrl.set(cfg.url());
                capturedMaxConnections.set(cfg.maxConnections());
              });

      consumerReader.run(TEST_CONFIG);

      assertThat(capturedUrl.get()).isEqualTo(DEFAULT_URL);
      assertThat(capturedMaxConnections.get()).isEqualTo(DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("fromConsumer() can be used for side effects")
    void fromConsumerCanBeUsedForSideEffects() {
      List<String> log = new ArrayList<>();

      Reader<TestConfig, Unit> loggingReader =
          Reader.fromConsumer(cfg -> log.add("Connecting to " + cfg.url()));

      assertThat(log).isEmpty(); // Not executed yet

      Unit result1 = loggingReader.run(TEST_CONFIG);
      assertThat(result1).isEqualTo(Unit.INSTANCE);
      assertThat(log).containsExactly("Connecting to " + DEFAULT_URL);

      Unit result2 = loggingReader.run(ALTERNATIVE_CONFIG);
      assertThat(result2).isEqualTo(Unit.INSTANCE);
      assertThat(log)
          .containsExactly("Connecting to " + DEFAULT_URL, "Connecting to " + ALTERNATIVE_URL);
    }

    @Test
    @DisplayName("fromConsumer() throws NullPointerException for null consumer")
    void fromConsumerThrowsNullPointerExceptionForNullConsumer() {
      assertThatThrownBy(() -> Reader.fromConsumer(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumer cannot be null");
    }

    @Test
    @DisplayName("fromConsumer() is lazy - consumer not called until run")
    void fromConsumerIsLazyConsumerNotCalledUntilRun() {
      AtomicInteger callCount = new AtomicInteger(0);

      Reader<TestConfig, Unit> consumerReader =
          Reader.fromConsumer(cfg -> callCount.incrementAndGet());

      assertThat(callCount.get()).isEqualTo(0);

      // Create chained operations - still lazy
      Reader<TestConfig, Unit> mapped = consumerReader.map(u -> Unit.INSTANCE);
      assertThat(callCount.get()).isEqualTo(0);

      Reader<TestConfig, String> flatMapped = consumerReader.flatMap(u -> Reader.constant("after"));
      assertThat(callCount.get()).isEqualTo(0);

      // Only when we run does the consumer execute
      flatMapped.run(TEST_CONFIG);
      assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("fromConsumer() can be composed with other Readers")
    void fromConsumerCanBeComposedWithOtherReaders() {
      List<String> log = new ArrayList<>();

      Reader<TestConfig, Unit> setupReader =
          Reader.fromConsumer(cfg -> log.add("Setup: " + cfg.url()));

      Reader<TestConfig, String> workReader =
          setupReader.flatMap(u -> Reader.of(cfg -> "Work with " + cfg.maxConnections()));

      Reader<TestConfig, Unit> teardownReader =
          workReader.flatMap(result -> Reader.fromConsumer(cfg -> log.add("Teardown: " + result)));

      teardownReader.run(TEST_CONFIG);

      assertThat(log)
          .containsExactly(
              "Setup: " + DEFAULT_URL, "Teardown: Work with " + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("fromConsumer() executes consumer every time run is called")
    void fromConsumerExecutesConsumerEveryTimeRunIsCalled() {
      AtomicInteger executionCount = new AtomicInteger(0);

      Reader<TestConfig, Unit> consumerReader =
          Reader.fromConsumer(cfg -> executionCount.incrementAndGet());

      consumerReader.run(TEST_CONFIG);
      assertThat(executionCount.get()).isEqualTo(1);

      consumerReader.run(TEST_CONFIG);
      assertThat(executionCount.get()).isEqualTo(2);

      consumerReader.run(ALTERNATIVE_CONFIG);
      assertThat(executionCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("fromConsumer() with exception propagates when run")
    void fromConsumerWithExceptionPropagatesWhenRun() {
      RuntimeException testException = new RuntimeException("Consumer failed");

      Reader<TestConfig, Unit> failingReader =
          Reader.fromConsumer(
              (TestConfig cfg) -> {
                throw testException;
              });

      // Reader created successfully - no exception during creation
      assertThat(failingReader).isNotNull();

      // Exception thrown when run
      assertThatThrownBy(() -> failingReader.run(TEST_CONFIG)).isSameAs(testException);
    }

    @Test
    @DisplayName("Factory methods type inference works correctly")
    void factoryMethodsTypeInference() {
      // Test that type inference works with explicit type parameters where needed
      var urlReader = Reader.of((TestConfig cfg) -> cfg.url());
      var constantReader = Reader.<TestConfig, String>constant("fixed");
      var askReader = Reader.<TestConfig>ask();
      var unitReader = Reader.<TestConfig>unit();

      // Should be able to assign to properly typed variables
      Reader<TestConfig, String> urlAssignment = urlReader;
      Reader<TestConfig, String> constantAssignment = constantReader;
      Reader<TestConfig, TestConfig> askAssignment = askReader;
      Reader<TestConfig, Unit> unitAssignment = unitReader;

      assertThatReader(urlAssignment).whenRunWith(TEST_CONFIG).produces(DEFAULT_URL);
      assertThatReader(constantAssignment).whenRunWith(TEST_CONFIG).produces("fixed");
      assertThatReader(askAssignment)
          .whenRunWith(TEST_CONFIG)
          .satisfies(result -> assertThat(result).isSameAs(TEST_CONFIG));
      assertThatReader(unitAssignment).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);
    }
  }

  @Nested
  @DisplayName("run() Method - Comprehensive Testing")
  class RunMethodTests {

    @Test
    @DisplayName("run() executes function with correct environment")
    void runExecutesFunctionCorrectly() {
      assertThatReader(getUrlReader).whenRunWith(TEST_CONFIG).produces(DEFAULT_URL);
      assertThatReader(getUrlReader).whenRunWith(ALTERNATIVE_CONFIG).produces(ALTERNATIVE_URL);

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

      assertThatReader(reader).isPureWhenRunWith(TEST_CONFIG);
    }

    @Test
    @DisplayName("run() handles null results correctly")
    void runHandlesNullResults() {
      assertThatReader(urlNullReader).whenRunWith(TEST_CONFIG).producesNull();
      assertThatReader(urlNullReader).whenRunWith(ALTERNATIVE_CONFIG).producesNull();
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
  @DisplayName("asUnit() Instance Method - Complete Coverage")
  class AsUnitInstanceMethodTests {

    @Test
    @DisplayName("asUnit() discards result and returns Unit")
    void asUnitDiscardsResultAndReturnsUnit() {
      Reader<TestConfig, String> urlReader = Reader.of(TestConfig::url);
      Reader<TestConfig, Unit> unitReader = urlReader.asUnit();

      assertThatReader(unitReader).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);

      assertThatReader(unitReader).whenRunWith(ALTERNATIVE_CONFIG).produces(Unit.INSTANCE);
    }

    @Test
    @DisplayName("asUnit() still executes the underlying computation")
    void asUnitStillExecutesUnderlyingComputation() {
      AtomicInteger callCount = new AtomicInteger(0);

      Reader<TestConfig, String> trackingReader =
          Reader.of(
              cfg -> {
                callCount.incrementAndGet();
                return cfg.url();
              });

      Reader<TestConfig, Unit> unitReader = trackingReader.asUnit();

      assertThat(callCount.get()).isEqualTo(0); // Not executed yet

      Unit result = unitReader.run(TEST_CONFIG);

      assertThat(result).isEqualTo(Unit.INSTANCE);
      assertThat(callCount.get()).isEqualTo(1); // Underlying computation executed
    }

    @Test
    @DisplayName("asUnit() preserves side effects")
    void asUnitPreservesSideEffects() {
      List<String> log = new ArrayList<>();

      Reader<TestConfig, String> loggingReader =
          Reader.of(
              cfg -> {
                String url = cfg.url();
                log.add("Accessing: " + url);
                return url;
              });

      Reader<TestConfig, Unit> unitReader = loggingReader.asUnit();

      unitReader.run(TEST_CONFIG);

      assertThat(log).containsExactly("Accessing: " + DEFAULT_URL);
    }

    @Test
    @DisplayName("asUnit() works with different result types")
    void asUnitWorksWithDifferentResultTypes() {
      // Integer result
      Reader<TestConfig, Integer> intReader = Reader.of(TestConfig::maxConnections);
      Reader<TestConfig, Unit> unitFromInt = intReader.asUnit();
      assertThatReader(unitFromInt).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);

      // String result
      Reader<TestConfig, String> stringReader = Reader.of(TestConfig::url);
      Reader<TestConfig, Unit> unitFromString = stringReader.asUnit();
      assertThatReader(unitFromString).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);

      // Complex result
      Reader<TestConfig, TestConfig> configReader = Reader.ask();
      Reader<TestConfig, Unit> unitFromConfig = configReader.asUnit();
      assertThatReader(unitFromConfig).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);
    }

    @Test
    @DisplayName("asUnit() is lazy - computation not executed until run")
    void asUnitIsLazyComputationNotExecutedUntilRun() {
      AtomicInteger executionCount = new AtomicInteger(0);

      Reader<TestConfig, String> trackingReader =
          Reader.of(
              cfg -> {
                executionCount.incrementAndGet();
                return cfg.url();
              });

      Reader<TestConfig, Unit> unitReader = trackingReader.asUnit();

      assertThat(executionCount.get()).isEqualTo(0);

      // Even creating chains doesn't execute
      Reader<TestConfig, Unit> chained = unitReader.map(u -> Unit.INSTANCE);
      assertThat(executionCount.get()).isEqualTo(0);

      // Only when run does it execute
      chained.run(TEST_CONFIG);
      assertThat(executionCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("asUnit() can be composed with other operations")
    void asUnitCanBeComposedWithOtherOperations() {
      Reader<TestConfig, String> urlReader = Reader.of(TestConfig::url);
      Reader<TestConfig, Unit> unitReader = urlReader.asUnit();

      // Map over Unit
      Reader<TestConfig, Unit> mappedUnit = unitReader.map(u -> Unit.INSTANCE);
      assertThatReader(mappedUnit).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);

      // FlatMap with Unit
      Reader<TestConfig, Integer> afterUnit =
          unitReader.flatMap(u -> Reader.of(TestConfig::maxConnections));
      assertThatReader(afterUnit).whenRunWith(TEST_CONFIG).produces(DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("asUnit() executes computation every time run is called")
    void asUnitExecutesComputationEveryTimeRunIsCalled() {
      AtomicInteger executionCount = new AtomicInteger(0);

      Reader<TestConfig, String> trackingReader =
          Reader.of(
              cfg -> {
                executionCount.incrementAndGet();
                return cfg.url();
              });

      Reader<TestConfig, Unit> unitReader = trackingReader.asUnit();

      unitReader.run(TEST_CONFIG);
      assertThat(executionCount.get()).isEqualTo(1);

      unitReader.run(TEST_CONFIG);
      assertThat(executionCount.get()).isEqualTo(2);

      unitReader.run(ALTERNATIVE_CONFIG);
      assertThat(executionCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("asUnit() propagates exceptions from underlying computation")
    void asUnitPropagatesExceptionsFromUnderlyingComputation() {
      RuntimeException testException = new RuntimeException("Computation failed");

      Reader<TestConfig, String> failingReader =
          Reader.of(
              (TestConfig cfg) -> {
                throw testException;
              });

      Reader<TestConfig, Unit> unitReader = failingReader.asUnit();

      // Reader created successfully - no exception during creation or asUnit call
      assertThat(unitReader).isNotNull();

      // Exception thrown when run
      assertThatThrownBy(() -> unitReader.run(TEST_CONFIG)).isSameAs(testException);
    }

    @Test
    @DisplayName("asUnit() handles null results correctly")
    void asUnitHandlesNullResultsCorrectly() {
      Reader<TestConfig, String> nullReader = Reader.constant(null);
      Reader<TestConfig, Unit> unitReader = nullReader.asUnit();

      // Should still return Unit.INSTANCE even though underlying result is null
      assertThatReader(unitReader).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Multiple asUnit calls are idempotent")
    void multipleAsUnitCallsAreIdempotent() {
      Reader<TestConfig, String> urlReader = Reader.of(TestConfig::url);
      Reader<TestConfig, Unit> unit1 = urlReader.asUnit();
      Reader<TestConfig, Unit> unit2 = unit1.asUnit();
      Reader<TestConfig, Unit> unit3 = unit2.asUnit();

      assertThatReader(unit3).whenRunWith(TEST_CONFIG).produces(Unit.INSTANCE);
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
          .satisfies(
              result -> {
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

      assertThatReader(useResource).whenRunWith(TEST_CONFIG).produces("Used: " + DEFAULT_URL);
    }

    @Test
    @DisplayName("Combining unit(), fromConsumer(), and asUnit() in workflow")
    void combiningUnitMethodsInWorkflow() {
      List<String> log = new ArrayList<>();

      // Setup phase using fromConsumer
      Reader<TestConfig, Unit> setup =
          Reader.fromConsumer(cfg -> log.add("Setup with " + cfg.url()));

      // Work phase that produces a result
      Reader<TestConfig, String> work =
          setup.flatMap(u -> Reader.of(cfg -> "Result:" + cfg.maxConnections()));

      // Discard result using asUnit
      Reader<TestConfig, Unit> discardResult = work.asUnit();

      // Teardown phase
      Reader<TestConfig, Unit> teardown =
          discardResult.flatMap(u -> Reader.fromConsumer(cfg -> log.add("Teardown")));

      // Execute the workflow
      teardown.run(TEST_CONFIG);

      assertThat(log).containsExactly("Setup with " + DEFAULT_URL, "Teardown");
    }

    @Test
    @DisplayName("Using unit() as a no-op in conditional logic")
    void usingUnitAsNoOpInConditionalLogic() {
      List<String> actions = new ArrayList<>();

      Reader<TestConfig, Unit> conditionalAction =
          Reader.of(TestConfig::maxConnections)
              .flatMap(
                  maxConn -> {
                    if (maxConn > 5) {
                      return Reader.fromConsumer(cfg -> actions.add("High connections"));
                    } else {
                      return Reader.unit(); // No-op
                    }
                  });

      // With TEST_CONFIG (maxConnections = 10 > 5)
      conditionalAction.run(TEST_CONFIG);
      assertThat(actions).containsExactly("High connections");

      actions.clear();

      // With ALTERNATIVE_CONFIG (maxConnections = 5 <= 5)
      conditionalAction.run(ALTERNATIVE_CONFIG);
      assertThat(actions).isEmpty(); // No action taken
    }

    @Test
    @DisplayName("Building a sequence of side-effecting operations with Unit")
    void buildingSequenceOfSideEffectingOperationsWithUnit() {
      List<String> executionOrder = new ArrayList<>();

      Reader<TestConfig, Unit> operation1 =
          Reader.fromConsumer(cfg -> executionOrder.add("1: Initialise " + cfg.url()));

      Reader<TestConfig, Unit> operation2 =
          operation1.flatMap(
              u ->
                  Reader.fromConsumer(
                      cfg -> executionOrder.add("2: Configure " + cfg.maxConnections())));

      Reader<TestConfig, Unit> operation3 =
          operation2.flatMap(u -> Reader.fromConsumer(cfg -> executionOrder.add("3: Finalize")));

      operation3.run(TEST_CONFIG);

      assertThat(executionOrder)
          .containsExactly(
              "1: Initialise " + DEFAULT_URL,
              "2: Configure " + DEFAULT_MAX_CONNECTIONS,
              "3: Finalize");
    }

    @Test
    @DisplayName("Discarding intermediate results with asUnit in computation chain")
    void discardingIntermediateResultsWithAsUnitInComputationChain() {
      AtomicInteger intermediateValue = new AtomicInteger(-1);

      Reader<TestConfig, String> step1 = Reader.of(TestConfig::url);

      Reader<TestConfig, Unit> step2Discarded =
          step1
              .map(
                  url -> {
                    intermediateValue.set(url.length());
                    return url.length();
                  })
              .asUnit();

      Reader<TestConfig, String> finalStep =
          step2Discarded.flatMap(u -> Reader.of(cfg -> "Final: " + cfg.maxConnections()));

      String result = finalStep.run(TEST_CONFIG);

      // Intermediate value was calculated but result was discarded
      assertThat(intermediateValue.get()).isEqualTo(DEFAULT_URL.length());
      assertThat(result).isEqualTo("Final: " + DEFAULT_MAX_CONNECTIONS);
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("Reader operations complete in reasonable time")
    void readerOperationsCompleteInReasonableTime() {
      Reader<TestConfig, String> test = Reader.of(TestConfig::url);

      // Verify operations complete without hanging (generous timeout)
      // Use JMH benchmarks in hkj-benchmarks module for precise performance measurement
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () -> {
            for (int i = 0; i < 100_000; i++) {
              test.map(String::toUpperCase)
                  .flatMap(s -> Reader.constant(s.toLowerCase()))
                  .run(TEST_CONFIG);
            }
          },
          "Reader operations should complete within reasonable time");
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
        assertThatReader(readers[i]).whenRunWith(TEST_CONFIG).produces(DEFAULT_URL + ":" + index);
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

      assertThatReader(result).whenRunWith(TEST_CONFIG).produces(1000);

      // flatMap chains
      Reader<TestConfig, Integer> flatMapResult = start;
      for (int i = 0; i < 500; i++) {
        flatMapResult = flatMapResult.flatMap(x -> Reader.constant(x + 1));
      }

      assertThatReader(flatMapResult).whenRunWith(TEST_CONFIG).produces(500);
    }

    @Test
    @DisplayName("Unit operations work correctly with concurrent execution")
    void unitOperationsWorkCorrectlyWithConcurrentExecution() throws Exception {
      AtomicInteger executionCount = new AtomicInteger(0);

      Reader<TestConfig, Unit> consumerReader =
          Reader.fromConsumer(cfg -> executionCount.incrementAndGet());

      // Execute concurrently
      int threadCount = 10;
      Thread[] threads = new Thread[threadCount];

      for (int i = 0; i < threadCount; i++) {
        threads[i] = new Thread(() -> consumerReader.run(TEST_CONFIG));
        threads[i].start();
      }

      for (Thread thread : threads) {
        thread.join();
      }

      assertThat(executionCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("asUnit preserves laziness in complex chains")
    void asUnitPreservesLazinessInComplexChains() {
      AtomicInteger stage1Count = new AtomicInteger(0);
      AtomicInteger stage2Count = new AtomicInteger(0);
      AtomicInteger stage3Count = new AtomicInteger(0);

      Reader<TestConfig, String> stage1 =
          Reader.of(
              cfg -> {
                stage1Count.incrementAndGet();
                return cfg.url();
              });

      Reader<TestConfig, Integer> stage2 =
          stage1
              .map(
                  url -> {
                    stage2Count.incrementAndGet();
                    return url.length();
                  })
              .asUnit()
              .flatMap(u -> Reader.of(cfg -> cfg.maxConnections()));

      Reader<TestConfig, String> stage3 =
          stage2.map(
              i -> {
                stage3Count.incrementAndGet();
                return "Final: " + i;
              });

      // Nothing executed yet
      assertThat(stage1Count.get()).isEqualTo(0);
      assertThat(stage2Count.get()).isEqualTo(0);
      assertThat(stage3Count.get()).isEqualTo(0);

      // Execute the entire chain
      stage3.run(TEST_CONFIG);

      // All stages executed exactly once
      assertThat(stage1Count.get()).isEqualTo(1);
      assertThat(stage2Count.get()).isEqualTo(1);
      assertThat(stage3Count.get()).isEqualTo(1);
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

      assertThatReader(intReader).whenRunWith(TEST_CONFIG).produces(DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("Reader works with complex generic types")
    void readerWorksWithComplexGenericTypes() {
      record ComplexConfig(List<String> urls, Map<String, Integer> settings) {}

      Reader<ComplexConfig, Integer> getUrlCount = Reader.of(cfg -> cfg.urls().size());

      Reader<ComplexConfig, Set<String>> getSettingKeys = Reader.of(cfg -> cfg.settings().keySet());

      ComplexConfig complex =
          new ComplexConfig(List.of("url1", "url2", "url3"), Map.of("timeout", 5000, "retries", 3));

      assertThatReader(getUrlCount).whenRunWith(complex).produces(3);
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

      assertThatReader(lengthReader).whenRunWith(largeConfig).produces(10000);

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

      assertThatReader(reader).isPureWhenRunWith(TEST_CONFIG);
      assertThatReader(reader).isPureWhenRunWith(ALTERNATIVE_CONFIG);
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

    @Test
    @DisplayName("fromConsumer with consumer that modifies mutable environment state")
    void fromConsumerWithConsumerThatModifiesMutableEnvironmentState() {
      // Note: This tests that Reader correctly passes the environment reference
      // In practice, environments should be immutable
      record MutableConfig(String url, AtomicInteger counter) {}

      MutableConfig config = new MutableConfig(DEFAULT_URL, new AtomicInteger(0));

      Reader<MutableConfig, Unit> mutatingReader =
          Reader.fromConsumer(cfg -> cfg.counter().incrementAndGet());

      mutatingReader.run(config);
      assertThat(config.counter().get()).isEqualTo(1);

      mutatingReader.run(config);
      assertThat(config.counter().get()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Exception Propagation - Reader Specific")
  class ExceptionPropagationTests {

    @Test
    @DisplayName("Reader.map propagates exceptions when run")
    void mapPropagatesExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: map");
      Function<String, Integer> throwingMapper =
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
      Function<String, Reader<TestConfig, Integer>> throwingFlatMapper =
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
      Function<String, Integer> throwingMapper =
          s -> {
            throw testException;
          };

      // Using the Functor interface - should not throw yet
      Kind<ReaderKind.Witness<TestConfig>, Integer> mapped =
          functor.map(throwingMapper, ReaderKindHelper.READER.widen(getUrlReader));

      // No exception yet - operation was lazy
      assertThat(mapped).isNotNull();

      // Exception only thrown when we run the reader
      Reader<TestConfig, Integer> reader = ReaderKindHelper.READER.narrow(mapped);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG)).isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Real-World Use Cases with Unit Methods")
  class RealWorldUseCases {

    @Test
    @DisplayName("Using fromConsumer for logging without affecting return value")
    void usingFromConsumerForLoggingWithoutAffectingReturnValue() {
      List<String> log = new ArrayList<>();

      Reader<TestConfig, String> businessLogic =
          Reader.<TestConfig>fromConsumer(
                  (TestConfig cfg) -> log.add("Starting operation for " + cfg.url()))
              .flatMap(u -> Reader.of((TestConfig cfg) -> "Processing " + cfg.maxConnections()))
              .map(
                  result -> {
                    log.add("Completed: " + result);
                    return result;
                  });

      String result = businessLogic.run(TEST_CONFIG);

      assertThat(result).isEqualTo("Processing " + DEFAULT_MAX_CONNECTIONS);
      assertThat(log)
          .containsExactly(
              "Starting operation for " + DEFAULT_URL,
              "Completed: Processing " + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("Using asUnit to discard validation results in pipeline")
    void usingAsUnitToDiscardValidationResultsInPipeline() {
      List<String> errors = new ArrayList<>();

      Reader<TestConfig, Boolean> validate =
          Reader.of(
              (TestConfig cfg) -> {
                boolean valid = cfg.maxConnections() > 0 && cfg.maxConnections() <= 100;
                if (!valid) {
                  errors.add("Invalid max connections: " + cfg.maxConnections());
                }
                return valid;
              });

      Reader<TestConfig, String> processAfterValidation =
          validate.asUnit().flatMap(u -> Reader.of((TestConfig cfg) -> "Processed: " + cfg.url()));

      String result = processAfterValidation.run(TEST_CONFIG);

      assertThat(result).isEqualTo("Processed: " + DEFAULT_URL);
      assertThat(errors).isEmpty();

      // Test with invalid config
      errors.clear();
      TestConfig invalidConfig = new TestConfig(DEFAULT_URL, 150);
      String result2 = processAfterValidation.run(invalidConfig);

      assertThat(result2).isEqualTo("Processed: " + DEFAULT_URL);
      assertThat(errors).containsExactly("Invalid max connections: 150");
    }

    @Test
    @DisplayName("Using unit() and fromConsumer for resource cleanup pattern")
    void usingUnitAndFromConsumerForResourceCleanupPattern() {
      List<String> lifecycle = new ArrayList<>();

      // Acquire resource
      Reader<TestConfig, String> acquireResource =
          Reader.<TestConfig>fromConsumer(
                  (TestConfig cfg) -> lifecycle.add("Acquiring resource for " + cfg.url()))
              .flatMap(u -> Reader.constant("Resource-Handle-123"));

      // Use resource
      Reader<TestConfig, String> useResource =
          acquireResource.map(
              handle -> {
                lifecycle.add("Using " + handle);
                return "Result from " + handle;
              });

      // Release resource (discarding the result)
      Reader<TestConfig, Unit> releaseResource =
          useResource
              .asUnit()
              .flatMap(
                  u ->
                      Reader.<TestConfig>fromConsumer(
                          (TestConfig cfg) -> lifecycle.add("Releasing resource")));

      releaseResource.run(TEST_CONFIG);

      assertThat(lifecycle)
          .containsExactly(
              "Acquiring resource for " + DEFAULT_URL,
              "Using Resource-Handle-123",
              "Releasing resource");
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
          .satisfies(
              result1 -> {
                String result2 = r2.run(TEST_CONFIG);
                assertThat(result1).isEqualTo(result2);
              });
    }
  }
}
