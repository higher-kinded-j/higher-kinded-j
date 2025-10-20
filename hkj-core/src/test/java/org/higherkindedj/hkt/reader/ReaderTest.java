// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
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
class ReaderTest
    extends TypeClassTestBase<ReaderKind.Witness<ReaderTest.Config>, ReaderTest.Config, String> {

  // Simple environment type for testing
  public record Config(String dbUrl, int timeout) {}

  private final Config testConfig = new Config("jdbc:test", 5000);
  private final Config alternativeConfig = new Config("jdbc:alternative", 3000);

  private final Reader<Config, String> getUrlReader = Reader.of(Config::dbUrl);
  private final Reader<Config, Integer> getTimeoutReader = Reader.of(Config::timeout);
  private final Reader<Config, String> urlNullReader = Reader.of(cfg -> null);

  // Type class testing fixtures
  private ReaderMonad<Config> monad;
  private ReaderFunctor<Config> functor;

  @Override
  protected Kind<ReaderKind.Witness<Config>, Config> createValidKind() {
    return ReaderKindHelper.READER.widen(Reader.ask());
  }

  @Override
  protected Kind<ReaderKind.Witness<Config>, Config> createValidKind2() {
    return ReaderKindHelper.READER.widen(Reader.of(cfg -> cfg));
  }

  @Override
  protected Function<Config, String> createValidMapper() {
    return cfg -> cfg.dbUrl();
  }

  @Override
  protected BiPredicate<Kind<ReaderKind.Witness<Config>, ?>, Kind<ReaderKind.Witness<Config>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      Reader<Config, ?> r1 = ReaderKindHelper.READER.narrow(k1);
      Reader<Config, ?> r2 = ReaderKindHelper.READER.narrow(k2);
      // For Reader, we compare results when run with test config
      return r1.run(testConfig).equals(r2.run(testConfig));
    };
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> s.toUpperCase();
  }

  @Override
  protected Function<Config, Kind<ReaderKind.Witness<Config>, String>> createValidFlatMapper() {
    return cfg -> ReaderKindHelper.READER.widen(Reader.of(c -> cfg.dbUrl() + ":" + c.timeout()));
  }

  @Override
  protected Kind<ReaderKind.Witness<Config>, Function<Config, String>> createValidFunctionKind() {
    return ReaderKindHelper.READER.widen(Reader.of(cfg -> Config::dbUrl));
  }

  @Override
  protected BiFunction<Config, Config, String> createValidCombiningFunction() {
    return (c1, c2) -> c1.dbUrl() + "+" + c2.dbUrl();
  }

  @Override
  protected Config createTestValue() {
    return testConfig;
  }

  @Override
  protected Function<Config, Kind<ReaderKind.Witness<Config>, String>> createTestFunction() {
    return cfg -> ReaderKindHelper.READER.widen(Reader.of(c -> cfg.dbUrl()));
  }

  @Override
  protected Function<String, Kind<ReaderKind.Witness<Config>, String>> createChainFunction() {
    return s -> ReaderKindHelper.READER.widen(Reader.constant(s + "!"));
  }

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
      TypeClassTest.<ReaderKind.Witness<Config>>monad(ReaderMonad.class)
          .<Config>instance(monad)
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
      TypeClassTest.<ReaderKind.Witness<Config>>functor(ReaderFunctor.class)
          .<Config>instance(functor)
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
      CoreTypeTest.<Config, String>reader(Reader.class)
          .withReader(getUrlReader)
          .withEnvironment(testConfig)
          .withMappers(String::toUpperCase)
          .testAll();
    }

    @Test
    @DisplayName("Test Reader with validation configuration")
    void testReaderWithValidationConfiguration() {
      CoreTypeTest.<Config, Integer>reader(Reader.class)
          .withReader(getTimeoutReader)
          .withEnvironment(testConfig)
          .withMappers(i -> "Timeout: " + i)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ReaderFunctor.class)
          .withFlatMapFrom(ReaderMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Test Reader selective operations")
    void testReaderSelectiveOperations() {
      CoreTypeTest.<Config, String>reader(Reader.class)
          .withReader(getUrlReader)
          .withEnvironment(testConfig)
          .withMappers(String::toLowerCase)
          .onlyFactoryMethods()
          .testAll();
    }

    @Test
    @DisplayName("Test Reader operations only")
    void testReaderOperationsOnly() {
      CoreTypeTest.<Config, String>reader(Reader.class)
          .withReader(getUrlReader)
          .withEnvironment(testConfig)
          .withMappers(String::toUpperCase)
          .skipValidations()
          .skipEdgeCases()
          .testAll();
    }

    @Test
    @DisplayName("Test Reader validations only")
    void testReaderValidationsOnly() {
      CoreTypeTest.<Config, String>reader(Reader.class)
          .withReader(getUrlReader)
          .withEnvironment(testConfig)
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
      Reader<Config, String> urlReader = Reader.of(Config::dbUrl);
      assertThat(urlReader).isNotNull();
      assertThat(urlReader.run(testConfig)).isEqualTo("jdbc:test");

      // Null returning function
      Reader<Config, String> nullReader = Reader.of(cfg -> null);
      assertThat(nullReader.run(testConfig)).isNull();

      // Complex types
      Reader<Config, Integer> timeoutReader = Reader.of(Config::timeout);
      assertThat(timeoutReader.run(testConfig)).isEqualTo(5000);

      // Function composition
      Reader<Config, String> composedReader = Reader.of(cfg -> cfg.dbUrl() + ":" + cfg.timeout());
      assertThat(composedReader.run(testConfig)).isEqualTo("jdbc:test:5000");
    }

    @Test
    @DisplayName("constant() creates correct Reader instances with all value types")
    void constantCreatesCorrectInstances() {
      // Non-null values
      Reader<Config, Integer> constantInt = Reader.constant(42);
      assertThat(constantInt.run(testConfig)).isEqualTo(42);
      assertThat(constantInt.run(alternativeConfig)).isEqualTo(42);

      // Null values
      Reader<Config, String> constantNull = Reader.constant(null);
      assertThat(constantNull.run(testConfig)).isNull();

      // Complex types
      Config constantConfig = new Config("constant", 999);
      Reader<Config, Config> constantComplex = Reader.constant(constantConfig);
      assertThat(constantComplex.run(testConfig)).isSameAs(constantConfig);
      assertThat(constantComplex.run(alternativeConfig)).isSameAs(constantConfig);
    }

    @Test
    @DisplayName("ask() creates correct Reader returning environment")
    void askCreatesCorrectInstances() {
      Reader<Config, Config> askReader = Reader.ask();

      assertThat(askReader.run(testConfig)).isSameAs(testConfig);
      assertThat(askReader.run(alternativeConfig)).isSameAs(alternativeConfig);

      // Can be used to extract parts of environment
      Reader<Config, String> extractUrl = Reader.<Config>ask().map(Config::dbUrl);
      assertThat(extractUrl.run(testConfig)).isEqualTo("jdbc:test");
    }

    @Test
    @DisplayName("Factory methods type inference works correctly")
    void factoryMethodsTypeInference() {
      // Test that type inference works with explicit type parameters where needed
      var urlReader = Reader.of((Config cfg) -> cfg.dbUrl());
      var constantReader = Reader.<Config, String>constant("fixed");
      var askReader = Reader.<Config>ask();

      // Should be able to assign to properly typed variables
      Reader<Config, String> urlAssignment = urlReader;
      Reader<Config, String> constantAssignment = constantReader;
      Reader<Config, Config> askAssignment = askReader;

      assertThat(urlAssignment.run(testConfig)).isEqualTo("jdbc:test");
      assertThat(constantAssignment.run(testConfig)).isEqualTo("fixed");
      assertThat(askAssignment.run(testConfig)).isSameAs(testConfig);
    }
  }

  @Nested
  @DisplayName("run() Method - Comprehensive Testing")
  class RunMethodTests {

    @Test
    @DisplayName("run() executes function with correct environment")
    void runExecutesFunctionCorrectly() {
      assertThat(getUrlReader.run(testConfig)).isEqualTo("jdbc:test");
      assertThat(getUrlReader.run(alternativeConfig)).isEqualTo("jdbc:alternative");

      assertThat(getTimeoutReader.run(testConfig)).isEqualTo(5000);
      assertThat(getTimeoutReader.run(alternativeConfig)).isEqualTo(3000);
    }

    @Test
    @DisplayName("run() is consistent for pure readers")
    void runIsConsistent() {
      Reader<Config, String> reader = Reader.of(Config::dbUrl);

      String result1 = reader.run(testConfig);
      String result2 = reader.run(testConfig);

      assertThat(result1).isEqualTo(result2);
    }

    @Test
    @DisplayName("run() handles null results correctly")
    void runHandlesNullResults() {
      assertThat(urlNullReader.run(testConfig)).isNull();
      assertThat(urlNullReader.run(alternativeConfig)).isNull();
    }

    @Test
    @DisplayName("run() passes different environments correctly")
    void runPassesDifferentEnvironments() {
      Reader<Config, String> reader =
          Reader.of(cfg -> cfg.dbUrl() + " with timeout " + cfg.timeout());

      String result1 = reader.run(testConfig);
      String result2 = reader.run(alternativeConfig);

      assertThat(result1).isEqualTo("jdbc:test with timeout 5000");
      assertThat(result2).isEqualTo("jdbc:alternative with timeout 3000");
    }
  }

  @Nested
  @DisplayName("Advanced Usage Patterns")
  class AdvancedUsagePatterns {

    @Test
    @DisplayName("Reader as functor maintains structure")
    void readerAsFunctorMaintainsStructure() {
      Reader<Config, Integer> start = Reader.of(Config::timeout);

      Reader<Config, Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

      assertThat(result.run(testConfig)).isCloseTo(Math.sqrt(10000.5), within(0.001));
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

      Reader<Config, DatabaseService> getDbService =
          Reader.of(cfg -> new DatabaseService(cfg.dbUrl()));

      Reader<Config, String> executeQuery =
          getDbService.flatMap(service -> Reader.constant(service.query("SELECT * FROM users")));

      String result = executeQuery.run(testConfig);
      assertThat(result).contains("jdbc:test");
      assertThat(result).contains("SELECT * FROM users");
    }

    @Test
    @DisplayName("Reader for configuration cascading")
    void readerForConfigurationCascading() {
      // Multiple config layers
      record AppConfig(String env, int maxConnections) {}
      record FullConfig(Config dbConfig, AppConfig appConfig) {}

      Reader<FullConfig, String> getConnectionString =
          Reader.<FullConfig>ask()
              .map(
                  fc ->
                      fc.dbConfig().dbUrl()
                          + "?maxConn="
                          + fc.appConfig().maxConnections()
                          + "&env="
                          + fc.appConfig().env());

      FullConfig fullConfig = new FullConfig(testConfig, new AppConfig("production", 100));

      String result = getConnectionString.run(fullConfig);
      assertThat(result).isEqualTo("jdbc:test?maxConn=100&env=production");
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

      Reader<Config, Resource> acquireResource =
          Reader.of(cfg -> new Resource(cfg.dbUrl(), false).acquire());

      Reader<Config, String> useResource =
          acquireResource.flatMap(
              resource -> {
                assertThat(resource.acquired()).isTrue();
                return Reader.constant("Used: " + resource.name());
              });

      String result = useResource.run(testConfig);
      assertThat(result).isEqualTo("Used: jdbc:test");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("Reader operations have predictable performance")
    void readerOperationsHavePredictablePerformance() {
      Reader<Config, String> test = Reader.of(Config::dbUrl);

      long start = System.nanoTime();
      for (int i = 0; i < 10000; i++) {
        test.map(String::toUpperCase)
            .flatMap(s -> Reader.constant(s.toLowerCase()))
            .run(testConfig);
      }
      long duration = System.nanoTime() - start;

      // Should complete in reasonable time (less than 100ms for 10k ops)
      assertThat(duration).isLessThan(100_000_000L);
    }

    @Test
    @DisplayName("Reader instances are lightweight")
    void readerInstancesAreLightweight() {
      // Creating many readers should not cause issues
      Reader<Config, String>[] readers = new Reader[1000];
      for (int i = 0; i < readers.length; i++) {
        final int index = i;
        readers[i] = Reader.of(cfg -> cfg.dbUrl() + ":" + index);
      }

      // All should work correctly
      for (int i = 0; i < readers.length; i++) {
        String result = readers[i].run(testConfig);
        assertThat(result).isEqualTo("jdbc:test:" + i);
      }
    }

    @Test
    @DisplayName("Reader composition is stack-safe")
    void readerCompositionIsStackSafe() {
      Reader<Config, Integer> start = Reader.constant(0);

      // Create a long chain
      Reader<Config, Integer> result = start;
      for (int i = 0; i < 1000; i++) {
        result = result.map(x -> x + 1);
      }

      assertThat(result.run(testConfig)).isEqualTo(1000);

      // flatMap chains
      Reader<Config, Integer> flatMapResult = start;
      for (int i = 0; i < 500; i++) {
        flatMapResult = flatMapResult.flatMap(x -> Reader.constant(x + 1));
      }

      assertThat(flatMapResult.run(testConfig)).isEqualTo(500);
    }
  }

  @Nested
  @DisplayName("Type Safety and Variance")
  class TypeSafetyAndVarianceTests {

    @Test
    @DisplayName("Reader maintains type safety across operations")
    void readerMaintainsTypeSafety() {
      Reader<Config, Number> numberReader = Reader.of(cfg -> cfg.timeout());
      Reader<Config, Integer> intReader = numberReader.map(Number::intValue);

      assertThat(intReader.run(testConfig)).isEqualTo(5000);
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

      assertThat(getUrlCount.run(complex)).isEqualTo(3);
      assertThat(getSettingKeys.run(complex)).containsExactlyInAnyOrder("timeout", "retries");
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
      Config largeConfig = new Config(largeString, Integer.MAX_VALUE);
      Reader<Config, Integer> lengthReader = Reader.of(cfg -> cfg.dbUrl().length());

      assertThat(lengthReader.run(largeConfig)).isEqualTo(10000);

      // Nested computations
      Reader<Config, String> nested =
          Reader.<Config>ask()
              .map(Config::dbUrl)
              .flatMap(url -> Reader.of(cfg -> url + ":" + cfg.timeout()))
              .map(String::toUpperCase);

      assertThat(nested.run(testConfig)).contains("JDBC:TEST");
    }

    @Test
    @DisplayName("Reader maintains referential transparency")
    void readerMaintainsReferentialTransparency() {
      Reader<Config, String> reader = Reader.of(Config::dbUrl);
      Function<String, String> transform = String::toUpperCase;

      Reader<Config, String> result1 = reader.map(transform);
      Reader<Config, String> result2 = reader.map(transform);

      assertThat(result1.run(testConfig)).isEqualTo(result2.run(testConfig));
    }

    @Test
    @DisplayName("Reader handles side effects predictably")
    void readerHandlesSideEffectsPredictably() {
      AtomicInteger counter = new AtomicInteger(0);

      Reader<Config, Integer> sideEffectReader =
          Reader.of(
              cfg -> {
                counter.incrementAndGet();
                return cfg.timeout();
              });

      // Each run executes the side effect
      sideEffectReader.run(testConfig);
      assertThat(counter.get()).isEqualTo(1);

      sideEffectReader.run(testConfig);
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
      Function<String, Integer> throwingMapper =
          s -> {
            throw testException;
          };

      // Create a reader that will throw when executed
      Reader<Config, String> sourceReader = Reader.of(Config::dbUrl);
      Reader<Config, Integer> mappedReader = sourceReader.map(throwingMapper);

      // Exception is only thrown when run() is called
      assertThatThrownBy(() -> mappedReader.run(testConfig))
          .as("map should propagate function exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Reader.flatMap propagates exceptions when run")
    void flatMapPropagatesExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap");
      Function<String, Reader<Config, Integer>> throwingFlatMapper =
          s -> {
            throw testException;
          };

      Reader<Config, String> sourceReader = Reader.of(Config::dbUrl);
      Reader<Config, Integer> flatMappedReader = sourceReader.flatMap(throwingFlatMapper);

      assertThatThrownBy(() -> flatMappedReader.run(testConfig))
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
      Kind<ReaderKind.Witness<Config>, Integer> mapped =
          functor.map(throwingMapper, ReaderKindHelper.READER.widen(getUrlReader));

      // No exception yet - operation was lazy
      assertThat(mapped).isNotNull();

      // Exception only thrown when we run the reader
      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(mapped);
      assertThatThrownBy(() -> reader.run(testConfig)).isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Equality and Hash Code")
  class EqualityTests {

    @Test
    @DisplayName("Reader equality is reference-based")
    void readerEqualityIsReferenceBased() {
      Reader<Config, String> r1 = Reader.of(Config::dbUrl);
      Reader<Config, String> r2 = Reader.of(Config::dbUrl);
      Reader<Config, String> r3 = r1;

      assertThat(r1).isNotEqualTo(r2); // Different instances
      assertThat(r1).isEqualTo(r3); // Same instance
    }

    @Test
    @DisplayName("Reader behaviour is determined by function")
    void readerBehaviourIsDeterminedByFunction() {
      Reader<Config, String> r1 = Reader.of(Config::dbUrl);
      Reader<Config, String> r2 = Reader.of(cfg -> cfg.dbUrl());

      // Different function instances but same behaviour
      assertThat(r1.run(testConfig)).isEqualTo(r2.run(testConfig));
    }
  }
}
