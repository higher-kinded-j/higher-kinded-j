// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.io.IOKindHelper.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOKindHelper Tests")
class IOKindHelperTest {

  // Simple IO action for testing
  private final IO<String> baseIO = IO.delay(() -> "Result");
  private final RuntimeException testException = new RuntimeException("IO Failure");
  private final IO<String> failingIO =
      IO.delay(
          () -> {
            throw testException;
          });

  @Nested
  @DisplayName("IO_OP.widen()")
  class WrapTests {
    @Test
    void widen_shouldReturnHolderForIO() {
      var kind = IO_OP.widen(baseIO);
      assertThat(kind).isInstanceOf(IOHolder.class);
      // Unwrap to verify
      assertThat(IO_OP.narrow(kind)).isSameAs(baseIO);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> IO_OP.widen(null))
          .withMessageContaining("Input IO cannot be null");
    }
  }

  @Nested
  @DisplayName("IO_OP.narrow()")
  class UnwrapTests {
    @Test
    void narrow_shouldReturnOriginalIO() {
      var kind = IO_OP.widen(baseIO);
      assertThat(IO_OP.narrow(kind)).isSameAs(baseIO);
    }

    // Dummy Kind implementation that is not IOHolder
    record DummyIOKind<A>() implements IOKind<A> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> IO_OP.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      IOKind<String> unknownKind = new DummyIOKind<>();
      assertThatThrownBy(() -> IO_OP.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyIOKind.class.getName());
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    void delay_shouldWrapSupplier() {
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
    void delay_shouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> IO_OP.delay(null))
          .withMessageContaining("thunk"); // Message from IO.delay check
    }
  }

  @Nested
  @DisplayName("unsafeRunSync()")
  class UnsafeRunSyncTests {
    @Test
    void unsafeRunSync_shouldExecuteWrappedIO() {
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
    void unsafeRunSync_shouldPropagateExceptionFromIO() {
      var failingKind = IO_OP.widen(failingIO);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(failingKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException);
    }

    @Test
    void unsafeRunSync_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> IO_OP.unsafeRunSync(null))
          .isInstanceOf(KindUnwrapException.class); // Propagates unwrap exception
    }
  }
}
