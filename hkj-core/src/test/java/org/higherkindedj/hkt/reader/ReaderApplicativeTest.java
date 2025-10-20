// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
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
class ReaderApplicativeTest
    extends TypeClassTestBase<ReaderKind.Witness<ReaderApplicativeTest.Config>, String, Integer> {

  // Simple environment type for testing
  public record Config(String dbUrl, int timeout) {}

  private final Config testConfig = new Config("jdbc:test", 5000);

  private ReaderApplicative<Config> applicative;
  private ReaderFunctor<Config> functor;

  @Override
  protected Kind<ReaderKind.Witness<Config>, String> createValidKind() {
    return ReaderKindHelper.READER.widen(Reader.of(Config::dbUrl));
  }

  @Override
  protected Kind<ReaderKind.Witness<Config>, String> createValidKind2() {
    return ReaderKindHelper.READER.widen(Reader.of(cfg -> cfg.dbUrl() + "_copy"));
  }

  @Override
  protected Function<String, Integer> createValidMapper() {
    return String::length;
  }

  @Override
  protected BiPredicate<Kind<ReaderKind.Witness<Config>, ?>, Kind<ReaderKind.Witness<Config>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      Reader<Config, ?> r1 = ReaderKindHelper.READER.narrow(k1);
      Reader<Config, ?> r2 = ReaderKindHelper.READER.narrow(k2);
      return r1.run(testConfig).equals(r2.run(testConfig));
    };
  }

  @Override
  protected Function<Integer, String> createSecondMapper() {
    return i -> "Length: " + i;
  }

  @Override
  protected Kind<ReaderKind.Witness<Config>, Function<String, Integer>> createValidFunctionKind() {
    return ReaderKindHelper.READER.widen(Reader.constant(String::length));
  }

  @Override
  protected BiFunction<String, String, Integer> createValidCombiningFunction() {
    return (s1, s2) -> s1.length() + s2.length();
  }

  @Override
  protected String createTestValue() {
    return "test";
  }

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
      TypeClassTest.<ReaderKind.Witness<Config>>applicative(ReaderApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
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
      Kind<ReaderKind.Witness<Config>, String> result = applicative.of(testValue);

      Reader<Config, String> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo(testValue);
      assertThat(reader.run(new Config("different", 999))).isEqualTo(testValue);
    }

    @Test
    @DisplayName("ap() applies function Reader to value Reader")
    void apAppliesFunctionReaderToValueReader() {
      Function<String, Integer> func = String::length;
      Kind<ReaderKind.Witness<Config>, Function<String, Integer>> funcKind =
          ReaderKindHelper.READER.widen(Reader.constant(func));

      Kind<ReaderKind.Witness<Config>, Integer> result = applicative.ap(funcKind, validKind);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo("jdbc:test".length());
    }

    @Test
    @DisplayName("ap() runs both readers with same environment")
    void apRunsBothReadersWithSameEnvironment() {
      // Function reader that captures the environment
      Reader<Config, Function<String, String>> funcReader =
          Reader.of(cfg -> s -> s + ":" + cfg.timeout());
      Kind<ReaderKind.Witness<Config>, Function<String, String>> funcKind =
          ReaderKindHelper.READER.widen(funcReader);

      Kind<ReaderKind.Witness<Config>, String> result = applicative.ap(funcKind, validKind);

      Reader<Config, String> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo("jdbc:test:5000");
    }

    @Test
    @DisplayName("map2() combines two Readers with combining function")
    void map2CombinesTwoReaders() {
      BiFunction<String, String, String> combiner = (s1, s2) -> s1 + " + " + s2;

      Kind<ReaderKind.Witness<Config>, String> result =
          applicative.map2(validKind, validKind2, combiner);

      Reader<Config, String> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo("jdbc:test + jdbc:test_copy");
    }

    @Test
    @DisplayName("map2() passes same environment to both readers")
    void map2PassesSameEnvironmentToBothReaders() {
      Kind<ReaderKind.Witness<Config>, String> urlReader = validKind;
      Kind<ReaderKind.Witness<Config>, Integer> timeoutReader =
          ReaderKindHelper.READER.widen(Reader.of(Config::timeout));

      BiFunction<String, Integer, String> combiner =
          (url, timeout) -> url + " with timeout " + timeout;

      @SuppressWarnings("unchecked")
      Kind<ReaderKind.Witness<Config>, String> result =
          (Kind<ReaderKind.Witness<Config>, String>)
              (Kind<?, ?>) applicative.map2(urlReader, timeoutReader, combiner);

      Reader<Config, String> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo("jdbc:test with timeout 5000");
    }
  }

  @Nested
  @DisplayName("Individual Test Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ReaderKind.Witness<Config>>applicative(ReaderApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ReaderKind.Witness<Config>>applicative(ReaderApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
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
      TypeClassTest.<ReaderKind.Witness<Config>>applicative(ReaderApplicative.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
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
      Reader<Config, Function<String, Integer>> throwingFuncReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<Config>, Function<String, Integer>> funcKind =
          ReaderKindHelper.READER.widen(throwingFuncReader);

      Kind<ReaderKind.Witness<Config>, Integer> result = applicative.ap(funcKind, validKind);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThatThrownBy(() -> reader.run(testConfig))
          .as("ap should propagate function Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("ap() propagates exceptions from value Reader when run")
    void apPropagatesValueReaderExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: value reader");
      Reader<Config, String> throwingValueReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<Config>, String> valueKind =
          ReaderKindHelper.READER.widen(throwingValueReader);

      Kind<ReaderKind.Witness<Config>, Integer> result =
          applicative.ap(validFunctionKind, valueKind);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThatThrownBy(() -> reader.run(testConfig))
          .as("ap should propagate value Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("ap() propagates exceptions from applied function when run")
    void apPropagatesAppliedFunctionExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: applied function");
      Function<String, Integer> throwingFunc =
          s -> {
            throw testException;
          };

      Kind<ReaderKind.Witness<Config>, Function<String, Integer>> funcKind =
          ReaderKindHelper.READER.widen(Reader.constant(throwingFunc));

      Kind<ReaderKind.Witness<Config>, Integer> result = applicative.ap(funcKind, validKind);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThatThrownBy(() -> reader.run(testConfig))
          .as("ap should propagate applied function exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map2() propagates exceptions from first Reader when run")
    void map2PropagatesFirstReaderExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: first reader");
      Reader<Config, String> throwingReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<Config>, String> throwingKind =
          ReaderKindHelper.READER.widen(throwingReader);

      Kind<ReaderKind.Witness<Config>, Integer> result =
          applicative.map2(throwingKind, validKind2, validCombiningFunction);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThatThrownBy(() -> reader.run(testConfig))
          .as("map2 should propagate first Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map2() propagates exceptions from second Reader when run")
    void map2PropagatesSecondReaderExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: second reader");
      Reader<Config, String> throwingReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<Config>, String> throwingKind =
          ReaderKindHelper.READER.widen(throwingReader);

      Kind<ReaderKind.Witness<Config>, Integer> result =
          applicative.map2(validKind, throwingKind, validCombiningFunction);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThatThrownBy(() -> reader.run(testConfig))
          .as("map2 should propagate second Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map2() propagates exceptions from combining function when run")
    void map2PropagatesCombiningFunctionExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: combining function");
      BiFunction<String, String, Integer> throwingCombiner =
          (s1, s2) -> {
            throw testException;
          };

      Kind<ReaderKind.Witness<Config>, Integer> result =
          applicative.map2(validKind, validKind2, throwingCombiner);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThatThrownBy(() -> reader.run(testConfig))
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
      Kind<ReaderKind.Witness<Config>, String> result = applicative.of(null);

      Reader<Config, String> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isNull();
    }

    @Test
    @DisplayName("ap() with null function throws when run")
    void apWithNullFunctionThrowsWhenRun() {
      Kind<ReaderKind.Witness<Config>, Function<String, Integer>> funcKind =
          ReaderKindHelper.READER.widen(Reader.constant(null));

      Kind<ReaderKind.Witness<Config>, Integer> result = applicative.ap(funcKind, validKind);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThatThrownBy(() -> reader.run(testConfig))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function extracted from Reader for 'ap' was null");
    }

    @Test
    @DisplayName("Applicative operations preserve environment threading")
    void applicativeOperationsPreserveEnvironmentThreading() {
      // Create readers that both access the same environment
      Reader<Config, String> urlReader = Reader.of(cfg -> "URL:" + cfg.dbUrl());
      Reader<Config, String> timeoutReader = Reader.of(cfg -> "Timeout:" + cfg.timeout());

      Kind<ReaderKind.Witness<Config>, String> urlKind = ReaderKindHelper.READER.widen(urlReader);
      Kind<ReaderKind.Witness<Config>, String> timeoutKind =
          ReaderKindHelper.READER.widen(timeoutReader);

      // Use map2 to combine them - both should receive the same environment
      BiFunction<String, String, String> combiner = (url, timeout) -> url + " and " + timeout;

      Kind<ReaderKind.Witness<Config>, String> result =
          applicative.map2(urlKind, timeoutKind, combiner);

      Reader<Config, String> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo("URL:jdbc:test and Timeout:5000");
    }
  }

  @Nested
  @DisplayName("KindHelper Integration")
  class KindHelperIntegration {

    @Test
    @DisplayName("Test ReaderKindHelper with Applicative operations")
    void testReaderKindHelperWithApplicativeOperations() {
      Reader<Config, String> reader = Reader.constant("test");

      CoreTypeTest.readerKindHelper(reader).test();
    }
  }
}
