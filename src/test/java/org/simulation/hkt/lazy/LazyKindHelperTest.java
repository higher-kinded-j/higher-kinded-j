package org.simulation.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.simulation.hkt.lazy.LazyKindHelper.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;

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
      Kind<LazyKind<?>, String> kind = wrap(baseLazy);
      assertThat(kind).isInstanceOf(LazyHolder.class);
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
      Kind<LazyKind<?>, String> kind = wrap(baseLazy);
      assertThat(unwrap(kind)).isSameAs(baseLazy);
    }

    record DummyLazyKind<A>() implements Kind<LazyKind<?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<LazyKind<?>, String> unknownKind = new DummyLazyKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyLazyKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullLazy() {
      LazyHolder<Double> holderWithNull = new LazyHolder<>(null);
      @SuppressWarnings("unchecked")
      Kind<LazyKind<?>, Double> kind = (Kind<LazyKind<?>, Double>) (Kind<?, ?>) holderWithNull;

      assertThatThrownBy(() -> unwrap(kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_HOLDER_STATE_MSG);
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    // Add throws Throwable because force() is called indirectly via unwrap().force()
    void defer_shouldWrapSupplier() throws Throwable {
      AtomicInteger counter = new AtomicInteger(0);
      // Use ThrowableSupplier here
      ThrowableSupplier<Integer> supplier =
          () -> {
            counter.incrementAndGet();
            return 42;
          };
      Kind<LazyKind<?>, Integer> kind = LazyKindHelper.defer(supplier);

      assertThat(counter.get()).isZero();

      Lazy<Integer> lazy = unwrap(kind);
      assertThat(lazy.force()).isEqualTo(42); // force() now throws Throwable
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
    void now_shouldWrapAlreadyEvaluatedValue() throws Throwable { // Add throws
      AtomicInteger counter = new AtomicInteger(0); // Use local counter for isolation
      Kind<LazyKind<?>, String> kind = LazyKindHelper.now("Precomputed");

      assertThat(counter.get()).isZero();
      Lazy<String> lazy = unwrap(kind);
      assertThat(lazy.force()).isEqualTo("Precomputed"); // force() throws Throwable
      assertThat(counter.get()).isZero();
    }

    @Test
    void now_shouldWrapNullValue() throws Throwable { // Add throws
      Kind<LazyKind<?>, String> kind = LazyKindHelper.now(null);
      Lazy<String> lazy = unwrap(kind);
      assertThat(lazy.force()).isNull(); // force() throws Throwable
    }
  }

  @Nested
  @DisplayName("force()")
  class ForceTests {
    @Test
    void force_shouldExecuteWrappedLazy() throws Throwable { // Add throws
      AtomicInteger counter = new AtomicInteger(0);
      Kind<LazyKind<?>, String> kind =
          LazyKindHelper.defer( // Use ThrowableSupplier implicitly
              () -> {
                counter.incrementAndGet();
                return "Executed";
              });

      assertThat(counter.get()).isZero();
      assertThat(force(kind)).isEqualTo("Executed"); // force() helper throws Throwable
      assertThat(counter.get()).isEqualTo(1);

      assertThat(force(kind)).isEqualTo("Executed");
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void force_shouldPropagateRuntimeExceptionFromLazy() { // No throws needed for unchecked
      Kind<LazyKind<?>, String> failingKind = wrap(failingLazy);
      // force() helper re-throws the original exception
      assertThatThrownBy(() -> force(failingKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException);
    }

    @Test
    void force_shouldPropagateCheckedExceptionFromLazy() { // No throws needed (catchThrowable)
      Kind<LazyKind<?>, String> checkedFailingKind = wrap(checkedFailingLazy);
      // Use catchThrowable as force() declares throws Throwable
      Throwable thrown = catchThrowable(() -> force(checkedFailingKind));
      assertThat(thrown)
          .isInstanceOf(IOException.class) // Expect the original checked exception
          .hasMessage("Checked IO Failure");
    }

    @Test
    void force_shouldThrowKindUnwrapExceptionIfKindIsInvalid() { // No throws needed
      assertThatThrownBy(() -> force(null))
          .isInstanceOf(KindUnwrapException.class); // Propagates unwrap exception
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
