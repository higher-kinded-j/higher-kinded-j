// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.vtask.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VTaskKindHelper Test Suite")
class VTaskKindHelperTest {

  private static final int TEST_VALUE = 42;
  private static final String TEST_STRING = "test";

  @Nested
  @DisplayName("widen() Operations")
  class WidenOperations {

    @Test
    @DisplayName("widen() returns same VTask instance")
    void widenReturnsSameVTaskInstance() {
      VTask<Integer> original = VTask.succeed(TEST_VALUE);

      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(original);

      assertThat(kind).isSameAs(original);
    }

    @Test
    @DisplayName("widen() throws for null input")
    void widenThrowsForNullInput() {
      assertThatThrownBy(() -> VTASK.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("VTask cannot be null");
    }

    @Test
    @DisplayName("widen() preserves VTask laziness")
    void widenPreservesVTaskLaziness() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<Integer> vtask =
          VTask.of(
              () -> {
                counter.incrementAndGet();
                return TEST_VALUE;
              });

      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(vtask);

      // Should not execute during widening
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThat(VTASK.narrow(kind).run()).isEqualTo(TEST_VALUE);
      assertThat(counter.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("narrow() Operations")
  class NarrowOperations {

    @Test
    @DisplayName("narrow() returns original VTask")
    void narrowReturnsOriginalVTask() {
      VTask<Integer> original = VTask.succeed(TEST_VALUE);
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(original);

      VTask<Integer> narrowed = VTASK.narrow(kind);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("narrow() throws for null input")
    void narrowThrowsForNullInput() {
      assertThatThrownBy(() -> VTASK.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for VTask");
    }

    @Test
    @DisplayName("narrow() throws for unknown Kind type")
    void narrowThrowsForUnknownKindType() {
      // Create a dummy VTaskKind that is not a VTask
      VTaskKind<String> unknownKind = new VTaskKind<String>() {};

      assertThatThrownBy(() -> VTASK.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance cannot be narrowed to VTask");
    }

    @Test
    @DisplayName("narrow() preserves VTask identity")
    void narrowPreservesVTaskIdentity() {
      VTask<String> original = VTask.succeed(TEST_STRING);
      Kind<VTaskKind.Witness, String> widened = VTASK.widen(original);
      VTask<String> narrowed = VTASK.narrow(widened);

      assertThat(narrowed).isSameAs(original);
      assertThat(narrowed.run()).isEqualTo(TEST_STRING);
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("of() creates VTask from Callable")
    void ofCreatesVTaskFromCallable() {
      Callable<Integer> callable = () -> TEST_VALUE;

      Kind<VTaskKind.Witness, Integer> kind = VTASK.of(callable);

      assertThat(VTASK.narrow(kind).run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("of() throws for null Callable")
    void ofThrowsForNullCallable() {
      assertThatThrownBy(() -> VTASK.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("delay() creates VTask from Supplier")
    void delayCreatesVTaskFromSupplier() {
      Supplier<Integer> supplier = () -> TEST_VALUE;

      Kind<VTaskKind.Witness, Integer> kind = VTASK.delay(supplier);

      assertThat(VTASK.narrow(kind).run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("delay() throws for null Supplier")
    void delayThrowsForNullSupplier() {
      assertThatThrownBy(() -> VTASK.delay(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("succeed() creates successful VTask")
    void succeedCreatesSuccessfulVTask() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.succeed(TEST_VALUE);

      assertThat(VTASK.narrow(kind).run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("succeed() handles null value")
    void succeedHandlesNullValue() {
      Kind<VTaskKind.Witness, String> kind = VTASK.succeed(null);

      assertThat(VTASK.narrow(kind).run()).isNull();
    }

    @Test
    @DisplayName("fail() creates failing VTask")
    void failCreatesFailingVTask() {
      RuntimeException exception = new RuntimeException("Test error");

      Kind<VTaskKind.Witness, Integer> kind = VTASK.fail(exception);

      assertThatVTask(VTASK.<Integer>narrow(kind))
          .fails()
          .withExceptionType(RuntimeException.class)
          .withMessage("Test error");
    }

    @Test
    @DisplayName("fail() throws for null Throwable")
    void failThrowsForNullThrowable() {
      assertThatThrownBy(() -> VTASK.fail(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Execution Methods")
  class ExecutionMethods {

    @Test
    @DisplayName("run() executes and returns value")
    void runExecutesAndReturnsValue() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.succeed(TEST_VALUE);

      Integer result = VTASK.run(kind);

      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("run() propagates exception")
    void runPropagatesException() {
      RuntimeException exception = new RuntimeException("Test error");
      Kind<VTaskKind.Witness, Integer> kind = VTASK.fail(exception);

      assertThatThrownBy(() -> VTASK.run(kind))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Test error");
    }

    @Test
    @DisplayName("runSafe() returns Success for successful VTask")
    void runSafeReturnsSuccessForSuccessfulVTask() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.succeed(TEST_VALUE);

      Try<Integer> result = VTASK.runSafe(kind);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(-1)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runSafe() returns Failure for failing VTask")
    void runSafeReturnsFailureForFailingVTask() {
      RuntimeException exception = new RuntimeException("Test error");
      Kind<VTaskKind.Witness, Integer> kind = VTASK.fail(exception);

      Try<Integer> result = VTASK.runSafe(kind);

      assertThat(result.isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Round-trip Tests")
  class RoundTripTests {

    @Test
    @DisplayName("widen then narrow preserves identity")
    void widenThenNarrowPreservesIdentity() {
      VTask<Integer> original = VTask.succeed(TEST_VALUE);

      Kind<VTaskKind.Witness, Integer> widened = VTASK.widen(original);
      VTask<Integer> narrowed = VTASK.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("multiple widen/narrow cycles preserve identity")
    void multipleWidenNarrowCyclesPreserveIdentity() {
      VTask<Integer> original = VTask.succeed(TEST_VALUE);

      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(original);
      for (int i = 0; i < 5; i++) {
        VTask<Integer> narrowed = VTASK.narrow(kind);
        kind = VTASK.widen(narrowed);
      }

      assertThat(VTASK.narrow(kind)).isSameAs(original);
    }
  }

  @Nested
  @DisplayName("Complex Type Tests")
  class ComplexTypeTests {

    @Test
    @DisplayName("handles nested VTask types")
    void handlesNestedVTaskTypes() {
      VTask<VTask<Integer>> nested = VTask.succeed(VTask.succeed(TEST_VALUE));

      Kind<VTaskKind.Witness, VTask<Integer>> kind = VTASK.widen(nested);
      VTask<VTask<Integer>> narrowed = VTASK.narrow(kind);

      assertThat(narrowed.run().run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("handles generic types")
    void handlesGenericTypes() {
      VTask<List<Integer>> vtask = VTask.succeed(List.of(1, 2, 3));

      Kind<VTaskKind.Witness, List<Integer>> kind = VTASK.widen(vtask);
      VTask<List<Integer>> narrowed = VTASK.narrow(kind);

      assertThat(narrowed.run()).containsExactly(1, 2, 3);
    }
  }
}
