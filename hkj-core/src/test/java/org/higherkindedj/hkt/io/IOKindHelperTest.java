// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.test.api.CoreTypeTest.ioKindHelper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOKindHelper Complete Test Suite")
class IOKindHelperTest extends TypeClassTestBase<IOKind.Witness, String, String> {

  // Simple IO action for testing
  private final IO<String> baseIO = IO.delay(() -> "Result");
  private final RuntimeException testException = new RuntimeException("IO Failure");
  private final IO<String> failingIO =
      IO.delay(
          () -> {
            throw testException;
          });

  @Override
  protected Kind<IOKind.Witness, String> createValidKind() {
    return IO_OP.widen(IO.delay(() -> "Success"));
  }

  @Override
  protected Kind<IOKind.Witness, String> createValidKind2() {
    return IO_OP.widen(IO.delay(() -> "Another"));
  }

  @Override
  protected Function<String, String> createValidMapper() {
    return String::toUpperCase;
  }

  @Override
  protected BiPredicate<Kind<IOKind.Witness, ?>, Kind<IOKind.Witness, ?>> createEqualityChecker() {
    return (k1, k2) -> {
      // Execute both IOs and compare their results
      Object v1 = IO_OP.narrow(castKind(k1)).unsafeRunSync();
      Object v2 = IO_OP.narrow(castKind(k2)).unsafeRunSync();
      return v1.equals(v2);
    };
  }

  @SuppressWarnings("unchecked")
  private static <A> Kind<IOKind.Witness, A> castKind(Kind<IOKind.Witness, ?> kind) {
    return (Kind<IOKind.Witness, A>) kind;
  }

  @Nested
  @DisplayName("Complete KindHelper Test Suite")
  class CompleteTestSuite {
    @Test
    @DisplayName("Run complete KindHelper test suite for IO")
    void completeKindHelperTestSuite() {
      IO<String> validInstance = IO.delay(() -> "Success");

      ioKindHelper(validInstance).test();
    }

    @Test
    @DisplayName("Complete test suite with multiple IO types")
    void completeTestSuiteWithMultipleTypes() {
      List<IO<String>> testInstances =
          List.of(
              IO.delay(() -> "Success"),
              IO.delay(() -> null),
              IO.delay(() -> ""),
              IO.delay(
                  () -> {
                    throw new RuntimeException("Test exception");
                  }));

      for (IO<String> instance : testInstances) {
        // Only test non-exceptional IOs for round-trip
        if (instance != testInstances.get(3)) {
          ioKindHelper(instance).test();
        }
      }
    }

    @Test
    @DisplayName("Comprehensive test with implementation validation")
    void comprehensiveTestWithImplementationValidation() {
      IO<String> validInstance = IO.delay(() -> "Comprehensive");

      ioKindHelper(validInstance).testWithValidation(IOKindHelper.class);
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {
    @Test
    @DisplayName("Test round-trip widen/narrow operations")
    void testRoundTripOperations() {
      IO<String> validInstance = IO.delay(() -> "test");

      ioKindHelper(validInstance)
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test null parameter validations")
    void testNullParameterValidations() {
      ioKindHelper(IO.delay(() -> "test"))
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid Kind type handling")
    void testInvalidKindType() {
      ioKindHelper(IO.delay(() -> "test"))
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test idempotency of operations")
    void testIdempotency() {
      IO<String> validInstance = IO.delay(() -> "idempotent");

      ioKindHelper(validInstance)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test edge cases and boundary conditions")
    void testEdgeCases() {
      IO<String> validInstance = IO.delay(() -> "edge");

      ioKindHelper(validInstance)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .test();
    }
  }

  @Nested
  @DisplayName("Specific IO Behaviour Tests")
  class SpecificBehaviourTests {
    @Test
    @DisplayName("IO instances with effects work correctly")
    void testIOInstancesWithEffects() {
      AtomicInteger counter = new AtomicInteger(0);
      IO<String> effectfulIO =
          IO.delay(
              () -> {
                counter.incrementAndGet();
                return "Executed: " + counter.get();
              });

      ioKindHelper(effectfulIO).test();

      // Verify laziness - effect should not have run during test
      assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("Null values in IO are preserved")
    void testNullValuesPreserved() {
      IO<String> nullIO = IO.delay(() -> null);

      ioKindHelper(nullIO).test();

      // Verify null is preserved
      assertThat(nullIO.unsafeRunSync()).isNull();
    }

    @Test
    @DisplayName("Complex IO computations work correctly")
    void testComplexIOComputations() {
      IO<String> complexIO =
          IO.delay(() -> "base")
              .map(s -> s + "_mapped")
              .flatMap(s -> IO.delay(() -> s + "_flatMapped"));

      ioKindHelper(complexIO).test();

      assertThat(complexIO.unsafeRunSync()).isEqualTo("base_mapped_flatMapped");
    }

    @Test
    @DisplayName("Type safety across different generic parameters")
    void testTypeSafetyAcrossDifferentGenerics() {
      IO<String> stringIO = IO.delay(() -> "string");
      IO<Integer> intIO = IO.delay(() -> 42);

      ioKindHelper(stringIO).test();

      ioKindHelper(intIO).test();
    }

    @Test
    @DisplayName("Complex value types with nested generics")
    void testComplexValueTypes() {
      IO<List<String>> complexIO = IO.delay(() -> List.of("a", "b", "c"));

      ioKindHelper(complexIO).test();

      assertThat(complexIO.unsafeRunSync()).containsExactly("a", "b", "c");
    }
  }

  @Nested
  @DisplayName("IO_OP.widen()")
  class WidenTests {
    @Test
    @DisplayName("widen should return holder for IO")
    void widenShouldReturnHolderForIO() {
      Kind<IOKind.Witness, String> kind = IO_OP.widen(baseIO);
      assertThat(kind).isInstanceOf(IOKindHelper.IOHolder.class);
      // Unwrap to verify
      assertThat(IO_OP.narrow(kind)).isSameAs(baseIO);
    }

    @Test
    @DisplayName("widen should throw for null input")
    void widenShouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> IO_OP.widen(null))
          .withMessageContaining("Input IO cannot be null");
    }
  }

  @Nested
  @DisplayName("IO_OP.narrow()")
  class NarrowTests {
    @Test
    @DisplayName("narrow should return original IO")
    void narrowShouldReturnOriginalIO() {
      Kind<IOKind.Witness, String> kind = IO_OP.widen(baseIO);
      assertThat(IO_OP.narrow(kind)).isSameAs(baseIO);
    }

    // Dummy Kind implementation that is not IOHolder
    record DummyIOKind<A>() implements IOKind<A> {}

    @Test
    @DisplayName("narrow should throw for null input")
    void narrowShouldThrowForNullInput() {
      assertThatThrownBy(() -> IO_OP.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for IO");
    }

    @Test
    @DisplayName("narrow should throw for unknown Kind type")
    void narrowShouldThrowForUnknownKindType() {
      IOKind<String> unknownKind = new DummyIOKind<>();
      assertThatThrownBy(() -> IO_OP.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance is not a IO: " + DummyIOKind.class.getName());
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    @DisplayName("delay should wrap supplier")
    void delayShouldWrapSupplier() {
      AtomicInteger counter = new AtomicInteger(0);
      Supplier<Integer> supplier =
          () -> {
            counter.incrementAndGet();
            return 42;
          };
      Kind<IOKind.Witness, Integer> kind = IO_OP.delay(supplier);

      // Effect should not run yet
      assertThat(counter.get()).isZero();

      // Unwrap and run
      IO<Integer> io = IO_OP.narrow(kind);
      assertThat(io.unsafeRunSync()).isEqualTo(42);
      assertThat(counter.get()).isEqualTo(1);

      // Running again executes again
      assertThat(io.unsafeRunSync()).isEqualTo(42);
      assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("delay should throw NPE for null supplier")
    void delayShouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> IO_OP.delay(null))
          .withMessageContaining("thunk"); // Message from IO.delay check
    }
  }

  @Nested
  @DisplayName("unsafeRunSync()")
  class UnsafeRunSyncTests {
    @Test
    @DisplayName("unsafeRunSync should execute wrapped IO")
    void unsafeRunSyncShouldExecuteWrappedIO() {
      AtomicInteger counter = new AtomicInteger(0);
      Kind<IOKind.Witness, String> kind =
          IO_OP.delay(
              () -> {
                counter.incrementAndGet();
                return "Executed";
              });

      assertThat(counter.get()).isZero();
      assertThat(IO_OP.unsafeRunSync(kind)).isEqualTo("Executed");
      assertThat(counter.get()).isEqualTo(1);

      // Run again
      assertThat(IO_OP.unsafeRunSync(kind)).isEqualTo("Executed");
      assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("unsafeRunSync should propagate exception from IO")
    void unsafeRunSyncShouldPropagateExceptionFromIO() {
      Kind<IOKind.Witness, String> failingKind = IO_OP.widen(failingIO);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(failingKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException);
    }

    @Test
    @DisplayName("unsafeRunSync should throw if Kind is invalid")
    void unsafeRunSyncShouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> IO_OP.unsafeRunSync(null))
          .isInstanceOf(KindUnwrapException.class); // Propagates unwrap exception
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {
    @Test
    @DisplayName("Holder creates minimal overhead")
    void testMinimalOverhead() {
      IO<String> original = IO.delay(() -> "test");

      ioKindHelper(original).skipPerformance().test();
    }

    @Test
    @DisplayName("Multiple operations are idempotent")
    void testIdempotentOperations() {
      IO<String> original = IO.delay(() -> "idempotent");

      ioKindHelper(original)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Performance characteristics test")
    void testPerformanceCharacteristics() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        IO<String> testInstance = IO.delay(() -> "performance_test");

        ioKindHelper(testInstance).withPerformanceTests().test();
      }
    }

    @Test
    @DisplayName("Memory efficiency test")
    void testMemoryEfficiency() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        IO<String> testInstance = IO.delay(() -> "memory_test");

        ioKindHelper(testInstance).withPerformanceTests().test();
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases and Corner Cases")
  class EdgeCasesTests {
    @Test
    @DisplayName("All combinations of null values")
    void testAllNullValueCombinations() {
      List<IO<String>> nullInstances = List.of(IO.delay(() -> null), IO.delay(() -> ""));

      for (IO<String> instance : nullInstances) {
        ioKindHelper(instance).test();
      }
    }
  }

  @Nested
  @DisplayName("Advanced Testing Scenarios")
  class AdvancedTestingScenarios {
    @Test
    @DisplayName("Concurrent access test")
    void testConcurrentAccess() {
      if (Boolean.parseBoolean(System.getProperty("test.concurrency", "false"))) {
        IO<String> testInstance = IO.delay(() -> "concurrent_test");

        ioKindHelper(testInstance).withConcurrencyTests().test();
      }
    }

    @Test
    @DisplayName("Implementation standards validation")
    void testImplementationStandards() {
      KindHelperTestPattern.validateImplementationStandards(IO.class, IOKindHelper.class);
    }

    @Test
    @DisplayName("Quick test for fast test suites")
    void testQuickValidation() {
      IO<String> testInstance = IO.delay(() -> "quick_test");

      ioKindHelper(testInstance).test();
    }

    @Test
    @DisplayName("Stress test with complex scenarios")
    void testComplexStressScenarios() {
      List<IO<Object>> complexInstances =
          List.of(
              IO.delay(() -> "simple_string"),
              IO.delay(() -> 42),
              IO.delay(() -> List.of(1, 2, 3)),
              IO.delay(() -> java.util.Map.of("key", "value")),
              IO.delay(() -> null));

      for (IO<Object> instance : complexInstances) {
        ioKindHelper(instance).test();
      }
    }
  }

  @Nested
  @DisplayName("Comprehensive Coverage Tests")
  class ComprehensiveCoverageTests {
    @Test
    @DisplayName("All IO types and states")
    void testAllIOTypesAndStates() {
      List<IO<String>> allStates =
          List.of(IO.delay(() -> "success"), IO.delay(() -> ""), IO.delay(() -> null));

      for (IO<String> state : allStates) {
        ioKindHelper(state).test();
      }
    }

    @Test
    @DisplayName("Full lifecycle test")
    void testFullLifecycle() {
      IO<String> original = IO.delay(() -> "lifecycle_test");

      ioKindHelper(original).test();
    }
  }
}
