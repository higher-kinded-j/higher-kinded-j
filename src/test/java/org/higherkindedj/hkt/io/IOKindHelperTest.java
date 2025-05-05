package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.io.IOKindHelper.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForIO() {
      Kind<IOKind<?>, String> kind = wrap(baseIO);
      assertThat(kind).isInstanceOf(IOHolder.class);
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(baseIO);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input IO cannot be null");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_shouldReturnOriginalIO() {
      Kind<IOKind<?>, String> kind = wrap(baseIO);
      assertThat(unwrap(kind)).isSameAs(baseIO);
    }

    // Dummy Kind implementation that is not IOHolder
    record DummyIOKind<A>() implements Kind<IOKind<?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<IOKind<?>, String> unknownKind = new DummyIOKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
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
      Kind<IOKind<?>, Integer> kind = IOKindHelper.delay(supplier);

      // Effect should not run yet
      assertThat(counter.get()).isZero();

      // Unwrap and run
      IO<Integer> io = unwrap(kind);
      assertThat(io.unsafeRunSync()).isEqualTo(42);
      assertThat(counter.get()).isEqualTo(1);

      // Running again executes again
      assertThat(io.unsafeRunSync()).isEqualTo(42);
      assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void delay_shouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> IOKindHelper.delay(null))
          .withMessageContaining("thunk"); // Message from IO.delay check
    }
  }

  @Nested
  @DisplayName("unsafeRunSync()")
  class UnsafeRunSyncTests {
    @Test
    void unsafeRunSync_shouldExecuteWrappedIO() {
      AtomicInteger counter = new AtomicInteger(0);
      Kind<IOKind<?>, String> kind =
          IOKindHelper.delay(
              () -> {
                counter.incrementAndGet();
                return "Executed";
              });

      assertThat(counter.get()).isZero();
      assertThat(unsafeRunSync(kind)).isEqualTo("Executed");
      assertThat(counter.get()).isEqualTo(1);

      // Run again
      assertThat(unsafeRunSync(kind)).isEqualTo("Executed");
      assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void unsafeRunSync_shouldPropagateExceptionFromIO() {
      Kind<IOKind<?>, String> failingKind = wrap(failingIO);

      assertThatThrownBy(() -> unsafeRunSync(failingKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException);
    }

    @Test
    void unsafeRunSync_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> unsafeRunSync(null))
          .isInstanceOf(KindUnwrapException.class); // Propagates unwrap exception
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {
    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<IOKindHelper> constructor = IOKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
