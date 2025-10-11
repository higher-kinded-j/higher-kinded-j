// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOFunctor Complete Test Suite")
class IOFunctorTest extends TypeClassTestBase<IOKind.Witness, Integer, String> {

  private IOFunctor functor;
  private Functor<IOKind.Witness> functorTyped;

  @Override
  protected Kind<IOKind.Witness, Integer> createValidKind() {
    return IO_OP.widen(IO.delay(() -> 42));
  }

  @Override
  protected Kind<IOKind.Witness, Integer> createValidKind2() {
    return IO_OP.widen(IO.delay(() -> 24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected BiPredicate<Kind<IOKind.Witness, ?>, Kind<IOKind.Witness, ?>> createEqualityChecker() {
    // IO equality compares results after execution
    return (k1, k2) -> {
      Object v1 = IO_OP.narrow(k1).unsafeRunSync();
      Object v2 = IO_OP.narrow(k2).unsafeRunSync();
      return java.util.Objects.equals(v1, v2);
    };
  }

  @BeforeEach
  void setUpFunctor() {
    functor = new IOFunctor();
    functorTyped = functor;
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
          .<Integer>instance(functorTyped)
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
                return 42;
              });

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(io);
      Kind<IOKind.Witness, String> result = functor.map(validMapper, kind);

      // Should not execute yet
      assertThat(executeCount.get()).isZero();

      // Execute and verify
      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("42");
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
      Kind<IOKind.Witness, Integer> nullKind = IO_OP.widen(IO.delay(() -> null));
      Function<Integer, String> nullSafeMapper = i -> String.valueOf(i);

      Kind<IOKind.Witness, String> result = functor.map(nullSafeMapper, nullKind);

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("null");
    }

    @Test
    @DisplayName("map() chains multiple transformations")
    void mapChainsMultipleTransformations() {
      Function<String, Integer> stringLength = String::length;

      Kind<IOKind.Witness, String> intermediate = functor.map(validMapper, validKind);
      Kind<IOKind.Witness, Integer> result = functor.map(stringLength, intermediate);

      Integer value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo(2); // "42".length() == 2
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {
    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<IOKind.Witness>functor(IOFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<IOKind.Witness>functor(IOFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Note: Default exception tests don't work with IO's lazy evaluation
      // See ExceptionPropagationTests nested class for IO-specific exception tests
      // This test intentionally empty - use EdgeCasesTests for exception verification
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<IOKind.Witness>functor(IOFunctor.class)
          .<Integer>instance(functorTyped)
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
      Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };

      Kind<IOKind.Witness, String> result = functor.map(throwingMapper, validKind);

      // Exception is thrown when IO is executed
      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(testException);
    }

    @Test
    @DisplayName("map() propagates exceptions from source IO")
    void mapPropagatesExceptionsFromSourceIO() {
      RuntimeException testException = new RuntimeException("Source IO failed");
      IO<Integer> failingIO =
          IO.delay(
              () -> {
                throw testException;
              });

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(failingIO);
      Kind<IOKind.Witness, String> result = functor.map(validMapper, kind);

      // Exception is thrown when IO is executed
      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(testException);
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
      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("positive:42");
    }

    @Test
    @DisplayName("map() preserves side effects order")
    void mapPreservesSideEffectsOrder() {
      StringBuilder log = new StringBuilder();

      IO<Integer> io =
          IO.delay(
              () -> {
                log.append("original;");
                return 42;
              });

      Function<Integer, String> mapper =
          i -> {
            log.append("mapped;");
            return String.valueOf(i);
          };

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(io);
      Kind<IOKind.Witness, String> result = functor.map(mapper, kind);

      // Execute
      IO_OP.unsafeRunSync(result);

      assertThat(log.toString()).isEqualTo("original;mapped;");
    }

    @Test
    @DisplayName("map() is repeatable")
    void mapIsRepeatable() {
      Kind<IOKind.Witness, String> result = functor.map(validMapper, validKind);

      String first = IO_OP.unsafeRunSync(result);
      String second = IO_OP.unsafeRunSync(result);

      assertThat(first).isEqualTo(second).isEqualTo("42");
    }

    @Test
    @DisplayName("map() with nested IO structures")
    void mapWithNestedIOStructures() {
      IO<IO<Integer>> nested = IO.delay(() -> IO.delay(() -> 42));
      Kind<IOKind.Witness, IO<Integer>> kind = IO_OP.widen(nested);

      Function<IO<Integer>, Integer> unwrapper = IO::unsafeRunSync;
      Kind<IOKind.Witness, Integer> result = functor.map(unwrapper, kind);

      Integer value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {
    @Test
    @DisplayName("map() efficient with many transformations")
    void mapEfficientWithManyTransformations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<IOKind.Witness, Integer> start = validKind;

        Kind<IOKind.Witness, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          result = functor.map(x -> x + 1, result);
        }

        Integer value = IO_OP.unsafeRunSync(result);
        assertThat(value).isEqualTo(142); // 42 + 100
      }
    }

    @Test
    @DisplayName("map() maintains lazy evaluation with long chains")
    void mapMaintainsLazyEvaluationWithLongChains() {
      AtomicInteger executeCount = new AtomicInteger(0);

      IO<Integer> io =
          IO.delay(
              () -> {
                executeCount.incrementAndGet();
                return 1;
              });

      Kind<IOKind.Witness, Integer> start = IO_OP.widen(io);
      Kind<IOKind.Witness, Integer> result = start;

      // Build long chain
      for (int i = 0; i < 50; i++) {
        result = functor.map(x -> x + 1, result);
      }

      // Should not have executed yet
      assertThat(executeCount.get()).isZero();

      // Execute once
      Integer value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo(51);
      assertThat(executeCount.get()).isEqualTo(1);
    }
  }
}
