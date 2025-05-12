package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LazyKindHelper Tests (ThrowableSupplier)")
class LazyKindHelperTest {

  // Simple IO action for testing - now using ThrowableSupplier
  private final ThrowableSupplier<String> baseComputation =
      () -> "Result"; // Simple lambda still works
  private final Lazy<String> baseLazy = Lazy.defer(baseComputation);

  private final RuntimeException testException = new RuntimeException("Lazy Failure");
  private final ThrowableSupplier<String> failingComputation =
      () -> {
        throw testException;
      };
  private final Lazy<String> failingLazy = Lazy.defer(failingComputation);

  // Supplier throwing a checked exception
  private final ThrowableSupplier<String> checkedExceptionComputation =
      () -> {
        throw new IOException("Checked IO Failure");
      };
  private final Lazy<String> checkedFailingLazy = Lazy.defer(checkedExceptionComputation);

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForLazy() {
      Kind<LazyKind.Witness, String> kind = wrap(baseLazy);
      assertThat(kind).isInstanceOf(LazyKindHelper.LazyHolder.class);
      assertThat(unwrap(kind)).isSameAs(baseLazy);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input Lazy cannot be null");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_shouldReturnOriginalLazy() {
      Kind<LazyKind.Witness, String> kind = wrap(baseLazy);
      assertThat(unwrap(kind)).isSameAs(baseLazy);
    }

    record DummyOtherKind<A>() implements Kind<LazyKind.Witness, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<LazyKind.Witness, String> unknownKind = new DummyOtherKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyOtherKind.class.getName());
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    // Add throws Throwable because force() is called indirectly via unwrap().force()
    void defer_shouldWrapSupplier() throws Throwable {
      AtomicInteger counter = new AtomicInteger(0);
      ThrowableSupplier<Integer> supplier =
          () -> {
            counter.incrementAndGet();
            return 42;
          };
      Kind<LazyKind.Witness, Integer> kind = LazyKindHelper.defer(supplier);

      assertThat(counter.get()).isZero();

      Lazy<Integer> lazy = unwrap(kind);
      assertThat(lazy.force()).isEqualTo(42);
      assertThat(counter.get()).isEqualTo(1);

      assertThat(lazy.force()).isEqualTo(42);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void defer_shouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> LazyKindHelper.defer(null))
          .withMessageContaining("computation"); // Message from Lazy.defer check
    }

    @Test
    void now_shouldWrapAlreadyEvaluatedValue() throws Throwable {
      AtomicInteger counter = new AtomicInteger(0);
      Kind<LazyKind.Witness, String> kind = LazyKindHelper.now("Precomputed");

      assertThat(counter.get()).isZero();
      Lazy<String> lazy = unwrap(kind);
      assertThat(lazy.force()).isEqualTo("Precomputed");
      assertThat(counter.get()).isZero();
    }

    @Test
    void now_shouldWrapNullValue() throws Throwable {
      Kind<LazyKind.Witness, String> kind = LazyKindHelper.now(null);
      Lazy<String> lazy = unwrap(kind);
      assertThat(lazy.force()).isNull();
    }
  }

  @Nested
  @DisplayName("force()")
  class ForceTests {
    @Test
    void force_shouldExecuteWrappedLazy() throws Throwable {
      AtomicInteger counter = new AtomicInteger(0);
      Kind<LazyKind.Witness, String> kind =
          LazyKindHelper.defer( // Use ThrowableSupplier implicitly
              () -> {
                counter.incrementAndGet();
                return "Executed";
              });

      assertThat(counter.get()).isZero();
      assertThat(force(kind)).isEqualTo("Executed");
      assertThat(counter.get()).isEqualTo(1);

      assertThat(force(kind)).isEqualTo("Executed");
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void force_shouldPropagateRuntimeExceptionFromLazy() {
      Kind<LazyKind.Witness, String> failingKind = wrap(failingLazy);
      assertThatThrownBy(() -> force(failingKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException);
    }

    @Test
    void force_shouldPropagateCheckedExceptionFromLazy() {
      Kind<LazyKind.Witness, String> checkedFailingKind = wrap(checkedFailingLazy);
      Throwable thrown = catchThrowable(() -> force(checkedFailingKind));
      assertThat(thrown).isInstanceOf(IOException.class).hasMessage("Checked IO Failure");
    }

    @Test
    void force_shouldThrowKindUnwrapExceptionIfKindIsInvalid() {
      assertThatThrownBy(() -> force(null)).isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {
    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<LazyKindHelper> constructor = LazyKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
