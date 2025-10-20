// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderMonad Complete Test Suite")
class ReaderMonadTest
    extends TypeClassTestBase<ReaderKind.Witness<ReaderMonadTest.Config>, Integer, String> {

  // Simple environment for tests
  public record Config(String url, int connections) {}

  private final Config testConfig = new Config("db://localhost", 5);

  private ReaderMonad<Config> monad;

  @Override
  protected Kind<ReaderKind.Witness<Config>, Integer> createValidKind() {
    return READER.widen(Reader.of(Config::connections));
  }

  @Override
  protected Kind<ReaderKind.Witness<Config>, Integer> createValidKind2() {
    return READER.widen(Reader.of(config -> config.connections() * 2));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<ReaderKind.Witness<Config>, String>> createValidFlatMapper() {
    return i -> READER.widen(Reader.of(config -> config.url() + ":" + i));
  }

  @Override
  protected Kind<ReaderKind.Witness<Config>, Function<Integer, String>> createValidFunctionKind() {
    return READER.widen(Reader.of(config -> i -> config.url() + i));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return 5;
  }

  @Override
  protected Function<Integer, Kind<ReaderKind.Witness<Config>, String>> createTestFunction() {
    return i -> READER.widen(Reader.of(config -> config.url() + ":" + i));
  }

  @Override
  protected Function<String, Kind<ReaderKind.Witness<Config>, String>> createChainFunction() {
    return s -> READER.widen(Reader.of(config -> s + " (" + config.connections() + ")"));
  }

  @Override
  protected BiPredicate<Kind<ReaderKind.Witness<Config>, ?>, Kind<ReaderKind.Witness<Config>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      // Run both readers with the same config and compare results
      Object result1 = READER.runReader(k1, testConfig);
      Object result2 = READER.runReader(k2, testConfig);
      if (result1 == null && result2 == null) return true;
      if (result1 == null || result2 == null) return false;
      return result1.equals(result2);
    };
  }

  @BeforeEach
  void setUpMonad() {
    monad = ReaderMonad.instance();
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<ReaderKind.Witness<Config>>monad(ReaderMonad.class)
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
          .skipExceptions() // Reader is lazy - exceptions only thrown on run()
          .test();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(ReaderMonadTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("of() creates constant Reader")
    void ofCreatesConstantReader() {
      Kind<ReaderKind.Witness<Config>, String> result = monad.of("constantValue");

      Reader<Config, String> reader = READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo("constantValue");
      assertThat(reader.run(new Config("other", 0))).isEqualTo("constantValue");
    }

    @Test
    @DisplayName("map() applies function to result")
    void mapAppliesFunctionToResult() {
      Kind<ReaderKind.Witness<Config>, String> result = monad.map(validMapper, validKind);

      Reader<Config, String> reader = READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo(String.valueOf(testConfig.connections()));
    }

    @Test
    @DisplayName("flatMap() sequences computations")
    void flatMapSequencesComputations() {
      Kind<ReaderKind.Witness<Config>, String> result = monad.flatMap(validFlatMapper, validKind);

      Reader<Config, String> reader = READER.narrow(result);
      assertThat(reader.run(testConfig))
          .isEqualTo(testConfig.url() + ":" + testConfig.connections());
    }

    @Test
    @DisplayName("flatMap() passes environment correctly")
    void flatMapPassesEnvironmentCorrectly() {
      Function<Integer, Kind<ReaderKind.Witness<Config>, String>> flatMapper =
          conns -> READER.widen(Reader.of(config -> config.url() + " [" + conns + "]"));

      Kind<ReaderKind.Witness<Config>, String> result = monad.flatMap(flatMapper, validKind);

      Reader<Config, String> reader = READER.narrow(result);
      assertThat(reader.run(testConfig))
          .isEqualTo(testConfig.url() + " [" + testConfig.connections() + "]");
    }

    @Test
    @DisplayName("ap() applies function to value - both from environment")
    void apAppliesFunctionToValue() {
      Kind<ReaderKind.Witness<Config>, Function<Integer, String>> funcKind =
          READER.widen(Reader.of(config -> i -> config.url() + ":" + i));
      Kind<ReaderKind.Witness<Config>, Integer> valueKind = validKind;

      Kind<ReaderKind.Witness<Config>, String> result = monad.ap(funcKind, valueKind);

      Reader<Config, String> reader = READER.narrow(result);
      assertThat(reader.run(testConfig))
          .isEqualTo(testConfig.url() + ":" + testConfig.connections());
    }

    @Test
    @DisplayName("ap() works with constant function and value")
    void apWorksWithConstantFunctionAndValue() {
      Kind<ReaderKind.Witness<Config>, Function<Integer, String>> funcKind =
          monad.of(i -> "Num" + i);
      Kind<ReaderKind.Witness<Config>, Integer> valueKind = monad.of(100);

      Kind<ReaderKind.Witness<Config>, String> result = monad.ap(funcKind, valueKind);

      assertThat(READER.runReader(result, testConfig)).isEqualTo("Num100");
    }

    @Test
    @DisplayName("map2() combines two Reader values")
    void map2CombinesTwoReaderValues() {
      Kind<ReaderKind.Witness<Config>, Integer> r1 = validKind;
      Kind<ReaderKind.Witness<Config>, Integer> r2 = validKind2;

      BiFunction<Integer, Integer, String> combiner = (a, b) -> a + "," + b;
      Kind<ReaderKind.Witness<Config>, String> result = monad.map2(r1, r2, combiner);

      assertThat(READER.runReader(result, testConfig))
          .isEqualTo(testConfig.connections() + "," + (testConfig.connections() * 2));
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ReaderKind.Witness<Config>>monad(ReaderMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ReaderKind.Witness<Config>>monad(ReaderMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ReaderFunctor.class)
          .withApFrom(ReaderApplicative.class)
          .withFlatMapFrom(ReaderMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Reader is lazy - exceptions only occur when run() is called
      // Exception propagation is thoroughly tested in EdgeCasesTests
      TypeClassTest.<ReaderKind.Witness<Config>>monad(ReaderMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ReaderFunctor.class)
          .withApFrom(ReaderApplicative.class)
          .withFlatMapFrom(ReaderMonad.class)
          .selectTests()
          .skipExceptions()
          .test();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<ReaderKind.Witness<Config>>monad(ReaderMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      Kind<ReaderKind.Witness<Config>, Integer> start = READER.widen(Reader.of(config -> 1));

      Kind<ReaderKind.Witness<Config>, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      assertThat(READER.runReader(result, testConfig))
          .isEqualTo(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("ask() identity - returns environment")
    void askIdentityReturnsEnvironment() {
      Kind<ReaderKind.Witness<Config>, Config> askReader = READER.ask();
      assertThat(READER.runReader(askReader, testConfig)).isSameAs(testConfig);
    }

    @Test
    @DisplayName("constant() ignores environment")
    void constantIgnoresEnvironment() {
      Kind<ReaderKind.Witness<Config>, String> constantReader = READER.constant("fixed");
      Config differentConfig = new Config("http://server", 10);
      assertThat(READER.runReader(constantReader, differentConfig)).isEqualTo("fixed");
    }

    @Test
    @DisplayName("map() preserves environment threading")
    void mapPreservesEnvironmentThreading() {
      Kind<ReaderKind.Witness<Config>, Config> askReader = READER.ask();
      Kind<ReaderKind.Witness<Config>, String> mappedAsk = monad.map(Config::url, askReader);
      assertThat(READER.runReader(mappedAsk, testConfig)).isEqualTo(testConfig.url());
    }

    @Test
    @DisplayName("Exception in function propagates through map")
    void exceptionInFunctionPropagatesThroughMap() {
      RuntimeException testException = new RuntimeException("Test exception: map test");
      Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };

      Kind<ReaderKind.Witness<Config>, String> result = monad.map(throwingMapper, validKind);
      Reader<Config, String> reader = READER.narrow(result);

      assertThatThrownBy(() -> reader.run(testConfig)).isSameAs(testException);
    }

    @Test
    @DisplayName("Exception in function propagates through flatMap")
    void exceptionInFunctionPropagatesThroughFlatMap() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap test");
      Function<Integer, Kind<ReaderKind.Witness<Config>, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };

      Kind<ReaderKind.Witness<Config>, String> result =
          monad.flatMap(throwingFlatMapper, validKind);
      Reader<Config, String> reader = READER.narrow(result);

      assertThatThrownBy(() -> reader.run(testConfig)).isSameAs(testException);
    }

    @Test
    @DisplayName("Exception in ap function propagates")
    void exceptionInApFunctionPropagates() {
      RuntimeException testException = new RuntimeException("Test exception: ap test");
      Kind<ReaderKind.Witness<Config>, Function<Integer, String>> funcKind =
          monad.of(
              i -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<Config>, String> result = monad.ap(funcKind, validKind);
      Reader<Config, String> reader = READER.narrow(result);

      assertThatThrownBy(() -> reader.run(testConfig)).isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("flatMap efficient with many operations")
    void flatMapEfficientWithManyOperations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<ReaderKind.Witness<Config>, Integer> start = monad.of(1);

        Kind<ReaderKind.Witness<Config>, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int increment = i;
          result = monad.flatMap(x -> monad.of(x + increment), result);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        assertThat(READER.runReader(result, testConfig)).isEqualTo(expectedSum);
      }
    }
  }

  @Nested
  @DisplayName("mapN Tests")
  class MapNTests {
    Kind<ReaderKind.Witness<Config>, Integer> r1 = READER.widen(Reader.of(Config::connections));
    Kind<ReaderKind.Witness<Config>, String> r2 = READER.widen(Reader.of(Config::url));
    Kind<ReaderKind.Witness<Config>, Double> r3 =
        READER.widen(Reader.of(config -> config.connections() * 1.5));
    Kind<ReaderKind.Witness<Config>, Boolean> r4 =
        READER.widen(Reader.of(config -> config.url().startsWith("db")));

    @Test
    @DisplayName("map2() combines results")
    void map2CombinesResults() {
      Kind<ReaderKind.Witness<Config>, String> result =
          monad.map2(r1, r2, (conns, url) -> url + " (" + conns + ")");
      assertThat(READER.runReader(result, testConfig))
          .isEqualTo(testConfig.url() + " (" + testConfig.connections() + ")");
    }

    @Test
    @DisplayName("map3() combines results")
    void map3CombinesResults() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("I:%d S:%s D:%.1f", i, s, d);
      Kind<ReaderKind.Witness<Config>, String> result = monad.map3(r1, r2, r3, f3);
      assertThat(READER.runReader(result, testConfig))
          .isEqualTo(
              String.format(
                  "I:%d S:%s D:%.1f",
                  testConfig.connections(), testConfig.url(), testConfig.connections() * 1.5));
    }

    @Test
    @DisplayName("map4() combines results")
    void map4CombinesResults() {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("I:%d S:%s D:%.1f B:%b", i, s, d, b);
      Kind<ReaderKind.Witness<Config>, String> result = monad.map4(r1, r2, r3, r4, f4);
      assertThat(READER.runReader(result, testConfig))
          .isEqualTo(
              String.format(
                  "I:%d S:%s D:%.1f B:%b",
                  testConfig.connections(),
                  testConfig.url(),
                  testConfig.connections() * 1.5,
                  testConfig.url().startsWith("db")));
    }
  }

  @Nested
  @DisplayName("Core Type Tests")
  class CoreTypeTests {

    @Test
    @DisplayName("Test Reader core type operations")
    void testReaderCoreTypeOperations() {
      Reader<Config, Integer> reader = Reader.of(Config::connections);

      CoreTypeTest.<Config, Integer>reader(Reader.class)
          .withReader(reader)
          .withEnvironment(testConfig)
          .withMappers(TestFunctions.INT_TO_STRING)
          .testAll();
    }

    @Test
    @DisplayName("Test ReaderKindHelper")
    void testReaderKindHelper() {
      Reader<Config, String> reader = Reader.constant("test");

      CoreTypeTest.readerKindHelper(reader).test();
    }
  }
}
