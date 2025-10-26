// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.reader.ReaderAssert.assertThatReader;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
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
class ReaderFunctorTest extends ReaderTestBase {

  private ReaderFunctor<TestConfig> functor;

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
      TypeClassTest.<ReaderKind.Witness<TestConfig>>functor(ReaderFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
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
      Kind<ReaderKind.Witness<TestConfig>, String> result = functor.map(validMapper, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces("Connections: " + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("map() preserves environment threading")
    void mapPreservesEnvironmentThreading() {
      // Create a reader that depends on the environment
      Reader<TestConfig, Integer> maxConnectionsReader = Reader.of(TestConfig::maxConnections);
      Kind<ReaderKind.Witness<TestConfig>, Integer> maxConnectionsKind =
          ReaderKindHelper.READER.widen(maxConnectionsReader);

      java.util.function.Function<Integer, String> mapper = i -> "MaxConnections: " + i;

      Kind<ReaderKind.Witness<TestConfig>, String> result = functor.map(mapper, maxConnectionsKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces("MaxConnections: " + DEFAULT_MAX_CONNECTIONS);

      // Verify it uses the environment correctly with different config
      assertThatReader(reader)
          .whenRunWith(ALTERNATIVE_CONFIG)
          .produces("MaxConnections: " + ALTERNATIVE_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("map() composition creates correct Reader")
    void mapCompositionCreatesCorrectReader() {
      Kind<ReaderKind.Witness<TestConfig>, String> firstMap = functor.map(validMapper, validKind);

      Kind<ReaderKind.Witness<TestConfig>, String> secondMap = functor.map(secondMapper, firstMap);

      Reader<TestConfig, String> reader = narrowToReader(secondMap);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces("CONNECTIONS: " + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("map() with identity function returns equivalent Reader")
    void mapWithIdentityFunctionReturnsEquivalentReader() {
      java.util.function.Function<Integer, Integer> identity = i -> i;

      Kind<ReaderKind.Witness<TestConfig>, Integer> result = functor.map(identity, validKind);

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
      TypeClassTest.<ReaderKind.Witness<TestConfig>>functor(ReaderFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>functor(ReaderFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .selectTests()
          .onlyValidations()
          .test();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>functor(ReaderFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
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
      java.util.function.Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };

      // Using Functor interface - should not throw yet (lazy evaluation)
      Kind<ReaderKind.Witness<TestConfig>, String> result = functor.map(throwingMapper, validKind);

      // No exception yet - operation was lazy
      assertThat(result).isNotNull();

      // Exception only thrown when we run the reader
      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG))
          .as("map should propagate function exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map() propagates exceptions from source Reader when run")
    void mapPropagatesExceptionsFromSourceReaderWhenRun() {
      RuntimeException testException = new RuntimeException("Test exception: source reader");
      Reader<TestConfig, Integer> throwingReader =
          Reader.of(
              cfg -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<TestConfig>, Integer> throwingKind =
          ReaderKindHelper.READER.widen(throwingReader);

      Kind<ReaderKind.Witness<TestConfig>, String> result = functor.map(validMapper, throwingKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG))
          .as("map should propagate source Reader exceptions when run")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Composed map() operations propagate exceptions lazily")
    void composedMapOperationsPropagateExceptionsLazily() {
      RuntimeException testException = new RuntimeException("Test exception: composed");
      java.util.function.Function<String, String> throwingSecondMapper =
          s -> {
            throw testException;
          };

      // First map should succeed (lazy)
      Kind<ReaderKind.Witness<TestConfig>, String> firstMap = functor.map(validMapper, validKind);
      assertThat(firstMap).isNotNull();

      // Second map should also succeed (still lazy)
      Kind<ReaderKind.Witness<TestConfig>, String> secondMap =
          functor.map(throwingSecondMapper, firstMap);
      assertThat(secondMap).isNotNull();

      // Exception only thrown when we run the composed reader
      Reader<TestConfig, String> reader = narrowToReader(secondMap);
      assertThatThrownBy(() -> reader.run(TEST_CONFIG)).isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesAndBoundaryConditions {

    @Test
    @DisplayName("map() handles null results correctly")
    void mapHandlesNullResultsCorrectly() {
      java.util.function.Function<Integer, String> nullReturningMapper = i -> null;

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          functor.map(nullReturningMapper, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader).whenRunWith(TEST_CONFIG).producesNull();
    }

    @Test
    @DisplayName("map() with multiple environment accesses")
    void mapWithMultipleEnvironmentAccesses() {
      // Create a reader that accesses environment multiple times
      Reader<TestConfig, String> multiAccessReader =
          Reader.of(cfg -> "url=" + cfg.url() + ",maxConnections=" + cfg.maxConnections());

      Kind<ReaderKind.Witness<TestConfig>, String> multiAccessKind =
          ReaderKindHelper.READER.widen(multiAccessReader);

      java.util.function.Function<String, Integer> countEquals =
          s -> (int) s.chars().filter(ch -> ch == '=').count();

      Kind<ReaderKind.Witness<TestConfig>, Integer> result =
          functor.map(countEquals, multiAccessKind);

      Reader<TestConfig, Integer> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces(2); // "url=...,maxConnections=..." has 2 equals signs
    }

    @Test
    @DisplayName("Functor operations preserve referential transparency")
    void functorOperationsPreserveReferentialTransparency() {
      // Create the same mapped reader twice
      Kind<ReaderKind.Witness<TestConfig>, String> result1 = functor.map(validMapper, validKind);
      Kind<ReaderKind.Witness<TestConfig>, String> result2 = functor.map(validMapper, validKind);

      // Both should produce the same results
      Reader<TestConfig, String> reader1 = narrowToReader(result1);
      Reader<TestConfig, String> reader2 = narrowToReader(result2);

      assertThatReader(reader1)
          .whenRunWith(TEST_CONFIG)
          .satisfies(
              r1 -> {
                String r2 = reader2.run(TEST_CONFIG);
                assertThat(r1).isEqualTo(r2);
              });

      assertThatReader(reader1).isPureWhenRunWith(TEST_CONFIG);
      assertThatReader(reader2).isPureWhenRunWith(TEST_CONFIG);
    }
  }

  @Nested
  @DisplayName("KindHelper Integration")
  class KindHelperIntegration {

    @Test
    @DisplayName("Test ReaderKindHelper with Functor operations")
    void testReaderKindHelperWithFunctorOperations() {
      Reader<TestConfig, String> reader = Reader.of(TestConfig::url);

      CoreTypeTest.readerKindHelper(reader).test();
    }
  }
}
