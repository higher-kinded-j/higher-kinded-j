// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.higherkindedj.hkt.test.fixtures.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("IOFunctor")
class IOFunctorTest extends IOTestBase {

  private final IOFunctor functor = IOFunctor.INSTANCE;

  @BeforeEach
  void setUpFunctor() {
    validateRequiredFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.io.IOLawFixtures#kinds")
    void identity(String label, Kind<IOKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.io.IOLawFixtures#kinds")
    void composition(String label, Kind<IOKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}
   * <em>propagates</em> a thrown mapper exception immediately, but an IO is lazy — the exception
   * surfaces only when the computation is run. That deferral is exercised by {@link
   * EdgeCasesTests}.
   */
  @Test
  @DisplayName(
      "Functor contract — operations & validations (laws verified above; IO defers the mapper"
          + " exception, verified below)")
  void functorContract() {
    TypeClassContract.<IOKind.Witness>functor(IOFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
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
      Function<Integer, String> nullSafeMapper = String::valueOf;

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
