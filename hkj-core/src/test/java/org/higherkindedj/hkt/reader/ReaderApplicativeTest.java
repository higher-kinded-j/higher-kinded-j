// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.reader.ReaderAssert.assertThatReader;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ReaderApplicative type class test using standardised patterns.
 *
 * <p>Tests the Applicative implementation for Reader with proper handling of lazy evaluation
 * semantics.
 */
@DisplayName("ReaderApplicative<R> Type Class - Standardised Test Suite")
class ReaderApplicativeTest extends ReaderTestBase {

  private ReaderApplicative<TestConfig> applicative;
  private ReaderFunctor<TestConfig> functor;

  @BeforeEach
  void setUpApplicative() {
    applicative = new ReaderApplicative<>();
    functor = new ReaderFunctor<>();
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Applicative test pattern")
    void runCompleteApplicativeTestPattern() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>applicative(ReaderApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ReaderFunctor.class)
          .withApFrom(ReaderApplicative.class)
          .withMap2From(Applicative.class)
          .selectTests()
          .skipExceptions() // Reader is lazy - exceptions only thrown on run()
          .test();
    }
  }

  @Nested
  @DisplayName("Applicative Operations")
  class ApplicativeOperations {

    @Test
    @DisplayName("of() lifts pure value into Reader context")
    void ofLiftsPureValue() {
      String testValue = "pure value";
      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.of(testValue);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader).whenRunWith(TEST_CONFIG).produces(testValue);
      assertThatReader(reader).isConstantFor(TEST_CONFIG, ALTERNATIVE_CONFIG);
    }

    @Test
    @DisplayName("ap() applies function Reader to value Reader")
    void apAppliesFunctionReaderToValueReader() {
      Function<Integer, String> func = i -> "Connections: " + i;
      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          ReaderKindHelper.READER.widen(Reader.constant(func));

      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.ap(funcKind, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces("Connections: " + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("ap() runs both readers with same environment")
    void apRunsBothReadersWithSameEnvironment() {
      // Function reader that captures the environment
      Reader<TestConfig, Function<Integer, String>> funcReader =
          Reader.of(cfg -> i -> cfg.url() + ":" + i);
      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          ReaderKindHelper.READER.widen(funcReader);

      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.ap(funcKind, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces(DEFAULT_URL + ":" + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("map2() combines two Readers with combining function")
    void map2CombinesTwoReaders() {
      BiFunction<Integer, Integer, String> combiner = (a, b) -> a + " + " + b;

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          applicative.map2(validKind, validKind2, combiner);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces(DEFAULT_MAX_CONNECTIONS + " + " + (DEFAULT_MAX_CONNECTIONS * 2));
    }

    @Test
    @DisplayName("map2() passes same environment to both readers")
    void map2PassesSameEnvironmentToBothReaders() {
      Kind<ReaderKind.Witness<TestConfig>, String> urlReaderKind = urlKind();
      Kind<ReaderKind.Witness<TestConfig>, Integer> maxConnectionsReaderKind = maxConnectionsKind();

      BiFunction<String, Integer, String> combiner =
          (url, maxConns) -> url + " with maxConnections " + maxConns;

      @SuppressWarnings("unchecked")
      Kind<ReaderKind.Witness<TestConfig>, String> result =
          (Kind<ReaderKind.Witness<TestConfig>, String>)
              (Kind<?, ?>) applicative.map2(urlReaderKind, maxConnectionsReaderKind, combiner);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces(DEFAULT_URL + " with maxConnections " + DEFAULT_MAX_CONNECTIONS);
    }
  }

  @Nested
  @DisplayName("Individual Test Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>applicative(ReaderApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>applicative(ReaderApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ReaderFunctor.class)
          .withApFrom(ReaderApplicative.class)
          .withMap2From(Applicative.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>applicative(ReaderApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }

  @Nested
  @DisplayName("Exception Propagation - Reader Specific")
  class ExceptionPropagationTests {

    @Test
    @DisplayName("ap() propagates exceptions from function Reader when run")
    void apPropagatesFunctionReaderExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: function reader");
      Reader<TestConfig, Function<Integer, String>> throwingFuncReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          ReaderKindHelper.READER.widen(throwingFuncReader);

      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.ap(funcKind, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG))
          .as("ap should propagate function Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("ap() propagates exceptions from value Reader when run")
    void apPropagatesValueReaderExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: value reader");
      Reader<TestConfig, Integer> throwingValueReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<TestConfig>, Integer> valueKind =
          ReaderKindHelper.READER.widen(throwingValueReader);

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          applicative.ap(validFunctionKind, valueKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG))
          .as("ap should propagate value Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("ap() propagates exceptions from applied function when run")
    void apPropagatesAppliedFunctionExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: applied function");
      Function<Integer, String> throwingFunc =
          i -> {
            throw testException;
          };

      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          ReaderKindHelper.READER.widen(Reader.constant(throwingFunc));

      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.ap(funcKind, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG))
          .as("ap should propagate applied function exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map2() propagates exceptions from first Reader when run")
    void map2PropagatesFirstReaderExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: first reader");
      Reader<TestConfig, Integer> throwingReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<TestConfig>, Integer> throwingKind =
          ReaderKindHelper.READER.widen(throwingReader);

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          applicative.map2(throwingKind, validKind2, validCombiningFunction);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG))
          .as("map2 should propagate first Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map2() propagates exceptions from second Reader when run")
    void map2PropagatesSecondReaderExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: second reader");
      Reader<TestConfig, Integer> throwingReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<TestConfig>, Integer> throwingKind =
          ReaderKindHelper.READER.widen(throwingReader);

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          applicative.map2(validKind, throwingKind, validCombiningFunction);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG))
          .as("map2 should propagate second Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map2() propagates exceptions from combining function when run")
    void map2PropagatesCombiningFunctionExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: combining function");
      BiFunction<Integer, Integer, String> throwingCombiner =
          (i1, i2) -> {
            throw testException;
          };

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          applicative.map2(validKind, validKind2, throwingCombiner);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG))
          .as("map2 should propagate combining function exceptions when run")
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesAndBoundaryConditions {

    @Test
    @DisplayName("of() with null value creates Reader that returns null")
    void ofWithNullValueCreatesReaderThatReturnsNull() {
      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.of(null);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader).whenRunWith(TEST_CONFIG).producesNull();
    }

    @Test
    @DisplayName("ap() with null function throws when run")
    void apWithNullFunctionThrowsWhenRun() {
      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          ReaderKindHelper.READER.widen(Reader.constant(null));

      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.ap(funcKind, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function extracted from Reader for 'ap' was null");
    }

    @Test
    @DisplayName("Applicative operations preserve environment threading")
    void applicativeOperationsPreserveEnvironmentThreading() {
      // Create readers that both access the same environment
      Reader<TestConfig, String> urlReader = Reader.of(cfg -> "URL:" + cfg.url());
      Reader<TestConfig, String> maxConnectionsReader =
          Reader.of(cfg -> "MaxConnections:" + cfg.maxConnections());

      Kind<ReaderKind.Witness<TestConfig>, String> urlKindLocal =
          ReaderKindHelper.READER.widen(urlReader);
      Kind<ReaderKind.Witness<TestConfig>, String> maxConnectionsKindLocal =
          ReaderKindHelper.READER.widen(maxConnectionsReader);

      // Use map2 to combine them - both should receive the same environment
      BiFunction<String, String, String> combiner = (url, maxConns) -> url + " and " + maxConns;

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          applicative.map2(urlKindLocal, maxConnectionsKindLocal, combiner);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces("URL:" + DEFAULT_URL + " and MaxConnections:" + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("of() creates constant readers that ignore environment")
    void ofCreatesConstantReadersThatIgnoreEnvironment() {
      String constantValue = "I am constant";
      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.of(constantValue);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader).isConstantFor(TEST_CONFIG, ALTERNATIVE_CONFIG);
      assertThatReader(reader).whenRunWith(TEST_CONFIG).produces(constantValue);
      assertThatReader(reader).whenRunWith(ALTERNATIVE_CONFIG).produces(constantValue);
    }

    @Test
    @DisplayName("map2() with constant and environment-dependent readers")
    void map2WithConstantAndEnvironmentDependentReaders() {
      Kind<ReaderKind.Witness<TestConfig>, String> constantKind = applicative.of("Prefix:");
      Kind<ReaderKind.Witness<TestConfig>, String> envKind = urlKind();

      BiFunction<String, String, String> combiner = (prefix, url) -> prefix + url;

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          applicative.map2(constantKind, envKind, combiner);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader).whenRunWith(TEST_CONFIG).produces("Prefix:" + DEFAULT_URL);
      assertThatReader(reader)
          .whenRunWith(ALTERNATIVE_CONFIG)
          .produces("Prefix:" + ALTERNATIVE_URL);
    }
  }

  @Nested
  @DisplayName("KindHelper Integration")
  class KindHelperIntegration {

    @Test
    @DisplayName("Test ReaderKindHelper with Applicative operations")
    void testReaderKindHelperWithApplicativeOperations() {
      Reader<TestConfig, String> reader = constantReader("test");

      CoreTypeTest.readerKindHelper(reader).test();
    }
  }

  @Nested
  @DisplayName("Purity and Referential Transparency")
  class PurityTests {

    @Test
    @DisplayName("of() produces pure readers")
    void ofProducesPureReaders() {
      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.of("pure");

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader).isPureWhenRunWith(TEST_CONFIG);
    }

    @Test
    @DisplayName("ap() produces pure readers when given pure readers")
    void apProducesPureReadersWhenGivenPureReaders() {
      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          applicative.of(i -> "Value: " + i);

      Kind<ReaderKind.Witness<TestConfig>, String> result = applicative.ap(funcKind, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader).isPureWhenRunWith(TEST_CONFIG);
    }

    @Test
    @DisplayName("map2() produces pure readers when given pure readers")
    void map2ProducesPureReadersWhenGivenPureReaders() {
      BiFunction<Integer, Integer, String> combiner = (a, b) -> "Sum: " + (a + b);

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          applicative.map2(validKind, validKind2, combiner);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader).isPureWhenRunWith(TEST_CONFIG);
    }
  }
}
