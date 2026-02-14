// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOFunctor Complete Test Suite")
class IOFunctorTest extends IOTestBase {

  private final IOFunctor functor = IOFunctor.INSTANCE;

  @BeforeEach
  void setUpFunctor() {
    validateRequiredFixtures();
  }

  @Nested
  @DisplayName("Complete Functor Test Suite")
  class CompleteFunctorTestSuite {
    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      // IO has lazy evaluation, so we skip default exception tests
      // and provide our own in ExceptionPropagationTests
      TypeClassTest.<IOKind.Witness>functor(IOFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .skipExceptions()
          .and()
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(IOFunctorTest.class);

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
    @DisplayName("map() applies function lazily")
    void mapAppliesFunctionLazily() {
      AtomicInteger executeCount = new AtomicInteger(0);

      IO<Integer> io =
          IO.delay(
              () -> {
                executeCount.incrementAndGet();
                return DEFAULT_IO_VALUE;
              });

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(io);
      Kind<IOKind.Witness, String> result = functor.map(validMapper, kind);

      // Should not execute yet
      assertThat(executeCount.get()).isZero();

      // Execute and verify
      assertThatIO(narrowToIO(result)).hasValue(String.valueOf(DEFAULT_IO_VALUE));
      assertThat(executeCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map() preserves IO structure")
    void mapPreservesIOStructure() {
      Kind<IOKind.Witness, String> result = functor.map(validMapper, validKind);

      assertThat(result).isNotNull();
      assertThat(IO_OP.narrow(result)).isInstanceOf(IO.class);
    }

    @Test
    @DisplayName("map() with null value in IO")
    void mapWithNullValueInIO() {
      Kind<IOKind.Witness, Integer> nullKind = ioKind(null);
      Function<Integer, String> nullSafeMapper = i -> String.valueOf(i);

      Kind<IOKind.Witness, String> result = functor.map(nullSafeMapper, nullKind);

      assertThatIO(narrowToIO(result)).hasValue("null");
    }

    @Test
    @DisplayName("map() chains multiple transformations")
    void mapChainsMultipleTransformations() {
      Function<String, Integer> stringLength = String::length;

      Kind<IOKind.Witness, String> intermediate = functor.map(validMapper, validKind);
      Kind<IOKind.Witness, Integer> result = functor.map(stringLength, intermediate);

      assertThatIO(narrowToIO(result)).hasValue(String.valueOf(DEFAULT_IO_VALUE).length());
    }

    @Test
    @DisplayName("map() applies function to value")
    void mapAppliesFunctionToValue() {
      Kind<IOKind.Witness, String> result = functor.map(validMapper, validKind);

      assertThatIO(narrowToIO(result)).hasValue(String.valueOf(DEFAULT_IO_VALUE));
    }

    @Test
    @DisplayName("map() with complex transformations")
    void mapWithComplexTransformations() {
      Function<Integer, String> complexMapper =
          i -> {
            if (i < 0) return "negative";
            if (i == 0) return "zero";
            return "positive:" + i;
          };

      Kind<IOKind.Witness, String> result = functor.map(complexMapper, validKind);
      assertThatIO(narrowToIO(result)).hasValue("positive:" + DEFAULT_IO_VALUE);
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {
    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<IOKind.Witness>functor(IOFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<IOKind.Witness>functor(IOFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Note: Default exception tests don't work with IO's lazy evaluation
      // See EdgeCasesTests nested class for IO-specific exception tests
      // This test intentionally empty - use EdgeCasesTests for exception verification
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<IOKind.Witness>functor(IOFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {
    @Test
    @DisplayName("map() propagates exceptions from mapper function")
    void mapPropagatesExceptionsFromMapper() {
      RuntimeException testException = new RuntimeException("Mapper failed");
      Function<Integer, String> throwingMapper = TestFunctions.throwingFunction(testException);

      Kind<IOKind.Witness, String> result = functor.map(throwingMapper, validKind);

      // Exception is thrown when IO is executed
      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("Mapper failed");
    }

    @Test
    @DisplayName("map() propagates exceptions from source IO")
    void mapPropagatesExceptionsFromSourceIO() {
      RuntimeException testException = new RuntimeException("Source IO failed");
      Kind<IOKind.Witness, Integer> failingKind = failingIO(testException);

      Kind<IOKind.Witness, String> result = functor.map(validMapper, failingKind);

      // Exception is thrown when IO is executed
      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("Source IO failed");
    }

    @Test
    @DisplayName("map() preserves side effects order")
    void mapPreservesSideEffectsOrder() {
      StringBuilder log = new StringBuilder();

      IO<Integer> io =
          IO.delay(
              () -> {
                log.append("original;");
                return DEFAULT_IO_VALUE;
              });

      Function<Integer, String> mapper =
          i -> {
            log.append("mapped;");
            return String.valueOf(i);
          };

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(io);
      Kind<IOKind.Witness, String> result = functor.map(mapper, kind);

      // Execute
      executeIO(result);

      assertThat(log.toString()).isEqualTo("original;mapped;");
    }

    @Test
    @DisplayName("map() is repeatable")
    void mapIsRepeatable() {
      Kind<IOKind.Witness, String> result = functor.map(validMapper, validKind);

      assertThatIO(narrowToIO(result)).isRepeatable();
    }

    @Test
    @DisplayName("map() with nested IO structures")
    void mapWithNestedIOStructures() {
      IO<IO<Integer>> nested = IO.delay(() -> IO.delay(() -> DEFAULT_IO_VALUE));
      Kind<IOKind.Witness, IO<Integer>> kind = IO_OP.widen(nested);

      Function<IO<Integer>, Integer> unwrapper = IO::unsafeRunSync;
      Kind<IOKind.Witness, Integer> result = functor.map(unwrapper, kind);

      assertThatIO(narrowToIO(result)).hasValue(DEFAULT_IO_VALUE);
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {
    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      assertThatThrownBy(() -> functor.map(null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function f for IOFunctor.map cannot be null");
    }

    @Test
    @DisplayName("map() validates null Kind")
    void mapValidatesNullKind() {
      assertThatThrownBy(() -> functor.map(validMapper, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("map");
    }
  }

  @Nested
  @DisplayName("IOAssert Integration Tests")
  class IOAssertIntegrationTests {
    @Test
    @DisplayName("IOAssert works with mapped IOs")
    void testIOAssertWithMappedIOs() {
      Kind<IOKind.Witness, String> result = functor.map(validMapper, validKind);

      assertThatIO(narrowToIO(result))
          .hasValueNonNull()
          .hasValueSatisfying(v -> assertThat(v).isEqualTo(String.valueOf(DEFAULT_IO_VALUE)));
    }

    @Test
    @DisplayName("IOAssert verifies laziness of mapped IOs")
    void testIOAssertVerifiesLaziness() {
      AtomicInteger counter = new AtomicInteger(0);
      Kind<IOKind.Witness, Integer> kind =
          ioKind(
              counter.incrementAndGet()); // Note: of() captures value eagerly but wraps in lazy IO

      Kind<IOKind.Witness, String> result = functor.map(validMapper, kind);

      // The IO itself is lazy, even though the value was captured
      assertThatIO(narrowToIO(result)).hasValue("1");
    }

    @Test
    @DisplayName("IOAssert detects exceptions in mapped IOs")
    void testIOAssertDetectsExceptions() {
      RuntimeException exception = new RuntimeException("Map failure");
      Kind<IOKind.Witness, String> result =
          functor.map(TestFunctions.throwingFunction(exception), validKind);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessageContaining("Map failure");
    }
  }
}
