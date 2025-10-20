// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;

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
 * ReaderFunctor type class test using standardised patterns.
 *
 * <p>Tests the Functor implementation for Reader with proper handling of lazy evaluation semantics.
 */
@DisplayName("ReaderFunctor<R> Type Class - Standardised Test Suite")
class ReaderFunctorTest
    extends TypeClassTestBase<ReaderKind.Witness<ReaderFunctorTest.Config>, String, Integer> {

  // Simple environment type for testing
  public record Config(String dbUrl, int timeout) {}

  private final Config testConfig = new Config("jdbc:test", 5000);

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

  @BeforeEach
  void setUpFunctor() {
    functor = new ReaderFunctor<>();
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<ReaderKind.Witness<Config>>functor(ReaderFunctor.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .skipExceptions() // Reader is lazy - exceptions only thrown on run()
          .test();
    }
  }

  @Nested
  @DisplayName("Functor Operations")
  class FunctorOperations {

    @Test
    @DisplayName("map() transforms Reader result")
    void mapTransformsReaderResult() {
      Kind<ReaderKind.Witness<Config>, Integer> result = functor.map(validMapper, validKind);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo("jdbc:test".length());
    }

    @Test
    @DisplayName("map() preserves environment threading")
    void mapPreservesEnvironmentThreading() {
      // Create a reader that depends on the environment
      Reader<Config, Integer> timeoutReader = Reader.of(Config::timeout);
      Kind<ReaderKind.Witness<Config>, Integer> timeoutKind =
          ReaderKindHelper.READER.widen(timeoutReader);

      Function<Integer, String> mapper = i -> "Timeout: " + i;

      Kind<ReaderKind.Witness<Config>, String> result = functor.map(mapper, timeoutKind);

      Reader<Config, String> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo("Timeout: 5000");

      // Verify it uses the environment correctly with different config
      Config differentConfig = new Config("other", 9999);
      assertThat(reader.run(differentConfig)).isEqualTo("Timeout: 9999");
    }

    @Test
    @DisplayName("map() composition creates correct Reader")
    void mapCompositionCreatesCorrectReader() {
      Kind<ReaderKind.Witness<Config>, Integer> firstMap = functor.map(validMapper, validKind);

      Kind<ReaderKind.Witness<Config>, String> secondMap = functor.map(secondMapper, firstMap);

      Reader<Config, String> reader = ReaderKindHelper.READER.narrow(secondMap);
      assertThat(reader.run(testConfig)).isEqualTo("Length: 9");
    }

    @Test
    @DisplayName("map() with identity function returns equivalent Reader")
    void mapWithIdentityFunctionReturnsEquivalentReader() {
      Function<String, String> identity = s -> s;

      Kind<ReaderKind.Witness<Config>, String> result = functor.map(identity, validKind);

      assertThat(equalityChecker.test(result, validKind))
          .as("map with identity should be equivalent to original")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Individual Test Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ReaderKind.Witness<Config>>functor(ReaderFunctor.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ReaderKind.Witness<Config>>functor(ReaderFunctor.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .selectTests()
          .onlyValidations()
          .test();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<ReaderKind.Witness<Config>>functor(ReaderFunctor.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }

  @Nested
  @DisplayName("Exception Propagation - Reader Specific")
  class ExceptionPropagationTests {

    @Test
    @DisplayName("map() propagates exceptions when run")
    void mapPropagatesExceptionsWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: map");
      Function<String, Integer> throwingMapper =
          s -> {
            throw testException;
          };

      // Using Functor interface - should not throw yet (lazy evaluation)
      Kind<ReaderKind.Witness<Config>, Integer> result = functor.map(throwingMapper, validKind);

      // No exception yet - operation was lazy
      assertThat(result).isNotNull();

      // Exception only thrown when we run the reader
      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThatThrownBy(() -> reader.run(testConfig))
          .as("map should propagate function exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map() propagates exceptions from source Reader when run")
    void mapPropagatesExceptionsFromSourceReaderWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: source reader");
      Reader<Config, String> throwingReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<Config>, String> throwingKind =
          ReaderKindHelper.READER.widen(throwingReader);

      Kind<ReaderKind.Witness<Config>, Integer> result = functor.map(validMapper, throwingKind);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThatThrownBy(() -> reader.run(testConfig))
          .as("map should propagate source Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Composed map() operations propagate exceptions lazily")
    void composedMapOperationsPropagateExceptionsLazily() {
      RuntimeException testException = new RuntimeException("Test exception: composed");
      Function<Integer, String> throwingSecondMapper =
          i -> {
            throw testException;
          };

      // First map should succeed (lazy)
      Kind<ReaderKind.Witness<Config>, Integer> firstMap = functor.map(validMapper, validKind);
      assertThat(firstMap).isNotNull();

      // Second map should also succeed (still lazy)
      Kind<ReaderKind.Witness<Config>, String> secondMap =
          functor.map(throwingSecondMapper, firstMap);
      assertThat(secondMap).isNotNull();

      // Exception only thrown when we run the composed reader
      Reader<Config, String> reader = ReaderKindHelper.READER.narrow(secondMap);
      assertThatThrownBy(() -> reader.run(testConfig)).isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesAndBoundaryConditions {

    @Test
    @DisplayName("map() handles null results correctly")
    void mapHandlesNullResultsCorrectly() {
      Function<String, Integer> nullReturningMapper = s -> null;

      Kind<ReaderKind.Witness<Config>, Integer> result =
          functor.map(nullReturningMapper, validKind);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isNull();
    }

    @Test
    @DisplayName("map() with multiple environment accesses")
    void mapWithMultipleEnvironmentAccesses() {
      // Create a reader that accesses environment multiple times
      Reader<Config, String> multiAccessReader =
          Reader.of(cfg -> "url=" + cfg.dbUrl() + ",timeout=" + cfg.timeout());

      Kind<ReaderKind.Witness<Config>, String> multiAccessKind =
          ReaderKindHelper.READER.widen(multiAccessReader);

      Function<String, Integer> countEquals = s -> (int) s.chars().filter(ch -> ch == '=').count();

      Kind<ReaderKind.Witness<Config>, Integer> result = functor.map(countEquals, multiAccessKind);

      Reader<Config, Integer> reader = ReaderKindHelper.READER.narrow(result);
      assertThat(reader.run(testConfig)).isEqualTo(2); // "url=...,timeout=..." has 2 equals signs
    }

    @Test
    @DisplayName("Functor operations preserve referential transparency")
    void functorOperationsPreserveReferentialTransparency() {
      // Create the same mapped reader twice
      Kind<ReaderKind.Witness<Config>, Integer> result1 = functor.map(validMapper, validKind);
      Kind<ReaderKind.Witness<Config>, Integer> result2 = functor.map(validMapper, validKind);

      // Both should produce the same results
      Reader<Config, Integer> reader1 = ReaderKindHelper.READER.narrow(result1);
      Reader<Config, Integer> reader2 = ReaderKindHelper.READER.narrow(result2);

      assertThat(reader1.run(testConfig)).isEqualTo(reader2.run(testConfig));
      assertThat(reader1.run(testConfig)).isEqualTo(reader2.run(testConfig));
    }
  }

  @Nested
  @DisplayName("KindHelper Integration")
  class KindHelperIntegration {

    @Test
    @DisplayName("Test ReaderKindHelper with Functor operations")
    void testReaderKindHelperWithFunctorOperations() {
      Reader<Config, String> reader = Reader.of(Config::dbUrl);

      CoreTypeTest.readerKindHelper(reader).test();
    }
  }
}
