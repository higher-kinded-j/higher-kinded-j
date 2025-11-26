// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.io.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.test.api.CoreTypeTest.ioKindHelper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOKindHelper Complete Test Suite")
class IOKindHelperTest extends IOTestBase {

  // Simple IO action for testing
  private final IO<String> baseIO = IO.delay(() -> "Result");
  private final RuntimeException testException = new RuntimeException("IO Failure");
  private final IO<String> failingIO =
      IO.delay(
          () -> {
            throw testException;
          });

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
      assertThatIO(nullIO).hasValueNull();
    }

    @Test
    @DisplayName("Complex IO computations work correctly")
    void testComplexIOComputations() {
      IO<String> complexIO =
          IO.delay(() -> "base")
              .map(s -> s + "_mapped")
              .flatMap(s -> IO.delay(() -> s + "_flatMapped"));

      ioKindHelper(complexIO).test();

      assertThatIO(complexIO).hasValue("base_mapped_flatMapped");
    }

    @Test
    @DisplayName("Type safety across different generic parameters")
    void testTypeSafetyAcrossDifferentGenerics() {
      IO<String> stringIO = IO.delay(() -> "string");
      IO<Integer> intIO = IO.delay(() -> DEFAULT_IO_VALUE);

      ioKindHelper(stringIO).test();
      ioKindHelper(intIO).test();
    }

    @Test
    @DisplayName("Complex value types with nested generics")
    void testComplexValueTypes() {
      IO<List<String>> complexIO = IO.delay(() -> List.of("a", "b", "c"));

      ioKindHelper(complexIO).test();

      assertThatIO(complexIO)
          .hasValueSatisfying(list -> assertThat(list).containsExactly("a", "b", "c"));
    }
  }

  @Nested
  @DisplayName("IO_OP.widen()")
  class WidenTests {
    @Test
    @DisplayName("widen should return the same IO instance (direct implementation)")
    void widenShouldReturnHolderForIO() {
      Kind<IOKind.Witness, String> kind = IO_OP.widen(baseIO);
      assertThat(kind).isInstanceOf(IO.class);
      // Verify identity is preserved (no wrapping)
      assertThat(kind).isSameAs(baseIO);
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

    @Test
    @DisplayName("widen preserves IO laziness")
    void widenPreservesIOLaziness() {
      AtomicInteger counter = new AtomicInteger(0);
      IO<Integer> io =
          IO.delay(
              () -> {
                counter.incrementAndGet();
                return DEFAULT_IO_VALUE;
              });

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(io);

      // Should not execute during widening
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThatIO(narrowToIO(kind)).hasValue(DEFAULT_IO_VALUE);
      assertThat(counter.get()).isEqualTo(1);
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
          .hasMessageContaining("Kind instance cannot be narrowed to " + IO.class.getSimpleName());
    }

    @Test
    @DisplayName("narrow preserves IO identity")
    void narrowPreservesIOIdentity() {
      IO<String> original = IO.delay(() -> "test");
      Kind<IOKind.Witness, String> widened = IO_OP.widen(original);
      IO<String> narrowed = IO_OP.narrow(widened);

      assertThat(narrowed).isSameAs(original);
      assertThatIO(narrowed).hasValue("test");
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
            return DEFAULT_IO_VALUE;
          };
      Kind<IOKind.Witness, Integer> kind = IO_OP.delay(supplier);

      // Effect should not run yet
      assertThat(counter.get()).isZero();

      // Unwrap and run
      assertThatIO(narrowToIO(kind)).hasValue(DEFAULT_IO_VALUE);
      assertThat(counter.get()).isEqualTo(1);

      // Running again executes again
      assertThatIO(narrowToIO(kind)).hasValue(DEFAULT_IO_VALUE);
      assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("delay should throw NPE for null supplier")
    void delayShouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> IO_OP.delay(null))
          .withMessageContaining("thunk"); // Message from IO.delay check
    }

    @Test
    @DisplayName("delay maintains referential transparency")
    void delayMaintainsReferentialTransparency() {
      Supplier<String> supplier = () -> "constant";
      Kind<IOKind.Witness, String> kind = IO_OP.delay(supplier);

      assertThatIO(narrowToIO(kind)).isRepeatable();
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

    @Test
    @DisplayName("unsafeRunSync handles null results correctly")
    void unsafeRunSyncHandlesNullResults() {
      Kind<IOKind.Witness, String> nullKind = IO_OP.delay(() -> null);

      assertThat(IO_OP.unsafeRunSync(nullKind)).isNull();
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

    @Test
    @DisplayName("Nested IO structures work correctly")
    void testNestedIOStructures() {
      IO<IO<String>> nested = IO.delay(() -> IO.delay(() -> "nested"));

      Kind<IOKind.Witness, IO<String>> kind = IO_OP.widen(nested);
      IO<IO<String>> narrowed = IO_OP.narrow(kind);

      assertThatIO(narrowed).hasValueSatisfying(inner -> assertThatIO(inner).hasValue("nested"));
    }

    @Test
    @DisplayName("Complex generic types are handled correctly")
    void testComplexGenericTypes() {
      IO<List<Integer>> complexIO = IO.delay(() -> List.of(1, 2, 3));

      Kind<IOKind.Witness, List<Integer>> kind = IO_OP.widen(complexIO);
      IO<List<Integer>> narrowed = IO_OP.narrow(kind);

      assertThatIO(narrowed).hasValueSatisfying(list -> assertThat(list).containsExactly(1, 2, 3));
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
              IO.delay(() -> DEFAULT_IO_VALUE),
              IO.delay(() -> List.of(1, 2, 3)),
              IO.delay(() -> Map.of("key", "value")),
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

    @Test
    @DisplayName("Verify operations preserve laziness")
    void testOperationsPreserveLaziness() {
      AtomicInteger counter = new AtomicInteger(0);
      IO<Integer> io =
          IO.delay(
              () -> {
                counter.incrementAndGet();
                return DEFAULT_IO_VALUE;
              });

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(io);

      // Multiple widen/narrow operations should not execute
      for (int i = 0; i < 5; i++) {
        IO<Integer> narrowed = IO_OP.narrow(kind);
        kind = IO_OP.widen(narrowed);
      }

      assertThat(counter.get()).as("IO should not execute during widen/narrow").isZero();

      // Finally execute
      assertThatIO(narrowToIO(kind)).hasValue(DEFAULT_IO_VALUE);
      assertThat(counter.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("IOAssert Integration Tests")
  class IOAssertIntegrationTests {
    @Test
    @DisplayName("IOAssert works with narrowed IOs")
    void testIOAssertWithNarrowedIOs() {
      Kind<IOKind.Witness, String> kind = ioKind("test");
      IO<String> narrowed = narrowToIO(kind);

      assertThatIO(narrowed).hasValue("test");
    }

    @Test
    @DisplayName("IOAssert detects failures correctly")
    void testIOAssertDetectsFailures() {
      Kind<IOKind.Witness, String> failingKind = failingIO(testException);
      IO<String> narrowed = narrowToIO(failingKind);

      assertThatIO(narrowed).throwsException(RuntimeException.class).withMessage("IO Failure");
    }

    @Test
    @DisplayName("IOAssert verifies repeatability")
    void testIOAssertVerifiesRepeatability() {
      Kind<IOKind.Witness, Integer> kind = ioKind(DEFAULT_IO_VALUE);
      IO<Integer> narrowed = narrowToIO(kind);

      assertThatIO(narrowed).isRepeatable();
    }
  }
}
